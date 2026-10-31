/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;

import de.planmarshall.provider.http.PagedResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paged result generator")
@EnableGeneratorController
class PagedResultGeneratorTest {

    private final PagedResultGenerator generator = new PagedResultGenerator();

    @Test
    @DisplayName("Should yield valid and distinct paged results")
    void shouldYieldValidAndDistinctPagedResults() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated paged results",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertFalse(first.pages().isEmpty(), "The read carries at least one page"),
                () -> assertTrue(first.complete(), "The generated read is complete"),
                () -> assertNotNull(first.last(), "The read carries its last response"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(PagedResult.class, generator.getType(), "The generator names the paged type");
    }
}
