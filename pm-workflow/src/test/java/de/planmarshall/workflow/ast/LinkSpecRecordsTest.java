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

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link LinkSpec} builds the value its canonical constructor builds. Every value is
 * generated, and the guard and the action are real values of their sealed interfaces.
 */
@EnableGeneratorController
@DisplayName("LinkSpec builder")
class LinkSpecRecordsTest {

    @Test
    @DisplayName("the builder builds the link spec the constructor builds")
    void linkSpecBuilderEqualsConstructor() {
        var rel = Generators.enumValues(LinkRelation.class).next();
        var idPattern = Generators.letterStrings(1, 20).next();
        var form = Generators.enumValues(LinkForm.class).next();
        var freeParams = List.of(new ParamSpec(Generators.letterStrings(1, 20).next(),
                new ParamType.SchemaRef(Generators.letterStrings(1, 20).next()), Generators.booleans().next(),
                WorkflowGenerators.sourceLocations().next()));
        var guard = new GuardSpec(new GuardExpr.InWorktree(), List.of());
        var action = new LinkAction.ExecutePrimitive(Generators.letterStrings(1, 20).next());
        var transitions = List.of(new OutcomeTransition(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), null, List.of(),
                WorkflowGenerators.sourceLocations().next()));
        var location = WorkflowGenerators.sourceLocations().next();

        var constructed = new LinkSpec(rel, idPattern, form, freeParams, guard, action, transitions, location);
        var built = LinkSpec.builder()
                .rel(rel)
                .idPattern(idPattern)
                .form(form)
                .freeParams(freeParams)
                .guard(guard)
                .action(action)
                .transitions(transitions)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same link spec");
    }
}
