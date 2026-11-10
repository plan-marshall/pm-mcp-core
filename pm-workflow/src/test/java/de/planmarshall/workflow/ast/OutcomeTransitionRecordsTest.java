/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link OutcomeTransition} builds the value its canonical constructor builds. Every value
 * is generated or a contract enum constant, and the result guard and the completion are real nested values.
 */
@EnableGeneratorController
@DisplayName("OutcomeTransition builder")
class OutcomeTransitionRecordsTest {

    @Test
    @DisplayName("the builder builds the outcome transition the constructor builds")
    void outcomeTransitionBuilderEqualsConstructor() {
        var outcomeName = Generators.letterStrings(1, 20).next();
        var targetStateId = Generators.letterStrings(1, 20).next();
        var resultGuard = new ResultGuard(Generators.letterStrings(1, 20).next(), Generators.booleans().next());
        var location = WorkflowGenerators.sourceLocations().next();
        var completions = List.of(new StepCompletion(Generators.letterStrings(1, 20).next(),
                Generators.enumValues(StepCompletionStatus.class).next(), location));

        var constructed = new OutcomeTransition(outcomeName, targetStateId, resultGuard, completions, location);
        var built = OutcomeTransition.builder()
                .outcomeName(outcomeName)
                .targetStateId(targetStateId)
                .resultGuard(resultGuard)
                .completions(completions)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same outcome transition");
    }
}
