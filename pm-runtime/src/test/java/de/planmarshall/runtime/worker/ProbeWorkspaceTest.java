/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The probe workspace (PM-CLIENT-5, Profile Verification): a fresh directory under the temporary directory with owner-
 * only permissions and a canary of its own, removed whole when it closes.
 */
@DisplayName("The probe workspace")
class ProbeWorkspaceTest {

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    @Test
    @DisplayName("a fresh workspace carries a canary under a 0700 root")
    void freshWorkspaceCarriesACanary(@TempDir Path parent) throws IOException {
        try (var workspace = ProbeWorkspace.create(parent)) {
            assertAll(
                    () -> assertTrue(Files.isDirectory(workspace.root()), "the root is a directory"),
                    () -> assertEquals(parent, workspace.root().getParent(), "the workspace is created below its parent"),
                    () -> assertTrue(workspace.root().getFileName().toString()
                            .startsWith(ProbeWorkspace.DIRECTORY_PREFIX), "the root carries the probe prefix"),
                    () -> assertTrue(workspace.canaryReadable(), "the canary file is readable"),
                    () -> assertEquals(ProbeWorkspace.CANARY_FILE,
                            workspace.canaryFile().getFileName().toString(), "the canary file has its name"),
                    () -> assertEquals(workspace.canary(),
                            Files.readString(workspace.canaryFile(), StandardCharsets.UTF_8),
                            "the file carries the token"),
                    () -> assertTrue(workspace.canary().startsWith("pm-probe-canary-"),
                            "the token names its purpose"),
                    () -> assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(workspace.root()),
                            "the workspace is owner-only"));
        }
    }

    @Test
    @DisplayName("two workspaces never share a canary")
    void canariesAreFresh(@TempDir Path parent) throws IOException {
        try (var one = ProbeWorkspace.create(parent); var two = ProbeWorkspace.create(parent)) {
            assertAll(
                    () -> assertNotEquals(one.canary(), two.canary(), "each run gets its own token"),
                    () -> assertNotEquals(one.root(), two.root(), "each run gets its own root"));
        }
    }

    @Test
    @DisplayName("close removes the workspace and everything in it")
    void closeRemovesTheTree(@TempDir Path parent) throws IOException {
        var workspace = ProbeWorkspace.create(parent);
        var root = workspace.root();
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested").resolve("file.txt"), "content", StandardCharsets.UTF_8);

        workspace.close();

        assertFalse(Files.exists(root), "the workspace is gone");
    }
}
