/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of the private record {@code SubprocessJobRunner.Ended} builds the value of its canonical
 * constructor, which is reached reflectively because the record is not visible outside the runner. Every value is
 * generated; the run parameters ({@code exitValue}, {@code cancelBeforeGroup}) are plain generated values.
 */
@EnableGeneratorController
@DisplayName("Subprocess runner ended record builder")
class SubprocessJobRunnerRecordsTest {

    @Test
    @DisplayName("the builder builds the ended record the constructor builds")
    void endedBuilderEqualsConstructor() throws Exception {
        var type = Class.forName("de.planmarshall.runtime.job.SubprocessJobRunner$Ended");
        var spawnedAt = Instant.ofEpochSecond(Generators.longs(0L, 1_900_000_000L).next());
        var endedAt = Instant.ofEpochSecond(Generators.longs(0L, 1_900_000_000L).next());
        var exitValue = Generators.integers(0, 255).next();
        JobEvidence.Termination termination = Generators.enumValues(JobEvidence.Termination.class).next();
        var cancelBeforeGroup = Generators.booleans().next();
        Constructor<?> constructor = type.getDeclaredConstructor(Instant.class, Instant.class, int.class,
                JobEvidence.Termination.class, boolean.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(spawnedAt, endedAt, exitValue, termination, cancelBeforeGroup);
        var builderFactory = type.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "spawnedAt", spawnedAt);
        invokeSetter(builder, "endedAt", endedAt);
        invokeSetter(builder, "exitValue", exitValue);
        invokeSetter(builder, "termination", termination);
        invokeSetter(builder, "cancelBeforeGroup", cancelBeforeGroup);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);

        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same ended record");
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
