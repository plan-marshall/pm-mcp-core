/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link SimplifyStep.ReviewTask} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Simplify step review task record builder")
class SimplifyStepRecordsTest {

    @Test
    @DisplayName("the builder builds the review task the constructor builds")
    void reviewTaskBuilderEqualsConstructor() {
        var role = Generators.letterStrings(1, 20).next();
        var skill = Generators.letterStrings(1, 40).next();
        var carriesWriteTool = Generators.booleans().next();
        var takesWriterSlot = Generators.booleans().next();
        var constructed = new SimplifyStep.ReviewTask(role, skill, carriesWriteTool, takesWriterSlot);
        var built = SimplifyStep.ReviewTask.builder()
                .role(role)
                .skill(skill)
                .carriesWriteTool(carriesWriteTool)
                .takesWriterSlot(takesWriterSlot)
                .build();

        assertEquals(constructed, built, "the builder builds the same review task");
    }

    @Test
    @DisplayName("the builder refuses a missing role like the constructor")
    void reviewTaskBuilderRefusesMissingRole() {
        var builder = SimplifyStep.ReviewTask.builder()
                .skill(Generators.letterStrings(1, 40).next());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("role", exception.getMessage(), "the builder names the missing role");
    }
}
