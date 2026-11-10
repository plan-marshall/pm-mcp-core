/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link ChangeLedgerRecord} and of its nested records build the values the canonical constructors
 * build, and an absent required component is refused with the same refusal.
 */
@DisplayName("Change ledger record builders")
class ChangeLedgerRecordBuilderContractTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses an absent kind")
    void shouldBuildRecordLikeTheConstructorAndRefuseAnAbsentKind() {
        var canonical = new ChangeLedgerRecord(ChangeLedgerRecord.Kind.BUILD, "a".repeat(64),
                ChangeLedgerRecord.WORKTREE_SHA_VERSION, null, "compile", ChangeLedgerRecord.Outcome.SUCCEEDED, 1_500L,
                "b-0123456789abcdef", null, List.of("pom.xml"), null, null, null, null, null, null, RECORDED_AT);
        var built = ChangeLedgerRecord.builder().kind(canonical.kind()).worktreeSha(canonical.worktreeSha())
                .worktreeShaVersion(canonical.worktreeShaVersion())
                .worktreeShaFailure(canonical.worktreeShaFailure()).gateKind(canonical.gateKind())
                .outcome(canonical.outcome()).durationMs(canonical.durationMs()).jobId(canonical.jobId())
                .commitSha(canonical.commitSha()).changedPaths(canonical.changedPaths())
                .changeOrigin(canonical.changeOrigin()).expect(canonical.expect())
                .expectResult(canonical.expectResult()).baseline(canonical.baseline())
                .deliverableId(canonical.deliverableId()).taskId(canonical.taskId())
                .recordedAt(canonical.recordedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ChangeLedgerRecord(null, "a".repeat(64),
                                    ChangeLedgerRecord.WORKTREE_SHA_VERSION, null, "compile",
                                    ChangeLedgerRecord.Outcome.SUCCEEDED, 1_500L, null, null, List.of(), null, null,
                                    null, null, null, null, RECORDED_AT),
                            "the constructor refuses a null kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ChangeLedgerRecord.builder().worktreeSha("a".repeat(64))
                                    .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).gateKind("compile")
                                    .outcome(ChangeLedgerRecord.Outcome.SUCCEEDED).changedPaths(List.of())
                                    .recordedAt(RECORDED_AT).build(),
                            "the builder refuses a missing kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent kind with the same message");
                });
    }

    @Test
    @DisplayName("the worktree sha failure builder builds the constructor's value and refuses an absent command")
    void shouldBuildWorktreeShaFailureLikeTheConstructorAndRefuseAnAbsentCommand() {
        var canonical = new ChangeLedgerRecord.WorktreeShaFailure(List.of("git", "rev-parse", "HEAD"),
                ChangeLedgerRecord.WorktreeShaFailure.FailureKind.EXIT_CODE, 128, null);
        var built = ChangeLedgerRecord.WorktreeShaFailure.builder().command(canonical.command())
                .failure(canonical.failure()).exitCode(canonical.exitCode()).signal(canonical.signal()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ChangeLedgerRecord.WorktreeShaFailure(null,
                                    ChangeLedgerRecord.WorktreeShaFailure.FailureKind.EXIT_CODE, 128, null),
                            "the constructor refuses a null command");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ChangeLedgerRecord.WorktreeShaFailure.builder()
                                    .failure(ChangeLedgerRecord.WorktreeShaFailure.FailureKind.EXIT_CODE)
                                    .exitCode(128).build(),
                            "the builder refuses a missing command");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent command with the same message");
                });
    }

    @Test
    @DisplayName("the baseline builder builds the constructor's value and refuses an absent gate kind")
    void shouldBuildBaselineLikeTheConstructorAndRefuseAnAbsentGateKind() {
        var canonical = new ChangeLedgerRecord.Baseline("compile", "a".repeat(40), "b".repeat(40),
                ChangeLedgerRecord.Baseline.ProofSource.CI_BASE_RESULT, "c".repeat(64), List.of("d".repeat(64)));
        var built = ChangeLedgerRecord.Baseline.builder().gateKind(canonical.gateKind())
                .mergeBase(canonical.mergeBase()).baseCommit(canonical.baseCommit())
                .proofSource(canonical.proofSource()).diagnosticsDigest(canonical.diagnosticsDigest())
                .fingerprints(canonical.fingerprints()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ChangeLedgerRecord.Baseline(null, "a".repeat(40), "b".repeat(40),
                                    ChangeLedgerRecord.Baseline.ProofSource.CI_BASE_RESULT, "c".repeat(64),
                                    List.of("d".repeat(64))),
                            "the constructor refuses a null gate kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ChangeLedgerRecord.Baseline.builder().mergeBase("a".repeat(40))
                                    .baseCommit("b".repeat(40))
                                    .proofSource(ChangeLedgerRecord.Baseline.ProofSource.CI_BASE_RESULT)
                                    .diagnosticsDigest("c".repeat(64)).fingerprints(List.of("d".repeat(64))).build(),
                            "the builder refuses a missing gate kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent gate kind with the same message");
                });
    }
}
