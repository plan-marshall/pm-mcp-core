/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;

@DisplayName("Endpoint profile generator")
@EnableGeneratorController
class EndpointProfileGeneratorTest {

    private final EndpointProfileGenerator generator = new EndpointProfileGenerator();

    @Test
    @DisplayName("Should yield valid and distinct endpoint profiles")
    void shouldYieldValidAndDistinctEndpointProfiles() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated endpoint profiles",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertEquals("openai_chat", first.endpoint().protocol(), "The protocol is the chat protocol"),
                () -> assertTrue(first.endpoint().baseUrl().startsWith("http://127.0.0.1:"),
                        "The base URL is a loopback endpoint"),
                () -> assertTrue(portInRange(first.endpoint().baseUrl()),
                        "The base URL carries a port in range"));
    }

    private static boolean portInRange(String baseUrl) {
        var port = URI.create(baseUrl).getPort();
        return port >= 1 && port <= 65_535;
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(EndpointProfile.class, generator.getType(), "The generator names the profile type");
    }
}
