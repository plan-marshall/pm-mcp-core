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
 * The answer to a worker's {@code pm_wait} (Model Work § {@code pm_wait}): an offer, an empty window, or the end of the
 * worker.
 *
 * @since 0.1
 */
public sealed interface WaitAnswer permits WaitAnswer.Offered, WaitAnswer.WaitAgain, WaitAnswer.End {

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
