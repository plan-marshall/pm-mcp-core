/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The release's default assignment (Model Work § Default Role Assignment): the tier, the effort, the preferred harness
 * and the advisory per role, read from {@code classpath:/roles/default-assignment.json}.
 */
@EnableGeneratorController
@DisplayName("Default assignment")
class DefaultAssignmentTest {

    @TempDir
    Path base;

    private static final String HARNESS = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String ADVISORY = Generators.letterStrings(5, 30).next();

    private static final String DOCUMENT = """
            {"format_version":1,"roles":{
              "implement":{"tier":"standard","effort":"high","preferred_harness":"%s",
                           "advisory":"Implements the tasks of a plan."},
              "screener":{"tier":"fast","effort":null,"preferred_harness":"%s",
                          "advisory":"Screens untrusted text."}}}"""
            .formatted(HARNESS, HARNESS);

    @Test
    @DisplayName("reads every member of a role's default")
    void read() {
        var defaults = DefaultAssignment.read(base.resolve("default-assignment.json"), DOCUMENT.getBytes(UTF_8));

        var implement = defaults.of("implement").orElseThrow();
        assertAll(
                () -> assertEquals("standard", implement.tier(), "the implement role keeps its tier"),
                () -> assertEquals(new EffortValue("high"), implement.effort(),
                        "the implement role keeps its effort"),
                () -> assertEquals(HARNESS, implement.preferredHarness(),
                        "the implement role keeps its harness"),
                () -> assertEquals("Implements the tasks of a plan.", implement.advisory(),
                        "the implement role keeps its advisory"),
                () -> assertNull(defaults.of("screener").orElseThrow().effort(),
                        "the screener role has no effort"),
                () -> assertTrue(defaults.of("outline").isEmpty(), "the outline role is absent"));
    }

    @Test
    @DisplayName("the release's own default assignment is beside the shipped role set")
    void loadFromClasspath() {
        var loaded = DefaultAssignment.load(getClass().getClassLoader());

        assertAll(
                () -> assertTrue(loaded.isPresent(), "the default assignment is shipped"),
                () -> assertTrue(loaded.orElseThrow().of("steward").isPresent(),
                        "the default names the steward"),
                () -> assertEquals("standard", loaded.orElseThrow().of("steward").orElseThrow().tier(),
                        "the steward's tier is standard"));
    }

    @Test
    @DisplayName("a release that ships no default assignment answers empty")
    void absent() {
        assertTrue(DefaultAssignment.load(ClassLoader.getSystemClassLoader().getParent()).isEmpty(),
                "no shipped assignment answers empty");
    }

    @Test
    @DisplayName("a tier outside the closed list, a missing member or another format version is refused")
    void refused() {
        var file = base.resolve("default-assignment.json");
        var newer = "{\"format_version\":2,\"roles\":{}}".getBytes(UTF_8);
        var missing = "{\"format_version\":1,\"roles\":{\"implement\":{\"tier\":\"standard\"}}}".getBytes(UTF_8);
        var withoutRoles = "{\"format_version\":1}".getBytes(UTF_8);
        var badTier = assertThrows(IllegalArgumentException.class,
                () -> new DefaultAssignment.DefaultRole("enormous", null, HARNESS, ADVISORY),
                "a tier outside the list is refused");
        var newerVersion = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, newer),
                "a newer format version is refused");
        var missingMember = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, missing),
                "a role missing a member is refused");
        var noRoles = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, withoutRoles),
                "a document without roles is refused");

        assertAll(
                () -> assertTrue(badTier.getMessage().contains("enormous"), "the refusal names the bad tier"),
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, newerVersion.outcome(),
                        "the newer version reports FORMAT_NEWER"),
                () -> assertEquals(file, missingMember.file(), "the missing member names the file"),
                () -> assertEquals(file, noRoles.file(), "the roleless document names the file"));
    }

    @Test
    @DisplayName("the loaded document names the roles in its order and never a role outside it")
    void roles() {
        var defaults = DefaultAssignment.read(base.resolve("default-assignment.json"), DOCUMENT.getBytes(UTF_8));

        assertEquals(Set.of("implement", "screener"), defaults.roles().keySet(), "both named roles are read");
        assertFalse(defaults.roles().isEmpty(), "the role set is not empty");
    }
}
