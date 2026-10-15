/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import de.planmarshall.runtime.test.TestBases;

/**
 * The periodic sweep under a runtime killed with {@code SIGKILL} in the middle of a run (PM-ARCH-7). The killed
 * runtime is a child JVM of {@link KilledRuntimeFixture}; the next run, in a new instance in this JVM, completes what
 * the interrupted run left, because every removal is judged on its own evidence.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Periodic sweep with a killed runtime")
class PeriodicSweepKilledRuntimeIT {

    private static final Duration AGED = PeriodicSweep.PENDING_ENTRY_MAX_AGE.plusHours(1);
    private static final Duration YOUNG = Duration.ofHours(1);

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private Path base;

    @BeforeEach
    void createBase() throws IOException {
        base = Files.createDirectory(TestBases.create("pmw"));
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(base);
        }
    }

    private void writePendingRecord(String name, Duration age) throws IOException {
        var file = Files.writeString(RuntimeChildProcess.pendingRecords(base).resolve(name), "{}");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
    }

    private List<String> records() throws IOException {
        try (var listing = Files.list(RuntimeChildProcess.pendingRecords(base))) {
            return listing.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("a run killed after one removal is completed by the next run; a young record is kept in both")
    void shouldCompleteInterruptedSweep() throws Exception {
        Files.createDirectory(RuntimeChildProcess.pendingRecords(base));
        writePendingRecord("a-aged.json", AGED);
        writePendingRecord("b-aged.json", AGED);
        writePendingRecord("c-young.json", YOUNG);
        fixture.start(RuntimeChildProcess.SWEEP, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.SWEEP));
        fixture.kill();
        var afterKill = records();

        var summary = new PeriodicSweep(() -> List.of(RecoveryStepFixtures.PROJECT_A), _ -> true,
                List.of(RuntimeChildProcess.removingStep(base, () -> {
                })), Clock.systemUTC()).runOnce(Instant.now());

        assertAll(
                () -> assertEquals(List.of("b-aged.json", "c-young.json"), afterKill, "one aged record removed"),
                () -> assertEquals(List.of("c-young.json"), records(), "the remaining aged record removed"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 0), summary));
    }
}
