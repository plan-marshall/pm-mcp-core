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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;

/**
 * A lock manager for the concurrency tests of a store: it serializes the transactions of the threads of the test on
 * one {@link ReentrantLock}. Unlike {@link RecordingLockManager} it therefore gives the store the mutual exclusion of
 * a real lock manager, without taking a lock of the operating system.
 * <p>
 * The transactions of one thread are not serialized against each other, which is the re-entrancy of the lock the
 * store's own transaction does not use.
 */
final class SerializingLockManager implements LockManager {

    private final ReentrantLock mutex = new ReentrantLock();

    @Override
    public LockTransaction openTransaction() {
        return new LockTransaction() {

            private final Set<LockKey> own = new HashSet<>();

            @Override
            public void acquire(LockKey key) {
                if (own.add(key)) {
                    mutex.lock();
                }
            }

            @Override
            public boolean holds(LockKey key) {
                return own.contains(key);
            }

            @Override
            public void close() {
                var held = own.size();
                own.clear();
                for (var index = 0; index < held; index++) {
                    mutex.unlock();
                }
            }
        };
    }
}
