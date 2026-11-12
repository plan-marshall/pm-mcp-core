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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertEquals(Optional.of(66.67), CoveragePercentages.percentage(20, 30), "Twenty of thirty is 66.67");
        assertEquals(Optional.of(100.0), CoveragePercentages.percentage(5, 5), "A full population is 100");
    }

    @Test
    @DisplayName("reads an empty population as absent, never as a zero")
    void readsAnEmptyPopulationAsAbsent() {
        long covered = Generators.integers(0, 1000).next();

        assertEquals(Optional.empty(), CoveragePercentages.percentage(0, 0), "An empty population is absent");
        assertEquals(Optional.of(0.0), CoveragePercentages.percentage(0, covered + 1),
                "A measured population of misses is zero");
    }

    @Test
    @DisplayName("converts a rate given as contract text to a rounded percentage")
    void convertsARate() {
        assertEquals(87.5, CoveragePercentages.percentageOfRate("0.875"), "The rate 0.875 is 87.5 percent");
        assertEquals(90.0, CoveragePercentages.percentageOfRate("0.9"), "The rate 0.9 is 90 percent");
        assertEquals(0.0, CoveragePercentages.percentageOfRate("0"), "The rate 0 is a measured zero");
        assertEquals(100.0, CoveragePercentages.percentageOfRate("1"), "The rate 1 is 100 percent");
    }

    @Test
    @DisplayName("refuses a rate outside 0..1, contract texts")
    void refusesARateOutsideTheRange() {
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate("1.5"),
                "A rate above 1 is refused");
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate("1.000001"),
                "A rate just above 1 is refused");
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate("-0.000001"),
                "A rate just below 0 is refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0x1p-1", "0x0.8p0", "", "half"})
    @DisplayName("refuses a rate that is not a plain decimal number, contract texts")
    void refusesARateThatIsNotADecimalNumber(String rate) {
        assertThrows(IllegalArgumentException.class, () -> CoveragePercentages.percentageOfRate(rate),
                "A non-decimal rate text is refused");
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
