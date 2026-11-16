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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.store.WorkerState;

/**
 * The implementation of the supervisor's inbound API {@link WorkerCalls} (PM-WORK-3, PM-WORK-7): the tests each call
 * records, answered with the protocol's three wait answers.
 * <p>
 * The class resolves the generation from the supervisor's registry and ignores a call of a generation it does not know,
 * so a transport of a fenced or ended generation reaches no state. It owns the per-generation {@link LossDetection},
 * created at the first sight of the generation with {@link LossDetection#started(Instant)} and handed a
 * {@link LossSignal} that ends the generation through the one end path, {@link WorkerEnding#end(WorkerGeneration,
 * LossSignal)}.
 * <p>
 * Every state change of a generation is written through {@link WorkerSupervisor#replaced(WorkerGeneration)} and its
 * {@link WorkerLifecycle} transition table, which refuses a transition the protocol does not have; a transition the
 * table does not have from the generation's current state leaves that state as it is, so a call that arrives while the
 * generation is already ending never throws at the transport.
 * <p>
 * The worker parameters and the role's class come from the caller, because the profile of a harness and the role set
 * belong to the assembly that builds this class: the parameters are the harness profile's {@code worker} and
 * {@code connection} block and the class is the one the role artifact declares.
 *
 * @since 0.1
 */
public final class SupervisorCalls implements WorkerCalls {

    private final WorkerSupervisor supervisor;
    private final WorkerEnding ending;
    private final WorkerRecycling recycling;
    private final WorkerParameters parameters;
    private final Function<String, RoleClass> roleClasses;
    private final Map<String, LossDetection> detections = new HashMap<>();
    private final Map<String, LossSignal> losses = new HashMap<>();
    private final Map<String, String> roleDigests = new HashMap<>();

