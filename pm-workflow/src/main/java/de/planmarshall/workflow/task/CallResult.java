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
 * The result of a call on a task entity (Model Work § Execute &amp; Submit).
 *
 * @since 0.1
 */
public sealed interface CallResult permits CallResult.Accepted, CallResult.Refused {

    /**
     * The call was carried out.
     *
     * @param task the task as it stands after the call
     * @since 0.1
     */
    record Accepted(QueueTaskRecord task) implements CallResult {

        /** @throws NullPointerException if the task is {@code null} */
        public Accepted {
            Objects.requireNonNull(task, "task");
        }
    }

    /**
     * The call was refused. The task is the entity's representation for a refusal that carries one
     * ({@code stale_entity}); it is {@code null} when no task of that id exists.
     *
     * @param refusal why the call was refused
     * @param task    the task, or {@code null} when the queue holds none of that id
     * @since 0.1
     */
    record Refused(TaskRefusal refusal, QueueTaskRecord task) implements CallResult {

        /** @throws NullPointerException if the refusal is {@code null} */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
