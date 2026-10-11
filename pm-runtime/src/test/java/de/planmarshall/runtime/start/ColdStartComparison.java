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

import java.time.Duration;
import java.util.List;

/**
 * The verdict of the {@code PM-TECH-3} guard of {@link StartupColdStartIT}: whether two populations of cold start
 * times, one on an empty base and one on a seeded base, agree.
 * <p>
 * Noise on a shared machine is one-sided: it only ever adds time to a run. The median of a population therefore
 * still carries it once more than half of the runs are disturbed, while the fastest runs estimate the intrinsic cost
 * of the work. The comparison asserts on the {@linkplain #QUANTILE lower quartile} of each population: the two
 * quartiles must lie within the relative tolerance of the larger one plus the absolute tolerance. The medians are
 * reported beside it, because the documented JVM figure is a median.
 */
final class ColdStartComparison {

    /** The quantile of each population that is compared: the fastest quartile. */
    static final double QUANTILE = 0.25;

    private ColdStartComparison() {
    }

    /**
     * The outcome of a comparison, with every number it rests on.
     *
     * @param emptyMedian the median of the empty population, in nanoseconds
     * @param seededMedian the median of the seeded population, in nanoseconds
     * @param emptyQuartile the lower quartile of the empty population, in nanoseconds
     * @param seededQuartile the lower quartile of the seeded population, in nanoseconds
     * @param allowed the largest difference of the quartiles that still agrees, in nanoseconds
     */
    record Verdict(long emptyMedian, long seededMedian, long emptyQuartile, long seededQuartile, long allowed) {

        /** @return whether the lower quartiles of the two populations agree within {@link #allowed()} */
        boolean agrees() {
            return Math.abs(seededQuartile - emptyQuartile) <= allowed;
        }

        /**
         * @param emptyProjects the number of enrolled projects of the empty population
         * @param seededProjects the number of enrolled projects of the seeded population
         * @return a description naming both statistics of both populations and the allowed difference
         */
        String describe(int emptyProjects, int seededProjects) {
            return ("lower quartiles %d ns (%d projects) and %d ns (%d projects), allowed difference %d ns;"
                    + " medians %d ns (%d projects) and %d ns (%d projects)").formatted(emptyQuartile, emptyProjects,
                    seededQuartile, seededProjects, allowed, emptyMedian, emptyProjects, seededMedian,
                    seededProjects);
        }
    }

    /**
     * Compares two populations.
     *
     * @param empty the times of the runs on the empty base, in nanoseconds; not empty
     * @param seeded the times of the runs on the seeded base, in nanoseconds; not empty
     * @param relativeTolerance how far the quartiles may lie apart, relative to the larger one
     * @param absoluteTolerance how far the quartiles may lie apart besides the relative tolerance
     * @return the verdict
     * @throws IllegalArgumentException if a population is empty
     */
    static Verdict compare(List<Long> empty, List<Long> seeded, double relativeTolerance,
            Duration absoluteTolerance) {
        var emptySorted = sorted(empty);
        var seededSorted = sorted(seeded);
        var emptyQuartile = quantile(emptySorted);
        var seededQuartile = quantile(seededSorted);
        var allowed = (long) (relativeTolerance * Math.max(emptyQuartile, seededQuartile))
                + absoluteTolerance.toNanos();
        return new Verdict(median(emptySorted), median(seededSorted), emptyQuartile, seededQuartile, allowed);
    }

    private static long[] sorted(List<Long> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("a population needs at least one sample");
        }
        return samples.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    /** The nearest-rank quantile: the smallest sample with at least {@link #QUANTILE} of the samples at or below. */
    private static long quantile(long[] sorted) {
        var rank = (int) Math.ceil(QUANTILE * sorted.length);
        return sorted[Math.max(rank, 1) - 1];
    }

    private static long median(long[] sorted) {
        var middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }
}