    /**
     * @param supervisor  the supervisor whose registry every call resolves its generation from
     * @param ending      the one end path of a generation
     * @param recycling   the recycling decisions of a wait and a closed task
     * @param parameters  the worker values of the harness's client profile
     * @param roleClasses the class of a role, by its name
     * @throws NullPointerException if an argument is {@code null}
     */
    public SupervisorCalls(WorkerSupervisor supervisor, WorkerEnding ending, WorkerRecycling recycling,
            WorkerParameters parameters, Function<String, RoleClass> roleClasses) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.ending = Objects.requireNonNull(ending, "ending");
        this.recycling = Objects.requireNonNull(recycling, "recycling");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.roleClasses = Objects.requireNonNull(roleClasses, "roleClasses");
    }

    @Override
    public synchronized void callHeartbeat(String generationId, Instant at) {
        var generation = known(generationId);
        if (generation == null) {
            return;
        }
        var detection = detection(generation);
        if (detection.firstCallMade()) {
            detection.called(at);
        } else {
            detection.firstCall(at);
            supervisor.environment().startGuard().firstCall(generation.role(), generation.harness());
        }
        supervisor.replaced(acknowledging(generation));
    }

    @Override
    public synchronized WaitAnswer waitOpened(String generationId, String queueTaskId, String roleDigest) {
        var generation = known(generationId);
        if (generation == null) {
            return new WaitAnswer.End();
        }
        var at = supervisor.environment().clock().instant();
        var detection = detection(generation);
        detection.waitOpened(at);
        var answer = answerTo(generation, queueTaskId, roleDigest, at, detection);
        detection.waitClosed(at);
        return answer;
    }

    /**
     * Decides the answer to an opened wait and records what it means for the generation: an end already requested ends
     * it without a lease, a named task is offered and leased, and no task counts an idle wake-up through the recycling,
     * which may recycle the generation in the same answer.
     *
     * @param generation  the generation of the wait
     * @param queueTaskId the candidate task the transport names; blank when it has none
     * @param roleDigest  the role's current configuration digest
     * @param at          the instant of the answer
     * @param detection   the generation's loss detection
     * @return the answer the transport receives
     */
    private WaitAnswer answerTo(WorkerGeneration generation, String queueTaskId, String roleDigest, Instant at,
            LossDetection detection) {
        var generationId = generation.generationId();
        if (ending.endRequested(generationId)) {
            return new WaitAnswer.End();
        }
        if (queueTaskId.isBlank()) {
            var result = recycling.waitAgain(generation);
            if (result.recycleCause() != null) {
                return new WaitAnswer.End();
            }
            supervisor.replaced(waiting(generation).withIdleWakeups(result.idleWakeups()));
            return new WaitAnswer.WaitAgain();
        }
        roleDigests.put(generationId, roleDigest);
        detection.offered(queueTaskId, at);
        supervisor.replaced(offered(generation, queueTaskId));
        return new WaitAnswer.Offered(queueTaskId, roleDigest);
    }

    @Override
    public synchronized void turnCompleted(String generationId, long contextTokens) {
        var generation = known(generationId);
        if (generation == null) {
            return;
        }
        var updated = executing(generation.withContextTokens(WorkerState.ContextTokens.measured(contextTokens)));
        supervisor.replaced(updated);
        recycling.afterTurnCompleted(updated);
    }

    @Override
    public synchronized void taskClosed(String generationId, String queueTaskId) {
        var generation = known(generationId);
        if (generation == null) {
            return;
        }
        var closed = generation.withServedTasks(served(generation.servedTasks(), queueTaskId)).withLeasedTask(null);
        var next = waiting(closed);
        supervisor.replaced(next);
        recycling.afterTaskClosed(next, roleDigests.get(generationId));
    }

    @Override
    public synchronized void planPaused(String planId) {
        for (var generation : supervisor.generations()) {
            if (!generation.terminal() && planId.equals(generation.planId())) {
                ending.forceEnd(generation, EndCause.PAUSE_AT_ONCE);
            }
        }
    }

    @Override
    public synchronized void harnessEnrolled(String clientId) {
        var guard = supervisor.environment().startGuard();
        for (var role : rolesOf(clientId)) {
            guard.clear(role, clientId);
        }
    }

    /**
     * The generation's loss detection, created at the first sight of the generation: armed from its spawn instant,
     * constructed with the supervisor's clock and timers, the class of its role and the profile's worker values, and
     * handed the one end path as the sink of its signal. The supervision pass reads the same detection, so a deadline
     * and an observed exit decide one generation together.
     *
     * @param generation the generation
     * @return its detection, the same one for every later call
     * @throws NullPointerException if the generation is {@code null}
     */
    public synchronized LossDetection detection(WorkerGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        var generationId = generation.generationId();
        return detections.computeIfAbsent(generationId, ignored -> {
            var environment = supervisor.environment();
            var roleClass = Objects.requireNonNull(roleClasses.apply(generation.role()),
                    () -> "The role '%s' has no class".formatted(generation.role()));
            var detection = new LossDetection(generationId, roleClass, parameters, environment.clock(),
                    environment.timers(), signal -> lost(generationId, signal));
            detection.started(generation.startedAt());
            return detection;
        });
    }

    /**
     * @param generationId the worker's generation
     * @return the loss the generation's detection raised, whether a deadline fired it or the pass observed the exit;
     *         empty while the generation produced none
     */
    public synchronized Optional<LossSignal> loss(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return Optional.ofNullable(losses.get(generationId));
    }

    /**
     * Forgets every value the calls keep for one generation, once its end is complete: its detection, its loss and the
     * configuration digest its last wait carried.
     *
     * @param generationId the worker's generation
     */
    public synchronized void forget(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        detections.remove(generationId);
        losses.remove(generationId);
        roleDigests.remove(generationId);
    }

    /**
     * Hands the detection's signal to the one end path and keeps it for the supervision pass, which names the
     * attempt's end on it. Runs on the timer thread, outside the detector's monitor.
     *
     * @param generationId the worker's generation
     * @param signal       the detection that settled the generation
     */
    private synchronized void lost(String generationId, LossSignal signal) {
        losses.put(generationId, signal);
        supervisor.generation(generationId).ifPresent(generation -> ending.end(generation, signal));
    }

    private @Nullable WorkerGeneration known(String generationId) {
        return supervisor.generation(generationId).orElse(null);
    }

    private List<String> rolesOf(String clientId) {
        var roles = new LinkedHashSet<String>();
        for (var generation : supervisor.generations()) {
            if (generation.harness().equals(clientId)) {
                roles.add(generation.role());
            }
        }
        return List.copyOf(roles);
    }

    /** @return the generation after its first call: {@code STARTING} settles as waiting */
    private static WorkerGeneration acknowledging(WorkerGeneration generation) {
        return generation.lifecycle() == WorkerLifecycle.STARTING ? generation.advanceTo(WorkerLifecycle.WAITING)
                : generation;
    }

    /** @return the generation that holds the offered task */
    private static WorkerGeneration offered(WorkerGeneration generation, String queueTaskId) {
        var next = generation.lifecycle() == WorkerLifecycle.STARTING ? generation.advanceTo(WorkerLifecycle.WAITING)
                : generation;
        var leased = next.withLeasedTask(queueTaskId);
        return mayAdvance(next, WorkerLifecycle.OFFERED) ? leased.advanceTo(WorkerLifecycle.OFFERED) : leased;
    }

    /** @return the generation after a completed turn: an acknowledged offer executes */
    private static WorkerGeneration executing(WorkerGeneration generation) {
        var next = generation;
        if (next.lifecycle() == WorkerLifecycle.OFFERED) {
            next = next.advanceTo(WorkerLifecycle.ACKNOWLEDGED);
        }
        return mayAdvance(next, WorkerLifecycle.EXECUTING) ? next.advanceTo(WorkerLifecycle.EXECUTING) : next;
    }

    /** @return the generation waiting again, holding no task */
    private static WorkerGeneration waiting(WorkerGeneration generation) {
        var next = generation.lifecycle() == WorkerLifecycle.HOLDING ? generation.advanceTo(WorkerLifecycle.EXECUTING)
                : generation;
        return mayAdvance(next, WorkerLifecycle.WAITING) ? next.advanceTo(WorkerLifecycle.WAITING) : next;
    }

    private static boolean mayAdvance(WorkerGeneration generation, WorkerLifecycle next) {
        return generation.lifecycle().canTransitionTo(next);
    }

    private static List<String> served(List<String> servedTasks, String queueTaskId) {
        var tasks = new ArrayList<>(servedTasks);
        if (!tasks.contains(queueTaskId)) {
            tasks.add(queueTaskId);
        }
        return List.copyOf(tasks);
    }
}
