/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Entry;
import de.planmarshall.core.store.JsonlStoreFile.Refused;

/**
 * The append-only history of one plan, {@code mcp/history.jsonl} (Store Schemas § The Plan's History): one line per
 * state entered and left, per sub-workflow called and returned, and per instance version taking force.
 * <p>
 * The history is written in the transaction of the transition it records, under the plan lock, so the state change and
 * its history are one journaled write, and it is covered by the plan's digest sidecar without any write of its own. No
 * line is ever changed or removed: the store appends only. {@code mcp/status.json} records where the plan is; this log
 * records where it has been, which branch it took and with which outcome. An {@code instance} line records a version
 * taking force; the composition path that writes it is not part of this store.
 *
 * @since 0.1
 */
public final class PlanHistory {

    /** The history of the plan, relative to the directory of the plan. */
    public static final String FILE_NAME = "mcp/history.jsonl";

    private final JournalScope scope;

    /**
     * @param scope the plan scope whose history this is
     * @throws IllegalArgumentException if the scope is not a plan
     */
    public PlanHistory(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("Only a plan has a plan history, not a " + scope.type().wireName());
        }
    }

    /** @return the history file, absolute and normalized */
    public Path store() {
        return scope.resolve(FILE_NAME);
    }

    /** @return the path of the history relative to the path base of its plan, as its journal and sidecar name it */
    public String storePath() {
        return FILE_NAME;
    }

    /**
     * Reads the history without a lock. A torn tail is reported, never read as a line, and a refused line keeps its
     * line number; nothing is repaired.
     *
     * @return what the history holds; no entry if it does not exist
     * @throws UncheckedIOException if the history exists and cannot be read
     */
    public Lines read() {
        var file = store();
        var content = new JsonlStoreFile(file, scope.lockKey()).snapshot();
        return new Lines(file, content.decode(PlanHistoryLine.FORMAT_VERSION,
                line -> PlanHistoryCodec.read(file, line)), content.tornTailOffset());
    }

    /**
     * Adds one line as an append target to a transaction of the plan; it is written when the caller commits the
     * transaction.
     *
     * @param transaction the open transaction of the plan
     * @param line        the line
     * @throws NullPointerException if an argument is {@code null}
     */
    public void append(ScopeTransaction transaction, PlanHistoryLine line) {
        Objects.requireNonNull(line, "line");
        append(transaction, List.of(line));
    }

    /**
     * Adds the lines of one transition as one append target, so the state change and every line it writes are one
     * journaled write. A transaction carries one append target per store, so a caller that runs several transitions in
     * one transaction collects their lines and appends them once, and numbers them from {@link #nextSeq()} in the order
     * of the transition.
     *
     * @param transaction the open transaction of the plan
     * @param lines       the lines, in the order of the transition
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the lines are empty
     */
    public void append(ScopeTransaction transaction, List<PlanHistoryLine> lines) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a plan history append names at least one line");
        }
        WaiverLog.requireOwnScope(transaction, scope, "plan history");
        var block = new ByteArrayOutputStream();
        lines.forEach(line -> block.writeBytes(WaiverLog.line(PlanHistoryCodec.write(line))));
        transaction.append(FILE_NAME, block.toByteArray());
    }

    /**
     * @return the sequence number the next line takes: one above the last readable line, {@code 1} for an empty history
     * @throws UncheckedIOException if the history exists and cannot be read
     */
    public long nextSeq() {
        var lines = read().records();
        return lines.isEmpty() ? 1 : lines.getLast().seq() + 1;
    }

    /**
     * What a history holds: one entry per complete line, in the order of the log.
     *
     * @param store          the history file
     * @param entries        one entry per complete line, in the order of the log
     * @param tornTailOffset the byte offset where a last line without a newline begins; empty if there is none
     * @since 0.1
     */
    public record Lines(Path store, List<Entry<PlanHistoryLine>> entries, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Lines {
            Objects.requireNonNull(store, "store");
            entries = List.copyOf(entries);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /** @return the lines that were read, in the order of the log, without the refused lines */
        public List<PlanHistoryLine> records() {
            return entries.stream().filter(entry -> entry instanceof Accepted<PlanHistoryLine>)
                    .map(entry -> ((Accepted<PlanHistoryLine>) entry).value()).toList();
        }

        /** @return the refused lines with their line numbers and refusals, in the order of the log */
        public List<Refused<PlanHistoryLine>> refused() {
            return entries.stream().filter(entry -> entry instanceof Refused<PlanHistoryLine>)
                    .map(entry -> (Refused<PlanHistoryLine>) entry).toList();
        }
    }
}
