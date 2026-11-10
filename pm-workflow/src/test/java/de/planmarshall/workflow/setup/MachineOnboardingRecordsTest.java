/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link MachineOnboarding} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Machine onboarding record builder")
class MachineOnboardingRecordsTest {

    @Test
    @DisplayName("the builder builds the onboarding the constructor builds")
    void machineOnboardingBuilderEqualsConstructor() {
        var firstRunCompleted = true;
        var state = Generators.letterStrings(1, 20).next();
        var pendingSteps = List.of(Generators.letterStrings(1, 20).next());
        var completedSteps = Set.of(Generators.letterStrings(1, 20).next());
        var constructed = new MachineOnboarding(firstRunCompleted, state, pendingSteps, completedSteps);
        var built = MachineOnboarding.builder()
                .firstRunCompleted(firstRunCompleted)
                .state(state)
                .pendingSteps(pendingSteps)
                .completedSteps(completedSteps)
                .build();

        assertEquals(constructed, built, "the builder builds the same onboarding");
    }

    @Test
    @DisplayName("the builder refuses a missing state like the constructor")
    void machineOnboardingBuilderRefusesMissingState() {
        var builder = MachineOnboarding.builder()
                .firstRunCompleted(true);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("state", exception.getMessage(), "the builder names the missing state");
    }
}
