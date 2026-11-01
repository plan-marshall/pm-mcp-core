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

import de.planmarshall.provider.http.Endpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Endpoint generator")
@EnableGeneratorController
class EndpointGeneratorTest {

    private final EndpointGenerator generator = new EndpointGenerator();

    @Test
    @DisplayName("Should yield valid and distinct endpoints")
    void shouldYieldValidAndDistinctEndpoints() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated endpoints",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertEquals("https", first.baseUri().getScheme(), "The endpoint speaks https"),
                () -> assertTrue(first.baseUri().getPath().endsWith("/"), "The API base ends with a slash"),
                () -> assertFalse(first.redirectHosts().isEmpty(), "The endpoint names a redirect host"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(Endpoint.class, generator.getType(), "The generator names the endpoint type");
    }
}
