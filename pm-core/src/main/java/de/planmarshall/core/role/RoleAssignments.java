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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The role assignment of a machine or of one project (PM-WORK-1, Role Assignment, and
 * {@code store-schemas/12-machine-stores.adoc#role-assignments-store}): per role name the harness, model and effort the
 * level names.
 * <p>
 * The store is written by the sub-workflow {@code roles.write} of the onboarding; this package only reads it, through
 * {@link RoleEnvironment}, and validates a value as the role's effective configuration when it resolves the role.
 *
 * @param roles the assignment by role name
 * @since 0.1
 */
public record RoleAssignments(Map<String, RoleAssignment> roles) {

    /** Copies the assignments. */
    public RoleAssignments {
        roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    }

    /**
     * @param role a role name
     * @return the assignment of the role, or empty when the level does not name it
     */
    public Optional<RoleAssignment> forRole(String role) {
        return Optional.ofNullable(roles.get(role));
    }
}
