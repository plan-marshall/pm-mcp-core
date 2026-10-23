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
 * The set a role artifact is read from (PM-WORK-1, Role Sets &amp; Precedence): the shipped set, the machine set, and the
 * project set.
 *
 * @since 0.1
 */
public enum RoleSetKind {

    /** The roles compiled into the release, {@code classpath:/roles/}. */
    SHIPPED("shipped"),
    /** The roles the operator installed for the machine, {@code <PM_MCP_BASE>/roles/}. */
    MACHINE("machine"),
    /** The roles the operator installed for one project, {@code <PM_MCP_BASE>/projects/<project_id>/roles/}. */
    PROJECT("project");

    private final String wireName;

    RoleSetKind(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the set in a representation */
    public String wireName() {
        return wireName;
    }
}
