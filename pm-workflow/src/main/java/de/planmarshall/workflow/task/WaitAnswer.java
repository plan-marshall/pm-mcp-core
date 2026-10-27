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
     * @param reason why no task was offered
     * @since 0.1
     */
    record WaitAgain(String reason) implements WaitAnswer {

        /** @throws NullPointerException if the reason is {@code null} */
        public WaitAgain {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The caller is to end.
     *
     * @param reason why the worker ends: {@code job_ended}, {@code fenced}, {@code recycled}, {@code drained} or
     *               {@code stopped}
     * @since 0.1
     */
    record End(String reason) implements WaitAnswer {

        /** @throws NullPointerException if the reason is {@code null} */
        public End {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
