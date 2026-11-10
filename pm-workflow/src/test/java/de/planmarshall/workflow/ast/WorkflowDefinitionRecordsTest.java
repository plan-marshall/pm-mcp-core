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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link WorkflowDefinition} builds the value its canonical constructor builds. Every value
 * is generated or a contract enum constant; the nested records are built through their canonical constructors.
 */
@EnableGeneratorController
@DisplayName("WorkflowDefinition builder")
class WorkflowDefinitionRecordsTest {

    private static ParamDecl param() {
        return new ParamDecl(Generators.letterStrings(1, 20).next(),
                new ParamType.SchemaRef(Generators.letterStrings(1, 20).next()),
                WorkflowGenerators.sourceLocations().next());
    }

    @Test
    @DisplayName("the builder builds the workflow definition the constructor builds")
    void workflowDefinitionBuilderEqualsConstructor() {
        var location = WorkflowGenerators.sourceLocations().next();
        var modificationDate = LocalDate.ofEpochDay(Generators.longs(0, 20_000).next());
        var frontmatter = new UnitFrontmatter(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 40).next(), Generators.enumValues(UnitType.class).next(),
                Generators.letterStrings(1, 20).next(), List.of(), List.of(), modificationDate,
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 20).next(), null, location);
        var id = Generators.letterStrings(1, 20).next();
        var version = Generators.integers(1, 99).next();
        var scope = Generators.enumValues(WorkflowScope.class).next();
        var params = List.of(param());
        var resultSchema = Generators.letterStrings(1, 20).next();
        var declarations = Map.of(Generators.letterStrings(1, 20).next(),
                new ValueDeclaration(new CallValue.IntValue(Generators.integers(0, 100).next()), location));
        var decisions = Map.<String, DecisionDeclaration>of(Generators.letterStrings(1, 20).next(),
                new DecisionDeclaration.Ask(location));
        var formatVersion = Generators.integers(1, 5).next();
        var origin = Generators.enumValues(UnitOrigin.class).next();
        var derivedFrom = Map.of(Generators.letterStrings(1, 20).next(), Generators.integers(1, 9).next());
        var entryBindings = Map.of(Generators.letterStrings(1, 20).next(),
                new WorkflowDefinition.EntryBinding(Generators.letterStrings(1, 20).next(), location));
        var migrations = List.of(new StateMigration(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), location));
        var states = Map.of(Generators.letterStrings(1, 20).next(),
                new StateNode(Generators.letterStrings(1, 20).next(), null, null, null,
                        new StateBehavior.TerminalState(), location));

        var constructed = new WorkflowDefinition(frontmatter, id, version, scope, params, resultSchema, null,
                declarations, decisions, formatVersion, origin, derivedFrom, null, entryBindings, migrations, states,
                location);
        var built = WorkflowDefinition.builder()
                .frontmatter(frontmatter)
                .id(id)
                .version(version)
                .scope(scope)
                .params(params)
                .resultSchema(resultSchema)
                .declarations(declarations)
                .decisions(decisions)
                .formatVersion(formatVersion)
                .origin(origin)
                .derivedFrom(derivedFrom)
                .entryBindings(entryBindings)
                .migrations(migrations)
                .states(states)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same workflow definition");
    }
}
