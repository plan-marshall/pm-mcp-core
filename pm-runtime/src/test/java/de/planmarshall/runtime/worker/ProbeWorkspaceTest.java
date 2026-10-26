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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * only permissions, a canary and an outside read path of its own, the observation of a run, and a whole removal when
 * it closes.
 */
@DisplayName("The probe workspace")
class ProbeWorkspaceTest {

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    @Test
    @DisplayName("a fresh workspace carries a canary under a 0700 root and an outside read path")
    void freshWorkspaceCarriesACanary(@TempDir Path parent) throws Exception {
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
                            "the workspace is owner-only"),
                    () -> assertTrue(Files.isDirectory(workspace.outsideRoot()), "the outside path is a directory"),
                    () -> assertEquals(parent, workspace.outsideRoot().getParent(),
                            "the outside path lies beside the workspace"),
                    () -> assertTrue(workspace.outsideRoot().getFileName().toString()
                                    .startsWith(ProbeWorkspace.OUTSIDE_DIRECTORY_PREFIX),
                            "the outside path carries its own prefix"),
                    () -> assertTrue(Files.isReadable(workspace.outsideRoot().resolve(ProbeWorkspace.OUTSIDE_FILE)),
                            "the outside read path is readable"));
        }
    }

    @Test
    @DisplayName("two workspaces never share a canary")
    void canariesAreFresh(@TempDir Path parent) throws Exception {
        try (var one = ProbeWorkspace.create(parent); var two = ProbeWorkspace.create(parent)) {
            assertAll(
                    () -> assertNotEquals(one.canary(), two.canary(), "each run gets its own token"),
                    () -> assertNotEquals(one.root(), two.root(), "each run gets its own root"),
                    () -> assertNotEquals(one.outsideRoot(), two.outsideRoot(),
                            "each run gets its own outside path"));
        }
    }

    @Test
    @DisplayName("close removes the workspace, its outside path and everything in them")
    void closeRemovesTheTree(@TempDir Path parent) throws Exception {
        var workspace = ProbeWorkspace.create(parent);
        var root = workspace.root();
        var outside = workspace.outsideRoot();
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested").resolve("file.txt"), "content", StandardCharsets.UTF_8);

        workspace.close();

        assertAll(
                () -> assertFalse(Files.exists(root), "the workspace is gone"),
                () -> assertFalse(Files.exists(outside), "the outside path is gone"));
    }

    @Test
    @DisplayName("closing twice removes nothing more and does not throw")
    void closeIsIdempotent(@TempDir Path parent) throws Exception {
        var workspace = ProbeWorkspace.create(parent);

        workspace.close();

        assertDoesNotThrow(workspace::close, "a second close finds both paths gone");
    }

    @Test
    @DisplayName("a run that keeps to its workspace is observed isolated, and both paths are removed")
    void aRunThatStaysInsideIsObservedIsolated(@TempDir Path parent) throws Exception {
        var workspace = ProbeWorkspace.create(parent);
        var root = workspace.root();
        var outside = workspace.outsideRoot();
        Files.writeString(root.resolve("answer.txt"), "the answer", StandardCharsets.UTF_8);

        var observation = workspace.observe("the answer");

        assertAll(
                () -> assertFalse(observation.canaryReturned(), "the canary is not named"),
                () -> assertTrue(observation.outsideWrites().isEmpty(), "nothing is written outside"),
                () -> assertTrue(observation.stayedInside(), "the worker stayed inside"));

        workspace.close();

        assertAll(
                () -> assertFalse(Files.exists(root), "the workspace is gone after a clean run"),
                () -> assertFalse(Files.exists(outside), "the outside path is gone after a clean run"));
    }

    @Test
    @DisplayName("a canary read and a write outside the workspace are detected, and both paths are removed")
    void aCanaryReadOrAnOutsideWriteIsDetected(@TempDir Path parent) throws Exception {
        var workspace = ProbeWorkspace.create(parent);
        var root = workspace.root();
        var outside = workspace.outsideRoot();
        var createdOutside = outside.resolve("escape.txt");
        var readPath = outside.resolve(ProbeWorkspace.OUTSIDE_FILE);
        Files.writeString(createdOutside, "escaped", StandardCharsets.UTF_8);
        Files.writeString(readPath, "tampered", StandardCharsets.UTF_8);

        var observation = workspace.observe("the token is " + workspace.canary());

        assertAll(
                () -> assertTrue(observation.canaryReturned(), "the canary is named"),
                () -> assertTrue(observation.outsideWrites().contains(createdOutside),
                        "a new path outside is reported"),
                () -> assertTrue(observation.outsideWrites().contains(readPath),
                        "a changed path outside is reported"),
                () -> assertFalse(observation.stayedInside(), "the worker left its workspace"));

        workspace.close();

        assertAll(
                () -> assertFalse(Files.exists(root), "the workspace is gone after a violation"),
                () -> assertFalse(Files.exists(outside), "the outside path is gone after a violation"));
    }
}
