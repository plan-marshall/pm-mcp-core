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

import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link ComposedUnit} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("ComposedUnit builder")
class ComposedUnitRecordsTest {

    @Test
    @DisplayName("the builder builds the composed unit the constructor builds")
    void composedUnitBuilderEqualsConstructor() {
        var definition = PlanFixtures.template();
        var composed = ComposedUnit.of(definition.id(), definition);

        var constructed = new ComposedUnit(composed.unit(), composed.version(), composed.definition(),
                composed.text(), composed.digest());
        var built = ComposedUnit.builder()
                .unit(composed.unit())
                .version(composed.version())
                .definition(composed.definition())
                .text(composed.text())
                .digest(composed.digest())
                .build();

        assertEquals(constructed, built, "the builder builds the same composed unit");
    }

    @Test
    @DisplayName("the builder refuses a missing unit id")
    void composedUnitBuilderRefusesMissingUnit() {
        var definition = PlanFixtures.template();
        var composed = ComposedUnit.of(definition.id(), definition);

        var exception = assertThrows(NullPointerException.class, () -> ComposedUnit.builder()
                .version(composed.version())
                .definition(composed.definition())
                .text(composed.text())
                .digest(composed.digest())
                .build());

        assertEquals("unit", exception.getMessage(), "the builder names the missing unit id");
    }
}
