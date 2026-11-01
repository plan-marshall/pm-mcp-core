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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;

import de.planmarshall.core.findings.FindingScope;
import de.planmarshall.core.findings.FindingSourceKey;

/**
 * One observation of one code analysis provider for one target, as a line of {@code analysis.jsonl} records it
 * (PM-IMPL-2): what the provider reported about a commit or a pull request, in the provider-neutral form of the
 * code-analysis contract (PM-IMPL-1). The components are exactly the members of the schema {@code AnalysisResult}
 * (Store Schemas § Analysis Result Record), in schema order. The new issues of an observation are not part of the
 * record; they are findings of the findings ledger with the source {@code analysis} and the same provider.
 * <p>
 * The compact constructor checks the consistency rules of the specification and the structural rules of the schema,
 * and refuses a violation with an {@link IllegalArgumentException} naming the rule. A required member that is
 * {@code null} is refused with a {@link NullPointerException} naming the member. The canonical JSON line of the
 * record is written and read by {@link AnalysisResultCodec}.
 *
 * @param formatVersion     the version of the record format; always 1
 * @param provider          the id of the code analysis provider, as registered in pm-core
 * @param target            the commit or pull request the provider analysed
 * @param status            the provider's result mapped to the closed analysis status
 * @param gate              the provider's quality gate for the target; {@code null} when the provider has no gate or
 *                          has not evaluated one
 * @param countStatus       whether the count of new issues was established
 * @param newCodeIssueCount the open issues on new code, at least 0; {@code null} iff {@code countStatus} is
 *                          {@link CountStatus#INDETERMINATE}
 * @param issuesFetched     the issues of this observation read from the provider and filed or matched in the
 *                          findings ledger, at least 0
 * @param workPerformed     whether this observation changed the findings ledger
 * @param analysisRef       the provider's own identifier of the analysis, at most 256 characters, untrusted;
 *                          nullable
 * @param observedAt        the instant of the observation
 * @since 0.1
 */
