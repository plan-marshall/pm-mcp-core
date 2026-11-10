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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link InvariantViolation} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Invariant violation record builder")
class InvariantViolationRecordsTest {

    @Test
    @DisplayName("InvariantViolation builder builds the canonical constructor's value")
    void invariantViolationBuilderEqualsConstructor() {
        var id = InvariantId.INV_GEN_1;
        var stateId = Generators.letterStrings(1, 20).next();
        var location = WorkflowGenerators.sourceLocations().next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new InvariantViolation(id, stateId, location, message);
        var built = InvariantViolation.builder()
                .id(id)
                .stateId(stateId)
                .location(location)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same invariant violation");
    }

    @Test
    @DisplayName("InvariantViolation builder refuses a missing id")
    void invariantViolationBuilderRefusesMissingId() {
        var exception = assertThrows(NullPointerException.class, () -> InvariantViolation.builder()
                .stateId(Generators.letterStrings(1, 20).next())
                .location(WorkflowGenerators.sourceLocations().next())
                .message(Generators.letterStrings(1, 60).next())
                .build());

        assertEquals("id", exception.getMessage(), "the builder names the missing id");
    }
}
