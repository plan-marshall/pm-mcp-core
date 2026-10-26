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
 * How often each item of a role's topic is offered (Model Work § Grading, Screener Repeated Runs). The set is closed:
 * the screener's injected items are offered five times, each in a fresh worker, because one caught run says little about
 * the next; every other role is graded once per item and form.
 *
 * @param runsPerItem how often each item of a topic is offered, at least one
 * @since 0.1
 */
public record GradingProfile(int runsPerItem) {

    /** The compiled count of the screener's repeated runs. */
    public static final int SCREENER_RUNS = 5;

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

    /** @return the profile of the screener, whose items are offered five times */
    public static GradingProfile screener() {
        return new GradingProfile(SCREENER_RUNS);
    }

    /** @return {@code true} if every item is offered more than once */
    public boolean repeated() {
        return runsPerItem > 1;
    }
}
