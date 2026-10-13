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
import java.util.Optional;
import java.util.OptionalLong;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Entry;
import de.planmarshall.core.store.JsonlStoreFile.Refused;

/**
 * The scope session log {@code sessions.jsonl} of one plan, epic or workspace scope (PM-ARCH-4, PM-CLIENT-3): one
 * {@link ScopeSessionRow} per {@code pm-mcp enter} on the scope, appended and never changed.
 * <p>
 * The log lies at the path base of its scope: {@code plans/<plan_id>/sessions.jsonl},
 * {@code epics/<epic_id>/sessions.jsonl}, or {@code workspace/sessions.jsonl} of the workspace. An append is never
 * committed on its own: it adds one row as an append target to the {@link ScopeTransaction} of the caller, which holds
 * the lock of the scope, so the row is journaled, a torn tail is repaired first, and the digest sidecar of the scope
 * covers the log. When the row declares another client than the latest row of the log, the same transaction owes the
 * {@code entry_client_changed} record, so the row and its record are one transaction: the record is handed over once
 * the row is applied, or by the next recovery of the scope.
 * <p>
 * The append reads the latest row while the transaction holds the lock of the scope, so no other append comes between
 * the comparison and the write. A read without the lock ({@link #read()}) reports the log as it is at that moment: an
 * append in progress shows as a torn tail and never as a row, and a line that is refused is reported with its line
 * number while the rows around it stay readable. Nothing reads the log to decide anything else: it is history and
 * audit data.
 *
 * @since 0.1
 */
public final class ScopeSessionLog {

    /** The file name of the session log of a scope. */
    public static final String FILE_NAME = "sessions.jsonl";

    private static final byte NEWLINE = '\n';

    private final JournalScope scope;
    private final String storePath;

    /**
     * What a read of the log found: one entry per complete line, each a row or a refused line, and a torn tail apart
     * from them.
     *
     * @param store          the log that was read
     * @param entries        one entry per complete line, in the order of the log
     * @param tornTailOffset the byte offset where a last line without a newline begins; empty if there is none
     * @since 0.1
     */
    public record Rows(Path store, List<Entry<ScopeSessionRow>> entries, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Rows {
            Objects.requireNonNull(store, "store");
            entries = List.copyOf(entries);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /** @return the rows that were read, in the order of the log, without the refused lines */
        public List<ScopeSessionRow> rows() {
            return entries.stream().filter(entry -> entry instanceof Accepted<ScopeSessionRow>)
                    .map(entry -> ((Accepted<ScopeSessionRow>) entry).value()).toList();
        }

        /** @return the refused lines with their line numbers and refusals, in the order of the log */
        public List<Refused<ScopeSessionRow>> refused() {
            return entries.stream().filter(entry -> entry instanceof Refused<ScopeSessionRow>)
                    .map(entry -> (Refused<ScopeSessionRow>) entry).toList();
        }

        /** @return the last row that was read; empty if the log has none */
        public Optional<ScopeSessionRow> latest() {
            var rows = rows();
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getLast());
        }
    }

    /**
     * @param scope the plan, epic or workspace scope whose log this is
     * @throws IllegalArgumentException if the scope is the lesson store, which has no session log
     */
    public ScopeSessionLog(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.storePath = switch (scope.type()) {
            case PLAN, EPIC -> FILE_NAME;
            case WORKSPACE -> "workspace/" + FILE_NAME;
            case LESSONS -> throw new IllegalArgumentException("The lesson store has no session log");
        };
    }

    /** @return the log file, absolute and normalized */
    public Path store() {
        return scope.resolve(storePath);
    }

    /** @return the path of the log relative to the path base of its scope, as its journal and sidecar name it */
    public String storePath() {
        return storePath;
    }

    /**
     * Reads the log without a lock. A torn tail is reported, never read as a row, and a refused line keeps its line
     * number; nothing is repaired.
     *
     * @return what the log holds; no entry if it does not exist
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public Rows read() {
        var file = store();
        var content = new JsonlStoreFile(file, scope.lockKey()).snapshot();
        return new Rows(file, content.decode(ScopeSessionRow.FORMAT_VERSION,
                line -> ScopeSessionRowCodec.read(file, line)), content.tornTailOffset());
    }

    /**
     * Reads the log without a lock and returns its last row, for the caller that compares the client a session
     * declares with the client of the latest entry. The append decides on its own read under the lock.
     *
     * @return the last row of the log; empty if it has none
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public Optional<ScopeSessionRow> latest() {
        return read().latest();
    }

    /**
     * Adds one row as an append target to a transaction of the scope; it is written when the caller commits the
     * transaction. When the row declares another client than the latest row of the log, the transaction owes the
     * {@code entry_client_changed} record with the previous and the new client and client session id; the first row
     * of a log and a row of the same client owe none.
     *
     * @param transaction an open transaction of this log's scope, opened by the caller's lock transaction
     * @param row         the entry row
     * @return the {@code entry_client_changed} record the transaction owes for the row; empty if it owes none
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the log is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     * @throws UncheckedIOException     if the log exists and cannot be read
     */
    public Optional<AuditEvent> append(ScopeTransaction transaction, ScopeSessionRow row) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(row, "row");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("A transaction of %s does not write the session log of %s"
                    .formatted(transaction.scope().pathBase(), scope.pathBase()));
        }
        var line = ScopeSessionRowCodec.write(row);
        var block = new byte[line.length + 1];
        System.arraycopy(line, 0, block, 0, line.length);
        block[line.length] = NEWLINE;
        transaction.append(storePath, block);
        var owed = latest().filter(row::clientDiffersFrom)
                .map(previous -> AuditEvent.entryClientChanged(previous.client().wireName(), row.client().wireName(),
                        previous.clientSessionId(), row.clientSessionId(), transaction.transactionId().value()));
        owed.ifPresent(transaction::owe);
        return owed;
    }
}
