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
import java.util.Objects;
import lombok.Builder;

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
     * @param report the signal and the facts the protocol records
     */
    void offTrack(OffTrackReport report);

    /**
     * One activity-derived off-track signal as the runtime reports it.
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
     * @since 0.1
     */
    @Builder
    record OffTrackReport(String generationId, String queueTaskId, int attempt, String signal, String action,
    double measurement, double threshold, Instant at) {
    }

    /**
     * Reports that a generation's hold ended: its task is suspended where it may resume, else it falls back, and the
     * worker is recycled (Model Work § Continuing after a Question, Budget).
     * <p>
     * The notification is sent before the recycle, while the generation still leases its task, so the protocol names
     * the attempt it ends.
     *
     * @param report the ended hold and the facts the protocol decides from
     * @since 0.1
     */
    void holdEnded(HoldEndedReport report);

    /**
     * One ended hold as the runtime reports it.
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the queue task the generation held
     * @param attempt      the attempt of that task, from 1
     * @param cause        why the hold ended
     * @param at           the instant the hold ended
     * @since 0.1
     */
    @Builder
    record HoldEndedReport(String generationId, String queueTaskId, int attempt, HoldEndCause cause, Instant at) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the attempt is below 1
         */
        public HoldEndedReport {
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(queueTaskId, "queueTaskId");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(at, "at");
            if (attempt < 1) {
                throw new IllegalArgumentException("'attempt' must not be below 1: " + attempt);
            }
        }
    }

    /**
     * Why a hold ended, as the closed set this runtime raises: the token budget is the only cause a supervisor decides
     * (Model Work § Continuing after a Question, Budget). A later cause extends this set, never a free string.
     *
     * @since 0.1
     */
    enum HoldEndCause {

        /** The holding worker reached its token budget. */
        TOKEN_BUDGET("token_budget");

        private final String wireName;

        HoldEndCause(String wireName) {
            this.wireName = wireName;
        }

        /** @return the wire name the protocol's record carries */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Closes the queued tasks of one role on one harness as lost with the reason {@code role_unavailable}, with their
     * conservative fallback, when the start-failure guard holds.
     *
     * @param role    the role
     * @param harness the harness id of the harness
     */
    void closeUnavailable(String role, String harness);
}
