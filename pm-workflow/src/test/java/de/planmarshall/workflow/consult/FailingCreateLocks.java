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

import java.io.IOException;
import java.io.UncheckedIOException;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;

/**
 * A lock manager for the test of a failed consultation-task creation: the first transaction is served, the second — the
 * one {@code QueueStore.create} opens — fails, and every later one is served again.
 */
final class FailingCreateLocks implements LockManager {

    private final LockManager delegate = new ConsultTestLocks();

    private int opened;

    @Override
    public LockTransaction openTransaction() {
        if (++opened == 2) {
            throw new UncheckedIOException(new IOException("injected creation failure"));
        }
        return delegate.openTransaction();
    }
}
