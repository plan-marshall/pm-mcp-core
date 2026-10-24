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

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;

/**
 * A lock manager for the tests of the consultation service: it takes no lock of the operating system and answers
 * {@link LockTransaction#holds(LockKey)} for the keys a transaction acquired until it closes.
 */
final class ConsultTestLocks implements LockManager {

    @Override
    public LockTransaction openTransaction() {
        return new LockTransaction() {

            private final Set<LockKey> own = new HashSet<>();

            @Override
            public void acquire(LockKey key) {
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
}
