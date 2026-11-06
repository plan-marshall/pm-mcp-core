/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The role set loader applies the precedence of {@code model-work/01#role-sets}: an artifact replaces the artifact of the
 * same name of a lower set as a whole, members are never merged, a higher set adds a role, and an invalid artifact of a
 * higher set leaves the role undefined instead of falling back to the lower set.
 */
@EnableGeneratorController
@DisplayName("Role set precedence")
class RoleSetLoaderTest {

    @TempDir
    Path machine;

    @TempDir
    Path project;

    @Test
    @DisplayName("the project artifact replaces the machine and shipped artifact as a whole")
    void projectReplacesWhole() throws Exception {
        var machineCount = Generators.integers(3, 8).next();
        var projectCount = Generators.integers(9, 16).next();
        var description = Generators.letterStrings(10, 60).next();
        write(machine, RoleFixtures.ROLE, RoleFixtures.valid().set("/count", machineCount)
                .set("/context_budget_tokens", Generators.integers(10_000, 500_000).next()).bytes());
        write(project, RoleFixtures.ROLE, RoleFixtures.valid().set("/count", projectCount)
                .set("/description", description).bytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine).withProject(project));

        assertAll(
                () -> assertEquals(projectCount, set.effective(RoleFixtures.ROLE).orElseThrow().count()),
                () -> assertEquals(description,
                        set.effective(RoleFixtures.ROLE).orElseThrow().description()),
                () -> assertTrue(set.effective(RoleFixtures.ROLE).orElseThrow().declaredContextBudget().isEmpty(),
                        "the project artifact omits the machine artifact's context budget"),
                () -> assertEquals(RoleSetKind.PROJECT, set.kind(RoleFixtures.ROLE).orElseThrow()));
    }

    @Test
    @DisplayName("the machine set of the test directory replaces the shipped artifact")
    void machineReplacesShipped() throws Exception {
        var count = Generators.integers(3, 16).next();
        write(machine, RoleFixtures.ROLE, RoleFixtures.valid().set("/count", count).bytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine));

        assertEquals(count, set.effective(RoleFixtures.ROLE).orElseThrow().count());
    }

    @Test
    @DisplayName("a set adds a role that the lower sets do not define")
    void higherSetAddsRole() throws Exception {
        var role = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        write(machine, role, RoleFixtures.valid().set("/name", role).bytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine));

        assertAll(
                () -> assertTrue(set.effective(role).isPresent()),
                () -> assertEquals(300, set.effective(role).orElseThrow().budgetFloorSeconds()));
    }

    @Test
    @DisplayName("an invalid higher artifact is undefined, never the lower artifact")
    void invalidHigherArtifactDoesNotFallBack() throws Exception {
        write(machine, RoleFixtures.ROLE, RoleFixtures.valid().set("/bogus", 1).bytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine));

        assertAll(
                () -> assertTrue(set.effective(RoleFixtures.ROLE).isEmpty()),
                () -> assertFalse(set.refused().isEmpty()),
                () -> assertEquals(RoleValidationReason.SCHEMA_INVALID,
                        set.refused().getFirst().refusal().reason()));
    }

    @Test
    @DisplayName("a file whose name differs from the artifact name is schema_invalid")
    void fileNameMustEqualName() throws Exception {
        var role = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        write(machine, role, RoleFixtures.validBytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine));

        assertAll(
                () -> assertTrue(set.effective(role).isEmpty()),
                () -> assertEquals(RoleValidationReason.SCHEMA_INVALID,
                        set.refused().getFirst().refusal().reason()));
    }

    @Test
    @DisplayName("a refusal of a lower set is gone when a higher set defines the role")
    void refusalIsSuperseded() throws Exception {
        write(machine, RoleFixtures.ROLE, RoleFixtures.valid().set("/bogus", 1).bytes());
        write(project, RoleFixtures.ROLE, RoleFixtures.validBytes());

        var set = RoleSetLoader.load(RoleFixtures.shippedValid().withMachine(machine).withProject(project));

        assertAll(
                () -> assertTrue(set.effective(RoleFixtures.ROLE).isPresent()),
                () -> assertTrue(set.refused().isEmpty(), () -> set.refused().toString()),
                () -> assertEquals(RoleSetKind.PROJECT, set.kind(RoleFixtures.ROLE).orElseThrow()));
    }

    @Nested
    @DisplayName("the shipped set is read from its documents")
    class Shipped {

        @Test
        @DisplayName("a shipped role is read as a role of the effective set")
        void shippedRoleIsEffective() {
            var set = RoleSetLoader.load(RoleFixtures.shipped(Map.of(RoleFixtures.ROLE, RoleFixtures.validBytes())));

            assertTrue(set.effective(RoleFixtures.ROLE).isPresent());
        }

        @Test
        @DisplayName("a shipped artifact that fails validation leaves its role undefined")
        void invalidShippedRoleIsUndefined() {
            var set = RoleSetLoader.load(RoleFixtures.shipped(
                    Map.of(RoleFixtures.ROLE, RoleFixtures.valid().set("/bogus", 1).bytes())));

            assertAll(
                    () -> assertTrue(set.effective(RoleFixtures.ROLE).isEmpty()),
                    () -> assertEquals(RoleSetKind.SHIPPED, set.refused().getFirst().kind()));
        }
    }

    private static void write(Path directory, String name, byte[] document) throws IOException {
        Files.write(directory.resolve(name + ".json"), document);
    }
}
