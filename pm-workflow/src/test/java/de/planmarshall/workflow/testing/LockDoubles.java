/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.testing;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;

/**
 * The EasyMock doubles of the lock seam (ADR 026: a factory that returns an EasyMock mock is not a hand-written double).
 * <p>
 * A store test that owns its scope needs no lock of another writer: the double answers {@code true} for every key, so
 * every store write passes the held-key guard, and a nice mock, so {@code acquire} and {@code close} answer nothing.
 *
 * @since 0.1
 */
public final class LockDoubles {

    private LockDoubles() {
    }

    /**
     * @return a lock transaction that holds every key
     */
    public static LockTransaction holdingEverything() {
        LockTransaction lock = createNiceMock(LockTransaction.class);
        expect(lock.holds(anyObject(LockKey.class))).andStubReturn(true);
        replay(lock);
        return lock;
    }
}
