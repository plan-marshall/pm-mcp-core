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

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The usage of an endpoint answer (PM-CLIENT-2, Client Profiles § The Request): the token counts of the answer's usage
 * object are recorded per task, and a count the answer lacks is {@code unmeasured} — never {@code 0}.
 */
@DisplayName("Endpoint usage")
class HarnessUsageEndpointTest {

    @Test
    @DisplayName("the prompt and completion counts are measured figures of the answer")
    void measuredCounts() {
        var usage = HarnessUsage.fromEndpoint(OptionalLong.of(10), OptionalLong.of(5));

        assertAll(
                () -> assertEquals(Measurement.measured(10), usage.inputTokens()),
                () -> assertEquals(Measurement.measured(5), usage.outputTokens()),
                () -> assertEquals(Measurement.measured(0), usage.toolUses()),
                () -> assertEquals(15, usage.totalTokens().orElseThrow()),
                () -> assertTrue(usage.unmeasuredReason().isEmpty()),
                () -> assertTrue(usage.costUsd().isEmpty()));
    }

    @Test
    @DisplayName("a count the answer lacks is unmeasured and leaves the total unmeasured")
    void missingCountIsUnmeasured() {
        var usage = HarnessUsage.fromEndpoint(OptionalLong.of(10), OptionalLong.empty());

        assertAll(
                () -> assertEquals(Measurement.measured(10), usage.inputTokens()),
                () -> assertEquals(Measurement.unmeasured(), usage.outputTokens()),
                () -> assertTrue(usage.totalTokens().isEmpty()),
                () -> assertEquals(Optional.of(HarnessUsage.UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                        usage.unmeasuredReason()));
    }

    @Test
    @DisplayName("an answer without a usage object is unmeasured at both counts")
    void noUsageObject() {
        var usage = HarnessUsage.fromEndpoint(OptionalLong.empty(), OptionalLong.empty());

        assertAll(
                () -> assertEquals(Measurement.unmeasured(), usage.inputTokens()),
                () -> assertEquals(Measurement.unmeasured(), usage.outputTokens()),
                () -> assertTrue(usage.unmeasuredReason().isPresent()));
    }
}
