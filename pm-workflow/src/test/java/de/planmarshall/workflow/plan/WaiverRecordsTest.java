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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link Waiver} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Waiver builder")
class WaiverRecordsTest {

    @Test
    @DisplayName("the builder builds the waiver the constructor builds")
    void waiverBuilderEqualsConstructor() {
        var id = Generators.letterStrings(1, 20).next();
        var gate = Generators.letterStrings(1, 20).next();
        var mode = Waiver.Mode.MET;

        var constructed = new Waiver(id, gate, mode, null);
        var built = Waiver.builder()
                .id(id)
                .gate(gate)
                .mode(mode)
                .build();

        assertEquals(constructed, built, "the builder builds the same waiver");
    }

    @Test
    @DisplayName("the builder refuses a missing waiver id")
    void waiverBuilderRefusesMissingId() {
        var exception = assertThrows(NullPointerException.class, () -> Waiver.builder()
                .gate(Generators.letterStrings(1, 20).next())
                .mode(Waiver.Mode.MET)
                .build());

        assertEquals("id", exception.getMessage(), "the builder names the missing waiver id");
    }
}
