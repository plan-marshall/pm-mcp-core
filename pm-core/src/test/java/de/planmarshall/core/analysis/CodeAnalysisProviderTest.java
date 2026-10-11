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

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.findings.Finding;
import de.planmarshall.core.findings.FindingScope;
import de.planmarshall.core.findings.FindingSourceKind;
import de.planmarshall.core.findings.FindingStatus;
import de.planmarshall.core.findings.Resolution;
import de.planmarshall.core.findings.ResolveState;
import de.planmarshall.core.findings.Severity;
import de.planmarshall.core.findings.TransmitOutcome;

/**
 * The code analysis provider contract (PM-IMPL-1): a fixture provider answers both operations with each of the five
 * outcomes, and every result it builds is accepted or refused by the rules of {@link AnalysisCallResult}.
 */
@DisplayName("Code analysis provider")
class CodeAnalysisProviderTest {

    private static final String PROVIDER_ID = "fixture-analysis";
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant AT = Instant.parse("2026-10-11T12:00:00Z");
    private static final FindingScope TARGET = FindingScope.pullRequest(SHA, 7);

    /**
     * A provider answering every call with one outcome. An {@code OK} answer carries the analysis result of the
     * target, or its issues with the configured completeness; a transport answer carries nothing.
     */
    private record FixtureProvider(AnalysisOutcome outcome, Completeness completeness) implements CodeAnalysisProvider {

        @Override
        public String id() {
            return PROVIDER_ID;
        }

        @Override
        public AnalysisCallResult<AnalysisResult> status(FindingScope target) {
            if (outcome != AnalysisOutcome.OK) {
                return AnalysisCallResult.status(outcome, null);
            }
            return AnalysisCallResult.status(outcome, new AnalysisResult(1, PROVIDER_ID, target,
                    AnalysisStatus.FINDINGS, null, CountStatus.MEASURED, 1L, 1, true, "analysis-1", AT));
        }

        @Override
        public AnalysisCallResult<List<Finding>> issues(FindingScope target) {
            if (outcome != AnalysisOutcome.OK) {
                return AnalysisCallResult.issues(outcome, null, null);
            }
            return AnalysisCallResult.issues(outcome, List.of(issue("issue-key-1")), completeness);
        }
    }

    /** A provider that breaks the contract by attaching a value and a completeness to every answer. */
    private record LeakingProvider(AnalysisOutcome outcome) implements CodeAnalysisProvider {

        @Override
        public String id() {
            return PROVIDER_ID;
        }

        @Override
        public AnalysisCallResult<AnalysisResult> status(FindingScope target) {
            return AnalysisCallResult.status(outcome, new AnalysisResult(1, PROVIDER_ID, target,
                    AnalysisStatus.UNDECIDABLE, null, CountStatus.INDETERMINATE, null, 0, false, null, AT));
        }

        @Override
        public AnalysisCallResult<List<Finding>> issues(FindingScope target) {
            return AnalysisCallResult.issues(outcome, List.of(), Completeness.INCOMPLETE);
        }
    }

    private static Finding issue(String issueKey) {
        return Finding.builder()
                .formatVersion(Finding.FORMAT_VERSION)
                .findingId("F-1")
                .source(FindingSourceKind.ANALYSIS)
                .provider(PROVIDER_ID)
                .providerRef(new Finding.ProviderRef(null, null, null, null, issueKey))
                .severity(Severity.MAJOR)
                .blocking(true)
                .status(FindingStatus.OPEN)
                .resolution(Resolution.PENDING)
                .summary("an issue on new code")
                .transmitOutcome(TransmitOutcome.PENDING)
                .resolveState(ResolveState.NOT_APPLICABLE)
                .createdAt(AT)
                .build();
    }

    @Nested
    @DisplayName("analysis.status")
    class Status {

        @ParameterizedTest(name = "{0}")
        @EnumSource(AnalysisOutcome.class)
        @DisplayName("each outcome is answered, with a value for ok only and never a completeness")
        void eachOutcome(AnalysisOutcome outcome) {
            var result = new FixtureProvider(outcome, null).status(TARGET);

            assertAll(
                    () -> assertEquals(outcome, result.outcome()),
                    () -> assertEquals(outcome == AnalysisOutcome.OK, result.value() != null),
                    () -> assertNull(result.completeness()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with a value is refused")
        void transportWithValueRefused(AnalysisOutcome outcome) {
            var provider = new LeakingProvider(outcome);

            assertThrows(IllegalArgumentException.class, () -> provider.status(TARGET));
        }

        @Test
        @DisplayName("an ok result is the provider's observation of the asked target")
        void okResultOfTarget() {
            var result = new FixtureProvider(AnalysisOutcome.OK, null).status(TARGET).value();

            assertAll(
                    () -> assertEquals(PROVIDER_ID, result.provider()),
                    () -> assertEquals(TARGET, result.target()));
        }
    }

    @Nested
    @DisplayName("analysis.issues")
    class Issues {

        @ParameterizedTest(name = "{0}")
        @EnumSource(AnalysisOutcome.class)
        @DisplayName("each outcome is answered completely read, with issues and completeness for ok only")
        void eachOutcomeComplete(AnalysisOutcome outcome) {
            var result = new FixtureProvider(outcome, Completeness.COMPLETE).issues(TARGET);

            assertAll(
                    () -> assertEquals(outcome, result.outcome()),
                    () -> assertEquals(outcome == AnalysisOutcome.OK, result.value() != null),
                    () -> assertEquals(outcome == AnalysisOutcome.OK ? Completeness.COMPLETE : null,
                            result.completeness()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(AnalysisOutcome.class)
        @DisplayName("each outcome is answered incompletely read, with issues and completeness for ok only")
        void eachOutcomeIncomplete(AnalysisOutcome outcome) {
            var result = new FixtureProvider(outcome, Completeness.INCOMPLETE).issues(TARGET);

            assertAll(
                    () -> assertEquals(outcome, result.outcome()),
                    () -> assertEquals(outcome == AnalysisOutcome.OK ? Completeness.INCOMPLETE : null,
                            result.completeness()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with issues and a completeness is refused")
        void transportWithValueRefused(AnalysisOutcome outcome) {
            var provider = new LeakingProvider(outcome);

            assertThrows(IllegalArgumentException.class, () -> provider.issues(TARGET));
        }

        @Test
        @DisplayName("an ok result without a completeness is refused")
        void okWithoutCompletenessRefused() {
            var provider = new FixtureProvider(AnalysisOutcome.OK, null);

            assertThrows(IllegalArgumentException.class, () -> provider.issues(TARGET));
        }

        @Test
        @DisplayName("the issues are analysis findings carrying the provider id and the provider's issue key")
        void issuesCarryProviderCoordinates() {
            var issues = new FixtureProvider(AnalysisOutcome.OK, Completeness.COMPLETE).issues(TARGET).value();

            assertAll(
                    () -> assertEquals(1, issues.size()),
                    () -> assertEquals(FindingSourceKind.ANALYSIS, issues.getFirst().source()),
                    () -> assertEquals(PROVIDER_ID, issues.getFirst().provider()),
                    () -> assertEquals("issue-key-1", issues.getFirst().providerRef().issueKey()));
        }
    }
}
