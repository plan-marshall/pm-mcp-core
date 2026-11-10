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

import java.time.Instant;
import java.util.List;
import java.util.TreeMap;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link PlanInstanceHeader} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PlanInstanceHeader builder")
class PlanInstanceHeaderRecordsTest {

    @Test
    @DisplayName("the builder builds the instance header the constructor builds")
    void planInstanceHeaderBuilderEqualsConstructor() {
        var planId = Generators.letterStrings(1, 20).next();
        var version = Generators.integers(1, 20).next();
        var composedAt = Instant.ofEpochMilli(Generators.longs(0, 2_000_000_000L).next());
        var inputs = new CompositionInputs(Generators.letterStrings(1, 40).next(), List.of(), List.of(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 64).next(),
                Generators.letterStrings(1, 64).next(), null, null, null);
        var steps = new PlanInstanceHeader.Steps(List.of(), List.of());
        var lane = Generators.enumValues(PlanInstanceHeader.Lane.class).next();
        var posture = Generators.enumValues(PlanInstanceHeader.Posture.class).next();
        var pruned = List.of(PrunedElement.builder()
                .element(Generators.enumValues(PrunedElement.Kind.class).next())
                .id(Generators.letterStrings(1, 8).next())
                .reason(Generators.enumValues(PruneReason.class).next())
                .sourceFact(Generators.letterStrings(1, 8).next())
                .build());
        var policyCalls = List.of(new PolicyCall(Generators.letterStrings(1, 8).next(),
                Generators.letterStrings(1, 8).next(), new TreeMap<>()));
        var waivedGates = List.of(new WaivedGate(Generators.letterStrings(1, 8).next(),
                Generators.letterStrings(1, 8).next()));

        var constructed = new PlanInstanceHeader(planId, version, CompositionEvent.START, composedAt, inputs, lane,
                posture, steps, pruned, policyCalls, waivedGates);
        var built = PlanInstanceHeader.builder()
                .planId(planId)
                .version(version)
                .event(CompositionEvent.START)
                .composedAt(composedAt)
                .inputs(inputs)
                .lane(lane)
                .posture(posture)
                .steps(steps)
                .pruned(pruned)
                .policyCalls(policyCalls)
                .waivedGates(waivedGates)
                .build();

        assertEquals(constructed, built, "the builder builds the same instance header");
    }

    @Test
    @DisplayName("the builder refuses a missing plan id")
    void planInstanceHeaderBuilderRefusesMissingPlanId() {
        var inputs = new CompositionInputs(Generators.letterStrings(1, 40).next(), List.of(), List.of(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 64).next(),
                Generators.letterStrings(1, 64).next(), null, null, null);

        var builder = PlanInstanceHeader.builder()
                .version(1)
                .event(CompositionEvent.START)
                .composedAt(Instant.EPOCH)
                .inputs(inputs)
                .steps(new PlanInstanceHeader.Steps(List.of(), List.of()))
                .pruned(List.of())
                .policyCalls(List.of())
                .waivedGates(List.of());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("planId", exception.getMessage(), "the builder names the missing plan id");
    }
}
