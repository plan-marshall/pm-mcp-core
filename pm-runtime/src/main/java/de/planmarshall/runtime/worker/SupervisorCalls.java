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

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.WorkerState;

/**
 * The implementation of the supervisor's inbound API {@link WorkerCalls} (PM-WORK-3, PM-WORK-7): the tests each call
 * records, answered with the protocol's three wait answers.
 * <p>
 * The class resolves the generation from the supervisor's registry and ignores a call of a generation it does not know or
 * one that already ended, so a transport of a fenced, ended or unknown generation reaches no state. It owns the
 * per-generation {@link LossDetection}, created at the first sight of the generation with
 * {@link LossDetection#started(Instant)} and handed a {@link LossSignal} that ends the generation through the one end
 * path, {@link WorkerEnding#end(WorkerGeneration, LossSignal)}.
 * <p>
 * Every state change of a generation is written through {@link WorkerSupervisor#replaced(WorkerGeneration)} and its
 * {@link WorkerLifecycle} transition table, which refuses a transition the protocol does not have; a transition the
 * table does not have from the generation's current state leaves that state as it is, so a call that arrives while the
 * generation is already ending never throws at the transport.
 * <p>
 * Every method holds this instance's monitor. {@link SupervisionPass} takes the same monitor only to claim a
 * generation's end and to record its result; the end work itself — the fence, the token revoke, the slot release and
 * the read of the terminal state — runs outside it, so a call or a deadline is dispatched while an end is written. The
 * terminal lifecycle is registered onto the generation the registry holds when the result is recorded, so a call that
 * ran meanwhile — a closed task, a completed turn — keeps the facts it wrote.
 * <p>
 * The worker parameters and the role's class come from the caller, because the profile of a harness and the role set
 * belong to the assembly that builds this class: the parameters are the harness profile's {@code worker} and
 * {@code connection} block and the class is the one the role artifact declares.
 *
 * @since 0.1
 */
public final class SupervisorCalls implements WorkerCalls {

    /** The name of the queue-task argument, as a null check reports it. */
    private static final String QUEUE_TASK_ARGUMENT = "queueTaskId";

