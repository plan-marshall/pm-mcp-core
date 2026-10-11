/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.findings.Finding.CarriedTo;
import de.planmarshall.core.findings.Finding.CiFailure;
import de.planmarshall.core.findings.Finding.Evidence;
import de.planmarshall.core.findings.Finding.ProviderRef;
import de.planmarshall.core.findings.Finding.Screen;

/**
 * The findings ledger record and the rules of its schema (PM-IMPL-1, PM-IMPL-2).
 */
@DisplayName("Finding")
class FindingTest {

    private static final String HEAD = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant CREATED = Instant.parse("2026-10-11T12:00:00Z");

    private static Finding.FindingBuilder base(FindingSourceKind source) {
        return Finding.builder()
                .formatVersion(Finding.FORMAT_VERSION)
                .findingId("F-1")
                .source(source)
                .severity(Severity.MAJOR)
                .blocking(true)
                .status(FindingStatus.OPEN)
                .resolution(Resolution.PENDING)
                .summary("a finding")
                .transmitOutcome(TransmitOutcome.PENDING)
                .resolveState(ResolveState.NOT_APPLICABLE)
                .createdAt(CREATED);
    }

    private static Finding.FindingBuilder analysis() {
        return base(FindingSourceKind.ANALYSIS)
                .provider("analysis-provider")
                .filePath("src/Main.java")
                .line(42)
                .providerRef(new ProviderRef(null, null, null, null, "issue-key-1"));
    }

    private static Finding.FindingBuilder ci() {
        return base(FindingSourceKind.CI)
                .raisedAtHead(HEAD)
                .ciFailure(new CiFailure("build", CiFailure.FailureClass.FAILURE, CiFailure.FailureKind.TEST, true));
    }

    private static Finding.FindingBuilder qgate() {
        return base(FindingSourceKind.QGATE)
                .phase(FindingPhase.PHASE_5_EXECUTE)
                .raisedAtHead(HEAD);
    }

    private static Finding.FindingBuilder review() {
        return base(FindingSourceKind.GITHUB_PR)
                .author("reviewer")
                .botKind("review-bot")
                .reviewedCommitSha(HEAD)
                .screen(new Screen(Screen.Verdict.CLEAN, false, null))
                .providerRef(new ProviderRef(7, "thread-1", "comment-1", ProviderRef.CommentKind.REVIEW_THREAD, null));
    }

    static Stream<Arguments> sourceFamilies() {
        return Stream.of(
                Arguments.of("analysis", analysis()),
                Arguments.of("ci", ci()),
                Arguments.of("qgate", qgate()),
                Arguments.of("review", review()));
    }

    static Stream<Arguments> headRequiringSources() {
        return Stream.of(
                Arguments.of(FindingSourceKind.SELF_REVIEW, base(FindingSourceKind.SELF_REVIEW)),
                Arguments.of(FindingSourceKind.QGATE, qgate().raisedAtHead(null)),
                Arguments.of(FindingSourceKind.CODE_REVIEW, base(FindingSourceKind.CODE_REVIEW)),
                Arguments.of(FindingSourceKind.SECURITY_AUDIT, base(FindingSourceKind.SECURITY_AUDIT)),
                Arguments.of(FindingSourceKind.ARCH_GATE, base(FindingSourceKind.ARCH_GATE)),
                Arguments.of(FindingSourceKind.CI, ci().raisedAtHead(null)));
    }

    static Stream<Arguments> requiredMembers() {
        return Stream.<Arguments>of(
                required("finding_id", builder -> builder.findingId(null)),
                required("source", builder -> builder.source(null)),
                required("severity", builder -> builder.severity(null)),
                required("status", builder -> builder.status(null)),
                required("resolution", builder -> builder.resolution(null)),
                required("summary", builder -> builder.summary(null)),
                required("transmit_outcome", builder -> builder.transmitOutcome(null)),
                required("resolve_state", builder -> builder.resolveState(null)),
                required("created_at", builder -> builder.createdAt(null)));
    }

    private static Arguments required(String member, UnaryOperator<Finding.FindingBuilder> change) {
        return Arguments.of(member, change);
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.findings.FindingTest#sourceFamilies")
        @DisplayName("a well-formed finding of each source family is accepted")
        void sourceFamily(String family, Finding.FindingBuilder builder) {
            assertDoesNotThrow(builder::build);
        }

        @Test
        @DisplayName("the requirement references are copied and cannot be changed afterwards")
        void requirementRefsCopied() {
            var refs = new ArrayList<>(List.of("PM-IMPL-1"));

            var finding = qgate().requirementRefs(refs).build();
            refs.add("PM-IMPL-2");

            assertAll(
                    () -> assertEquals(List.of("PM-IMPL-1"), finding.requirementRefs()),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> finding.requirementRefs().add("PM-IMPL-3")));
        }

