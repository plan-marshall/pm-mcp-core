/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link EndpointClient.Response} builds the value its canonical constructor builds,
 * including its null refusal for the body. Every value is generated; the answer is an accepted one that names no
 * failure beyond {@code none}.
 */
@EnableGeneratorController
@DisplayName("EndpointClient.Response builder")
class EndpointClientRecordsTest {

    @Test
    @DisplayName("the builder builds the endpoint response the constructor builds")
    void responseBuilderEqualsConstructor() {
        var status = Generators.integers(200, 500).next();
        var body = Generators.letterStrings(1, 40).next();
        var failure = EndpointClient.Failure.NONE;
        var detail = Generators.letterStrings(1, 40).next();

        var constructed = new EndpointClient.Response(status, body, failure, detail);
        var built = EndpointClient.Response.builder()
                .status(status)
                .body(body)
                .failure(failure)
                .detail(detail)
                .build();

        assertEquals(constructed, built, "the builder builds the same endpoint response");
    }

    @Test
    @DisplayName("the builder refuses a missing body like the constructor")
    void responseBuilderRefusesMissingBody() {
        var refusal = assertThrows(NullPointerException.class, () -> EndpointClient.Response.builder().build());

        assertEquals("body", refusal.getMessage(), "the refusal names the response body");
    }
}