@Builder(toBuilder = true)
public record AnalysisResult(int formatVersion, String provider, FindingScope target, AnalysisStatus status,
@Nullable Gate gate, CountStatus countStatus, @Nullable Long newCodeIssueCount, long issuesFetched,
boolean workPerformed, @Nullable String analysisRef, Instant observedAt) {

    /** The only version of the record format. */
    public static final int FORMAT_VERSION = 1;

    /** The most conditions a quality gate holds. */
    public static final int MAX_CONDITIONS = 256;

    private static final int ANALYSIS_REF_MAX_LENGTH = 256;
    private static final int METRIC_MAX_LENGTH = 128;
    private static final int THRESHOLD_MAX_LENGTH = 64;
    private static final int ACTUAL_MAX_LENGTH = 64;
    private static final String CHARACTERS = " characters";
    private static final Set<AnalysisStatus> INDETERMINATE_ALLOWED = EnumSet.of(AnalysisStatus.UNDECIDABLE,
            AnalysisStatus.RUNNING, AnalysisStatus.NOT_RUN);

    /**
     * @throws NullPointerException     if a required member is {@code null}
     * @throws IllegalArgumentException if a consistency or structural rule is violated; the message names the rule
     */
    public AnalysisResult {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(countStatus, "count_status");
        Objects.requireNonNull(observedAt, "observed_at");
        requireThat(formatVersion == FORMAT_VERSION, "format_version must be " + FORMAT_VERSION);
        requireThat(FindingSourceKey.isProviderId(provider), "provider must match the provider id pattern");
        requireThat((countStatus == CountStatus.INDETERMINATE) == (newCodeIssueCount == null),
                "new_code_issue_count is null iff count_status is indeterminate");
        requireThat(newCodeIssueCount == null || newCodeIssueCount >= 0, "new_code_issue_count must be at least 0");
        requireThat(issuesFetched >= 0, "issues_fetched must be at least 0");
        requireThat(analysisRef == null || analysisRef.length() <= ANALYSIS_REF_MAX_LENGTH,
                "analysis_ref must be at most " + ANALYSIS_REF_MAX_LENGTH + CHARACTERS);
        checkConsistency(status, gate, countStatus, newCodeIssueCount);
    }

    private static void checkConsistency(AnalysisStatus status, @Nullable Gate gate, CountStatus countStatus,
            @Nullable Long count) {
        var gateFailed = gate != null && gate.result() == GateResult.FAILED;
        switch (status) {
            case PASSED -> requireThat(countStatus == CountStatus.MEASURED && count == 0 && !gateFailed,
                    "status passed requires count_status measured, new_code_issue_count 0 and a gate that is null "
                            + "or passed");
            case GATE_FAILED -> requireThat(gateFailed, "status gate_failed requires gate.result failed");
            case FINDINGS -> requireThat(count != null && count > 0 && !gateFailed,
                    "status findings requires new_code_issue_count greater than 0 and no failed gate");
            default -> {
                // running, undecidable and not_run state no rule of their own
            }
        }
        requireThat(countStatus != CountStatus.INDETERMINATE || INDETERMINATE_ALLOWED.contains(status),
                "count_status indeterminate requires status undecidable unless the status is running or not_run");
    }

    private static void requireThat(boolean rule, String message) {
        if (!rule) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * The quality gate of a provider for a target (member {@code gate}).
     *
     * @param result     whether the gate passed or failed
     * @param conditions the conditions of the gate, at most {@value AnalysisResult#MAX_CONDITIONS}, in the
     *                   provider's order
     */
    public record Gate(GateResult result, List<Condition> conditions) {

        /**
         * @throws NullPointerException     if the result, the conditions or one of them is {@code null}
         * @throws IllegalArgumentException if the gate holds more than {@value AnalysisResult#MAX_CONDITIONS}
         *                                  conditions
         */
        public Gate {
            Objects.requireNonNull(result, "gate.result");
            conditions = List.copyOf(Objects.requireNonNull(conditions, "gate.conditions"));
            requireThat(conditions.size() <= MAX_CONDITIONS,
                    "gate.conditions must hold at most " + MAX_CONDITIONS + " conditions");
        }
    }

    /**
     * One condition of a quality gate. Its strings come from the provider: they are untrusted, length-bounded and
     * stored as data only.
     *
     * @param metric     the provider's name of what the condition measures, 1 to 128 characters
     * @param comparator the comparison under which the condition fails
     * @param threshold  the threshold, 1 to 64 characters
     * @param actual     the measured value, at most 64 characters; {@code null} when the provider reports none
     * @param result     whether the condition passed or failed
     */
    public record Condition(String metric, Comparator comparator, String threshold, @Nullable String actual,
    GateResult result) {

        /**
         * @throws NullPointerException     if a member other than {@code actual} is {@code null}
         * @throws IllegalArgumentException if a string is outside its length bounds
         */
        public Condition {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(comparator, "comparator");
            Objects.requireNonNull(threshold, "threshold");
            Objects.requireNonNull(result, "result");
            requireThat(!metric.isEmpty() && metric.length() <= METRIC_MAX_LENGTH,
                    "metric must be 1 to " + METRIC_MAX_LENGTH + CHARACTERS);
            requireThat(!threshold.isEmpty() && threshold.length() <= THRESHOLD_MAX_LENGTH,
                    "threshold must be 1 to " + THRESHOLD_MAX_LENGTH + CHARACTERS);
            requireThat(actual == null || actual.length() <= ACTUAL_MAX_LENGTH,
                    "actual must be at most " + ACTUAL_MAX_LENGTH + CHARACTERS);
        }
    }

    /**
     * Whether the count of new issues was established (member {@code count_status}).
     */
    public enum CountStatus {
        /** The count was established. */
        MEASURED("measured"),
        /** The count was not established; the status never passes. */
        INDETERMINATE("indeterminate");

        private final String wireName;

        CountStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the analysis log */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the analysis log
         * @return the value of that name; empty if no value has it
         */
        public static Optional<CountStatus> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The result of a quality gate or of one of its conditions.
     */
    public enum GateResult {
        /** The gate or condition passed. */
        PASSED("passed"),
        /** The gate or condition failed. */
        FAILED("failed");

        private final String wireName;

        GateResult(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the analysis log */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the analysis log
         * @return the value of that name; empty if no value has it
         */
        public static Optional<GateResult> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The comparison of a gate condition: the condition fails when {@code actual <comparator> threshold} holds.
     */
    public enum Comparator {
        /** Less than. */
        LT("lt"),
        /** Less than or equal. */
        LE("le"),
        /** Greater than. */
        GT("gt"),
        /** Greater than or equal. */
        GE("ge"),
        /** Equal. */
        EQ("eq"),
        /** Not equal. */
        NE("ne");

        private final String wireName;

        Comparator(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the analysis log */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the analysis log
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Comparator> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }
}
