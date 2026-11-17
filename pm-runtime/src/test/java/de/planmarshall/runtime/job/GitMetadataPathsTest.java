/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The metadata paths of a project (PM-WATCH-JOB-48): the git directory and the {@code .git} link file of every linked
 * worktree, found over trees laid out as git writes them, with a symmetric link refused as a value.
 * <p>
 * Contract literals that stay: the directory and pointer-file names git writes ({@code .git}, {@code worktrees},
 * {@code gitdir}, {@code commondir}, {@code gitdir:}), and the reason codes of the result. The directory names below
 * the temporary tree and the file contents are generated.
 */
@EnableGeneratorController
@DisplayName("Git metadata paths")
class GitMetadataPathsTest {

    private static final String GITDIR_PREFIX = "gitdir: ";

    @TempDir
    Path base;

    @Nested
    @DisplayName("Finds")
    class Finds {

        @Test
        @DisplayName("Should find the git directory of a plain repository")
        void shouldFindGitDirectoryOfPlainRepository() throws Exception {
            var root = plainRepository(base.resolve(name()));

            var result = GitMetadataPaths.of(root);

            assertAll("Plain repository",
                    () -> assertEquals(root.resolve(".git"), result.gitDirectory(), "Git directory is .git"),
                    () -> assertTrue(result.linkFiles().isEmpty(), "Plain repository has no link file"),
                    () -> assertTrue(result.skipped().isEmpty(), "Plain repository skips no record"),
                    () -> assertTrue(result.refused().isEmpty(), "Plain repository refuses no path"));
        }

        @Test
        @DisplayName("Should find the common directory of a root that is a linked worktree")
        void shouldFindCommonDirectoryOfLinkedWorktreeRoot() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var worktreeRoot = main.resolveSibling(name());
            linkedWorktree(main, worktreeRoot, name());

            var result = GitMetadataPaths.of(worktreeRoot);

            assertAll("Linked worktree root",
                    () -> assertEquals(main.resolve(".git"), result.gitDirectory(), "Common directory is main .git"),
                    () -> assertEquals(List.of(worktreeRoot.resolve(".git")), result.linkFiles(),
                            "Own link file is returned"),
                    () -> assertTrue(result.refused().isEmpty(), "Linked worktree refuses no path"));
        }

        @Test
        @DisplayName("Should find linked worktrees inside and outside the root in one sorted set")
        void shouldFindLinkedWorktreesInsideAndOutsideRoot() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var inside = linkedWorktree(main, main.resolve(name()),
                    name());
            var outside = linkedWorktree(main, base.resolve(name()),
                    name());

            var result = GitMetadataPaths.of(main);

