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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Entry;
import de.planmarshall.core.store.JsonlStoreFile.Refused;

/**
 * The waiver log {@code plans/<plan_id>/waivers.jsonl} of one plan (PM-SEC-3, PM-IMPL-2, PM-IMPL-7): one
 * {@link WaiverRecord} per operator act that sets or revokes a waiver, appended and never changed.
 * <p>
 * Only a plan has a waiver log. An append is never committed on its own: it adds one line as an append target to the
 * {@link ScopeTransaction} of the caller, which holds the lock of the plan, so the line is journaled, a torn tail is
 * repaired first, and the digest sidecar of the plan covers the log. No method of this type rewrites or removes a
 * line: a revocation is a new {@link WaiverRecord.Event#REVOKED} line, and the line it revokes stays as it was. That the
 * operator's act is the only writer is the rule of the caller that executes the act.
 * <p>
 * A read without the lock ({@link #read()}) reports the log as it is at that moment: an append in progress shows as a
 * torn tail and never as a line, and a line that is refused is reported with its line number while the lines around it
 * stay readable. The current state of a waiver is the last readable line of its id, taken whole; a torn last line is
 * no version of its waiver, so the line before it stays current.
 *
 * @since 0.1
 */
public final class WaiverLog {

    /** The file name of the waiver log of a plan. */
    public static final String FILE_NAME = "waivers.jsonl";

    private final JournalScope scope;

    /**
     * What a read of the log found: one entry per complete line, each a record or a refused line, and a torn tail
     * apart from them.
     *
     * @param store          the log that was read
     * @param entries        one entry per complete line, in the order of the log
     * @param tornTailOffset the byte offset where a last line without a newline begins; empty if there is none
     * @since 0.1
     */
    public record Lines(Path store, List<Entry<WaiverRecord>> entries, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Lines {
            Objects.requireNonNull(store, "store");
            entries = List.copyOf(entries);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /** @return the records that were read, in the order of the log, without the refused lines */
        public List<WaiverRecord> records() {
            return entries.stream().filter(entry -> entry instanceof Accepted<WaiverRecord>)
                    .map(entry -> ((Accepted<WaiverRecord>) entry).value()).toList();
        }

        /** @return the refused lines with their line numbers and refusals, in the order of the log */
        public List<Refused<WaiverRecord>> refused() {
            return entries.stream().filter(entry -> entry instanceof Refused<WaiverRecord>)
                    .map(entry -> (Refused<WaiverRecord>) entry).toList();
        }

        /**
         * @param waiverId the id of a waiver
         * @return the last readable line of the waiver, its current state; empty if the log has none
         */
        public Optional<WaiverRecord> current(String waiverId) {
            Objects.requireNonNull(waiverId, "waiverId");
            return records().reversed().stream().filter(waiver -> waiver.waiverId().equals(waiverId)).findFirst();
        }

        /** @return the current state of every waiver of the log, by its id, in the order the ids first occur */
        public Map<String, WaiverRecord> currentWaivers() {
            var current = new LinkedHashMap<String, WaiverRecord>();
            records().forEach(waiver -> current.put(waiver.waiverId(), waiver));
            return Collections.unmodifiableMap(current);
        }
    }

    /**
     * @param scope the plan scope whose log this is
     * @throws IllegalArgumentException if the scope is not a plan
     */
    public WaiverLog(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("Only a plan has a waiver log, not a " + scope.type().wireName());
        }
    }

    /** @return the log file, absolute and normalized */
    public Path store() {
        return scope.resolve(FILE_NAME);
    }

    /** @return the path of the log relative to the path base of its plan, as its journal and sidecar name it */
    public String storePath() {
        return FILE_NAME;
    }

    /**
     * Reads the log without a lock. A torn tail is reported, never read as a line, and a refused line keeps its line
     * number; nothing is repaired.
     *
     * @return what the log holds; no entry if it does not exist
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public Lines read() {
        var file = store();
        var content = new JsonlStoreFile(file, scope.lockKey()).snapshot();
        return new Lines(file, content.decode(WaiverRecord.FORMAT_VERSION, line -> WaiverRecordCodec.read(file, line)),
                content.tornTailOffset());
    }

    /**
     * Adds one line as an append target to a transaction of the plan; it is written when the caller commits the
     * transaction.
     *
     * @param transaction an open transaction of this log's plan, opened by the caller's lock transaction
     * @param waiver      the line
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the log is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void append(ScopeTransaction transaction, WaiverRecord waiver) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(waiver, "waiver");
        requireOwnScope(transaction, scope, "waiver log");
        transaction.append(FILE_NAME, line(WaiverRecordCodec.write(waiver)));
    }

    /**
     * @param transaction a transaction
     * @param scope       the scope of a log
     * @param log         the name of the log in a refusal
     * @throws IllegalArgumentException if the transaction belongs to another scope
     */
    static void requireOwnScope(ScopeTransaction transaction, JournalScope scope, String log) {
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("A transaction of %s does not write the %s of %s"
                    .formatted(transaction.scope().pathBase(), log, scope.pathBase()));
        }
    }

    /**
     * @param record the JSON object of one line, without a newline
     * @return the line, ending with a newline
     */
    static byte[] line(byte[] record) {
        var block = new byte[record.length + 1];
        System.arraycopy(record, 0, block, 0, record.length);
        block[record.length] = '\n';
        return block;
    }
}
