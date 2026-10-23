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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shipped role set is read from {@code classpath:/roles/}, which the assembly supplies (PM-WORK-1, Role Sets): the
 * documents are the fixtures below, the reserved {@code default-assignment.json} is not a role, and the qualification
 * subdirectory is not read as a role set. The test uses fixture roles only.
 */
@DisplayName("The shipped role set")
class ShippedRolesTest {

    @Test
    @DisplayName("the fixture classpath supplies the shipped roles")
    void readsRoles() {
        var sets = ShippedRoles.read();

        assertAll(
                () -> assertTrue(sets.shipped().containsKey("interpret")),
                () -> assertTrue(sets.shipped().containsKey("steward")),
                () -> assertEquals(2, sets.shipped().size()));
    }

    @Test
    @DisplayName("the reserved default assignment is not read as a role")
    void skipsDefaultAssignment() {
        assertFalse(ShippedRoles.read().shipped().containsKey("default-assignment"));
    }

    @Test
    @DisplayName("the shipped documents validate as role artifacts")
    void documentsValidate() {
        var set = RoleSetLoader.load(ShippedRoles.read());

        assertAll(
                () -> assertTrue(set.refused().isEmpty(), () -> set.refused().toString()),
                () -> assertEquals(RoleClass.READ_ONLY, set.effective("interpret").orElseThrow().roleClass()),
                () -> assertEquals(300, set.effective("steward").orElseThrow().budgetFloorSeconds()));
    }
}
