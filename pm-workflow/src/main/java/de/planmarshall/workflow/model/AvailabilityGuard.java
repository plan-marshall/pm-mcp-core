/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

/**
 * The start-failure guard of the model caller's Available step (PM-WORK-1, PM-WORK-3): after the compiled number of
 * consecutive start failures a role on a harness is unavailable, and every task of it closes {@code role_unavailable}
 * until a probe clears the guard.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface AvailabilityGuard {

    /**
     * @param roleName the role name
     * @param harness  the role's effective harness
     * @return whether the guard holds, so the task cannot start a worker
     */
    boolean holds(String roleName, String harness);
}
