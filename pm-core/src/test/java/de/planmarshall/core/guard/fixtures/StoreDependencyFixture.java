/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard.fixtures;

import de.planmarshall.core.store.ScopeType;

/**
 * Control of the analysis boundary guard: a class depending on {@link ScopeType} of {@code core.store}, on which the
 * rule must fail.
 */
public final class StoreDependencyFixture {

    private StoreDependencyFixture() {
    }

    /** @return the scope type of a plan */
    public static ScopeType planScope() {
        return ScopeType.PLAN;
    }
}
