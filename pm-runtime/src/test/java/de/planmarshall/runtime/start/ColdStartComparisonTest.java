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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.GeneratorType;
import de.cuioss.test.generator.junit.parameterized.GeneratorsSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnableGeneratorController
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

    /** @return a generated base of a quiet population, between 1 ms and 5 ms */
    private static long quietBase() {
        return Duration.ofMillis(Generators.integers(1, 5).next()).toNanos();
    }

    @Nested
    @DisplayName("agrees")
    class Agrees {

        @Test
        @DisplayName("for identical populations")
        void shouldAgreeForIdenticalPopulations() {
            var samples = quiet(quietBase());

            var verdict = ColdStartComparison.compare(samples, samples, RELATIVE, ABSOLUTE);

            assertAll(
                    () -> assertTrue(verdict.agrees(), "identical populations must agree"),
                    () -> assertEquals(verdict.emptyQuartile(), verdict.seededQuartile(),
                            "identical populations share their quartile"),
                    () -> assertEquals(verdict.emptyMedian(), verdict.seededMedian(),
                            "identical populations share their median"));
        }

        @ParameterizedTest(name = "with {0} of 15 runs disturbed by one-sided noise")
        @GeneratorsSource(generator = GeneratorType.INTEGERS, low = "1", high = "9", count = 3)
        @DisplayName("for a population with large one-sided outliers against a quiet one")
        void shouldAgreeDespiteOneSidedNoise(int disturbed) {
            var calm = quiet(quietBase());
            var noisy = new ArrayList<>(calm);
            for (var i = 0; i < disturbed; i++) {
                noisy.set(RUNS - 1 - i, Duration.ofMillis(Generators.integers(20, 200).next()).toNanos());
            }

            var verdict = ColdStartComparison.compare(calm, noisy, RELATIVE, ABSOLUTE);

            assertTrue(verdict.agrees(), "one-sided outliers must not break agreement");
        }

        @Test
        @DisplayName("where the medians alone would not, because more than half of the runs are disturbed")
        void shouldAgreeWhereTheMedianWouldNot() {
            var calm = quiet(quietBase());
            var noisy = new ArrayList<>(calm);
            // 9 of 15 runs: more than half, fewer than the runs above the lower quartile
            var noise = Duration.ofMillis(Generators.integers(40, 200).next()).toNanos();
            for (var i = 0; i < 9; i++) {
                noisy.set(RUNS - 1 - i, noise);
            }

            var verdict = ColdStartComparison.compare(calm, noisy, RELATIVE, ABSOLUTE);
            var medianAllowed = (long) (RELATIVE * Math.max(verdict.emptyMedian(), verdict.seededMedian()))
                    + ABSOLUTE.toNanos();

            assertAll(
                    () -> assertTrue(Math.abs(verdict.seededMedian() - verdict.emptyMedian()) > medianAllowed,
                            "the medians carry the noise"),
                    () -> assertTrue(verdict.agrees(), "the lower quartiles must still agree"));
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @ParameterizedTest(name = "a population three times one of {0} ms")
        @GeneratorsSource(generator = GeneratorType.LONGS, low = "20", high = "500", count = 3)
        @DisplayName("a population whose every sample is a clear multiple of the other")
        void shouldRejectRealScaling(long baseMillis) {
            var base = Duration.ofMillis(baseMillis).toNanos();
            var empty = quiet(base);
            var seeded = empty.stream().map(sample -> 3 * sample).toList();

            var verdict = ColdStartComparison.compare(empty, seeded, RELATIVE, ABSOLUTE);

            assertFalse(verdict.agrees(), "a population three times as slow is rejected");
        }

        @Test
        @DisplayName("an empty population")
        void shouldRejectEmptyPopulation() {
            var samples = quiet(quietBase());
            List<Long> none = List.of();

            assertThrows(IllegalArgumentException.class,
                    () -> ColdStartComparison.compare(none, samples, RELATIVE, ABSOLUTE),
                    "an empty population must be refused");
        }
    }

    @ParameterizedTest(name = "seeded {0} ns against empty 10000000 ns agrees: {1}")
    @CsvSource({"29999998, true", "30000000, true", "30000002, false", "30000004, false"})
    @DisplayName("at the tolerance edge, a difference just inside the band agrees and just outside is rejected "
            + "(the edge values are the specified tolerance)")
    void shouldHonourTheToleranceEdge(long seededNanos, boolean expected) {
        // allowed = 0.5 * 30 ms + 5 ms = 20 ms, exactly the difference of 30 ms and 10 ms
        var empty = constant(10_000_000L);
        var seeded = constant(seededNanos);

        var verdict = ColdStartComparison.compare(empty, seeded, RELATIVE, ABSOLUTE);

        assertEquals(expected, verdict.agrees(), "the tolerance edge must decide the agreement");
    }

    @Test
    @DisplayName("describes both statistics of both populations and the allowed difference (the text is the format)")
    void shouldDescribeEveryNumber() {
        var numbers = Generators.longs(0, 1_000_000_000L);
        var emptyMedian = numbers.next();
        var seededMedian = numbers.next();
        var emptyQuartile = numbers.next();
        var seededQuartile = numbers.next();
        var allowed = numbers.next();
        var projects = Generators.integers(1, 10_000).next();
        var verdict = new ColdStartComparison.Verdict(emptyMedian, seededMedian, emptyQuartile, seededQuartile,
                allowed);

        var description = verdict.describe(0, projects);

        assertEquals("lower quartiles %d ns (0 projects) and %d ns (%d projects), allowed difference %d ns;".formatted(
                emptyQuartile, seededQuartile, projects, allowed)
                + " medians %d ns (0 projects) and %d ns (%d projects)".formatted(emptyMedian, seededMedian, projects),
                description, "the description must name every number");
    }
}
