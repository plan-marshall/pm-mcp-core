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

import java.util.Map;
import java.util.Set;

import de.planmarshall.core.store.WorkerState;

/**
 * The supervisor's lifecycle of one worker generation (PM-WORK-3, Supervision &amp; Recycling), and its mapping onto the
 * task protocol's worker states of the job record ({@link WorkerState.State}).
 * <p>
 * The set is deliberately smaller than the protocol's own bookkeeping in one direction and wider in another: it adds
 * the states the supervisor owns before and after a call ({@code STARTING}, {@code ENDING}, {@code START_FAILED}) and
 * folds the protocol's {@code SUBMITTED} into {@code WAITING}, because a submitted task ends the attempt and the
 * worker waits again. {@link #transitionTo(WorkerLifecycle)} refuses every transition the table does not name and
 * every transition out of a terminal state, so a lost worker is never resurrected as a running one.
 *
 * @since 0.1
 */
public enum WorkerLifecycle {

    /** The worker was started and has made no call yet. */
    STARTING(WorkerState.State.WAITING),

    /** The worker waits for a task. */
    WAITING(WorkerState.State.WAITING),

    /** A task was offered to the worker. */
    OFFERED(WorkerState.State.OFFERED),

    /** The offer was acknowledged. */
    ACKNOWLEDGED(WorkerState.State.ACKNOWLEDGED),

    /** The worker executes the task. */
    EXECUTING(WorkerState.State.EXECUTING),

    /** The worker consults. */
    CONSULTING(WorkerState.State.CONSULTING),

    /** The worker holds its task for the answer to its question. */
    HOLDING(WorkerState.State.CONSULTING),

    /** The worker was instructed to end and has not exited yet. */
    ENDING(WorkerState.State.WAITING),

    /** The worker ended. */
    ENDED(WorkerState.State.ENDED),

    /** The worker was detected as lost. */
    LOST(WorkerState.State.LOST),

    /** The worker exited before its first call. */
    START_FAILED(WorkerState.State.ENDED);

    private static final Map<WorkerLifecycle, Set<WorkerLifecycle>> TRANSITIONS = Map.of(
            STARTING, Set.of(WAITING, ENDING, LOST, START_FAILED),
            WAITING, Set.of(OFFERED, ENDING, LOST),
            OFFERED, Set.of(ACKNOWLEDGED, WAITING, ENDING, LOST),
            ACKNOWLEDGED, Set.of(EXECUTING, CONSULTING, WAITING, ENDING, LOST),
            EXECUTING, Set.of(CONSULTING, WAITING, ENDING, LOST),
            CONSULTING, Set.of(EXECUTING, HOLDING, WAITING, ENDING, LOST),
            HOLDING, Set.of(EXECUTING, ENDING, LOST),
            ENDING, Set.of(ENDED, START_FAILED, LOST));

    private final WorkerState.State recordState;

    WorkerLifecycle(WorkerState.State recordState) {
        this.recordState = recordState;
    }

    /** @return the worker state the job record carries in this lifecycle state */
    public WorkerState.State recordState() {
        return recordState;
    }

    /** @return whether this state ends the generation's life */
    public boolean terminal() {
        return this == ENDED || this == LOST || this == START_FAILED;
    }

    /**
     * @param next the state the supervisor wants to enter
     * @return whether the protocol has this transition
     */
    public boolean canTransitionTo(WorkerLifecycle next) {
        return next != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    /**
     * @param next the state the supervisor wants to enter
     * @return the next state
     * @throws IllegalStateException if the transition is not in the table, including every transition out of a terminal
     *                               state
     */
    public WorkerLifecycle transitionTo(WorkerLifecycle next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("A worker cannot move from %s to %s".formatted(this, next));
        }
        return next;
    }
}
