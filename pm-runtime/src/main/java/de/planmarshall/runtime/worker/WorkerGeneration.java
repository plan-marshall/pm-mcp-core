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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.With;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.launch.LaunchRequest;

/**
 * One worker generation of a supervisor (PM-WORK-3, Supervision &amp; Recycling): the immutable facts it was started
 * with and the state the supervision advances as it serves tasks.
 * <p>
 * The value is a snapshot, not a mutable holder: {@link WorkerLifecycle#transitionTo(WorkerLifecycle)} checks the
 * transition and the supervisor writes the successor value through the {@code @With} methods, so a caller never
 * observes half a transition. Equality is by {@link #generationId()} alone — two snapshots of the same generation are
 * the same generation, whatever state each of them records.
 *
 * @param generationId        the worker's generation, equal to its job id
 * @param role                the role the worker serves
 * @param harness             the client id of the worker's harness
 * @param model               the model the launch passes to the harness
 * @param form                the form of the role's workers
 * @param workdirKind         the kind of the working directory fixed at the launch
 * @param planId              the plan of a plan-worktree launch; {@code null} otherwise
 * @param workdir             the working directory fixed at the launch
 * @param configurationDigest the role's configuration digest the generation was started with
 * @param startedAt           the spawn instant
 * @param jobId               the job record that carries the worker state
 * @param slot                the model slot the generation holds for its whole life
 * @param lifecycle           the supervisor's lifecycle state
 * @param leasedTask          the queue task leased to the generation; {@code null} while it holds none
 * @param servedTasks         the queue tasks the generation closed, in order
 * @param idleWakeups         the consecutive bounded waits that ended without a task
 * @param contextTokens       the context size of the last turn, or explicitly unmeasured
 * @param worktreeMark        the working directory's state at the latest close; {@code null} before any close
 * @since 0.1
 */
@With
public record WorkerGeneration(String generationId, String role, String harness, String model, RoleForm form,
LaunchRequest.WorkdirKind workdirKind, @Nullable String planId, Path workdir, String configurationDigest,
Instant startedAt, JobId jobId, ModelSlots.Lease slot, WorkerLifecycle lifecycle, @Nullable String leasedTask,
List<String> servedTasks, long idleWakeups, WorkerState.ContextTokens contextTokens,
WorkerState.@Nullable WorktreeMark worktreeMark) {

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the working-directory pair is inconsistent or the idle wake-ups are negative
     */
    public WorkerGeneration {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(workdirKind, "workdirKind");
        Objects.requireNonNull(workdir, "workdir");
        Objects.requireNonNull(configurationDigest, "configurationDigest");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(lifecycle, "lifecycle");
        servedTasks = List.copyOf(Objects.requireNonNull(servedTasks, "servedTasks"));
        Objects.requireNonNull(contextTokens, "contextTokens");
        if ((workdirKind == LaunchRequest.WorkdirKind.PLAN_WORKTREE) != (planId != null)) {
            throw new IllegalArgumentException("Only a plan-worktree generation names its plan");
        }
        if (idleWakeups < 0) {
            throw new IllegalArgumentException("'idleWakeups' must not be negative: " + idleWakeups);
        }
    }

    /** @return whether the generation reached a terminal lifecycle state */
    public boolean terminal() {
        return lifecycle.terminal();
    }

    /** @return the generation in the next lifecycle state */
    public WorkerGeneration advanceTo(WorkerLifecycle next) {
        return withLifecycle(lifecycle.transitionTo(next));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkerGeneration generation && generationId.equals(generation.generationId);
    }

    @Override
    public int hashCode() {
        return generationId.hashCode();
    }

    @Override
    public String toString() {
        return "WorkerGeneration[generationId=%s, role=%s, lifecycle=%s]".formatted(generationId, role, lifecycle);
    }
}
