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

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One set of coverage percentages as its report format gives them.
 * <p>
 * The line percentage is always present; every other metric is {@code null} when the format carries no such counter, so
 * a consumer can tell an absent metric from a measured zero. The {@link #function()} metric is JaCoCo's {@code METHOD}
 * counter and LCOV's and Istanbul's function counter, which is why it is named for the coverage rather than for one
 * tool's spelling.
 * <p>
 * The values never feed a threshold: {@link CoverageReport#evaluate(int)} compares the line percentage alone.
 *
 * @param instruction the instruction percentage, or {@code null} when the format gives none
 * @param branch      the branch percentage, or {@code null} when the format gives none
 * @param line        the line percentage, always present
 * @param function    the method or function percentage, or {@code null} when the format gives none
 * @param statement   the statement percentage, or {@code null} when the format gives none
 * @since 0.1
 */
@Builder
public record CoverageMetrics(
@Nullable Double instruction,
@Nullable Double branch,
double line,
@Nullable Double function,
@Nullable Double statement) {

    /**
     * Validates the percentages.
     *
     * @throws IllegalArgumentException if a percentage is outside {@code 0..100}
     */
    public CoverageMetrics {
        requirePercentage("line", line);
        requirePercentage("instruction", instruction);
        requirePercentage("branch", branch);
        requirePercentage("function", function);
        requirePercentage("statement", statement);
    }

    private static void requirePercentage(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("coverage " + name + " must be within 0..100: " + value);
        }
    }

    private static void requirePercentage(String name, @Nullable Double value) {
        if (value != null) {
            requirePercentage(name, value.doubleValue());
        }
    }
}
