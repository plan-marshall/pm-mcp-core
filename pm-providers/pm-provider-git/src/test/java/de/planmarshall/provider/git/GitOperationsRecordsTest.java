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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of the value records of {@link GitOperations} build the value the canonical constructor builds. The two
 * input records that validate their components ({@code FetchCommitInput}, {@code WorktreeAddInput}) carry the
 * constructor's null refusal into the builder, proved here. Every value is generated; the component names the refusal
 * tests assert are contract vocabulary rather than test data.
 */
@EnableGeneratorController
@DisplayName("GitOperations value records")
class GitOperationsRecordsTest {

    private static String text() {
        return Generators.letterStrings(1, 20).next();
    }

    private static Path path() {
        return Path.of(text());
    }

    private static Instant instant() {
        return Instant.ofEpochSecond(Generators.longs(0L, 1_900_000_000L).next());
    }

    private static GitOperations.Identity identity() {
        return new GitOperations.Identity(text(), text());
    }

    @Test
    @DisplayName("the fetch input builder builds the constructor value")
    void fetchCommitInputBuilderEqualsConstructor() {
        var cache = path();
        var url = text();
        var commit = text();
        var policy = new RemotePolicy(URI.create("https://" + text() + ".example"));

        var built = GitOperations.FetchCommitInput.builder()
                .cache(cache)
                .url(url)
                .commit(commit)
                .policy(policy)
                .build();
        var constructed = new GitOperations.FetchCommitInput(cache, url, commit, policy);

        assertEquals(constructed, built, "the builder builds the same fetch input");
    }

    @Test
    @DisplayName("the fetch input builder refuses a missing cache")
    void fetchCommitInputBuilderRefusesMissingCache() {
        var refusal = assertThrows(NullPointerException.class, () -> GitOperations.FetchCommitInput.builder()
                .url(text())
                .commit(text())
                .policy(new RemotePolicy(null))
                .build());

        assertEquals("cache", refusal.getMessage(), "the refusal names the missing cache");
    }

    @Test
    @DisplayName("the repository info builder builds the constructor value")
    void repositoryInfoBuilderEqualsConstructor() {
        var worktreeRoot = path();
        var gitDir = path();
        var commonDir = path();
        var head = text();
        var branch = text();
        var linked = Generators.booleans().next();

        var built = GitOperations.RepositoryInfo.builder()
                .worktreeRoot(worktreeRoot)
                .gitDir(gitDir)
                .commonDir(commonDir)
                .head(head)
                .branch(branch)
                .linked(linked)
                .build();
        var constructed = new GitOperations.RepositoryInfo(worktreeRoot, gitDir, commonDir, head, branch, linked);

        assertEquals(constructed, built, "the builder builds the same repository facts");
    }

    @Test
    @DisplayName("the worktree builder builds the constructor value")
    void worktreeBuilderEqualsConstructor() {
        var path = path();
        var name = text();
        var branch = text();
        var head = text();
        var main = Generators.booleans().next();
        var locked = Generators.booleans().next();
        var prunable = Generators.booleans().next();

        var built = GitOperations.Worktree.builder()
                .path(path)
                .name(name)
                .branch(branch)
                .head(head)
                .main(main)
                .locked(locked)
                .prunable(prunable)
                .build();
        var constructed = new GitOperations.Worktree(path, name, branch, head, main, locked, prunable);

        assertEquals(constructed, built, "the builder builds the same worktree value");
    }

    @Test
    @DisplayName("the worktree add builder builds the constructor value")
    void worktreeAddInputBuilderEqualsConstructor() {
        var root = path();
        var path = path();
        var branch = text();
        var createBranch = Generators.booleans().next();
        var startPoint = text();

        var built = GitOperations.WorktreeAddInput.builder()
                .root(root)
                .path(path)
                .branch(branch)
                .createBranch(createBranch)
                .startPoint(startPoint)
                .build();
        var constructed = new GitOperations.WorktreeAddInput(root, path, branch, createBranch, startPoint);

        assertEquals(constructed, built, "the builder builds the same add input");
    }

    @Test
    @DisplayName("the worktree add builder refuses a missing root")
    void worktreeAddInputBuilderRefusesMissingRoot() {
        var refusal = assertThrows(NullPointerException.class, () -> GitOperations.WorktreeAddInput.builder()
                .path(path())
                .createBranch(false)
                .startPoint(text())
                .build());

        assertEquals("root", refusal.getMessage(), "the refusal names the missing root");
    }

    @Test
    @DisplayName("the commit info builder builds the constructor value")
    void commitInfoBuilderEqualsConstructor() {
        var id = text();
        var parents = List.of(text());
        var author = identity();
        var authoredAt = instant();
        var committer = identity();
        var committedAt = instant();
        var message = text();

        var built = GitOperations.CommitInfo.builder()
                .id(id)
                .parents(parents)
                .author(author)
                .authoredAt(authoredAt)
                .committer(committer)
                .committedAt(committedAt)
                .message(message)
                .build();
        var constructed = new GitOperations.CommitInfo(id, parents, author, authoredAt, committer, committedAt,
                message);

        assertEquals(constructed, built, "the builder builds the same commit facts");
    }

    @Test
    @DisplayName("the remote input builder builds the constructor value")
    void remoteInputBuilderEqualsConstructor() {
        var root = path();
        var remote = text();
        var refSpecs = List.of(text());
        var credential = new GitOperations.GitCredential(text(), text().toCharArray());

        var built = GitOperations.RemoteInput.builder()
                .root(root)
                .remote(remote)
                .refSpecs(refSpecs)
                .credential(credential)
                .build();
        var constructed = new GitOperations.RemoteInput(root, remote, refSpecs, credential);

        assertEquals(constructed, built, "the builder builds the same remote input");
    }

    @Test
    @DisplayName("the push input builder builds the constructor value")
    void pushInputBuilderEqualsConstructor() {
        var root = path();
        var remote = text();
        var localRef = text();
        var remoteRef = text();
        var expectedRemoteHead = text();
        var credential = new GitOperations.GitCredential(text(), text().toCharArray());

        var built = GitOperations.PushInput.builder()
                .root(root)
                .remote(remote)
                .localRef(localRef)
                .remoteRef(remoteRef)
                .expectedRemoteHead(expectedRemoteHead)
                .credential(credential)
                .build();
        var constructed = new GitOperations.PushInput(root, remote, localRef, remoteRef, expectedRemoteHead,
                credential);

        assertEquals(constructed, built, "the builder builds the same push input");
    }

    @Test
    @DisplayName("the ref update builder builds the constructor value")
    void refUpdateBuilderEqualsConstructor() {
        var ref = text();
        var oldId = text();
        var newId = text();
        var status = text();

        var built = GitOperations.RefUpdate.builder()
                .ref(ref)
                .oldId(oldId)
                .newId(newId)
                .status(status)
                .build();
        var constructed = new GitOperations.RefUpdate(ref, oldId, newId, status);

        assertEquals(constructed, built, "the builder builds the same ref update");
    }
}
