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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of {@link TestCounts}: a measured population is a consistent sum with an immutable per-runner map, and an
 * unmeasured population carries no number at all.
 */
@EnableGeneratorController
@DisplayName("TestCounts")
class TestCountsTest {

    @Test
    @DisplayName("a measured population is the sum of its three parts")
    void measuredIsConsistent() {
        var passed = Generators.integers(0, 500).next();
        var failed = Generators.integers(0, 50).next();
        var skipped = Generators.integers(0, 50).next();

        var counts = new TestCounts.Measured(passed, failed, skipped, passed + failed + skipped, null);

        assertAll(
                () -> assertEquals(passed, counts.passed(), "The passed count is stored"),
                () -> assertEquals(failed, counts.failed(), "The failed count is stored"),
                () -> assertEquals(skipped, counts.skipped(), "The skipped count is stored"),
                () -> assertEquals(passed + failed, counts.executed(), "Executed excludes the skipped tests"),
                () -> assertEquals(TestsPopulation.MEASURED, counts.population(), "The population is measured"));
    }

    @Test
    @DisplayName("a total that is not the sum of the parts is refused")
    void refusesInconsistentTotal() {
        var passed = Generators.integers(0, 50).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TestCounts.Measured(passed, 1, 0, passed + 2, null),
                        "The total must be the sum of the three parts"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TestCounts.Measured(-1, 0, 0, -1, null),
                        "A count is never negative"));
    }

    @Test
    @DisplayName("the per-runner map is copied so later changes cannot alter it")
    void copiesByRunner() {
        var source = new LinkedHashMap<String, Integer>();
        source.put("surefire", Generators.integers(0, 900).next());
        var counts = new TestCounts.Measured(1, 0, 0, 1, source);
        var expected = Map.copyOf(source);

        source.put("failsafe", 7);

        assertAll(
                () -> assertEquals(expected, counts.byRunner(), "The counts keep the map they were built from"),
                () -> assertNull(new TestCounts.Measured(1, 0, 0, 1, null).byRunner(),
                        "No runner attribution is a null map"));
        var byRunner = counts.byRunner();
        assertThrows(UnsupportedOperationException.class,
                () -> byRunner.put("failsafe", 1),
                "The stored map cannot be modified");
    }

    @Test
    @DisplayName("a negative per-runner executed count is refused")
    void refusesNegativeRunnerCount() {
        var byRunner = Map.of("surefire", -3);

        assertThrows(IllegalArgumentException.class, () -> new TestCounts.Measured(1, 0, 0, 1, byRunner),
                "An executed count is never negative");
    }

    @Test
    @DisplayName("counts outside the int range are no measurement")
    void refusesUnrepresentableCounts() {
        var overflowing = TestCounts.from((long) Integer.MAX_VALUE + 1, 0, 0, null);
        var wrapped = TestCounts.from(Integer.MAX_VALUE, 1, 0, null);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, overflowing,
                        "A count above the int range is unmeasured"),
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, wrapped, "A wrapped sum is unmeasured"),
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, TestCounts.from(Long.MAX_VALUE, 1, 0, null),
                        "A long sum that wraps is unmeasured"),
                () -> assertInstanceOf(TestCounts.Measured.class, TestCounts.from(4999, 3, 1, null),
                        "A representable sum is a measurement"));
    }

    @Test
    @DisplayName("an unmeasured population carries no number")
    void unmeasuredCarriesNoNumber() {
        var counts = TestCounts.unmeasured();

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, counts, "The state is a single value"),
                () -> assertEquals(TestsPopulation.UNMEASURED, counts.population(), "The population is unmeasured"),
                () -> assertInstanceOf(TestCounts.Unmeasured.class, counts, "The unmeasured state has no count accessor"));
    }
}
