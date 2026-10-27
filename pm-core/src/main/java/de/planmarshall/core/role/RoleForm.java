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

import java.util.Arrays;
import java.util.Optional;

/**
 * The form of a role's workers (PM-WORK-1, Role Artifact Schema): a {@code fresh} role gets one worker per task, which
 * ends after the task; a {@code warm} role gets supervised workers that take task after task and are recycled. A
 * {@code zero_tool} role is always {@code fresh}.
 *
 * @since 0.1
 */
public enum RoleForm {

    /** One worker per task, which ends after the task. */
    FRESH("fresh"),
    /** Supervised workers that take task after task. */
    WARM("warm");

    private final String wireName;

    RoleForm(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the form in a representation */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a form in a document
     * @return the form; empty if the name is not one
     */
    public static Optional<RoleForm> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(form -> form.wireName.equals(wireName)).findFirst();
    }
}
