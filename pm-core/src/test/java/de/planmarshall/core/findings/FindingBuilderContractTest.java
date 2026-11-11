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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link Finding} and its nested values build what their canonical constructors build, and a missing
 * required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Finding builders")
class FindingBuilderContractTest {

    private static final String FINDING_ID = "F-1";

    @Test
    @DisplayName("the finding builder builds the constructor's value and refuses alike")
    void shouldBuildFindingLikeTheConstructorAndRefuseAlike() {
        var canonical = new Finding(Finding.FORMAT_VERSION, FINDING_ID, FindingSourceKind.GITHUB_PR, null, null, null,
                null, null, null, null, Severity.MAJOR, false, FindingStatus.OPEN, null, null, null,
                Resolution.PENDING, null, null, null, null, "summary", null, null, null, null, null, null, null, null,
                null, TransmitOutcome.PENDING, null, null, null, ResolveState.NOT_APPLICABLE, Instant.EPOCH, null);
        var built = Finding.builder().formatVersion(canonical.formatVersion()).findingId(canonical.findingId())
                .source(canonical.source()).provider(canonical.provider()).ciFailure(canonical.ciFailure())
                .phase(canonical.phase()).queueTaskId(canonical.queueTaskId()).batchId(canonical.batchId())
                .screen(canonical.screen()).requirementRefs(canonical.requirementRefs())
                .severity(canonical.severity()).blocking(canonical.blocking()).status(canonical.status())
                .disposition(canonical.disposition()).suppressReason(canonical.suppressReason())
                .introducedBy(canonical.introducedBy()).resolution(canonical.resolution())
                .scopeClass(canonical.scopeClass()).carriedTo(canonical.carriedTo())
                .filePath(canonical.filePath()).line(canonical.line()).summary(canonical.summary())
                .body(canonical.body()).author(canonical.author()).botKind(canonical.botKind())
                .raisedAtHead(canonical.raisedAtHead()).reviewedCommitSha(canonical.reviewedCommitSha())
                .editMarker(canonical.editMarker()).evidence(canonical.evidence())
                .providerRef(canonical.providerRef()).dispositionRationale(canonical.dispositionRationale())
                .transmitOutcome(canonical.transmitOutcome()).transmitReason(canonical.transmitReason())
                .transmitDisposition(canonical.transmitDisposition()).respondedAt(canonical.respondedAt())
                .resolveState(canonical.resolveState()).createdAt(canonical.createdAt())
                .updatedAt(canonical.updatedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new Finding(Finding.FORMAT_VERSION, null, FindingSourceKind.GITHUB_PR, null, null,
                                    null, null, null, null, null, Severity.MAJOR, false, FindingStatus.OPEN, null,
                                    null, null, Resolution.PENDING, null, null, null, null, "summary", null, null,
                                    null, null, null, null, null, null, null, TransmitOutcome.PENDING, null, null,
                                    null, ResolveState.NOT_APPLICABLE, Instant.EPOCH, null),
                            "the constructor refuses a null finding id");
                    var incomplete = Finding.builder().formatVersion(Finding.FORMAT_VERSION)
                            .source(FindingSourceKind.GITHUB_PR).summary("summary");
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing finding id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent finding id with the same message");
                });
    }

    @Test
    @DisplayName("the CI failure builder builds the constructor's value and refuses alike")
    void shouldBuildCiFailureLikeTheConstructorAndRefuseAlike() {
        var checkName = Generators.letterStrings(1, 10).next();

        var canonical = new Finding.CiFailure(checkName, Finding.CiFailure.FailureClass.FAILURE,
                Finding.CiFailure.FailureKind.TEST, true);
        var built = Finding.CiFailure.builder().checkName(canonical.checkName())
                .failureClass(canonical.failureClass()).failureKind(canonical.failureKind())
                .requiredCheck(canonical.requiredCheck()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new Finding.CiFailure(null, Finding.CiFailure.FailureClass.FAILURE,
                                    Finding.CiFailure.FailureKind.TEST, true),
                            "the constructor refuses a null check name");
                    var incomplete = Finding.CiFailure.builder()
                            .failureClass(Finding.CiFailure.FailureClass.FAILURE)
                            .failureKind(Finding.CiFailure.FailureKind.TEST).requiredCheck(true);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing check name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent check name with the same message");
                });
    }

    @Test
    @DisplayName("the provider ref builder builds the constructor's value")
    void shouldBuildProviderRefLikeTheConstructor() {
        var canonical = new Finding.ProviderRef(42, "thread", "comment",
                Finding.ProviderRef.CommentKind.REVIEW_THREAD, "issue");

        var built = Finding.ProviderRef.builder().prNumber(canonical.prNumber()).threadId(canonical.threadId())
                .commentId(canonical.commentId()).commentKind(canonical.commentKind())
                .issueKey(canonical.issueKey()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
