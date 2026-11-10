/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.RoleClass;

/**
 * Proves that the builder of {@link RoleUse} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Role use record builders")
class RoleUseRecordsTest {

    @Test
    @DisplayName("RoleUse builder builds the canonical constructor's value")
    void roleUseBuilderEqualsConstructor() {
        var stateId = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var acceptedClasses = Set.of(Generators.enumValues(RoleClass.class).next());
        var consultation = Generators.booleans().next();
        var constructed = new RoleUse(stateId, role, acceptedClasses, consultation);
        var built = RoleUse.builder()
                .stateId(stateId)
                .role(role)
                .acceptedClasses(acceptedClasses)
                .consultation(consultation)
                .build();

        assertEquals(constructed, built, "the builder builds the same role use");
    }

    @Test
    @DisplayName("RoleUse builder refuses a missing state id")
    void roleUseBuilderRefusesMissingStateId() {
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var acceptedClasses = Set.of(Generators.enumValues(RoleClass.class).next());
        var builder = RoleUse.builder()
                .role(role)
                .acceptedClasses(acceptedClasses)
                .consultation(false);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }
}
