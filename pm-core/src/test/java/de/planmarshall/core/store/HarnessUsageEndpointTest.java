/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The usage of an endpoint answer (PM-CLIENT-2, Client Profiles § The Request): the token counts of the answer's usage
 * object are recorded per task, and a count the answer lacks is {@code unmeasured} — never {@code 0}.
 */
@EnableGeneratorController
@DisplayName("Endpoint usage")
class HarnessUsageEndpointTest {

    @Test
    @DisplayName("the prompt and completion counts are measured figures of the answer")
    void measuredCounts() {
        var prompt = Generators.longs(1, 1_000).next();
        var completion = Generators.longs(1, 1_000).next();
        var usage = HarnessUsage.fromEndpoint(OptionalLong.of(prompt), OptionalLong.of(completion));

        assertAll(
                () -> assertEquals(Measurement.measured(prompt), usage.inputTokens(),
                        "The answer keeps its prompt token count"),
                () -> assertEquals(Measurement.measured(completion), usage.outputTokens(),
                        "The answer keeps its completion token count"),
                () -> assertEquals(Measurement.measured(0), usage.toolUses(),
                        "An answer reports no tool uses"),
                () -> assertEquals(prompt + completion, usage.totalTokens().orElseThrow(),
                        "The total sums prompt and completion counts"),
                () -> assertTrue(usage.unmeasuredReason().isEmpty(),
                        "A fully measured answer has no reason"),
                () -> assertTrue(usage.costUsd().isEmpty(),
                        "An endpoint answer carries no cost figure"));
    }

    @Test
    @DisplayName("a count the answer lacks is unmeasured and leaves the total unmeasured")
    void missingCountIsUnmeasured() {
        var prompt = Generators.longs(1, 1_000).next();
        var usage = HarnessUsage.fromEndpoint(OptionalLong.of(prompt), OptionalLong.empty());

        assertAll(
                () -> assertEquals(Measurement.measured(prompt), usage.inputTokens(),
                        "The measured prompt count is kept"),
                () -> assertEquals(Measurement.unmeasured(), usage.outputTokens(),
                        "A missing completion count is unmeasured"),
                () -> assertTrue(usage.totalTokens().isEmpty(),
                        "A partial figure has no measured total"),
                () -> assertEquals(Optional.of(HarnessUsage.UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                        usage.unmeasuredReason(), "The reason names the harness omission"));
    }

    @Test
    @DisplayName("an answer without a usage object is unmeasured at both counts")
    void noUsageObject() {
        var usage = HarnessUsage.fromEndpoint(OptionalLong.empty(), OptionalLong.empty());

        assertAll(
                () -> assertEquals(Measurement.unmeasured(), usage.inputTokens(),
                        "Without a usage object no prompt count is known"),
                () -> assertEquals(Measurement.unmeasured(), usage.outputTokens(),
                        "Without a usage object no completion count is known"),
                () -> assertTrue(usage.unmeasuredReason().isPresent(),
                        "The absence carries an unmeasured reason"));
    }
}
