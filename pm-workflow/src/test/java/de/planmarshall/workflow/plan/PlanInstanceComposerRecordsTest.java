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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link PlanInstanceComposer.Composed} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PlanInstanceComposer.Composed builder")
class PlanInstanceComposerRecordsTest {

    @Test
    @DisplayName("the builder builds the composed value the constructor builds")
    void composedBuilderEqualsConstructor() throws ReflectiveOperationException {
        var composedClass = Class.forName("de.planmarshall.workflow.plan.PlanInstanceComposer$Composed");
        var units = List.of();
        var positionStates = Set.of();
        var subUnits = Set.of();
        var pruned = List.of();
        var unresolved = Set.of();
        var constructor = composedClass.getDeclaredConstructor(List.class, Set.class, Set.class, List.class,
                Set.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(units, positionStates, subUnits, pruned, unresolved);
        var builderFactory = composedClass.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "units", units);
        invokeSetter(builder, "positionStates", positionStates);
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
