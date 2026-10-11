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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;

/**
 * A lock manager for the tests of the stores: it takes no lock of the operating system and records what it is asked
 * for. It counts every transaction it opened, records every key acquired, and keeps the keys held at the moment, one
 * entry per transaction that holds a key. A transaction answers {@link LockTransaction#holds(LockKey)} for the keys
 * it acquired until it is closed.
 * <p>
 * It asserts no lock order: a test of the order uses the lock manager of the runtime.
 */
final class RecordingLockManager implements LockManager {

    private final List<LockKey> acquisitions = new ArrayList<>();
    private final List<LockKey> held = new ArrayList<>();
    private int transactions;

    @Override
    public LockTransaction openTransaction() {
        transactions++;
        return new LockTransaction() {

            private final Set<LockKey> own = new HashSet<>();

            @Override
            public void acquire(LockKey key) {
                acquisitions.add(key);
                if (own.add(key)) {
                    held.add(key);
                }
            }

            @Override
            public boolean holds(LockKey key) {
                return own.contains(key);
            }

            @Override
            public void close() {
                own.forEach(held::remove);
                own.clear();
            }
        };
    }

    /** @return every key acquired since the last {@link #reset()}, in order, re-entries included */
    List<LockKey> acquisitions() {
        return List.copyOf(acquisitions);
    }

    /** @return the keys held now by the transactions that are open */
    List<LockKey> held() {
        return List.copyOf(held);
    }

    /** @return the number of transactions opened since the last {@link #reset()} */
    int transactions() {
        return transactions;
    }

    /** Forgets the acquisitions and transactions recorded so far; the keys held now stay held. */
    void reset() {
        acquisitions.clear();
        transactions = 0;
    }
}
