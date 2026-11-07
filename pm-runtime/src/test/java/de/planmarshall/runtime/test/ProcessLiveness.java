/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.test;

import java.time.Duration;

/**
 * The liveness of a process for the tests that start real descendants: whether a PID is alive, and a bounded wait
 * until it is not, shared so that no test class copies them.
 */
public final class ProcessLiveness {

    /** How long {@link #awaitDead(long)} waits before it fails. */
    private static final Duration DEATH_TIMEOUT = Duration.ofSeconds(10);

    private ProcessLiveness() {
    }

    /**
     * Waits until a PID is no longer alive, so a killed process is not seen as a zombie for one instant.
     *
     * @param pid the PID of the process
     * @throws AssertionError if the process is still alive after ten seconds
     */
    public static void awaitDead(long pid) {
        var deadline = System.nanoTime() + DEATH_TIMEOUT.toNanos();
        while (alive(pid)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("the descendant %d is still alive".formatted(pid));
            }
            Thread.onSpinWait();
        }
    }

    /**
     * @param pid the PID of the process
     * @return whether a process of this PID exists and is alive
     */
    public static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
