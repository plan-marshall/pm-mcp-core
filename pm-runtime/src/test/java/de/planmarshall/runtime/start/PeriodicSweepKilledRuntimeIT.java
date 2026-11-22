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
import java.time.Clock;
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
 * The names of the records are generated; a name prefix fixes the order in which the step of the child removes them,
 * and the records are written in another order than that one, so the order the test pins is the one of the names and
 * not the one of the writes. A record with a {@code .keep} sibling stands for an open entry, which no run removes.
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

    private void writeResidue(String name) throws IOException {
        // Contract literal: the smallest record, an empty JSON object
        Files.writeString(RuntimeChildProcess.pendingRecords(base).resolve(name), "{}");
    }

    private void writeOpen(String name) throws IOException {
        writeResidue(name);
        Files.writeString(RuntimeChildProcess.pendingRecords(base).resolve(name + ".keep"), "");
    }

    private List<String> records() throws IOException {
        try (var listing = Files.list(RuntimeChildProcess.pendingRecords(base))) {
            return listing.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("a run killed after one removal is completed by the next run; an open record is kept in both")
    void shouldCompleteInterruptedSweep() throws Exception {
        var firstResidue = recordName("a");
        var openRecord = recordName("b");
        var secondResidue = recordName("c");
        Files.createDirectory(RuntimeChildProcess.pendingRecords(base));
        writeResidue(secondResidue);
        writeOpen(openRecord);
        writeResidue(firstResidue);
        fixture.start(RuntimeChildProcess.SWEEP, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.SWEEP));
        fixture.kill();
        var afterKill = records();

        var summary = new PeriodicSweep(() -> List.of(RecoveryStepFixtures.PROJECT_A), _ -> true,
                List.of(RuntimeChildProcess.removingStep(base, () -> {
                })), Clock.systemUTC()).runOnce(Instant.now());

        assertAll(
                () -> assertEquals(List.of(openRecord, openRecord + ".keep", secondResidue), afterKill,
                        "the killed run removed the first residue record only"),
                () -> assertEquals(List.of(openRecord, openRecord + ".keep"), records(),
                        "the next run removed the remaining residue record"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 0), summary,
                        "the next run swept the one project"));
    }
}
