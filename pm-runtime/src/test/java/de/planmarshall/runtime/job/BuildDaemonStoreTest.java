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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * The per-project build daemon store (PM-EXEC-4, PM-SEC-5): the digest of a write set is stable, order-independent and
 * 16 lower-case hex characters, and the store of one project and write set sits apart from every other.
 * <p>
 * Contract literals that stay: the digest shape, the store layout {@code projects/<project>/daemons/<digest>} with
 * its {@code mvnd} and {@code gradle} directories, and the Gradle registry argument. The project id and the paths of
 * the write set are generated.
 */
@EnableGeneratorController
@DisplayName("Build daemon store")
class BuildDaemonStoreTest {

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{16}");

    @TempDir
    Path base;

    private Path first;
    private Path second;
    private Path third;

    @BeforeEach
    void drawWriteSet() {
        first = absolute("first-");
        second = absolute("second-");
        third = absolute("third-");
    }

    /** @return a generated absolute path whose first segment starts with the prefix */
    private static Path absolute(String prefix) {
        return Path.of("/", prefix + words().next(), words().next());
    }

    private List<Path> writeSet() {
        return List.of(first, second, third);
    }

    @Test
    @DisplayName("the digest is the first 16 hex characters of the SHA-256 over the sorted write set")
    void digestShape() {
        var digest = BuildDaemonStore.digest(writeSet());

        assertAll(
                () -> assertTrue(DIGEST.matcher(digest).matches(), "the digest is 16 lower-case hex characters"),
                () -> assertEquals(digest, BuildDaemonStore.digest(List.of(third, first, second)),
                        "the order of the write set does not change the digest"),
                () -> assertNotEquals(digest, BuildDaemonStore.digest(List.of(third, absolute("other-"))),
                        "another write set gives another digest"));
    }

    @Test
    @DisplayName("the store is projects/<project>/daemons/<digest>")
    void storePath() {
        var project = CoreGenerators.projectEnrolments().next().projectId();

        var store = BuildDaemonStore.path(base, project, writeSet());

        assertAll(
                () -> assertEquals(base.resolve("projects").resolve(project).resolve("daemons")
                        .resolve(BuildDaemonStore.digest(writeSet())), store,
                        "the store sits below the project's daemons"),
                () -> assertEquals(store.resolve("mvnd"), BuildDaemonStore.mvndDirectory(store),
                        "the mvnd directory sits in the store"),
                () -> assertEquals(store.resolve("gradle"), BuildDaemonStore.gradleDirectory(store),
                        "the gradle directory sits in the store"),
                () -> assertEquals("-Dorg.gradle.daemon.registry.base=" + store.resolve("gradle"),
                        BuildDaemonStore.gradleRegistryArgument(store),
                        "the registry argument names the directory"));
    }

    @Test
    @DisplayName("a duplicate path does not change the digest")
    void duplicatePath() {
        assertEquals(BuildDaemonStore.digest(List.of(first)), BuildDaemonStore.digest(List.of(first, first)),
                "a duplicate path leaves the digest unchanged");
    }

    @Test
    @DisplayName("a relative base is refused")
    void relativeBase() {
        var paths = writeSet();
        var relativeBase = Path.of(words().next());
        var project = CoreGenerators.projectEnrolments().next().projectId();

        assertThrows(IllegalArgumentException.class, () -> BuildDaemonStore.path(relativeBase, project, paths),
                "a relative base is refused");
    }
}
