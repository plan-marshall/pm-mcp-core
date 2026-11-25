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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code freshness_reconcile} stamp (Phase Workflows § Finalize Commit Instrumentation, item 3): its five members
 * are the schema's required ones, the digests and the finalize step id are the wire contract, and the hash version is
 * the schema's constant.
 */
@EnableGeneratorController
@DisplayName("Freshness reconcile")
class FreshnessReconcileTest {

    private static final String COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);
    private static final Instant RECORDED_AT = Instant.parse("2026-11-23T10:00:00Z");

    private static String stepId() {
        return "default:" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
    }

    /** One stamp built through the builder, so an `assertThrows` lambda holds the one throwing invocation. */
    private static FreshnessReconcile stamp(String commit, String step, String prior, int version, Instant at) {
        return FreshnessReconcile.builder().commitSha(commit).stepId(step).priorBuildWorktreeSha(prior)
                .worktreeShaVersion(version).recordedAt(at).build();
    }

    @Test
    @DisplayName("a complete stamp is held with its five members")
    void completeStampIsHeld() {
        var stamp = FreshnessReconcile.builder().commitSha(COMMIT).stepId(stepId())
                .priorBuildWorktreeSha(DIGEST).worktreeShaVersion(FreshnessReconcile.WORKTREE_SHA_VERSION)
                .recordedAt(RECORDED_AT).build();

        assertAll(
                () -> assertEquals(COMMIT, stamp.commitSha(), "the commit should round-trip"),
                () -> assertEquals(DIGEST, stamp.priorBuildWorktreeSha(), "the prior build should round-trip"),
                () -> assertEquals(1, stamp.worktreeShaVersion(), "the hash version should be one"));
    }

    @Test
    @DisplayName("a commit sha that is no 40-digit digest is refused")
    void commitGrammar() {
        var shortCommit = "a".repeat(39);
        var step = stepId();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> stamp(shortCommit, step, DIGEST, 1, RECORDED_AT), "a short commit sha is refused");

        assertEquals("'freshness_reconcile.commit_sha' is not a digest: " + shortCommit, refused.getMessage(),
                "the refusal should name the commit sha");
    }

    @Test
    @DisplayName("a step id outside the finalize step grammar is refused")
    void stepGrammar() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> stamp(COMMIT, "architecture-refresh", DIGEST, 1, RECORDED_AT),
                        "a step id without its prefix is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> stamp(COMMIT, "other:refresh", DIGEST, 1, RECORDED_AT),
                        "a step id with another prefix is refused"));
    }

    @Test
    @DisplayName("a prior build digest that is no 64-digit digest is refused")
    void priorBuildGrammar() {
        var shortDigest = "b".repeat(63);
        var step = stepId();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> stamp(COMMIT, step, shortDigest, 1, RECORDED_AT), "a short prior build digest is refused");

        assertEquals("'freshness_reconcile.prior_build_worktree_sha' is not a digest: " + shortDigest,
                refused.getMessage(), "the refusal should name the prior build digest");
    }

    @Test
    @DisplayName("a hash version other than one is refused")
    void hashVersion() {
        var step = stepId();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> stamp(COMMIT, step, DIGEST, 2, RECORDED_AT),
                        "a raised hash version is refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> stamp(COMMIT, step, DIGEST, 1, null),
                        "an absent instant is refused"));
    }

    @Test
    @DisplayName("a step id of one letter after the prefix is admitted")
    void shortestStepId() {
        assertDoesNotThrow(() -> FreshnessReconcile.builder().commitSha(COMMIT).stepId("project:a")
                        .priorBuildWorktreeSha(DIGEST).worktreeShaVersion(1).recordedAt(RECORDED_AT).build(),
                "a one-letter step slug should be admitted");
    }
}
