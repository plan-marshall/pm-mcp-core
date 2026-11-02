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

import java.util.Objects;
import lombok.Builder;

/**
 * One recipe finding of the OpenRewrite log, parsed from a structured {@code WARN} line.
 * <p>
 * The five fields are the ones the recipe's log template carries — source path, line, column, recipe and message — and the
 * classification is the machine reading of the line's identifier.
 *
 * @param path           the source path the recipe pointed at, never blank
 * @param line           the line in that file, never negative
 * @param column         the column in that line, never negative
 * @param recipe         the recipe that produced the finding, never blank
 * @param message        the finding message, possibly empty
 * @param classification whether the finding is new in this run
 * @since 0.1
 */
@Builder
public record RewriteFinding(
String path,
int line,
int column,
String recipe,
String message,
RewriteClassification classification) {

    /**
     * Validates the finding.
     *
     * @throws IllegalArgumentException if the path or the recipe is blank, or a location is negative
     * @throws NullPointerException     if a field is null
     */
    public RewriteFinding {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(classification, "classification");
        if (path.isBlank()) {
            throw new IllegalArgumentException("a finding path must not be blank");
        }
        if (recipe.isBlank()) {
            throw new IllegalArgumentException("a finding recipe must not be blank");
        }
        if (line < 0) {
            throw new IllegalArgumentException("a finding line must not be negative: " + line);
        }
        if (column < 0) {
            throw new IllegalArgumentException("a finding column must not be negative: " + column);
        }
    }
}
