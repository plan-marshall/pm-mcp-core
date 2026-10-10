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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The linked worktrees of the path confinement (PM-SEC-2). The fixtures are the plain files git writes for a
 * linked worktree, written by the test: the {@code .git} pointer file of the worktree and, in its git directory
 * below {@code .git/worktrees} of the repository, the {@code commondir} file and the {@code gitdir} record that
 * names the worktree back. No git process runs.
 */
@DisplayName("PathConfinement with linked worktrees")
class LinkedWorktreeConfinementTest {

    private static final String OUTCOME_CODE = "path_traversal_rejected";

    @TempDir
    Path temp;

    private Path base;

    /** The canonical enrolled root, a repository with a {@code .git} directory. */
    private Path root;

    /** A canonical linked worktree of the enrolled root, beside it. */
    private Path worktree;

    /** A canonical second repository beside the enrolled root. */
    private Path other;

    @BeforeEach
    void repositories() throws IOException {
        base = temp.toRealPath();
        root = repository("repo");
        other = repository("other");
        worktree = Files.createDirectories(base.resolve("linked/src")).getParent();
        Files.writeString(worktree.resolve("src/Main.java"), "worktree");
        link(worktree, root, "linked");
    }

    private Path repository(String name) throws IOException {
        var repository = Files.createDirectories(base.resolve(name).resolve(".git/worktrees")).getParent().getParent();
        Files.writeString(repository.resolve("README.md"), name);
        return repository;
    }

    /** Writes the three pointer files that make {@code linkedWorktree} a linked worktree of {@code repository}. */
    private static void link(Path linkedWorktree, Path repository, String name) throws IOException {
        register(linkedWorktree, repository.resolve(".git/worktrees").resolve(name), "../..");
    }

    /**
     * Writes the pointer files of a linked worktree with its git directory at any place: the {@code commondir}
     * file, the {@code gitdir} record naming the worktree back, and the {@code .git} file of the worktree.
     */
    private static void register(Path linkedWorktree, Path gitDirectory, String commonDirectory) throws IOException {
        Files.createDirectories(gitDirectory);
        Files.writeString(gitDirectory.resolve("commondir"), commonDirectory + "\n");
        Files.writeString(gitDirectory.resolve("gitdir"), linkedWorktree.resolve(".git") + "\n");
        Files.writeString(linkedWorktree.resolve(".git"), "gitdir: " + gitDirectory + "\n");
    }

    private Path directoryBesideRoot(String name) throws IOException {
        var directory = Files.createDirectories(base.resolve(name));
        Files.writeString(directory.resolve("file.txt"), name);
        return directory;
    }

    private ConfinementOutcome confine(Path path, Path... liveWorktrees) {
        return PathConfinement.confine(new WorkspaceRoots(root, Set.of(liveWorktrees)), path.toString());
    }

    private static void assertRefused(ConfinementOutcome outcome) {
        var rejected = assertInstanceOf(Rejected.class, outcome, "The path should be refused");
        assertAll("refusal",
                () -> assertEquals(OUTCOME_CODE, rejected.code(), "A refusal should carry the closed outcome code"),
                () -> assertEquals(Reason.OUTSIDE_WORKSPACE, rejected.reason(),
                        "The path should count as outside the workspace"));
    }

    private static void assertAccepted(Path expected, ConfinementOutcome outcome) {
        var confined = assertInstanceOf(Confined.class, outcome, "The path should be accepted");
        assertEquals(expected, confined.path(), "The outcome should carry the canonical path");
    }

    @Nested
    @DisplayName("a live linked worktree of the enrolled root")
    class LiveWorktree {

        @ParameterizedTest(name = "accepts <worktree>/{0}")
        @ValueSource(strings = {"src/Main.java", "src", "", "src/new/File.java", ".git"})
        @DisplayName("accepts a path inside it, the worktree root and a target that does not exist yet included")
        void acceptsInside(String belowWorktree) {
            var outcome = confine(worktree.resolve(belowWorktree), worktree);

            assertAccepted(worktree.resolve(belowWorktree), outcome);
        }

        @Test
        @DisplayName("accepts it when the common directory is named by an absolute path")
        void acceptsAbsoluteCommonDirectory() throws Exception {
            Files.writeString(root.resolve(".git/worktrees/linked/commondir"), root.resolve(".git") + "\n");

            var outcome = confine(worktree.resolve("src/Main.java"), worktree);

            assertAccepted(worktree.resolve("src/Main.java"), outcome);
        }

