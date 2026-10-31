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

import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.RoleArtifact;

/**
 * The task facts the runtime needs to evaluate the off-track signals of a worker's activity (Model Work § A Worker Off
 * Track): the attempt the worker serves, the role whose thresholds apply, the signals already raised on that attempt
 * and the two plan-task figures the journal cannot derive — the predicted cost and the unplanned changes.
 * <p>
 * The provisions live in the workflow module and in the envelope's records; the assembly adapts them, so the runtime
 * never reads a queue record or an envelope itself.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface WorkerTaskActivity {

    /**
     * @param queueTaskId the queue task the worker holds
     * @return the task's facts, or empty when the task is unknown or closed
     */
    Optional<TaskActivity> facts(String queueTaskId);

    /**
     * The facts of one served task.
     *
     * @param attempt             the attempt the worker serves
     * @param role                the role of the task
     * @param alreadyRaised       the signals the task record holds for the attempt
     * @param predictedCostTokens the predicted cost of the current plan task; {@code null} when the task is not an
     *                            envelope task or nothing predicts it
     * @param unplannedChanges    the unplanned changed files of the current plan task; {@code -1} when the task is not
     *                            an envelope task
     * @since 0.1
     */
    record TaskActivity(int attempt, RoleArtifact role, Set<RoleArtifact.OffTrackSignal> alreadyRaised,
    @Nullable Double predictedCostTokens, int unplannedChanges) {
    }
}
