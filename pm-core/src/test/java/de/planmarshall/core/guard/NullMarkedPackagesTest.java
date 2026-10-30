/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards {@code @NullMarked} on every production package of the reactor.
 * <p>
 * The contract of the coding skills is that every production package carries a {@code package-info.java} annotated
 * with {@code org.jspecify.annotations.NullMarked}. The scan reads the source trees of the reactor from the repository
 * root, the same way {@code BuildGuardsIT} finds its fixtures, so a later plan that adds a package without the
 * annotation fails this build instead of leaving the contract incomplete.
 */
@DisplayName("Production packages are @NullMarked")
class NullMarkedPackagesTest {

    private static final String PRODUCTION_SOURCE = "src/main/java";

    private static final String PACKAGE_INFO = "package-info.java";

    private static final String NULL_MARKED = "@NullMarked";

    @Test
    @DisplayName("every production package of the reactor is @NullMarked")
    void everyProductionPackageIsNullMarked() {
        var root = repositoryRoot();
        var unmarked = new ArrayList<Path>();
        for (var productionRoot : productionRoots(root)) {
            unmarked.addAll(unmarkedPackages(productionRoot));
        }

        assertTrue(unmarked.isEmpty(), () -> "Every production package must be @NullMarked: " + unmarked);
    }

    @Test
    @DisplayName("a package without package-info.java is reported")
    void packageWithoutPackageInfoIsReported(@TempDir Path temp) throws Exception {
        var packageDir = packageDirectory(temp, "unmarked/without/info");
        writeSource(packageDir, "Marker.java", "final class Marker {}");

        assertTrue(unmarkedPackages(temp).contains(packageDir), "A package without package-info must be reported");
    }

    @Test
    @DisplayName("a package-info.java without @NullMarked is reported")
    void packageInfoWithoutNullMarkedIsReported(@TempDir Path temp) throws Exception {
        var packageDir = packageDirectory(temp, "unmarked/without/annotation");
        writeSource(packageDir, PACKAGE_INFO, "package unmarked.without.annotation;");

        assertTrue(unmarkedPackages(temp).contains(packageDir), "An unannotated package must be reported");
    }

    @Test
    @DisplayName("an annotated package is not reported")
    void annotatedPackageIsNotReported(@TempDir Path temp) throws Exception {
        var packageDir = packageDirectory(temp, "marked/package");
        writeSource(packageDir, PACKAGE_INFO,
                "@NullMarked\npackage marked.package;\n\nimport org.jspecify.annotations.NullMarked;");

        assertFalse(unmarkedPackages(temp).contains(packageDir), "An annotated package must not be reported");
    }

    private static Path repositoryRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        for (var candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("pm-core"))
                    && Files.isDirectory(candidate.resolve("pm-providers"))) {
                return candidate;
            }
        }
        return fail("the root of the pm-mcp-core reactor was not found");
    }

    private static List<Path> productionRoots(Path root) {
        var roots = new ArrayList<Path>();
        roots.add(root.resolve("pm-core").resolve(PRODUCTION_SOURCE));
        roots.add(root.resolve("pm-workflow").resolve(PRODUCTION_SOURCE));
        roots.add(root.resolve("pm-runtime").resolve(PRODUCTION_SOURCE));
        var providers = root.resolve("pm-providers");
        try (var modules = Files.list(providers)) {
            modules.filter(Files::isDirectory)
                    .map(module -> module.resolve(PRODUCTION_SOURCE))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(roots::add);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return roots;
    }

    private static List<Path> unmarkedPackages(Path productionRoot) {
        if (!Files.isDirectory(productionRoot)) {
            return List.of();
        }
        try (var files = Files.walk(productionRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .map(Path::getParent)
                    .distinct()
                    .filter(packageDir -> !isNullMarked(packageDir))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean isNullMarked(Path packageDir) {
        var packageInfo = packageDir.resolve(PACKAGE_INFO);
        if (!Files.isRegularFile(packageInfo)) {
            return false;
        }
        try {
            return Files.readString(packageInfo).contains(NULL_MARKED);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path packageDirectory(Path root, String name) throws IOException {
        var directory = root.resolve(name.replace('.', '/'));
        Files.createDirectories(directory);
        return directory;
    }

    private static void writeSource(Path directory, String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content);
    }
}
