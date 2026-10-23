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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The build test of the shipped profile set (PM-CLIENT-5, Profile Sets): every profile the release ships loads and
 * validates, its {@code client_id} is the name of its resource, and the detection precedences are pairwise different.
 */
@DisplayName("Shipped client profiles")
class ShippedProfilesTest {

    @Test
    @DisplayName("every shipped profile loads and validates")
    void everyShippedProfileValidates() {
        var shipped = ShippedProfiles.read().shipped();

        assertFalse(shipped.isEmpty(),
                "The resources of pm-api must hold at least one profile below classpath:/profiles/");
        assertAll(shipped.keySet().stream().map(clientId -> (Executable) () -> {
            var document = shipped.get(clientId).document();
            var profile = ProfileValidator.validate(document, "profiles/" + clientId + ".json", ProfileRules.none());
            assertEquals(clientId, ProfileValidator.clientId(profile),
                    "The client id of the profile must equal the name of its resource");
        }).toList());
    }

    @Test
    @DisplayName("the detection precedences of the shipped profiles are pairwise different")
    void precedencesArePairwiseDifferent() {
        var shipped = ShippedProfiles.read().shipped();
        var seen = new HashSet<Long>();
        for (var entry : shipped.entrySet()) {
            var profile = ProfileValidator.validate(entry.getValue().document(),
                    "profiles/" + entry.getKey() + ".json", ProfileRules.none());
            var detection = profile.member("detection").filter(value -> !value.isNull());
            if (detection.isEmpty()) {
                continue;
            }
            var precedence = detection.get().member("precedence");
            if (precedence.isEmpty() || precedence.get().integerValue().isEmpty()) {
                continue;
            }
            var value = precedence.get().integerValue().getAsLong();
            assertTrue(seen.add(value), () -> "The detection precedence %d occurs twice".formatted(value));
        }
    }

    @Test
    @DisplayName("a shipped set is read from the JAR of the dependency")
    void readsAJar(@TempDir Path directory) throws Exception {
        var document = ProfileFixtures.validBytes();
        var jarFile = directory.resolve("pm-api.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            addEntry(jar, "profiles/", null);
            addEntry(jar, "profiles/acme-cli.json", document);
            addEntry(jar, "profiles/nested/other.json", document);
            addEntry(jar, "elsewhere/third.json", document);
        }

        try (var loader = new URLClassLoader(new URL[]{jarFile.toUri().toURL()}, null)) {
            var shipped = ShippedProfiles.read(loader).shipped();

            assertEquals(Set.of("acme-cli"), shipped.keySet(), "only a document directly below profiles/ is shipped");
            assertArrayEquals(document, shipped.get("acme-cli").document());
        }
    }

    @Test
    @DisplayName("a shipped file that is not named by a client id refuses the read")
    void refusesAFileNameThatIsNoClientId(@TempDir Path directory) throws Exception {
        var profiles = Files.createDirectories(directory.resolve("profiles"));
        Files.write(profiles.resolve("Acme CLI.json"), ProfileFixtures.validBytes());

        try (var loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            var thrown = assertThrows(IllegalStateException.class, () -> ShippedProfiles.read(loader));

            assertTrue(thrown.getMessage().contains("Acme CLI.json"), thrown.getMessage());
        }
    }

    private static void addEntry(JarOutputStream jar, String name, byte[] content) throws IOException {
        jar.putNextEntry(new ZipEntry(name));
        if (content != null) {
            jar.write(content);
        }
        jar.closeEntry();
    }
}
