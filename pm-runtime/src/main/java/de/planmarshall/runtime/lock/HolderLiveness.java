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

import java.time.Instant;
import java.util.Optional;


import de.planmarshall.core.store.HolderInstance;
import lombok.experimental.UtilityClass;

/**
 * Whether the runtime instance that serves a lease is still alive (PM-IMPL-7). The operating system reuses process
 * ids, so a process id alone proves nothing: a holder is alive only if a process with its process id runs and that
 * process started at the instant the holder recorded. The two are compared together.
 * <p>
 * A lease is never taken for abandoned on a guess. A running process whose start instant the operating system does
 * not tell is therefore taken for the holder; a holder is not alive only when no process has its process id, or the
 * process that has it started at another instant.
 *
 * @since 0.1
 */
@UtilityClass
public class HolderLiveness {

    /**
     * @param holder the runtime instance a lease names as its holder
     * @return whether that runtime instance is still running
     */
    public static boolean isAlive(HolderInstance holder) {
        var process = ProcessHandle.of(holder.runtimePid()).filter(ProcessHandle::isAlive);
        return isAlive(holder, process.isPresent(), process.flatMap(running -> running.info().startInstant()));
    }

    /**
     * The decision on what the operating system tells about the process id of the holder.
     *
     * @param holder        the runtime instance a lease names as its holder
     * @param processExists whether a process with the process id of the holder runs
     * @param startInstant  the instant that process started; empty if the operating system does not tell
     * @return whether the holder is alive
     */
    static boolean isAlive(HolderInstance holder, boolean processExists, Optional<Instant> startInstant) {
        return processExists && startInstant.map(holder.runtimeStartedAt()::equals).orElse(true);
    }
}
