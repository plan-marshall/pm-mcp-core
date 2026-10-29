/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;

/**
 * A lock manager for the tests of the configuration stores: it takes no lock of the operating system and records what it
 * is asked for.
 */
final class TestLocks implements LockManager {

    private final List<LockKey> acquisitions = new ArrayList<>();

    @Override
    public LockTransaction openTransaction() {
        return new LockTransaction() {

            private final Set<LockKey> own = new HashSet<>();

            @Override
            public void acquire(LockKey key) {
                acquisitions.add(key);
                own.add(key);
            }

            @Override
            public boolean holds(LockKey key) {
                return own.contains(key);
            }

            @Override
            public void close() {
                own.clear();
            }
        };
    }

    /** @return every key acquired so far, in order */
    List<LockKey> acquisitions() {
        return List.copyOf(acquisitions);
    }
}
