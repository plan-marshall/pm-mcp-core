/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JsonlStoreFile;

/**
 * Proves that the builder of the private record {@code AuditVerification.State} builds the value of its canonical
 * constructor, which is reached reflectively because the record is not visible outside the verification read. The head
 * bytes are the same array instance in both values, because the record compares them by reference. Every text is
 * generated; the index and log snapshots hold the empty population.
 */
@EnableGeneratorController
@DisplayName("Audit verification state record builder")
class AuditVerificationRecordsTest {

    @Test
    @DisplayName("the builder builds the state record the constructor builds")
    void stateBuilderEqualsConstructor() throws Exception {
        var type = Class.forName("de.planmarshall.runtime.audit.AuditVerification$State");
        var head = Generators.letterStrings(1, 20).next().getBytes(StandardCharsets.UTF_8);
        var index = new AuditIndex.Snapshot(List.of(), Generators.letterStrings(64, 64).next(), OptionalLong.empty());
        var log = new JsonlStoreFile.Content(Path.of(Generators.letterStrings(1, 20).next()), List.of(),
                OptionalLong.empty());
        var logExists = Generators.booleans().next();
        Constructor<?> constructor = type.getDeclaredConstructor(byte[].class, AuditIndex.Snapshot.class,
                JsonlStoreFile.Content.class, boolean.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(head, index, log, logExists);
        var builderFactory = type.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "head", head);
        invokeSetter(builder, "index", index);
        invokeSetter(builder, "log", log);
        invokeSetter(builder, "logExists", logExists);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);

        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the same state record");
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