        @Test
        @DisplayName("a screen without spans has an empty list of spans")
        void screenWithoutSpans() {
            var screen = new Screen(Screen.Verdict.CLEAN, false, null);

            assertTrue(screen.spans().isEmpty());
        }

        @Test
        @DisplayName("a finding without external text, triage or transmission keeps those members null")
        void optionalMembersNull() {
            var finding = qgate().build();

            assertAll(
                    () -> assertNull(finding.provider()),
                    () -> assertNull(finding.ciFailure()),
                    () -> assertNull(finding.screen()),
                    () -> assertNull(finding.requirementRefs()),
                    () -> assertNull(finding.disposition()),
                    () -> assertNull(finding.transmitReason()),
                    () -> assertNull(finding.updatedAt()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TransmitOutcome.class, names = {"NOT_SENT", "SENT_UNRESOLVED", "SKIPPED_NO_ADDRESS",
                "PARTIAL"})
        @DisplayName("a transmit reason is accepted for an outcome that is neither pending nor sent")
        void transmitReasonForUnsentOutcome(TransmitOutcome outcome) {
            var builder = review().transmitOutcome(outcome).transmitReason(TransmitReason.REPLY_FAILED);

            assertDoesNotThrow(builder::build);
        }

        @Test
        @DisplayName("the nested members of a carried and resolved finding are accepted")
        void carriedAndResolved() {
            var builder = analysis()
                    .status(FindingStatus.CARRIED)
                    .disposition(Disposition.SUPPRESS)
                    .scopeClass(ScopeClass.IN_FOOTPRINT)
                    .carriedTo(new CarriedTo(CarriedTo.Target.LESSON, "lesson-1", CREATED))
                    .evidence(new Evidence(Evidence.EvidenceClass.SUBSTITUTE, "src/Main.java:42", true))
                    .screen(new Screen(Screen.Verdict.SUSPICIOUS, true,
                            List.of(new Screen.Span(0, 4, "instruction-like text"))));

            assertDoesNotThrow(builder::build);
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.findings.FindingTest#requiredMembers")
        @DisplayName("a missing required member is refused")
        void requiredMember(String member, UnaryOperator<Finding.FindingBuilder> change) {
            var builder = change.apply(qgate());

            var refusal = assertThrows(NullPointerException.class, builder::build);

            assertEquals(member, refusal.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, 2})
        @DisplayName("a format version other than 1 is refused")
        void formatVersion(int version) {
            var builder = qgate().formatVersion(version);

            assertRefused(builder, "format_version");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "F-", "F-1a", "f-1", "X-1"})
        @DisplayName("a finding id outside its pattern is refused")
        void findingId(String findingId) {
            assertRefused(qgate().findingId(findingId), "finding_id");
        }

        @Test
        @DisplayName("an analysis finding without a provider is refused")
        void analysisWithoutProvider() {
            assertRefused(analysis().provider(null), "provider is required iff source is analysis");
        }

        @Test
        @DisplayName("a provider on a finding of another source is refused")
        void providerOnOtherSource() {
            assertRefused(qgate().provider("analysis-provider"), "provider is required iff source is analysis");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"ab", "Upper", "-leading", "trailing-", "with space"})
        @DisplayName("a provider outside the provider id pattern is refused")
        void providerPattern(String provider) {
            assertRefused(analysis().provider(provider), "provider must match");
        }

        @Test
        @DisplayName("a CI finding without its CI failure is refused")
        void ciWithoutFailure() {
            assertRefused(ci().ciFailure(null), "ci_failure is required iff source is ci");
        }

        @Test
        @DisplayName("a CI failure on a finding of another source is refused")
        void ciFailureOnOtherSource() {
            var failure = new CiFailure("build", CiFailure.FailureClass.FAILURE, CiFailure.FailureKind.TEST, true);

            assertRefused(qgate().ciFailure(failure), "ci_failure is required iff source is ci");
        }

        @Test
        @DisplayName("a CI failure with an empty check name is refused")
        void ciFailureCheckName() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new CiFailure("", CiFailure.FailureClass.FAILURE, CiFailure.FailureKind.UNKNOWN, false));

