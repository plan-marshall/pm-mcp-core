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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;

import de.planmarshall.provider.http.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Response generator")
@EnableGeneratorController
class ResponseGeneratorTest {

    private final ResponseGenerator generator = new ResponseGenerator();

    @Test
    @DisplayName("Should yield valid and distinct responses")
    void shouldYieldValidAndDistinctResponses() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated responses",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertEquals(Response.Outcome.OK, first.outcome(), "The response is a success"),
                () -> assertEquals(200, first.status(), "The response carries the success status"),
                () -> assertTrue(first.etag().isPresent(), "The response carries an ETag"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(Response.class, generator.getType(), "The generator names the response type");
    }
}