        @Test
        @DisplayName("accepts it when the caller names it through a symbolic link of the platform")
        void acceptsWorktreeNamedThroughTemp() {
            var outcome = confine(temp.resolve("linked/src/Main.java"), temp.resolve("linked"));

            assertAccepted(worktree.resolve("src/Main.java"), outcome);
        }

        @Test
        @DisplayName("still accepts the enrolled root and still refuses what lies beside both")
        void leavesTheRootRuleAsItIs() throws Exception {
            var beside = directoryBesideRoot("beside");

            assertAll(
                    () -> assertAccepted(root.resolve("README.md"), confine(root.resolve("README.md"), worktree)),
                    () -> assertRefused(confine(beside.resolve("file.txt"), worktree)),
                    () -> assertRefused(confine(other.resolve("README.md"), worktree)));
        }
    }

    @Nested
    @DisplayName("a worktree that does not belong to the enrolled root")
    class ForgedWorktree {

        @Test
        @DisplayName("refuses a linked worktree that is not in the live set")
        void refusesWorktreeNotLive() {
            assertRefused(confine(worktree.resolve("src/Main.java")));
        }

        @Test
        @DisplayName("refuses a worktree whose common directory resolves to another repository")
        void refusesWorktreeOfOtherRepository() throws Exception {
            var foreign = directoryBesideRoot("foreign");
            link(foreign, other, "foreign");

            assertRefused(confine(foreign.resolve("file.txt"), foreign));
        }

