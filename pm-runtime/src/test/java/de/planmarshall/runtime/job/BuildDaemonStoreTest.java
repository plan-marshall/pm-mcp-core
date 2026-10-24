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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-project build daemon store (PM-EXEC-4, PM-SEC-5): the digest of a write set is stable, order-independent and
 * 16 lower-case hex characters, and the store of one project and write set sits apart from every other.
 */
@DisplayName("Build daemon store")
class BuildDaemonStoreTest {

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{16}");

    @TempDir
    Path base;

    private static List<Path> writeSet() {
        return List.of(Path.of("/work/project"), Path.of("/home/operator/.m2/repository"), Path.of("/tmp"));
    }

    @Test
    @DisplayName("the digest is the first 16 hex characters of the SHA-256 over the sorted write set")
    void digestShape() {
        var digest = BuildDaemonStore.digest(writeSet());

        assertAll(
                () -> assertTrue(DIGEST.matcher(digest).matches(), digest),
                () -> assertEquals(digest, BuildDaemonStore.digest(List.of(Path.of("/tmp"),
                                Path.of("/work/project"), Path.of("/home/operator/.m2/repository"))),
                        "the order of the write set does not change the digest"),
                () -> assertNotEquals(digest, BuildDaemonStore.digest(List.of(Path.of("/tmp"),
                        Path.of("/work/other"))), "another write set gives another digest"));
    }

    @Test
    @DisplayName("the store is projects/<project>/daemons/<digest>")
    void storePath() {
        var store = BuildDaemonStore.path(base, "job-project", writeSet());

        assertAll(
                () -> assertEquals(base.resolve("projects/job-project/daemons")
                        .resolve(BuildDaemonStore.digest(writeSet())), store),
                () -> assertEquals(store.resolve("mvnd"), BuildDaemonStore.mvndDirectory(store)),
                () -> assertEquals(store.resolve("gradle"), BuildDaemonStore.gradleDirectory(store)),
                () -> assertEquals("-Dorg.gradle.daemon.registry.base=" + store.resolve("gradle"),
                        BuildDaemonStore.gradleRegistryArgument(store)));
    }

    @Test
    @DisplayName("a duplicate path does not change the digest")
    void duplicatePath() {
        assertEquals(BuildDaemonStore.digest(List.of(Path.of("/tmp"))),
                BuildDaemonStore.digest(List.of(Path.of("/tmp"), Path.of("/tmp"))));
    }

    @Test
    @DisplayName("a relative base is refused")
    void relativeBase() {
        var paths = writeSet();
        var relativeBase = Path.of("base");
        assertThrows(IllegalArgumentException.class, () -> BuildDaemonStore.path(relativeBase, "p", paths));
    }
}
