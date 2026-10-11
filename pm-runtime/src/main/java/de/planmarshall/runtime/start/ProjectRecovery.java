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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;

/**
 * The per-project recovery of the runtime (doc/specification/runtime-model/02-startup-recovery-drain.adoc,
 * Per-Project Recovery; PM-ARCH-7). It never runs before the socket is bound: nothing is recovered at construction,
 * only by one of two triggers.
 * <ul>
 * <li>The lazy trigger {@link #awaitRecovered(String)}, on the first call addressing a project. It waits at most the
 * project recovery wait ({@link #RECOVERY_WAIT}) and then answers {@link Result.Recovering}, which the assembly maps
 * to the retryable error {@code runtime_recovering}; the recovery continues, and a repeated call is served once it
 * has finished.</li>
 * <li>The background pass {@link #startBackgroundPass()}, called after the bind, over every project of
 * {@link EnrolledProjects}.</li>
 * </ul>
 * Each enrolled project is recovered once per runtime instance, by whichever trigger comes first; the other waits
 * for the same result. A project that is not enrolled is not recovered and gets no outcome.
 * <p>
 * The steps run in the order of {@link RecoveryStep.Name}, whatever order they are given in; the jobs-and-workers
 * step therefore runs before the leases step. A failed step is logged and recorded in the outcome and stops the
 * recovery of its project only; other projects are unaffected. The recoveries run on the executor the caller
 * supplies.
 * <p>
 * "Recover once, whichever trigger comes first" is a claim on a shared slot: an atomic
 * {@link ConcurrentMap#computeIfAbsent} installs the future of a project's recovery, only the installer starts it,
 * and every other caller waits on the installed future. The bounded wait never cancels the recovery. Across
 * processes the singleton lock rules out two runtime instances recovering the same base.
 *
 * @since 0.1
 */
public final class ProjectRecovery {

    private static final CuiLogger LOGGER = new CuiLogger(ProjectRecovery.class);

    /** The project recovery wait (doc/specification/timeouts/02-runtime-leases-operator-web.adoc). */
    public static final Duration RECOVERY_WAIT = Duration.ofSeconds(10);

    private final EnrolledProjects enrolled;
    private final List<RecoveryStep> steps;
    private final Executor executor;
    private final Duration wait;
    private final ConcurrentMap<String, CompletableFuture<ProjectOutcome>> recoveries = new ConcurrentHashMap<>();

    /**
     * What the recovery of one project did.
     *
     * @param projectId the id of the project
     * @param steps     the steps that ran, in their order, with their outcomes; a failed step is the last
     */
    public record ProjectOutcome(String projectId, List<StepResult> steps) {

        /**
         * Copies the steps.
         */
        public ProjectOutcome {
            Objects.requireNonNull(projectId, "projectId");
            steps = List.copyOf(steps);
        }

        /**
         * @return {@code true} when a step failed and the recovery of the project stopped there
         */
        public boolean failed() {
            return !steps.isEmpty() && steps.getLast().outcome() instanceof RecoveryStep.Outcome.Failed;
        }
    }

    /**
     * One step of a project's recovery and its outcome.
     *
     * @param name    the name of the step
     * @param outcome what the step did
     */
    public record StepResult(RecoveryStep.Name name, RecoveryStep.Outcome outcome) {
    }

    /**
     * The answer of the lazy trigger.
     */
    public sealed interface Result {

        /**
         * The recovery of the project has finished; a failed one included.
         *
         * @param outcome what the recovery did
         */
        record Recovered(ProjectOutcome outcome) implements Result {
        }

        /** The recovery has not finished within the project recovery wait; it continues. */
        record Recovering() implements Result {
        }

        /** The project is not enrolled; nothing is recovered. */
        record NotEnrolled() implements Result {
        }
    }

    /**
     * @param enrolled the enrolled projects
     * @param steps    the recovery steps, exactly one per {@link RecoveryStep.Name}; a step whose content is not
     *                 built yet is a {@link RecoveryStep#notImplemented(RecoveryStep.Name)} seam
     * @param executor the executor the recoveries run on
     * @throws IllegalArgumentException if two steps have the same name, or a {@link RecoveryStep.Name} has no step;
     *                                  the message names the missing ones
     */
    public ProjectRecovery(EnrolledProjects enrolled, List<RecoveryStep> steps, Executor executor) {
        this(enrolled, steps, executor, RECOVERY_WAIT);
    }