        @Test
        @DisplayName("refuses a pointer into the enrolled root whose commondir file names another repository")
        void refusesForgedCommonDirectory() throws Exception {
            var forged = directoryBesideRoot("forged");
            link(forged, root, "forged");
            Files.writeString(root.resolve(".git/worktrees/forged/commondir"), other.resolve(".git") + "\n");

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @Test
        @DisplayName("refuses a directory whose .git is missing")
        void refusesMissingGit() throws Exception {
            var plain = directoryBesideRoot("plain");

            assertRefused(confine(plain.resolve("file.txt"), plain));
        }

        @Test
        @DisplayName("refuses a directory whose .git is a directory, another repository itself included")
        void refusesGitDirectory() {
            assertRefused(confine(other.resolve("README.md"), other));
        }

        @Test
        @DisplayName("refuses a directory whose .git is a directory with the content of a git directory of the root")
        void refusesGitDirectoryWithCommonDirectory() throws Exception {
            var copy = directoryBesideRoot("copy");
            Files.createDirectories(copy.resolve(".git"));
            Files.writeString(copy.resolve(".git/commondir"), root.resolve(".git") + "\n");

            assertRefused(confine(copy.resolve("file.txt"), copy));
        }

        @ParameterizedTest(name = "pointer {index}")
        @ValueSource(strings = {"", "gitdir:", "gitdir:   \n", "worktree: %s", "%s", "gitdir: %s/absent"})
        @DisplayName("refuses a .git file that is no pointer to a git directory")
        void refusesPointerFileWithoutTarget(String content) throws Exception {
            var forged = directoryBesideRoot("forged");
            link(forged, root, "forged");
            Files.writeString(forged.resolve(".git"), content.formatted(root.resolve(".git/worktrees/forged")));

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @Test
        @DisplayName("refuses a .git file that is too large for a pointer")
        void refusesOversizedPointerFile() throws Exception {
            var forged = directoryBesideRoot("forged");
            link(forged, root, "forged");
            var pointer = Files.readString(forged.resolve(".git"));
            Files.writeString(forged.resolve(".git"), pointer + "\n".repeat(8192));

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @ParameterizedTest(name = "commondir {index}")
        @ValueSource(strings = {"", "..", "../../..", "../../absent"})
        @DisplayName("refuses a git directory whose commondir file does not name the .git directory of the root")
        void refusesCommonDirectoryElsewhere(String content) throws Exception {
            var forged = directoryBesideRoot("forged");
            link(forged, root, "forged");
            Files.writeString(root.resolve(".git/worktrees/forged/commondir"), content);

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @Test
        @DisplayName("refuses a git directory without a commondir file")
        void refusesMissingCommonDirectoryFile() throws Exception {
            var forged = directoryBesideRoot("forged");
            link(forged, root, "forged");
            Files.delete(root.resolve(".git/worktrees/forged/commondir"));

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @Test
        @DisplayName("refuses every worktree when the enrolled root is itself a linked worktree")
        void refusesWhenRootHasNoGitDirectory() throws Exception {
            var enrolled = directoryBesideRoot("enrolled");
            link(enrolled, root, "enrolled");
            var roots = new WorkspaceRoots(enrolled, Set.of(worktree));

            var outcome = PathConfinement.confine(roots, worktree.resolve("src/Main.java").toString());

            assertRefused(outcome);
        }

        @Test
        @DisplayName("refuses a live worktree that does not exist")
        void refusesAbsentWorktree() throws Exception {
            var beside = directoryBesideRoot("beside");

            assertRefused(confine(beside.resolve("file.txt"), base.resolve("absent")));
        }
    }

    @Nested
    @DisplayName("the registration of a linked worktree with the enrolled root")
    class Registration {

        /** A canonical live candidate beside the enrolled root, registered and named back like a worktree of git. */
        private Path candidate;

        /** The git directory of the candidate, registered below {@code .git/worktrees} of the enrolled root. */
        private Path gitDirectory;

        @BeforeEach
        void registered() throws IOException {
            candidate = directoryBesideRoot("candidate");
            link(candidate, root, "candidate");
            gitDirectory = root.resolve(".git/worktrees/candidate");
        }

        @Test
        @DisplayName("accepts a worktree that is registered below .git/worktrees and named back by its record")
        void acceptsRegisteredWorktree() {
            assertAccepted(candidate.resolve("file.txt"), confine(candidate.resolve("file.txt"), candidate));
        }

        @Test
        @DisplayName("accepts a gitdir record that names the worktree by a path relative to the git directory")
        void acceptsRelativeBackPointer() throws Exception {
            var relative = gitDirectory.relativize(candidate.resolve(".git"));
            Files.writeString(gitDirectory.resolve("gitdir"), relative + "\n");

            var outcome = confine(candidate.resolve("file.txt"), candidate);

            assertAccepted(candidate.resolve("file.txt"), outcome);
        }

        @ParameterizedTest(name = "git directory at <base>/{0}")
        @ValueSource(strings = {"repo/.git/unregistered", "outside/admin", "repo/.git/worktrees/nested/admin"})
        @DisplayName("refuses a git directory that is not registered directly below .git/worktrees of the root")
        void refusesUnregisteredGitDirectory(String belowBase) throws Exception {
            var forged = directoryBesideRoot("forged");
            register(forged, base.resolve(belowBase), root.resolve(".git").toString());

            assertRefused(confine(forged.resolve("file.txt"), forged));
        }

        @Test
        @DisplayName("refuses a registered git directory without a gitdir record")
        void refusesMissingBackPointer() throws Exception {
            Files.delete(gitDirectory.resolve("gitdir"));

            assertRefused(confine(candidate.resolve("file.txt"), candidate));
        }

        @ParameterizedTest(name = "gitdir {index}")
        @ValueSource(strings = {"%s/linked/.git", "%s/candidate", "%s/absent/.git", "", "  \n"})
        @DisplayName("refuses a gitdir record that does not name the .git file of the candidate")
        void refusesBackPointerElsewhere(String content) throws Exception {
            Files.writeString(gitDirectory.resolve("gitdir"), content.formatted(base));

            assertRefused(confine(candidate.resolve("file.txt"), candidate));
        }

        @Test
        @DisplayName("refuses a gitdir record that is too large for a pointer")
        void refusesOversizedBackPointer() throws Exception {
            var backPointer = Files.readString(gitDirectory.resolve("gitdir"));
            Files.writeString(gitDirectory.resolve("gitdir"), backPointer + "\n".repeat(8192));

            assertRefused(confine(candidate.resolve("file.txt"), candidate));
        }

        @Test
        @DisplayName("refuses a gitdir record that is a directory")
        void refusesBackPointerDirectory() throws Exception {
            Files.delete(gitDirectory.resolve("gitdir"));
            Files.createDirectories(gitDirectory.resolve("gitdir"));

            assertRefused(confine(candidate.resolve("file.txt"), candidate));
        }

        @Test
        @DisplayName("refuses a second directory that points at the git directory of a registered worktree")
        void refusesSharedGitDirectory() throws Exception {
            var second = directoryBesideRoot("second");
            Files.writeString(second.resolve(".git"), "gitdir: " + gitDirectory + "\n");

            assertAll(
                    () -> assertRefused(confine(second.resolve("file.txt"), candidate, second)),
                    () -> assertAccepted(candidate.resolve("file.txt"),
                            confine(candidate.resolve("file.txt"), candidate, second)));
        }

        @Test
        @DisplayName("refuses a worktree moved by hand until its gitdir record is rewritten")
        void refusesMovedWorktreeUntilRepaired() throws Exception {
            var moved = Files.move(candidate, base.resolve("moved"));
            var stale = confine(moved.resolve("file.txt"), moved);
            Files.writeString(gitDirectory.resolve("gitdir"), moved.resolve(".git") + "\n");

            var repaired = confine(moved.resolve("file.txt"), moved);

            assertAll(
                    () -> assertRefused(stale),
                    () -> assertAccepted(moved.resolve("file.txt"), repaired));
        }

        @Nested
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
        @DisplayName("through a symbolic link")
        class ThroughLinks {

            @Test
            @DisplayName("refuses a git directory below .git/worktrees that is a link to a directory outside")
            void refusesLinkedGitDirectory() throws Exception {
                var forged = directoryBesideRoot("forged");
                var registered = root.resolve(".git/worktrees/forged");
                register(forged, base.resolve("outside/admin"), root.resolve(".git").toString());
                Files.createSymbolicLink(registered, base.resolve("outside/admin"));
                Files.writeString(forged.resolve(".git"), "gitdir: " + registered + "\n");

                assertRefused(confine(forged.resolve("file.txt"), forged));
            }

            @Test
            @DisplayName("refuses every worktree when .git/worktrees of the root is itself a link")
            void refusesLinkedRegistry() throws Exception {
                var registry = Files.move(root.resolve(".git/worktrees"), base.resolve("registry"));
                Files.createSymbolicLink(root.resolve(".git/worktrees"), registry);
                Files.writeString(gitDirectory.resolve("commondir"), root.resolve(".git") + "\n");

                assertRefused(confine(candidate.resolve("file.txt"), candidate));
            }
        }
    }

    @Nested
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
    @DisplayName("a symbolic link in a live linked worktree")
    class SymbolicLinks {

        private Path beside;

        @BeforeEach
        void links() throws IOException {
            beside = directoryBesideRoot("beside");
            Files.createSymbolicLink(worktree.resolve("file-out"), beside.resolve("file.txt"));
            Files.createSymbolicLink(worktree.resolve("dir-out"), beside);
            Files.createSymbolicLink(worktree.resolve("to-other"), other);
            Files.createSymbolicLink(worktree.resolve("dir-in"), worktree.resolve("src"));
            Files.createSymbolicLink(worktree.resolve("to-root"), root);
        }

        @ParameterizedTest(name = "refuses <worktree>/{0}")
        @ValueSource(strings = {"file-out", "dir-out/file.txt", "dir-out/new.txt", "to-other/README.md"})
        @DisplayName("refuses a link that points out of the worktree and out of the root")
        void refusesLinkOut(String belowWorktree) {
            assertRefused(confine(worktree.resolve(belowWorktree), worktree));
        }

        @Test
        @DisplayName("accepts a link that stays inside the worktree, with the link resolved")
        void acceptsLinkInside() {
            assertAccepted(worktree.resolve("src/Main.java"), confine(worktree.resolve("dir-in/Main.java"), worktree));
        }

        @Test
        @DisplayName("accepts a link from the worktree into the enrolled root, which is inside the workspace")
        void acceptsLinkIntoRoot() {
            assertAccepted(root.resolve("README.md"), confine(worktree.resolve("to-root/README.md"), worktree));
        }

        @Test
        @DisplayName("refuses a directory whose .git is a symbolic link to the pointer file of a live worktree")
        void refusesLinkedPointerFile() throws Exception {
            Files.createSymbolicLink(beside.resolve(".git"), worktree.resolve(".git"));

            assertRefused(confine(beside.resolve("file.txt"), beside));
        }
    }
}
