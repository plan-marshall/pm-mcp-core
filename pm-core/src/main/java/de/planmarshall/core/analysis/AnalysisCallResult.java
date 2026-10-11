/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.findings.Finding;

/**
 * The result of a call to a code analysis provider with its closed outcome (PM-IMPL-1).
 * <p>
 * {@link AnalysisOutcome#OK} carries a value; a transport outcome carries neither value nor completeness and counts
 * as {@code undecidable} in the wait. The completeness belongs to {@code analysis.issues} alone: an {@code OK}
 * result of the issues, a list of findings, carries it, and a result of {@code analysis.status}, an analysis result,
 * never does. The two named factories {@link #status} and {@link #issues} build the result of each operation; the
 * compact constructor checks every rule, so a result breaking one is never built. A violation is refused with an
 * {@link IllegalArgumentException} naming the rule.
 *
 * @param outcome      the outcome of the call
 * @param value        the value of an {@code OK} result; {@code null} for a transport outcome
 * @param completeness whether the issues were read completely, for an {@code OK} result of {@code analysis.issues};
 *                     {@code null} otherwise
 * @param <V>          the value type: {@link AnalysisResult} for {@code analysis.status}, a list of
 *                     {@link Finding} for {@code analysis.issues}
 * @since 0.1
 */
public record AnalysisCallResult<V>(AnalysisOutcome outcome, V value, Completeness completeness) {

    /**
     * @throws NullPointerException     if the outcome is {@code null}
     * @throws IllegalArgumentException if a transport outcome carries a value or a completeness, an {@code OK}
     *                                  result has no value, an analysis result carries a completeness, or an
     *                                  {@code OK} result of the issues has none
     */
    public AnalysisCallResult {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == AnalysisOutcome.OK) {
            requireThat(value != null, "outcome ok requires a value");
        } else {
            requireThat(value == null, "transport outcome " + outcome.wireName() + " carries no value");
            requireThat(completeness == null,
                    "transport outcome " + outcome.wireName() + " carries no completeness");
        }
        requireThat(!(value instanceof AnalysisResult) || completeness == null,
                "the result of analysis.status carries no completeness");
        requireThat(!(value instanceof List<?>) || completeness != null,
                "an ok result of analysis.issues requires a completeness");
    }

    /**
     * Builds the result of {@code analysis.status}, which never carries a completeness.
     *
     * @param outcome the outcome of the call
     * @param value   the analysis result for {@link AnalysisOutcome#OK}; {@code null} for a transport outcome
     * @return the result of the call
     * @throws IllegalArgumentException if the value does not match the outcome
     */
    public static AnalysisCallResult<AnalysisResult> status(AnalysisOutcome outcome, AnalysisResult value) {
        return new AnalysisCallResult<>(outcome, value, null);
    }

    /**
     * Builds the result of {@code analysis.issues}.
     *
     * @param outcome      the outcome of the call
     * @param value        the issues, as findings of the source {@code analysis}, for {@link AnalysisOutcome#OK};
     *                     {@code null} for a transport outcome
     * @param completeness whether every issue was read, for {@link AnalysisOutcome#OK}; {@code null} for a
     *                     transport outcome
     * @return the result of the call; the issues are copied
     * @throws IllegalArgumentException if the value or the completeness does not match the outcome
     */
    public static AnalysisCallResult<List<Finding>> issues(AnalysisOutcome outcome, List<Finding> value,
            Completeness completeness) {
        return new AnalysisCallResult<>(outcome, value == null ? null : List.copyOf(value), completeness);
    }

    /**
     * @return whether the outcome is {@link AnalysisOutcome#OK}
     */
    public boolean isOk() {
        return outcome == AnalysisOutcome.OK;
    }

    private static void requireThat(boolean rule, String message) {
        if (!rule) {
            throw new IllegalArgumentException(message);
        }
    }
}
