/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import de.planmarshall.core.store.LockKey;

/**
 * The locks one transaction holds (PM-IMPL-7). A transaction is used by one thread and closed by it, typically in a
 * try-with-resources statement.
 * <p>
 * The order assertion and the acquisition are the one call {@link #acquire(LockKey)}; there is no separate check a
 * caller could run first, so no other acquisition can come between the check and the lock.
 *
 * @since 0.1
 */
public interface LockTransaction extends AutoCloseable {

    /**
     * Asserts the total lock order against the keys this transaction holds and takes the lock, waiting for it if
     * another transaction holds it. A key this transaction already holds is not taken again.
     *
     * @param key the key to acquire
     * @throws InternalFaultException if the key must not be acquired after the keys this transaction holds; nothing
     *                                was acquired then
     */
    void acquire(LockKey key);

    /**
     * @param key a key
     * @return whether this transaction holds the key
     */
    boolean holds(LockKey key);

    /**
     * Releases every lock this transaction holds, the last acquired first. Closing a closed transaction does
     * nothing.
     */
    @Override
    void close();
}
