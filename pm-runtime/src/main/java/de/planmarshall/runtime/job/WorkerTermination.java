/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.time.Instant;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.WorkerState;

/**
 * The terminal worker block a settled job record carries (PM-WORK-3, Job Record; PM-EXEC-4, Write order at job end):
 * the settlement writes it in the same transaction as the fate, so a settled record is final and the supervision pass
 * completes an end without writing the worker state again ({@link JobRecordStore} refuses a worker write into a
 * settled record, and that refusal stays).
 * <p>
 * The end reason is derived from the fate the runner observed and from the worker's own connection: a worker that
 * never called has failed to start unless its role class is {@code zero_tool}, which gets no connection and so is
 * judged by its fate alone; a succeeded job served its task, and every other fate is the end the evidence names. The
 * mapping is deliberately coarse where the fate is coarse — a recycle and a drain of a worker are both a
 * {@code cancelled} fate — and the fine-grained cause stays the supervisor's, recorded in the attempt's end and in
 * {@code WORKER_GENERATION_ENDED}.
 *
 * @since 0.1
 */
@UtilityClass
public final class WorkerTermination {

    /**
     * @param current the worker state the record carried before the settlement
     * @param settled the settled record of the worker's job
     * @return the terminal worker state, with its leased task cleared
     * @throws NullPointerException if a component is {@code null}
     */
    public static WorkerState terminal(WorkerState current, JobRecord settled) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(settled, "settled");
        var reason = endReason(current, settled);
        return current.toBuilder()
                .state(stateOf(reason))
                .stateAt(settledAt(current, settled))
                .task(null)
                .endReason(reason)
                .startFailure(reason == WorkerState.EndReason.START_FAILURE)
                .build();
    }

    /**
     * @param current the worker state the record carried
     * @param settled the settled record of the worker's job
     * @return why the worker ended, as the fate and the connection decide it
     */
    static WorkerState.EndReason endReason(WorkerState current, JobRecord settled) {
        if (current.connectedAt() == null && current.roleClass() != WorkerState.RoleClass.ZERO_TOOL) {
            return WorkerState.EndReason.START_FAILURE;
        }
        return switch (settled.fate()) {
            case SUCCEEDED -> WorkerState.EndReason.TASK_DONE;
            case TIMED_OUT -> WorkerState.EndReason.BUDGET;
            case KILLED -> WorkerState.EndReason.DRAIN;
            case CANCELLED -> WorkerState.EndReason.WITHDRAWN;
            case FAILED, FAILED_UNPARSED, INDETERMINATE, REFUSED -> WorkerState.EndReason.EXITED;
        };
    }

    /**
     * @param reason why the worker ended
     * @return the terminal protocol state of that end: a loss is {@code lost}, every other end {@code ended}
     */
    static WorkerState.State stateOf(WorkerState.EndReason reason) {
        return reason == WorkerState.EndReason.EXITED ? WorkerState.State.LOST : WorkerState.State.ENDED;
    }

    private static Instant settledAt(WorkerState current, JobRecord settled) {
        var endedAt = settled.endedAt();
        return endedAt == null ? current.stateAt() : endedAt;
    }
}