            var expected = List.of(inside.resolve(".git"), outside.resolve(".git")).stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString)).toList();
            assertAll("Two linked worktrees",
                    () -> assertEquals(main.resolve(".git"), result.gitDirectory(), "Git directory is main .git"),
                    () -> assertEquals(expected, result.linkFiles(), "Both link files are returned sorted"),
                    () -> assertTrue(result.skipped().isEmpty(), "Valid records are not skipped"),
                    () -> assertTrue(result.refused().isEmpty(), "Valid worktrees are not refused"));
        }
    }

    @Nested
    @DisplayName("Skips and refuses")
    class SkipsAndRefuses {

        @Test
        @DisplayName("Should skip a stale worktree record and name its reason")
        void shouldSkipStaleWorktreeRecord() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var name = name();
            var gitdirFile = Files.createDirectories(main.resolve(".git/worktrees").resolve(name))
                    .resolve("gitdir");
            var target = base.resolve("gone").resolve(".git");
            Files.writeString(gitdirFile, target + "\n");

            var result = GitMetadataPaths.of(main);

            assertAll("Stale record",
                    () -> assertEquals(main.resolve(".git"), result.gitDirectory(), "Git directory is still found"),
                    () -> assertTrue(result.linkFiles().isEmpty(), "Stale record yields no link file"),
                    () -> assertEquals(List.of(new GitMetadataPaths.Skipped(target,
                                    GitMetadataPaths.TARGET_MISSING)), result.skipped(),
                            "Stale record is skipped with its reason"));
        }

        @Test
        @DisplayName("Should return an empty result for a project without git")
        void shouldReturnEmptyResultWithoutGit() throws Exception {
            var root = Files.createDirectories(base.resolve(name()));

            var result = GitMetadataPaths.of(root);

            assertAll("No git",
                    () -> assertNull(result.gitDirectory(), "No git directory is returned"),
                    () -> assertTrue(result.linkFiles().isEmpty(), "No link file is returned"),
                    () -> assertTrue(result.skipped().isEmpty(), "no worktree record is skipped here"),
                    () -> assertTrue(result.refused().isEmpty(), "no path is refused in this tree"));
        }

        @Test
        @DisplayName("Should skip a record that names another administration directory")
        void shouldSkipRecordThatNamesAnotherAdministration() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var worktree = main.resolve(name());
            var admin = Files.createDirectories(main.resolve(".git/worktrees").resolve(name()));
            var otherAdmin = Files.createDirectories(base.resolve(name()));
            Files.writeString(admin.resolve("commondir"), "../..\n");
            Files.createDirectories(worktree);
            Files.writeString(worktree.resolve(".git"), GITDIR_PREFIX + otherAdmin + "\n");
            Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git") + "\n");

            var result = GitMetadataPaths.of(main);

            Files.writeString(worktree.resolve(".git"), GITDIR_PREFIX + admin + "\n");
            Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git") + "\n");
            var control = GitMetadataPaths.of(main);

            assertAll("Record naming another administration",
                    () -> assertTrue(result.linkFiles().isEmpty(), "the record yields no link file"),
                    () -> assertEquals(List.of(new GitMetadataPaths.Skipped(admin.resolve("gitdir"),
                                    GitMetadataPaths.NOT_NAMED_BACK)), result.skipped(),
                            "the record names another administration"),
                    () -> assertEquals(List.of(worktree.resolve(".git")), control.linkFiles(),
                            "the repaired record yields the link file"));
        }

        @Test
        @DisplayName("Should refuse a symbolic link as the git directory of a linked worktree")
        void shouldRefuseSymlinkAsGitDirectoryOfWorktree() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var admin = Files.createDirectories(main.resolve(".git/worktrees").resolve(name()));
            Files.writeString(admin.resolve("commondir"), "../..\n");
            var adminLink = main.resolve(".git/worktrees").resolve(name() + "-link");
            Files.createSymbolicLink(adminLink, admin);
            var worktree = Files.createDirectories(base.resolve(name()));
            Files.writeString(worktree.resolve(".git"), GITDIR_PREFIX + adminLink + "\n");

            var result = GitMetadataPaths.of(worktree);

            Files.delete(adminLink);
            Files.createDirectories(adminLink);
            Files.writeString(adminLink.resolve("commondir"), "../..\n");
            var control = GitMetadataPaths.of(worktree);

            assertAll("Symlinked administration directory",
                    () -> assertNull(result.gitDirectory(), "a refused administration directory yields none"),
                    () -> assertEquals(List.of(new GitMetadataPaths.Refused(adminLink,
                                    GitMetadataPaths.PATH_TRAVERSAL_REJECTED)), result.refused(),
                            "the symlinked administration directory is refused"),
                    () -> assertEquals(main.resolve(".git"), control.gitDirectory(),
                            "the real administration directory is found"));
        }

        @Test
        @DisplayName("Should refuse a symbolic link as a worktree's link file")
        void shouldRefuseSymlinkAsLinkFile() throws Exception {
            var main = plainRepository(base.resolve(name()));
            var target = Files.createDirectories(base.resolve(name()));
            var linkFile = target.resolve(".git");
            Files.createSymbolicLink(linkFile, main.resolve(".git"));
            var admin = Files.createDirectories(main.resolve(".git/worktrees").resolve(name()));
            Files.writeString(admin.resolve("gitdir"), linkFile + "\n");

            var result = GitMetadataPaths.of(main);

            Files.delete(linkFile);
            Files.writeString(linkFile, GITDIR_PREFIX + admin + "\n");
            Files.writeString(admin.resolve("commondir"), "../..\n");
            var control = GitMetadataPaths.of(main);

            assertAll("Symlinked link file",
                    () -> assertTrue(result.linkFiles().isEmpty(), "the refused link file is not returned"),
                    () -> assertEquals(List.of(new GitMetadataPaths.Refused(linkFile,
                                    GitMetadataPaths.PATH_TRAVERSAL_REJECTED)), result.refused(),
                            "the symlinked link file is refused"),
                    () -> assertEquals(List.of(linkFile), control.linkFiles(), "the real link file is found"));
        }

        @Test
        @DisplayName("Should refuse a symbolic link as .git and find the control without it")
        void shouldRefuseSymlinkAsGitDirectory() throws Exception {
            var root = Files.createDirectories(base.resolve(name()));
            var target = Files.createDirectories(base.resolve(name()));
            Files.createSymbolicLink(root.resolve(".git"), target);

            var refused = GitMetadataPaths.of(root);

            var control = plainRepositoryIn(root);

            assertAll("Symlinked .git",
                    () -> assertTrue(refused.linkFiles().isEmpty(), "A refused path yields no link file"),
                    () -> assertNull(refused.gitDirectory(), "A refused .git yields no directory"),
                    () -> assertEquals(List.of(new GitMetadataPaths.Refused(root.resolve(".git"),
                                    GitMetadataPaths.PATH_TRAVERSAL_REJECTED)), refused.refused(),
                            "Symlinked .git is refused"),
                    () -> assertEquals(root.resolve(".git"), control.gitDirectory(),
                            "Control without the link finds the directory"));
        }
    }

    /** Replaces the symbolic link of the root with a real git directory: the positive control of the same tree. */
    private static GitMetadataPaths.Result plainRepositoryIn(Path root) throws IOException {
        Files.delete(root.resolve(".git"));
        Files.createDirectories(root.resolve(".git/hooks"));
        Files.writeString(root.resolve(".git/config"), name());
        return GitMetadataPaths.of(root);
    }

    /** @return a generated directory name without a separator */
    private static String name() {
        return "d-" + Generators.longs(1, Long.MAX_VALUE).next();
    }

    private static Path plainRepository(Path root) throws IOException {
        Files.createDirectories(root.resolve(".git/hooks"));
        Files.writeString(root.resolve(".git/config"), name());
        return root;
    }

    private static Path linkedWorktree(Path main, Path worktree, String name) throws IOException {
        var admin = Files.createDirectories(main.resolve(".git/worktrees").resolve(name));
        Files.writeString(admin.resolve("commondir"), "../..\n");
        Files.writeString(admin.resolve("config.worktree"), name());
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), GITDIR_PREFIX + admin + "\n");
        Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git") + "\n");
        return worktree;
    }
}
