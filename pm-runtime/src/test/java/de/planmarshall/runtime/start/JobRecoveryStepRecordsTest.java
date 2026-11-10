/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of the private record {@code JobRecoveryStep.Handling} builds the value of its canonical
 * constructor, which is reached reflectively because the record is not visible outside its recovery step. Every value
 * is generated, and the rule is drawn from the enum that names the recovery rules.
 */
@EnableGeneratorController
@DisplayName("Job recovery handling record builder")
class JobRecoveryStepRecordsTest {

    @Test
    @DisplayName("the builder builds the handling record the constructor builds")
    void handlingBuilderEqualsConstructor() throws ReflectiveOperationException {
        var type = Class.forName("de.planmarshall.runtime.start.JobRecoveryStep$Handling");
        JobRecoveryStep.Rule rule = Generators.enumValues(JobRecoveryStep.Rule.class).next();
        var signal = Generators.letterStrings(1, 20).next();
        var terminated = Generators.booleans().next();
        var failure = Generators.letterStrings(1, 20).next();
        Constructor<?> constructor = type.getDeclaredConstructor(JobRecoveryStep.Rule.class, String.class,
                boolean.class, String.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(rule, signal, terminated, failure);
        var builderFactory = type.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "rule", rule);
        invokeSetter(builder, "signal", signal);
        invokeSetter(builder, "terminated", terminated);
        invokeSetter(builder, "failure", failure);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);

        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same handling record");
    }

    private static void invokeSetter(Object builder, String name, Object value) throws ReflectiveOperationException {
        var setter = java.util.Arrays.stream(builder.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
        setter.setAccessible(true);
        setter.invoke(builder, value);
    }
}
