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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.Worktree;
import de.planmarshall.provider.git.GitOperations.WorktreeAddInput;

/**
 * {@code git.worktree-sha} and the linked worktrees of {@link JGitOperations}: the digest framing, adding, listing,
 * removing and the refusals of a worktree target.
 */
@EnableGeneratorController
@DisplayName("JGitOperations (native git variant): worktree sha and linked worktrees")
class JGitOperationsWorktreeTest extends JGitOperationsFixtures {

    @Test
    @DisplayName("matches an independent computation of the version-1 framing")
    void framing() throws Exception {
        // two names in byte order, so the framing's sort is exercised
        var first = names.next();
        var second = names.next();
        var a = (first.compareTo(second) < 0 ? first : second) + ".txt";
        var b = (first.compareTo(second) < 0 ? second : first) + ".txt";
        var link = names.next();
        var aContent = text();
        var bContent = text();
        write(b, bContent);
        write(a, aContent);
        Files.createSymbolicLink(repo.resolve(link), Path.of(a));
        String head = git.open(repo).value().head();

        String expected = WorktreeShaDigestTest.independentDigest(head, List.of(
                WorktreeShaDigestTest.file(a, aContent), WorktreeShaDigestTest.file(b, bContent),
                WorktreeShaDigestTest.link(link, a)));

        assertEquals(expected, sha(repo), "the digest matches the independent framing");
    }

    private Path worktrees() {
        // fixture path: the worktree directory of the enrolled project
        return repo.resolve(".marshall/local/worktrees");
    }

    /** @return the administration directory git keeps for a linked worktree of that name */
    private Path admin(String name) {
        return repo.resolve(".git/worktrees").resolve(name);
    }

