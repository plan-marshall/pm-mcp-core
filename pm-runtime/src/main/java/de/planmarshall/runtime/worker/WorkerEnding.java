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
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.core.store.WorkerStateCodec;
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
 *       {@link de.planmarshall.runtime.launch.JobTokenStore} and its model slot released, and the terminal
 *       {@link WorkerState} the settlement wrote with the job's fate is read from the record;</li>
 *   <li>the end is logged once, and a successor is started when work remains.</li>
 * </ol>
 * The runner derives the job's fate and records the ended worker's usage as unmeasured; the supervisor starts no
 * process and sends no signal itself.
 *
 * @since 0.1
 */
public final class WorkerEnding {

    private static final CuiLogger LOGGER = new CuiLogger(WorkerEnding.class);

    /** The name of the generation argument, as a null check reports it. */
    private static final String GENERATION_ARGUMENT = "generation";

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
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        Objects.requireNonNull(cause, "cause");
        if (requests.containsKey(generation.generationId())) {
            return;
        }
        requests.put(generation.generationId(), new Request(cause, cause.endReason(), false));
        var at = environment.clock().instant().plus(TerminationProtocol.GRACE_PERIOD);
        graces.put(generation.generationId(),
                environment.timers().schedule(at, () -> forceIfRequested(generation, cause)));
    }

    /**
     * Ends a detected-lost generation at once: an unresponsive worker is forced without waiting for an instructed end.
     *
     * @param generation the lost generation
     * @param signal     the detection that ended it
     */
    public synchronized void end(WorkerGeneration generation, LossSignal signal) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        Objects.requireNonNull(signal, "signal");
        if (requests.containsKey(generation.generationId())) {
            return;
        }
        requests.put(generation.generationId(), new Request(EndCause.LOST, signal.reason().endReason(), false));
        force(generation, EndCause.LOST);
    }

    /**
     * Forces the end of a generation at once, as the drain does after its timeout expired.
     *
     * @param generation the generation to end
     * @param cause      why it ends
     */
    public synchronized void forceEnd(WorkerGeneration generation, EndCause cause) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        Objects.requireNonNull(cause, "cause");
        requests.putIfAbsent(generation.generationId(), new Request(cause, cause.endReason(), false));
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
     * Completes the end after the worker's exit: fences, revokes the token and releases the slot once per generation,
     * reads the terminal state the settlement wrote, logs the end and starts a successor when work remains.
     * <p>
     * The settlement writes the terminal worker block with the fate, so this path never writes the worker state of a
     * settled record — the store refuses that write, and the refusal stays. A retry after a failed end repeats none of
     * the side effects: the generation's own request carries the mark that they ran, and the request stays available,
     * so the retry re-reads the cause instead of re-deriving it as a loss.
     *
     * @param generation the generation whose process exited
     * @return the generation in its terminal state
     */
    public synchronized WorkerGeneration exited(WorkerGeneration generation) {
        Objects.requireNonNull(generation, GENERATION_ARGUMENT);
        var generationId = generation.generationId();
        var request = requests.get(generationId);
        var cause = request != null ? request.cause() : null;
        var reason = request != null ? request.reason() : WorkerState.EndReason.EXITED;
        var at = environment.clock().instant();
        if (request == null || !request.completed()) {
            cancelGrace(generationId);
            environment.protocol().fence(generationId);
            environment.jobTokens().revoke(generationId);
            environment.slots().release(generation.slot());
            requests.put(generationId, request == null ? new Request(EndCause.LOST, reason, true)
                    : request.markCompleted());
        }
        var recorded = JobId.parse(generationId)
                .flatMap(jobId -> recordedTerminal(
                        JobRecordStore.ofProject(environment.base(), environment.projectId()), jobId));
        if (recorded.isEmpty()) {
            // A record the settlement has not written yet: the supervisor's own write is the only terminal state.
            var written = cause == null ? EndCause.LOST : cause;
            environment.stateWriter().write(generationId, written.terminalState(), at, reason,
                    written == EndCause.START_FAILURE);
        }
        LOGGER.info(INFO.WORKER_GENERATION_ENDED, generationId, generation.role(),
                endName(cause, recorded.orElse(null)));
        var terminal = terminal(generation, cause, recorded.orElse(null));
        if (workQueued.getAsBoolean()) {
            successor.run();
        }
        requests.remove(generationId);
        return terminal;
    }

    /**
     * Forwards the grace timer's force to the runner, but only while the generation's end is still requested: the timer
     * action runs on the timer thread, and the worker's exit may have completed the end in the meantime. The monitor is
     * taken so the check and the effect are one step against the methods that remove the request.
     *
     * @param generation the generation whose grace expired
     * @param cause      why it is being ended
     */
    private synchronized void forceIfRequested(WorkerGeneration generation, EndCause cause) {
        if (requests.containsKey(generation.generationId())) {
            force(generation, cause);
        }
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

    private static WorkerGeneration terminal(WorkerGeneration generation, @Nullable EndCause cause,
            @Nullable WorkerState recorded) {
        if (generation.lifecycle().terminal()) {
            return generation;
        }
        if (cause == null) {
            var state = recorded == null ? WorkerState.State.LOST : recorded.state();
            if (state == WorkerState.State.LOST) {
                return generation.advanceTo(WorkerLifecycle.LOST);
            }
            return recorded != null && recorded.startFailure()
                    ? generation.advanceTo(WorkerLifecycle.ENDING).advanceTo(WorkerLifecycle.START_FAILED)
                    : generation.advanceTo(WorkerLifecycle.ENDING).advanceTo(WorkerLifecycle.ENDED);
        }
        if (cause.terminalState() == WorkerState.State.LOST) {
            return generation.advanceTo(WorkerLifecycle.LOST);
        }
        if (cause == EndCause.START_FAILURE) {
            return generation.advanceTo(WorkerLifecycle.ENDING).advanceTo(WorkerLifecycle.START_FAILED);
        }
        var state = recorded != null ? recorded.state() : cause.terminalState();
        return state == WorkerState.State.LOST
                ? generation.advanceTo(WorkerLifecycle.LOST)
                : generation.advanceTo(WorkerLifecycle.ENDING).advanceTo(WorkerLifecycle.ENDED);
    }

    /**
     * @param cause    the requested end; {@code null} when the supervisor requested none
     * @param recorded the terminal state the settlement wrote; {@code null} when it wrote none
     * @return the end's name for the log line
     */
    private static String endName(@Nullable EndCause cause, @Nullable WorkerState recorded) {
        if (cause != null) {
            return cause.wireName();
        }
        var reason = recorded == null ? null : recorded.endReason();
        return reason == null ? EndCause.LOST.wireName() : reason.wireName();
    }

    /**
     * Reads the terminal worker state a settled record carries, which the settlement wrote with the fate.
     *
     * @param jobs  the job records of the generation's project
     * @param jobId the generation's job
     * @return the terminal worker state; empty when the record is absent, not settled or carries no worker block
     * @throws StoreRefusal         if the record or its worker block is unreadable
     * @throws UncheckedIOException if the record cannot be read
     */
    static Optional<WorkerState> recordedTerminal(JobRecordStore jobs, JobId jobId) {
        return jobs.read(jobId)
                .filter(job -> job.status().isSettled() && job.kind() == JobId.Kind.LLM && job.worker() != null)
                .map(job -> WorkerStateCodec.read(jobs.recordPath(jobId), job.worker()))
                .filter(state -> state.state().isTerminal());
    }

    /**
     * One requested end: its cause, the end reason the terminal state carries (the loss signal refines the reason) and
     * whether the end's side effects already ran, which is the generation's own terminal mark.
     *
     * @param cause     the cause
     * @param reason    the end reason
     * @param completed whether the side effects of the end ran
     */
    private record Request(EndCause cause, WorkerState.EndReason reason, boolean completed) {

        /** @return the request after the end's side effects ran */
        Request markCompleted() {
            return new Request(cause, reason, true);
        }
    }
}
