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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
 * <p>
 * The names and ages of the records are generated; a name prefix fixes the order in which the step of the child
 * removes them, and the records are written in another order than that one, so the order the test pins is the one of
 * the names and not the one of the writes.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Periodic sweep with a killed runtime")
class PeriodicSweepKilledRuntimeIT {

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private Path base;

    @BeforeEach
    void createBase() throws IOException {
        // A fixture argument, the prefix of the base directory, not test data
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

    /** @return a generated record name that sorts by its prefix */
    private static String recordName(String prefix) {
        return prefix + "-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT) + ".json";
    }

    /** @return a generated age beyond the maximum age of a pending entry record */
    private static Duration aged() {
        return PeriodicSweep.PENDING_ENTRY_MAX_AGE.plusHours(Generators.integers(1, 48).next());
    }

    /** @return a generated age below the maximum age, by at least an hour, which no run of the test outlasts */
    private static Duration young() {
        var maxMinutes = PeriodicSweep.PENDING_ENTRY_MAX_AGE.minusHours(1).toMinutes();
        return Duration.ofMinutes(Generators.longs(1, maxMinutes).next());
    }

    private void writePendingRecord(String name, Duration age) throws IOException {
        // Contract literal: the smallest record, an empty JSON object; the sweep judges the age alone
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
        var firstAged = recordName("a");
        var youngRecord = recordName("b");
        var secondAged = recordName("c");
        Files.createDirectory(RuntimeChildProcess.pendingRecords(base));
        writePendingRecord(secondAged, aged());
        writePendingRecord(youngRecord, young());
        writePendingRecord(firstAged, aged());
        fixture.start(RuntimeChildProcess.SWEEP, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.SWEEP));
        fixture.kill();
        var afterKill = records();

        var summary = new PeriodicSweep(() -> List.of(RecoveryStepFixtures.PROJECT_A), _ -> true,
                List.of(RuntimeChildProcess.removingStep(base, () -> {
                })), Clock.systemUTC()).runOnce(Instant.now());

        assertAll(
                () -> assertEquals(List.of(youngRecord, secondAged), afterKill,
                        "the killed run removed the first aged record"),
                () -> assertEquals(List.of(youngRecord), records(),
                        "the next run removed the remaining aged record"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 0), summary,
                        "the next run swept the one project"));
    }
}
