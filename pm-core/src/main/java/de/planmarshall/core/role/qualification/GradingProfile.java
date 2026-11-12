/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

/**
 * How often each item of a role's topic is offered (Model Work § Grading). The count is the caller's, one by default,
 * and the grading run offers every role exactly the runs the caller requested.
 *
 * @param runsPerItem how often each item of a topic is offered, at least one
 * @since 0.1
 */
public record GradingProfile(int runsPerItem) {

    /**
     * @throws IllegalArgumentException if the count is below one
     */
    public GradingProfile {
        if (runsPerItem < 1) {
            throw new IllegalArgumentException("An item is offered at least once: " + runsPerItem);
        }
    }

    /** @return the profile of a role graded once per item and form */
    public static GradingProfile single() {
        return new GradingProfile(1);
    }

    /** @return {@code true} if every item is offered more than once */
    public boolean repeated() {
        return runsPerItem > 1;
    }
}
