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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.analysis.AnalysisResult.Comparator;
import de.planmarshall.core.analysis.AnalysisResult.Condition;
import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.analysis.AnalysisResult.Gate;
import de.planmarshall.core.analysis.AnalysisResult.GateResult;
import de.planmarshall.core.findings.FindingScope;

/**
 * The analysis result record with the consistency rules of the specification and the structural rules of its schema
 * (PM-IMPL-2).
 */
@EnableGeneratorController
@DisplayName("Analysis result")
class AnalysisResultTest {

    private static final String PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String SHA = "%040x".formatted(Generators.longs().next());
    private static final Instant OBSERVED_AT =
            Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String ANALYSIS_REF = Generators.letterStrings(5, 20).next();
    private static final String METRIC = Generators.letterStrings(3, 20).next();
    private static final String THRESHOLD = Integer.toString(Generators.integers(92, 100).next());
    private static final Condition PASSED_CONDITION = new Condition("new_coverage", Comparator.LT, THRESHOLD, "91.5",
            GateResult.PASSED);
    private static final Condition FAILED_CONDITION = new Condition("new_violations", Comparator.GT, "0", "3",
            GateResult.FAILED);
    private static final Gate PASSED_GATE = new Gate(GateResult.PASSED, List.of(PASSED_CONDITION));
    private static final Gate FAILED_GATE = new Gate(GateResult.FAILED, List.of(PASSED_CONDITION, FAILED_CONDITION));

    private static AnalysisResult.AnalysisResultBuilder passed() {
        return AnalysisResult.builder()
                .formatVersion(AnalysisResult.FORMAT_VERSION)
                .provider(PROVIDER)
                .target(FindingScope.commit(SHA))
                .status(AnalysisStatus.PASSED)
                .gate(null)
                .countStatus(CountStatus.MEASURED)
                .newCodeIssueCount(0L)
                .issuesFetched(0)
                .workPerformed(false)
                .analysisRef(ANALYSIS_REF)
                .observedAt(OBSERVED_AT);
    }

