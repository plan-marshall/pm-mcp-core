/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value-object contract of a complete line of a JSONL store (PM-IMPL-7): {@link JsonlStoreFile.Line} writes its
 * own {@code equals} and {@code hashCode} over its number and the bytes of its content. The contract checks equality,
 * hashing and the string form for every generated line.
 * <p>
 * The serializable contract of {@link ShouldHandleObjectContracts} is replaced by its deliberate opposite: a line is an
 * in-memory view of one line of the store and the store's persistence contract is its JSONL wire format, so the type
 * carries no serialized form. Making it {@link Serializable} would be a production change outside this plan.
 */
@EnableGeneratorController
class JsonlStoreFileContractTest implements ShouldHandleObjectContracts<JsonlStoreFile.Line> {

    @Override
    public JsonlStoreFile.Line getUnderTest() {
        return new JsonlStoreFile.Line(Generators.longs(1, Long.MAX_VALUE).next(),
                Generators.strings(0, 40).next().getBytes(UTF_8));
    }

    @Override
    @Test
    @DisplayName("a line stays an in-memory value and carries no serialized form")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(JsonlStoreFile.Line.class),
                "A store line is an in-memory value, not a serialized one");
    }
}
