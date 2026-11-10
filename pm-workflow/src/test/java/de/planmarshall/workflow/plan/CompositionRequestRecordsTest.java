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

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link CompositionRequest} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("CompositionRequest builder")
class CompositionRequestRecordsTest {

    @Test
    @DisplayName("the builder builds the composition request the constructor builds")
    void compositionRequestBuilderEqualsConstructor() {
        var planId = Generators.letterStrings(1, 20).next();
        var template = PlanFixtures.template();
        var facts = PlanFacts.builder().build();
        var configHash = Generators.letterStrings(1, 40).next();
        var position = InstancePosition.at(Generators.letterStrings(1, 20).next());

        var constructed = new CompositionRequest(planId, CompositionEvent.START, template, List.of(), List.of(), facts,
                configHash, null, null, List.of(), position, null, List.of(), List.of());
        var built = CompositionRequest.builder()
                .planId(planId)
                .event(CompositionEvent.START)
                .template(template)
                .templates(List.of())
                .subUnits(List.of())
                .facts(facts)
                .configHash(configHash)
                .waivers(List.of())
                .position(position)
                .verificationSteps(List.of())
                .finalizeSteps(List.of())
                .build();

        assertEquals(constructed, built, "the builder builds the same composition request");
    }

    @Test
    @DisplayName("the builder refuses a missing plan id")
    void compositionRequestBuilderRefusesMissingPlanId() {
        var exception = assertThrows(NullPointerException.class, () -> CompositionRequest.builder()
                .event(CompositionEvent.START)
                .template(PlanFixtures.template())
                .templates(List.of())
                .subUnits(List.of())
                .facts(PlanFacts.builder().build())
                .configHash(Generators.letterStrings(1, 40).next())
                .waivers(List.of())
                .position(InstancePosition.at(Generators.letterStrings(1, 20).next()))
                .verificationSteps(List.of())
                .finalizeSteps(List.of())
                .build());

        assertEquals("planId", exception.getMessage(), "the builder names the missing plan id");
    }
}
