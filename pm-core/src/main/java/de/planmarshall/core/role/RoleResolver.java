/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

/**
 * Resolves one named role in the effective role set (PM-WORK-1, Composition Check). A catalog of one plan creates it
 * ({@link RoleCatalog#forPlan(RoleAssignments)}), so a composition resolves every name of a unit against the same plan's
 * overrides.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface RoleResolver {

    /**
     * @param roleName a role name
     * @return the effective role, or why it is undefined
     */
    RoleResolution resolve(String roleName);
}
