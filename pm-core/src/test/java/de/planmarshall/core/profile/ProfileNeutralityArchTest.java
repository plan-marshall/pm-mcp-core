/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.fixtures.ClientNamedFixture;

/**
 * Guards that no class of the profile loader names a harness (PM-CLIENT-5): what a harness is, is the profile document,
 * and the loader implements the mechanisms the schema names. The scan reads the compiled main classes of the package,
 * so a class name, a member name, a string constant and a switch label all count: a class that names a client ties the
 * loader to that client, and a harness could no longer be added as a document.
 * <p>
 * The control fixture {@link ClientNamedFixture} proves that the scan finds a client named in a constant pool.
 */
@DisplayName("Harness neutrality of the profile loader")
class ProfileNeutralityArchTest {

    /** The client ids no class of the loader may name, compared in lower case. */
    private static final List<String> CLIENT_NAMES = List.of("claude", "opencode", "antigravity", "codex");

    private static final String PACKAGE = "de/planmarshall/core/profile";

    @Test
    @DisplayName("the loader names no harness")
    void loaderNamesNoHarness() throws IOException {
        var violations = violationsIn(packageClasses());

        assertFalse(packageClasses().isEmpty(), "The scan must find the classes of the profile package");
        assertTrue(violations.isEmpty(), () -> "A class names a harness: " + violations);
    }

    @Test
    @DisplayName("the scan finds a harness named in a class constant pool")
    void controlFixtureFails() throws IOException {
        var found = violationsIn(List.of(classFileOf(ClientNamedFixture.class)));

        assertTrue(found.values().stream().flatMap(Set::stream).anyMatch("opencode"::equals), () -> found.toString());
    }

    private static Map<Path, Set<String>> violationsIn(List<Path> classFiles) throws IOException {
        var violations = new LinkedHashMap<Path, Set<String>>();
        for (var classFile : classFiles) {
            var text = Files.readString(classFile, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            var found = CLIENT_NAMES.stream().filter(text::contains).collect(Collectors.toCollection(TreeSet::new));
            if (!found.isEmpty()) {
                violations.put(classFile, found);
            }
        }
        return violations;
    }

    private static List<Path> packageClasses() throws IOException {
        var directory = codeSourceOf(ProfileValidator.class).resolve(PACKAGE);
        try (var files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".class")).toList();
        }
    }

    private static Path classFileOf(Class<?> type) {
        var resource = type.getResource(type.getSimpleName() + ".class");
        if (resource == null) {
            throw new IllegalStateException("The class file of " + type.getName() + " is not on the classpath");
        }
        return toPath(resource);
    }

    private static Path codeSourceOf(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new IllegalStateException("The code source of " + type.getName() + " is unknown");
        }
        return toPath(source.getLocation());
    }

    private static Path toPath(java.net.URL url) {
        try {
            return Path.of(url.toURI());
        } catch (java.net.URISyntaxException | java.nio.file.FileSystemNotFoundException e) {
            throw new IllegalStateException("Cannot resolve a classpath location: " + url, e);
        }
    }
}
