/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Builder;

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobEvidence.ParseResult;
import de.planmarshall.runtime.job.JobEvidence.ProcessEnded;
import de.planmarshall.runtime.job.JobEvidence.RefusedBeforeSpawn;
import de.planmarshall.runtime.job.JobEvidence.Signalled;
import de.planmarshall.runtime.job.JobEvidence.Termination;
import de.planmarshall.runtime.job.JobEvidence.WaitStatus;
import de.planmarshall.runtime.job.PmExecLauncher.Launch;
import de.planmarshall.runtime.job.PmExecLauncher.Launched;
import de.planmarshall.runtime.job.SettlementStep.Outcome;
import de.planmarshall.runtime.job.SettlementStep.Settled;
import de.planmarshall.runtime.job.TerminationProtocol.Ending;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The subprocess runner of the job runtime (PM-EXEC-4, PM-ARCH-7): it implements the service {@link JobRunner} and
 * the engine port {@link JobStarter}, and it runs every process job of a project from its verification to its
 * settlement.
 * <p>
 * A start runs in this order:
 * <ol>
 * <li>the job is verified by the {@link VerifyNotResolvePipeline} in a transaction of its own, which is closed before
 * anything is launched, so no caller holds the enrolment lock while the job is launched or awaited; a refusal settles
 * the job {@code refused} (outcome row 1) in one write and without a process, its {@code child_wall_ms} {@code null};
 * </li>
 * <li>the job id is allocated and the write-ahead record ({@code running}, {@code pid: null}) is written, in one
 * transaction under the lock of the scope that owns the job;</li>
 * <li>the job is launched through {@link PmExecLauncher}, and its budget starts at the spawn;</li>
 * <li>the launch report, the first line of the job log, is awaited, and the post-spawn record with {@code pid},
 * {@code process_started_at}, {@code spawned_at}, {@code confinement} and {@code landlock_abi} replaces the
 * write-ahead record. Only then does the job count as running, and only then does the start answer it: a start never
 * reports a job running whose record lacks its PID. A launcher that fails before {@code execve} writes no report; its
 * job keeps the write-ahead record and is settled from the launcher's exit.</li>
 * </ol>
 * <p>
 * A virtual thread per job then waits for the end of its process, for its budget or for a cancel, whichever comes
 * first. A cancel names its {@link JobRunner.CancelCause cause}; the cause, or the expiry of the budget, is the
 * termination the {@link TerminationProtocol} carries out against the job's process group, and it decides the fate by
 * the {@link OutcomeDerivation}: an engine cancel and a pause of the plan that holds now give {@code cancelled}, the
 * budget gives {@code timed_out}, the drain gives {@code killed}. Once the process has ended, the fate is derived from
 * the evidence the runner observed (its wait status, the state of the job log, what the {@link OutputParser} found in
 * it) and the job is settled by {@link JobSettlement} in the fixed write order. A status the job reports about itself
 * is never its outcome.
 * <p>
 * Two wall times are recorded at the settlement: the job's own from {@code spawned_at} to {@code ended_at}, and the
 * child's {@code child_wall_ms} from {@code process_started_at} to the exit the runner observed. The child's start is
 * reported by the launcher on the clock of the operating system and is coarser than the runner's clock; the child's
 * wall time is therefore bounded by the job's, and it is {@code null} for a job whose launcher reported no start.
 * <p>
 * The engine port maps a {@link JobStarter.StartRequest} to a job through the caller's {@link PrimitiveJobs}; a
 * primitive with no job spec is refused {@value #PRIMITIVE_WITHOUT_JOB}. The id it answers keeps the port's grammar
 * {@code ^[bcgp]-[0-9a-f]{16}$}. This runner starts no worker, whose launch belongs to the supervisor, and no probe: a
 * probe start is refused {@value #PROBE_NOT_SUPPORTED}.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Subprocess Lifecycle &
 * Process-Group Isolation; Process Termination & Cancellation Protocol; Outcome Derivation);
 * {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record);
 * {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Job Budgets).
 *
 * @since 0.1
 */
public final class SubprocessJobRunner implements JobRunner<JobRequest, Void>, JobStarter {

    private static final CuiLogger LOGGER = new CuiLogger(SubprocessJobRunner.class);

    /** The refusal of the engine port for a primitive that names no job. */
    public static final String PRIMITIVE_WITHOUT_JOB = "primitive_without_job";

    /** The refusal of a probe start. */
    public static final String PROBE_NOT_SUPPORTED = "probe_not_supported";

    /** How often the job log is read for the launch report after the spawn. */
    static final Duration REPORT_POLL_INTERVAL = Duration.ofMillis(10);

    /**
     * The launcher's own exit for a failed {@code execve}; a launcher that cannot be started at all is settled by the
     * same exit, because the job had no process either way.
     */
    static final int EXECVE_FAILED = 127;

    /** The exit value Java reports for a process ended by a signal is this base plus the signal number. */
    private static final int SIGNAL_EXIT_BASE = 128;

    /** The highest signal number of Linux and macOS. */
    private static final int HIGHEST_SIGNAL = 64;

    private static final List<String> SIGNAL_NAMES = List.of("SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP",
            "SIGABRT", "SIGBUS", "SIGFPE", "SIGKILL", "SIGUSR1", "SIGSEGV", "SIGUSR2", "SIGPIPE", "SIGALRM",
            "SIGTERM");

    private final Path base;
    private final LockManager lockManager;
    private final VerifyNotResolvePipeline pipeline;
    private final PmExecLauncher launcher;
    private final TerminationProtocol termination;
    private final Environment environment;
    private final OutputParser parser;
    private final PrimitiveJobs primitives;
    private final InstantSource clock;
    private final Map<JobId, JobRecordStore> stores = new ConcurrentHashMap<>();
    private final Map<JobId, RunningJob> running = new ConcurrentHashMap<>();

    /**
     * @param base        the base directory of the runtime, below which the job records of a project lie
     * @param lockManager the lock manager of the runtime
     * @param pipeline    the verify-not-resolve pipeline
     * @param launcher    the launcher {@code pm-exec}
     * @param termination the termination protocol
     * @param environment the environment of a verified job
     * @param parser      the parser of the job log of an ended job
     * @param primitives  the jobs of the primitives the engine port starts
     * @param clock       the clock of the records and the budgets; {@code null} for the system clock
     * @throws NullPointerException if an argument other than the clock is {@code null}
     */
    @Builder
    private SubprocessJobRunner(Path base, LockManager lockManager, VerifyNotResolvePipeline pipeline,
            PmExecLauncher launcher, TerminationProtocol termination, Environment environment, OutputParser parser,
            PrimitiveJobs primitives, InstantSource clock) {
        this.base = Objects.requireNonNull(base, "base");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.termination = Objects.requireNonNull(termination, "termination");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.primitives = Objects.requireNonNull(primitives, "primitives");
        this.clock = clock == null ? InstantSource.system() : clock;
    }

    /**
     * The environment a verified job runs with: exactly the allowlist of its confinement, nothing of the runtime's own
     * environment.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface Environment {

        /**
         * @param job the verified job
         * @return the complete environment of the job
         */
        Map<String, String> of(VerifiedJob job);
    }

    /**
     * The parser of a job's kind: what it finds in the output of an ended job, the launch report stripped.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface OutputParser {

        /**
         * @param kind   the kind of the job
         * @param output the job's output, the job log after its launch report
         * @return what the parser found
         */
        ParseResult parse(JobId.Kind kind, String output);
    }

    /**
     * The job of a primitive the engine port starts.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface PrimitiveJobs {

        /**
         * @param request the state and primitive the engine starts a job for
         * @return the job of the primitive; empty for a primitive that has no job spec
         */
        Optional<JobRunner.StartRequest<JobRequest>> resolve(JobStarter.StartRequest request);
    }

    /**
     * Starts a job of a project, waiting until its post-spawn record is written.
     *
     * @param request what to start
     * @return the id of the started job, also of one whose launcher failed and which is settled already; or the
     *         refusal of its verification, recorded as the job's fate {@code refused}
     * @throws IllegalArgumentException if the kind is a worker, or the scope does not belong to a project
     * @throws UncheckedIOException     if a record cannot be written
     * @throws StoreRefusal             if a record of the job's store cannot be read
     */
    @Override
    public JobRunner.StartResult start(JobRunner.StartRequest<JobRequest> request) {
        Objects.requireNonNull(request, "request");
        if (request.kind() == JobId.Kind.LLM) {
            throw new IllegalArgumentException("A worker is launched by the supervisor with its worker block");
        }
        var budget = JobBudget.of(request);
        var store = JobRecordStore.ofProject(base, request.request().projectId());
        // refuses a scope outside the project before anything is verified or written
        store.lockKey(request.scope());
        JobVerification verification;
        try (var transaction = lockManager.openTransaction()) {
            verification = pipeline.verify(transaction, request.request());
        }
        return switch (verification) {
            case JobVerification.Refused(var refusal) -> refuse(request, budget, store, refusal.reason().code());
            case JobVerification.Verified(var job) -> launch(request, budget, store, job);
        };
    }

    /**
     * Refuses every probe: this runner starts no probe.
     *
     * @param request the probe
     * @return the refusal {@value #PROBE_NOT_SUPPORTED}
     */
    @Override
    public JobRunner.StartResult startProbe(ProbeRequest<Void> request) {
        Objects.requireNonNull(request, "request");
        return new JobRunner.StartResult.Refused(PROBE_NOT_SUPPORTED);
    }

    /**
     * Cancels a running job: its termination is carried out by the virtual thread that waits for the job, and the
     * cause decides the fate.
     *
     * @param jobId the job
     * @param cause why the job is cancelled
     * @return whether the job was running and this call decided its termination; {@code false} for a job that has
     *         ended, is cancelled already or is unknown
     */
    @Override
    public boolean cancel(JobId jobId, CancelCause cause) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(cause, "cause");
        var job = running.get(jobId);
        return job != null && job.cancel(termination(cause));
    }

    /**
     * Reads the record of a job this runner started, from its store.
     *
     * @param jobId the job
     * @return the record; empty for a job this runner did not start
     * @throws StoreRefusal         if the record is unreadable
     * @throws UncheckedIOException if the record cannot be read
     */
    @Override
    public Optional<JobRecord> record(JobId jobId) {
        Objects.requireNonNull(jobId, "jobId");
        return Optional.ofNullable(stores.get(jobId)).flatMap(store -> store.read(jobId));
    }

    /**
     * Starts the job of a job state through the engine port.
     *
     * @param request the state the job is started for
     * @return the started job, or the refusal: {@value #PRIMITIVE_WITHOUT_JOB} for a primitive without a job spec,
     *         otherwise the refusal of the job's verification
     */
    @Override
    public JobStarter.StartResult start(JobStarter.StartRequest request) {
        Objects.requireNonNull(request, "request");
        var job = primitives.resolve(request).filter(resolved -> resolved.kind() != JobId.Kind.LLM);
        if (job.isEmpty()) {
            return new JobStarter.StartResult.Refused(PRIMITIVE_WITHOUT_JOB);
        }
        return switch (start(job.get())) {
            case JobRunner.StartResult.Started(var jobId) -> new JobStarter.StartResult.Started(jobId.value());
            case JobRunner.StartResult.Refused(var reason) -> new JobStarter.StartResult.Refused(reason);
        };
    }

    /** @return the jobs whose process has not been settled yet */
    public Set<JobId> runningJobs() {
        return Set.copyOf(running.keySet());
    }

    /**
     * Waits until a job is settled.
     *
     * @param jobId   the job
     * @param timeout how long to wait at most
     * @return the settled record; empty if the job did not settle within the timeout, the waiting thread was
     *         interrupted, or the runner does not know the job
     */
    public Optional<JobRecord> awaitSettled(JobId jobId, Duration timeout) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(timeout, "timeout");
        var job = running.get(jobId);
        if (job == null) {
            return record(jobId).filter(found -> found.status().isSettled());
        }
        try {
            return Optional.of(job.settled.get(Math.max(0, timeout.toMillis()), TimeUnit.MILLISECONDS))
                    .filter(found -> found.status().isSettled());
        } catch (TimeoutException | ExecutionException _) {
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Settles a refused job in the one write of its record. */
    private JobRunner.StartResult refuse(JobRunner.StartRequest<JobRequest> request, JobBudget budget,
            JobRecordStore store, String reason) {
        var outcome = OutcomeDerivation.derive(new RefusedBeforeSpawn(reason));
        var now = clock.instant();
        JobRecord settled;
        try (var transaction = lockManager.openTransaction()) {
            var jobId = store.allocate(transaction, request.kind(), request.scope());
            settled = newRecord(jobId, request, budget, null, now).status(Status.REFUSED).fate(outcome.fate())
                    .outcomeRow(outcome.row()).refusalReason(reason).endedAt(now).build();
            store.writeFate(transaction, settled);
        }
        stores.put(settled.jobId(), store);
        LOGGER.info(INFO.JOB_SETTLED, settled.jobId(), settled.fate().wireName(), settled.outcomeRow());
        return new JobRunner.StartResult.Refused(reason);
    }

    /** Writes the write-ahead record, launches the job and writes its post-spawn record. */
    private JobRunner.StartResult launch(JobRunner.StartRequest<JobRequest> request, JobBudget budget,
            JobRecordStore store, VerifiedJob verified) {
        JobRecord ahead;
        try (var transaction = lockManager.openTransaction()) {
            var jobId = store.allocate(transaction, request.kind(), request.scope());
            ahead = newRecord(jobId, request, budget, argvDigest(verified.argv()), clock.instant())
                    .status(Status.RUNNING).build();
            store.writeAhead(transaction, ahead);
        }
        var jobId = ahead.jobId();
        stores.put(jobId, store);
        var job = new RunningJob(ahead, store, budget);
        running.put(jobId, job);
        var log = new JobLog(store.logPath(jobId));
        var spawnedAt = clock.instant();
        Launched launched;
        try {
            launched = launcher.launch(Launch.of(verified, environment.of(verified), request.stdin(), log.path()));
        } catch (IOException e) {
            LOGGER.debug(e, "The launcher of job %s could not be started", jobId);
            settleUnlaunched(job, log);
            return new JobRunner.StartResult.Started(jobId);
        }
        job.launched(launched.process());
        var deadline = spawnedAt.plus(budget.duration());
        var awaited = awaitReport(launched.process(), log, deadline);
        awaited.report().ifPresent(report -> writePostSpawn(job, launched, report, spawnedAt));
        Thread.ofVirtual().name("job-" + jobId).start(() -> supervise(job, launched, spawnedAt,
                awaited.observedLength()));
        return new JobRunner.StartResult.Started(jobId);
    }

    /** The post-spawn replace: from it on the job counts as running. */
    private void writePostSpawn(RunningJob job, Launched launched, LaunchReport report, Instant spawnedAt) {
        var postSpawn = job.record.toBuilder().pid(report.pid()).processStartedAt(report.processStartedAt())
                .spawnedAt(spawnedAt).confinement(report.confinement()).landlockAbi(report.landlockAbi()).build();
        try (var transaction = lockManager.openTransaction()) {
            job.store.writePostSpawn(transaction, postSpawn);
        } catch (UncheckedIOException | StoreRefusal e) {
            termination.terminate(launched.pid());
            running.remove(postSpawn.jobId());
            throw e;
        }
        job.record = postSpawn;
        LOGGER.info(INFO.JOB_STARTED, postSpawn.jobId(), postSpawn.kind().wireName(), report.pid(),
                job.budget.seconds());
    }

    /** The launch report and how much of the log was read for it. */
    private record AwaitedReport(Optional<LaunchReport> report, long observedLength) {
    }

    /**
     * Reads the job log until its first line is complete, the process has ended or the deadline has passed.
     */
    private AwaitedReport awaitReport(Process process, JobLog log, Instant deadline) {
        var interrupted = false;
        try {
            while (true) {
                var alive = process.isAlive();
                if (log.readLines(0) instanceof JobLog.Lines lines
                        && (lines.report().isPresent() || !lines.lines().isEmpty())) {
                    return new AwaitedReport(lines.report(), lines.nextOffset());
                }
                if (!alive || interrupted || !clock.instant().isBefore(deadline)) {
                    return new AwaitedReport(Optional.empty(), 0);
                }
                try {
                    Thread.sleep(REPORT_POLL_INTERVAL);
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Waits for the end of the job, terminates it for a cancel or its budget, and settles it. */
    private void supervise(RunningJob job, Launched launched, Instant spawnedAt, long observedLength) {
        var interrupted = false;
        try {
            var process = launched.process();
            var deadline = spawnedAt.plus(job.budget.duration());
            Termination cause = null;
            TerminationProtocol.Report report = null;
            var awaited = awaitEndOrCause(job, process, deadline);
            interrupted = awaited.interrupted();
            if (awaited.cause().isPresent()) {
                cause = awaited.cause().get();
                var pid = job.record.isSpawnRecorded() ? job.record.pid() : launched.pid();
                report = termination.terminate(pid);
                if (report.ending() == Ending.GROUP_KILLED) {
                    LOGGER.warn(WARN.JOB_TERMINATION_ESCALATED, job.record.jobId(), pid, cause.name());
                } else if (report.ending() == Ending.SIGNAL_REFUSED) {
                    process.destroyForcibly();
                }
            }
            var exit = waitFor(process);
            interrupted |= exit.interrupted();
            settle(job, new Ended(spawnedAt, clock.instant(), exit.value(), Optional.ofNullable(cause),
                    report != null && report.cancelBeforeGroup()), observedLength);
        } finally {
            running.remove(job.record.jobId());
            job.settled.complete(job.record);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** What ended the wait for a job: its exit, or the cause of its termination. */
    private record Awaited(Optional<Termination> cause, boolean interrupted) {
    }

    private Awaited awaitEndOrCause(RunningJob job, Process process, Instant deadline) {
        var exit = process.onExit();
        var interrupted = false;
        while (true) {
            if (exit.isDone()) {
                return new Awaited(Optional.empty(), interrupted);
            }
            if (job.cause.isDone()) {
                return new Awaited(Optional.of(job.cause.join()), interrupted);
            }
            var remaining = Duration.between(clock.instant(), deadline);
            if (!remaining.isPositive()) {
                job.cause.complete(Termination.BUDGET_EXPIRED);
                continue;
            }
            try {
                CompletableFuture.anyOf(exit, job.cause).get(Math.max(1, remaining.toMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException _) {
                // the budget is checked at the top of the loop
            } catch (InterruptedException _) {
                interrupted = true;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Neither the exit nor the cause of a job completes exceptionally", e);
            }
        }
    }

    /** The exit value of an ended process. */
    private record Exit(int value, boolean interrupted) {
    }

    private static Exit waitFor(Process process) {
        var interrupted = false;
        while (true) {
            try {
                return new Exit(process.waitFor(), interrupted);
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }
    }

    /**
     * What the runner observed of an ended process.
     *
     * @param spawnedAt         the instant the job was spawned
     * @param endedAt           the instant the runner observed the exit
     * @param exitValue         the exit value of the process as Java reports it
     * @param termination       why the runner terminated the job; empty when it did not
     * @param cancelBeforeGroup whether the termination reached the job before its group existed
     */
    private record Ended(Instant spawnedAt, Instant endedAt, int exitValue, Optional<Termination> termination,
    boolean cancelBeforeGroup) {
    }

    /** Derives the fate of an ended process job and settles it. */
    private void settle(RunningJob job, Ended ended, long observedLength) {
        var current = job.record;
        var waitStatus = waitStatus(ended.exitValue());
        LogState logState;
        ParseResult parse;
        switch (new JobLog(job.store.logPath(current.jobId())).readWhole(observedLength)) {
            case JobLog.Complete complete -> {
                logState = LogState.READABLE;
                parse = LaunchReport.isLauncherFailure(complete.reading(), ended.exitValue()) ? ParseResult.NOTHING
                        : parser.parse(current.kind(), complete.reading().jobOutput());
            }
            case JobLog.Unavailable unavailable -> {
                logState = unavailable.state();
                parse = ParseResult.NOTHING;
            }
        }
        var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), ended.termination(), waitStatus,
                logState, parse, Set.of(), true));
        var settled = current.toBuilder().status(Status.ENDED).fate(outcome.fate()).outcomeRow(outcome.row())
                .spawnedAt(ended.spawnedAt()).endedAt(ended.endedAt())
                .exitCode(waitStatus instanceof Exited(var code) ? Long.valueOf(code) : null)
                .signal(waitStatus instanceof Signalled(var signal) ? signal : null)
                .childWallMs(childWallMs(current.processStartedAt(), ended.spawnedAt(), ended.endedAt()))
                .cancelBeforeGroup(ended.cancelBeforeGroup())
                .selfReportContradiction(outcome.row() == 6 ? parse.selfReportContradiction().orElse(null) : null)
                .build();
        settle(job, settled);
    }

    /** Settles a job whose launcher could not be started: it had no process, and its fate is the launcher's. */
    private void settleUnlaunched(RunningJob job, JobLog log) {
        try {
            var current = job.record;
            var logState = log.readWhole(0) instanceof JobLog.Unavailable unavailable ? unavailable.state()
                    : LogState.READABLE;
            var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), Optional.empty(),
                    new Exited(EXECVE_FAILED), logState, ParseResult.NOTHING, Set.of(), true));
            settle(job, current.toBuilder().status(Status.ENDED).fate(outcome.fate()).outcomeRow(outcome.row())
                    .endedAt(clock.instant()).build());
        } finally {
            running.remove(job.record.jobId());
            job.settled.complete(job.record);
        }
    }

    /** Runs the settlement of a settled record and logs its result. */
    private void settle(RunningJob job, JobRecord settled) {
        var report = JobSettlement.of(job.store, lockManager).settle(new Settled(settled, Optional.empty()));
        var steps = report.steps();
        if (!steps.isEmpty() && steps.getFirst().outcome() instanceof Outcome.Done) {
            job.record = settled;
            LOGGER.info(INFO.JOB_SETTLED, settled.jobId(), settled.fate().wireName(), settled.outcomeRow());
        }
        report.stoppedAt().ifPresent(step -> LOGGER.error(ERROR.JOB_SETTLEMENT_STEP_FAILED, settled.jobId(), step,
                ((Outcome.Failed) steps.getLast().outcome()).reason()));
    }

    /**
     * The wall time of the child from its reported start to the exit the runner observed, bounded by the job's wall
     * time; {@code null} without a reported start.
     */
    static Long childWallMs(Instant processStartedAt, Instant spawnedAt, Instant endedAt) {
        if (processStartedAt == null) {
            return null;
        }
        var jobWall = Math.max(0, Duration.between(spawnedAt, endedAt).toMillis());
        var childWall = Duration.between(processStartedAt, endedAt).toMillis();
        return Math.clamp(childWall, 0, jobWall);
    }

    /** The wait status of an exit value as Java reports it: a signal ended the process for {@code 128 + n}. */
    static WaitStatus waitStatus(int exitValue) {
        var signal = exitValue - SIGNAL_EXIT_BASE;
        if (signal < 1 || signal > HIGHEST_SIGNAL) {
            return new Exited(exitValue);
        }
        return new Signalled(signal <= SIGNAL_NAMES.size() ? SIGNAL_NAMES.get(signal - 1) : "SIG" + signal);
    }

    /**
     * The SHA-256 of a verified argument vector: every element as UTF-8, each followed by a {@code NUL} byte, so no
     * two vectors share their digest by splitting an element differently.
     *
     * @param argv the verified argument vector
     * @return the digest, lower-case hexadecimal
     */
    static String argvDigest(List<String> argv) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", e);
        }
        for (var element : argv) {
            digest.update(element.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** @return the termination of a cancel cause */
    static Termination termination(CancelCause cause) {
        return switch (cause) {
            case ENGINE_CANCEL -> Termination.ENGINE_CANCEL;
            case PLAN_PAUSED_NOW -> Termination.PAUSE_NOW;
            case SUPERVISOR_END -> Termination.SUPERVISOR_END;
            case BUDGET_EXPIRED -> Termination.BUDGET_EXPIRED;
            case DRAIN -> Termination.DRAIN;
        };
    }

    private static JobRecord.JobRecordBuilder newRecord(JobId jobId, JobRunner.StartRequest<JobRequest> request,
            JobBudget budget, String argvDigest, Instant createdAt) {
        return JobRecord.builder().jobId(jobId).execution(Execution.PROCESS).scope(request.scope())
                .budgetKey(budget.key()).budgetSeconds(budget.seconds()).budgetDerivation(budget.derivation())
                .argvDigest(argvDigest).confinement(Confinement.UNAVAILABLE).createdAt(createdAt)
                .clampedValues(List.of());
    }

    /** A job between its write-ahead record and its settlement. */
    private static final class RunningJob {

        private final JobRecordStore store;
        private final JobBudget budget;
        private final CompletableFuture<Termination> cause = new CompletableFuture<>();
        private final CompletableFuture<JobRecord> settled = new CompletableFuture<>();
        private volatile JobRecord record;
        private volatile Process process;

        RunningJob(JobRecord record, JobRecordStore store, JobBudget budget) {
            this.record = record;
            this.store = store;
            this.budget = budget;
        }

        void launched(Process launched) {
            this.process = launched;
        }

        /** @return whether this call decided the termination of the job */
        boolean cancel(Termination termination) {
            var current = process;
            return (current == null || current.isAlive()) && cause.complete(termination);
        }
    }
}
