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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one rule of a path a commit cannot stage (PM-WF-16, Stages 3 and 4): its textual part, its file-system part with
 * the parent confinement through a symbolic link, and its tracked-tree part, which stays silent on a repository it
 * cannot read.
 */
@EnableGeneratorController
@DisplayName("Commit path refusals")
class CommitPathRefusalsTest {

    private static String name() {
        return Generators.letterStrings(1, 12).next().toLowerCase(Locale.ROOT) + ".txt";
    }

    @Nested
    @DisplayName("The textual rule")
    class Textual {

        @Test
        @DisplayName("the shared rule delegates to the textual rule of CommitPaths")
        void delegatesToCommitPaths() {
            assertAll(
                    () -> assertTrue(CommitPathRefusals.isRefusedText(":!include"), "pathspec magic is refused"),
                    () -> assertTrue(CommitPathRefusals.isRefusedText("a\\b"), "a backslash is refused"),
                    () -> assertTrue(CommitPathRefusals.isRefusedText("a\u0000b"), "a name with a NUL is refused"),
                    () -> assertFalse(CommitPathRefusals.isRefusedText("app/[slug]/page.tsx"),
                            "a glob character is a literal path"));
        }
    }

    @Nested
    @DisplayName("The file-system rule")
    class FileSystem {

        @TempDir
        Path root;

        @TempDir
        Path outside;

        @Test
        @DisplayName("a real directory, an absolute path and a parent through a link to outside are refused")
        void refusedLocations() throws Exception {
            Files.createDirectories(root.resolve("dir"));
            Files.createDirectories(root.resolve("real"));
            Files.createSymbolicLink(root.resolve("out"), outside);

            assertAll(
                    () -> assertTrue(CommitPathRefusals.isRefusedLocation(root, "dir"),
                            "a directory is refused"),
                    () -> assertTrue(CommitPathRefusals.isRefusedLocation(root, outside.resolve(name()).toString()),
                            "an absolute path is refused"),
                    () -> assertTrue(CommitPathRefusals.isRefusedLocation(root, "out/" + name()),
                            "a path through a link to outside is refused"),
                    () -> assertFalse(CommitPathRefusals.isRefusedLocation(root, "real/" + name()),
                            "a name below the root is admitted"));
        }

        @Test
        @DisplayName("a final symbolic link is a legal commit entry, a real link to outside or dangling")
        void finalLinkIsAdmitted() throws Exception {
            Files.createSymbolicLink(root.resolve("link"), outside);
            Files.createSymbolicLink(root.resolve("dangling"), outside.resolve("gone"));

            assertAll(
                    () -> assertFalse(CommitPathRefusals.isRefusedLocation(root, "link"),
                            "a final link to outside is admitted"),
                    () -> assertFalse(CommitPathRefusals.isRefusedLocation(root, "dangling"),
                            "a final dangling link is admitted"),
                    () -> assertTrue(CommitPathRefusals.isRefusedLocation(root, "dangling/" + name()),
                            "a path through a dangling link is refused"));
        }
    }

    @Nested
    @DisplayName("The tracked-tree rule")
    class TrackedTree {

        @TempDir
        Path worktree;

        @Test
        @DisplayName("a worktree without a repository stays silent and refuses no candidate")
        void unreadableRepositoryStaysSilent() {
            var names = List.of(name(), name());

            var refused = CommitPathRefusals.refusedTrackedTrees(worktree, names);

            assertEquals(List.of(), List.copyOf(refused), "an unreadable repository refuses no name");
        }

        @Test
        @DisplayName("an empty candidate set refuses nothing without opening the repository")
        void emptyCandidatesRefuseNothing() {
            assertEquals(List.of(), List.copyOf(CommitPathRefusals.refusedTrackedTrees(worktree, List.of())),
                    "no candidate names nothing to refuse");
        }

        @Test
        @DisplayName("a repository whose config cannot be read still leaves the rule silent")
        void unreadableConfigStaysSilent() throws Exception {
            var gitDir = worktree.resolve(".git");
            Files.createDirectories(gitDir.resolve("objects"));
            Files.createDirectories(gitDir.resolve("refs"));
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
            // an unterminated section: JGit answers the config as unreadable, the IOException half of the rule's catch
            Files.writeString(gitDir.resolve("config"), "[invalid");
            var names = List.of(name());

            assertAll(
                    () -> assertThrows(IOException.class,
                            () -> CommitPathRefusals.open(worktree, new RefusingFS()),
                            "the JGit contract answers an IOException for the config"),
                    () -> assertEquals(List.of(), List.copyOf(CommitPathRefusals.refusedTrackedTrees(worktree, names)),
                            "an unreadable repository refuses no name"));
        }
    }
}
