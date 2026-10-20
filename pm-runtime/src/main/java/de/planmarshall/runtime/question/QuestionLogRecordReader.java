/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.question;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.QuestionRecordReader;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.QuestionLog;
import de.planmarshall.core.store.QuestionLog.Lines;
import de.planmarshall.core.store.QuestionRecord;

/**
 * The adapter of the engine port {@link QuestionRecordReader} over the question log {@link QuestionLog} of
 * {@code core.store} (PM-ARCH-4, PM-IMPL-5; ADR {@code core-store/032}): it reads the record a {@code question} state
 * waits on for the scope the request addresses and returns the port's tri-state read.
 * <p>
 * The read is a read: it takes no lock that an append could wait on, writes nothing and repairs nothing. The log is
 * judged as a whole, so a record is never reported open on the strength of the readable lines alone: an absent log, a
 * line refused when read (an unparseable line, a record of another class, another {@code format_version}), a torn tail
 * and a log the store cannot read are all {@link QuestionRecordReader.QuestionRead.Unreadable}, never
 * {@link QuestionRecordReader.QuestionRead.Open} (ADR {@code core-store/039}).
 * <p>
 * The scope of the request is the plan or epic its id names, or the workspace scope of the project for an empty id. A
 * scope id that names a plan and an epic is refused rather than guessed, and an id that is no identifier is a value,
 * not an exception.
 *
 * @since 0.1
 */
public final class QuestionLogRecordReader implements QuestionRecordReader {

    private final Path base;
    private final String projectId;

    /**
     * @param base      the base directory of the runtime, below which the project stores lie
     * @param projectId the project whose question logs are read
     * @throws NullPointerException if a component is {@code null}
     */
    public QuestionLogRecordReader(Path base, String projectId) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /**
     * {@inheritDoc}
     * <p>
     * The whole log is judged, not only the lines of the question: a refused line anywhere in it, a torn tail or an
     * unreadable store makes the read {@code Unreadable}, whether the question's own last readable line is open or
     * closed.
     *
     * @throws NullPointerException if the request is {@code null}
     */
    @Override
    public QuestionRead read(ReadRequest request) {
        Objects.requireNonNull(request, "request");
        var resolution = resolve(request.scopeId());
        if (resolution.refused()) {
            return new QuestionRead.Unreadable(resolution.refusal());
        }
        var log = new QuestionLog(resolution.scope());
        Lines lines;
        try {
            lines = log.read();
        } catch (UncheckedIOException _) {
            return new QuestionRead.Unreadable(
                    "the question log " + QuestionLog.FILE_NAME + " cannot be read");
        }
        var refused = lines.refused();
        if (!refused.isEmpty()) {
            return new QuestionRead.Unreadable(
                    "the question log holds a refused line " + refused.getFirst().line());
        }
        if (lines.tornTailOffset().isPresent()) {
            return new QuestionRead.Unreadable(
                    "the question log ends in a torn tail at byte " + lines.tornTailOffset().getAsLong());
        }
        var current = lines.current(request.questionId());
        if (current.isEmpty()) {
            return new QuestionRead.Unreadable("the question " + request.questionId() + " is not in the log");
        }
        return resolved(current.get());
    }

    /** The record is resolved exactly when its current line is a closing line; that line's event is its outcome. */
    private static QuestionRead resolved(QuestionRecord record) {
        return record.closes() ? new QuestionRead.Resolved(record.event().wireName()) : new QuestionRead.Open();
    }

    /**
     * The scope a request addresses: the workspace scope for an empty id, else the plan or epic of that id. The store
     * directory of the scope tells the two apart; an id that names both is refused rather than guessed.
     */
    private Resolution resolve(Optional<String> scopeId) {
        try {
            if (scopeId.isEmpty()) {
                return Resolution.of(JournalScope.workspace(base, projectId));
            }
            var id = scopeId.get();
            var plan = JournalScope.plan(base, projectId, id);
            var epic = JournalScope.epic(base, projectId, id);
            var planExists = Files.isDirectory(plan.pathBase());
            var epicExists = Files.isDirectory(epic.pathBase());
            if (planExists && epicExists) {
                return Resolution.refused("the scope " + id + " names a plan and an epic");
            }
            if (!planExists && !epicExists) {
                return Resolution.refused("no question log for the scope");
            }
            return Resolution.of(planExists ? plan : epic);
        } catch (IllegalArgumentException _) {
            return Resolution.refused("the scope is not an identifier: " + scopeId.orElse(""));
        }
    }

    /** The scope a request resolved to, or the reason it resolved to none. */
    private record Resolution(JournalScope scope, String refusal) {

        static Resolution of(JournalScope scope) {
            return new Resolution(scope, null);
        }

        static Resolution refused(String reason) {
            return new Resolution(null, reason);
        }

        boolean refused() {
            return refusal != null;
        }
    }
}
