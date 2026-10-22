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
 * The release's default role assignment, {@code classpath:/roles/default-assignment.json} (PM-WORK-1, Default Role
 * Assignment): per role a model tier of the closed list, the effort, the preferred harness, and the advisory.
 * <p>
 * The assignment and its resolution {@code roles.default} are the onboarding's; this package reads one member of it, the
 * effort the steward runs with, through {@link RoleEnvironment}.
 *
 * @param roles the default assignment by role name
 * @since 0.1
 */
public record DefaultAssignment(Map<String, DefaultRole> roles) {

    /** Copies the entries. */
    public DefaultAssignment {
        roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    }

    /**
     * @param role a role name
     * @return the default assignment of the role, or empty when the release does not name it
     */
    public Optional<DefaultRole> forRole(String role) {
        return Optional.ofNullable(roles.get(role));
    }

    /**
     * The default assignment of one role.
     *
     * @param tier            the model tier, one of {@code frontier}, {@code standard}, {@code fast}
     * @param effort          the effort, which the launch narrows where the harness does not accept it
     * @param preferredHarness the client id of the harness the release graded the role on
     * @param advisory        what the role does and why this tier
     * @since 0.1
     */
    public record DefaultRole(String tier, String effort, String preferredHarness, String advisory) {
    }
}
