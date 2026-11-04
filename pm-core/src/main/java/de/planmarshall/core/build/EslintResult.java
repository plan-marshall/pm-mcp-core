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

/**
 * The result of parsing an ESLint log: the parsed build and, when the log printed it, the problem summary.
 * <p>
 * The summary is a fact of its own because it is not derivable from the diagnostics: the stylish formatter prints a
 * summary row for every problem it found, while a row the scanner cannot place is not filed as a diagnostic. The two
 * counts therefore disagree on a real log, and collapsing them would hide the difference (ADR {@code core-build/003}).
 *
 * @param build    the parsed build, carrying the placed diagnostics and the status
 * @param problems the printed problem summary, or {@code null} when the log carried none
 * @since 0.1
 */
public record EslintResult(ParsedBuild build, @Nullable ProblemCounts problems) {

    /**
     * The problem counts ESLint printed in its summary row.
     *
     * @param errors   the number of problems of severity {@code error}, never negative
     * @param warnings the number of problems of severity {@code warning}, never negative
     * @since 0.1
     */
    public record ProblemCounts(int errors, int warnings) {

        /**
         * Validates the counts.
         *
         * @throws IllegalArgumentException if a count is negative
         */
        public ProblemCounts {
            if (errors < 0 || warnings < 0) {
                throw new IllegalArgumentException("problem counts must not be negative: " + errors + "/" + warnings);
            }
        }

        /**
         * The number of problems the summary reports.
         *
         * @return {@code errors + warnings}
         */
        public long total() {
            return (long) errors + warnings;
        }
    }
}
