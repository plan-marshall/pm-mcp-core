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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The threshold verdict of a coverage report: it compares the overall line percentage only, and the low-coverage list
 * compares each item's line percentage only.
 *
 * @since 0.1
 */
@DisplayName("CoverageReport")
class CoverageReportTest {

    @Test
    @DisplayName("passes a report whose branch coverage is below the threshold")
    void passesOnLineThoughBranchIsBelow() {
        CoverageReport report = new CoverageReport(CoverageFormat.JACOCO,
                CoverageMetrics.builder().line(90).branch(33.33).build(),
                List.of(itemWithBranch("com.example.Branchy", 90, 33.33)));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(90.0, report.overall().line(), "The overall line percentage is 90"),
                () -> assertEquals(33.33, report.overall().branch(), "The branch percentage is below 80"),
                () -> assertTrue(verdict.passed(), "The line above the threshold passes"),
                () -> assertTrue(verdict.lowCoverage().isEmpty(), "No item is below by its line percentage"));
    }

    @Test
    @DisplayName("fails a report whose branch coverage is above the threshold")
    void failsOnLineThoughBranchIsAbove() {
        CoverageReport report = new CoverageReport(CoverageFormat.JACOCO,
                CoverageMetrics.builder().line(40).branch(90.0).build(),
                List.of(itemWithBranch("com.example.LowLine", 40, 90.0)));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(40.0, report.overall().line(), "The overall line percentage is 40"),
                () -> assertEquals(90.0, report.overall().branch(), "The branch percentage is above 80"),
                () -> assertFalse(verdict.passed(), "The line below the threshold fails"),
                () -> assertEquals(1, verdict.lowCoverage().size(), "The item is listed as low coverage"),
                () -> assertEquals("com.example.LowLine", verdict.lowCoverage().getFirst().name(),
                        "The low-coverage item is the class"));
    }

    @Test
    @DisplayName("passes a line percentage exactly at the threshold")
    void passesExactlyAtTheThreshold() {
        CoverageReport report = report(80, List.of(item("exact", 80), item("just-below", 79.99)));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertTrue(verdict.passed(), "A line at the threshold passes"),
                () -> assertEquals(1, verdict.lowCoverage().size(), "Only the lower item is listed"),
                () -> assertEquals("just-below", verdict.lowCoverage().getFirst().name(),
                        "The item strictly below is listed"));
    }

    @Test
    @DisplayName("lists the items by their line percentage only")
    void listsTheItemsByLineOnly() {
        CoverageReport report = report(80, List.of(itemWithBranch("low-branch", 90, 0.0),
                itemWithBranch("high-branch", 70, 100.0)));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertTrue(verdict.passed(), "The overall line passes"),
                () -> assertEquals(1, verdict.lowCoverage().size(), "One item is below by its line"),
                () -> assertEquals("high-branch", verdict.lowCoverage().getFirst().name(),
                        "The item is chosen by its line, not its branch"));
    }

    @Test
    @DisplayName("refuses a threshold outside the percentage range")
    void refusesAThresholdOutsideTheRange() {
        CoverageReport report = report(50, List.of());

        assertThrows(IllegalArgumentException.class, () -> report.evaluate(101), "A threshold above 100 is refused");
        assertThrows(IllegalArgumentException.class, () -> report.evaluate(-1), "A negative threshold is refused");
    }

    private static CoverageReport report(double overallLine, List<CoverageItem> items) {
        return new CoverageReport(CoverageFormat.LCOV, CoverageMetrics.builder().line(overallLine).build(), items);
    }

    private static CoverageItem item(String name, double line) {
        return itemWithBranch(name, line, null);
    }

    private static CoverageItem itemWithBranch(String name, double line, Double branch) {
        return CoverageItem.builder().name(name).line(line).branch(branch).missedMethods(List.of()).build();
    }
}
