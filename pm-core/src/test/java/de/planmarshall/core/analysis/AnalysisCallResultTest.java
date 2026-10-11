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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.findings.Finding;
import de.planmarshall.core.findings.FindingScope;
import de.planmarshall.core.findings.FindingSourceKind;
import de.planmarshall.core.findings.FindingStatus;
import de.planmarshall.core.findings.Resolution;
import de.planmarshall.core.findings.ResolveState;
import de.planmarshall.core.findings.Severity;
import de.planmarshall.core.findings.TransmitOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The result of a call to a code analysis provider and its rules (PM-IMPL-1).
 */
@DisplayName("Analysis call result")
class AnalysisCallResultTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant AT = Instant.parse("2026-10-11T12:00:00Z");
    private static final AnalysisResult RESULT = new AnalysisResult(1, "sonar", FindingScope.commit(SHA),
            AnalysisStatus.PASSED, null, CountStatus.MEASURED, 0L, 0, false, null, AT);
    private static final Finding ISSUE = Finding.builder()
            .formatVersion(Finding.FORMAT_VERSION)
            .findingId("F-1")
            .source(FindingSourceKind.ANALYSIS)
            .provider("sonar")
            .providerRef(new Finding.ProviderRef(null, null, null, null, "issue-key-1"))
            .severity(Severity.MAJOR)
            .blocking(true)
            .status(FindingStatus.OPEN)
            .resolution(Resolution.PENDING)
            .summary("an issue on new code")
            .transmitOutcome(TransmitOutcome.PENDING)
            .resolveState(ResolveState.NOT_APPLICABLE)
            .createdAt(AT)
            .build();

    private static void assertRefused(String rule, Executable construction) {
        var refusal = assertThrows(IllegalArgumentException.class, construction);
        assertTrue(refusal.getMessage().contains(rule),
                () -> "the refusal names the rule '%s': %s".formatted(rule, refusal.getMessage()));
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @Test
        @DisplayName("an ok status result carries the analysis result and no completeness")
        void okStatus() {
            var result = AnalysisCallResult.status(AnalysisOutcome.OK, RESULT);

            assertAll(
                    () -> assertTrue(result.isOk()),
                    () -> assertEquals(RESULT, result.value()),
                    () -> assertNull(result.completeness()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome of either operation carries neither value nor completeness")
        void transport(AnalysisOutcome outcome) {
            var status = AnalysisCallResult.status(outcome, null);
            var issues = AnalysisCallResult.issues(outcome, null, null);

            assertAll(
                    () -> assertFalse(status.isOk()),
                    () -> assertNull(status.value()),
                    () -> assertFalse(issues.isOk()),
                    () -> assertNull(issues.value()),
                    () -> assertNull(issues.completeness()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Completeness.class)
        @DisplayName("an ok issues result carries the issues and their completeness")
        void okIssues(Completeness completeness) {
            var result = AnalysisCallResult.issues(AnalysisOutcome.OK, List.of(ISSUE), completeness);

            assertAll(
                    () -> assertEquals(List.of(ISSUE), result.value()),
                    () -> assertEquals(completeness, result.completeness()));
        }

        @Test
        @DisplayName("an ok issues result of no issues is a value, not a missing one")
        void okNoIssues() {
            var result = AnalysisCallResult.issues(AnalysisOutcome.OK, List.of(), Completeness.COMPLETE);

            assertEquals(List.of(), result.value());
        }

        @Test
        @DisplayName("the issues are copied and cannot be changed")
        void issuesCopied() {
            var issues = new ArrayList<>(List.of(ISSUE));
            var result = AnalysisCallResult.issues(AnalysisOutcome.OK, issues, Completeness.COMPLETE);
            issues.clear();

            assertAll(
                    () -> assertEquals(List.of(ISSUE), result.value()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> result.value().add(ISSUE)));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"OK, ok", "AUTH_FAILED, auth_failed", "PERMISSION_DENIED, permission_denied",
                "PROVIDER_UNAVAILABLE, provider_unavailable", "MALFORMED_RESPONSE, malformed_response"})
        @DisplayName("the outcomes carry their wire names and are found by them")
        void outcomeWireNames(AnalysisOutcome outcome, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, outcome.wireName()),
                    () -> assertEquals(Optional.of(outcome), AnalysisOutcome.fromWireName(wireName)));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({"COMPLETE, complete", "INCOMPLETE, incomplete"})
        @DisplayName("the completeness values carry their wire names and are found by them")
        void completenessWireNames(Completeness completeness, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, completeness.wireName()),
                    () -> assertEquals(Optional.of(completeness), Completeness.fromWireName(wireName)));
        }

        @Test
        @DisplayName("exactly five outcomes exist, and no other wire name finds one")
        void closedOutcomes() {
            assertAll(
                    () -> assertEquals(5, AnalysisOutcome.values().length),
                    () -> assertEquals(Optional.empty(), AnalysisOutcome.fromWireName("not_found")),
                    () -> assertEquals(Optional.empty(), AnalysisOutcome.fromWireName("rate_limited")),
                    () -> assertEquals(Optional.empty(), Completeness.fromWireName("partial")));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with a value is refused")
        void transportWithValue(AnalysisOutcome outcome) {
            assertAll(
                    () -> assertRefused("carries no value", () -> AnalysisCallResult.status(outcome, RESULT)),
                    () -> assertRefused("carries no value",
                            () -> AnalysisCallResult.issues(outcome, List.of(ISSUE), null)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with a completeness is refused")
        void transportWithCompleteness(AnalysisOutcome outcome) {
            assertRefused("carries no completeness",
                    () -> AnalysisCallResult.issues(outcome, null, Completeness.INCOMPLETE));
        }

        @Test
        @DisplayName("an ok result without a value is refused")
        void okWithoutValue() {
            assertAll(
                    () -> assertRefused("outcome ok requires a value",
                            () -> AnalysisCallResult.status(AnalysisOutcome.OK, null)),
                    () -> assertRefused("outcome ok requires a value",
                            () -> AnalysisCallResult.issues(AnalysisOutcome.OK, null, Completeness.COMPLETE)));
        }

        @Test
        @DisplayName("a status result with a completeness is refused")
        void statusWithCompleteness() {
            assertRefused("analysis.status carries no completeness",
                    () -> new AnalysisCallResult<>(AnalysisOutcome.OK, RESULT, Completeness.COMPLETE));
        }

        @Test
        @DisplayName("an ok issues result without a completeness is refused")
        void issuesWithoutCompleteness() {
            assertRefused("analysis.issues requires a completeness",
                    () -> AnalysisCallResult.issues(AnalysisOutcome.OK, List.of(ISSUE), null));
        }

        @Test
        @DisplayName("a missing outcome is refused")
        void missingOutcome() {
            assertThrows(NullPointerException.class, () -> AnalysisCallResult.status(null, RESULT));
        }
    }
}
