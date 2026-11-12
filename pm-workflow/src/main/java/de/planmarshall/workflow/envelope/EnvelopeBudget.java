/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import java.util.Objects;

import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The resolution of the per-envelope budget the {@link EnvelopePacker} packs under.
 * <p>
 * The first value given wins, in this order: the operator's override per project ({@code envelope_budget_tokens}),
 * the context budget of the envelope role's artifact ({@code context_budget_tokens}), the harness fallback of the
 * harness profile ({@code headless.context_budget_tokens}) and the compiled fallback of
 * {@value #COMPILED_FALLBACK_TOKENS} tokens. Either fallback is capped at the long-context threshold of the role's
 * model where one is given; an operator or role value above that threshold is kept and reported as
 * {@link Resolution#aboveThreshold()}, never capped. The resolution takes plain values only: it reads no store, no
 * role artifact and no harness profile.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}, § Cost Sizing, item 4.
 */
@UtilityClass
public class EnvelopeBudget {

    /** The compiled fallback budget in tokens, used when no other value is given. */
    public static final long COMPILED_FALLBACK_TOKENS = 400_000;

    /** Where a resolved budget comes from. */
    public enum Source {

        /** The operator's override per project, {@code envelope_budget_tokens}. */
        OPERATOR,

        /** The context budget of the envelope role's artifact, {@code context_budget_tokens}. */
        ROLE,

        /** The harness fallback of the harness profile, {@code headless.context_budget_tokens}. */
        PROFILE,

        /** The compiled fallback, {@link EnvelopeBudget#COMPILED_FALLBACK_TOKENS}. */
        COMPILED
    }

    /**
     * A resolved per-envelope budget.
     *
     * @param tokens         the budget in tokens
     * @param source         where the budget comes from
     * @param capped         {@code true} if a fallback was lowered to the long-context threshold
     * @param aboveThreshold {@code true} if an operator or role value above the long-context threshold was kept
     */
    @Builder
    public record Resolution(long tokens, Source source, boolean capped, boolean aboveThreshold) {

        /**
         * Requires the source.
         *
         * @param tokens         the budget in tokens
         * @param source         where the budget comes from
         * @param capped         whether a fallback was capped
         * @param aboveThreshold whether a kept value lies above the threshold
         */
        public Resolution {
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * Resolves the per-envelope budget.
     *
     * @param operatorTokens             the operator's override per project, or {@code null} if none is set
     * @param roleTokens                 the role artifact's context budget, or {@code null} if it sets none
     * @param profileTokens              the harness profile's fallback, or {@code null} if it sets none
     * @param longContextThresholdTokens the long-context threshold of the role's model, or {@code null} if its
     *                                   profile names none
     * @return the budget with its source; never absent, because the compiled fallback always applies last
     * @throws IllegalArgumentException if a given value is not positive
     */
    public static Resolution resolve(@Nullable Long operatorTokens, @Nullable Long roleTokens,
            @Nullable Long profileTokens, @Nullable Long longContextThresholdTokens) {
        requirePositive(operatorTokens, "operatorTokens");
        requirePositive(roleTokens, "roleTokens");
        requirePositive(profileTokens, "profileTokens");
        requirePositive(longContextThresholdTokens, "longContextThresholdTokens");
        if (operatorTokens != null) {
            return kept(operatorTokens, Source.OPERATOR, longContextThresholdTokens);
        }
        if (roleTokens != null) {
            return kept(roleTokens, Source.ROLE, longContextThresholdTokens);
        }
        if (profileTokens != null) {
            return cappedFallback(profileTokens, Source.PROFILE, longContextThresholdTokens);
        }
        return cappedFallback(COMPILED_FALLBACK_TOKENS, Source.COMPILED, longContextThresholdTokens);
    }

    private static Resolution kept(long tokens, Source source, @Nullable Long threshold) {
        return Resolution.builder()
                .tokens(tokens)
                .source(source)
                .capped(false)
                .aboveThreshold(threshold != null && tokens > threshold)
                .build();
    }

    private static Resolution cappedFallback(long tokens, Source source, @Nullable Long threshold) {
        long resolved = threshold != null ? Math.min(tokens, threshold) : tokens;
        return Resolution.builder()
                .tokens(resolved)
                .source(source)
                .capped(resolved < tokens)
                .aboveThreshold(false)
                .build();
    }

    private static void requirePositive(@Nullable Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("The value of " + name + " must be positive: " + value);
        }
    }
}
