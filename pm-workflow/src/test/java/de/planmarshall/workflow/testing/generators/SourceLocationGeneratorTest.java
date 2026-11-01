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
import de.planmarshall.workflow.ast.SourceLocation;

@DisplayName("Source location generator")
@EnableGeneratorController
class SourceLocationGeneratorTest {

    private final SourceLocationGenerator generator = new SourceLocationGenerator();

    @Test
    @DisplayName("Should yield valid and distinct source locations")
    void shouldYieldValidAndDistinctSourceLocations() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated source locations",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertTrue(first.line() >= 1, "The line is at least one"),
                () -> assertTrue(first.column() >= 1, "The column is at least one"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(SourceLocation.class, generator.getType(), "The generator names the location type");
    }
}
