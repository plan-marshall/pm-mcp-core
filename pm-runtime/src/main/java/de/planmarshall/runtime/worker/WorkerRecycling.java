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

import de.cuioss.tools.logging.CuiLogger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The recycling decision of a supervisor's warm workers (PM-WORK-3, Recycling Warm Workers): the four triggers the
 * specification names — idle, token budget, tasks per generation and a changed role configuration — and the idle yield
 * of a slot another role's queued task waits for.
 * <p>
 * A recycle ends the generation by the answer {@code end} to its next wait, never in the middle of a task: every
 * decision here hands the generation to {@link WorkerEnding} through {@link EndCause#RECYCLE}, so the one end path
 * fences, revokes, releases the slot, writes the terminal state and starts a successor when work remains. Each recycle
 * logs {@code WORKER_GENERATION_RECYCLED} once with its {@link RecycleCause}.
 * <p>
 * The decision reads the facts the supervisor records on the {@link WorkerGeneration} — its form, its lease, its idle
 * wake-ups, the context of its latest turn and the tasks it closed — and the recycling bounds of its role. A fresh
 * generation is never recycled: it ends after its one task. The token budget reads the context of the latest turn only
 * (never a run total) and recycles a generation only after it closed at least one task and only once it no longer
 * leases a task, unless it holds its task for an answer. The host's {@code worker.idle_wakeups} bounds the idle trigger
 * and the role's {@code recycling.idle_wakeups} can only lower it.
 *
 * @since 0.1
 */
public final class WorkerRecycling {

    private static final CuiLogger LOGGER = new CuiLogger(WorkerRecycling.class);

    /** The name of the generation argument, as a null check reports it. */
    private static final String GENERATION_ARGUMENT = "generation";

    private final SupervisorEnvironment environment;
    private final WorkerEnding ending;
    private final Supplier<List<WorkerGeneration>> generations;
    private final Function<String, Optional<RoleArtifact.Recycling>> roleBounds;
    private final WorkerParameters parameters;
    private final Set<String> recycled = new HashSet<>();

    /**
     * @param environment the collaborators of the project, read for the model slots and the start-failure guard
     * @param ending      the one end path a recycle runs through
     * @param generations the registered generations of the supervisor, read by the idle yield
     * @param roleBounds  the recycling bounds of one role; empty for a fresh role
     * @param parameters  the worker values of the harness's client profile, which bound the idle trigger
     * @throws NullPointerException if a collaborator is {@code null}
     */
    public WorkerRecycling(SupervisorEnvironment environment, WorkerEnding ending,
            Supplier<List<WorkerGeneration>> generations,
            Function<String, Optional<RoleArtifact.Recycling>> roleBounds, WorkerParameters parameters) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.ending = Objects.requireNonNull(ending, "ending");
        this.generations = Objects.requireNonNull(generations, "generations");
        this.roleBounds = Objects.requireNonNull(roleBounds, "roleBounds");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    /**
     * One {@code wait_again} answer and what it made of the generation.
     *
     * @param idleWakeups  the consecutive bounded waits the generation has ended without a task after this answer
     * @param recycleCause why the answer recycled the generation; empty when it did not
     * @since 0.1
     */
    public record WaitAgainResult(long idleWakeups, Optional<RecycleCause> recycleCause) {

        /**
         * @throws NullPointerException     if the cause is {@code null}
         * @throws IllegalArgumentException if the count is negative
         */
        public WaitAgainResult {
            Objects.requireNonNull(recycleCause, "recycleCause");
            if (idleWakeups < 0) {
                throw new IllegalArgumentException("'idleWakeups' must not be negative: " + idleWakeups);
            }
        }
    }

    /**
     * Records one {@code wait_again} answer. A generation holding a lease — a consulting worker or one holding for an
     * answer — is not idle: its answer counts no idle wake-up and recycles nothing. A warm generation that reached its
     * idle wake-ups is recycled.
     *
     * @param generation the generation the answer belongs to
     * @return the generation's idle wake-ups after the answer and the recycle it caused, when it caused one
     * @throws NullPointerException if the generation is {@code null}
     */
    public synchronized WaitAgainResult waitAgain(WorkerGeneration generation) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        if (generation.terminal() || generation.leasedTask() != null) {
            return new WaitAgainResult(generation.idleWakeups(), Optional.empty());
        }
        var idleWakeups = generation.idleWakeups() + 1;
        if (generation.form() != RoleForm.WARM || idleWakeups < idleWakeupsBound(generation.role())) {
            return new WaitAgainResult(idleWakeups, Optional.empty());
        }
        return new WaitAgainResult(idleWakeups, recycle(generation, RecycleCause.IDLE));
    }

    /**
     * Decides the token budget after a turn. The context of the generation's latest turn is the only size read; a
     * generation that still holds a leased task waits for that task to close, unless it holds it for an answer. A
     * generation that closed no task yet is recycled only while it holds its task for an answer, because the context a
     * worker starts with can exceed a small budget.
     *
     * @param generation the generation whose turn completed
     * @return why the turn recycled the generation; empty when it did not
     * @throws NullPointerException if the generation is {@code null}
     */
    public synchronized Optional<RecycleCause> afterTurnCompleted(WorkerGeneration generation) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        if (generation.terminal() || generation.form() != RoleForm.WARM) {
            return Optional.empty();
        }
        var bounds = bounds(generation.role());
        if (bounds.isEmpty()) {
            return Optional.empty();
        }
        var context = generation.contextTokens();
        if (context.unmeasured() || context.tokens() < bounds.get().tokenBudget()) {
            return Optional.empty();
        }
        if (generation.leasedTask() != null && generation.lifecycle() != WorkerLifecycle.HOLDING) {
            return Optional.empty();
        }
        if (generation.servedTasks().isEmpty() && generation.lifecycle() != WorkerLifecycle.HOLDING) {
            return Optional.empty();
        }
        return recycle(generation, RecycleCause.TOKEN_BUDGET);
    }

    /**
     * Decides the triggers a closed task settles: the role's tasks per generation, and a role configuration digest that
     * differs from the one the generation was started with. A changed configuration also clears the start-failure guard
     * of the role on its harness, whether or not the tasks-per-generation trigger recycles in the same call.
     *
     * @param generation the generation whose task closed
     * @param roleDigest the role's current configuration digest, as the wait carried it; {@code null} when the caller
     *                   knows none, which settles the configuration trigger as unchanged
     * @return why the closed task recycled the generation; empty when it did not
     * @throws NullPointerException if the generation is {@code null}
     */
    public synchronized Optional<RecycleCause> afterTaskClosed(WorkerGeneration generation,
            @Nullable String roleDigest) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        if (generation.terminal() || generation.form() != RoleForm.WARM) {
            return Optional.empty();
        }
        var configurationChanged = roleDigest != null && !roleDigest.equals(generation.configurationDigest());
        if (configurationChanged) {
            environment.startGuard().clear(generation.role(), generation.harness());
        }
        var bounds = bounds(generation.role());
        if (bounds.isPresent() && bounds.get().tasksPerGeneration() != null
                && generation.servedTasks().size() >= bounds.get().tasksPerGeneration()) {
            return recycle(generation, RecycleCause.TASKS_PER_GENERATION);
        }
        if (configurationChanged) {
            return recycle(generation, RecycleCause.CONFIGURATION_CHANGED);
        }
        return Optional.empty();
    }

    /**
     * Yields a model slot to a waiting role: when a task of another role waits for a slot, the longest-idle warm
     * generation that holds no lease is recycled, so idle warm workers never starve queued work.
     *
     * @return why the yield recycled a generation; empty when no other role waits or no idle warm generation holds no
     *         lease
     */
    public synchronized Optional<RecycleCause> yieldIdleSlot() {
        var waiting = environment.slots().nextWaitingRole();
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        var candidate = generations.get().stream()
                .filter(generation -> generation.form() == RoleForm.WARM)
                .filter(generation -> !generation.terminal())
                .filter(generation -> generation.lifecycle() == WorkerLifecycle.WAITING)
                .filter(generation -> generation.leasedTask() == null)
                .filter(generation -> !generation.role().equals(waiting.get()))
                .min(Comparator.comparingLong(WorkerGeneration::idleWakeups).reversed()
                        .thenComparing(WorkerGeneration::startedAt));
        return candidate.flatMap(generation -> recycle(generation, RecycleCause.IDLE_YIELD));
    }

    /**
     * Hands one generation to the one end path and logs the recycle once.
     *
     * @param generation the generation to recycle
     * @param cause      why it is recycled
     * @return the cause, or empty when this generation was already recycled
     */
    private Optional<RecycleCause> recycle(WorkerGeneration generation, RecycleCause cause) {
        if (!recycled.add(generation.generationId())) {
            return Optional.empty();
        }
        LOGGER.info(INFO.WORKER_GENERATION_RECYCLED, generation.generationId(), generation.role(), cause.wireName());
        ending.end(generation, cause.endCause());
        return Optional.of(cause);
    }

    /** @return the recycling bounds of the role, or empty for a fresh role */
    private Optional<RoleArtifact.Recycling> bounds(String role) {
        return roleBounds.apply(role);
    }

    /** @return the idle wake-ups that recycle a generation of the role: the host's value, lowered by the role's */
    private long idleWakeupsBound(String role) {
        var declared = bounds(role).map(RoleArtifact.Recycling::idleWakeups).orElse(null);
        return declared == null ? parameters.idleWakeups() : Math.min(parameters.idleWakeups(), declared);
    }
}
