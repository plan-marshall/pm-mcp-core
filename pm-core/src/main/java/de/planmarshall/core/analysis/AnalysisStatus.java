/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

/**
 * The closed status of code analysis for a commit or pull request, which every code analysis provider maps its
 * results to (PM-IMPL-1). A new provider needs an adapter, never a new value.
 *
 * @since 0.1
 */
public enum AnalysisStatus {
    /** The analysis has not settled. */
    RUNNING("running"),
    /** The quality gate is met and no new issue is open. */
    PASSED("passed"),
    /** Conditions of the quality gate failed. */
    GATE_FAILED("gate_failed"),
    /** The quality gate is met and new issues are open. */
    FINDINGS("findings"),
    /** The result cannot be established; it never passes. */
    UNDECIDABLE("undecidable"),
    /** The provider does not analyse this change, or no provider is configured. */
    NOT_RUN("not_run");

    private final String wireName;

    AnalysisStatus(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the value of the wait dimension {@code analysis_status} on the wire
     */
    public String wireName() {
        return wireName;
    }
}
