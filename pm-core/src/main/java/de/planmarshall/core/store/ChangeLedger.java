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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;

/**
 * The writer and reader of a {@code change-ledger.jsonl} (Store Schemas § Change Ledger Record): the append-only ledger
 * of a plan, an epic or the workspace, one {@link ChangeLedgerRecord} per line.
 * <p>
 * A row is never rewritten. An append is one short transaction on the lock of the scope: the append-only store repairs
 * a torn tail, writes the record and forces the file to disk before the lock is released, so a reader sees complete
 * lines only. The ledger carries the {@code worktree_sha} every freshness gate is keyed by, and it preserves the
 * order in which the runtime observed the rows.
 *
 * @since 0.1
 */
public final class ChangeLedger {

    private static final Set<LockLevel> SCOPE_LEVELS =
            EnumSet.of(LockLevel.PLAN, LockLevel.EPIC, LockLevel.WORKSPACE);

    private final JsonlStoreFile storeFile;
    private final LockManager lockManager;
    private final AuditSink auditSink;

    /**
     * @param storeFile   the ledger file with the key of the lock of its scope
     * @param lockManager the lock manager of the runtime
     * @param auditSink   where the record of a tail repair is handed over
     * @throws IllegalArgumentException if the key of the ledger file is not the lock of a plan, an epic or the
     *                                  workspace
     */
    public ChangeLedger(JsonlStoreFile storeFile, LockManager lockManager, AuditSink auditSink) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        if (!SCOPE_LEVELS.contains(storeFile.lockKey().level())) {
            throw new IllegalArgumentException(
                    "A change ledger is guarded by the lock of its scope: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Appends one record in one transaction on the lock of the ledger.
     *
     * @param record the record
     * @throws StoreRefusal           if a torn tail cannot be repaired
     * @throws UncheckedIOException   if the ledger cannot be appended to
     * @throws InternalFaultException if the lock of the ledger must not be acquired by the transaction
     */
    public void append(ChangeLedgerRecord record) {
        Objects.requireNonNull(record, "record");
        var line = new String(ChangeLedgerCodec.write(record), StandardCharsets.UTF_8) + "\n";
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            storeFile.append(transaction, line.getBytes(StandardCharsets.UTF_8), auditSink);
        }
    }

    /**
     * Reads the ledger without a lock. A line that is unreadable or of another format version stays in the ledger and
     * is reported with its line number, while the other records stay readable.
     *
     * @return one entry per complete line, in the order of the ledger
     * @throws UncheckedIOException if the ledger exists and cannot be read
     */
    public List<JsonlStoreFile.Entry<ChangeLedgerRecord>> read() {
        return storeFile.snapshot().decode(ChangeLedgerRecord.FORMAT_VERSION,
                line -> ChangeLedgerCodec.read(storeFile.store(), line));
    }

    /** @return the ledger file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
