/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link ModelWait.Progress} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Model wait record builders")
class ModelWaitRecordsTest {

    @Test
    @DisplayName("Progress builder builds the canonical constructor's value")
    void progressBuilderEqualsConstructor() {
        var call = ModelCall.PM_BUILD;
        var startedAt = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var window = Duration.ofSeconds(60);
        var interval = Duration.ofSeconds(5);
        var constructed = new ModelWait.Progress(call, startedAt, window, interval);
        var built = ModelWait.Progress.builder()
                .call(call)
                .startedAt(startedAt)
                .window(window)
                .interval(interval)
                .build();

        assertEquals(constructed, built, "the builder builds the progress wait");
    }

    @Test
    @DisplayName("Progress builder refuses a missing call")
    void progressBuilderRefusesMissingCall() {
        var startedAt = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var window = Duration.ofSeconds(60);
        var interval = Duration.ofSeconds(5);
        var exception = assertThrows(NullPointerException.class, () -> ModelWait.Progress.builder()
                .startedAt(startedAt)
                .window(window)
                .interval(interval)
                .build());

        assertEquals("call", exception.getMessage(), "the builder names the missing call");
    }
}
