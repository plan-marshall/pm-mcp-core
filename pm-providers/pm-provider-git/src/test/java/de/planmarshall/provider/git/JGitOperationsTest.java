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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.GitCredential;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOperations.PushInput;
import de.planmarshall.provider.git.GitOperations.RemoteInput;
import de.planmarshall.provider.git.GitOperations.Worktree;
import de.planmarshall.provider.git.GitOperations.WorktreeAddInput;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

@EnableGeneratorController
@DisplayName("JGitOperations (native git variant)")
class JGitOperationsTest {

    /** The revision git resolves to the checked-out commit: part of git's revision grammar. */
    private static final String HEAD = "HEAD";
    /** The remote name git gives the source of a clone: git's own default. */
    private static final String ORIGIN = "origin";

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path repo;
    private Identity author;
    private Identity agent;
    private String branch;
    private String readme;
    private TypedGenerator<String> names;

    @BeforeEach
    void initRepository() throws IOException {
        // names from lower-case letters outside the hex digits: valid ref and path names, never a commit id, ordered
        // as the administration directories are
        names = Generators.uniqueValues(Generators.strings("ghijklmnopqrstuvwxyz", 4, 12));
        author = new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
        agent = new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
        branch = names.next();
        readme = text();
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture path of the test repository
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, branch).isOk(), "the test repository is initialised");
        write("README.md", readme);
        commit(text());
    }

    private static String text() {
        return Generators.letterStrings(1, 40).next();
    }

    private String file() {
        return names.next() + ".txt";
    }

    private void write(String path, String content) throws IOException {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private CommitInfo commit(String message) {
        return commitIn(repo, message);
    }

    private CommitInfo commitIn(Path root, String message) {
        GitResult<CommitInfo> result = git.commit(new CommitInput(root, message, author, agent));
        assertEquals(GitOutcome.OK, result.outcome(), "the fixture commit is recorded");
        return result.value().orElseThrow();
    }

    private String sha(Path root) {
        GitResult<GitOperations.WorktreeSha> result = git.worktreeSha(root);
        assertEquals(GitOutcome.OK, result.outcome(), "the working-tree digest is computed");
        // contract literal: the worktree-sha hash version pinned by the specification is 1
        assertEquals(1, result.value().orElseThrow().version(),
                "the digest carries hash version 1");
        return result.value().orElseThrow().digest();
    }

    @Nested
    @DisplayName("open, commit and log")
    class OpenCommitLog {

        @Test
        @DisplayName("opens a repository and reports its facts")
        void opens() throws Exception {
            var info = git.open(repo).value().orElseThrow();

            assertAll(
                    () -> assertEquals(repo.toRealPath(), info.worktreeRoot(), "the worktree root is the real path"),
                    () -> assertEquals(Optional.of(branch), info.branch(), "the checked-out branch is reported"),
                    () -> assertTrue(info.head().isPresent(), "the HEAD commit id is reported"),
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
                            git.commit(new CommitInput(plain, text(), author, agent)).outcome(),
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
            var result = git.commit(new CommitInput(repo, text(), author, agent));

            assertEquals(GitOutcome.NOTHING_TO_COMMIT, result.outcome(), "a clean tree has nothing to commit");
        }

        @Test
        @DisplayName("walks the history newest first, bounded by the count")
        void logs() throws Exception {
            write(file(), text());
            CommitInfo second = commit(text());
            write(file(), text());
            CommitInfo third = commit(text());

            var all = git.log(repo, HEAD, 10).value().orElseThrow();
            var one = git.log(repo, branch, 1).value().orElseThrow();

            assertAll(
                    () -> assertEquals(List.of(third.id(), second.id()),
                            all.subList(0, 2).stream().map(CommitInfo::id).toList(), "the newest commits come first"),
                    () -> assertEquals(3, all.size(), "the history holds the three commits"),
                    () -> assertEquals(List.of(third), one, "a count of one yields the newest commit"),
                    () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.log(repo, names.next(), 1).outcome(),
                            "an unknown branch is an unknown revision"));
        }
    }

    @Nested
    @DisplayName("git.worktree-sha")
    class WorktreeShaTests {

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
            String head = git.open(repo).value().orElseThrow().head().orElseThrow();

            String expected = WorktreeShaDigestTest.independentDigest(head, List.of(
                    WorktreeShaDigestTest.file(a, aContent), WorktreeShaDigestTest.file(b, bContent),
                    WorktreeShaDigestTest.link(link, a)));

            assertEquals(expected, sha(repo), "the digest matches the independent framing");
        }
    }

    @Nested
    @DisplayName("linked worktrees")
    class Worktrees {

        private Path worktrees() {
            // fixture path: the worktree directory of the enrolled project
            return repo.resolve(".marshall/local/worktrees");
        }

        /** @return the administration directory git keeps for a linked worktree of that name */
        private Path admin(String name) {
            return repo.resolve(".git/worktrees").resolve(name);
        }

        private Worktree add(String name, String newBranch) {
            var result = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(name), Optional.of(newBranch),
                    true, HEAD));
            assertEquals(GitOutcome.OK, result.outcome(), "the linked worktree is added");
            return result.value().orElseThrow();
        }

        @Test
        @DisplayName("adds a worktree on a new branch, checked out and usable")
        void addsOnNewBranch() throws Exception {
            var name = names.next();
            var newBranch = names.next();
            Worktree added = add(name, newBranch);
            var info = git.open(added.path()).value().orElseThrow();

            Files.writeString(added.path().resolve(file()), text());
            CommitInfo onBranch = commitIn(added.path(), text());

            assertAll(
                    () -> assertEquals(readme, Files.readString(added.path().resolve("README.md")),
                            "the worktree holds the checked-out files"),
                    () -> assertTrue(info.linked(), "the added worktree is a linked worktree"),
                    () -> assertEquals(Optional.of(newBranch), info.branch(), "the new branch is checked out"),
                    () -> assertEquals(git.open(repo).value().orElseThrow().commonDir(), info.commonDir(),
                            "the linked worktree shares the common dir"),
                    () -> assertTrue(git.worktreeSha(added.path()).isOk(), "the linked worktree has a digest"),
                    () -> assertFalse(Files.exists(admin(name).resolve("locked")),
                            "the initialization lock is removed"),
                    () -> assertEquals(onBranch.id(), git.log(repo, newBranch, 1).value().orElseThrow().getFirst()
                            .id(), "the commit lands on the new branch"),
                    () -> assertEquals(Optional.of(branch), git.open(repo).value().orElseThrow().branch(),
                            "the main worktree's HEAD is untouched"));
        }

        @Test
        @DisplayName("lists the main worktree first, then the linked ones")
        void lists() throws Exception {
            var onBranch = names.next();
            var newBranch = names.next();
            var detachedName = names.next();
            add(onBranch, newBranch);
            var detached = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(detachedName),
                    Optional.empty(), false, HEAD));
            assertTrue(detached.isOk(), "the detached worktree is added");
            var detachedFirst = detachedName.compareTo(onBranch) < 0;

            List<Worktree> list = git.worktreeList(repo).value().orElseThrow();
            var detachedEntry = list.get(detachedFirst ? 1 : 2);
            var branchEntry = list.get(detachedFirst ? 2 : 1);

            assertAll(
                    () -> assertEquals(3, list.size(), "the list holds the main and two linked trees"),
                    () -> assertTrue(list.getFirst().main(), "the main worktree comes first"),
                    () -> assertEquals(repo.toRealPath(), list.getFirst().path(), "the main entry is the repository"),
                    () -> assertEquals(Optional.of(branch), list.getFirst().branch(), "the main entry has its branch"),
                    () -> assertEquals(Optional.of(detachedName), detachedEntry.name(),
                            "the linked worktrees are ordered by name"),
                    () -> assertTrue(detachedEntry.branch().isEmpty(), "a detached worktree has no branch"),
                    () -> assertTrue(detachedEntry.head().isPresent(), "a detached worktree has a HEAD"),
                    () -> assertEquals(Optional.of(newBranch), branchEntry.branch(),
                            "the branch worktree reports its branch"),
                    () -> assertEquals(list, git.worktreeList(branchEntry.path()).value().orElseThrow(),
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
                                    temp.resolve("outside"), Optional.empty(), false, HEAD)).outcome(),
                            "a target outside the root is refused"),
                    () -> assertEquals(GitOutcome.ALREADY_EXISTS, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(occupied), Optional.empty(), false, HEAD)).outcome(),
                            "an occupied target already exists"),
                    () -> assertEquals(GitOutcome.ALREADY_EXISTS, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(again), Optional.of(held), true, HEAD)).outcome(),
                            "a branch to create already exists"),
                    () -> assertEquals(GitOutcome.BRANCH_CHECKED_OUT, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(again), Optional.of(held), false, HEAD)).outcome(),
                            "a branch a worktree holds is checked out"),
                    () -> assertEquals(GitOutcome.BRANCH_CHECKED_OUT, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(again), Optional.of(branch), false, HEAD)).outcome(),
                            "the main worktree's branch is checked out"),
                    () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(again), Optional.of(names.next()), false, HEAD)).outcome(),
                            "a missing branch is an unknown revision"),
                    () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.worktreeAdd(new WorktreeAddInput(repo,
                                    worktrees().resolve(again), Optional.empty(), false, names.next())).outcome(),
                            "an unknown start point is refused"),
                    () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.worktreeAdd(new WorktreeAddInput(plain,
                                    plain.resolve("wt"), Optional.empty(), false, HEAD)).outcome(),
                            "a root outside a repository is refused"));
        }

        @Test
        @DisplayName("checks out an existing branch that no worktree holds")
        void existingBranch() {
            var existing = names.next();
            Worktree first = add(names.next(), existing);
            assertTrue(git.worktreeRemove(repo, first.path()).isOk(), "the first worktree is removed");

            var again = git.worktreeAdd(new WorktreeAddInput(repo, worktrees().resolve(names.next()),
                    Optional.of(existing), false, names.next()));

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
                    () -> assertEquals(3, git.worktreeList(repo).value().orElseThrow().size(),
                            "the main and two refused worktrees remain"));
        }

        @Test
        @DisplayName("reports a worktree whose directory is gone as prunable and removes its administration")
        void prunable() throws Exception {
            var name = names.next();
            Worktree gone = add(name, names.next());
            LinkedWorktrees.deleteTree(gone.path());

            Worktree listed = git.worktreeList(repo).value().orElseThrow().get(1);
            var removed = git.worktreeRemove(repo, gone.path());

            assertAll(
                    () -> assertTrue(listed.prunable(), "a worktree without directory is prunable"),
                    () -> assertEquals(GitOutcome.OK, removed.outcome(), "a prunable worktree is removed"),
                    () -> assertFalse(Files.exists(admin(name)), "the prunable worktree's admin dir is gone"));
        }
    }

    @Nested
    @DisplayName("fetch and push over file://")
    class Transport {

        private Path remote;
        private String remoteHead;

        @BeforeEach
        void bareRemote() throws GitAPIException, IOException, URISyntaxException {
            remoteHead = "refs/heads/" + branch;
            remote = temp.resolve("remote.git");
            Git.init().setBare(true).setDirectory(remote.toFile()).setInitialBranch(branch).setFs(new RefusingFS())
                    .call().close();
            try (Git jgit = Git.open(repo.toFile())) {
                jgit.remoteAdd().setName(ORIGIN).setUri(new URIish(remote.toUri().toString())).call();
            }
        }

        private GitResult<GitOperations.RefUpdate> push(Path root, String expected) {
            return git.push(new PushInput(root, ORIGIN, HEAD, remoteHead, Optional.ofNullable(expected),
                    Optional.empty()));
        }

        @Test
        @DisplayName("pushes, fetches into a second clone, and rejects a non-fast-forward and a stale lease")
        void pushFetchReject() throws Exception {
            String head = git.open(repo).value().orElseThrow().head().orElseThrow();
            var pushed = push(repo, null);
            assertEquals(GitOutcome.OK, pushed.outcome(), "the first push is accepted");
            assertEquals(Optional.of(head), pushed.value().orElseThrow().newId(), "the remote now holds the head");

            Path other = temp.resolve("other");
            Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(other.toFile()).setFs(new RefusingFS())
                    .call().close();
            Files.writeString(other.resolve(file()), text());
            CommitInfo otherCommit = commitIn(other, text());
            assertEquals(GitOutcome.OK, push(other, head).outcome(), "lease on the expected head holds");

            write(file(), text());
            commit(text());
            assertEquals(GitOutcome.REJECTED_NON_FAST_FORWARD, push(repo, null).outcome(),
                    "a push behind the remote is no fast-forward");
            assertEquals(GitOutcome.REJECTED_LEASE, push(repo, head).outcome(), "a stale lease is rejected");

            var fetched = git.fetch(new RemoteInput(repo, ORIGIN, List.of(), Optional.empty()));
            assertEquals(GitOutcome.OK, fetched.outcome(), "the fetch from the remote succeeds");
            assertEquals(Optional.of(otherCommit.id()), fetched.value().orElseThrow().stream()
                    .filter(u -> ("refs/remotes/" + ORIGIN + "/" + branch).equals(u.ref())).findFirst().orElseThrow()
                    .newId(), "the tracking ref holds the other clone's commit");
        }

        @Test
        @DisplayName("refuses remotes outside the policy before sending anything, and unknown remotes")
        void refusesRemotes() {
            // the https origin of the credential entry is the contract of the remote policy
            var httpsOnly = new JGitOperations(RemotePolicy.httpsOrigin(URI.create("https://github.com")));
            var generated = GitGenerators.credentials().next();
            var secret = new String(generated.secret());
            var credential = Optional.of(generated);

            assertAll(
                    () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH,
                            httpsOnly.fetch(new RemoteInput(repo, ORIGIN, List.of(), credential)).outcome(),
                            "a fetch outside the policy is refused"),
                    () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH, httpsOnly.push(new PushInput(repo, ORIGIN,
                                    HEAD, remoteHead, Optional.empty(), credential)).outcome(),
                            "a push outside the policy is refused"),
                    () -> assertEquals(GitOutcome.UNKNOWN_REVISION,
                            git.fetch(new RemoteInput(repo, names.next(), List.of(), Optional.empty())).outcome(),
                            "a fetch from an unknown remote is refused"),
                    () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.push(new PushInput(repo, ORIGIN, names.next(),
                                    remoteHead, Optional.empty(), Optional.empty())).outcome(),
                            "a push of an unknown ref is refused"),
                    () -> assertFalse(credential.orElseThrow().toString().contains(secret),
                            "the credential's text hides the secret"));
        }

        @Test
        @DisplayName("compares credentials by the content of the secret")
        void credentialEquality() {
            var credential = GitGenerators.credentials().next();
            var same = new GitCredential(credential.username(), credential.secret().clone());
            // the other values extend the generated ones, so they differ whatever was generated
            var otherSecret = (new String(credential.secret()) + text()).toCharArray();
            var otherUser = credential.username() + text();

            assertAll(
                    () -> assertEquals(credential, same, "an equal secret in another array is equal"),
                    () -> assertEquals(credential.hashCode(), same.hashCode(), "equal credentials share a hash code"),
                    () -> assertNotEquals(credential, new GitCredential(credential.username(), otherSecret),
                            "another secret makes another credential"),
                    () -> assertNotEquals(credential, new GitCredential(otherUser, credential.secret().clone()),
                            "another user makes another credential"),
                    () -> assertNotEquals(credential, (Object) new String(credential.secret()),
                            "a credential never equals its secret text"));
        }
    }

    @Nested
    @DisplayName("refusing file system")
    class Refusals {

        @Test
        @DisplayName("refuses a commit when a repository hook is present, writing nothing")
        void hookPresent() throws Exception {
            // the hook name and the refusal detail are the contract of git and of the outcome
            Path hook = repo.resolve(".git/hooks/pre-commit");
            var marker = names.next();
            Files.createDirectories(hook.getParent());
            Files.writeString(hook, "#!/bin/sh\ntouch " + marker + "\n");
            Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));
            String before = git.open(repo).value().orElseThrow().head().orElseThrow();
            write(file(), text());

            var result = git.commit(new CommitInput(repo, text(), author, agent));

            assertAll(
                    () -> assertEquals(GitOutcome.CAPABILITY_MISSING, result.outcome(),
                            "a present hook is a missing capability"),
                    () -> assertEquals("repository_hook", result.detail(), "the detail names the repository hook"),
                    () -> assertEquals(before, git.open(repo).value().orElseThrow().head().orElseThrow(),
                            "the refused commit leaves HEAD unchanged"),
                    () -> assertFalse(Files.exists(repo.resolve(marker)), "the hook is never run"));
        }

        @Test
        @DisplayName("refuses filter drivers instead of running them")
        void filterDriver() throws Exception {
            // the attribute and configuration keys of a git filter driver, and the refusal detail, are the contract
            var filter = names.next();
            write(".gitattributes", "*.txt filter=" + filter + "\n");
            try (Git jgit = Git.open(repo.toFile())) {
                StoredConfig config = jgit.getRepository().getConfig();
                config.setString("filter", filter, "clean", "tr a-z A-Z");
                config.save();
            }
            write(file(), text());

            var result = git.commit(new CommitInput(repo, text(), author, agent));

            assertAll(
                    () -> assertEquals(GitOutcome.CAPABILITY_MISSING, result.outcome(),
                            "a filter driver is a missing capability"),
                    () -> assertEquals("process_execution", result.detail(),
                            "the detail names the process execution"));
        }
    }
}
