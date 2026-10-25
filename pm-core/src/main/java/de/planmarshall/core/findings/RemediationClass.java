/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import java.util.Arrays;
import java.util.Optional;

/**
 * The class of the remediation fix that introduced a finding (PM-IMPL-2, Fix Attribution): the producing remediation
 * class whose fix commit changed the finding's lines.
 *
 * @since 0.1
 */
public enum RemediationClass {

    /** A quality-gate findings remediation. */
    QGATE_FINDINGS("qgate-findings"),
    /** A build-failure remediation. */
    BUILD_FAILURE("build-failure"),
    /** A CI-failure remediation. */
    CI_FAILURE("ci-failure"),
    /** A code-analysis remediation. */
    ANALYSIS("analysis"),
    /** A review-findings remediation. */
    REVIEW_FINDINGS("review-findings"),
    /** A merge-conflict remediation. */
    MERGE_CONFLICT("merge-conflict"),
    /** A self-review remediation. */
    SELF_REVIEW("self-review"),
    /** A loop-back iteration's fix. */
    LOOP_BACK("loop-back");

    private final String wireName;

    RemediationClass(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the class in the findings store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a class in the findings store
     * @return the class of that name; empty if no class has it
     */
    public static Optional<RemediationClass> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