    ProjectRecovery(EnrolledProjects enrolled, List<RecoveryStep> steps, Executor executor, Duration wait) {
        this.enrolled = Objects.requireNonNull(enrolled, "enrolled");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.wait = Objects.requireNonNull(wait, "wait");
        var names = EnumSet.noneOf(RecoveryStep.Name.class);
        for (var step : steps) {
            if (!names.add(step.name())) {
                throw new IllegalArgumentException("two recovery steps named " + step.name());
            }
        }
        var missing = EnumSet.complementOf(names);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("no recovery step named " + missing);
        }
        this.steps = steps.stream().sorted(Comparator.comparing(RecoveryStep::name)).toList();
    }

    /**
     * Starts the recovery of every enrolled project that no trigger has started yet, on the executor.
     */
    public void startBackgroundPass() {
        for (var projectId : enrolled.projectIds()) {
            recovery(projectId);
        }
    }

    /**
     * The lazy trigger: starts the recovery of the project unless a trigger has started it, and waits for it, at
     * most the project recovery wait.
     *
     * @param projectId the id of the project a call addresses
     * @return the outcome of the recovery, {@link Result.Recovering} when it has not finished in time, or
     *         {@link Result.NotEnrolled}
     */
    public Result awaitRecovered(String projectId) {
        if (!enrolled.projectIds().contains(projectId)) {
            return new Result.NotEnrolled();
        }
        try {
            return new Result.Recovered(recovery(projectId).get(wait.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException _) {
            LOGGER.warn(WARN.PROJECT_RECOVERY_WAIT_EXPIRED, projectId, wait);
            return new Result.Recovering();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new Result.Recovering();
        } catch (ExecutionException e) {
            // Unreachable: whatever ends a recovery exceptionally, a step that throws, a rejected executor or a
            // failure after the last step, is turned into a failed outcome by brokenStep
            throw new IllegalStateException("recovery of " + projectId + " ended without an outcome", e);
        }
    }

    /**
     * Whether the recovery of the project has finished without a failure, without starting or waiting for it; the
     * periodic sweep skips a project for which this is {@code false}.
     *
     * @param projectId the id of a project
     * @return {@code true} if a trigger recovered the project and no step failed
     */
    public boolean isRecovered(String projectId) {
        var recovery = recoveries.get(projectId);
        return recovery != null && recovery.isDone() && !recovery.join().failed();
    }

    private CompletableFuture<ProjectOutcome> recovery(String projectId) {
        return recoveries.computeIfAbsent(projectId, this::start);
    }

    private CompletableFuture<ProjectOutcome> start(String projectId) {
        var results = new CopyOnWriteArrayList<StepResult>();
        // Applied to a completed stage, an executor that rejects the task completes the future exceptionally
        // instead of throwing to the caller, so a rejection reaches brokenStep like a failing step
        return CompletableFuture.completedFuture(projectId)
                .thenApplyAsync(id -> recover(id, results), executor)
                .exceptionally(failure -> brokenStep(projectId, results, failure));
    }

    private ProjectOutcome recover(String projectId, List<StepResult> results) {
        for (var step : steps) {
            var outcome = step.run(projectId);
            results.add(new StepResult(step.name(), outcome));
            if (outcome instanceof RecoveryStep.Outcome.Failed(var reason)) {
                LOGGER.error(ERROR.PROJECT_RECOVERY_FAILED, projectId, step.name(), reason);
                break;
            }
        }
        return finished(new ProjectOutcome(projectId, results));
    }

    /**
     * The recovery ended exceptionally: a step broke its contract and threw instead of reporting a failure, the
     * executor rejected the recovery, or something failed after the last step. The pending step, or the last step when
     * none is pending, is recorded as the failed step.
     */
    ProjectOutcome brokenStep(String projectId, List<StepResult> results, Throwable failure) {
        var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause()
                : failure;
        var pending = results.size() < steps.size();
        var step = (pending ? steps.get(results.size()) : steps.getLast()).name();
        String reason;
        if (cause instanceof RejectedExecutionException) {
            reason = "the recovery was not started: " + cause;
        } else if (pending) {
            reason = "the step threw " + cause;
        } else {
            reason = "the recovery failed after its last step: " + cause;
        }
        LOGGER.error(cause, ERROR.PROJECT_RECOVERY_FAILED, projectId, step, reason);
        var all = new ArrayList<>(results);
        all.add(new StepResult(step, new RecoveryStep.Outcome.Failed(reason)));
        return finished(new ProjectOutcome(projectId, all));
    }

    private static ProjectOutcome finished(ProjectOutcome outcome) {
        var summary = outcome.failed() ? "failed in " + outcome.steps().getLast().name()
                : outcome.steps().size() + " steps done";
        LOGGER.info(INFO.PROJECT_RECOVERY_FINISHED, outcome.projectId(), summary);
        return outcome;
    }
}
