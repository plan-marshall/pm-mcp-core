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

import de.cuioss.tools.logging.CuiLogger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The periodic sweep of the runtime over the enrolled projects
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Per-Project Recovery): every {@link #INTERVAL} it
 * runs each {@link SweepStep} for each project of {@link EnrolledProjects}, with the pending entry records older than
 * {@link #PENDING_ENTRY_MAX_AGE} as the ones to remove ({@link PendingEntryRecordsSweep}). The duration is a compiled
 * constant and not configurable.
 * <p>
 * A project whose recovery has not finished is skipped in a run and taken up by the next; a project whose recovery
 * failed is skipped as well, because its stores wait for the integrity breach to be resolved. A failing step, one
 * that reports a failure or throws whatever exception, is logged and stops neither the other steps nor the other
 * projects. Each run logs its counts. A scheduled run that fails outside its steps is logged, and the schedule stays
 * alive for the next run. {@link #stop()} ends the schedule for the drain; a run in progress finishes.
 *
 * @since 0.1
 */
public final class PeriodicSweep {

    private static final CuiLogger LOGGER = new CuiLogger(PeriodicSweep.class);

    /** The interval of the sweep. */
    public static final Duration INTERVAL = Duration.ofSeconds(3600);

    /** The age from which a pending entry record is removed. */
    public static final Duration PENDING_ENTRY_MAX_AGE = Duration.ofSeconds(86400);

    private final EnrolledProjects enrolled;
    private final Predicate<String> recovered;
    private final List<SweepStep> steps;
    private final Clock clock;
    private ScheduledFuture<?> schedule;

    /**
     * The counts of one run.
     *
     * @param swept   the projects every step ran for without a failure
     * @param skipped the projects skipped because their recovery has not finished or failed
     * @param failed  the projects for which a step failed
     */
    public record Summary(int swept, int skipped, int failed) {
    }

    /**
     * @param enrolled  the enrolled projects
     * @param recovered whether the recovery of a project has finished without a failure, such as
     *                  {@link ProjectRecovery#isRecovered(String)}
     * @param steps     the sweep steps, run in this order
     * @param clock     the clock of a scheduled run
     */
    public PeriodicSweep(EnrolledProjects enrolled, Predicate<String> recovered, List<SweepStep> steps, Clock clock) {
        this.enrolled = Objects.requireNonNull(enrolled, "enrolled");
        this.recovered = Objects.requireNonNull(recovered, "recovered");
        this.steps = List.copyOf(steps);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Schedules a run every {@link #INTERVAL}, the first one interval from now.
     *
     * @param executor the executor of the runs
     * @throws IllegalStateException if the sweep was started already
     */
    public synchronized void start(ScheduledExecutorService executor) {
        Objects.requireNonNull(executor, "executor");
        if (schedule != null) {
            throw new IllegalStateException("The periodic sweep was started already");
        }
        var interval = INTERVAL.toMillis();
        schedule = executor.scheduleAtFixedRate(this::scheduledRun, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * One scheduled run. A scheduled task that throws suppresses every later execution, so whatever the run throws,
     * outside its steps too (the enrolled projects, the recovered predicate, the clock), is logged and contained here:
     * the run happens on the calling thread, and its failure is received by the future's handler without a catch
     * clause. The task therefore never throws, and the schedule stays alive.
     */
    void scheduledRun() {
        CompletableFuture.runAsync(() -> runOnce(clock.instant()), Runnable::run).exceptionally(thrown -> {
            var cause = cause(thrown);
            LOGGER.error(cause, ERROR.PERIODIC_SWEEP_RUN_FAILED, cause);
            return null;
        });
    }

    /**
     * Ends the schedule, for the drain; a run in progress finishes, and no other starts. Stopping a sweep that was
     * not started or was stopped does nothing.
     */
    public synchronized void stop() {
        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /**
     * Runs every step for every enrolled project whose recovery has finished.
     *
     * @param now the instant of the run
     * @return the counts of the run
     */
    public Summary runOnce(Instant now) {
        var olderThan = now.minus(PENDING_ENTRY_MAX_AGE);
        var swept = 0;
        var skipped = 0;
        var failed = 0;
        for (var projectId : enrolled.projectIds()) {
            if (!recovered.test(projectId)) {
                LOGGER.debug("Periodic sweep skips project '%s': its recovery has not finished", projectId);
                skipped++;
            } else if (sweep(projectId, olderThan)) {
                swept++;
            } else {
                failed++;
            }
        }
        LOGGER.info(INFO.PERIODIC_SWEEP_FINISHED, swept, skipped, failed);
        return new Summary(swept, skipped, failed);
    }

    /** @return whether every step ran for the project without a failure */
    private boolean sweep(String projectId, Instant olderThan) {
        var clean = true;
        for (var step : steps) {
            // The step runs on the calling thread; whatever it throws, a store refusal, an I/O failure or a broken
            // contract, reaches the future's handler and becomes a failed outcome of this step alone
            RecoveryStep.Outcome outcome = CompletableFuture
                    .supplyAsync(() -> step.run(projectId, olderThan), Runnable::run)
                    .exceptionally(thrown -> new RecoveryStep.Outcome.Failed("the step threw " + cause(thrown)))
                    .join();
            if (outcome instanceof RecoveryStep.Outcome.Failed(var reason)) {
                LOGGER.warn(WARN.PERIODIC_SWEEP_STEP_FAILED, projectId, step.name(), reason);
                clean = false;
            }
        }
        return clean;
    }

    private static Throwable cause(Throwable thrown) {
        return thrown instanceof CompletionException && thrown.getCause() != null ? thrown.getCause() : thrown;
    }
}
