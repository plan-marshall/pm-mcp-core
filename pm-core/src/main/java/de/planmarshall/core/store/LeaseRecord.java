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
 * One lease in a lease store (PM-IMPL-7): a long-lived claim that is a persisted record and never a lock of the
 * operating system. It is claimed and released inside a short transaction on the lock of its store, so holding a
 * lease while taking another lock does not invert the lock order.
 *
 * @param key            what the lease is a claim on; unique within its store
 * @param owner          the scope that owns the lease and the runtime that serves it
 * @param acquiredAt     the instant the lease was claimed
 * @param leaseExpiresAt the instant the lease expires; {@code null} for a lease without a time bound
 * @since 0.1
 */
public record LeaseRecord(String key, LeaseOwner owner, Instant acquiredAt, Instant leaseExpiresAt) {

    /**
     * @throws NullPointerException     if the key, the owner or the instant of the claim is {@code null}
     * @throws IllegalArgumentException if the key is blank
     */
    public LeaseRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(acquiredAt, "acquired_at");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    /**
     * @param newOwner the owner of the lease from now on
     * @return this lease with the new owner
     */
    public LeaseRecord withOwner(LeaseOwner newOwner) {
        return new LeaseRecord(key, newOwner, acquiredAt, leaseExpiresAt);
    }
}
