/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link DeclarationBounds.Finding} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Declaration bounds finding record builder")
class DeclarationBoundsRecordsTest {

    @Test
    @DisplayName("Finding builder builds the canonical constructor's value")
    void findingBuilderEqualsConstructor() {
        var name = Generators.letterStrings(1, 20).next();
        var location = WorkflowGenerators.sourceLocations().next();
        var bound = Generators.letterStrings(1, 20).next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new DeclarationBounds.Finding(name, location, bound, message);
        var built = DeclarationBounds.Finding.builder()
                .name(name)
                .location(location)
                .bound(bound)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same declaration finding");
    }

    @Test
    @DisplayName("Finding builder refuses a missing name")
    void findingBuilderRefusesMissingName() {
        var exception = assertThrows(NullPointerException.class, () -> DeclarationBounds.Finding.builder()
                .location(WorkflowGenerators.sourceLocations().next())
                .bound(Generators.letterStrings(1, 20).next())
                .message(Generators.letterStrings(1, 60).next())
                .build());

        assertEquals("name", exception.getMessage(), "the builder names the missing name");
    }
}
