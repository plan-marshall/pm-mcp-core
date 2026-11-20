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
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.core.store.WorkerState.EndReason;
import de.planmarshall.runtime.job.JobLog;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * One periodic step of the supervision (PM-WORK-3, PM-WORK-7): the part of the loop that reads the world and settles
 * what it finds, as a function the assembly schedules.
 * <p>
 * The class starts no thread and owns no timer: the deadlines of {@link LossDetection} keep running on
 * {@link WorkerTimers}, which already owns the supervisor's one scheduler thread, so {@link #run()} is a step a caller
 * drives from wherever its scheduling belongs. It takes one reading of the clock and, for every generation that is not
 * terminal, creates the generation's loss detection — so a worker that never calls is lost at the deadline of "silent
 * from spawn", not at the runner's budget — and reads the generation's job record from the store the terminal-state
 * writer writes. A job the record reports as ended settles its detection with {@link LossDetection#exit(Instant)} and
 * completes the end through {@link WorkerEnding#exited(WorkerGeneration)}, a loss going through
 * {@link WorkerEnding#end(WorkerGeneration, LossSignal)} first. A generation that holds a task reads its job log from
 * the offset its pump last read, hands the complete lines to that generation's {@link WorkerActivityPump} and runs
 * {@link AgentStartedDetector#detect(WorkerGeneration, long, AgentStartedDetector.HeldTask)} with the PID of the job
 * record. Once per pass the recycling yields an idle slot to a role that waits for one.
 * <p>
 * A pass that finds nothing to do reads the job records and their logs and writes nothing; a log that is unreadable or
 * truncated appends nothing and is read again at the next pass (ADR {@code runtime-worker/011}). Every state change of
 * a generation is written through {@link WorkerSupervisor#replaced(WorkerGeneration)} and its {@link WorkerLifecycle}
 * transition table.
 * <p>
 * The monitor of {@link SupervisorCalls} is taken only to claim a generation's end and to record its result; the end
 * work itself — the fence, the token revoke, the slot release and the read of the terminal state — runs outside it, so
 * a deadline that fires on the supervisor's one timer thread while an end is written is dispatched instead of waiting
 * for the store ({@code SupervisorCalls}). A failure of one generation's step is reported as that generation's failure
 * and the pass goes on to the later generation, and the idle yield's own failure is reported as well, never in place of
 * the generation's.
 *
 * @since 0.1
 */
public final class SupervisionPass {

    private static final CuiLogger LOGGER = new CuiLogger(SupervisionPass.class);

    private final WorkerSupervisor supervisor;
    private final SupervisorCalls calls;
    private final WorkerEnding ending;
    private final WorkerRecycling recycling;
    private final WorkerTaskActivity tasks;
    private final AgentStartedDetector detector;
    private final Function<WorkerGeneration, WorkerActivityPump> pumps;
    private final Map<String, WorkerActivityPump> active = new HashMap<>();
    private final Set<String> finished = new HashSet<>();

    /**
     * @param supervisor the supervisor whose registry the pass advances
     * @param calls      the supervisor's inbound calls, which own the per-generation loss detections
     * @param ending     the one end path of a generation
     * @param recycling  the recycling decision of the idle yield
     * @param tasks      the facts of a served task, which name the attempt the {@code agent_started} signal carries
     * @param detector   the {@code agent_started} detection of a generation's process tree
     * @param pumps      the activity pump of one generation, which the caller composes with its mapper and recorder
     * @throws NullPointerException if an argument is {@code null}
     */
    public SupervisionPass(WorkerSupervisor supervisor, SupervisorCalls calls, WorkerEnding ending,
            WorkerRecycling recycling, WorkerTaskActivity tasks, AgentStartedDetector detector,
            Function<WorkerGeneration, WorkerActivityPump> pumps) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.ending = Objects.requireNonNull(ending, "ending");
        this.recycling = Objects.requireNonNull(recycling, "recycling");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.pumps = Objects.requireNonNull(pumps, "pumps");
    }

    /**
     * Runs one step of the supervision over the supervisor's registry, the job records and the job logs. A failure of
     * one generation's step is reported and the later generations still run, and the idle yield runs even then, so a
     * single failing generation neither hides the others nor starves a role that waits for a slot.
     */
    public synchronized void run() {
        var environment = supervisor.environment();
        var now = environment.clock().instant();
        var jobs = JobRecordStore.ofProject(environment.base(), environment.projectId());
        try {
            for (var generation : supervisor.generations()) {
                advanceReported(generation, now, jobs);
            }
        } finally {
            yieldIdleSlot();
        }
    }

    /**
     * Advances one generation and reports a failure of its step as that generation's: a store that refuses the record,
     * a resource whose read or write fails and the settled-record refusal a fallback write meets stop this generation's
     * step alone.
     */
    private void advanceReported(WorkerGeneration generation, Instant now, JobRecordStore jobs) {
        try {
            advance(generation, now, jobs);
        } catch (StoreRefusal | UncheckedIOException | IllegalStateException | IllegalArgumentException failure) {
            LOGGER.warn(failure, WARN.WORKER_ADVANCE_FAILED, generation.generationId(), failure.getMessage());
        }
    }

    /**
     * Yields an idle slot to a waiting role and reports a failure of the yield instead of letting it replace the
     * failure of a generation's step that is still in flight.
     */
    private void yieldIdleSlot() {
        try {
            recycling.yieldIdleSlot();
        } catch (StoreRefusal | UncheckedIOException | IllegalStateException failure) {
            LOGGER.warn(failure, WARN.WORKER_IDLE_YIELD_FAILED, failure.getMessage());
        }
    }

    /**
     * Advances one generation of the registry: a terminal one is left alone, a running one gets its detection, an
     * ended job completes its end, and a running worker's log and process tree are read. A generation that closed its
     * task without an end path first closes the attempt whose log its pump still holds.
     */
    private void advance(WorkerGeneration generation, Instant now, JobRecordStore jobs) {
        if (generation.terminal()) {
            return;
        }
        calls.detection(generation);
        var job = jobs.read(generation.jobId());
        if (job.isEmpty()) {
            return;
        }
        if (job.get().status().isSettled()) {
            completeEnd(generation, now, jobs, job.get());
            return;
        }
        var task = generation.leasedTask();
        if (task != null) {
            observe(generation, task, job.get(), jobs);
        } else {
            closeAttempt(generation, jobs);
        }
    }

    /**
     * Reads the log a generation's task grew and examines its process tree. The log's offset lives in the pump, so a
     * line the log gained is appended once however often the pass runs.
     */
    private void observe(WorkerGeneration generation, String task, JobRecord job, JobRecordStore jobs) {
        pump(generation, task, jobs);
        var pid = job.pid();
        if (pid == null) {
            return;
        }
        tasks.facts(task).ifPresent(facts -> detector.detect(generation, pid,
                new AgentStartedDetector.HeldTask(task, facts.attempt())));
    }

    /**
     * Appends the lines the job log gained, through the generation's own pump. A task whose facts can no longer be read
     * has no journal this run can append to — the recorder refuses an unknown or closed task — so nothing is appended
     * and the pass moves on instead of failing every later pass.
     */
    private void pump(WorkerGeneration generation, String task, JobRecordStore jobs) {
        var facts = tasks.facts(task).orElse(null);
        if (facts == null) {
            return;
        }
        var pump = active.computeIfAbsent(generation.generationId(), ignored -> pumps.apply(generation));
        try {
            pump.pump(task, generation.generationId(), facts.attempt(),
                    new JobLog(jobs.logPath(generation.jobId())));
        } catch (IllegalStateException _) {
            // The task closed between the read of its facts and the append: the recorder refuses an unknown or closed
            // task, and that refusal is a benign race of the pass, not a defect of the worker.
        }
    }

    /**
     * Closes the attempt a generation's pump still holds after its task closed without an end path: the tail the worker
     * wrote before the close and the attempt's end go to the task that produced them, never to the next one.
     */
    private void closeAttempt(WorkerGeneration generation, JobRecordStore jobs) {
        var pump = active.get(generation.generationId());
        if (pump == null) {
            return;
        }
        pump.close(generation.generationId(), new JobLog(jobs.logPath(generation.jobId())));
    }

    /**
     * Settles a generation whose job record is settled: a deadline or the observed exit decides the loss, an exit
     * before the first call is the start failure, and the one end path completes the end exactly once. The generation's
     * log is read once more first, so the tail a worker wrote before its exit is appended before the attempt ends. The
     * generation is claimed under the calls' monitor, the end work runs outside it, and its result is recorded under
     * the monitor again.
     */
    private void completeEnd(WorkerGeneration generation, Instant now, JobRecordStore jobs, JobRecord job) {
        var generationId = generation.generationId();
        var task = generation.leasedTask();
        if (!finished.contains(generationId)) {
            if (task != null) {
                // The log's tail is read before the monitor is taken: a pump writes the journal, not the generation,
                // and the monitor must not hold the supervisor's timer thread for the length of a log read.
                pump(generation, task, jobs);
            } else {
                // The task closed before the job settled: the tail and the attempt's end still belong to that task.
                closeAttempt(generation, jobs);
            }
        }
        // The classification reads the settled record, which is I/O: it happens before the monitor is taken, so a
        // deadline of another generation never waits for it.
        var recorded = recordedEnd(jobs, job);
        var claim = claimEnd(generation, now, recorded);
        WorkerGeneration terminal = null;
        try {
            if (claim != null) {
                // The journal write is I/O too, and it runs outside the monitor for the same reason; the claim is
                // recorded only once it is written, so a retry after a failure here appends it instead of losing it.
                finishAttempt(generation, claim.cause(), claim.loss(), claim.recorded());
                synchronized (calls) {
                    finished.add(generationId);
                }
            }
            terminal = ending.exited(generation);
        } finally {
            recordEnd(generation, terminal);
        }
    }

    /**
     * Claims a generation's end under the monitor of the calls: the detection settles, the requested end is read, and
     * the claim is answered with what the attempt's end needs, so the caller appends it and the reading of the settled
     * record and the journal write stay outside the monitor. A generation whose end was claimed before answers
     * {@code null}: its attempt's end is written already.
     *
     * @return the claim, or {@code null} when the generation's end was claimed before
     */
    private @Nullable Claim claimEnd(WorkerGeneration generation, Instant now, @Nullable EndReason recorded) {
        var generationId = generation.generationId();
        synchronized (calls) {
            if (finished.contains(generationId)) {
                return null;
            }
            var detection = calls.detection(generation);
            // The detection settles with every claimed end, so a deadline cannot fire into an end that already ran —
            // also for a settled worker that holds no task. An exit is a loss only while the generation still leases a
            // task: that is the task the loss offers again. A worker that ended without a task has no attempt to lose,
            // and the settled record names its end.
            var exit = detection.exit(now);
            var lostTask = generation.leasedTask() != null ? exit : Optional.<LossSignal>empty();
            lostTask.ifPresent(signal -> {
                // The exit is kept for a retry whose attempt end is not written yet: the settled detection answers
                // nothing, and the pass must still name the exit rather than a plain loss.
                calls.remember(generationId, signal);
                ending.end(generation, signal);
            });
            var loss = lostTask.or(() -> calls.loss(generationId)).orElse(null);
            var requested = ending.requestedCause(generationId).orElse(null);
            if (requested == null && detection.startFailure()) {
                ending.end(generation, EndCause.START_FAILURE);
                requested = ending.requestedCause(generationId).orElse(null);
            }
            return new Claim(requested, loss, recorded);
        }
    }

    /**
     * Records the result of an end under the monitor of the calls: the terminal generation replaces the registered one
     * and the per-generation values are forgotten. A failed end keeps them, and the generation, for the next pass.
     */
    private void recordEnd(WorkerGeneration generation, @Nullable WorkerGeneration terminal) {
        var generationId = generation.generationId();
        synchronized (calls) {
            if (terminal == null) {
                // The end failed and stays owed: the generation keeps its claim, so the retry neither re-reads the log
                // nor appends a second attempt end.
                return;
            }
            try {
                supervisor.replaced(terminal);
                calls.forget(generationId);
            } finally {
                finished.remove(generationId);
                active.remove(generationId);
            }
        }
    }

    /**
     * Appends the attempt's end to the journal of the task the generation held, through the pump that read its log; a
     * generation whose log was never pumped appends nothing, because the attempt has no journal in this run.
     */
    private void finishAttempt(WorkerGeneration generation, @Nullable EndCause cause, @Nullable LossSignal loss,
            @Nullable EndReason recorded) {
        var task = generation.leasedTask();
        var pump = active.get(generation.generationId());
        if (task != null && pump != null && tasks.facts(task).isPresent()) {
            try {
                pump.finish(task, generation.generationId(), attemptEnd(cause, loss, recorded));
            } catch (IllegalStateException _) {
                // The same race as the pump's: a task that closed first has no journal entry to append.
            }
        }
        // The pump leaves only after its end is appended: a completion that throws before that keeps the pump, and the
        // retry resumes from its offset instead of re-reading the whole log.
        active.remove(generation.generationId());
    }

    /**
     * @param cause    why the generation was ended; {@code null} when the registry holds no requested end
     * @param loss     the signal that settled its detection; {@code null} when it was not lost
     * @param recorded the end reason the settled record carries; {@code null} when it carries none
     * @return how the attempt of the held task ended
     */
    static QueueTaskRecord.AttemptEnd attemptEnd(@Nullable EndCause cause, @Nullable LossSignal loss,
            @Nullable EndReason recorded) {
        if (cause == null) {
            return recorded == null ? QueueTaskRecord.AttemptEnd.WORKER_LOST : attemptEndOf(recorded);
        }
        return switch (cause) {
            case TASK_DONE, RECYCLE -> QueueTaskRecord.AttemptEnd.CLOSED;
            case DRAIN -> QueueTaskRecord.AttemptEnd.DRAIN;
            case PAUSE_AT_ONCE -> QueueTaskRecord.AttemptEnd.PAUSED;
            case OFF_TRACK -> QueueTaskRecord.AttemptEnd.OFF_TRACK;
            case START_FAILURE -> QueueTaskRecord.AttemptEnd.START_FAILURE;
            case LOST -> loss == null ? QueueTaskRecord.AttemptEnd.WORKER_LOST : loss.reason().attemptEnd();
        };
    }

    /**
     * The end of an attempt whose worker ended without an end the supervisor requested, as the settlement recorded it:
     * a task the worker finished closes its attempt, a loss ends it without its submit, and a budget, a drain, a start
     * failure and a withdrawal carry their own end.
     *
     * @param recorded the end reason the settled record carries
     * @return how the attempt ended
     */
    private static QueueTaskRecord.AttemptEnd attemptEndOf(EndReason recorded) {
        return switch (recorded) {
            case TASK_DONE, IDLE_RECYCLE, IDLE_YIELD, TOKEN_BUDGET, TASKS_PER_GENERATION, ARTIFACT_CHANGED ->
                QueueTaskRecord.AttemptEnd.CLOSED;
            case EXITED -> QueueTaskRecord.AttemptEnd.EXITED_WITHOUT_SUBMIT;
            case ACK_MISSED, SILENCE -> QueueTaskRecord.AttemptEnd.WORKER_LOST;
            case BUDGET -> QueueTaskRecord.AttemptEnd.BUDGET;
            case DRAIN -> QueueTaskRecord.AttemptEnd.DRAIN;
            case START_FAILURE -> QueueTaskRecord.AttemptEnd.START_FAILURE;
            case WITHDRAWN -> QueueTaskRecord.AttemptEnd.PAUSED;
        };
    }

    /**
     * @param jobs the job records of the generation's project
     * @param job  the settled record of the generation's job
     * @return the end reason the record carries; {@code null} when it carries none
     */
    private static @Nullable EndReason recordedEnd(JobRecordStore jobs, JobRecord job) {
        return WorkerEnding.recordedTerminal(jobs, job.jobId()).map(WorkerState::endReason).orElse(null);
    }

    /**
     * What a claimed end hands to the attempt's journal write, so that write runs outside the calls' monitor.
     *
     * @param cause    why the generation was ended; {@code null} when the registry holds no requested end
     * @param loss     the signal that settled its detection; {@code null} when it was not lost
     * @param recorded the end reason the settled record carries; {@code null} when it carries none
     */
    private record Claim(@Nullable EndCause cause, @Nullable LossSignal loss, @Nullable EndReason recorded) {
    }
}
