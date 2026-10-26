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

import java.time.Instant;

/**
 * The module-boundary contract between the supervisor and the task protocol (PM-WORK-3, PM-ARCH-8).
 * <p>
 * The task protocol lives in {@code pm-workflow}, which {@code pm-runtime} may not reference; the assembly
 * {@code pm-mcp-server} adapts it to this interface over {@code de.planmarshall.workflow.task.TaskProtocol}. The
 * protocol stays the owner of the fences, the re-offers and the {@code agent_started} security record: the supervisor
 * reports the fact and never writes those records itself.
 *
 * @since 0.1
 */
public interface WorkerProtocol {

    /**
     * Fences one worker generation, so every later request of its job token is refused {@code generation_fenced}.
     *
     * @param generationId the worker's generation
     */
    void fence(String generationId);

    /**
     * Reports that a process of the worker's tree started an enrolled harness executable or the operator binary.
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the queue task the generation holds
     * @param attempt      the attempt of that task
     * @param executable   the executable's path
     * @param at           the instant the process started
     */
    void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at);

    /**
     * Reports an activity-derived off-track signal to the task protocol, which records it in the queue task's
     * {@code signals[]} and as a {@code signal} entry of the activity journal, and takes its action (Model Work § A
     * Worker Off Track).
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the queue task the generation holds
     * @param attempt      the attempt of that task
     * @param signal       the signal's wire name, one of {@code repeating}, {@code no_progress}, {@code over_size},
     *                     {@code outside_footprint}
     * @param action       the action's wire name, one of {@code show}, {@code nudge}, {@code yield}, {@code restart}
     * @param measurement  the measured value
     * @param threshold    the threshold in force
     * @param at           the instant the signal was raised
     * @implSpec the assembly adapter must override this; a protocol that does not is refused rather than silently
     *           dropping the signal.
     */
    default void offTrack(String generationId, String queueTaskId, int attempt, String signal, String action,
            double measurement, double threshold, Instant at) {
        throw new UnsupportedOperationException(
                "The assembly adapter of WorkerProtocol must record the off-track signal " + signal);
    }

    /**
     * Closes the queued tasks of one role on one harness as lost with the reason {@code role_unavailable}, with their
     * conservative fallback, when the start-failure guard holds.
     *
     * @param role    the role
     * @param harness the client id of the harness
     */
    void closeUnavailable(String role, String harness);
}
