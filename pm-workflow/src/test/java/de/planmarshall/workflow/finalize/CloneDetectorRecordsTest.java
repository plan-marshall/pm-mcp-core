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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of the private record {@code CloneDetector.Occurrence} builds the value of its canonical
 * constructor, which is reached reflectively because the record is not visible outside its detector.
 */
@EnableGeneratorController
@DisplayName("Clone detector occurrence record builder")
class CloneDetectorRecordsTest {

    @Test
    @DisplayName("the builder builds the occurrence the constructor builds")
    void occurrenceBuilderEqualsConstructor() throws ReflectiveOperationException {
        var occurrenceClass = Class.forName("de.planmarshall.workflow.finalize.CloneDetector$Occurrence");
        var path = Generators.letterStrings(1, 20).next();
        var tokens = List.of();
        var start = Generators.integers(0, 100).next();
        var constructor = occurrenceClass.getDeclaredConstructor(String.class, List.class, int.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(path, tokens, start);
        var builderFactory = occurrenceClass.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "path", path);
        invokeSetter(builder, "tokens", tokens);
        invokeSetter(builder, "start", start);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);
        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same occurrence");
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
