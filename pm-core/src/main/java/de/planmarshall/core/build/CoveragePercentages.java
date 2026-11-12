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

import java.math.BigDecimal;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The percentage arithmetic the coverage parsers share.
 * <p>
 * Every parser answers the same two questions — what percentage do these counters give, and what percentage does a rate
 * in {@code 0..1} give — and rounds both the way the ported parser does, to two decimal places. An empty population is
 * absent, never a measured {@code 0.0}: nothing was measured, so there is no percentage to report, and each parser
 * decides what an absent metric means for its report. A rate is read as a plain decimal number, so {@code NaN},
 * {@code Infinity} and the hexadecimal floating-point forms are refused rather than read.
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
     * @return the percentage rounded to two decimals, or empty for an empty population
     */
    static Optional<Double> percentage(long covered, long total) {
        return total <= 0 ? Optional.empty() : Optional.of(round2(covered * 100.0 / total));
    }

    /**
     * Returns the percentage of a rate in {@code 0..1}, given as the text the format printed.
     *
     * @param rate the rate as the format printed it
     * @return the percentage rounded to two decimals
     * @throws IllegalArgumentException if the rate is not a decimal number within {@code 0..1}
     */
    static double percentageOfRate(String rate) {
        return round2(rate(rate).doubleValue() * 100.0);
    }

    /**
     * Reads a rate in {@code 0..1} from the text the format printed.
     *
     * @param rate the rate as the format printed it
     * @return the rate as a decimal number
     * @throws IllegalArgumentException if the rate is not a decimal number within {@code 0..1}
     */
    static BigDecimal rate(String rate) {
        BigDecimal value;
        try {
            value = new BigDecimal(rate.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the coverage rate is not a decimal number: " + rate, e);
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("the coverage rate must be within 0..1: " + rate);
        }
        return value;
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
