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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value-object contract of the composer's provenance mark: {@link Verified} writes its own {@code equals} and
 * {@code hashCode} over its version and its text digest. The contract checks equality, hashing and the string form for
 * every generated mark.
 * <p>
 * The serializable contract of {@link ShouldHandleObjectContracts} is replaced by its deliberate opposite: a mark is an
 * in-memory token held only beside the version it names, so the type carries no serialized form.
 */
@EnableGeneratorController
@DisplayName("Verified")
class VerifiedContractTest implements ShouldHandleObjectContracts<Verified> {

    @Override
    public Verified getUnderTest() {
        return Verified.of(Generators.integers(1, 9).next(), Generators.letterStrings(64, 64).next());
    }

    @Override
    @Test
    @DisplayName("a mark stays an in-memory token and carries no serialized form")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(Verified.class),
                "A mark is an in-memory token, not serialized");
    }

    @Test
    @DisplayName("a mark below version one or without a digest is refused")
    void invalidMarkIsRefused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Verified.of(0, "a".repeat(64)),
                        "a version below one is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> Verified.of(1, " "),
                        "a blank digest is refused"));
    }

    @Test
    @DisplayName("two marks of one version and digest are equal and hash alike")
    void equalMarksAreEqual() {
        var first = Verified.of(2, "b".repeat(64));
        var second = Verified.of(2, "b".repeat(64));

        assertAll(
                () -> assertEquals(first, second, "equal marks should be equal"),
                () -> assertEquals(first.hashCode(), second.hashCode(), "equal marks should hash alike"),
                () -> assertFalse(first.equals(Verified.of(2, "c".repeat(64))),
                        "marks of another digest should differ"));
    }
}