    /** The name of the generation argument, as a null check reports it. */
    private static final String GENERATION_ID_ARGUMENT = "generationId";

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
     * @param parameters  the worker values of the harness's harness profile
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
        Objects.requireNonNull(at, "at");
        var generation = known(generationId).orElse(null);
        if (generation == null) {
            return;
        }
        var detection = detection(generation);
        if (detection.firstCallMade()) {
            // A call by a worker that holds an unacknowledged offer or launch is that acknowledgement: the deadline
            // runs from the offer (or a fresh worker's first call) and only a call clears it. LossDetection.acknowledged
            // records the call as well.
            detection.acknowledged(at);
        } else {
            firstCall(generation, detection, at);
        }
        supervisor.replaced(acknowledging(generation));
    }

    @Override
    public synchronized WaitAnswer waitOpened(String generationId, String queueTaskId, String roleDigest) {
        Objects.requireNonNull(queueTaskId, QUEUE_TASK_ARGUMENT);
        Objects.requireNonNull(roleDigest, "roleDigest");
        var generation = known(generationId).orElse(null);
        if (generation == null) {
            return new WaitAnswer.End();
        }
        var at = supervisor.environment().clock().instant();
        var detection = detection(generation);
        if (!detection.firstCallMade()) {
            // Every call is a heartbeat, and a wait is a call: a generation whose first call is its wait owes the same
            // first-call record and guard clear as one that began with another call.
            firstCall(generation, detection, at);
        }
        detection.waitOpened(at);
        if (generation.lifecycle() == WorkerLifecycle.HOLDING) {
            // A hold keeps its task and its wait: a bounded wait of a holding generation is answered wait again, so it
            // is never offered a second task and its hold stays closable by the task it names. An end already requested
            // is told to the transport, which is the only way the worker leaves gracefully from a hold.
            if (ending.endRequested(generationId)) {
                detection.waitClosed(at);
                return new WaitAnswer.End();
            }
            return new WaitAnswer.WaitAgain();
        }
        try {
            return answerTo(generation, queueTaskId, roleDigest, at, detection);
        } finally {
            // The answer closes the wait: a held wait suppresses the silence signal, and the answer re-arms it.
            detection.waitClosed(at);
        }
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
            var next = generation.leasedTask() == null ? waiting(generation) : generation;
            supervisor.replaced(next.withIdleWakeups(result.idleWakeups()));
            return result.recycleCause() != null ? new WaitAnswer.End() : new WaitAnswer.WaitAgain();
        }
        roleDigests.put(generationId, roleDigest);
        detection.offered(queueTaskId, at);
        supervisor.replaced(offered(generation, queueTaskId));
        return new WaitAnswer.Offered(queueTaskId, roleDigest);
    }

    @Override
    public synchronized void turnCompleted(String generationId, long contextTokens) {
        var generation = known(generationId).orElse(null);
        if (generation == null) {
            return;
        }
        var updated = generation.withContextTokens(WorkerState.ContextTokens.measured(contextTokens));
        // A turn reported while the generation holds leaves the hold as it is: the recycling decides the hold's budget
        // on that turn, and the generation holds until its hold ends or the end path runs.
        var next = generation.lifecycle() == WorkerLifecycle.HOLDING ? updated : executing(updated);
        supervisor.replaced(next);
        recycling.afterTurnCompleted(next);
    }

    @Override
    public synchronized void taskClosed(String generationId, String queueTaskId) {
        Objects.requireNonNull(queueTaskId, QUEUE_TASK_ARGUMENT);
        var generation = known(generationId).orElse(null);
        if (generation == null) {
            return;
        }
        var at = supervisor.environment().clock().instant();
        var closed = generation.withServedTasks(served(generation.servedTasks(), queueTaskId)).withLeasedTask(null);
        var next = waiting(closed);
        if (generation.lifecycle() == WorkerLifecycle.HOLDING) {
            // A closed task ends the hold as well: its wait closes, so the silence limit counts again from here.
            detection(generation).waitClosed(at);
        }
        supervisor.replaced(next);
        recycling.afterTaskClosed(next, roleDigests.get(generationId));
    }

    @Override
    public synchronized void holdOpened(String generationId, String queueTaskId, Instant at) {
        Objects.requireNonNull(queueTaskId, QUEUE_TASK_ARGUMENT);
        Objects.requireNonNull(at, "at");
        var generation = known(generationId).orElse(null);
        if (generation == null || !namesTheHeldTask(generation, queueTaskId)
                || !mayAdvance(generation, WorkerLifecycle.HOLDING)) {
            return;
        }
        var holding = generation.advanceTo(WorkerLifecycle.HOLDING).withLeasedTask(queueTaskId);
        var detection = detection(generation);
        detection.waitOpened(at);
        // A worker that asks has received its task: the hold is the acknowledgement of the offer, so the offer's
        // acknowledgement deadline must not fire for a generation that holds.
        detection.acknowledged(at);
        supervisor.replaced(holding);
    }

    @Override
    public synchronized void holdClosed(String generationId, String queueTaskId, Instant at) {
        Objects.requireNonNull(queueTaskId, QUEUE_TASK_ARGUMENT);
        Objects.requireNonNull(at, "at");
        var generation = known(generationId).orElse(null);
        if (generation == null || generation.lifecycle() != WorkerLifecycle.HOLDING
                || !namesTheHeldTask(generation, queueTaskId)) {
            return;
        }
        var executing = generation.advanceTo(WorkerLifecycle.EXECUTING);
        detection(generation).waitClosed(at);
        supervisor.replaced(executing);
    }

    @Override
    public synchronized void answerDelivered(String generationId, Instant at) {
        Objects.requireNonNull(at, "at");
        var generation = known(generationId).orElse(null);
        if (generation != null) {
            var detection = detection(generation);
            // The answer ends the wait of the hold: the silence limit counts again from the answer, whichever way the
            // host's acknowledgement is configured (ADR runtime-worker/012).
            detection.waitClosed(at);
            detection.answerDelivered(at);
        }
    }

    @Override
    public synchronized void planPaused(String planId) {
        Objects.requireNonNull(planId, "planId");
        for (var generation : supervisor.generations()) {
            if (!generation.terminal() && planId.equals(generation.planId())) {
                ending.forceEnd(generation, EndCause.PAUSE_AT_ONCE);
            }
        }
    }

    @Override
    public synchronized void harnessEnrolled(String harnessId) {
        Objects.requireNonNull(harnessId, "harnessId");
        var guard = supervisor.environment().startGuard();
        for (var role : rolesOf(harnessId)) {
            guard.clear(role, harnessId);
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
     * @throws NullPointerException  if the generation is {@code null}
     * @throws IllegalStateException if the generation's role has no class, so no detection can be built for it; the
     *                               supervision pass reports it as that generation's failure
     */
    public synchronized LossDetection detection(WorkerGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        var generationId = generation.generationId();
        return detections.computeIfAbsent(generationId, ignored -> {
            var environment = supervisor.environment();
            var roleClass = roleClasses.apply(generation.role());
            if (roleClass == null) {
                throw new IllegalStateException("The role '%s' has no class".formatted(generation.role()));
            }
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
        Objects.requireNonNull(generationId, GENERATION_ID_ARGUMENT);
        return Optional.ofNullable(losses.get(generationId));
    }

    /**
     * Keeps a loss the pass derived from the settlement for the generation's later steps: the exit the pass observed is
     * the attempt's end on a retry as well, which the detection can no longer answer once it settled.
     *
     * @param generationId the worker's generation
     * @param signal       the loss
     */
    public synchronized void remember(String generationId, LossSignal signal) {
        Objects.requireNonNull(generationId, GENERATION_ID_ARGUMENT);
        Objects.requireNonNull(signal, "signal");
        losses.put(generationId, signal);
    }

    /**
     * Forgets every value the calls keep for one generation, once its end is complete: its detection, its loss and the
     * configuration digest its last wait carried.
     *
     * @param generationId the worker's generation
     */
    public synchronized void forget(String generationId) {
        Objects.requireNonNull(generationId, GENERATION_ID_ARGUMENT);
        var detection = detections.remove(generationId);
        if (detection != null) {
            detection.cancelled();
        }
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
        known(generationId).ifPresent(generation -> {
            losses.put(generationId, signal);
            ending.end(generation, signal);
        });
    }

    /**
     * Records the generation's first call, which clears the start-failure guard of the role on the harness, and settles
     * the acknowledgement deadline the call armed where the worker owes none: the specification scopes that deadline to
     * a fresh worker's first call and to an offer, so a warm worker's first call clears what {@code firstCall} armed.
     *
     * @param generation the generation whose first call this is
     * @param detection  its loss detection
     * @param at         the instant of the call
     */
    private void firstCall(WorkerGeneration generation, LossDetection detection, Instant at) {
        detection.firstCall(at);
        supervisor.environment().startGuard().firstCall(generation.role(), generation.harness());
        if (generation.form() != RoleForm.FRESH) {
            detection.acknowledged(at);
        }
    }

    /**
     * @param generationId the worker's generation
     * @return the registered generation, empty when this supervisor started none of that id or it already ended
     */
    private Optional<WorkerGeneration> known(String generationId) {
        return supervisor.generation(generationId).filter(generation -> !generation.terminal());
    }

    private List<String> rolesOf(String harnessId) {
        var roles = new LinkedHashSet<String>();
        for (var generation : supervisor.generations()) {
            if (generation.harness().equals(harnessId)) {
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

    /**
     * @param generation   the generation
     * @param queueTaskId  the task the call names
     * @return whether the call names the task the generation leases, or a generation that leases none
     */
    private static boolean namesTheHeldTask(WorkerGeneration generation, String queueTaskId) {
        var leased = generation.leasedTask();
        return leased == null || leased.equals(queueTaskId);
    }

    private static List<String> served(List<String> servedTasks, String queueTaskId) {
        var tasks = new ArrayList<>(servedTasks);
        if (!tasks.contains(queueTaskId)) {
            tasks.add(queueTaskId);
        }
        return List.copyOf(tasks);
    }
}
