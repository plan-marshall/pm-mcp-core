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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The relay version-mismatch detection under a runtime killed with {@code SIGKILL} after it recorded a mismatch. The
 * killed runtime is a child JVM of {@link KilledRuntimeFixture}; the detection state belongs to the instance and is
 * not persisted, so a new instance in this JVM that observes the same relay version records it again and reports it.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Relay version-mismatch detection with a killed runtime")
class RelayVersionCheckKilledRuntimeIT {

    private static final String RECORD = RelayVersionCheck.RELAY_VERSION_MISMATCH + " "
            + RuntimeChildProcess.MISMATCHING_RELAY + " " + RuntimeChildProcess.CHILD_RUNTIME_VERSION;

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private Path base;

    @BeforeEach
    void createBase() throws IOException {
        base = Files.createDirectory(TestBases.create("pmv"));
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(base);
        }
    }

    private List<String> records() throws IOException {
        return Files.readAllLines(RuntimeChildProcess.versionRecords(base), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a new instance records the mismatch a killed runtime recorded again and reports the status data")
    void shouldRecordAgainInNewInstance() throws Exception {
        fixture.start(RuntimeChildProcess.VERSION, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.VERSION));
        fixture.kill();
        var afterKill = records();

        var check = new RelayVersionCheck(RuntimeChildProcess.CHILD_RUNTIME_VERSION,
                RuntimeChildProcess.recordingSink(base));
        check.observe(RuntimeChildProcess.MISMATCHING_RELAY);
        check.observe(RuntimeChildProcess.MISMATCHING_RELAY);

        assertAll(
                () -> assertEquals(List.of(RECORD), afterKill, "the killed runtime recorded once"),
                () -> assertEquals(List.of(RECORD, RECORD), records(), "the new instance recorded once again"),
                () -> assertEquals(Optional.of(new RelayVersionCheck.Mismatch(RuntimeChildProcess.MISMATCHING_RELAY,
                                RuntimeChildProcess.CHILD_RUNTIME_VERSION, RelayVersionCheck.RECOMMENDED_COMMAND)),
                        check.mismatch()));
    }
}
