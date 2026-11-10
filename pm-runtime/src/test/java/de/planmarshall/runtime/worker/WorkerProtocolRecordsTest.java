/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link WorkerProtocol.OffTrackReport} builds the value its canonical constructor builds.
 * The report checks no component for null, so it has no refusal to mirror. Every value is generated; the signal and the
 * action name wire values, and the instant is a generated epoch second.
 */
@EnableGeneratorController
@DisplayName("WorkerProtocol.OffTrackReport builder")
class WorkerProtocolRecordsTest {

    @Test
    @DisplayName("the off-track report builder builds the report the constructor builds")
    void offTrackReportBuilderEqualsConstructor() {
        var generationId = Generators.letterStrings(1, 40).next();
        var queueTaskId = Generators.letterStrings(1, 40).next();
        var attempt = Generators.integers(0, 10).next();
        var signal = Generators.letterStrings(1, 20).next();
        var action = Generators.letterStrings(1, 20).next();
        var measurement = Generators.integers(0, 10_000).next() / 8.0;
        var threshold = Generators.integers(0, 10_000).next() / 8.0;
        var at = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());

        var constructed = new WorkerProtocol.OffTrackReport(generationId, queueTaskId, attempt, signal, action,
                measurement, threshold, at);
        var built = WorkerProtocol.OffTrackReport.builder()
                .generationId(generationId)
                .queueTaskId(queueTaskId)
                .attempt(attempt)
                .signal(signal)
                .action(action)
                .measurement(measurement)
                .threshold(threshold)
                .at(at)
                .build();

        assertEquals(constructed, built, "the builder builds the same off-track report");
    }
}
