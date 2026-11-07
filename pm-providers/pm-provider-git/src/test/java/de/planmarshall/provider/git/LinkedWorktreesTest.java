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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The native administration of git's linked worktrees (JGit has no {@code git worktree} command): a common directory without
 * a {@code worktrees/} subtree has no linked worktree, and a deletion removes a tree of files and directories, while a
 * missing root or lock is no error.
 */
@EnableGeneratorController
@DisplayName("Linked worktrees")
class LinkedWorktreesTest {

    @TempDir
    Path base;

    private static String name() {
        return Generators.letterStrings(3, 12).next();
    }

    @Test
    @DisplayName("a common directory without worktrees/ has no linked worktree")
    void listWithoutWorktrees() throws Exception {
        // the common git directory of a repository is named .git
        var commonDir = Files.createDirectories(base.resolve(".git"));

        assertEquals(List.of(), LinkedWorktrees.list(commonDir), "no worktrees directory lists no worktree");
    }

    @Test
    @DisplayName("deleting an absent root is no error")
    void deleteAbsentTree() {
        var absent = base.resolve(name());

        assertDoesNotThrow(() -> LinkedWorktrees.deleteTree(absent), "deleting an absent tree does not fail");
    }

    @Test
    @DisplayName("a deletion removes the files and directories of the tree")
    void deleteTree() throws Exception {
        var tree = base.resolve(name());
        var nested = Files.createDirectories(tree.resolve(name()));
        var file = Files.writeString(nested.resolve(name() + ".txt"), Generators.letterStrings(1, 40).next(),
                StandardCharsets.UTF_8);

        LinkedWorktrees.deleteTree(tree);

        assertAll(
                () -> assertFalse(Files.exists(file), "the nested file is deleted"),
                () -> assertFalse(Files.exists(tree), "the root of the tree is deleted"));
    }

    @Test
    @DisplayName("removing an absent lock is no error")
    void unlockAbsent() throws Exception {
        var admin = Files.createDirectories(base.resolve(name()));

        assertAll(
                () -> assertDoesNotThrow(() -> LinkedWorktrees.unlock(admin), "unlocking without a lock does not fail"),
                () -> assertTrue(Files.isDirectory(admin), "the administration directory remains"));
    }
}
