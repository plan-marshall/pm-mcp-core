/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.plan.UnitReference;

@DisplayName("Unit reference generator")
@EnableGeneratorController
class UnitReferenceGeneratorTest {

    private final UnitReferenceGenerator generator = new UnitReferenceGenerator();

    @Test
    @DisplayName("Should yield valid and distinct unit references")
    void shouldYieldValidAndDistinctUnitReferences() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated unit references",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertTrue(first.file().isPresent(), "The reference names the unit file"),
                () -> assertTrue(first.digest().matches("[0-9a-f]{64}"), "The digest is a lower-case SHA-256"),
                () -> assertEquals(UnitOrigin.PROJECT, first.origin(), "The unit is a project unit"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(UnitReference.class, generator.getType(), "The generator names the unit type");
    }
}
