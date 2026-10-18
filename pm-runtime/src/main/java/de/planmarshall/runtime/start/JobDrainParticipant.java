/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.service.JobRunner.CancelCause;
import de.planmarshall.runtime.job.SubprocessJobRunner;
import de.planmarshall.runtime.job.TerminationProtocol;

/**
 * The {@link DrainParticipant} of the job runner (PM-ARCH-7, PM-EXEC-4): it stops the admission of jobs, waits for the
 * running jobs up to the drain timeout, terminates the groups of those still running after it, which are recorded
 * {@code killed} (outcome row 4), and settles every job still {@code queued} as {@code killed} without a signal when
 * the drain closes it.
 * <ul>
 * <li>{@link #stopAdmitting()}: from now on a job whose start is asked for is recorded {@code queued} and never
 * spawned ({@link SubprocessJobRunner#stopAdmitting()}).</li>
 * <li>{@link #endIdle()}: a job is never idle; nothing is ended.</li>
 * <li>{@link #awaitQuiet(Instant)}: waits for every running job until the deadline.</li>
 * <li>{@link #terminateRemaining()}: cancels every job still running for the drain, which the runner carries out with
 * the termination protocol, and waits for their settlement, at most the grace period and a margin per job.</li>
 * <li>{@link #close()}: settles every job still {@code queued} as {@code killed} without a signal; a queued job holds
 * no slot.</li>
 * </ul>
 * The settlement of a terminated job releases its slot after its fate is recorded. The admission contract of
 * {@link DrainParticipant} holds: the runner takes its admission decision and the stop of the admission under one
 * lock.
 * <p>
 * Specification: {@code doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc} (Drain, Stop & Upgrade,
 * the jobs participant); {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Job status).
 *
 * @since 0.1
 */
public final class JobDrainParticipant implements DrainParticipant {

    /** The name of the participant in the log and in the drain outcome. */
    public static final String DRAIN_NAME = "jobs";

    /** How long a terminated job is waited for: the grace period and a margin for its settlement. */
    static final Duration TERMINATION_WAIT = TerminationProtocol.GRACE_PERIOD.plusSeconds(10);

    private final SubprocessJobRunner runner;
    private final InstantSource clock;

    /**
     * @param runner the job runner
     * @throws NullPointerException if the runner is {@code null}
     */
    public JobDrainParticipant(SubprocessJobRunner runner) {
        this(runner, InstantSource.system());
    }

    /**
     * @param runner the job runner
     * @param clock  the clock the deadline of {@link #awaitQuiet(Instant)} is measured on
     */
    JobDrainParticipant(SubprocessJobRunner runner, InstantSource clock) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return DRAIN_NAME;
    }

    @Override
    public void stopAdmitting() {
        runner.stopAdmitting();
    }

    @Override
    public void endIdle() {
        // a job is never idle: it runs until it ends, is cancelled or is terminated by the drain
    }

    @Override
    public boolean awaitQuiet(Instant deadline) throws InterruptedException {
        Objects.requireNonNull(deadline, "deadline");
        for (var jobId : runner.runningJobs()) {
            var remaining = Duration.between(clock.instant(), deadline);
            if (!remaining.isPositive()) {
                break;
            }
            runner.awaitSettled(jobId, remaining);
            if (Thread.interrupted()) {
                throw new InterruptedException("the drain was interrupted while it waited for job " + jobId);
            }
        }
        return runner.runningJobs().isEmpty();
    }

    @Override
    public List<String> terminateRemaining() {
        var terminated = new ArrayList<String>();
        var remaining = runner.runningJobs();
        for (var jobId : remaining) {
            if (runner.cancel(jobId, CancelCause.DRAIN)) {
                terminated.add("job " + jobId);
            }
        }
        for (var jobId : remaining) {
            runner.awaitSettled(jobId, TERMINATION_WAIT);
        }
        return terminated;
    }

    @Override
    public void close() {
        runner.settleQueued();
    }
}
