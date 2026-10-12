/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.List;
import java.util.Objects;

/**
 * What {@link TransitionFuzzer} found in one unit (PM-TEST-3).
 *
 * @param configuration the configuration the fuzzer ran with
 * @param runsExecuted  the number of runs it executed
 * @param deadStates    every dead state it reached, each once with its first witness, in the order they were found
 * @since 0.1
 */
public record FuzzReport(FuzzConfiguration configuration, int runsExecuted, List<DeadState> deadStates) {

    /**
     * Refuses a missing configuration and copies the dead states into an immutable list.
     *
     * @param configuration the configuration the fuzzer ran with
     * @param runsExecuted  the number of runs it executed
     * @param deadStates    every dead state it reached
     * @throws NullPointerException if the configuration, the list or one of its elements is {@code null}
     */
    public FuzzReport {
        Objects.requireNonNull(configuration, "configuration");
        deadStates = List.copyOf(deadStates);
    }

    /**
     * @return {@code true} when a run reached a dead state
     */
    public boolean foundDeadState() {
        return !deadStates.isEmpty();
    }
}
