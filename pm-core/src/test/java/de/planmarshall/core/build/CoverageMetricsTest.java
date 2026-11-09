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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of a coverage metric set.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageMetrics")
class CoverageMetricsTest {

    @Test
    @DisplayName("keeps the metrics the format does not give absent")
    void keepsTheAbsentMetricsNull() {
        CoverageMetrics metrics = CoverageMetrics.builder()
                .line(Generators.integers(0, 100).next().doubleValue())
                .build();

        assertAll(
                () -> assertNull(metrics.instruction(), "JaCoCo's absent instruction is null"),
                () -> assertNull(metrics.branch(), "An absent branch metric is null"),
                () -> assertNull(metrics.function(), "An absent function metric is null"),
                () -> assertNull(metrics.statement(), "An absent statement metric is null"));
    }

    @Test
    @DisplayName("refuses a line percentage outside the percentage range")
    void refusesALineOutsideTheRange() {
        var lineAbove = CoverageMetrics.builder().line(100.01);
        var lineBelow = CoverageMetrics.builder().line(-0.01);
        var lineNaN = CoverageMetrics.builder().line(Double.NaN);

        assertThrows(IllegalArgumentException.class, lineAbove::build, "A line above 100 is refused");
        assertThrows(IllegalArgumentException.class, lineBelow::build, "A negative line is refused");
        assertThrows(IllegalArgumentException.class, lineNaN::build, "A NaN line is refused");
    }

    @Test
    @DisplayName("refuses an optional percentage outside the percentage range")
    void refusesAnOptionalMetricOutsideTheRange() {
        var branchAbove = CoverageMetrics.builder().line(50).branch(101.0);
        var statementBelow = CoverageMetrics.builder().line(50).statement(-1.0);

        assertThrows(IllegalArgumentException.class, branchAbove::build, "A branch above 100 is refused");
        assertThrows(IllegalArgumentException.class, statementBelow::build, "A negative statement is refused");
    }

    @Test
    @DisplayName("keeps a measured zero apart from an absent metric")
    void keepsAMeasuredZero() {
        CoverageMetrics metrics = CoverageMetrics.builder().line(0).branch(0.0).build();

        assertAll(
                () -> assertEquals(0.0, metrics.line(), "A measured zero line stays zero"),
                () -> assertEquals(0.0, metrics.branch(), "A measured zero branch stays zero"),
                () -> assertNull(metrics.statement(), "An absent statement stays null"));
    }
}
