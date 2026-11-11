/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * Proves that the builder of {@link OffTrackSignal} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Off-track signal record builders")
class OffTrackSignalRecordsTest {

    @Test
    @DisplayName("OffTrackSignal builder builds the canonical constructor's value")
    void offTrackSignalBuilderEqualsConstructor() {
        var signal = QueueTaskRecord.SignalName.REPEATING;
        var action = QueueTaskRecord.SignalAction.NUDGE;
        var measurement = Generators.integers(0, 100).next();
        var threshold = Generators.integers(101, 200).next();
        var constructed = new OffTrackSignal(signal, action, measurement, threshold);
        var built = OffTrackSignal.builder()
                .signal(signal)
                .action(action)
                .measurement(measurement)
                .threshold(threshold)
                .build();

        assertEquals(constructed, built, "the builder builds the same off-track signal");
    }

    @Test
    @DisplayName("OffTrackSignal builder refuses a missing signal")
    void offTrackSignalBuilderRefusesMissingSignal() {
        var action = QueueTaskRecord.SignalAction.NUDGE;
        var builder = OffTrackSignal.builder()
                .action(action)
                .measurement(1)
                .threshold(2);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("signal", exception.getMessage(), "the builder names the missing signal");
    }
}
