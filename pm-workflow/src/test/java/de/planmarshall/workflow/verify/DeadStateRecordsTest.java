/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link DeadState} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Dead state record builder")
class DeadStateRecordsTest {

    @Test
    @DisplayName("DeadState builder builds the canonical constructor's value")
    void deadStateBuilderEqualsConstructor() {
        var stateId = Generators.letterStrings(1, 20).next();
        var location = WorkflowGenerators.sourceLocations().next();
        var assignment = Map.of(Generators.letterStrings(1, 12).next(), Generators.booleans().next());
        var run = Generators.integers(1, 100).next();
        var constructed = new DeadState(stateId, location, assignment, run);
        var built = DeadState.builder()
                .stateId(stateId)
                .location(location)
                .assignment(assignment)
                .run(run)
                .build();

        assertEquals(constructed, built, "the builder builds the same dead state");
    }

    @Test
    @DisplayName("DeadState builder refuses a missing state id")
    void deadStateBuilderRefusesMissingStateId() {
        var location = WorkflowGenerators.sourceLocations().next();
        var assignment = Map.of(Generators.letterStrings(1, 12).next(), Generators.booleans().next());
        var exception = assertThrows(NullPointerException.class, () -> DeadState.builder()
                .location(location)
                .assignment(assignment)
                .run(Generators.integers(1, 100).next())
                .build());

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }
}
