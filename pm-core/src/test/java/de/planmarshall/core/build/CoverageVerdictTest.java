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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of a coverage verdict.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageVerdict")
class CoverageVerdictTest {

    @Test
    @DisplayName("freezes the low-coverage list against later mutation")
    void freezesTheLowCoverageList() {
        List<CoverageItem> lowCoverage = new ArrayList<>(List.of(item(30)));
        CoverageVerdict verdict = CoverageVerdict.builder()
                .passed(false)
                .threshold(80)
                .overallLine(30)
                .lowCoverage(lowCoverage)
                .build();

        lowCoverage.clear();

        assertEquals(1, verdict.lowCoverage().size(), "The verdict keeps its own frozen list");
    }

    @Test
    @DisplayName("refuses a threshold outside the percentage range")
    void refusesAThresholdOutsideTheRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CoverageVerdict.builder().threshold(101).overallLine(50).lowCoverage(List.of()).build(),
                "A threshold above 100 is refused");
        assertThrows(IllegalArgumentException.class,
                () -> CoverageVerdict.builder().threshold(-1).overallLine(50).lowCoverage(List.of()).build(),
                "A negative threshold is refused");
    }

    @Test
    @DisplayName("refuses an overall line percentage outside the range")
    void refusesAnOverallLineOutsideTheRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CoverageVerdict.builder().threshold(80).overallLine(100.5).lowCoverage(List.of()).build(),
                "An overall line above 100 is refused");
    }

    @Test
    @DisplayName("keeps a passing verdict without a low-coverage item")
    void keepsAPassingVerdict() {
        double overallLine = Generators.integers(80, 100).next().doubleValue();
        CoverageVerdict verdict = CoverageVerdict.builder()
                .passed(true)
                .threshold(80)
                .overallLine(overallLine)
                .lowCoverage(List.of())
                .build();

        assertTrue(verdict.passed(), "A line at or above the threshold passes");
    }

    private static CoverageItem item(double line) {
        return CoverageItem.builder()
                .name(Generators.nonEmptyStrings().next())
                .line(line)
                .missedMethods(List.of())
                .build();
    }
}
