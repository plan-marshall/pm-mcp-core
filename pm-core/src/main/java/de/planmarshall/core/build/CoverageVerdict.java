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
import lombok.Builder;

/**
 * The threshold verdict of a coverage report: whether the overall line percentage met the threshold, and which items fell
 * below it by their line percentage.
 *
 * @param passed      whether the overall line percentage met the threshold
 * @param threshold   the minimum line percentage the verdict was decided with
 * @param overallLine the overall line percentage the verdict was decided from
 * @param lowCoverage the items whose line percentage is below the threshold, in report order
 * @since 0.1
 */
@Builder
public record CoverageVerdict(boolean passed, int threshold, double overallLine, List<CoverageItem> lowCoverage) {

    /**
     * Validates the verdict and freezes the low-coverage list.
     *
     * @throws IllegalArgumentException if the threshold or the overall line percentage is outside {@code 0..100}
     */
    public CoverageVerdict {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("threshold must be within 0..100: " + threshold);
        }
        if (!Double.isFinite(overallLine) || overallLine < 0 || overallLine > 100) {
            throw new IllegalArgumentException("overall line must be within 0..100: " + overallLine);
        }
        lowCoverage = List.copyOf(lowCoverage);
    }
}
