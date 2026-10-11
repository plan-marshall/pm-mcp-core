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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;


import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The drain under a runtime killed with {@code SIGKILL} in the drain wait (PM-ARCH-7). The killed runtime is a child
 * JVM of {@link KilledRuntimeFixture}; the next start runs in this JVM on the same {@code PM_MCP_BASE} and cleans up
 * what the interrupted drain left, as after any crash.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Runtime drain with a killed runtime")
class RuntimeDrainKilledRuntimeIT {

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private MachinePaths paths;

    @BeforeEach
    void createBase() {
        paths = new MachinePaths(TestBases.create("pmk"), MachinePaths.Os.current());
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(paths.base());
        }
    }

    private StartupSequence.Outcome start() throws IOException {
        return new StartupSequence(paths, System.getProperty("user.name"), AuditChainVerification.NONE, _ -> {
        }).run(new SecureRandom());
    }

    @Test
    @DisplayName("a runtime killed in the drain wait leaves the lock free; the next start cleans up and keeps the leases")
    void shouldStartAfterKillInDrainWait() throws Exception {
        ((StartupSequence.Outcome.Ready) start()).lock().close();
        var seeded = ProviderLeaseStores.seed(paths.base());
        fixture.start(RuntimeChildProcess.DRAIN, paths.base());
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.DRAIN));
        var killedToken = Files.readString(paths.runtimeToken());
        var socketWhileDraining = Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS);

        fixture.kill();
        var next = start();

        var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, next);
        try (var _ = ready.lock()) {
            var token = Files.readString(paths.runtimeToken());
            assertAll(
                    () -> assertTrue(socketWhileDraining, "socket of the draining runtime"),
                    () -> assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS), "stale socket"),
                    () -> assertFalse(Files.exists(paths.runtimeRecord()), "stale runtime record"),
                    () -> assertEquals(43, token.length()),
                    () -> assertNotEquals(killedToken, token, "token of the killed runtime replaced"),
                    () -> ProviderLeaseStores.assertUnchanged(seeded));
        }
    }
}
