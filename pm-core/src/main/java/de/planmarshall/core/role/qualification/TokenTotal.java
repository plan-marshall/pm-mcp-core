/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

/**
 * The tokens a grading item or form used (Store Schemas § Role Qualification Record, {@code total_tokens}): a measured
 * count of at least one, or the explicit mark {@code unmeasured} when the harness reported none.
 *
 * @param tokens the count; {@code null} when unmeasured
 * @since 0.1
 */
public record TokenTotal(Long tokens) {

    /** The one word a record writes for an unmeasured count. */
    public static final String UNMEASURED = "unmeasured";

    /**
     * @throws IllegalArgumentException if the count is not positive
     */
    public TokenTotal {
        if (tokens != null && tokens < 1) {
            throw new IllegalArgumentException("'total_tokens' must be at least 1: " + tokens);
        }
    }

    /**
     * @param tokens the measured count
     * @return the total
     */
    public static TokenTotal measured(long tokens) {
        return new TokenTotal(tokens);
    }

    /** @return the unmeasured total */
    public static TokenTotal unmeasured() {
        return new TokenTotal(null);
    }

    /** @return {@code true} if the count is measured */
    public boolean measured() {
        return tokens != null;
    }

    /**
     * @param other another total
     * @return the sum; unmeasured if either is
     */
    public TokenTotal plus(TokenTotal other) {
        if (tokens == null || other.tokens == null) {
            return unmeasured();
        }
        return new TokenTotal(tokens + other.tokens);
    }
}