    private Worktree add(String name, String newBranch) {
        var result = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(name), newBranch,
                true, HEAD));
        assertEquals(GitOutcome.OK, result.outcome(), "the linked worktree is added");
        return result.value();
    }

    @Test
    @DisplayName("adds a worktree on a new branch, checked out and usable")
    void addsOnNewBranch() throws Exception {
        var name = names.next();
        var newBranch = names.next();
        Worktree added = add(name, newBranch);
        var info = git.open(added.path()).value();

        Files.writeString(added.path().resolve(file()), text());
        CommitInfo onBranch = commitIn(added.path(), text());

        assertAll(
                () -> assertEquals(readme, Files.readString(added.path().resolve("README.md")),
                        "the worktree holds the checked-out files"),
                () -> assertTrue(info.linked(), "the added worktree is a linked worktree"),
                () -> assertEquals(newBranch, info.branch(), "the new branch is checked out"),
                () -> assertEquals(git.open(repo).value().commonDir(), info.commonDir(),
                        "the linked worktree shares the common dir"),
                () -> assertTrue(git.worktreeSha(added.path()).isOk(), "the linked worktree has a digest"),
                () -> assertFalse(Files.exists(admin(name).resolve("locked")),
                        "the initialization lock is removed"),
                () -> assertEquals(onBranch.id(), git.log(repo, newBranch, 1).value().getFirst()
                        .id(), "the commit lands on the new branch"),
                () -> assertEquals(branch, git.open(repo).value().branch(),
                        "the main worktree's HEAD is untouched"));
    }

    @Test
    @DisplayName("lists the main worktree first, then the linked ones")
    void lists() {
        var onBranch = names.next();
        var newBranch = names.next();
        var detachedName = names.next();
        add(onBranch, newBranch);
        var detached = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(detachedName),
                null, false, HEAD));
        assertTrue(detached.isOk(), "the detached worktree is added");
        var detachedFirst = detachedName.compareTo(onBranch) < 0;

        List<Worktree> list = git.worktreeList(repo).value();
        var detachedEntry = list.get(detachedFirst ? 1 : 2);
        var branchEntry = list.get(detachedFirst ? 2 : 1);

        assertAll(
                () -> assertEquals(3, list.size(), "the list holds the main and two linked trees"),
                () -> assertTrue(list.getFirst().main(), "the main worktree comes first"),
                () -> assertEquals(repo.toRealPath(), list.getFirst().path(), "the main entry is the repository"),
                () -> assertEquals(branch, list.getFirst().branch(), "the main entry has its branch"),
                () -> assertEquals(detachedName, detachedEntry.name(),
                        "the linked worktrees are ordered by name"),
                () -> assertNull(detachedEntry.branch(), "a detached worktree has no branch"),
                () -> assertNotNull(detachedEntry.head(), "a detached worktree has a HEAD"),
                () -> assertEquals(newBranch, branchEntry.branch(),
                        "the branch worktree reports its branch"),
                () -> assertEquals(list, git.worktreeList(branchEntry.path()).value(),
                        "a linked worktree lists the same worktrees"));
    }

    @Test
    @DisplayName("writes git's own administration layout (checked with the git CLI when present)")
    void interoperatesWithGitCli() throws Exception {
        var newBranch = names.next();
        Worktree added = add(names.next(), newBranch);
        // the git CLI and its porcelain output format are the contract the layout is checked against
        Path gitCli = Path.of("/usr/bin/git");
        Assumptions.assumeTrue(Files.isExecutable(gitCli), "git CLI not installed");

        var process = new ProcessBuilder(gitCli.toString(), "worktree", "list", "--porcelain").directory(repo.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertEquals(0, process.waitFor(), "the git CLI lists the worktrees"),
                () -> assertTrue(output.contains("worktree " + added.path()),
                        "the git CLI lists the linked worktree"),
                () -> assertTrue(output.contains("branch refs/heads/" + newBranch),
                        "the git CLI reports the worktree's branch"),
                () -> assertFalse(output.contains("prunable"), "the git CLI finds nothing to prune"));
    }

    @Test
    @DisplayName("refuses targets outside the root, existing targets, branches and unknown start points")
    void refusesAdd() throws Exception {
        var held = names.next();
        add(names.next(), held);
        var occupied = names.next();
        var again = names.next();
        write(".marshall/local/worktrees/" + occupied + "/" + file(), text());
        Path plain = Files.createDirectories(temp.resolve("plain"));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, git.worktreeAdd(new WorktreeAddInput(repo,
                                temp.resolve("outside"), null, false, HEAD)).outcome(),
                        "a target outside the root is refused"),
                () -> assertEquals(GitOutcome.ALREADY_EXISTS, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(occupied), null, false, HEAD)).outcome(),
                        "an occupied target already exists"),
                () -> assertEquals(GitOutcome.ALREADY_EXISTS, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(again), held, true, HEAD)).outcome(),
                        "a branch to create already exists"),
                () -> assertEquals(GitOutcome.BRANCH_CHECKED_OUT, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(again), held, false, HEAD)).outcome(),
                        "a branch a worktree holds is checked out"),
                () -> assertEquals(GitOutcome.BRANCH_CHECKED_OUT, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(again), branch, false, HEAD)).outcome(),
                        "the main worktree's branch is checked out"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(again), names.next(), false, HEAD)).outcome(),
                        "a missing branch is an unknown revision"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.worktreeAdd(new WorktreeAddInput(repo,
                                worktrees().resolve(again), null, false, names.next())).outcome(),
                        "an unknown start point is refused"),
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.worktreeAdd(new WorktreeAddInput(plain,
                                plain.resolve("wt"), null, false, HEAD)).outcome(),
                        "a root outside a repository is refused"));
    }

    @Test
    @DisplayName("checks out an existing branch that no worktree holds")
    void existingBranch() {
        var existing = names.next();
        Worktree first = add(names.next(), existing);
        assertTrue(git.worktreeRemove(repo, first.path()).isOk(), "the first worktree is removed");

        var again = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(names.next()),
                existing, false, names.next()));

        assertEquals(GitOutcome.OK, again.outcome(), "a free existing branch is checked out");
    }

    @Test
    @DisplayName("removes a clean worktree and refuses dirty, locked, main and unknown ones")
    void removes() throws Exception {
        var cleanName = names.next();
        var lockedName = names.next();
        Worktree clean = add(cleanName, names.next());
        Worktree dirty = add(names.next(), names.next());
        Worktree locked = add(lockedName, names.next());
        Files.writeString(dirty.path().resolve(file()), text());
        // the lock file of git's administration layout
        Files.writeString(admin(lockedName).resolve("locked"), text());
        Path plain = Files.createDirectories(temp.resolve("plain"));

        assertAll(
                () -> assertEquals(GitOutcome.WORKTREE_DIRTY, git.worktreeRemove(repo, dirty.path()).outcome(),
                        "a worktree with changes is dirty"),
                () -> assertEquals(GitOutcome.WORKTREE_LOCKED, git.worktreeRemove(repo, locked.path()).outcome(),
                        "a locked worktree is not removed"),
                () -> assertEquals(GitOutcome.PATH_REFUSED, git.worktreeRemove(repo, repo).outcome(),
                        "the main worktree is not removed"),
                () -> assertEquals(GitOutcome.WORKTREE_NOT_FOUND,
                        git.worktreeRemove(repo, temp.resolve(names.next())).outcome(),
                        "an unknown worktree is not found"),
                () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.worktreeRemove(plain, clean.path()).outcome(),
                        "a root outside a repository is refused"));

        var removed = git.worktreeRemove(repo, clean.path());

        assertAll(
                () -> assertEquals(GitOutcome.OK, removed.outcome(), "the clean worktree is removed"),
                () -> assertFalse(Files.exists(clean.path()), "the clean worktree's directory is gone"),
                () -> assertFalse(Files.exists(admin(cleanName)), "the clean worktree's admin dir is gone"),
                () -> assertEquals(3, git.worktreeList(repo).value().size(),
                        "the main and two refused worktrees remain"));
    }

    @Test
    @DisplayName("reports a worktree whose directory is gone as prunable and removes its administration")
    void prunable() throws Exception {
        var name = names.next();
        Worktree gone = add(name, names.next());
        LinkedWorktrees.deleteTree(gone.path());

        Worktree listed = git.worktreeList(repo).value().get(1);
        var removed = git.worktreeRemove(repo, gone.path());

        assertAll(
                () -> assertTrue(listed.prunable(), "a worktree without directory is prunable"),
                () -> assertEquals(GitOutcome.OK, removed.outcome(), "a prunable worktree is removed"),
                () -> assertFalse(Files.exists(admin(name)), "the prunable worktree's admin dir is gone"));
    }
}
