/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ColdStartComparison")
class ColdStartComparisonTest {

    private static final double RELATIVE = StartupColdStartIT.RELATIVE_TOLERANCE;
    private static final Duration ABSOLUTE = StartupColdStartIT.ABSOLUTE_TOLERANCE;
    private static final int RUNS = StartupColdStartIT.MEASURED_RUNS;

    /** A quiet population: {@link #RUNS} samples from {@code base} upwards in steps of 1 % of {@code base}. */
    private static List<Long> quiet(long base) {
        return LongStream.range(0, RUNS).map(i -> base + i * base / 100).boxed().toList();
    }

    private static List<Long> constant(long value) {
        return LongStream.range(0, RUNS).map(_ -> value).boxed().toList();
    }

    @Nested
    @DisplayName("agrees")
    class Agrees {

        @Test
        @DisplayName("for identical populations")
        void shouldAgreeForIdenticalPopulations() {
            var samples = quiet(Duration.ofMillis(3).toNanos());

            var verdict = ColdStartComparison.compare(samples, samples, RELATIVE, ABSOLUTE);

            assertAll(
                    () -> assertTrue(verdict.agrees(), verdict.describe(0, 300)),
                    () -> assertEquals(verdict.emptyQuartile(), verdict.seededQuartile()),
                    () -> assertEquals(verdict.emptyMedian(), verdict.seededMedian()));
        }

        @ParameterizedTest(name = "with {0} of 15 runs disturbed by one-sided noise")
        @ValueSource(ints = {1, 4, 9})
        @DisplayName("for a population with large one-sided outliers against a quiet one")
        void shouldAgreeDespiteOneSidedNoise(int disturbed) {
            var base = Duration.ofMillis(3).toNanos();
            var calm = quiet(base);
            var noisy = new ArrayList<>(calm);
            for (var i = 0; i < disturbed; i++) {
                noisy.set(RUNS - 1 - i, Duration.ofMillis(20 + 5L * i).toNanos());
            }

            var verdict = ColdStartComparison.compare(calm, noisy, RELATIVE, ABSOLUTE);

            assertTrue(verdict.agrees(), verdict.describe(0, 300));
        }

        @Test
        @DisplayName("where the medians alone would not, because more than half of the runs are disturbed")
        void shouldAgreeWhereTheMedianWouldNot() {
            var calm = quiet(Duration.ofMillis(3).toNanos());
            var noisy = new ArrayList<>(calm);
            for (var i = 0; i < 9; i++) {
                noisy.set(RUNS - 1 - i, Duration.ofMillis(20).toNanos());
            }

            var verdict = ColdStartComparison.compare(calm, noisy, RELATIVE, ABSOLUTE);
            var medianAllowed = (long) (RELATIVE * Math.max(verdict.emptyMedian(), verdict.seededMedian()))
                    + ABSOLUTE.toNanos();

            assertAll(
                    () -> assertTrue(Math.abs(verdict.seededMedian() - verdict.emptyMedian()) > medianAllowed,
                            "the medians carry the noise"),
                    () -> assertTrue(verdict.agrees(), verdict.describe(0, 300)));
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @ParameterizedTest(name = "a population three times one of {0} ms")
        @ValueSource(longs = {20, 50, 200})
        @DisplayName("a population whose every sample is a clear multiple of the other")
        void shouldRejectRealScaling(long baseMillis) {
            var base = Duration.ofMillis(baseMillis).toNanos();
            var empty = quiet(base);
            var seeded = empty.stream().map(sample -> 3 * sample).toList();

            var verdict = ColdStartComparison.compare(empty, seeded, RELATIVE, ABSOLUTE);

            assertFalse(verdict.agrees(), verdict.describe(0, 300));
        }

        @Test
        @DisplayName("an empty population")
        void shouldRejectEmptyPopulation() {
            var samples = quiet(1000);
            List<Long> none = List.of();

            assertThrows(IllegalArgumentException.class,
                    () -> ColdStartComparison.compare(none, samples, RELATIVE, ABSOLUTE));
        }
    }

    @ParameterizedTest(name = "seeded {0} ns against empty 10000000 ns agrees: {1}")
    @CsvSource({"29999998, true", "30000000, true", "30000002, false", "30000004, false"})
    @DisplayName("at the tolerance edge, a difference just inside the band agrees and just outside is rejected")
    void shouldHonourTheToleranceEdge(long seededNanos, boolean expected) {
        // allowed = 0.5 * 30 ms + 5 ms = 20 ms, exactly the difference of 30 ms and 10 ms
        var empty = constant(10_000_000L);
        var seeded = constant(seededNanos);

        var verdict = ColdStartComparison.compare(empty, seeded, RELATIVE, ABSOLUTE);

        assertEquals(expected, verdict.agrees(), verdict.describe(0, 300));
    }

    @Test
    @DisplayName("describes both statistics of both populations and the allowed difference")
    void shouldDescribeEveryNumber() {
        var verdict = new ColdStartComparison.Verdict(11, 22, 3, 4, 55);

        var description = verdict.describe(0, 300);

        assertEquals("lower quartiles 3 ns (0 projects) and 4 ns (300 projects), allowed difference 55 ns;"
                + " medians 11 ns (0 projects) and 22 ns (300 projects)", description);
    }
}
