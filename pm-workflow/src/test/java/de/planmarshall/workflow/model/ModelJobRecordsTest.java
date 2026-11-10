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
 * Proves that the builder of {@link ModelJob} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Model job record builders")
class ModelJobRecordsTest {

    @Test
    @DisplayName("ModelJob builder builds the canonical constructor's value")
    void modelJobBuilderEqualsConstructor() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var stateId = Generators.letterStrings(1, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var consultation = Generators.booleans().next();
        var acceptedClasses = Set.of(Generators.enumValues(RoleClass.class).next());
        var constructed = new ModelJob(scopeId, stateId, entityKey, role, consultation, acceptedClasses);
        var built = ModelJob.builder()
                .scopeId(scopeId)
                .stateId(stateId)
                .entityKey(entityKey)
                .role(role)
                .consultation(consultation)
                .acceptedClasses(acceptedClasses)
                .build();

        assertEquals(constructed, built, "the builder builds the same model job");
    }

    @Test
    @DisplayName("ModelJob builder refuses a missing state id")
    void modelJobBuilderRefusesMissingStateId() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var acceptedClasses = Set.of(Generators.enumValues(RoleClass.class).next());
        var exception = assertThrows(NullPointerException.class, () -> ModelJob.builder()
                .scopeId(scopeId)
                .entityKey(entityKey)
                .role(role)
                .consultation(false)
                .acceptedClasses(acceptedClasses)
                .build());

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }
}
