/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The engine port that creates the queue task of a {@code job llm} state (PM-IMPL-5, bullet 1: model work is an
 * explicit side effect of the state that owns it). The engine calls it once per entity when it enters a task state
 * and records the returned {@code queue_task_id}, keyed by the entity, in the state's {@code active_tasks} of the
 * status store, so the task belongs to persisted state and never to the memory of a running engine (PM-ARCH-4,
 * bullets 4 and 5).
 * <p>
 * The task queue of {@code pm-runtime} implements the port; no class of {@code pm-core} does. A role that cannot take
 * the task is not refused here: the queue closes such a task {@code lost}, which the state routes.
 *
 * @since 0.1
 */
public interface QueueTaskCreator {

    /**
     * Creates one queue task of a role for an entity of a task state.
     *
     * @param request the state, entity and role the task is created for
     * @return the created task
     */
    Created create(CreateRequest request);

    /**
     * What the engine asks the port to create: one task of one role for one entity of one state.
     *
     * @param scopeId the identifier of the plan or epic the state belongs to; empty for the workspace scope
     * @param stateId the identifier of the task state the task is created for
     * @param entityKey the identifier of the entity the task works on, the key of the task in {@code active_tasks}
     * @param role the name of the role whose worker takes the task
     * @since 0.1
     */
    record CreateRequest(Optional<String> scopeId, String stateId, String entityKey, String role) {

        /**
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if the state identifier, the entity key or the role is blank
         */
        public CreateRequest {
            Objects.requireNonNull(scopeId, "scopeId");
            requireNonBlank(stateId, "stateId");
            requireNonBlank(entityKey, "entityKey");
            requireNonBlank(role, "role");
        }

        private static void requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }

    /**
     * The created task.
     *
     * @param queueTaskId the identifier of the created queue task, {@code ^qt-[0-9a-f]{16}$}
     * @since 0.1
     */
    record Created(String queueTaskId) {

        private static final Pattern QUEUE_TASK_ID = Pattern.compile("^qt-[0-9a-f]{16}$");

        /**
         * @throws NullPointerException if the identifier is {@code null}
         * @throws IllegalArgumentException if the identifier does not match {@code ^qt-[0-9a-f]{16}$}
         */
        public Created {
            Objects.requireNonNull(queueTaskId, "queueTaskId");
            if (!QUEUE_TASK_ID.matcher(queueTaskId).matches()) {
                throw new IllegalArgumentException(
                        "queue_task_id does not match ^qt-[0-9a-f]{16}$: " + queueTaskId);
            }
        }
    }
}
