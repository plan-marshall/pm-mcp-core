/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link WaitRequest} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Wait request record builders")
class WaitRequestRecordsTest {

    @Test
    @DisplayName("WaitRequest builder builds the canonical constructor's value")
    void waitRequestBuilderEqualsConstructor() {
        var generationId = "l-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var generationDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var roleDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var reOfferBudget = Generators.integers(0, 10).next();
        var constructed = new WaitRequest(generationId, role, generationDigest, roleDigest, reOfferBudget);
        var built = WaitRequest.builder()
                .generationId(generationId)
                .role(role)
                .generationDigest(generationDigest)
                .roleDigest(roleDigest)
                .reOfferBudget(reOfferBudget)
                .build();

        assertEquals(constructed, built, "the builder builds the same wait request");
    }

    @Test
    @DisplayName("WaitRequest builder refuses a missing generation id")
    void waitRequestBuilderRefusesMissingGenerationId() {
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var generationDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var roleDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var builder = WaitRequest.builder()
                .role(role)
                .generationDigest(generationDigest)
                .roleDigest(roleDigest)
                .reOfferBudget(3);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("generationId", exception.getMessage(), "the builder names the missing generation");
    }
}
