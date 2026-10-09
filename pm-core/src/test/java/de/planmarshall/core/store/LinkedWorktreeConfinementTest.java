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
 * linked worktree, written by the test: the {@code .git} pointer file of the worktree and the {@code commondir}
 * file of its git directory. No git process runs.
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

    /** Writes the two pointer files that make {@code linkedWorktree} a linked worktree of {@code repository}. */
    private static void link(Path linkedWorktree, Path repository, String name) throws IOException {
        var gitDirectory = Files.createDirectories(repository.resolve(".git/worktrees").resolve(name));
        Files.writeString(gitDirectory.resolve("commondir"), "../..\n");
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
