/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of the private record {@code JudgmentVerification.Probe} builds the value of its canonical
 * constructor, which is reached reflectively because the record is not visible outside its verifier, and that the
 * builder carries the constructor's null refusal for the body. Every value is generated.
 */
@EnableGeneratorController
@DisplayName("Judgment verification probe record builder")
class JudgmentVerificationRecordsTest {

    @Test
    @DisplayName("the builder builds the probe record the constructor builds")
    void probeBuilderEqualsConstructor() throws Exception {
        var type = Class.forName("de.planmarshall.runtime.endpoint.JudgmentVerification$Probe");
        var valid = false;
        var status = Generators.integers(100, 599).next();
        var body = Generators.letterStrings(1, 20).next();
        Constructor<?> constructor = type.getDeclaredConstructor(boolean.class, SystemOneAnswer.Answer.class,
                int.class, String.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(valid, null, status, body);
        var builderFactory = type.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "valid", valid);
        invokeSetter(builder, "status", status);
        invokeSetter(builder, "body", body);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);

        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same probe record");
    }

    @Test
    @DisplayName("the builder refuses a missing body like the constructor")
    void probeBuilderRefusesMissingBody() throws Exception {
        var type = Class.forName("de.planmarshall.runtime.endpoint.JudgmentVerification$Probe");
        var builderFactory = type.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "valid", false);
        invokeSetter(builder, "status", 200);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);

        var refusal = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> build.invoke(builder));

        assertEquals("body", refusal.getCause().getMessage(), "the refusal names the missing body");
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
