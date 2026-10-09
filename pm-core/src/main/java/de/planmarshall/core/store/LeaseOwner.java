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

import java.time.Instant;
import java.util.Objects;

/**
 * The owner of a lease (PM-IMPL-7). A lease is owned by its scope, never by the runtime that serves it: the recovery
 * of a project selects its leases by the project id, and {@link #holderInstance()} records only which runtime serves
 * the lease at the moment.
 * <p>
 * A lease whose holder is no longer alive at a runtime start is not released but marked as orphaned; the next call
 * that addresses the owning scope adopts it, which names the new runtime as holder and clears the mark.
 *
 * @param projectId      the enrolment identifier of the owning project
 * @param scopeType      the kind of the owning scope
 * @param scopeId        the plan id or epic id; {@code null} for the workspace scope and for the project
 * @param holderInstance the runtime instance that serves the lease
 * @param orphanedAt     the instant the holder was found dead at a runtime start; {@code null} unless orphaned
 * @since 0.1
 */
public record LeaseOwner(String projectId, ScopeType scopeType, String scopeId, HolderInstance holderInstance,
        Instant orphanedAt) {

    /**
     * @throws NullPointerException     if the project id, the scope type or the holder is {@code null}, or the scope
     *                                  id of a plan or an epic
     * @throws IllegalArgumentException if the project id or the scope id is not a valid identifier, or a scope id is
     *                                  given for the workspace scope or the project
     */
    public LeaseOwner {
        LockKey.identifier(projectId, "project_id");
        Objects.requireNonNull(scopeType, "scope_type");
        Objects.requireNonNull(holderInstance, "holder_instance");
        if (scopeType.hasScopeId()) {
            LockKey.identifier(scopeId, "scope_id");
        } else if (scopeId != null) {
            throw new IllegalArgumentException(
                    "scope_id must be null for the scope type '%s': %s".formatted(scopeType.wireName(), scopeId));
        }
    }

    /** @return whether the holder was found dead and no call has adopted the lease since */
    public boolean isOrphaned() {
        return orphanedAt != null;
    }

    /**
     * @param newHolder the runtime instance that serves the lease from now on
     * @return this owner served by the new holder and no longer orphaned
     */
    public LeaseOwner adoptedBy(HolderInstance newHolder) {
        return new LeaseOwner(projectId, scopeType, scopeId, newHolder, null);
    }

    /**
     * @param at the instant the holder was found dead
     * @return this owner marked as orphaned at the instant
     */
    public LeaseOwner orphaned(Instant at) {
        return new LeaseOwner(projectId, scopeType, scopeId, holderInstance, Objects.requireNonNull(at, "at"));
    }
}
