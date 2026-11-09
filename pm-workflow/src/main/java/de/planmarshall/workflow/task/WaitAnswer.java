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
import de.planmarshall.workflow.hypermedia.Answers;

/**
 * The answer to a worker's {@code pm_wait} (Model Work § {@code pm_wait}): an offer, the answer to the task the worker
 * holds, an empty window, or the end of the worker.
 *
 * @since 0.1
 */
public sealed interface WaitAnswer permits WaitAnswer.Offered, WaitAnswer.Answered, WaitAnswer.WaitAgain,
        WaitAnswer.End {

    /**
     * A task leased to the caller's generation.
     *
     * @param task the offered task
     * @since 0.1
     */
    record Offered(QueueTaskRecord task) implements WaitAnswer {

        /** @throws NullPointerException if the task is {@code null} */
        public Offered {
            Objects.requireNonNull(task, "task");
        }
    }

    /**
     * The answer to a question of the task the caller holds (Model Work § Continuing after a Question): the task's
     * representation carries the answer in {@code facts.answers}, and the representation offers {@code ack} as its only
     * link until the caller acknowledges it.
     *
     * @param task    the held task
     * @param answers the answer's items
     * @since 0.1
     */
    record Answered(QueueTaskRecord task, Answers answers) implements WaitAnswer {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Answered {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(answers, "answers");
        }
    }

    /**
     * The window ended without a task for the caller.
     *
     * @param reason why no task was offered, one constant of {@link WaitAgainReason}
     * @since 0.1
     */
    record WaitAgain(WaitAgainReason reason) implements WaitAnswer {

        /** @throws NullPointerException if the reason is {@code null} */
        public WaitAgain {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The caller is to end.
     * <p>
     * A worker also ends <em>drained</em> or <em>stopped</em> when the runtime drains it or the operator stops the
     * runtime; that ending is the runtime's, not a reason this protocol answers.
     *
     * @param reason why the worker ends, one constant of {@link EndReason}
     * @since 0.1
     */
    record End(EndReason reason) implements WaitAnswer {

        /** @throws NullPointerException if the reason is {@code null} */
        public End {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Why a window ended without a task. Every value the protocol answers is a constant here, and the Javadoc of each
     * constant names what it means; no other reason can be used.
     *
     * @since 0.1
     */
    enum WaitAgainReason {

        /** Nothing in the role's queue was offered: no queued task, or the held task is not in a waiting state. */
        NO_TASK("no_task"),
        /** The held task belongs to a plan that is paused. */
        PAUSED("paused"),
        /** The held task is consulting. */
        CONSULTING("consulting"),
        /** The held task waits for the answer to its question, which has not arrived. */
        AWAITING_ANSWER("awaiting_answer"),
        /** The attempt ended without a result and the re-offer budget is spent. */
        ATTEMPTS_SPENT("attempts_spent");

        private final String wireName;

        WaitAgainReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in the protocol */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Why the worker is to end. Every value the protocol answers is a constant here, and the Javadoc of each constant
     * names what it means; no other reason can be used.
     *
     * @since 0.1
     */
    enum EndReason {

        /** The worker's job ended before the wait. */
        JOB_ENDED("job_ended"),
        /** The caller's generation was fenced. */
        FENCED("fenced"),
        /** The worker's configuration digest changed, so it is recycled. */
        RECYCLED("recycled");

        private final String wireName;

        EndReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in the protocol */
        public String wireName() {
            return wireName;
        }
    }
}