    private static void assertRefused(String rule, Executable construction) {
        var refusal = assertThrows(IllegalArgumentException.class,
                construction, "an invalid result is refused as illegal argument");
        assertTrue(refusal.getMessage().contains(rule), "the refusal message names the broken rule");
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @Test
        @DisplayName("a passed commit result without a gate keeps its members")
        void passedCommitWithoutGate() {
            var result = passed().build();

            assertAll(
                    () -> assertEquals(AnalysisResult.FORMAT_VERSION, result.formatVersion(),
                            "the format version is the one written"),
                    () -> assertEquals(PROVIDER, result.provider(), "the provider id is kept on the result"),
                    () -> assertEquals(FindingScope.commit(SHA), result.target(),
                            "the target is the commit under test"),
                    () -> assertEquals(AnalysisStatus.PASSED, result.status(), "the status is the passed status"),
                    () -> assertNull(result.gate(), "a passed result carries no gate"),
                    () -> assertEquals(CountStatus.MEASURED, result.countStatus(),
                            "the count status is measured"),
                    () -> assertEquals(0L, result.newCodeIssueCount(), "no new issues were counted"),
                    () -> assertEquals(0L, result.issuesFetched(), "no issues were fetched"),
                    () -> assertEquals(ANALYSIS_REF, result.analysisRef(),
                            "the analysis reference is carried over"),
                    () -> assertEquals(OBSERVED_AT, result.observedAt(),
                            "the observation instant is carried over"));
        }

        @Test
        @DisplayName("a passed pull-request result with a passed gate and its conditions is accepted")
        void passedPullRequestWithGate() {
            var result = passed().target(FindingScope.pullRequest(SHA, 42)).gate(PASSED_GATE).build();

            assertAll(
                    () -> assertEquals(FindingScope.Kind.PULL_REQUEST, result.target().kind(),
                            "a pull request target reports its kind"),
                    () -> assertEquals(List.of(PASSED_CONDITION), result.gate().conditions(),
                            "the gate holds its passed condition"));
        }

        @Test
        @DisplayName("a gate_failed result carries its failed gate")
        void gateFailed() {
            var result = passed().status(AnalysisStatus.GATE_FAILED).gate(FAILED_GATE).newCodeIssueCount(3L).build();

            assertEquals(GateResult.FAILED, result.gate().result(), "the gate result is failed");
        }

        @Test
        @DisplayName("a findings result has new issues and a passed gate")
        void findings() {
            var result = passed().status(AnalysisStatus.FINDINGS).gate(PASSED_GATE).newCodeIssueCount(2L)
                    .issuesFetched(2).workPerformed(true).build();

            assertAll(
                    () -> assertEquals(2L, result.newCodeIssueCount(), "two new issues were counted"),
                    () -> assertTrue(result.workPerformed(), "the findings result reports work performed"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisStatus.class, names = {"UNDECIDABLE", "RUNNING", "NOT_RUN"})
        @DisplayName("an indeterminate count with a null count is accepted for undecidable, running and not_run")
        void indeterminate(AnalysisStatus status) {
            var result = passed().status(status).countStatus(CountStatus.INDETERMINATE).newCodeIssueCount(null)
                    .analysisRef(null).build();

            assertAll(
                    () -> assertNull(result.newCodeIssueCount(), "an indeterminate count has no value"),
                    () -> assertNull(result.analysisRef(), "an indeterminate result has no analysis reference"));
        }

        @Test
        @DisplayName("the conditions of a gate are copied and cannot be changed")
        void conditionsCopied() {
            var conditions = new ArrayList<>(List.of(PASSED_CONDITION));
            var gate = new Gate(GateResult.PASSED, conditions);
            conditions.add(FAILED_CONDITION);
            var held = gate.conditions();

            assertAll(
                    () -> assertEquals(List.of(PASSED_CONDITION), held,
                            "the gate keeps the condition it was given"),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> held.add(FAILED_CONDITION),
                            "the conditions of a gate cannot be changed"));
        }

        @Test
        @DisplayName("a gate of 256 conditions and a condition without an actual value are accepted")
        void bounds() {
            var condition = new Condition("m".repeat(128), Comparator.EQ, "t".repeat(64), null, GateResult.PASSED);
            var gate = new Gate(GateResult.PASSED, Collections.nCopies(AnalysisResult.MAX_CONDITIONS, condition));
            var result = passed().gate(gate).analysisRef("r".repeat(256)).build();

            assertAll(
                    () -> assertEquals(AnalysisResult.MAX_CONDITIONS, result.gate().conditions().size(),
                            "the gate holds the maximum condition count"),
                    () -> assertNull(condition.actual(), "a condition may have no actual value"));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"MEASURED, measured", "INDETERMINATE, indeterminate"})
        @DisplayName("the count statuses carry their wire names and are found by them")
        void countStatusWireNames(CountStatus value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName(), "the count status carries its wire name"),
                    () -> assertEquals(Optional.of(value), CountStatus.fromWireName(wireName),
                            "its wire name finds the count status"));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"PASSED, passed", "FAILED, failed"})
        @DisplayName("the gate results carry their wire names and are found by them")
        void gateResultWireNames(GateResult value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName(), "the gate result carries its wire name"),
                    () -> assertEquals(Optional.of(value), GateResult.fromWireName(wireName),
                            "its wire name finds the gate result"));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"LT, lt", "LE, le", "GT, gt", "GE, ge", "EQ, eq", "NE, ne"})
        @DisplayName("the comparators carry their wire names and are found by them")
        void comparatorWireNames(Comparator value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName(), "the comparator carries its wire name"),
                    () -> assertEquals(Optional.of(value), Comparator.fromWireName(wireName),
                            "its wire name finds the comparator"));
        }

        @Test
        @DisplayName("an undeclared wire name finds no value")
        void undeclaredWireName() {
            assertAll(
                    () -> assertEquals(Optional.empty(), CountStatus.fromWireName("MEASURED"),
                            "an upper case count status name finds none"),
                    () -> assertEquals(Optional.empty(), GateResult.fromWireName("error"),
                            "an unknown gate result name finds none"),
                    () -> assertEquals(Optional.empty(), Comparator.fromWireName("<"),
                            "an unknown comparator name finds none"));
        }
    }

    @Nested
    @DisplayName("consistency rules")
    class ConsistencyRules {

        @Test
        @DisplayName("passed with an indeterminate count is refused")
        void passedIndeterminate() {
            assertRefused("status passed requires",
                    () -> passed().countStatus(CountStatus.INDETERMINATE).newCodeIssueCount(null).build());
        }

        @Test
        @DisplayName("passed with new issues is refused")
        void passedWithIssues() {
            assertRefused("status passed requires", () -> passed().newCodeIssueCount(1L).build());
        }

        @Test
        @DisplayName("passed with a failed gate is refused")
        void passedWithFailedGate() {
            assertRefused("status passed requires", () -> passed().gate(FAILED_GATE).build());
        }

        @Test
        @DisplayName("gate_failed without a gate is refused")
        void gateFailedWithoutGate() {
            assertRefused("status gate_failed requires", () -> passed().status(AnalysisStatus.GATE_FAILED).build());
        }

        @Test
        @DisplayName("gate_failed with a passed gate is refused")
        void gateFailedWithPassedGate() {
            assertRefused("status gate_failed requires",
                    () -> passed().status(AnalysisStatus.GATE_FAILED).gate(PASSED_GATE).build());
        }

        @Test
        @DisplayName("findings without new issues is refused")
        void findingsWithoutIssues() {
            assertRefused("status findings requires", () -> passed().status(AnalysisStatus.FINDINGS).build());
        }

        @Test
        @DisplayName("findings with a failed gate is refused")
        void findingsWithFailedGate() {
            assertRefused("status findings requires",
                    () -> passed().status(AnalysisStatus.FINDINGS).gate(FAILED_GATE).newCodeIssueCount(2L).build());
        }

        @Test
        @DisplayName("an indeterminate count of a status other than undecidable, running or not_run is refused")
        void indeterminateOtherStatus() {
            assertRefused("count_status indeterminate requires status undecidable",
                    () -> passed().status(AnalysisStatus.GATE_FAILED).gate(FAILED_GATE)
                            .countStatus(CountStatus.INDETERMINATE).newCodeIssueCount(null).build());
        }
    }

    @Nested
    @DisplayName("structural rules")
    class StructuralRules {

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, 2})
        @DisplayName("a format version other than 1 is refused")
        void formatVersion(int version) {
            assertRefused("format_version", () -> passed().formatVersion(version).build());
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"Sonar", "so", "-sonar", "sonar-", "son_ar"})
        @DisplayName("a provider outside the provider id pattern is refused")
        void provider(String provider) {
            assertRefused("provider", () -> passed().provider(provider).build());
        }

        @Test
        @DisplayName("a measured count without a value is refused")
        void measuredWithoutCount() {
            assertRefused("new_code_issue_count is null iff", () -> passed().newCodeIssueCount(null).build());
        }

        @Test
        @DisplayName("an indeterminate count with a value is refused")
        void indeterminateWithCount() {
            assertRefused("new_code_issue_count is null iff",
                    () -> passed().status(AnalysisStatus.UNDECIDABLE).countStatus(CountStatus.INDETERMINATE)
                            .newCodeIssueCount(0L).build());
        }

        @Test
        @DisplayName("a negative count of new issues is refused")
        void negativeCount() {
            assertRefused("new_code_issue_count must be at least 0",
                    () -> passed().status(AnalysisStatus.UNDECIDABLE).newCodeIssueCount(-1L).build());
        }

        @Test
        @DisplayName("a negative count of fetched issues is refused")
        void negativeIssuesFetched() {
            assertRefused("issues_fetched", () -> passed().issuesFetched(-1).build());
        }

        @Test
        @DisplayName("an analysis reference longer than 256 characters is refused")
        void analysisRefTooLong() {
            assertRefused("analysis_ref", () -> passed().analysisRef("r".repeat(257)).build());
        }

        @Test
        @DisplayName("a gate of more than 256 conditions is refused")
        void tooManyConditions() {
            var conditions = Collections.nCopies(AnalysisResult.MAX_CONDITIONS + 1, PASSED_CONDITION);

            assertRefused("gate.conditions", () -> new Gate(GateResult.PASSED, conditions));
        }

        @ParameterizedTest(name = "metric of {0} characters")
        @ValueSource(ints = {0, 129})
        @DisplayName("a metric outside 1 to 128 characters is refused")
        void metricLength(int length) {
            var metric = "m".repeat(length);

            assertRefused("metric",
                    () -> new Condition(metric, Comparator.LT, THRESHOLD, null, GateResult.PASSED));
        }

        @ParameterizedTest(name = "threshold of {0} characters")
        @ValueSource(ints = {0, 65})
        @DisplayName("a threshold outside 1 to 64 characters is refused")
        void thresholdLength(int length) {
            var threshold = "t".repeat(length);

            assertRefused("threshold",
                    () -> new Condition(METRIC, Comparator.LT, threshold, null, GateResult.PASSED));
        }

        @Test
        @DisplayName("an actual value longer than 64 characters is refused")
        void actualTooLong() {
            var actual = "a".repeat(65);

            assertRefused("actual",
                    () -> new Condition(METRIC, Comparator.LT, THRESHOLD, actual, GateResult.PASSED));
        }

        @Test
        @DisplayName("a missing required member is refused")
        void missingMembers() {
            var withoutProvider = passed().provider(null);
            var withoutTarget = passed().target(null);
            var withoutStatus = passed().status(null);
            var withoutCountStatus = passed().countStatus(null);
            var withoutObservedAt = passed().observedAt(null);

            assertAll(
                    () -> assertThrows(NullPointerException.class, withoutProvider::build,
                            "a result without a provider is refused"),
                    () -> assertThrows(NullPointerException.class, withoutTarget::build,
                            "a result without a target is refused"),
                    () -> assertThrows(NullPointerException.class, withoutStatus::build,
                            "a result without a status is refused"),
                    () -> assertThrows(NullPointerException.class, withoutCountStatus::build,
                            "a result without a count status is refused"),
                    () -> assertThrows(NullPointerException.class, withoutObservedAt::build,
                            "a result without observedAt is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Gate(null, List.of()),
                            "a gate without a result is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Gate(GateResult.PASSED, null),
                            "a gate without conditions is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition(null, Comparator.LT, THRESHOLD, null, GateResult.PASSED),
                            "a condition without a metric is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition(METRIC, null, THRESHOLD, null, GateResult.PASSED),
                            "a condition without a comparator is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition(METRIC, Comparator.LT, null, null, GateResult.PASSED),
                            "a condition without a threshold is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition(METRIC, Comparator.LT, THRESHOLD, null, null),
                            "a condition without a gate result is refused"));
        }
    }
}
