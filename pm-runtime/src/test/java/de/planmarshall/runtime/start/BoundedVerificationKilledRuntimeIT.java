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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.test.TestBases;

/**
 * Step 4 of the startup sequence, the bounded verification, under a runtime killed with {@code SIGKILL}
 * (PM-ARCH-7). The killed runtime is a child JVM of {@link KilledRuntimeFixture}; the next start runs in this JVM on
 * the same {@code PM_MCP_BASE}. The operating system releases the singleton lock of a killed process, so the next
 * start acquires it, runs step 4 again and writes a fresh token.
 * <p>
 * The modes and markers are the protocol with the child ({@link RuntimeChildProcess}); the token length is the
 * specified form of the token. The test holds no other data: the token itself is created by the startup.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Bounded verification with a killed runtime")
class BoundedVerificationKilledRuntimeIT {

    private static final String USER = System.getProperty("user.name");

    /** Contract literal: the length of a 256-bit token in unpadded base64url. */
    private static final int TOKEN_LENGTH = 43;

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    private Path base;
    private MachinePaths paths;

    @BeforeEach
    void createBase() {
        // The short prefix keeps the socket path below the limit of the OS; a fixture argument, not test data
        base = TestBases.create("pmk");
        paths = new MachinePaths(base, MachinePaths.Os.current());
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(base);
        }
    }

    private StartupSequence.Outcome start(AuditChainVerification auditChain) throws IOException {
        // The SecureRandom is the entropy source the startup needs to create its token, not test data
        return new StartupSequence(paths, USER, auditChain, _ -> {
        }).run(new SecureRandom());
    }

    @Test
    @DisplayName("a runtime killed inside step 4 leaves the lock free and no token; the next start verifies again")
    void shouldStartAfterKillInsideStep4() throws Exception {
        fixture.start(RuntimeChildProcess.STARTUP, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.STARTUP));
        var whileHeld = start(AuditChainVerification.NONE);
        var tokenWhileHeld = Files.exists(paths.runtimeToken());

        fixture.kill();
        var verifications = new AtomicInteger();
        var next = start(() -> {
            verifications.incrementAndGet();
            return new AuditChainVerification.Outcome.Intact();
        });

        var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, next,
                "the next start must reach ready after the kill");
        try (var _ = ready.lock()) {
            assertAll(
                    () -> assertInstanceOf(StartupSequence.Outcome.LockHeld.class, whileHeld,
                            "a start while the child is inside step 4"),
                    () -> assertFalse(tokenWhileHeld, "no token may be written before step 4 ended"),
                    () -> assertEquals(1, verifications.get(), "the next start must verify exactly once"),
                    () -> assertEquals(TOKEN_LENGTH, Files.readString(paths.runtimeToken()).length(),
                            "the next start must write a full token"));
        }
    }

    @Test
    @DisplayName("a runtime killed after ready leaves its token, which the next start replaces")
    void shouldReplaceTokenOfKilledRuntime() throws Exception {
        fixture.start(RuntimeChildProcess.STARTUP_READY, base);
        fixture.awaitMarker(RuntimeChildProcess.ready(RuntimeChildProcess.STARTUP_READY));
        var killedToken = Files.readString(paths.runtimeToken());

        fixture.kill();
        var next = start(AuditChainVerification.NONE);

        var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, next,
                "the next start must reach ready after the kill");
        try (var _ = ready.lock()) {
            var token = Files.readString(paths.runtimeToken());
            assertAll(
                    () -> assertEquals(TOKEN_LENGTH, token.length(), "the next start must write a full token"),
                    () -> assertNotEquals(killedToken, token, "the token of the killed runtime is replaced"));
        }
    }
}
