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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.QuestionChannel;
import de.planmarshall.core.store.QuestionEvent;
import de.planmarshall.core.store.QuestionLog;
import de.planmarshall.core.store.QuestionRecord;
import de.planmarshall.core.store.QuestionRecord.Answer;
import de.planmarshall.core.store.QuestionRecord.AnswerKind;
import de.planmarshall.core.store.QuestionRecord.Blocks;
import de.planmarshall.core.store.QuestionRecord.Decision;
import de.planmarshall.core.store.QuestionRecord.Origin;
import de.planmarshall.core.store.QuestionRecord.QuestionClass;
import de.planmarshall.core.store.QuestionRecord.Reversibility;
import de.planmarshall.core.store.QuestionRecordCodec;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.workspace.WorkspaceConfinement;

/** The fixture log of the adapter tests: records the store accepts, the raw lines it refuses, and both line endings. */
final class QuestionFixtures {

    static final Instant AT = Instant.parse("2026-10-19T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);

    private QuestionFixtures() {
    }

    /** The two line endings every line-oriented fixture gets. */
    enum EndOfLine {
        LF("\n"),
        CRLF("\r\n");

        private final String text;

        EndOfLine(String text) {
            this.text = text;
        }
    }

    static QuestionLogRecordReader reader(Path base, String projectId) {
        return new QuestionLogRecordReader(base, projectId,
                new WorkspaceConfinement(new WorkspaceRoots(base, Set.of())));
    }

    static QuestionRecord asked(String questionId) {
        return QuestionRecord.builder()
                .event(QuestionEvent.ASKED)
                .questionId(questionId)
                .questionClass(QuestionClass.ASK)
                .origin(Origin.SERVER)
                .sourceEntity("question-log-record-reader-test")
                .questionSet(questionId)
                .revision(1)
                .decision(new Decision("A decision", "A header", "A description", List.of(), Blocks.NOTHING,
                        Reversibility.REVERSIBLE))
                .envelope("{}")
                .at(AT)
                .build();
    }

    static QuestionRecord delivered(String questionId) {
        return delivered(questionId, 1);
    }

    static QuestionRecord delivered(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.DELIVERED).questionId(questionId).revision(revision).at(AT)
                .build();
    }

    static QuestionRecord closing(String questionId, QuestionEvent event) {
        return closing(questionId, event, 1);
    }

    static QuestionRecord closing(String questionId, QuestionEvent event, int revision) {
        var builder = QuestionRecord.builder().event(event).questionId(questionId).at(AT);
        return switch (event) {
            case ANSWERED -> builder
                    .revision(revision)
                    .decisionId("D-0001")
                    .answer(new Answer(AnswerKind.CHOSEN, List.of("option_a"), null, null, null, null, false))
                    .channel(QuestionChannel.CLI)
                    .build();
            case WITHDRAWN, CANCELLED -> builder.reason("scope_ended").build();
            default -> throw new IllegalArgumentException("not a closing event: " + event);
        };
    }

    /** Appends records through the store, as the production writer does, and gives the log the fixture's endings. */
    static void append(FileLockManager locks, JournalScope scope, EndOfLine endOfLine, QuestionRecord... records) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, event -> {
                // these fixtures record nothing
            }, CLOCK);
            var refusal = new QuestionLog(scope).append(write, List.of(records));
            if (refusal.isPresent()) {
                throw new IllegalStateException("the fixture append should be accepted: " + refusal.get());
            }
            write.commit();
        }
        if (endOfLine == EndOfLine.CRLF) {
            convertToCrlf(scope);
        }
    }

    static void appendLine(JournalScope scope, String line) {
        try {
            var store = new QuestionLog(scope).store();
            Files.createDirectories(store.getParent());
            Files.writeString(store, line, UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String line(QuestionRecord record) {
        return new String(QuestionRecordCodec.write(record), UTF_8);
    }

    /** Writes raw lines, each terminated by the fixture's ending; the file is the scope's question log. */
    static void writeRaw(JournalScope scope, EndOfLine endOfLine, String... lines) {
        var store = new QuestionLog(scope).store();
        try {
            Files.createDirectories(store.getParent());
            Files.writeString(store, String.join(endOfLine.text, lines) + endOfLine.text, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void convertToCrlf(JournalScope scope) {
        var store = new QuestionLog(scope).store();
        try {
            Files.writeString(store, Files.readString(store).replace("\n", "\r\n"), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
