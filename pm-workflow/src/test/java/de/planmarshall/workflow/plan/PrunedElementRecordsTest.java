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
 * Proves that the builder of {@link PrunedElement} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PrunedElement builder")
class PrunedElementRecordsTest {

    @Test
    @DisplayName("the builder builds the pruned element the constructor builds")
    void prunedElementBuilderEqualsConstructor() {
        var element = PrunedElement.Kind.STATE;
        var id = Generators.letterStrings(1, 20).next();
        var reason = PruneReason.OTHER_LANE;
        var sourceFact = Generators.letterStrings(1, 20).next();

        var constructed = new PrunedElement(element, id, reason, sourceFact);
        var built = PrunedElement.builder()
                .element(element)
                .id(id)
                .reason(reason)
                .sourceFact(sourceFact)
                .build();

        assertEquals(constructed, built, "the builder builds the same pruned element");
    }

    @Test
    @DisplayName("the builder refuses a missing element kind")
    void prunedElementBuilderRefusesMissingElement() {
        var exception = assertThrows(NullPointerException.class, () -> PrunedElement.builder()
                .id(Generators.letterStrings(1, 20).next())
                .reason(PruneReason.POSTURE)
                .sourceFact("posture")
                .build());

        assertEquals("element", exception.getMessage(), "the builder names the missing element kind");
    }
}
