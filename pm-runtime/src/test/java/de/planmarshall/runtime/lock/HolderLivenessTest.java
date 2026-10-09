/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.HolderInstance;

/**
 * Whether the holder of a lease is alive (PM-IMPL-7): a process id together with the start instant of the process,
 * because the operating system reuses process ids.
 */
@DisplayName("Holder liveness")
class HolderLivenessTest {

    private static final Instant RECORDED = Instant.parse("2026-10-09T08:15:30.123Z");

    private static Instant ownStartInstant() {
        return ProcessHandle.current().info().startInstant().orElseThrow();
    }

    static Stream<Arguments> observations() {
        return Stream.of(
                Arguments.of("a process with the recorded start instant", true, Optional.of(RECORDED), true),
                Arguments.of("a process with another start instant", true, Optional.of(RECORDED.plusMillis(1)), false),
                Arguments.of("a process whose start instant is not told", true, Optional.empty(), true),
                Arguments.of("no process, start instant not told", false, Optional.empty(), false),
                Arguments.of("no process, although a start instant is given", false, Optional.of(RECORDED), false));
    }

    @Test
    @DisplayName("the process of this test with its own start instant is alive")
    void ownProcess() {
        var holder = new HolderInstance(ProcessHandle.current().pid(), ownStartInstant());

        assertTrue(HolderLiveness.isAlive(holder));
    }

    /**
     * The operating system gives the process id of an ended runtime to a new process. The lease of the ended
     * runtime then names a process id that runs, and only the start instant tells that it is another process.
     */
    @Test
    @DisplayName("the process id of this test with another start instant is not alive: the process id was reused")
    void reusedProcessId() {
        var holder = new HolderInstance(ProcessHandle.current().pid(), ownStartInstant().minusSeconds(1));

        assertFalse(HolderLiveness.isAlive(holder));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("the process id of an ended child process is not alive")
    void endedProcess() throws IOException, InterruptedException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var child = new ProcessBuilder(java, "-version")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        var startedAt = child.info().startInstant().orElse(RECORDED);
        child.waitFor();

        var holder = new HolderInstance(child.pid(), startedAt);

        assertFalse(HolderLiveness.isAlive(holder));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("observations")
    @DisplayName("a holder is alive only with a running process that did not start at another instant")
    void decision(String observation, boolean processExists, Optional<Instant> startInstant, boolean alive) {
        var holder = new HolderInstance(4711, RECORDED);

        assertEquals(alive, HolderLiveness.isAlive(holder, processExists, startInstant), observation);
    }
}
