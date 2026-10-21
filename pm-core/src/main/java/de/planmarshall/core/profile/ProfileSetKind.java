/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

/**
 * The set a profile was read from, in increasing precedence (PM-CLIENT-5, Profile Sets).
 *
 * @since 0.1
 */
public enum ProfileSetKind {

    /** The profiles compiled into the release, {@code classpath:/profiles/}. */
    SHIPPED("shipped"),
    /** The profiles the operator installed for this machine, {@code <PM_MCP_BASE>/profiles/}. */
    MACHINE("machine"),
    /** The profiles the operator installed for this project, {@code <PM_MCP_BASE>/projects/<project_id>/profiles/}. */
    PROJECT("project");

    private final String wireName;

    ProfileSetKind(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the set in a representation */
    public String wireName() {
        return wireName;
    }
}
