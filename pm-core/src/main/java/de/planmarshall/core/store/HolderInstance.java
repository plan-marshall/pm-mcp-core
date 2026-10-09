/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.time.Instant;
import java.util.Objects;

/**
 * The runtime instance that currently serves a lease (PM-IMPL-7). It says whether the lease is still served, never
 * who owns it: a process id alone is reused by the operating system, so an instance is its process id together with
 * the instant the process started.
 *
 * @param runtimePid       the process id of the runtime, at least 1
 * @param runtimeStartedAt the instant the process started, as the operating system reports it
 * @since 0.1
 */
public record HolderInstance(long runtimePid, Instant runtimeStartedAt) {

    /**
     * @throws NullPointerException     if the start instant is {@code null}
     * @throws IllegalArgumentException if the process id is below 1
     */
    public HolderInstance {
        Objects.requireNonNull(runtimeStartedAt, "runtimeStartedAt");
        if (runtimePid < 1) {
            throw new IllegalArgumentException("runtime_pid must be at least 1: " + runtimePid);
        }
    }
}
