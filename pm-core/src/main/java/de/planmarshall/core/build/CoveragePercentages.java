/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import lombok.experimental.UtilityClass;

/**
 * The percentage arithmetic the coverage parsers share.
 * <p>
 * Every parser answers the same two questions — what percentage do these counters give, and what percentage does a rate
 * in {@code 0..1} give — and rounds both the way the ported parser does, to two decimal places. An empty population is a
 * measured {@code 0.0}, never a refusal: the counters the format printed are a zero, while a report the parser cannot
 * read is refused before this arithmetic runs.
 *
 * @since 0.1
 */
@UtilityClass
class CoveragePercentages {

    /**
     * Returns the percentage covered out of a population.
     *
     * @param covered the covered count, never negative
     * @param total   the total count, never negative
     * @return the percentage rounded to two decimals, {@code 0.0} for an empty population
     */
    static double percentage(long covered, long total) {
        return total <= 0 ? 0.0 : round2(covered * 100.0 / total);
    }

    /**
     * Returns the percentage of a rate in {@code 0..1}.
     *
     * @param rate the rate the format printed
     * @return the percentage rounded to two decimals
     * @throws IllegalArgumentException if the rate is not a finite number
     */
    static double percentageOfRate(double rate) {
        if (!Double.isFinite(rate)) {
            throw new IllegalArgumentException("the coverage rate is not a number: " + rate);
        }
        return round2(rate * 100.0);
    }

    /**
     * Rounds a percentage to two decimal places.
     *
     * @param value the value to round
     * @return the value rounded to two decimals
     */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
