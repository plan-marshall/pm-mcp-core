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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import de.cuioss.tools.logging.CuiLogger;

import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.job.TerminationProtocol;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The one end path of a worker generation (PM-WORK-3, Ending a Worker). Every cause — a closed task, a recycle, the
 * drain, a pause at once, a loss, an off-track restart or a start failure — runs through it, so the order the
 * specification requires holds once, here:
 * <ol>
 *   <li>the end is requested, so the transport answers the worker's next wait with {@code end};</li>
 *   <li>when the worker does not exit within the termination grace, the force reaches the process only through
 *       {@link SupervisorEnvironment.WorkerStarter#cancel}, which is the job runtime's termination protocol;</li>
 *   <li>after the exit, the generation is fenced through {@link WorkerProtocol}, its job token revoked through
 *       {@link de.planmarshall.runtime.launch.JobTokenStore}, its model slot released and its terminal
 *       {@link WorkerState} written;</li>
 *   <li>the end is logged once, and a successor is started when work remains.</li>
 * </ol>
 * The runner derives the job's fate and records the ended worker's usage as unmeasured; the supervisor starts no
 * process and sends no signal itself.
 *
 * @since 0.1
 */
public final class WorkerEnding {

    private static final CuiLogger LOGGER = new CuiLogger(WorkerEnding.class);

    private final SupervisorEnvironment environment;
    private final BooleanSupplier workQueued;
    private final Runnable successor;
    private final Map<String, Request> requests = new HashMap<>();
    private final Map<String, WorkerTimers.Cancellable> graces = new HashMap<>();

    /**
     * @param environment the collaborators of the project
     * @param workQueued  whether a task of the role is queued, so a successor is started after the end
     * @param successor   starts the successor generation
     * @throws NullPointerException if a collaborator is {@code null}
     */
    public WorkerEnding(SupervisorEnvironment environment, BooleanSupplier workQueued, Runnable successor) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.workQueued = Objects.requireNonNull(workQueued, "workQueued");
        this.successor = Objects.requireNonNull(successor, "successor");
    }

    /**
     * Requests the instructed end of a generation and forces it when the worker does not exit within the termination
     * grace.
     *
     * @param generation the generation to end
     * @param cause      why it ends
     */
    public synchronized void end(WorkerGeneration generation, EndCause cause) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(cause, "cause");
        if (requests.containsKey(generation.generationId())) {
            return;
        }
        requests.put(generation.generationId(), new Request(cause, cause.endReason()));
        var at = environment.clock().instant().plus(TerminationProtocol.GRACE_PERIOD);
        graces.put(generation.generationId(), environment.timers().schedule(at, () -> force(generation, cause)));
    }

    /**
     * Ends a detected-lost generation at once: an unresponsive worker is forced without waiting for an instructed end.
     *
     * @param generation the lost generation
     * @param signal     the detection that ended it
     */
    public synchronized void end(WorkerGeneration generation, LossSignal signal) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(signal, "signal");
        if (requests.containsKey(generation.generationId())) {
            return;
        }
        requests.put(generation.generationId(), new Request(EndCause.LOST, signal.reason().endReason()));
        force(generation, EndCause.LOST);
    }

    /**
     * Forces the end of a generation at once, as the drain does after its timeout expired.
     *
     * @param generation the generation to end
     * @param cause      why it ends
     */
    public synchronized void forceEnd(WorkerGeneration generation, EndCause cause) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(cause, "cause");
        requests.putIfAbsent(generation.generationId(), new Request(cause, cause.endReason()));
        force(generation, cause);
    }

    /**
     * @param generationId the worker's generation
     * @return whether the generation's next wait is answered with {@code end}
     */
    public synchronized boolean endRequested(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return requests.containsKey(generationId);
    }

    /**
     * @param generationId the worker's generation
     * @return why the generation is being ended, while an end is requested
     */
    public synchronized Optional<EndCause> requestedCause(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return Optional.ofNullable(requests.get(generationId)).map(Request::cause);
    }

    /**
     * Completes the end after the worker's exit: fences, revokes the token, releases the slot, writes the terminal
     * state, logs the end and starts a successor when work remains.
     *
     * @param generation the generation whose process exited
     * @return the generation in its terminal state
     */
    public synchronized WorkerGeneration exited(WorkerGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        var generationId = generation.generationId();
        cancelGrace(generationId);
        var request = requests.remove(generationId);
        var cause = request != null ? request.cause() : EndCause.LOST;
        var reason = request != null ? request.reason() : WorkerState.EndReason.EXITED;
        var at = environment.clock().instant();
        environment.protocol().fence(generationId);
        environment.jobTokens().revoke(generationId);
        environment.slots().release(generation.slot());
        environment.stateWriter().write(generationId, cause.terminalState(), at, reason,
                cause == EndCause.START_FAILURE);
        LOGGER.info(INFO.WORKER_GENERATION_ENDED, generationId, generation.role(), cause.wireName());
        var terminal = terminal(generation, cause);
        if (workQueued.getAsBoolean()) {
            successor.run();
        }
        return terminal;
    }

    private void force(WorkerGeneration generation, EndCause cause) {
        cancelGrace(generation.generationId());
        environment.starter().cancel(generation.jobId(), cause.cancelCause());
    }

    private void cancelGrace(String generationId) {
        var grace = graces.remove(generationId);
        if (grace != null) {
            grace.cancel();
        }
    }

    private static WorkerGeneration terminal(WorkerGeneration generation, EndCause cause) {
        if (generation.lifecycle().terminal()) {
            return generation;
        }
        if (cause.terminalState() == WorkerState.State.LOST) {
            return generation.advanceTo(WorkerLifecycle.LOST);
        }
        return generation.advanceTo(WorkerLifecycle.ENDING).advanceTo(WorkerLifecycle.ENDED);
    }

    /**
     * One requested end: its cause and the end reason the terminal state carries (the loss signal refines the reason).
     *
     * @param cause  the cause
     * @param reason the end reason
     */
    private record Request(EndCause cause, WorkerState.EndReason reason) {
    }
}