            assertTrue(refusal.getMessage().contains("ci_failure.check_name"));
        }

        @Test
        @DisplayName("a quality-gate finding without its phase is refused")
        void qgateWithoutPhase() {
            assertRefused(qgate().phase(null), "phase is required for source qgate");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.findings.FindingTest#headRequiringSources")
        @DisplayName("a finding of a source that needs the head it was raised at is refused without it")
        void raisedAtHeadRequired(FindingSourceKind source, Finding.FindingBuilder builder) {
            assertRefused(builder, "raised_at_head is required for source " + source.wireName());
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"0123456789ABCDEF0123456789ABCDEF01234567", "0123456789abcdef",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"})
        @DisplayName("a raised-at head outside the hash pattern is refused")
        void raisedAtHeadPattern(String head) {
            assertRefused(qgate().raisedAtHead(head), "raised_at_head");
        }

        @Test
        @DisplayName("a reviewed commit outside the hash pattern is refused")
        void reviewedCommitPattern() {
            assertRefused(review().reviewedCommitSha("not-a-commit"), "reviewed_commit_sha");
        }

        @Test
        @DisplayName("an issue key on a finding of another source than analysis is refused")
        void issueKeyOnOtherSource() {
            var reference = new ProviderRef(7, null, null, null, "issue-key-1");

            assertRefused(review().providerRef(reference), "provider_ref.issue_key is set only for source analysis");
        }

        @Test
        @DisplayName("an issue key longer than 256 characters is refused")
        void issueKeyLength() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new ProviderRef(null, null, null, null, "k".repeat(257)));

            assertTrue(refusal.getMessage().contains("provider_ref.issue_key"));
        }

        @Test
        @DisplayName("a pull-request number of the provider reference below 1 is refused")
        void providerRefPrNumber() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new ProviderRef(0, null, null, null, null));

            assertTrue(refusal.getMessage().contains("provider_ref.pr_number"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Screen.Verdict.class)
        @DisplayName("a screen whose withheld flag disagrees with its verdict is refused")
        void screenWithheld(Screen.Verdict verdict) {
            var withheld = verdict != Screen.Verdict.SUSPICIOUS;

            var refusal = assertThrows(IllegalArgumentException.class, () -> new Screen(verdict, withheld, null));

            assertTrue(refusal.getMessage().contains("screen.withheld"));
        }

        @Test
        @DisplayName("a flagged span with a negative offset or an overlong reason is refused")
        void screenSpan() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Screen.Span(-1, 4, "reason")),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Screen.Span(0, -1, "reason")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Screen.Span(0, 4, "r".repeat(201))));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TransmitOutcome.class, names = {"PENDING", "SENT"})
        @DisplayName("a transmit reason while the outcome is pending or sent is refused")
        void transmitReason(TransmitOutcome outcome) {
            var builder = review().transmitOutcome(outcome).transmitReason(TransmitReason.REPLY_FAILED);

            assertRefused(builder, "transmit_reason must be null when transmit_outcome is pending or sent");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"qt-0123", "qt-0123456789ABCDEF", "QT-0123456789abcdef"})
        @DisplayName("a queue task id outside its pattern is refused")
        void queueTaskId(String queueTaskId) {
            assertRefused(qgate().queueTaskId(queueTaskId), "queue_task_id");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"BT-", "bt-1", "BT-x"})
        @DisplayName("a batch id outside its pattern is refused")
        void batchId(String batchId) {
            assertRefused(review().batchId(batchId), "batch_id");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "Bot", "-bot", "bbbbbbbbbbb" + "bbbbbbbbbbb" + "bbbbbbbbbbb"})
        @DisplayName("a bot kind outside the login pattern is refused")
        void botKind(String botKind) {
            assertRefused(review().botKind(botKind), "bot_kind");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("a line below 1 is refused")
        void line(int line) {
            assertRefused(analysis().line(line), "line must be at least 1");
        }

        @Test
        @DisplayName("an empty summary is refused")
        void summary() {
            assertRefused(qgate().summary(""), "summary must not be empty");
        }

        @Test
        @DisplayName("an empty or overlong requirement reference is refused")
        void requirementRefs() {
            assertAll(
                    () -> assertRefused(qgate().requirementRefs(List.of("")), "requirement_refs"),
                    () -> assertRefused(qgate().requirementRefs(List.of("r".repeat(121))), "requirement_refs"));
        }

        private static void assertRefused(Finding.FindingBuilder builder, String rule) {
            var refusal = assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(refusal.getMessage().contains(rule),
                    () -> "the refusal names the rule " + rule + ": " + refusal.getMessage());
        }
    }
}
