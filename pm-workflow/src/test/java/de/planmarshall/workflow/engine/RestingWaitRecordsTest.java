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

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.AwaitedEvent;

/**
 * Proves that the builder of {@link RestingWait} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Resting wait record builders")
class RestingWaitRecordsTest {

    @Test
    @DisplayName("RestingWait builder builds the canonical constructor's value")
    void restingWaitBuilderEqualsConstructor() {
        var stateId = Generators.letterStrings(1, 8).next();
        var awaitedEvent = Generators.enumValues(AwaitedEvent.class).next();
        var jobIds = Set.of(Generators.letterStrings(1, 8).next());
        var taskEntities = Set.of(Generators.letterStrings(1, 8).next());
        var constructed = new RestingWait(stateId, awaitedEvent, jobIds, taskEntities);
        var built = RestingWait.builder()
                .stateId(stateId)
                .awaitedEvent(awaitedEvent)
                .jobIds(jobIds)
                .taskEntities(taskEntities)
                .build();

        assertEquals(constructed, built, "the builder builds the resting wait");
    }

    @Test
    @DisplayName("RestingWait builder refuses a missing state id")
    void restingWaitBuilderRefusesMissingStateId() {
        var awaitedEvent = Generators.enumValues(AwaitedEvent.class).next();
        var exception = assertThrows(NullPointerException.class, () -> RestingWait.builder()
                .awaitedEvent(awaitedEvent)
                .jobIds(Set.of())
                .taskEntities(Set.of())
                .build());

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }
}
