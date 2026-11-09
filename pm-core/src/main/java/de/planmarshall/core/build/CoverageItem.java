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
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One item of a coverage report — a class for the XML formats, a file for LCOV and Istanbul — with its line and branch
 * percentages and, where the format names them, the methods the item left untested.
 * <p>
 * The item is the unit the low-coverage list is decided on: {@link CoverageReport#evaluate(int)} compares its line
 * percentage alone. The {@code missedMethods} detail is the format's own answer, so a class whose methods all ran has an
 * empty list rather than a placeholder.
 *
 * @param name          the reported name: a class or a file, never null or blank
 * @param file          the source file the format reports beside the name, or {@code null} when it gives none
 * @param line          the line percentage, always present
 * @param branch        the branch percentage, or {@code null} when the format gives none
 * @param missedMethods the methods with a missed counter, in report order, never null
 * @since 0.1
 */
@Builder
public record CoverageItem(
String name,
@Nullable String file,
double line,
@Nullable Double branch,
List<String> missedMethods) {

    /**
     * Validates the item and freezes the method list.
     *
     * @throws IllegalArgumentException if the name or file is blank or a percentage is outside {@code 0..100}
     * @throws NullPointerException     if the name or the method list is null
     */
    public CoverageItem {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("coverage item name must not be blank");
        }
        if (file != null && file.isBlank()) {
            throw new IllegalArgumentException("coverage item file must not be blank");
        }
        requirePercentage("line", line);
        if (branch != null) {
            requirePercentage("branch", branch.doubleValue());
        }
        missedMethods = List.copyOf(missedMethods);
    }

    private static void requirePercentage(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("coverage " + name + " must be within 0..100: " + value);
        }
    }
}
