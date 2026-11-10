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
 * Proves that the builder of {@link Violation} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Violation record builder")
class ViolationRecordsTest {

    @Test
    @DisplayName("Violation builder builds the canonical constructor's value")
    void violationBuilderEqualsConstructor() {
        var rule = VerificationRule.PRIMITIVE_BINDING;
        var stateId = Generators.letterStrings(1, 20).next();
        var location = WorkflowGenerators.sourceLocations().next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new Violation(rule, stateId, location, message);
        var built = Violation.builder()
                .rule(rule)
                .stateId(stateId)
                .location(location)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same violation");
    }

    @Test
    @DisplayName("Violation builder refuses a missing rule")
    void violationBuilderRefusesMissingRule() {
        var builder = Violation.builder()
                .stateId(Generators.letterStrings(1, 20).next())
                .location(WorkflowGenerators.sourceLocations().next())
                .message(Generators.letterStrings(1, 60).next());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("rule", exception.getMessage(), "the builder names the missing rule");
    }
}
