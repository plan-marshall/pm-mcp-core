/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;

/**
 * A lock manager for the tests of the consultation service: it takes no lock of the operating system and answers
 * {@link LockTransaction#holds(LockKey)} for the keys a transaction acquired until it closes. It also answers, for the
 * whole manager, whether any open transaction holds a key, so a test can observe that a seam ran under the queue lock.
 */
// Every datum in this class is a contract: the class is a lock-manager double and holds no test data.
final class ConsultTestLocks implements LockManager {

    private final Set<LockKey> held = ConcurrentHashMap.newKeySet();

    /**
     * @param key a lock key
     * @return whether an open transaction of this manager holds the key
     */
    boolean holdsAnywhere(LockKey key) {
        return held.contains(key);
    }

    @Override
    public LockTransaction openTransaction() {
        return new LockTransaction() {

            private final Set<LockKey> own = new HashSet<>();

            @Override
            public void acquire(LockKey key) {
                own.add(key);
                held.add(key);
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
}
