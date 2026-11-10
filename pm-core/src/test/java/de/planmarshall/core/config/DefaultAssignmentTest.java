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
                () -> assertEquals("standard", implement.tier()),
                () -> assertEquals(new EffortValue("high"), implement.effort()),
                () -> assertEquals(HARNESS, implement.preferredHarness()),
                () -> assertEquals("Implements the tasks of a plan.", implement.advisory()),
                () -> assertNull(defaults.of("screener").orElseThrow().effort()),
                () -> assertTrue(defaults.of("outline").isEmpty()));
    }

    @Test
    @DisplayName("the release's own default assignment is beside the shipped role set")
    void loadFromClasspath() {
        var loaded = DefaultAssignment.load(getClass().getClassLoader());

        assertAll(
                () -> assertTrue(loaded.isPresent()),
                () -> assertTrue(loaded.orElseThrow().of("steward").isPresent()),
                () -> assertEquals("standard", loaded.orElseThrow().of("steward").orElseThrow().tier()));
    }

    @Test
    @DisplayName("a release that ships no default assignment answers empty")
    void absent() {
        assertTrue(DefaultAssignment.load(ClassLoader.getSystemClassLoader().getParent()).isEmpty());
    }

    @Test
    @DisplayName("a tier outside the closed list, a missing member or another format version is refused")
    void refused() {
        var file = base.resolve("default-assignment.json");
        var newer = "{\"format_version\":2,\"roles\":{}}".getBytes(UTF_8);
        var missing = "{\"format_version\":1,\"roles\":{\"implement\":{\"tier\":\"standard\"}}}".getBytes(UTF_8);
        var withoutRoles = "{\"format_version\":1}".getBytes(UTF_8);
        var badTier = assertThrows(IllegalArgumentException.class,
                () -> new DefaultAssignment.DefaultRole("enormous", null, HARNESS, ADVISORY));
        var newerVersion = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, newer));
        var missingMember = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, missing));
        var noRoles = assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file, withoutRoles));

        assertAll(
                () -> assertTrue(badTier.getMessage().contains("enormous")),
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, newerVersion.outcome()),
                () -> assertEquals(file, missingMember.file()),
                () -> assertEquals(file, noRoles.file()));
    }

    @Test
    @DisplayName("the loaded document names the roles in its order and never a role outside it")
    void roles() {
        var defaults = DefaultAssignment.read(base.resolve("default-assignment.json"), DOCUMENT.getBytes(UTF_8));

        assertEquals(Set.of("implement", "screener"), defaults.roles().keySet());
        assertFalse(defaults.roles().isEmpty());
    }
}
