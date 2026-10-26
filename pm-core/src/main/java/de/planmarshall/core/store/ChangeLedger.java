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

/**
 * The writer and reader of a {@code change-ledger.jsonl} (Store Schemas § Change Ledger Record): the append-only ledger
 * of a plan, an epic or the workspace, one {@link ChangeLedgerRecord} per line.
 * <p>
 * A row is never rewritten. An append is never committed on its own: it adds the line as one append target to the
 * {@link ScopeTransaction} of the caller, which holds the lock of the scope, so the line is journaled, a torn tail is
 * repaired first, and the digest sidecar of the scope covers the ledger. The ledger carries the {@code worktree_sha}
 * every freshness gate is keyed by, and it preserves the order in which the runtime observed the rows.
 *
 * @since 0.1
 */
public final class ChangeLedger {

    /** The file name of the change ledger of a plan or an epic scope. */
    public static final String FILE_NAME = "change-ledger.jsonl";

    private static final String WORKSPACE_DIRECTORY = "workspace";

    private final JournalScope scope;
    private final String storePath;

    /**
     * @param scope the plan, epic or workspace scope whose ledger this is
     * @throws IllegalArgumentException if the scope is the lesson store, which has no change ledger
     */
    public ChangeLedger(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.storePath = switch (scope.type()) {
            case PLAN, EPIC -> FILE_NAME;
            case WORKSPACE -> Path.of(WORKSPACE_DIRECTORY, FILE_NAME).toString();
            case MACHINE -> FILE_NAME;
            case LESSONS -> throw new IllegalArgumentException("The lesson store has no change ledger");
        };
    }

    /**
     * Adds one record as an append target to a transaction of the scope; it is written when the caller commits the
     * transaction.
     *
     * @param transaction an open transaction of this ledger's scope, opened by the caller's lock transaction
     * @param row         the record
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the ledger is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void append(ScopeTransaction transaction, ChangeLedgerRecord row) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(row, "row");
        WaiverLog.requireOwnScope(transaction, scope, "change ledger");
        transaction.append(storePath, WaiverLog.line(ChangeLedgerCodec.write(row)));
    }

    /**
     * Reads the ledger without a lock. A line that is unreadable or of another format version stays in the ledger and
     * is reported with its line number, while the other records stay readable.
     *
     * @return one entry per complete line, in the order of the ledger
     * @throws UncheckedIOException if the ledger exists and cannot be read
     */
    public List<JsonlStoreFile.Entry<ChangeLedgerRecord>> read() {
        var file = store();
        return new JsonlStoreFile(file, scope.lockKey()).snapshot().decode(ChangeLedgerRecord.FORMAT_VERSION,
                line -> ChangeLedgerCodec.read(file, line));
    }

    /** @return the ledger file, absolute and normalized */
    public Path store() {
        return scope.resolve(storePath);
    }

    /** @return the path of the ledger relative to the path base of its scope, as its journal and sidecar name it */
    public String storePath() {
        return storePath;
    }
}
