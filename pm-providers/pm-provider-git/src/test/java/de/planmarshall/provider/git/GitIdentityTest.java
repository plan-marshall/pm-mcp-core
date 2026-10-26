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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOperations.WorktreeAddInput;

/**
 * The git identity rules (PM-WATCH-GEN-59): a pinned commit is an identity, never a name. A branch named like the pinned
 * commit, an abbreviated commit id and a name that resolves as two refs are refused, and a commit that differs from
 * its pin after a worktree creation is refused {@code git_identity_mismatch} with both ids.
 */
@DisplayName("Git identity")
class GitIdentityTest {

    private static final Identity AUTHOR = new Identity("Operator", "operator@example.com");
    private static final Identity AGENT = new Identity("pm-agent[bot]", "1+pm-agent[bot]@users.noreply.github.com");

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path repo;
    private CommitInfo initial;

    @BeforeEach
    void initRepository() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, "main").isOk());
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("README.md"), "hello\n", StandardCharsets.UTF_8);
        initial = commit("initial");
    }

    private CommitInfo commit(String message) {
        var result = git.commit(new CommitInput(repo, message, AUTHOR, AGENT));
        assertEquals(GitOutcome.OK, result.outcome(), result.detail());
        return result.value().orElseThrow();
    }

    private WorktreeAddInput detached(String startPoint) {
        return new WorktreeAddInput(repo, repo.resolve("wt"), Optional.empty(), false, startPoint);
    }

    @Test
    @DisplayName("a branch named like the pinned commit makes the name ambiguous")
    void branchNamedLikeThePinnedCommit() throws Exception {
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(initial.id()).setStartPoint(initial.id()).call();
        }

        var result = git.worktreeAdd(detached(initial.id()));

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(), result.detail());
    }

    @Test
    @DisplayName("an abbreviated commit id is refused, never widened to the one object it matches")
    void abbreviatedCommitId() {
        var result = git.worktreeAdd(detached(initial.id().substring(0, 8)));

        assertAll(
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, result.outcome(), result.detail()),
                () -> assertTrue(result.detail().contains("abbreviated"), result.detail()));
    }

    @Test
    @DisplayName("a name that resolves as two refs is ambiguous")
    void twoRefs() throws Exception {
        try (var repository = Git.open(repo.toFile()); var walk = new RevWalk(repository.getRepository())) {
            repository.branchCreate().setName("dup").setStartPoint(initial.id()).call();
            repository.tag().setName("dup").setObjectId(walk.parseCommit(ObjectId.fromString(initial.id()))).call();
        }

        var result = git.worktreeAdd(detached("dup"));

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(), result.detail());
    }

    @Test
    @DisplayName("a branch that is not at the pin is refused with both ids")
    void mismatch() throws Exception {
        Files.writeString(repo.resolve("later.txt"), "later\n", StandardCharsets.UTF_8);
        var later = commit("later");
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName("pinned").setStartPoint(later.id()).call();
        }

        var result = git.worktreeAdd(new WorktreeAddInput(repo, repo.resolve("wt"), Optional.of("pinned"), false,
                initial.id()));

        assertAll(
                () -> assertEquals(GitOutcome.GIT_IDENTITY_MISMATCH, result.outcome(), result.detail()),
                () -> assertTrue(result.detail().contains(initial.id()), result.detail()),
                () -> assertTrue(result.detail().contains(later.id()), result.detail()));
    }

    @Test
    @DisplayName("an abbreviated start point is refused even when an existing branch ignores it")
    void abbreviatedStartPointWithExistingBranch() throws Exception {
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName("pinned").setStartPoint(initial.id()).call();
        }

        var result = git.worktreeAdd(new WorktreeAddInput(repo, repo.resolve("wt"), Optional.of("pinned"), false,
                initial.id().substring(0, 8)));

        assertAll(
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, result.outcome(), result.detail()),
                () -> assertTrue(result.detail().contains("abbreviated"), result.detail()));
    }

    @Test
    @DisplayName("a log of an annotated tag's id is refused with both ids")
    void logAnnotatedTagId() throws Exception {
        String tagId;
        try (var repository = Git.open(repo.toFile()); var walk = new RevWalk(repository.getRepository())) {
            var commit = walk.parseCommit(ObjectId.fromString(initial.id()));
            repository.tag().setName("annotated").setAnnotated(true).setMessage("tag").setObjectId(commit).call();
            tagId = repository.getRepository().resolve("annotated").name();
        }

        var result = git.log(repo, tagId, 10);

        assertAll(
                () -> assertEquals(GitOutcome.GIT_IDENTITY_MISMATCH, result.outcome(), result.detail()),
                () -> assertTrue(result.detail().contains(tagId), result.detail()),
                () -> assertTrue(result.detail().contains(initial.id()), result.detail()));
    }

    @Test
    @DisplayName("the ref spellings are the full ones of the contract")
    void refSpellings() {
        assertAll(
                () -> assertEquals("refs/heads/main", GitIdentity.branchRef("main")),
                () -> assertEquals("refs/heads/main", GitIdentity.branchRef("refs/heads/main")),
                () -> assertEquals("refs/pm-mcp/head-at-completion", GitIdentity.runtimeRef("head-at-completion")),
                () -> assertEquals("refs/remotes/origin/main", GitIdentity.remoteBranchRef("origin", "main")),
                () -> assertTrue(GitIdentity.isFullCommitId(initial.id())),
                () -> assertTrue(GitIdentity.isFullCommitId(initial.id().toUpperCase(Locale.ROOT))),
                () -> assertFalse(GitIdentity.isFullCommitId(initial.id().substring(0, 8))),
                () -> assertTrue(GitIdentity.isAbbreviatedCommitId(initial.id().substring(0, 8))),
                () -> assertTrue(GitIdentity.isAbbreviatedCommitId(
                        initial.id().substring(0, 8).toUpperCase(Locale.ROOT))),
                () -> assertFalse(GitIdentity.isFullCommitId(null)),
                () -> assertFalse(GitIdentity.isAbbreviatedCommitId(null)));
    }

    @Test
    @DisplayName("a log of an ambiguous revision is refused")
    void logAmbiguous() throws Exception {
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(initial.id()).setStartPoint(initial.id()).call();
        }

        var result = git.log(repo, initial.id(), 10);

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(), result.detail());
    }
}
