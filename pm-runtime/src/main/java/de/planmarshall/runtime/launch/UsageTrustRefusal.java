/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

/**
 * The refusal of an installation of a verified profile's usage report (PM-CLIENT-5, Harness Profiles § Tokens, Not Money;
 * Evaluation Reference § Results): the figures of a new harness or of a new harness version are trusted only after the
 * evaluation ran twice and both runs recorded the same figure statuses.
 * <p>
 * The refusal is the seam's own: it does not decide that a harness reports nothing, it decides that what was measured is
 * not yet trusted. The install path that carries the report into the enrolment shows the code and runs the evaluation
 * again.
 *
 * @since 0.1
 */
public final class UsageTrustRefusal extends RuntimeException {

    /** The evaluation of the harness or version was not repeated. */
    public static final String NOT_REPEATED = "harness_usage_not_repeated";

    /** Two evaluations of the harness or version disagree on a figure status. */
    public static final String DISAGREES = "harness_usage_disagrees";

    private final String code;

    /**
     * @param code    the refusal code
     * @param message what the refusal names
     */
    public UsageTrustRefusal(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return the code of the refusal, a value of this class's constants */
    public String code() {
        return code;
    }
}
