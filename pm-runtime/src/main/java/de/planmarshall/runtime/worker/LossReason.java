/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import de.planmarshall.core.store.AttemptEnd;
import de.planmarshall.core.store.WorkerState;

/**
 * Why the supervisor detected a worker as lost (PM-WORK-3, Detection): the process exited after its first call, an
 * offer was not acknowledged within the host's acknowledgement deadline, or the worker was silent past the bounded wait
 * plus its silence grace.
 * <p>
 * The reason is one value for the whole report: the job record's worker state carries {@link #endReason()}, and the
 * attempt of the queue task carries {@link #attemptEnd()}. A {@code zero_tool} worker produces none of them — its exit
 * and its budget alone decide ({@link LossDetection}).
 *
 * @since 0.1
 */
public enum LossReason {

    /** The worker's process exited after its first call. */
    EXIT("exit", WorkerState.EndReason.EXITED, AttemptEnd.EXITED_WITHOUT_SUBMIT),

    /** An offer was not acknowledged within the acknowledgement deadline. */
    ACK_MISSED("ack_missed", WorkerState.EndReason.ACK_MISSED, AttemptEnd.WORKER_LOST),

    /** The worker was silent past the bounded wait plus its silence grace. */
    SILENCE("silence", WorkerState.EndReason.SILENCE, AttemptEnd.WORKER_LOST);

    private final String wireName;
    private final WorkerState.EndReason endReason;
    private final AttemptEnd attemptEnd;

    LossReason(String wireName, WorkerState.EndReason endReason, AttemptEnd attemptEnd) {
        this.wireName = wireName;
        this.endReason = endReason;
        this.attemptEnd = attemptEnd;
    }

    /** @return the name of the reason in a record and in a report */
    public String wireName() {
        return wireName;
    }

    /** @return the worker state's end reason of a worker lost for this reason */
    public WorkerState.EndReason endReason() {
        return endReason;
    }

    /** @return how the attempt of the worker's task ended */
    public AttemptEnd attemptEnd() {
        return attemptEnd;
    }
}
