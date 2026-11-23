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

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * The stamp for a Phase 6 commit made after the latest successful build, the member {@code freshness_reconcile} of the
 * workflow state store (Phase Workflows § Finalize Commit Instrumentation, item 3).
 * <p>
 * The record is written by {@code git.commit_and_push} when a {@code mutates_source} step commits after the latest
 * successful build: it names the commit, the finalize step that made it, and the working-tree digest of that build, so
 * the push gate recognizes a finalize-internal commit without requiring a redundant full build. It is never written
 * when no successful build exists, because {@code prior_build_worktree_sha} is required.
 *
 * @param commitSha             the commit the finalized step made
 * @param stepId                the finalize step that made it, {@code default:} or {@code project:}
 * @param priorBuildWorktreeSha the working-tree digest of the latest successful build
 * @param worktreeShaVersion    the hash version of {@code prior_build_worktree_sha}
 * @param recordedAt            the instant the stamp was recorded
 * @since 0.1
 */
@Builder
public record FreshnessReconcile(String commitSha, String stepId, String priorBuildWorktreeSha,
        int worktreeShaVersion, Instant recordedAt) {

    /** The hash version of {@code prior_build_worktree_sha}. */
    public static final int WORKTREE_SHA_VERSION = 1;

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    private static final Pattern STEP = Pattern.compile("(default|project):[a-z][a-z0-9-]{0,63}");

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a digest breaks its grammar, the step id is no finalize step id, or the hash
     *                                  version is not {@link #WORKTREE_SHA_VERSION}
     */
    public FreshnessReconcile {
        requirePattern(commitSha, COMMIT, "freshness_reconcile.commit_sha");
        requirePattern(stepId, STEP, "freshness_reconcile.step_id", "a finalize step id");
        requirePattern(priorBuildWorktreeSha, DIGEST, "freshness_reconcile.prior_build_worktree_sha");
        if (worktreeShaVersion != WORKTREE_SHA_VERSION) {
            throw new IllegalArgumentException(
                    "'freshness_reconcile.worktree_sha_version' is " + WORKTREE_SHA_VERSION);
        }
        Objects.requireNonNull(recordedAt, "freshness_reconcile.recorded_at");
    }

    private static void requirePattern(String value, Pattern pattern, String name) {
        requirePattern(value, pattern, name, "a digest");
    }

    private static void requirePattern(String value, Pattern pattern, String name, String kind) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not %s: %s".formatted(name, kind, value));
        }
    }
}
