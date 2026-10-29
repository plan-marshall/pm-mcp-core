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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
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
@DisplayName("Default assignment")
class DefaultAssignmentTest {

    @TempDir
    Path base;

    private static final String DOCUMENT = """
            {"format_version":1,"roles":{
              "implement":{"tier":"standard","effort":"high","preferred_harness":"acme-cli",
                           "advisory":"Implements the tasks of a plan."},
              "screener":{"tier":"fast","effort":null,"preferred_harness":"acme-cli",
                          "advisory":"Screens untrusted text."}}}""";

    @Test
    @DisplayName("reads every member of a role's default")
    void read() {
        var defaults = DefaultAssignment.read(base.resolve("default-assignment.json"), DOCUMENT.getBytes(UTF_8));

        var implement = defaults.of("implement").orElseThrow();
        assertAll(
                () -> assertEquals("standard", implement.tier()),
                () -> assertEquals(Optional.of(new EffortValue("high")), implement.effort()),
                () -> assertEquals("acme-cli", implement.preferredHarness()),
                () -> assertEquals("Implements the tasks of a plan.", implement.advisory()),
                () -> assertTrue(defaults.of("screener").orElseThrow().effort().isEmpty()),
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

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DefaultAssignment.DefaultRole("enormous", Optional.empty(), "acme-cli", "advice")),
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER,
                        assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file,
                                "{\"format_version\":2,\"roles\":{}}".getBytes(UTF_8))).outcome()),
                () -> assertThrows(StoreRefusal.class, () -> DefaultAssignment.read(file,
                        "{\"format_version\":1,\"roles\":{\"implement\":{\"tier\":\"standard\"}}}".getBytes(UTF_8))),
                () -> assertThrows(StoreRefusal.class,
                        () -> DefaultAssignment.read(file, "{\"format_version\":1}".getBytes(UTF_8))));
    }

    @Test
    @DisplayName("the loaded document names the roles in its order and never a role outside it")
    void roles() {
        var defaults = DefaultAssignment.read(base.resolve("default-assignment.json"), DOCUMENT.getBytes(UTF_8));

        assertEquals(Set.of("implement", "screener"), defaults.roles().keySet());
        assertFalse(defaults.roles().isEmpty());
    }
}
