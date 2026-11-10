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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
@EnableGeneratorController
@DisplayName("Git identity")
class GitIdentityTest {

    /** The length of an abbreviated commit id: a boundary of the identity rule. */
    private static final int ABBREVIATED = 8;

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path repo;
    private CommitInfo initial;
    private Identity author;
    private Identity agent;
    private TypedGenerator<String> names;

    @BeforeEach
    void initRepository() throws IOException {
        // names from letters outside the hex digits, so no name reads as an abbreviated commit id
        names = Generators.uniqueValues(Generators.strings("ghijklmnopqrstuvwxyz", 4, 12));
        author = new Identity(names.next(), new EmailGenerator().next());
        agent = new Identity(names.next(), new EmailGenerator().next());
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture paths of the test repository and its worktree
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, names.next()).isOk(), "the test repository is initialised");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("README.md"), Generators.letterStrings(1, 40).next(), StandardCharsets.UTF_8);
        initial = commit();
    }

    private CommitInfo commit() {
        var result = git.commit(new CommitInput(repo, Generators.letterStrings(3, 30).next(), author, agent));
        assertEquals(GitOutcome.OK, result.outcome(), "the fixture commit is recorded");
        return result.value();
    }

    private WorktreeAddInput detached(String startPoint) {
        return new WorktreeAddInput(repo, repo.resolve("wt"), null, false, startPoint);
    }

    @Test
    @DisplayName("a branch named like the pinned commit makes the name ambiguous")
    void branchNamedLikeThePinnedCommit() throws Exception {
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(initial.id()).setStartPoint(initial.id()).call();
        }

        var result = git.worktreeAdd(detached(initial.id()));

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(),
                "a commit id that is a branch name is ambiguous");
    }

    @Test
    @DisplayName("an abbreviated commit id is refused, never widened to the one object it matches")
    void abbreviatedCommitId() {
        var result = git.worktreeAdd(detached(initial.id().substring(0, ABBREVIATED)));

        assertAll(
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, result.outcome(),
                        "an abbreviated id is an unknown revision"),
                // the reason word of the refusal detail is the contract
                () -> assertTrue(result.detail().contains("abbreviated"), "the detail names the abbreviated id"));
    }

    @Test
    @DisplayName("a name that resolves as two refs is ambiguous")
    void twoRefs() throws Exception {
        var duplicate = names.next();
        try (var repository = Git.open(repo.toFile()); var walk = new RevWalk(repository.getRepository())) {
            repository.branchCreate().setName(duplicate).setStartPoint(initial.id()).call();
            repository.tag().setName(duplicate).setObjectId(walk.parseCommit(ObjectId.fromString(initial.id()))).call();
        }

        var result = git.worktreeAdd(detached(duplicate));

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(),
                "a name of a branch and a tag is ambiguous");
    }

    @Test
    @DisplayName("a branch that is not at the pin is refused with both ids")
    void mismatch() throws Exception {
        var pinned = names.next();
        Files.writeString(repo.resolve(names.next() + ".txt"), Generators.letterStrings(1, 40).next(),
                StandardCharsets.UTF_8);
        var later = commit();
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(pinned).setStartPoint(later.id()).call();
        }

        var result = git.worktreeAdd(new WorktreeAddInput(repo, repo.resolve("wt"), pinned, false,
                initial.id()));

        assertAll(
                () -> assertEquals(GitOutcome.GIT_IDENTITY_MISMATCH, result.outcome(),
                        "a branch away from its pin is a mismatch"),
                () -> assertTrue(result.detail().contains(initial.id()), "the detail names the pinned commit id"),
                () -> assertTrue(result.detail().contains(later.id()), "the detail names the branch's commit id"));
    }

    @Test
    @DisplayName("an abbreviated start point is refused even when an existing branch ignores it")
    void abbreviatedStartPointWithExistingBranch() throws Exception {
        var pinned = names.next();
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(pinned).setStartPoint(initial.id()).call();
        }

        var result = git.worktreeAdd(new WorktreeAddInput(repo, repo.resolve("wt"), pinned, false,
                initial.id().substring(0, ABBREVIATED)));

        assertAll(
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, result.outcome(),
                        "an abbreviated start point is unknown"),
                // the reason word of the refusal detail is the contract
                () -> assertTrue(result.detail().contains("abbreviated"), "the detail names the abbreviated id"));
    }

    @Test
    @DisplayName("a log of an annotated tag's id is refused with both ids")
    void logAnnotatedTagId() throws Exception {
        var tagName = names.next();
        String tagId;
        try (var repository = Git.open(repo.toFile()); var walk = new RevWalk(repository.getRepository())) {
            var commit = walk.parseCommit(ObjectId.fromString(initial.id()));
            repository.tag().setName(tagName).setAnnotated(true).setMessage(Generators.letterStrings(3, 30).next())
                    .setObjectId(commit).call();
            tagId = repository.getRepository().resolve(tagName).name();
        }

        var result = git.log(repo, tagId, 10);

        assertAll(
                () -> assertEquals(GitOutcome.GIT_IDENTITY_MISMATCH, result.outcome(),
                        "a tag id in place of a commit is a mismatch"),
                () -> assertTrue(result.detail().contains(tagId), "the detail names the annotated tag's id"),
                () -> assertTrue(result.detail().contains(initial.id()), "the detail names the tagged commit id"));
    }

    @Test
    @DisplayName("the ref spellings are the full ones of the contract (ref prefixes are the contract)")
    void refSpellings() {
        var branch = names.next();
        var remote = names.next();
        var abbreviated = initial.id().substring(0, ABBREVIATED);

        assertAll(
                () -> assertEquals("refs/heads/" + branch, GitIdentity.branchRef(branch),
                        "a short branch name gains the heads prefix"),
                () -> assertEquals("refs/heads/" + branch, GitIdentity.branchRef("refs/heads/" + branch),
                        "a full branch ref is kept as it is"),
                // the runtime ref name is the contract of the job runtime
                () -> assertEquals("refs/pm-mcp/head-at-completion", GitIdentity.runtimeRef("head-at-completion"),
                        "a runtime ref lives below refs/pm-mcp"),
                () -> assertEquals("refs/remotes/" + remote + "/" + branch, GitIdentity.remoteBranchRef(remote, branch),
                        "a remote branch lives below refs/remotes"),
                () -> assertTrue(GitIdentity.isFullCommitId(initial.id()), "a full lower-case id is a full id"),
                () -> assertTrue(GitIdentity.isFullCommitId(initial.id().toUpperCase(Locale.ROOT)),
                        "a full upper-case id is a full id"),
                () -> assertFalse(GitIdentity.isFullCommitId(abbreviated), "an abbreviated id is no full id"),
                () -> assertTrue(GitIdentity.isAbbreviatedCommitId(abbreviated),
                        "an abbreviated id is recognised as such"),
                () -> assertTrue(GitIdentity.isAbbreviatedCommitId(abbreviated.toUpperCase(Locale.ROOT)),
                        "an upper-case abbreviated id is recognised"),
                () -> assertFalse(GitIdentity.isFullCommitId(null), "no id is no full commit id"),
                () -> assertFalse(GitIdentity.isAbbreviatedCommitId(null), "no id is no abbreviated commit id"));
    }

    @Test
    @DisplayName("a log of an ambiguous revision is refused")
    void logAmbiguous() throws Exception {
        try (var repository = Git.open(repo.toFile())) {
            repository.branchCreate().setName(initial.id()).setStartPoint(initial.id()).call();
        }

        var result = git.log(repo, initial.id(), 10);

        assertEquals(GitOutcome.GIT_IDENTITY_AMBIGUOUS, result.outcome(), "a log of an ambiguous name is refused");
    }
}
