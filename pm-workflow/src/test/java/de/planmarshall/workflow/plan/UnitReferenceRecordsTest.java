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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link UnitReference} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("UnitReference builder")
class UnitReferenceRecordsTest {

    @Test
    @DisplayName("the builder builds the unit reference the constructor builds")
    void unitReferenceBuilderEqualsConstructor() {
        var reference = WorkflowGenerators.unitReferences().next();

        var constructed = new UnitReference(reference.unit(), reference.file(), reference.digest(),
                reference.origin());
        var built = UnitReference.builder()
                .unit(reference.unit())
                .file(reference.file())
                .digest(reference.digest())
                .origin(reference.origin())
                .build();

        assertEquals(constructed, built, "the builder builds the same unit reference");
    }

    @Test
    @DisplayName("the builder refuses a missing unit id")
    void unitReferenceBuilderRefusesMissingUnit() {
        var reference = WorkflowGenerators.unitReferences().next();

        var builder = UnitReference.builder()
                .file(reference.file())
                .digest(reference.digest())
                .origin(reference.origin());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("unit", exception.getMessage(), "the builder names the missing unit id");
    }
}
