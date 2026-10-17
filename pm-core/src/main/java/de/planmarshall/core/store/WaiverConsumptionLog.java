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
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Entry;
import de.planmarshall.core.store.JsonlStoreFile.Refused;

/**
 * The waiver consumption log {@code plans/<plan_id>/waiver-consumptions.jsonl} of one plan (PM-SEC-3, PM-IMPL-2,
 * PM-IMPL-7): one {@link WaiverConsumptionRecord} per evaluation of a waivable gate that relied on a {@code met}
 * waiver, appended and never changed.
 * <p>
 * Only a plan has a consumption log. An append is never committed on its own: it adds one line as an append target to
 * the {@link ScopeTransaction} of the caller, which holds the lock of the plan. Every consumption is a line of its own,
 * so two evaluations that relied on one waiver are two lines, and no method of this type rewrites or removes a line.
 * That {@code waiver.lookup} is the only writer is the rule of its caller.
 * <p>
 * A read without the lock ({@link #read()}) reports the log as it is at that moment: an append in progress shows as a
 * torn tail and never as a line, and a refused line keeps its line number while the lines around it stay readable.
 *
 * @since 0.1
 */
public final class WaiverConsumptionLog {

    /** The file name of the waiver consumption log of a plan. */
    public static final String FILE_NAME = "waiver-consumptions.jsonl";

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
    public record Lines(Path store, List<Entry<WaiverConsumptionRecord>> entries, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Lines {
            Objects.requireNonNull(store, "store");
            entries = List.copyOf(entries);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /** @return the records that were read, in the order of the log, without the refused lines */
        public List<WaiverConsumptionRecord> records() {
            return entries.stream().filter(entry -> entry instanceof Accepted<WaiverConsumptionRecord>)
                    .map(entry -> ((Accepted<WaiverConsumptionRecord>) entry).value()).toList();
        }

        /** @return the refused lines with their line numbers and refusals, in the order of the log */
        public List<Refused<WaiverConsumptionRecord>> refused() {
            return entries.stream().filter(entry -> entry instanceof Refused<WaiverConsumptionRecord>)
                    .map(entry -> (Refused<WaiverConsumptionRecord>) entry).toList();
        }

        /**
         * @param waiverId the id of a waiver
         * @return the consumptions of the waiver, in the order of the log; empty if it has none
         */
        public List<WaiverConsumptionRecord> of(String waiverId) {
            Objects.requireNonNull(waiverId, "waiverId");
            return records().stream().filter(consumption -> consumption.waiverId().equals(waiverId)).toList();
        }
    }

    /**
     * @param scope the plan scope whose log this is
     * @throws IllegalArgumentException if the scope is not a plan
     */
    public WaiverConsumptionLog(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException(
                    "Only a plan has a waiver consumption log, not a " + scope.type().wireName());
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
        return new Lines(file, content.decode(WaiverConsumptionRecord.FORMAT_VERSION,
                line -> WaiverConsumptionCodec.read(file, line)), content.tornTailOffset());
    }

    /**
     * Adds one line as an append target to a transaction of the plan; it is written when the caller commits the
     * transaction.
     *
     * @param transaction an open transaction of this log's plan, opened by the caller's lock transaction
     * @param consumption the line
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the log is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void append(ScopeTransaction transaction, WaiverConsumptionRecord consumption) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(consumption, "consumption");
        WaiverLog.requireOwnScope(transaction, scope, "waiver consumption log");
        transaction.append(FILE_NAME, WaiverLog.line(WaiverConsumptionCodec.write(consumption)));
    }
}
