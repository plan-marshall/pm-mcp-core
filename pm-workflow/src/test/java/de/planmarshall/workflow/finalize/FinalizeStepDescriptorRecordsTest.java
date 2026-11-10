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

import java.util.List;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builders of {@link FinalizeStepDescriptor} and {@link FinalizeStepDescriptor.Lane} build the values
 * their canonical constructors build.
 */
@EnableGeneratorController
@DisplayName("Finalize step descriptor record builders")
class FinalizeStepDescriptorRecordsTest {

    @Test
    @DisplayName("the builder builds the step descriptor the constructor builds")
    void finalizeStepDescriptorBuilderEqualsConstructor() {
        var name = Generators.letterStrings(1, 20).next();
        var order = Generators.integers(1, 1_000).next();
        var defaultOn = Generators.booleans().next();
        var presets = Set.of(Generators.letterStrings(1, 12).next());
        var description = Generators.letterStrings(1, 40).next();
        var mutatesSource = Generators.booleans().next();
        var headDependent = Generators.booleans().next();
        var inputScope = Generators.enumValues(FinalizeStepDescriptor.InputScope.class).next();
        var verdictInputs = List.of(Generators.letterStrings(1, 12).next());
        var advancesMainViaRebase = Generators.booleans().next();
        var postRunReview = Generators.booleans().next();
        var reads = List.of(Generators.letterStrings(1, 12).next());
        var destroys = List.of(Generators.letterStrings(1, 12).next());
        var recordsFacts = List.of(Generators.letterStrings(1, 12).next());
        var lane = new FinalizeStepDescriptor.Lane(
                Generators.enumValues(FinalizeStepDescriptor.LaneClass.class).next(),
                Generators.letterStrings(1, 12).next(), null, Generators.letterStrings(1, 4).next());
        var requires = List.of(Generators.letterStrings(1, 12).next());
        var configurable = List.of(new FinalizeStepDescriptor.ConfigurableKey(
                Generators.letterStrings(1, 12).next(), Generators.letterStrings(1, 12).next(),
                Generators.letterStrings(1, 20).next()));
        var persona = Generators.letterStrings(1, 12).next();
        var requiresPromptFields = List.of(Generators.letterStrings(1, 12).next());
        var cannotReach = Generators.letterStrings(1, 12).next();
        var filesFindings = List.of(Generators.letterStrings(1, 12).next());

        var constructed = new FinalizeStepDescriptor(name, order, defaultOn, presets, description, mutatesSource,
                headDependent, inputScope, verdictInputs, advancesMainViaRebase, postRunReview, reads, destroys,
                recordsFacts, lane, requires, configurable, persona, requiresPromptFields, cannotReach, filesFindings);
        var built = FinalizeStepDescriptor.builder()
                .name(name)
                .order(order)
                .defaultOn(defaultOn)
                .presets(presets)
                .description(description)
                .mutatesSource(mutatesSource)
                .headDependent(headDependent)
                .inputScope(inputScope)
                .verdictInputs(verdictInputs)
                .advancesMainViaRebase(advancesMainViaRebase)
                .postRunReview(postRunReview)
                .reads(reads)
                .destroys(destroys)
                .recordsFacts(recordsFacts)
                .lane(lane)
                .requires(requires)
                .configurable(configurable)
                .persona(persona)
                .requiresPromptFields(requiresPromptFields)
                .cannotReach(cannotReach)
                .filesFindings(filesFindings)
                .build();

        assertEquals(constructed, built, "the builder builds the same step descriptor");
    }

    @Test
    @DisplayName("the builder builds the lane the constructor builds")
    void laneBuilderEqualsConstructor() {
        var laneClass = Generators.enumValues(FinalizeStepDescriptor.LaneClass.class).next();
        var tier = Generators.letterStrings(1, 12).next();
        var prunableWhen = Generators.letterStrings(1, 12).next();
        var costSize = Generators.letterStrings(1, 4).next();
        var constructed = new FinalizeStepDescriptor.Lane(laneClass, tier, prunableWhen, costSize);
        var built = FinalizeStepDescriptor.Lane.builder()
                .laneClass(laneClass)
                .tier(tier)
                .prunableWhen(prunableWhen)
                .costSize(costSize)
                .build();

        assertEquals(constructed, built, "the builder builds the same lane");
    }

    @Test
    @DisplayName("the builder refuses a missing name like the constructor")
    void finalizeStepDescriptorBuilderRefusesMissingName() {
        var exception = assertThrows(NullPointerException.class, () -> FinalizeStepDescriptor.builder()
                .description(Generators.letterStrings(1, 40).next())
                .build());

        assertEquals("name", exception.getMessage(), "the builder names the missing name");
    }

    @Test
    @DisplayName("the builder refuses a missing lane class like the constructor")
    void laneBuilderRefusesMissingLaneClass() {
        var exception = assertThrows(NullPointerException.class, () -> FinalizeStepDescriptor.Lane.builder()
                .costSize(Generators.letterStrings(1, 4).next())
                .build());

        assertEquals("laneClass", exception.getMessage(), "the builder names the missing lane class");
    }
}
