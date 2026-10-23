/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleForm;

/**
 * The task queue the model caller queues into and closes through (PM-WORK-1 Queue and the closed outcomes of
 * {@code model-work/02-task-protocol.adoc#task-outcomes}). The queue itself is the task protocol's package; the model caller
 * only states what to queue and why a task closes without an attempt.
 *
 * @since 0.1
 */
public interface ModelQueue {

    /**
     * @param request the task to queue
     * @return the created task
     */
    Queued enqueue(Enqueue request);

    /**
     * @param request the task to close without an attempt
     */
    void close(Close request);

    /**
     * The task to queue, with the effective configuration the worker generation must have.
     *
     * @param scopeId             the plan or epic; empty for the workspace scope
     * @param stateId             the creating state
     * @param entityKey           the entity the task works on
     * @param role                the role
     * @param form                the form the task runs
     * @param configurationDigest the configuration digest at creation
     * @param harness             the effective harness
     * @param model               the effective model
     * @param effort              the effective effort
     * @param consultation        whether the task serves a {@code consult role} clause
     * @since 0.1
     */
    record Enqueue(Optional<String> scopeId, String stateId, String entityKey, String role, RoleForm form,
            String configurationDigest, String harness, String model, EffortValue effort, boolean consultation) {

        /** Rejects {@code null} components. */
        public Enqueue {
            Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(stateId, "stateId");
            Objects.requireNonNull(entityKey, "entityKey");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(configurationDigest, "configurationDigest");
            Objects.requireNonNull(harness, "harness");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(effort, "effort");
        }
    }

    /**
     * The created task.
     *
     * @param queueTaskId the identifier of the created queue task, {@code ^qt-[0-9a-f]{16}$}
     * @since 0.1
     */
    record Queued(String queueTaskId) {

        private static final Pattern QUEUE_TASK_ID = Pattern.compile("^qt-[0-9a-f]{16}$");

        /**
         * @throws IllegalArgumentException if the identifier does not match {@code ^qt-[0-9a-f]{16}$}
         */
        public Queued {
            if (queueTaskId == null || !QUEUE_TASK_ID.matcher(queueTaskId).matches()) {
                throw new IllegalArgumentException("Not a queue task id: " + queueTaskId);
            }
        }
    }

    /**
     * The task to close without an attempt.
     *
     * @param scopeId   the plan or epic; empty for the workspace scope
     * @param stateId   the creating state
     * @param entityKey the entity the task works on
     * @param role      the role
     * @param reason    one of {@link ModelCallResult#ROLE_UNDEFINED}, {@link ModelCallResult#CLASS_MISMATCH},
     *                  {@link ModelCallResult#ROLE_UNQUALIFIED} or {@link ModelCallResult#ROLE_UNAVAILABLE}
     * @since 0.1
     */
    record Close(Optional<String> scopeId, String stateId, String entityKey, String role, String reason) {

        /** Rejects {@code null} components. */
        public Close {
            Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(stateId, "stateId");
            Objects.requireNonNull(entityKey, "entityKey");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
