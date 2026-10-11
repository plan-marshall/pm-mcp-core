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

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;

/**
 * The one check every store access under a lock runs first (PM-IMPL-7): the transaction holds the key that guards
 * the store, or the access is an internal fault and touches nothing.
 *
 * @since 0.1
 */
@UtilityClass
class StoreLockGuard {

    /**
     * @param transaction the transaction that accesses the store
     * @param store       the store, named by the fault
     * @param lockKey     the key that guards the store
     * @throws InternalFaultException if the transaction does not hold the key, with the reason
     *                                {@link Reason#STORE_LOCK_NOT_HELD}
     */
    static void requireHeld(LockTransaction transaction, Path store, LockKey lockKey) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.holds(lockKey)) {
            throw new InternalFaultException(Reason.STORE_LOCK_NOT_HELD,
                    "Store '%s' accessed without its lock '%s'".formatted(store, lockKey.lockFile()),
                    Map.of("store", store.toString(), "lock", lockKey.lockFile().toString()));
        }
    }
}
