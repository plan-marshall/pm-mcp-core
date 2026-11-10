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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link StateNode} builds the value its canonical constructor builds. Every value is
 * generated or a contract enum constant, and the behavior is a permitted record of the sealed interface.
 */
@EnableGeneratorController
@DisplayName("StateNode builder")
class StateNodeRecordsTest {

    @Test
    @DisplayName("the builder builds the state node the constructor builds")
    void stateNodeBuilderEqualsConstructor() {
        var id = Generators.letterStrings(1, 20).next();
        var terminalOutcome = new TerminalOutcome(Generators.letterStrings(1, 20).next(), OutcomeClass.SUCCESS);
        var behavior = new StateBehavior.TerminalState();
        var location = WorkflowGenerators.sourceLocations().next();

        var constructed = new StateNode(id, terminalOutcome, null, null, behavior, location);
        var built = StateNode.builder()
                .id(id)
                .terminalOutcome(terminalOutcome)
                .behavior(behavior)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same state node");
    }
}
