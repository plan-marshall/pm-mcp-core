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

/**
 * The one lock manager every lock acquisition of the runtime goes through (PM-IMPL-7). Locks are taken for one
 * transaction at a time: a caller opens a transaction, acquires the keys it needs in the total lock order, and
 * closes the transaction, which releases them. Implemented by {@code pm-runtime}.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface LockManager {

    /**
     * @return a new transaction that holds no lock yet; the caller closes it
     */
    LockTransaction openTransaction();
}
