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

import java.time.Instant;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link Result} builds the value the canonical constructor builds, and it carries the constructor's
 * null refusal for the outcome. Every value is generated; the outcome names the required component the refusal test
 * exercises, which is contract vocabulary rather than test data.
 */
@EnableGeneratorController
@DisplayName("Result builder")
class ResultRecordsTest {

    @Test
    @DisplayName("the builder builds the result the constructor builds")
    void resultBuilderEqualsConstructor() {
        var outcome = Generators.enumValues(Result.Outcome.class).next();
        var value = Generators.letterStrings(1, 20).next();
        var complete = Generators.booleans().next();
        var resetAt = Instant.ofEpochSecond(Generators.longs(0L, 1_900_000_000L).next());
        var detail = Generators.letterStrings(1, 20).next();

        var built = Result.<String>builder()
                .outcome(outcome)
                .value(value)
                .complete(complete)
                .resetAt(resetAt)
                .detail(detail)
                .build();
        var constructed = new Result<>(outcome, value, complete, resetAt, detail);

        assertEquals(constructed, built, "the builder builds the same result value");
    }

    @Test
    @DisplayName("the builder refuses a missing outcome like the constructor")
    void resultBuilderRefusesMissingOutcome() {
        var refusal = assertThrows(NullPointerException.class,
                () -> Result.<String>builder().value("v").complete(true).detail("").build());

        assertEquals("outcome", refusal.getMessage(), "the refusal names the missing outcome");
    }
}
