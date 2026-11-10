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
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link PlanInstance} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PlanInstance builder")
class PlanInstanceRecordsTest {

    @Test
    @DisplayName("the builder builds the plan instance the constructor builds")
    void planInstanceBuilderEqualsConstructor() {
        var unit = PlanFixtures.template();
        var version = unit.version();
        var inputs = new CompositionInputs(Generators.letterStrings(1, 40).next(), List.of(), List.of(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 64).next(),
                Generators.letterStrings(1, 64).next(), null, null, null);
        var header = new PlanInstanceHeader(Generators.letterStrings(1, 20).next(), version, CompositionEvent.START,
                Instant.ofEpochMilli(Generators.longs(0, 2_000_000_000L).next()), inputs, null, null,
                new PlanInstanceHeader.Steps(List.of(), List.of()), List.of(), List.of(), List.of());
        var text = Generators.letterStrings(1, 40).next();
        var units = List.of(ComposedUnit.of(unit.id(), unit));

        var constructed = new PlanInstance(version, header, unit, text, units);
        var built = PlanInstance.builder()
                .version(version)
                .header(header)
                .unit(unit)
                .text(text)
                .units(units)
                .build();

        assertEquals(constructed, built, "the builder builds the same plan instance");
    }

    @Test
    @DisplayName("the builder refuses a missing instance header")
    void planInstanceBuilderRefusesMissingHeader() {
        var unit = PlanFixtures.template();

        var exception = assertThrows(NullPointerException.class, () -> PlanInstance.builder()
                .version(unit.version())
                .unit(unit)
                .text(Generators.letterStrings(1, 20).next())
                .units(List.of())
                .build());

        assertEquals("header", exception.getMessage(), "the builder names the missing header");
    }
}
