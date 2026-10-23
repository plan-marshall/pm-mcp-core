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

import java.util.Optional;

/**
 * What a role may do (PM-WORK-1, Role Artifact Schema): a {@code writer} changes the plan worktree and runs builds, a
 * {@code read_only} role reads and submits, and a {@code zero_tool} role has no tool and no PM-MCP connection. The
 * class bounds the tool set of the role and names the class the harness profile must run
 * ({@code headless.classes}, PM-CLIENT-2).
 *
 * @since 0.1
 */
public enum RoleClass {

    /** Changes the plan worktree and runs builds. */
    WRITER("writer"),
    /** Reads and submits. */
    READ_ONLY("read_only"),
    /** Has no tool and no PM-MCP connection; the screener and the judge. */
    ZERO_TOOL("zero_tool");

    private final String wireName;

    RoleClass(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the class in a representation and in a profile */
    public String wireName() {
        return wireName;
    }

    /**
     * @param name the name of a class in a representation or a profile
     * @return the class, or empty for a name the schema does not declare
     */
    public static Optional<RoleClass> fromWireName(String name) {
        for (var value : values()) {
            if (value.wireName.equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
