/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import java.util.Objects;

import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * One off-track signal the runtime computed for a worker, with the action the role artifact chooses (Model Work § A
 * Worker Off Track).
 *
 * @param signal      the signal
 * @param action      the action to take; {@code show} where the artifact names none
 * @param measurement the measured value
 * @param threshold   the threshold in force
 * @since 0.1
 */
public record OffTrackSignal(QueueTaskRecord.SignalName signal, QueueTaskRecord.SignalAction action,
double measurement, double threshold) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a value is negative, or an {@code agent_started} signal does not take the
     *                                  action {@code restart}
     */
    public OffTrackSignal {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(action, "action");
        if (measurement < 0 || threshold < 0) {
            throw new IllegalArgumentException("A signal's measurement and threshold are not negative");
        }
        if (signal == QueueTaskRecord.SignalName.AGENT_STARTED
                && action != QueueTaskRecord.SignalAction.RESTART) {
            throw new IllegalArgumentException(
                    "An 'agent_started' signal always takes the action 'restart', not '"
                            + action.wireName() + "'");
        }
    }

    /**
     * @param executable the executable the supervisor detected
     * @return the signal of an {@code agent_started} detection: measured 1, threshold 1, action {@code restart}
     */
    public static OffTrackSignal agentStarted(String executable) {
        Objects.requireNonNull(executable, "executable");
        return new OffTrackSignal(QueueTaskRecord.SignalName.AGENT_STARTED, QueueTaskRecord.SignalAction.RESTART, 1, 1);
    }
}
