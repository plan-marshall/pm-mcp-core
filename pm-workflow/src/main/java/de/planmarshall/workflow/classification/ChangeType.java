/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.classification;

/**
 * The intent of a plan and of its deliverables, a closed set of six values that is orthogonal to domain and
 * profile (PM-WF-11). Whether a change breaks compatibility is no change type.
 *
 * @since 0.1
 */
public enum ChangeType {
    /** An analysis without a change. */
    ANALYSIS("analysis", 1),
    /** A new capability. */
    FEATURE("feature", 2),
    /** An extension of an existing capability. */
    ENHANCEMENT("enhancement", 3),
    /** The correction of a defect. */
    BUG_FIX("bug_fix", 4),
    /** A change that pays down technical debt. */
    TECH_DEBT("tech_debt", 5),
    /** A verification of what exists. */
    VERIFICATION("verification", 6);

    private final String wireName;
    private final int priorityOrder;

    ChangeType(String wireName, int priorityOrder) {
        this.wireName = wireName;
        this.priorityOrder = priorityOrder;
    }

    /**
     * @return the value of {@code change_type} on the wire and in the stores
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @return the position of the type in the priority order, from one
     */
    public int priorityOrder() {
        return priorityOrder;
    }
}
