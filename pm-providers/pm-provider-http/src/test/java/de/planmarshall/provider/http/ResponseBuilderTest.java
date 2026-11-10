/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link Response} builds the value the canonical constructor builds, and it carries the constructor's
 * null refusal for the outcome. Every value is generated; the outcome names the required component the refusal test
 * exercises, which is contract vocabulary rather than test data.
 */
@EnableGeneratorController
@DisplayName("Response builder")
class ResponseBuilderTest {

    @Test
    @DisplayName("the builder builds the response the constructor builds")
    void builderEqualsConstructor() {
        var outcome = Generators.enumValues(Response.Outcome.class).next();
        var status = Generators.integers(0, 599).next();
        var body = Generators.letterStrings(1, 20).next();
        var etag = Generators.letterStrings(1, 20).next();
        var next = URI.create("https://" + Generators.letterStrings(3, 10).next() + ".example");
        var resetAt = Instant.ofEpochSecond(Generators.longs(0L, 1_900_000_000L).next());
        var detail = Generators.letterStrings(1, 20).next();

        var built = Response.builder()
                .outcome(outcome)
                .status(status)
                .body(body)
                .etag(etag)
                .next(next)
                .resetAt(resetAt)
                .detail(detail)
                .build();
        var constructed = new Response(outcome, status, body, etag, next, resetAt, detail);

        assertEquals(constructed, built, "the builder builds the same response value");
    }

    @Test
    @DisplayName("the builder refuses a missing outcome like the constructor")
    void builderRefusesMissingOutcome() {
        var refusal = assertThrows(NullPointerException.class,
                () -> Response.builder().status(200).body("").detail("").build());

        assertEquals("outcome", refusal.getMessage(), "the refusal names the missing outcome");
    }
}
