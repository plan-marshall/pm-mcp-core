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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shipped role set is read from {@code classpath:/roles/}, which the assembly supplies (PM-WORK-1, Role Sets): the
 * documents are the fixtures below, the reserved {@code default-assignment.json} is not a role, and the qualification
 * subdirectory is not read as a role set, and a file named by a reserved keyword of the workflow language refuses the
 * read. The test uses fixture roles only.
 */
// Every datum in this class is a contract: the classpath fixtures' names, class and budget are what the loader reads.
@DisplayName("The shipped role set")
class ShippedRolesTest {

    @Test
    @DisplayName("the fixture classpath supplies the shipped roles")
    void readsRoles() {
        var sets = ShippedRoles.read();

        assertAll(
                () -> assertTrue(sets.shipped().containsKey("interpret"), "interpret is among the shipped roles"),
                () -> assertTrue(sets.shipped().containsKey("steward"), "steward is among the shipped roles"),
                () -> assertEquals(2, sets.shipped().size(), "the fixture supplies two roles"));
    }

    @Test
    @DisplayName("the reserved default assignment is not read as a role")
    void skipsDefaultAssignment() {
        assertFalse(ShippedRoles.read().shipped().containsKey("default-assignment"),
                "the default assignment is not a role");
    }

    @Test
    @DisplayName("the shipped documents validate as role artifacts")
    void documentsValidate() {
        var set = RoleSetLoader.load(ShippedRoles.read());

        assertAll(
                () -> assertEquals(List.of(), set.refused(), "no shipped role document is refused"),
                () -> assertEquals(RoleClass.READ_ONLY, set.effective("interpret").orElseThrow().roleClass(),
                        "interpret is a read_only role"),
                () -> assertEquals(300, set.effective("steward").orElseThrow().budgetFloorSeconds(),
                        "steward keeps a five minute floor"));
    }

    // the keyword is the contract: a reserved word of the workflow language that matches the role-name grammar
    @Test
    @DisplayName("a shipped role file named by a reserved keyword refuses the read")
    void refusesAReservedKeyword(@TempDir Path classpath) throws Exception {
        var roles = Files.createDirectory(classpath.resolve("roles"));
        Files.write(roles.resolve("consult.json"), RoleFixtures.valid().set("/name", "consult").bytes());

        try (var loader = new URLClassLoader(new URL[]{classpath.toUri().toURL()}, null)) {
            var refusal = assertThrows(IllegalStateException.class, () -> ShippedRoles.read(loader),
                    "a keyword-named shipped role is refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains("consult.json"),
                            "the refusal names the shipped file"),
                    () -> assertTrue(refusal.getMessage().contains("reserved keyword"),
                            "the refusal says the name is a keyword"));
        }
    }
}
