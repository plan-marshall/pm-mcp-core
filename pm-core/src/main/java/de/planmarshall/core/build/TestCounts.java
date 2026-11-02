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

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;

/**
 * The test counts of a parsed build, as either a measurement or the explicit absence of one.
 * <p>
 * The two states are two types, so {@link TestsPopulation#UNMEASURED unmeasured} cannot carry a number: there is no
 * accessor to reach one. That is the deliberate change against the ported {@code plan-marshall} parser, which keeps the
 * summed counts of an unmeasured population (ADR {@code core-build/002}).
 * <p>
 * A measured population is aggregated over every summary block of a multi-module run — one block per module and per test
 * plugin — never the last block alone (the aggregation defect the implementation watch recorded as PM-WATCH-JOB-10,
 * closed by this package and deleted from the watch document).
 *
 * @since 0.1
 */
public sealed interface TestCounts {

    /**
     * The population of these counts.
     *
     * @return {@link TestsPopulation#MEASURED} for {@link Measured}, {@link TestsPopulation#UNMEASURED} for
     *         {@link Unmeasured}
     */
    TestsPopulation population();

    /**
     * Counts derived from summary blocks the parser recognised.
     *
     * @param passed   the tests that ran and passed, never negative
     * @param failed   the tests that ran and failed, never negative
     * @param skipped  the tests the runner declined to run, never negative
     * @param total    the collected count, {@code passed + failed + skipped}
     * @param byRunner the executed count per test runner that contributed, or {@code null} when the parser attributed
     *                 none or a parallel build makes the attribution unreliable
     * @since 0.1
     */
    @Builder
    record Measured(int passed, int failed, int skipped, int total, @Nullable Map<String, Integer> byRunner)
            implements TestCounts {

        /**
         * Validates the counts and copies the per-runner map.
         *
         * @throws IllegalArgumentException if a count is negative, {@code total} is not the sum of the three parts, or the
         *                                  per-runner map carries a negative executed count
         */
        public Measured {
            if (passed < 0 || failed < 0 || skipped < 0) {
                throw new IllegalArgumentException(
                        "test counts must not be negative: " + passed + "/" + failed + "/" + skipped);
            }
            long sum = (long) passed + failed + skipped;
            if (total != sum) {
                throw new IllegalArgumentException("total " + total + " is not " + passed + "+" + failed + "+" + skipped);
            }
            if (byRunner != null) {
                if (byRunner.values().stream().anyMatch(executed -> executed < 0)) {
                    throw new IllegalArgumentException("executed counts must not be negative: " + byRunner);
                }
                byRunner = Collections.unmodifiableMap(new LinkedHashMap<>(byRunner));
            }
        }

        @Override
        public TestsPopulation population() {
            return TestsPopulation.MEASURED;
        }

        /**
         * The number of tests that actually ran: {@code passed + failed}. A skipped test did not run, so it is not part
         * of this figure however many the suite collected.
         *
         * @return the executed count
         */
        public int executed() {
            return passed + failed;
        }
    }

    /**
     * The explicit absence of a test count: the parser could not aggregate the summaries of the run.
     * <p>
     * The state carries no number by construction.
     *
     * @since 0.1
     */
    enum Unmeasured implements TestCounts {

        /** The single value of this state. */
        INSTANCE;

        @Override
        public TestsPopulation population() {
            return TestsPopulation.UNMEASURED;
        }
    }

    /**
     * Returns the single unmeasured value.
     *
     * @return {@link Unmeasured#INSTANCE}
     */
    static TestCounts unmeasured() {
        return Unmeasured.INSTANCE;
    }

    /**
     * Builds a measurement from counts gathered as {@code long}, or the {@link Unmeasured} state when the counts cannot be
     * represented: a count outside the {@code int} range, a negative count, or a per-runner executed count below zero is
     * no measurement, and reporting one would let a wrapped sum pass as a figure.
     *
     * @param passed   the tests that passed
     * @param failed   the tests that failed
     * @param skipped  the tests the runner declined to run
     * @param byRunner the executed count per runner, or {@code null}
     * @return the measured counts, or {@link Unmeasured#INSTANCE}
     */
    public static TestCounts from(long passed, long failed, long skipped, @Nullable Map<String, Integer> byRunner) {
        long total = passed + failed + skipped;
        boolean representable = passed >= 0 && passed <= Integer.MAX_VALUE
                && failed >= 0 && failed <= Integer.MAX_VALUE
                && skipped >= 0 && skipped <= Integer.MAX_VALUE
                && total <= Integer.MAX_VALUE
                && (byRunner == null || byRunner.values().stream().noneMatch(executed -> executed < 0));
        if (!representable) {
            return Unmeasured.INSTANCE;
        }
        return new Measured((int) passed, (int) failed, (int) skipped, (int) total, byRunner);
    }
}
