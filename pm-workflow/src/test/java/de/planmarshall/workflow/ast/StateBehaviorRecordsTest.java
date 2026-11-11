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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builders of {@link StateBehavior.ModelJob} and {@link StateBehavior.SubWorkflowCall} build the values
 * their canonical constructors build, including the model job's null refusal for the asking form. Every value is
 * generated, and the records under test are permitted variants of the sealed interface.
 */
@EnableGeneratorController
@DisplayName("StateBehavior builders")
class StateBehaviorRecordsTest {

    private static OutcomeTransition transition() {
        return new OutcomeTransition(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(),
                new ResultGuard(Generators.letterStrings(1, 20).next(), Generators.booleans().next()),
                List.of(), WorkflowGenerators.sourceLocations().next());
    }

    @Test
    @DisplayName("the builder builds the model job the constructor builds")
    void modelJobBuilderEqualsConstructor() {
        var entityKind = Generators.letterStrings(1, 20).next();
        var roleName = Generators.letterStrings(1, 20).next();
        var fanOutPrimitiveId = Generators.letterStrings(1, 20).next();
        var rolesPrimitiveId = Generators.letterStrings(1, 20).next();
        var askForm = Generators.enumValues(StateBehavior.AskForm.class).next();
        var consultRoles = List.of(Generators.letterStrings(1, 20).next());
        var transitions = List.of(transition());

        var constructed = new StateBehavior.ModelJob(entityKind, roleName, fanOutPrimitiveId, rolesPrimitiveId,
                askForm, consultRoles, transitions);
        var built = StateBehavior.ModelJob.builder()
                .entityKind(entityKind)
                .roleName(roleName)
                .fanOutPrimitiveId(fanOutPrimitiveId)
                .rolesPrimitiveId(rolesPrimitiveId)
                .askForm(askForm)
                .consultRoles(consultRoles)
                .transitions(transitions)
                .build();

        assertEquals(constructed, built, "the builder builds the same model job");
    }

    @Test
    @DisplayName("the builder builds the sub-workflow call the constructor builds")
    void subWorkflowCallBuilderEqualsConstructor() {
        var subWorkflowId = Generators.letterStrings(1, 20).next();
        var entry = Generators.letterStrings(1, 20).next();
        var paramBindings = Map.<String, CallValue>of(Generators.letterStrings(1, 20).next(),
                new CallValue.EnumValue(Generators.letterStrings(1, 20).next()));
        var transitions = List.of(transition());

        var constructed = new StateBehavior.SubWorkflowCall(subWorkflowId, entry, paramBindings, transitions);
        var built = StateBehavior.SubWorkflowCall.builder()
                .subWorkflowId(subWorkflowId)
                .entry(entry)
                .paramBindings(paramBindings)
                .transitions(transitions)
                .build();

        assertEquals(constructed, built, "the builder builds the same sub-workflow call");
    }

    @Test
    @DisplayName("the builder refuses a missing asking form like the constructor")
    void modelJobBuilderRefusesMissingAskForm() {
        var builder = StateBehavior.ModelJob.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("askForm", refusal.getMessage(), "the refusal names the asking form");
    }
}
