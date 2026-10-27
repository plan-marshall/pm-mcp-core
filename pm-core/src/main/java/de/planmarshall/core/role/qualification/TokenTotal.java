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

import java.util.OptionalLong;

/**
 * The tokens a grading item or form used (Store Schemas § Role Qualification Record, {@code total_tokens}): a measured
 * count of at least one, or the explicit mark {@code unmeasured} when the harness reported none. Beside the total it
 * carries the split a view shows (PM-WF-14, Client Profiles § Tokens, Not Money): {@code tokens_in} (input, cache read
 * and cache creation) and {@code tokens_out} (output and reasoning), each measured or {@code unmeasured}. The split is
 * measured as a pair and sums to the total; a total whose split is unknown carries {@code unmeasured} at both, and is
 * shown as its total alone.
 *
 * @param tokens    the total; {@code null} when unmeasured
 * @param tokensIn  the input split; {@code null} when unmeasured
 * @param tokensOut the output split; {@code null} when unmeasured
 * @since 0.1
 */
public record TokenTotal(Long tokens, Long tokensIn, Long tokensOut) {

    /** The one word a record writes for an unmeasured count. */
    public static final String UNMEASURED_MARK = "unmeasured";

    /**
     * @throws IllegalArgumentException if the total is not positive, the split is not a measured pair, a split part is
     *                                  negative, the split does not sum to the total, or an unmeasured total carries a
     *                                  split
     */
    public TokenTotal {
        validate(tokens, tokensIn, tokensOut);
    }

    private static void validate(Long tokens, Long tokensIn, Long tokensOut) {
        if (tokens == null) {
            if (tokensIn != null || tokensOut != null) {
                throw new IllegalArgumentException("An unmeasured 'total_tokens' carries no split");
            }
            return;
        }
        if (tokens < 1) {
            throw new IllegalArgumentException("'total_tokens' must be at least 1: " + tokens);
        }
        if (tokensIn == null && tokensOut == null) {
            return;
        }
        if (tokensIn == null || tokensOut == null) {
            throw new IllegalArgumentException("The split is measured as a pair");
        }
        if (tokensIn < 0 || tokensOut < 0) {
            throw new IllegalArgumentException("A split part must not be negative: " + tokensIn + "/" + tokensOut);
        }
        if (tokensIn + tokensOut != tokens) {
            throw new IllegalArgumentException("The split %d + %d does not sum to 'total_tokens' %d"
                    .formatted(tokensIn, tokensOut, tokens));
        }
    }

    /**
     * @param tokens the total; {@code null} when unmeasured
     */
    public TokenTotal(Long tokens) {
        this(tokens, null, null);
    }

    /**
     * @param tokens the measured count
     * @return the total without a split
     */
    public static TokenTotal measured(long tokens) {
        return new TokenTotal(tokens, null, null);
    }

    /**
     * @param tokens    the measured count
     * @param tokensIn  the measured input split
     * @param tokensOut the measured output split
     * @return the total with its split
     */
    public static TokenTotal measured(long tokens, long tokensIn, long tokensOut) {
        return new TokenTotal(tokens, tokensIn, tokensOut);
    }

    /** @return the unmeasured total */
    public static TokenTotal unmeasured() {
        return new TokenTotal(null, null, null);
    }

    /** @return {@code true} if the count is measured */
    public boolean measured() {
        return tokens != null;
    }

    /** @return the input split, empty when it is unmeasured */
    public OptionalLong splitIn() {
        return tokensIn == null ? OptionalLong.empty() : OptionalLong.of(tokensIn);
    }

    /** @return the output split, empty when it is unmeasured */
    public OptionalLong splitOut() {
        return tokensOut == null ? OptionalLong.empty() : OptionalLong.of(tokensOut);
    }

    /**
     * @param other another total
     * @return the sum; unmeasured if either is, and without a split when either has none
     */
    public TokenTotal plus(TokenTotal other) {
        if (tokens == null || other.tokens == null) {
            return unmeasured();
        }
        if (tokensIn == null || other.tokensIn == null) {
            return measured(tokens + other.tokens);
        }
        return measured(tokens + other.tokens, tokensIn + other.tokensIn, tokensOut + other.tokensOut);
    }
}
