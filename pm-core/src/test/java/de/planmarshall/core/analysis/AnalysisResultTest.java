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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
@DisplayName("Analysis result")
class AnalysisResultTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-11T12:00:00Z");
    private static final Condition PASSED_CONDITION = new Condition("new_coverage", Comparator.LT, "80", "91.5",
            GateResult.PASSED);
    private static final Condition FAILED_CONDITION = new Condition("new_violations", Comparator.GT, "0", "3",
            GateResult.FAILED);
    private static final Gate PASSED_GATE = new Gate(GateResult.PASSED, List.of(PASSED_CONDITION));
    private static final Gate FAILED_GATE = new Gate(GateResult.FAILED, List.of(PASSED_CONDITION, FAILED_CONDITION));

    private static AnalysisResult.AnalysisResultBuilder passed() {
        return AnalysisResult.builder()
                .formatVersion(AnalysisResult.FORMAT_VERSION)
                .provider("sonar")
                .target(FindingScope.commit(SHA))
                .status(AnalysisStatus.PASSED)
                .gate(null)
                .countStatus(CountStatus.MEASURED)
                .newCodeIssueCount(0L)
                .issuesFetched(0)
                .workPerformed(false)
                .analysisRef("AY-analysis-1")
                .observedAt(OBSERVED_AT);
    }

    private static void assertRefused(String rule, Executable construction) {
        var refusal = assertThrows(IllegalArgumentException.class, construction);
        assertTrue(refusal.getMessage().contains(rule),
                () -> "the refusal names the rule '%s': %s".formatted(rule, refusal.getMessage()));
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @Test
        @DisplayName("a passed commit result without a gate keeps its members")
        void passedCommitWithoutGate() {
            var result = passed().build();

            assertAll(
                    () -> assertEquals(AnalysisResult.FORMAT_VERSION, result.formatVersion()),
                    () -> assertEquals("sonar", result.provider()),
                    () -> assertEquals(FindingScope.commit(SHA), result.target()),
                    () -> assertEquals(AnalysisStatus.PASSED, result.status()),
                    () -> assertNull(result.gate()),
                    () -> assertEquals(CountStatus.MEASURED, result.countStatus()),
                    () -> assertEquals(0L, result.newCodeIssueCount()),
                    () -> assertEquals(0L, result.issuesFetched()),
                    () -> assertEquals("AY-analysis-1", result.analysisRef()),
                    () -> assertEquals(OBSERVED_AT, result.observedAt()));
        }

        @Test
        @DisplayName("a passed pull-request result with a passed gate and its conditions is accepted")
        void passedPullRequestWithGate() {
            var result = passed().target(FindingScope.pullRequest(SHA, 42)).gate(PASSED_GATE).build();

            assertAll(
                    () -> assertEquals(FindingScope.Kind.PULL_REQUEST, result.target().kind()),
                    () -> assertEquals(List.of(PASSED_CONDITION), result.gate().conditions()));
        }

        @Test
        @DisplayName("a gate_failed result carries its failed gate")
        void gateFailed() {
            var result = passed().status(AnalysisStatus.GATE_FAILED).gate(FAILED_GATE).newCodeIssueCount(3L).build();

            assertEquals(GateResult.FAILED, result.gate().result());
        }

        @Test
        @DisplayName("a findings result has new issues and a passed gate")
        void findings() {
            var result = passed().status(AnalysisStatus.FINDINGS).gate(PASSED_GATE).newCodeIssueCount(2L)
                    .issuesFetched(2).workPerformed(true).build();

            assertAll(
                    () -> assertEquals(2L, result.newCodeIssueCount()),
                    () -> assertTrue(result.workPerformed()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisStatus.class, names = {"UNDECIDABLE", "RUNNING", "NOT_RUN"})
        @DisplayName("an indeterminate count with a null count is accepted for undecidable, running and not_run")
        void indeterminate(AnalysisStatus status) {
            var result = passed().status(status).countStatus(CountStatus.INDETERMINATE).newCodeIssueCount(null)
                    .analysisRef(null).build();

            assertAll(
                    () -> assertNull(result.newCodeIssueCount()),
                    () -> assertNull(result.analysisRef()));
        }

        @Test
        @DisplayName("the conditions of a gate are copied and cannot be changed")
        void conditionsCopied() {
            var conditions = new ArrayList<>(List.of(PASSED_CONDITION));
            var gate = new Gate(GateResult.PASSED, conditions);
            conditions.add(FAILED_CONDITION);
            var held = gate.conditions();

            assertAll(
                    () -> assertEquals(List.of(PASSED_CONDITION), held),
                    () -> assertThrows(UnsupportedOperationException.class, () -> held.add(FAILED_CONDITION)));
        }

        @Test
        @DisplayName("a gate of 256 conditions and a condition without an actual value are accepted")
        void bounds() {
            var condition = new Condition("m".repeat(128), Comparator.EQ, "t".repeat(64), null, GateResult.PASSED);
            var gate = new Gate(GateResult.PASSED, Collections.nCopies(AnalysisResult.MAX_CONDITIONS, condition));
            var result = passed().gate(gate).analysisRef("r".repeat(256)).build();

            assertAll(
                    () -> assertEquals(AnalysisResult.MAX_CONDITIONS, result.gate().conditions().size()),
                    () -> assertNull(condition.actual()));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"MEASURED, measured", "INDETERMINATE, indeterminate"})
        @DisplayName("the count statuses carry their wire names and are found by them")
        void countStatusWireNames(CountStatus value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName()),
                    () -> assertEquals(Optional.of(value), CountStatus.fromWireName(wireName)));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"PASSED, passed", "FAILED, failed"})
        @DisplayName("the gate results carry their wire names and are found by them")
        void gateResultWireNames(GateResult value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName()),
                    () -> assertEquals(Optional.of(value), GateResult.fromWireName(wireName)));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"LT, lt", "LE, le", "GT, gt", "GE, ge", "EQ, eq", "NE, ne"})
        @DisplayName("the comparators carry their wire names and are found by them")
        void comparatorWireNames(Comparator value, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, value.wireName()),
                    () -> assertEquals(Optional.of(value), Comparator.fromWireName(wireName)));
        }

        @Test
        @DisplayName("an undeclared wire name finds no value")
        void undeclaredWireName() {
            assertAll(
                    () -> assertEquals(Optional.empty(), CountStatus.fromWireName("MEASURED")),
                    () -> assertEquals(Optional.empty(), GateResult.fromWireName("error")),
                    () -> assertEquals(Optional.empty(), Comparator.fromWireName("<")));
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
                    () -> new Condition(metric, Comparator.LT, "80", null, GateResult.PASSED));
        }

        @ParameterizedTest(name = "threshold of {0} characters")
        @ValueSource(ints = {0, 65})
        @DisplayName("a threshold outside 1 to 64 characters is refused")
        void thresholdLength(int length) {
            var threshold = "t".repeat(length);

            assertRefused("threshold",
                    () -> new Condition("coverage", Comparator.LT, threshold, null, GateResult.PASSED));
        }

        @Test
        @DisplayName("an actual value longer than 64 characters is refused")
        void actualTooLong() {
            var actual = "a".repeat(65);

            assertRefused("actual", () -> new Condition("coverage", Comparator.LT, "80", actual, GateResult.PASSED));
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
                    () -> assertThrows(NullPointerException.class, withoutProvider::build),
                    () -> assertThrows(NullPointerException.class, withoutTarget::build),
                    () -> assertThrows(NullPointerException.class, withoutStatus::build),
                    () -> assertThrows(NullPointerException.class, withoutCountStatus::build),
                    () -> assertThrows(NullPointerException.class, withoutObservedAt::build),
                    () -> assertThrows(NullPointerException.class, () -> new Gate(null, List.of())),
                    () -> assertThrows(NullPointerException.class, () -> new Gate(GateResult.PASSED, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition(null, Comparator.LT, "80", null, GateResult.PASSED)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition("coverage", null, "80", null, GateResult.PASSED)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition("coverage", Comparator.LT, null, null, GateResult.PASSED)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Condition("coverage", Comparator.LT, "80", null, null)));
        }
    }
}
