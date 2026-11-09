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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The percentage arithmetic the coverage parsers share.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoveragePercentages")
class CoveragePercentagesTest {

    @Test
    @DisplayName("rounds a counter percentage to two decimals")
    void roundsACounterPercentage() {
        assertEquals(66.67, CoveragePercentages.percentage(20, 30), "Twenty of thirty is 66.67");
        assertEquals(100.0, CoveragePercentages.percentage(5, 5), "A full population is 100");
    }

    @Test
    @DisplayName("reads an empty population as a measured zero")
    void readsAnEmptyPopulationAsZero() {
        assertEquals(0.0, CoveragePercentages.percentage(0, 0), "An empty population is a zero");
    }

    @Test
    @DisplayName("converts a rate to a rounded percentage")
    void convertsARate() {
        assertEquals(87.5, CoveragePercentages.percentageOfRate(0.875), "The rate 0.875 is 87.5 percent");
        assertEquals(90.0, CoveragePercentages.percentageOfRate(0.9), "The rate 0.9 is 90 percent");
    }

    @Test
    @DisplayName("refuses a rate that is not a finite number")
    void refusesANonFiniteRate() {
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate(Double.NaN),
                "A NaN rate is refused");
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate(1.5),
                "A rate above 1 is refused");
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate(1.000001),
                "A rate just above 1 is refused");
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate(-0.000001),
                "A rate just below 0 is refused");
    }

    @Test
    @DisplayName("rounds half away from zero at two decimals")
    void roundsAtTwoDecimals() {
        double value = Generators.integers(1, 100).next().doubleValue();

        assertEquals(value, CoveragePercentages.round2(value), "An integer value is unchanged");
        assertEquals(33.33, CoveragePercentages.round2(33.333), "33.333 rounds to 33.33");
        assertEquals(33.34, CoveragePercentages.round2(33.336), "33.336 rounds to 33.34");
    }
}
