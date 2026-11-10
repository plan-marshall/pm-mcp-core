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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link CompositionCause} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("CompositionCause builder")
class CompositionCauseRecordsTest {

    @Test
    @DisplayName("the builder builds the composition cause the constructor builds")
    void compositionCauseBuilderEqualsConstructor() {
        var gate = Generators.letterStrings(1, 20).next();
        var fallbackCause = CompositionCause.FallbackCause.EXHAUSTED;
        var waiverId = Generators.letterStrings(1, 20).next();
        var deliverableId = Generators.letterStrings(1, 20).next();
        var replanId = Generators.letterStrings(1, 20).next();
        var fromConfigHash = Generators.letterStrings(1, 64).next();
        var toConfigHash = Generators.letterStrings(1, 64).next();
        var refusal = CompositionResult.Refusal.INVARIANT_FAILED;

        var constructed = new CompositionCause(gate, fallbackCause, waiverId, deliverableId, replanId, fromConfigHash,
                toConfigHash, refusal);
        var built = CompositionCause.builder()
                .gate(gate)
                .fallbackCause(fallbackCause)
                .waiverId(waiverId)
                .deliverableId(deliverableId)
                .replanId(replanId)
                .fromConfigHash(fromConfigHash)
                .toConfigHash(toConfigHash)
                .refusal(refusal)
                .build();

        assertEquals(constructed, built, "the builder builds the same composition cause");
    }
}
