/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.engine.EngineEvent;

/**
 * The scripted fixture worker of one role in the replay harness {@code pm-scenario} (PM-TEST-4): it takes the queue
 * task the engine created for its role and settles it with the next task outcome the script declares for the role,
 * without a live model. The settlement is the {@link EngineEvent.TasksSettled} of the task state, with the outcome the
 * fixture return evaluation ({@link ScenarioFixtures#evaluate(String)}) gives the task.
 * <p>
 * The worker settles the task directly: it follows no links, holds no lease and has no generation; those arrive with
 * the task protocol. A task the worker has no declared outcome left for is a scenario failure naming the role, and so
 * is a declared outcome no task took ({@link #remaining()}).
 */
final class ScriptedFixtureWorker {

    private final String role;
    private final Deque<String> outcomes;
    private final List<String> settled = new ArrayList<>();

    /**
     * @param declaration the worker declaration of the script
     */
    ScriptedFixtureWorker(ScenarioDirective.Worker declaration) {
        Objects.requireNonNull(declaration, "declaration");
        this.role = declaration.role();
        this.outcomes = new ArrayDeque<>(declaration.outcomes());
    }

    /** @return the role the worker serves */
    String role() {
        return role;
    }

    /**
     * Settles the queue task of an entity with the next declared task outcome.
     *
     * @param entityKey the entity the task works on, the key of the task in {@code active_tasks}
     * @param task      the task as the status holds it
     * @return the settlement of the task state
     * @throws IllegalStateException if the task belongs to another role, the worker has no task outcome left, or the
     *                               fixture return evaluation does not evaluate the outcome; the message names the role
     */
    EngineEvent.TasksSettled settle(String entityKey, WorkflowStatus.TaskEntry task) {
        Objects.requireNonNull(entityKey, "entityKey");
        Objects.requireNonNull(task, "task");
        if (!role.equals(task.role())) {
            throw new IllegalStateException("the worker of role %s does not take the task %s of role %s"
                    .formatted(role, task.queueTaskId(), task.role()));
        }
        var outcome = outcomes.poll();
        if (outcome == null) {
            throw new IllegalStateException("the worker of role %s has no task outcome left for the task %s"
                    .formatted(role, task.queueTaskId()));
        }
        String evaluated;
        try {
            evaluated = ScenarioFixtures.evaluate(outcome);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("the worker of role %s: %s".formatted(role, e.getMessage()), e);
        }
        settled.add(outcome);
        return new EngineEvent.TasksSettled(Map.of(entityKey, outcome), evaluated);
    }

    /** @return the outcome of the last task the worker settled; empty before its first */
    Optional<String> lastOutcome() {
        return settled.isEmpty() ? Optional.empty() : Optional.of(settled.getLast());
    }

    /** @return the task outcomes the worker settled, in order */
    List<String> settled() {
        return List.copyOf(settled);
    }

    /** @return the declared task outcomes no task took yet */
    List<String> remaining() {
        return List.copyOf(outcomes);
    }
}
