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
import java.util.Objects;
import java.util.Optional;

/**
 * The workflow state store {@code mcp/status.json} of one plan, epic or workspace scope (PM-ARCH-6: the persisted
 * state machine position; PM-ARCH-4: every run state lives in the store, so a killed run resumes where it stopped).
 * <p>
 * A read takes no lock: it reads the store as it is at that moment ({@link StoreSnapshot}), which is the content
 * before or after a write and never a part of one. A store that does not exist is reported as absent, never as an
 * empty status. A write never commits on its own: it adds the whole document as one replace target to the
 * {@link ScopeTransaction} of the caller, which holds the lock of the scope, so a state change and the writes of the
 * other stores of the same scope are one journaled transaction.
 * <p>
 * The store lies in the scope metadata directory: {@code mcp/status.json} of a plan, {@code status.json} of an epic
 * (whose metadata directory is the epic directory itself), {@code workspace/mcp/status.json} of the workspace.
 *
 * @since 0.1
 */
public final class WorkflowStatusStore {

    private final JournalScope scope;
    private final String storePath;

    /**
     * @param scope the plan, epic or workspace scope whose status this store holds
     * @throws IllegalArgumentException if the scope is the lesson store, which has no status
     */
    public WorkflowStatusStore(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.storePath = switch (scope.type()) {
            case PLAN -> "mcp/status.json";
            case EPIC -> "status.json";
            case WORKSPACE -> "workspace/mcp/status.json";
            case MACHINE -> "mcp/status.json";
            case LESSONS -> throw new IllegalArgumentException("The lesson store has no status document");
        };
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return scope.resolve(storePath);
    }

    /**
     * Reads the status without a lock.
     *
     * @return the status of the scope; empty if the store does not exist
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<WorkflowStatus> read() {
        var file = store();
        return StoreSnapshot.read(file).content().map(document -> WorkflowStatusCodec.read(file, document));
    }

    /**
     * Adds the status as the new content of the store to a transaction of the scope; it is written when the caller
     * commits the transaction.
     *
     * @param transaction an open transaction of this store's scope, opened by the caller's lock transaction
     * @param status      the new status
     * @throws IllegalArgumentException if the status belongs to another scope, or the store is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void write(ScopeTransaction transaction, WorkflowStatus status) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(status, "status");
        if (status.scopeType() != scope.type() || !Objects.equals(status.scopeId(), scope.scopeId())) {
            throw new IllegalArgumentException("The status of the %s scope %s is not the status of the %s scope %s"
                    .formatted(status.scopeType().wireName(), status.scopeId(), scope.type().wireName(),
                            scope.scopeId()));
        }
        transaction.replace(storePath, WorkflowStatusCodec.write(status));
    }
}
