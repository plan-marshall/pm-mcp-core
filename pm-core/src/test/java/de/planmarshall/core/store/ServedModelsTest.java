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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The three-valued {@code served_models} column (PM-WORK-7): the distinct models a row's turns answered with, in order
 * of first appearance, or {@code unmeasured} when no turn reported one — never an empty list and never a default.
 */
@EnableGeneratorController
@DisplayName("Served models")
class ServedModelsTest {

    private static final java.util.concurrent.atomic.AtomicInteger SEQUENCE =
            new java.util.concurrent.atomic.AtomicInteger();

    /** A fresh identifier per call: two draws never collide, so the distinctness test cannot flake. */
    private static String identifier() {
        return Generators.letterStrings(3, 12).next() + "-" + SEQUENCE.incrementAndGet();
    }

    @Test
    @DisplayName("unmeasured is a value of its own, not an empty list")
    void unmeasuredIsNotAnEmptyList() {
        var unmeasured = ServedModels.unmeasured();

        assertAll(
                () -> assertTrue(unmeasured.isUnmeasured(), "unmeasured reports itself"),
                () -> assertTrue(unmeasured.measured().isEmpty(), "unmeasured names no list"));
    }

    @Test
    @DisplayName("names the distinct identifiers in order of first appearance")
    void distinctInOrder() {
        var first = identifier();
        var second = identifier();
        var served = ServedModels.of(List.of(second, first, second, first));

        assertEquals(List.of(second, first), served.measured().orElseThrow(),
                "distinct identifiers stay in first-appearance order");
    }

    @Test
    @DisplayName("copies the list it is handed")
    void copiesTheList() {
        var models = new ArrayList<>(List.of(identifier()));
        var served = ServedModels.of(models);
        models.add(identifier());

        assertEquals(1, served.measured().orElseThrow().size(), "the value does not follow its source list");
    }

    @Test
    @DisplayName("refuses an empty list and an identifier outside 1 to 128 characters")
    void bounds() {
        var longest = Generators.letterStrings(128, 128).next();
        var tooLong = longest + longest.substring(0, 1);
        var noIdentifier = List.of("");
        var overLongIdentifier = List.of(tooLong);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ServedModels.of(List.of()),
                        "an empty list is not a value of the column"),
                () -> assertThrows(IllegalArgumentException.class, () -> ServedModels.of(noIdentifier),
                        "an empty identifier is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> ServedModels.of(overLongIdentifier),
                        "a 129-character identifier is refused"),
                () -> assertEquals(List.of(longest), ServedModels.of(List.of(longest)).measured().orElseThrow(),
                        "a 128-character identifier is accepted"));
    }
}
