/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link PlanProcessStates.NodeState} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PlanProcessStates.NodeState builder")
class PlanProcessStatesRecordsTest {

    @Test
    @DisplayName("the builder builds the node state the constructor builds")
    void nodeStateBuilderEqualsConstructor() {
        var node = Generators.letterStrings(1, 20).next();
        var state = PlanProcessStates.State.DONE;
        var outcome = Generators.letterStrings(1, 20).next();
        var reason = Generators.letterStrings(1, 20).next();

        var constructed = new PlanProcessStates.NodeState(node, state, outcome, reason);
        var built = PlanProcessStates.NodeState.builder()
                .node(node)
                .state(state)
                .outcome(outcome)
                .reason(reason)
                .build();

        assertEquals(constructed, built, "the builder builds the same node state");
    }

    @Test
    @DisplayName("the builder refuses a missing node")
    void nodeStateBuilderRefusesMissingNode() {
        var exception = assertThrows(NullPointerException.class, () -> PlanProcessStates.NodeState.builder()
                .state(PlanProcessStates.State.DONE)
                .build());

        assertEquals("node", exception.getMessage(), "the builder names the missing node");
    }
}
