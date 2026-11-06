/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("Native library path")
class NativeLibraryPathTest {

    /** @return a generated absolute directory of two segments */
    private static String directory() {
        return "/" + Generators.letterStrings(2, 10).next() + "/" + Generators.letterStrings(2, 10).next();
    }

    @Test
    @DisplayName("prepends the executable directory to the library path")
    void shouldPrependLibraryPath() {
        var dir = directory();
        var existing = directory();

        assertAll(
                () -> assertEquals(dir, NativeLibraryPath.prepend(Path.of(dir), null),
                        "without a library path only the directory is set"),
                () -> assertEquals(dir, NativeLibraryPath.prepend(Path.of(dir), ""),
                        "an empty library path is replaced by the directory"),
                () -> assertEquals(dir + File.pathSeparator + existing, NativeLibraryPath.prepend(Path.of(dir), existing),
                        "the directory must come before the library path"));
    }

    @Test
    @DisplayName("leaves the library path of a JVM alone")
    void shouldIgnoreJvm() {
        var before = System.getProperty(NativeLibraryPath.LIBRARY_PATH);

        NativeLibraryPath.includeExecutableDirectory();

        assertEquals(before, System.getProperty(NativeLibraryPath.LIBRARY_PATH),
                "a JVM must keep its library path unchanged");
    }
}
