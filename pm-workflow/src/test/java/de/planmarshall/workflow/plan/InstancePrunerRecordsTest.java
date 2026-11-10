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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.StateNode;

/**
 * Proves that the builder of {@link InstancePruner.Pruned} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("InstancePruner.Pruned builder")
class InstancePrunerRecordsTest {

    @Test
    @DisplayName("the builder builds the pruned value the constructor builds")
    void prunedBuilderEqualsConstructor() {
        var states = Map.<String, StateNode>of();
        var pruned = List.of(new PrunedElement(PrunedElement.Kind.STEP,
                "verify:module-tests", PruneReason.POSTURE, "posture"));
        var policyCalls = List.of(new PolicyCall("finalize.ci", "ci", new TreeMap<>()));
        var waivedGates = List.of(new WaivedGate("pre-merge", "W-1"));
        var steps = new PlanInstanceHeader.Steps(List.of(), List.of());
        var settled = Map.<String, Set<String>>of();

        var constructed = new InstancePruner.Pruned(states, pruned, policyCalls, waivedGates, steps, settled);
        var built = InstancePruner.Pruned.builder()
                .states(states)
                .pruned(pruned)
                .policyCalls(policyCalls)
                .waivedGates(waivedGates)
                .steps(steps)
                .settled(settled)
                .build();

        assertEquals(constructed, built, "the builder builds the same pruned value");
    }
}
