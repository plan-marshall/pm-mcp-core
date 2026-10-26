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

/**
 * The supervisor's inbound API, consumed by the assembly's transport (PM-WORK-3, PM-WORK-7): the facts that detection,
 * recycling and the start-failure guard need, handed over without the supervisor ever reading a transport.
 * <p>
 * Every call names the generation it concerns; the supervisor resolves the generation from its registry and ignores a
 * call of a generation it does not know. A wait answer is one of the protocol's three: an offer, another wait, or the
 * end of the worker.
 *
 * @since 0.1
 */
public interface WorkerCalls {

    /**
     * A worker made a call; it is not silent from this instant on.
     *
     * @param generationId the worker's generation
     * @param at           the instant of the call
     */
    void callHeartbeat(String generationId, Instant at);

    /**
     * A worker opened a bounded wait.
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the task the worker asked a task for
     * @param roleDigest   the role's current configuration digest, which the answer carries
     * @return the answer to the wait
     */
    WaitAnswer waitOpened(String generationId, String queueTaskId, String roleDigest);

    /**
     * A turn completed with the context size of the worker's host.
     *
     * @param generationId  the worker's generation
     * @param contextTokens the input, cache-read and cache-creation tokens of the turn
     */
    void turnCompleted(String generationId, long contextTokens);

    /**
     * A worker closed its task.
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the closed task
     */
    void taskClosed(String generationId, String queueTaskId);

    /**
     * A plan was paused with the pause point <em>now</em>: its workers are ended by force at once and its tasks are
     * queued again without counting a re-offer.
     *
     * @param planId the paused plan
     */
    void planPaused(String planId);

    /**
     * A harness was enrolled anew, which clears its start-failure guard.
     *
     * @param clientId the client id of the harness
     */
    void harnessEnrolled(String clientId);

    /**
     * The answer to an opened wait.
     *
     * @since 0.1
     */
    sealed interface WaitAnswer permits WaitAnswer.Offered, WaitAnswer.WaitAgain, WaitAnswer.End {

        /**
         * A task is offered to the worker.
         *
         * @param queueTaskId the offered task
         * @param roleDigest  the role's current configuration digest
         * @since 0.1
         */
        record Offered(String queueTaskId, String roleDigest) implements WaitAnswer {

            /**
             * @throws NullPointerException if a component is {@code null}
             */
            public Offered {
                Objects.requireNonNull(queueTaskId, "queueTaskId");
                Objects.requireNonNull(roleDigest, "roleDigest");
            }
        }

        /**
         * No task is queued: the worker waits again, which may be an idle wake-up.
         *
         * @since 0.1
         */
        record WaitAgain() implements WaitAnswer {
        }

        /**
         * The worker ends: its task closed, it was recycled, or the runtime drains.
         *
         * @since 0.1
         */
        record End() implements WaitAnswer {
        }
    }
}
