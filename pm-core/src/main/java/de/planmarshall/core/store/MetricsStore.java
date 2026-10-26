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
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The writer and reader of a {@code metrics.json} (Store Schemas § Metrics &amp; Quality Report): the rows of a task
 * and of a worker's idle turns, in the metrics store of the task's scope.
 * <p>
 * A write never commits on its own: it adds the whole document as one replace target to the {@link ScopeTransaction} of
 * the caller, which holds the lock of the scope, so the digest sidecar of the scope covers {@code metrics.json} like
 * every other store written by a transaction of its scope. An append is decided while that lock is held: it reads the
 * document, checks that the row is not there yet, adds it and stages the document back. The rows are what a job's
 * settlement writes and what {@code record-metrics} reads; no aggregate is persisted, because every aggregate is
 * derived from the rows.
 * <p>
 * One row per attempt: a second row for the same {@code queue_task_id} and {@code attempt}, or a second worker-idle
 * row for the same {@code job_id}, is refused rather than silently doubling a figure. A row of another kind that the
 * document already holds is kept verbatim (see {@link MetricsCodec}).
 * <p>
 * Each scope has its own store and its own partition of the rows (Store Schemas § Metrics Store, Scope): a plan's file
 * holds its task rows and records their {@code phase}; the epic and workspace files hold only task rows with
 * {@code phase: null}; the workspace file also holds the {@code worker_idle} rows of the project's warm workers, which
 * serve every scope and are reported apart, never in a plan's figures. A row of another partition is refused.
 *
 * @since 0.1
 */
public final class MetricsStore {

    /** The format version of a metrics store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The file name of the metrics store of a plan or an epic scope. */
    public static final String FILE_NAME = "metrics.json";

    private static final String WORKSPACE_DIRECTORY = "workspace";

    private final JournalScope scope;
    private final String storePath;

    /**
     * @param scope the plan, epic or workspace scope whose metrics store this is
     * @throws IllegalArgumentException if the scope is the lesson store, which has no metrics store
     */
    public MetricsStore(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.storePath = switch (scope.type()) {
            case PLAN, EPIC -> FILE_NAME;
            case WORKSPACE -> Path.of(WORKSPACE_DIRECTORY, FILE_NAME).toString();
            case LESSONS -> throw new IllegalArgumentException("The lesson store has no metrics store");
        };
    }

    /**
     * Adds one task row as the new content of the store to a transaction of the scope; it is written when the caller
     * commits the transaction.
     *
     * @param transaction an open transaction of this store's scope, opened by the caller's lock transaction
     * @param row         the row
     * @throws IllegalArgumentException if the row is outside the partition of the store's scope, the document holds a
     *                                  row for the same task and attempt already, or the transaction belongs to
     *                                  another scope or holds the store as a target already
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws IllegalStateException    if the transaction was committed
     * @throws UncheckedIOException     if the store cannot be read
     */
    public void append(ScopeTransaction transaction, MetricsRow row) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(row, "row");
        WaiverLog.requireOwnScope(transaction, scope, "metrics store");
        requireTaskRowOfThisScope(row);
        update(transaction, document -> {
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
     * Adds one worker-idle row as the new content of the store to a transaction of the workspace scope; it is written
     * when the caller commits the transaction.
     *
     * @param transaction an open transaction of this store's scope, opened by the caller's lock transaction
     * @param row         the row
     * @throws IllegalArgumentException if the store is not the workspace's, the document holds a worker-idle row for
     *                                  the same worker already, or the transaction belongs to another scope or holds
     *                                  the store as a target already
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws IllegalStateException    if the transaction was committed
     * @throws UncheckedIOException     if the store cannot be read
     */
    public void append(ScopeTransaction transaction, WorkerIdleRow row) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(row, "row");
        WaiverLog.requireOwnScope(transaction, scope, "metrics store");
        requireIdleRowOfThisScope();
        update(transaction, document -> {
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
        return StoreSnapshot.read(store()).content().map(this::decode).orElseGet(MetricsCodec.Document::empty);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return scope.resolve(storePath);
    }

    /** @return the path of the store relative to the path base of its scope, as its journal and sidecar name it */
    public String storePath() {
        return storePath;
    }

    private void requireTaskRowOfThisScope(MetricsRow row) {
        if (scope.type() != JournalScopeType.PLAN && row.phase() != null) {
            throw new IllegalArgumentException(
                    "The metrics store of the %s holds only task rows with no phase, not the phase %s"
                            .formatted(scopeName(), row.phase().wireName()));
        }
    }

    private void requireIdleRowOfThisScope() {
        if (scope.type() != JournalScopeType.WORKSPACE) {
            throw new IllegalArgumentException(
                    "The worker-idle rows of the project's warm workers belong to the workspace metrics store, not to "
                            + "the " + scopeName());
        }
    }

    /** The store's scope, named in a refusal: the type and its id where it has one. */
    private String scopeName() {
        return scope.scopeId() == null ? scope.type().wireName()
                : "%s '%s'".formatted(scope.type().wireName(), scope.scopeId());
    }

    private void update(ScopeTransaction transaction, UnaryOperator<MetricsCodec.Document> change) {
        var before = snapshot();
        var after = change.apply(before);
        if (!after.equals(before)) {
            transaction.replace(storePath, MetricsCodec.write(after));
        }
    }

    private MetricsCodec.Document decode(byte[] document) {
        return MetricsCodec.read(store(), document);
    }
}
