/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The absent case of the git contract components this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class AbsenceContractsTest {

    @Test
    @DisplayName("a failed git result carries no value")
    void failedGitResultWithoutValue() {
        var result = new GitResult<String>(GitOutcome.WORKTREE_NOT_FOUND, null, "missing");

        assertNull(result.value(), "a failed git result carries no value");
    }

    @Test
    @DisplayName("a local-only remote policy has no origin")
    void localOnlyRemotePolicyHasNoOrigin() {
        assertNull(RemotePolicy.localOnly().origin(), "a local-only policy has no origin");
    }

    @Test
    @DisplayName("an unborn, detached repository info has no head and no branch")
    void repositoryInfoWithoutHeadOrBranch() {
        var info = new GitOperations.RepositoryInfo(Path.of("/worktree"), Path.of("/git"), Path.of("/common"), null, null,
                false);

        assertAll(
                () -> assertNull(info.head(), "an unborn repository has no head"),
                () -> assertNull(info.branch(), "a detached repository has no branch"));
    }

    @Test
    @DisplayName("the main worktree has no administrative name, branch or head when unborn")
    void mainWorktreeWithoutName() {
        var worktree = new GitOperations.Worktree(Path.of("/worktree"), null, null, null, true, false, false);

        assertAll(
                () -> assertNull(worktree.name(), "the main worktree has no name"),
                () -> assertNull(worktree.branch(), "a detached worktree has no branch"),
                () -> assertNull(worktree.head(), "an unborn worktree has no head"));
    }

    @Test
    @DisplayName("a created or deleted ref has an absent old or new id")
    void refUpdateWithoutOldOrNewId() {
        assertAll(
                () -> assertNull(new GitOperations.RefUpdate("refs/heads/a", null, "new", "created").oldId(),
                        "a created ref has no old id"),
                () -> assertNull(new GitOperations.RefUpdate("refs/heads/a", "old", null, "deleted").newId(),
                        "a deleted ref has no new id"));
    }
}
