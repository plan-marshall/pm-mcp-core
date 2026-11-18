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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link PlanInstanceComposer.Composed} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PlanInstanceComposer.Composed builder")
class PlanInstanceComposerRecordsTest {

    @Test
    @DisplayName("the builder builds the composed value the constructor builds")
    void composedBuilderEqualsConstructor() throws Exception {
        var composedClass = Class.forName("de.planmarshall.workflow.plan.PlanInstanceComposer$Composed");
        var units = List.of();
        var positionStates = Map.of(Generators.letterStrings(1, 8).next(), Set.of(Generators.letterStrings(1, 8)
                .next()));
        var policyCalls = List.of(PolicyCall.of(Generators.letterStrings(1, 8).next(),
                "remediation.qgate-findings", Map.of()));
        var subUnits = Set.of(Generators.letterStrings(1, 8).next());
        var pruned = List.of(PrunedElement.builder()
                .element(Generators.enumValues(PrunedElement.Kind.class).next())
                .id(Generators.letterStrings(1, 8).next())
                .reason(Generators.enumValues(PruneReason.class).next())
                .sourceFact(Generators.letterStrings(1, 8).next())
                .build());
        var unresolved = Set.of(Generators.letterStrings(1, 8).next());
        var components = composedClass.getRecordComponents();
        var constructor = composedClass.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
        constructor.setAccessible(true);
        var values = Map.of("units", units, "positionStates", positionStates, "policyCalls", policyCalls,
                "subUnits", subUnits, "pruned", pruned, "unresolved", unresolved);
        var constructed = constructor.newInstance(
                Arrays.stream(components).map(component -> values.get(component.getName())).toArray());
        var builderFactory = composedClass.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "units", units);
        invokeSetter(builder, "positionStates", positionStates);
        invokeSetter(builder, "policyCalls", policyCalls);
        invokeSetter(builder, "subUnits", subUnits);
        invokeSetter(builder, "pruned", pruned);
        invokeSetter(builder, "unresolved", unresolved);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);
        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same composed value");
    }

    private static void invokeSetter(Object builder, String name, Object value) throws ReflectiveOperationException {
        var setter = Arrays.stream(builder.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
        setter.setAccessible(true);
        setter.invoke(builder, value);
    }
}
