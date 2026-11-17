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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.job.JobLog;

/**
 * One periodic step of the supervision (PM-WORK-3, PM-WORK-7): the part of the loop that reads the world and settles
 * what it finds, as a function the assembly schedules.
 * <p>
 * The class starts no thread and owns no timer: the deadlines of {@link LossDetection} keep running on
 * {@link WorkerTimers}, which already owns the supervisor's one scheduler thread, so {@link #run()} is a step a caller
 * drives from wherever its scheduling belongs. It takes one reading of the clock and, for every generation that is not
 * terminal, reads the generation's job record from the store the terminal-state writer reads. A job the record reports
 * as ended settles its detection with {@link LossDetection#exit(Instant)} and completes the end through
 * {@link WorkerEnding#exited(WorkerGeneration)}, a loss going through
 * {@link WorkerEnding#end(WorkerGeneration, LossSignal)} first. A generation that holds a task reads its job log from
 * the offset its pump last read, hands the complete lines to that generation's {@link WorkerActivityPump} and runs
 * {@link AgentStartedDetector#detect(WorkerGeneration, long, AgentStartedDetector.HeldTask)} with the PID of the job
 * record. Once per pass the recycling yields an idle slot to a role that waits for one.
 * <p>
 * A pass that finds nothing to do reads the job records and their logs and writes nothing; a log that is unreadable or
 * truncated appends nothing and is read again at the next pass (ADR {@code runtime-worker/011}). Every state change of
 * a generation is written through {@link WorkerSupervisor#replaced(WorkerGeneration)} and its {@link WorkerLifecycle}
 * transition table. {@link #run()} holds this instance's monitor and, while it completes an end, the monitor of
 * {@link SupervisorCalls}, so the terminal write and a transport call are one writer at a time.
 *
 * @since 0.1
 */
public final class SupervisionPass {

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
     * Runs one step of the supervision over the supervisor's registry, the job records and the job logs. The idle yield
     * runs even when one generation's step throws, so a single failing generation never starves a role that waits for a
     * slot.
     */
    public synchronized void run() {
        var environment = supervisor.environment();
        var now = environment.clock().instant();
        var jobs = JobRecordStore.ofProject(environment.base(), environment.projectId());
        try {
            for (var generation : supervisor.generations()) {
                advance(generation, now, jobs);
            }
        } finally {
            recycling.yieldIdleSlot();
        }
    }

    /**
     * Advances one generation of the registry: a terminal one is left alone, an ended job completes its end, and a
     * running worker's log and process tree are read.
     */
    private void advance(WorkerGeneration generation, Instant now, JobRecordStore jobs) {
        if (generation.terminal()) {
            return;
        }
        var job = jobs.read(generation.jobId());
        if (job.isEmpty()) {
            return;
        }
        if (job.get().status().isSettled()) {
            completeEnd(generation, now, jobs);
            return;
        }
        var task = generation.leasedTask();
        if (task != null) {
            observe(generation, task, job.get(), jobs);
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
        if (tasks.facts(task).isEmpty()) {
            return;
        }
        var pump = active.computeIfAbsent(generation.generationId(), ignored -> pumps.apply(generation));
        try {
            pump.pump(task, generation.generationId(), new JobLog(jobs.logPath(generation.jobId())));
        } catch (IllegalStateException _) {
            // The task closed between the read of its facts and the append: the recorder refuses an unknown or closed
            // task, and that refusal is a benign race of the pass, not a defect of the worker.
        }
    }

    /**
     * Settles a generation whose job record is settled: a deadline or the observed exit decides the loss, an exit
     * before the first call is the start failure, and the one end path completes the end exactly once. The generation's
     * log is read once more first, so the tail a worker wrote before its exit is appended before the attempt ends.
     */
    private void completeEnd(WorkerGeneration generation, Instant now, JobRecordStore jobs) {
        var generationId = generation.generationId();
        var task = generation.leasedTask();
        if (task != null && !finished.contains(generationId)) {
            // The log's tail is read before the monitor is taken: a pump writes the journal, not the generation, and the
            // monitor must not hold the supervisor's timer thread for the length of a log read.
            pump(generation, task, jobs);
        }
        // One writer at a time: a transport call and this completion both write the generation, and a call that read it
        // before the terminal write must not land after it. The calls object is the monitor every call holds.
        synchronized (calls) {
            var detection = calls.detection(generation);
            var exit = detection.exit(now);
            exit.ifPresent(signal -> ending.end(generation, signal));
            var loss = exit.or(() -> calls.loss(generationId)).orElse(null);
            var requested = ending.requestedCause(generationId).orElse(null);
            if (requested == null && detection.startFailure()) {
                ending.end(generation, EndCause.START_FAILURE);
                requested = ending.requestedCause(generationId).orElse(null);
            }
            if (!finished.contains(generationId)) {
                // The attempt's end is appended once, before the terminal write, so a refused end cannot swallow it and a
                // retried completion neither re-pumps the log nor appends a second one.
                finishAttempt(generation, requested, loss);
                finished.add(generationId);
            }
            supervisor.replaced(ending.exited(generation));
            calls.forget(generationId);
            finished.remove(generationId);
        }
    }

    /**
     * Appends the attempt's end to the journal of the task the generation held, through the pump that read its log; a
     * generation whose log was never pumped appends nothing, because the attempt has no journal in this run. The append
     * happens before the terminal state is written, so a store that refuses the end cannot swallow the journal entry.
     */
    private void finishAttempt(WorkerGeneration generation, @Nullable EndCause cause, @Nullable LossSignal loss) {
        var task = generation.leasedTask();
        var pump = active.get(generation.generationId());
        if (task != null && pump != null && tasks.facts(task).isPresent()) {
            try {
                pump.finish(task, generation.generationId(), attemptEnd(cause, loss));
            } catch (IllegalStateException _) {
                // The same race as the pump's: a task that closed first has no journal entry to append.
            }
        }
        // The pump leaves only after its end is appended: a completion that throws before that keeps the pump, and the
        // retry resumes from its offset instead of re-reading the whole log.
        active.remove(generation.generationId());
    }

    /**
     * @param cause why the generation was ended; {@code null} when the registry holds no requested end
     * @param loss  the signal that settled its detection; {@code null} when it was not lost
     * @return how the attempt of the held task ended
     */
    static QueueTaskRecord.AttemptEnd attemptEnd(@Nullable EndCause cause, @Nullable LossSignal loss) {
        return switch (cause == null ? EndCause.LOST : cause) {
            case TASK_DONE, RECYCLE -> QueueTaskRecord.AttemptEnd.CLOSED;
            case DRAIN -> QueueTaskRecord.AttemptEnd.DRAIN;
            case PAUSE_AT_ONCE -> QueueTaskRecord.AttemptEnd.PAUSED;
            case OFF_TRACK -> QueueTaskRecord.AttemptEnd.OFF_TRACK;
            case START_FAILURE -> QueueTaskRecord.AttemptEnd.START_FAILURE;
            case LOST -> loss == null ? QueueTaskRecord.AttemptEnd.WORKER_LOST : loss.reason().attemptEnd();
        };
    }
}
