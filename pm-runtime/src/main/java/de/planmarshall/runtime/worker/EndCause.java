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

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.WorkerState;

/**
 * Why one worker generation is ended (PM-WORK-3, Ending a Worker): the closed set of causes the one end path runs
 * through {@link WorkerEnding}, with what the job record's worker state carries and whether the attempt of its task
 * spends a re-offer.
 * <p>
 * The cancel cause is the one the job runtime maps to a fate: a pause at once is the operator's own
 * {@link JobRunner.CancelCause#PLAN_PAUSED_NOW}, every other end is the supervisor's
 * {@link JobRunner.CancelCause#SUPERVISOR_END}. The supervisor never sends a signal itself; the forced end reaches the
 * process only through the runner's termination protocol.
 *
 * @since 0.1
 */
public enum EndCause {

    /** A fresh worker served its task. */
    TASK_DONE("task_done", WorkerState.EndReason.TASK_DONE, false, WorkerState.State.ENDED),

    /** A warm worker was recycled. */
    RECYCLE("recycle", WorkerState.EndReason.IDLE_RECYCLE, false, WorkerState.State.ENDED),

    /** The runtime drains; the task stays queued, not lost. */
    DRAIN("drain", WorkerState.EndReason.DRAIN, false, WorkerState.State.ENDED),

    /** The operator paused the plan at once; its task is queued again without counting a re-offer. */
    PAUSE_AT_ONCE("pause_at_once", WorkerState.EndReason.WITHDRAWN, false, WorkerState.State.ENDED),

    /** A worker was detected as lost; its task is offered again within its re-offer budget. */
    LOST("lost", WorkerState.EndReason.EXITED, true, WorkerState.State.LOST),

    /** An off-track action restarted the attempt; it is offered again within its re-offer budget. */
    OFF_TRACK("off_track", WorkerState.EndReason.WITHDRAWN, true, WorkerState.State.ENDED),

    /** The worker exited before its first call. */
    START_FAILURE("start_failure", WorkerState.EndReason.START_FAILURE, false, WorkerState.State.ENDED);

    private final String wireName;
    private final WorkerState.EndReason endReason;
    private final boolean spendsReoffer;
    private final WorkerState.State terminalState;

    EndCause(String wireName, WorkerState.EndReason endReason, boolean spendsReoffer,
            WorkerState.State terminalState) {
        this.wireName = wireName;
        this.endReason = endReason;
        this.spendsReoffer = spendsReoffer;
        this.terminalState = terminalState;
    }

    /** @return the name of the cause in the log and in a report */
    public String wireName() {
        return wireName;
    }

    /** @return the end reason the job record's worker state carries; the loss signal refines it for a lost worker */
    public WorkerState.EndReason endReason() {
        return endReason;
    }

    /** @return whether the ended attempt spends one re-offer of its task */
    public boolean spendsReoffer() {
        return spendsReoffer;
    }

    /** @return the terminal worker state of the generation */
    public WorkerState.State terminalState() {
        return terminalState;
    }

    /** @return the cancel cause a forced end reaches the runner with */
    public JobRunner.CancelCause cancelCause() {
        return this == PAUSE_AT_ONCE ? JobRunner.CancelCause.PLAN_PAUSED_NOW : JobRunner.CancelCause.SUPERVISOR_END;
    }
}
