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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The per-project recovery under a runtime killed with {@code SIGKILL} inside a recovery step (PM-ARCH-7). The killed
 * runtime is a child JVM of {@link KilledRuntimeFixture}; a new runtime instance in this JVM recovers the project
 * again from the first step: a project is recovered once per runtime instance, not once ever.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Per-project recovery with a killed runtime")
class ProjectRecoveryKilledRuntimeIT {

    private static final String PROJECT = RuntimeChildProcess.RECOVERED_PROJECT;

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private Path base;

    @BeforeEach
    void createBase() throws IOException {
        // The recovery runs after the startup sequence has created the base; this test needs no more of it
        base = Files.createDirectory(TestBases.create("pmr"));
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(base);
        }
    }

    @Test
    @DisplayName("a runtime killed inside a recovery step; the next instance recovers the project again from the start")
    void shouldRecoverAgainAfterKillInsideStep() throws Exception {
        fixture.start(RuntimeChildProcess.RECOVERY, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.RECOVERY));
        var runsOfKilled = Files.readAllLines(RuntimeChildProcess.recoveryRuns(base));
        fixture.kill();

        var steps = RecoveryStepFixtures.completeSteps(RuntimeChildProcess.markingStep(base));
        var next = new ProjectRecovery(() -> List.of(PROJECT), steps, Runnable::run);
        next.startBackgroundPass();
        var result = next.awaitRecovered(PROJECT);
        var repeated = next.awaitRecovered(PROJECT);

        var outcome = assertInstanceOf(ProjectRecovery.Result.Recovered.class, result).outcome();
        assertAll(
                () -> assertEquals(List.of(PROJECT), runsOfKilled, "the killed runtime ran the first step"),
                () -> assertFalse(outcome.failed()),
                () -> assertEquals(RecoveryStep.Name.values().length, outcome.steps().size()),
                () -> assertEquals(result, repeated),
                () -> assertEquals(List.of(PROJECT, PROJECT), Files.readAllLines(RuntimeChildProcess.recoveryRuns(base)),
                        "recovered again from the first step, and once per instance"));
    }
}
