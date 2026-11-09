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

/**
 * The result of parsing one coverage report: either the parsed {@link CoverageReport} or a refusal that names the format
 * and the reason it was refused.
 * <p>
 * The two states are two types, so a refused report cannot be read as a report whose percentages are zero. That is the
 * fail-closed rule the specification states: an unreadable or truncated report is a refusal, never {@code 0 %}.
 *
 * @since 0.1
 */
public sealed interface CoverageParseResult {

    /**
     * A report the parser read completely.
     *
     * @param report the parsed report, never null
     * @since 0.1
     */
    record Parsed(CoverageReport report) implements CoverageParseResult {

        /**
         * Validates the parsed report.
         *
         * @throws NullPointerException if the report is null
         */
        public Parsed {
            Objects.requireNonNull(report, "report");
        }
    }

    /**
     * A report the parser refused.
     *
     * @param format the format that was refused, never null
     * @param reason the refusal's reason, naming the format and what was wrong, never blank
     * @since 0.1
     */
    record Refused(CoverageFormat format, String reason) implements CoverageParseResult {

        /**
         * Validates the refusal.
         *
         * @throws NullPointerException     if the format or the reason is null
         * @throws IllegalArgumentException if the reason is blank
         */
        public Refused {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("a refusal must carry a reason");
            }
        }
    }
}
