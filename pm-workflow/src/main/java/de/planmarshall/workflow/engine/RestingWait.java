/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * What a resting state waits for, derived from the persisted status alone (PM-ARCH-4: a resumed engine reads the wait
 * from the store, never from memory; PM-WF-2: waiting on a job is an engine event).
 * <p>
 * The wait names the event kind ({@link AwaitedEvent}) and, where the status binds them, the jobs and the task
 * entities of the state: {@code active_job} and {@code active_jobs} for a job or composite job state,
 * {@code active_tasks} for a task state. An event that does not match the wait is refused, and the status stays as
 * it is ({@link #accepts(EngineEvent)}). A question state waits for the resolution of its record, which the engine
 * reads through the question port; a link state waits for a model's link and accepts no engine event.
 *
 * @param stateId      the resting state
 * @param awaitedEvent the event kind the state waits for
 * @param jobIds       the jobs bound to the state; empty when none is bound
 * @param taskEntities the entities of the state's queue tasks; empty when it has none
 * @since 0.1
 */
public record RestingWait(String stateId, AwaitedEvent awaitedEvent, Set<String> jobIds, Set<String> taskEntities) {

    /**
     * Copies the jobs and entities into unmodifiable sets.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public RestingWait {
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(awaitedEvent, "awaitedEvent");
        jobIds = Set.copyOf(jobIds);
        taskEntities = Set.copyOf(taskEntities);
    }

    /**
     * Derives the wait of the current state from the status.
     *
     * @param status       the status of a scope resting in the state
     * @param awaitedEvent the event kind the state waits for
     * @return the wait
     */
    static RestingWait of(WorkflowStatus status, AwaitedEvent awaitedEvent) {
        var jobs = new LinkedHashSet<String>();
        status.activeJobBinding().filter(binding -> binding.stateId().equals(status.state()))
                .ifPresent(binding -> jobs.add(binding.jobId()));
        status.activeJobs().values().forEach(job -> jobs.add(job.jobId()));
        return new RestingWait(status.state(), awaitedEvent, jobs, status.activeTasks().keySet());
    }

    /**
     * @param event an event
     * @return whether the event is the one the state waits for: a settled job bound to the state, the settlement of
     *         exactly the state's tasks, a question resolution for a question state, the passing of time for a state
     *         that waits for time
     */
    public boolean accepts(EngineEvent event) {
        return switch (Objects.requireNonNull(event, "event")) {
            case EngineEvent.JobSettled(var jobId, var _) ->
                awaitedEvent == AwaitedEvent.JOB_SETTLED && jobIds.contains(jobId);
            case EngineEvent.TasksSettled(var outcomes, var _) ->
                awaitedEvent == AwaitedEvent.TASK_SETTLED && outcomes.keySet().equals(taskEntities);
            case EngineEvent.QuestionResolved _ -> awaitedEvent == AwaitedEvent.QUESTION_ANSWER;
            case EngineEvent.TimeElapsed _ -> awaitedEvent == AwaitedEvent.TIME;
        };
    }
}
