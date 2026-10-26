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
@DisplayName("Linked worktrees")
class LinkedWorktreesTest {

    @TempDir
    Path base;

    @Test
    @DisplayName("a common directory without worktrees/ has no linked worktree")
    void listWithoutWorktrees() throws Exception {
        var commonDir = Files.createDirectories(base.resolve(".git"));

        assertEquals(List.of(), LinkedWorktrees.list(commonDir));
    }

    @Test
    @DisplayName("deleting an absent root is no error")
    void deleteAbsentTree() {
        assertDoesNotThrow(() -> LinkedWorktrees.deleteTree(base.resolve("absent")));
    }

    @Test
    @DisplayName("a deletion removes the files and directories of the tree")
    void deleteTree() throws Exception {
        var root = Files.createDirectories(base.resolve("tree").resolve("nested"));
        var file = Files.writeString(root.resolve("file.txt"), "content", StandardCharsets.UTF_8);

        LinkedWorktrees.deleteTree(base.resolve("tree"));

        assertAll(
                () -> assertFalse(Files.exists(file)),
                () -> assertFalse(Files.exists(base.resolve("tree"))));
    }

    @Test
    @DisplayName("removing an absent lock is no error")
    void unlockAbsent() throws Exception {
        var admin = Files.createDirectories(base.resolve("admin"));

        assertAll(
                () -> assertDoesNotThrow(() -> LinkedWorktrees.unlock(admin)),
                () -> assertTrue(Files.isDirectory(admin)));
    }
}
