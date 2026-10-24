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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;

/**
 * The writer and reader of a {@code metrics.json} (Store Schemas § Metrics &amp; Quality Report): the rows of a task
 * and of a worker's idle turns, in the metrics store of the task's scope.
 * <p>
 * The store is replaced by atomic rename and guarded by the transaction lock of its scope, so an append is one
 * short transaction: it reads the document, checks that the row is not there yet, adds it and writes the document
 * back. The rows are what a job's settlement writes and what {@code record-metrics} reads; no aggregate is persisted,
 * because every aggregate is derived from the rows.
 * <p>
 * One row per attempt: a second row for the same {@code queue_task_id} and {@code attempt}, or a second worker-idle
 * row for the same {@code job_id}, is refused rather than silently doubling a figure. A row of another kind that the
 * document already holds is kept verbatim (see {@link MetricsCodec}).
 *
 * @since 0.1
 */
public final class MetricsStore {

    /** The format version of a metrics store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Set<LockLevel> SCOPE_LEVELS = EnumSet.of(LockLevel.PLAN, LockLevel.EPIC, LockLevel.WORKSPACE);

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * @param storeFile   the metrics store file with the key of the lock of its scope
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not the lock of a plan, an epic or the workspace
     */
    public MetricsStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (!SCOPE_LEVELS.contains(storeFile.lockKey().level())) {
            throw new IllegalArgumentException(
                    "A metrics store is guarded by the lock of its scope: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Appends one task row in one transaction on the lock of the store.
     *
     * @param row the row
     * @throws IllegalArgumentException if the document holds a row for the same task and attempt already
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws UncheckedIOException     if the store cannot be read or written
     * @throws InternalFaultException   if the lock of the store must not be acquired by the transaction
     */
    public void append(MetricsRow row) {
        Objects.requireNonNull(row, "row");
        update(document -> {
            for (var existing : document.taskRows()) {
                if (existing.queueTaskId().equals(row.queueTaskId())
                        && Objects.equals(existing.attempt(), row.attempt())) {
                    throw new IllegalArgumentException(
                            "The metrics store holds the row of the task '%s' and attempt %s already"
                                    .formatted(row.queueTaskId(), row.attempt()));
                }
            }
            var tasks = new ArrayList<>(document.taskRows());
            tasks.add(row);
            return new MetricsCodec.Document(tasks, document.idleRows(), document.foreignRows());
        });
    }

    /**
     * Appends one worker-idle row in one transaction on the lock of the store.
     *
     * @param row the row
     * @throws IllegalArgumentException if the document holds a worker-idle row for the same worker already
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws UncheckedIOException     if the store cannot be read or written
     * @throws InternalFaultException   if the lock of the store must not be acquired by the transaction
     */
    public void append(WorkerIdleRow row) {
        Objects.requireNonNull(row, "row");
        update(document -> {
            for (var existing : document.idleRows()) {
                if (existing.jobId().equals(row.jobId())) {
                    throw new IllegalArgumentException(
                            "The metrics store holds the idle row of the worker '%s' already".formatted(row.jobId()));
                }
            }
            var idle = new ArrayList<>(document.idleRows());
            idle.add(row);
            return new MetricsCodec.Document(document.taskRows(), idle, document.foreignRows());
        });
    }

    /**
     * Reads the document without a lock, as it is at the moment of the read. A write replaces the store in one step,
     * so the result is the document before or after a write and never a part of one.
     *
     * @return the rows of the store; the empty document if the store does not exist
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public MetricsCodec.Document snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode)
                .orElseGet(MetricsCodec.Document::empty);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private void update(UnaryOperator<MetricsCodec.Document> change) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElseGet(MetricsCodec.Document::empty);
            var after = change.apply(before);
            if (!after.equals(before)) {
                storeFile.write(transaction, MetricsCodec.write(after));
            }
        }
    }

    private MetricsCodec.Document decode(byte[] document) {
        return MetricsCodec.read(storeFile.store(), document);
    }
}
