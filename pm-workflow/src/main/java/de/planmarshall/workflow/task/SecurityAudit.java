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

import java.time.Instant;
import java.util.Objects;

/**
 * Where the task protocol records the {@code security} audit event {@code agent_started} (Model Work § A Worker Off
 * Track): the supervisor found a process of the worker's process tree that runs an enrolled harness executable or the
 * operator binary {@code pm-operator}.
 * <p>
 * The record names the executable. A second agent started by a worker would run outside every task, tool set and usage
 * record, so the detection is always a {@code restart}.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface SecurityAudit {

    /** An audit that records nothing, for a caller that has no audit log. */
    SecurityAudit NONE = event -> {
    };

    /**
     * @param event the detection
     */
    void agentStarted(Event event);

    /**
     * One {@code agent_started} detection.
     *
     * @param executable   the enrolled harness executable or {@code pm-operator} the process runs
     * @param generationId the worker's generation
     * @param queueTaskId  the task the worker serves
     * @param attempt      the attempt of the task
     * @param at           the instant the supervisor detected it
     * @since 0.1
     */
    record Event(String executable, String generationId, String queueTaskId, int attempt, Instant at) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the executable is blank or the attempt is below 1
         */
        public Event {
            Objects.requireNonNull(executable, "executable");
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(queueTaskId, "queueTaskId");
            Objects.requireNonNull(at, "at");
            if (executable.isBlank()) {
                throw new IllegalArgumentException("An agent_started event names its executable");
            }
            if (attempt < 1) {
                throw new IllegalArgumentException("An attempt counts from 1: " + attempt);
            }
        }
    }
}
