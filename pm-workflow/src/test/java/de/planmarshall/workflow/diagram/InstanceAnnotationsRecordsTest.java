/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builders of {@link InstanceAnnotations} and {@link InstanceAnnotations.Pruned} build the values their
 * canonical constructors build.
 */
@EnableGeneratorController
@DisplayName("Instance annotations record builders")
class InstanceAnnotationsRecordsTest {

    @Test
    @DisplayName("the builder builds the annotations the constructor builds")
    void instanceAnnotationsBuilderEqualsConstructor() {
        var planId = Generators.letterStrings(1, 20).next();
        var version = Generators.integers(1, 99).next();
        var event = Generators.letterStrings(1, 20).next();
        var lane = Generators.letterStrings(1, 12).next();
        var posture = Generators.letterStrings(1, 12).next();
        var activeState = Generators.letterStrings(1, 20).next();
        var pruned = List.of(new InstanceAnnotations.Pruned(Generators.letterStrings(1, 12).next(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 12).next(),
                Generators.letterStrings(1, 20).next()));
        var waivedGates = List.of(new InstanceAnnotations.Waived(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 12).next()));
        var composed = List.of(new InstanceAnnotations.Composed(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 40).next()));

        var constructed = new InstanceAnnotations(planId, version, event, lane, posture, activeState, pruned,
                waivedGates, composed);
        var built = InstanceAnnotations.builder()
                .planId(planId)
                .version(version)
                .event(event)
                .lane(lane)
                .posture(posture)
                .activeState(activeState)
                .pruned(pruned)
                .waivedGates(waivedGates)
                .composed(composed)
                .build();

        assertEquals(constructed, built, "the builder builds the same annotations");
    }

    @Test
    @DisplayName("the builder builds the pruned element the constructor builds")
    void prunedBuilderEqualsConstructor() {
        var element = Generators.letterStrings(1, 12).next();
        var id = Generators.letterStrings(1, 20).next();
        var reason = Generators.letterStrings(1, 12).next();
        var sourceFact = Generators.letterStrings(1, 20).next();
        var constructed = new InstanceAnnotations.Pruned(element, id, reason, sourceFact);
        var built = InstanceAnnotations.Pruned.builder()
                .element(element)
                .id(id)
                .reason(reason)
                .sourceFact(sourceFact)
                .build();

        assertEquals(constructed, built, "the builder builds the same pruned element");
    }

    @Test
    @DisplayName("the builder refuses a missing plan id like the constructor")
    void instanceAnnotationsBuilderRefusesMissingPlanId() {
        var builder = InstanceAnnotations.builder()
                .event(Generators.letterStrings(1, 20).next());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("planId", exception.getMessage(), "the builder names the missing plan id");
    }
}
