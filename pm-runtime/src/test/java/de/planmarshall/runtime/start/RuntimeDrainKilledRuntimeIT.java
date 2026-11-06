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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.test.TestBases;

/**
 * The drain under a runtime killed with {@code SIGKILL} in the drain wait (PM-ARCH-7). The killed runtime is a child
 * JVM of {@link KilledRuntimeFixture}; the next start runs in this JVM on the same {@code PM_MCP_BASE} and cleans up
 * what the interrupted drain left, as after any crash.
 * <p>
 * The mode and marker are the protocol with the child ({@link RuntimeChildProcess}), the token length is the specified
 * form of the token, and the provider leases come generated from {@link ProviderLeaseStores}.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Runtime drain with a killed runtime")
class RuntimeDrainKilledRuntimeIT {

    /** Contract literal: the length of a 256-bit token in unpadded base64url. */
    private static final int TOKEN_LENGTH = 43;

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private MachinePaths paths;

    @BeforeEach
    void createBase() {
        // The short prefix keeps the socket path below the limit of the OS; a fixture argument, not test data
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
        // The SecureRandom is the entropy source the startup needs to create its token, not test data
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

        var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, next,
                "the next start must reach ready after the kill");
        try (var _ = ready.lock()) {
            var token = Files.readString(paths.runtimeToken());
            assertAll(
                    () -> assertTrue(socketWhileDraining, "the draining runtime had its socket"),
                    () -> assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS),
                            "the next start removed the stale socket"),
                    () -> assertFalse(Files.exists(paths.runtimeRecord()),
                            "the next start removed the stale runtime record"),
                    () -> assertEquals(TOKEN_LENGTH, token.length(), "the next start must write a full token"),
                    () -> assertNotEquals(killedToken, token, "the token of the killed runtime is replaced"),
                    () -> ProviderLeaseStores.assertUnchanged(seeded));
        }
    }
}
