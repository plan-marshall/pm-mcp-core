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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
@EnableGeneratorController
@DisplayName("Code analysis provider")
class CodeAnalysisProviderTest {

    private static final String PROVIDER_ID =
            "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String SHA = "%040x".formatted(Generators.longs().next());
    private static final Instant AT = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String ANALYSIS_REF = Generators.letterStrings(5, 20).next();
    private static final String ISSUE_KEY = Generators.letterStrings(5, 20).next();
    private static final FindingScope TARGET =
            FindingScope.pullRequest(SHA, Generators.integers(1, 10_000).next());

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
                    AnalysisStatus.FINDINGS, null, CountStatus.MEASURED, 1L, 1, true, ANALYSIS_REF, AT));
        }

        @Override
        public AnalysisCallResult<List<Finding>> issues(FindingScope target) {
            if (outcome != AnalysisOutcome.OK) {
                return AnalysisCallResult.issues(outcome, null, null);
            }
            return AnalysisCallResult.issues(outcome, List.of(issue(ISSUE_KEY)), completeness);
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
                .findingId("F-" + Generators.integers(1, 999).next())
                .source(FindingSourceKind.ANALYSIS)
                .provider(PROVIDER_ID)
                .providerRef(new Finding.ProviderRef(null, null, null, null, issueKey))
                .severity(Severity.MAJOR)
                .blocking(true)
                .status(FindingStatus.OPEN)
                .resolution(Resolution.PENDING)
                .summary(Generators.letterStrings(10, 40).next())
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
                    () -> assertEquals(outcome, result.outcome(), "the answered outcome is the given one"),
                    () -> assertEquals(outcome == AnalysisOutcome.OK, result.value() != null,
                            "only an ok outcome carries a value"),
                    () -> assertNull(result.completeness(), "a status result carries no completeness"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with a value is refused")
        void transportWithValueRefused(AnalysisOutcome outcome) {
            var provider = new LeakingProvider(outcome);

            assertThrows(IllegalArgumentException.class, () -> provider.status(TARGET),
                    "a transport status with a value is refused");
        }

        @Test
        @DisplayName("an ok result is the provider's observation of the asked target")
        void okResultOfTarget() {
            var result = new FixtureProvider(AnalysisOutcome.OK, null).status(TARGET).value();

            assertAll(
                    () -> assertEquals(PROVIDER_ID, result.provider(), "an ok result names its own provider"),
                    () -> assertEquals(TARGET, result.target(), "an ok result observes the asked target"));
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
                    () -> assertEquals(outcome, result.outcome(), "the answered outcome is carried through"),
                    () -> assertEquals(outcome == AnalysisOutcome.OK, result.value() != null,
                            "only an ok issues call carries issues"),
                    () -> assertEquals(outcome == AnalysisOutcome.OK ? Completeness.COMPLETE : null,
                            result.completeness(), "an ok call reports the complete flag"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(AnalysisOutcome.class)
        @DisplayName("each outcome is answered incompletely read, with issues and completeness for ok only")
        void eachOutcomeIncomplete(AnalysisOutcome outcome) {
            var result = new FixtureProvider(outcome, Completeness.INCOMPLETE).issues(TARGET);

            assertAll(
                    () -> assertEquals(outcome, result.outcome(), "the incomplete outcome is answered back"),
                    () -> assertEquals(outcome == AnalysisOutcome.OK ? Completeness.INCOMPLETE : null,
                            result.completeness(), "an ok call reports the incomplete flag"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisOutcome.class, mode = EnumSource.Mode.EXCLUDE, names = "OK")
        @DisplayName("a transport outcome with issues and a completeness is refused")
        void transportWithValueRefused(AnalysisOutcome outcome) {
            var provider = new LeakingProvider(outcome);

            assertThrows(IllegalArgumentException.class, () -> provider.issues(TARGET),
                    "a transport issues call with a value is refused");
        }

        @Test
        @DisplayName("an ok result without a completeness is refused")
        void okWithoutCompletenessRefused() {
            var provider = new FixtureProvider(AnalysisOutcome.OK, null);

            assertThrows(IllegalArgumentException.class, () -> provider.issues(TARGET),
                    "an ok call without a completeness is refused");
        }

        @Test
        @DisplayName("the issues are analysis findings carrying the provider id and the provider's issue key")
        void issuesCarryProviderCoordinates() {
            var issues = new FixtureProvider(AnalysisOutcome.OK, Completeness.COMPLETE).issues(TARGET).value();

            assertAll(
                    () -> assertEquals(1, issues.size(), "the issues answer holds one issue"),
                    () -> assertEquals(FindingSourceKind.ANALYSIS, issues.getFirst().source(),
                            "the issue source is an analysis finding"),
                    () -> assertEquals(PROVIDER_ID, issues.getFirst().provider(),
                            "the issue names the answering provider"),
                    () -> assertEquals(ISSUE_KEY, issues.getFirst().providerRef().issueKey(),
                            "the issue key is the provider's own key"));
        }
    }
}
