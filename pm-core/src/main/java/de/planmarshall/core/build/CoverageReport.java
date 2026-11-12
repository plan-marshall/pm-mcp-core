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

import java.util.List;
import java.util.Objects;

/**
 * A parsed coverage report: the overall percentages, the per-item percentages and the format they came from.
 * <p>
 * The model carries every metric the format gives, and the verdict is decided from the line percentage alone
 * ({@link #evaluate(int)}): the threshold compares {@link #overall()} {@code line} with it, and the low-coverage list
 * compares each {@link CoverageItem#line()}. A metric outside the line never changes either, so a report whose branch
 * coverage lies below the threshold passes when its line coverage does not.
 *
 * @param format  the format the report was parsed from, never null
 * @param overall the overall percentages, never null
 * @param items   the per-item percentages, never null
 * @since 0.1
 */
public record CoverageReport(CoverageFormat format, CoverageMetrics overall, List<CoverageItem> items) {

    /**
     * Validates the report and freezes the item list.
     *
     * @throws NullPointerException if the format, the overall metrics or the item list is null
     */
    public CoverageReport {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(overall, "overall");
        items = List.copyOf(items);
    }

    /**
     * Decides the threshold verdict from the overall line percentage and collects the items below the threshold by their
     * line percentage.
     * <p>
     * The comparison is {@code >=}: a line percentage exactly at the threshold passes. No other metric is consulted.
     * <p>
     * The percentages compared are the ones the parser stored, already rounded to two decimals, so the rounding happens
     * before the comparison: a line coverage of {@code 79.996} is stored as {@code 80.0} and passes a threshold of
     * {@code 80}.
     *
     * @param threshold the minimum line percentage to pass, within {@code 0..100}
     * @return the verdict for this report
     * @throws IllegalArgumentException if the threshold is outside {@code 0..100}
     */
    public CoverageVerdict evaluate(int threshold) {
        List<CoverageItem> lowCoverage = items.stream()
                .filter(item -> item.line() < threshold)
                .toList();
        return CoverageVerdict.builder()
                .passed(overall.line() >= threshold)
                .threshold(threshold)
                .overallLine(overall.line())
                .lowCoverage(lowCoverage)
                .build();
    }
}
