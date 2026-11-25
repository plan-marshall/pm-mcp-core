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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;

/**
 * {@link JGitOperations} on an initialised repository: opening it and its facts, a commit of every change or of the
 * listed paths, and the history walk.
 */
@EnableGeneratorController
@DisplayName("JGitOperations (native git variant): open, commit and log")
class JGitOperationsTest extends JGitOperationsFixtures {

    @Test
    @DisplayName("opens a repository and reports its facts")
    void opens() {
        var info = git.open(repo).value();

        assertAll(
                () -> assertEquals(repo.toRealPath(), info.worktreeRoot(), "the worktree root is the real path"),
                () -> assertEquals(branch, info.branch(), "the checked-out branch is reported"),
                () -> assertNotNull(info.head(), "the HEAD commit id is reported"),
                () -> assertFalse(info.linked(), "the main worktree is no linked worktree"),
                () -> assertEquals(info.gitDir(), info.commonDir(), "the main worktree's git dir is common"));
    }

    @Test
    @DisplayName("reports a directory outside any repository")
    void notARepository() throws Exception {
        Path plain = Files.createDirectories(temp.resolve("plain"));

        assertAll(
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.open(plain).outcome(),
                        "open refuses a plain directory"),
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.log(plain, HEAD, 1).outcome(),
                        "log refuses a plain directory"),
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY,
                        git.commit(new CommitInput(plain, text(), author, agent, List.of())).outcome(),
                        "commit refuses a plain directory"),
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.worktreeList(plain).outcome(),
                        "worktreeList refuses a plain directory"));
    }

    @Test
    @DisplayName("commits additions and deletions with author and committer, unsigned")
    void commitsAllChanges() throws Exception {
        write("src/" + file(), text());
        Files.delete(repo.resolve("README.md"));
        // a full message: subject, blank line, body, final line feed
        var message = text() + "\n\n" + text() + "\n";

        CommitInfo commit = commit(message);

        assertAll(
                () -> assertEquals(author, commit.author(), "the author is the operator's identity"),
                () -> assertEquals(agent, commit.committer(), "the committer is the agent identity"),
                () -> assertEquals(message, commit.message(), "the full message is kept unchanged"),
                () -> assertEquals(1, commit.parents().size(), "the commit has the initial one as parent"),
                () -> assertFalse(Files.exists(repo.resolve("README.md")), "the deleted file stays deleted"),
                () -> assertTrue(git.worktreeSha(repo).isOk(), "the committed tree has a digest"));
    }

    @Test
    @DisplayName("reports nothing to commit")
    void nothingToCommit() {
        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(), "a clean tree has nothing to commit");
    }

    @Test
    @DisplayName("commits only the listed paths, leaving a pre-staged and a dirty path out")
    void commitsOnlyListedPaths() throws Exception {
        var staged = file();
        var listed = file();
        var dirty = file();
        write(staged, text());
        write(listed, text());
        write(dirty, text());
        // pre-stage the unrelated file in the index, as another writer would
        try (var jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern(staged).call();
        }
        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(listed)));

        var commit = result.value();
        assertAll(result.detail(),
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the subset commit is recorded"),
                () -> assertTrue(inCommit(commit.id(), listed), "the listed path is committed"),
                () -> assertFalse(inCommit(commit.id(), staged),
                        "the pre-staged path is not carried into the commit"),
                () -> assertFalse(inCommit(commit.id(), dirty), "the dirty unlisted path is left out"));
    }

    @Test
    @DisplayName("commits a deletion named in the listed paths")
    void commitsListedDeletion() throws Exception {
        Files.delete(repo.resolve("README.md"));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("README.md")));

        assertAll(result.detail(),
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the deletion commit is recorded"),
                () -> assertFalse(inCommit(result.value().id(), "README.md"),
                        "the deleted path is gone from the committed tree"));
    }

    @Test
    @DisplayName("a listed path without a change reports nothing to commit")
    void listedPathWithoutChange() {
        // README.md was committed at setUp and is tracked and unchanged, so the subset staged for it is empty
        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("README.md")));

        assertAll(
                () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                        "a subset without a change commits nothing"),
                () -> assertEquals("no changes", result.detail(), "the refusal names the empty change set"));
    }

    @Test
    @DisplayName("a staged change the worktree reverted is nothing to commit and keeps the staged entry")
    void stagedDriftKeepsTheIndex() throws Exception {
        // the fixture README holds A: stage B, then put the worktree back to A, so only the index differs from HEAD
        var original = Files.readString(repo.resolve("README.md"));
        Files.writeString(repo.resolve("README.md"), text());
        try (var jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern("README.md").call();
        }
        Files.writeString(repo.resolve("README.md"), original);
        var staged = indexEntry("README.md").getObjectId();

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("README.md")));

        try (var jgit = Git.open(repo.toFile())) {
            var status = jgit.status().call();
            assertAll("staged drift",
                    () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                            "The staged drift is nothing to commit"),
                    () -> assertEquals(staged, indexEntry("README.md").getObjectId(),
                            "The staged entry is kept"),
                    () -> assertTrue(status.getChanged().contains("README.md"),
                            "The staged change still differs from HEAD"),
                    () -> assertTrue(status.getModified().contains("README.md"),
                            "The reverted worktree shows as modified"));
        }
    }

    @Test
    @DisplayName("a listed path without a change leaves the index untouched")
    void nothingToCommitLeavesTheIndexUntouched() throws Exception {
        // the content equals HEAD, and only the stat moves, so an add would refresh the index entry and stage it
        Files.setLastModifiedTime(repo.resolve("README.md"),
                FileTime.from(Instant.parse("2020-01-02T03:04:05Z")));
        var before = indexEntry("README.md").getLastModifiedInstant();

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("README.md")));

        assertAll("index untouched",
                () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                        "the unchanged path commits nothing"),
                () -> assertEquals(before, indexEntry("README.md").getLastModifiedInstant(),
                        "the refused commit stages no listed path"));
    }

    @Test
    @DisplayName("a builder without paths commits every change")
    void builderWithoutPaths() {
        var input = CommitInput.builder().root(repo).message(text()).author(author).committer(agent).build();

        var result = git.commit(input);

        assertAll(
                () -> assertEquals(List.of(), input.paths(), "the builder fills the empty path list"),
                () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                        "the clean tree has nothing to commit"));
    }

    @Test
    @DisplayName("an empty path list that commits nothing restores the whole index")
    void emptyPathListRestoresTheWholeIndex() throws Exception {
        // the fixture README holds A: stage B, then put the worktree back to A, so only the index differs from HEAD
        var original = Files.readString(repo.resolve("README.md"));
        Files.writeString(repo.resolve("README.md"), text());
        try (var jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern("README.md").call();
        }
        Files.writeString(repo.resolve("README.md"), original);
        var staged = indexEntry("README.md").getObjectId();

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        try (var jgit = Git.open(repo.toFile())) {
            var status = jgit.status().call();
            assertAll("whole-index restore",
                    () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                            "The reverted staging is nothing to commit"),
                    () -> assertEquals(staged, indexEntry("README.md").getObjectId(),
                            "The staged entry is kept, not overwritten by add ."),
                    () -> assertTrue(status.getChanged().contains("README.md"),
                            "The staged change still differs from HEAD"));
        }
    }

    @Test
    @DisplayName("an empty path list restores the whole index beside worktree dirt a submodule keeps")
    void emptyPathListRestoresTheWholeIndexBesideSubmoduleDirt() throws Exception {
        // a submodule whose own worktree is dirty: `add .` re-records the same gitlink, so the index can equal HEAD
        // while the status is not clean
        var submodule = names.next();
        Path nested = repo.resolve(submodule);
        try (Git inner = Git.init().setDirectory(nested.toFile()).call()) {
            Files.writeString(nested.resolve(file()), text(), StandardCharsets.UTF_8);
            inner.add().addFilepattern(".").call();
            commitThroughGit(inner);
        }
        try (Git outer = Git.open(repo.toFile()); Git inner = Git.open(nested.toFile())) {
            recordGitlink(outer.getRepository(), inner.getRepository(), submodule);
            commitThroughGit(outer);
        }
        Files.writeString(nested.resolve(names.next() + ".txt"), text(), StandardCharsets.UTF_8);
        // the staged and reverted change the empty-list route has to restore
        var original = Files.readString(repo.resolve("README.md"));
        Files.writeString(repo.resolve("README.md"), text());
        try (var jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern("README.md").call();
        }
        Files.writeString(repo.resolve("README.md"), original);
        var staged = indexEntry("README.md").getObjectId();

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        try (var jgit = Git.open(repo.toFile())) {
            var status = jgit.status().call();
            assertAll("whole-index restore beside submodule dirt",
                    () -> assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(),
                            "The reverted staging is nothing to commit"),
                    () -> assertEquals(staged, indexEntry("README.md").getObjectId(),
                            "The staged entry is kept, not overwritten by add ."),
                    () -> assertTrue(status.getChanged().contains("README.md"),
                            "The staged change still differs from HEAD"),
                    () -> assertTrue(status.getModified().contains(submodule),
                            "The submodule's own checkout stays dirty"));
        }
    }

    @Test
    @DisplayName("a conflicted index is refused with the path of its conflict")
    void conflictedIndexIsRefused() throws Exception {
        var conflicted = file();
        write(conflicted, text());
        commit(text());
        var other = names.next();
        try (var jgit = Git.open(repo.toFile())) {
            jgit.branchCreate().setName(other).call();
            Files.writeString(repo.resolve(conflicted), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(conflicted).call();
            commitThroughGit(jgit);
            jgit.checkout().setName(other).call();
            Files.writeString(repo.resolve(conflicted), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(conflicted).call();
            commitThroughGit(jgit);
            jgit.checkout().setName(branch).call();
            var merge = jgit.merge().include(jgit.getRepository().resolve(other)).call();
            assertTrue(merge.getConflicts() != null && merge.getConflicts().containsKey(conflicted),
                    "the merge leaves the conflict in the index");
        }

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(conflicted)));

        assertAll("conflicted index",
                () -> assertEquals(GitOutcome.FAILED, result.outcome(), "A conflicted index is refused"),
                () -> assertTrue(String.valueOf(result.detail()).contains(conflicted),
                        "The refusal names the conflicted path"));
    }

    @Test
    @DisplayName("an empty path list with a conflicted index is refused with the path of its conflict")
    void conflictedIndexWithEmptyPathListIsRefused() throws Exception {
        var conflicted = file();
        write(conflicted, text());
        commit(text());
        var other = names.next();
        try (var jgit = Git.open(repo.toFile())) {
            jgit.branchCreate().setName(other).call();
            Files.writeString(repo.resolve(conflicted), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(conflicted).call();
            commitThroughGit(jgit);
            jgit.checkout().setName(other).call();
            Files.writeString(repo.resolve(conflicted), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(conflicted).call();
            commitThroughGit(jgit);
            jgit.checkout().setName(branch).call();
            var merge = jgit.merge().include(jgit.getRepository().resolve(other)).call();
            assertTrue(merge.getConflicts() != null && merge.getConflicts().containsKey(conflicted),
                    "the merge leaves the conflict in the index");
        }

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        assertAll("conflicted index with the whole index listed",
                () -> assertEquals(GitOutcome.FAILED, result.outcome(), "A conflicted index is refused"),
                () -> assertTrue(String.valueOf(result.detail()).contains(conflicted),
                        "The refusal names the conflicted path"));
    }

    @Test
    @DisplayName("walks the history newest first, bounded by the count")
    void logs() throws Exception {
        write(file(), text());
        CommitInfo second = commit(text());
        write(file(), text());
        CommitInfo third = commit(text());

        var all = git.log(repo, HEAD, 10).value();
        var one = git.log(repo, branch, 1).value();

        assertAll(
                () -> assertEquals(List.of(third.id(), second.id()),
                        all.subList(0, 2).stream().map(CommitInfo::id).toList(), "the newest commits come first"),
                () -> assertEquals(3, all.size(), "the history holds the three commits"),
                () -> assertEquals(List.of(third), one, "a count of one yields the newest commit"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.log(repo, names.next(), 1).outcome(),
                        "an unknown branch is an unknown revision"));
    }

    private DirCacheEntry indexEntry(String path) throws IOException {
        try (var jgit = Git.open(repo.toFile())) {
            return Objects.requireNonNull(jgit.getRepository().readDirCache().getEntry(path), "the entry");
        }
    }

    private boolean inCommit(String commitId, String path) throws IOException {
        try (var jgit = Git.open(repo.toFile())) {
            var tree = jgit.getRepository().parseCommit(
                    jgit.getRepository().resolve(commitId)).getTree();
            return TreeWalk.forPath(jgit.getRepository(), path, tree) != null;
        }
    }
}
