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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lombok.Builder;

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
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
 * job keeps the write-ahead record and is settled from the launcher's exit. A post-spawn record that cannot be written
 * terminates the job's process group, and the job is settled from its observed end before the failure is passed on.
 * </li>
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
 * While the runtime drains ({@link #stopAdmitting()}), no job is admitted: a job whose start is asked for then is
 * verified and recorded {@code queued}, never spawned, and settled {@code killed} without a signal at exit
 * ({@link #settleQueued()}). The admission decision and the stop of the admission are taken under one lock, so a job
 * admitted before the stop is among {@link #runningJobs()} when the drain waits for them.
 * <p>
 * The engine port maps a {@link JobStarter.StartRequest} to a job through the caller's {@link PrimitiveJobs}; a
 * primitive with no job spec is refused {@value #PRIMITIVE_WITHOUT_JOB}. The id it answers keeps the port's grammar
 * {@code ^[bcgp]-[0-9a-f]{16}$}. This runner starts no worker, whose launch belongs to the supervisor.
 * <p>
 * A probe ({@link #startProbe}) runs only with the operator's consent of its kind; without it the start is refused
 * {@value #CONSENT_REQUIRED} and nothing is recorded. Its step 0 is the {@link ProbeVerification} against its
 * {@link ProbeCandidate candidate record} instead of an enrolment, run under the lock of its scope together with the
 * allocation of its {@code v-} job id and its first record. Its record and log lie in the store of its scope: the
 * machine scope's {@code machine/jobs/} or the onboarding entry's {@code onboardings/<entry_id>/jobs/}. A probe that
 * does not run in a checkout runs in the empty {@code cwd/} of its job-private directory
 * {@code <base>/run/jobs/<job_id>/}, below the real path of the base directory. Its budget is the fixed budget of its kind ({@link ProbeBudget}); rows 1 to 4 of
 * the outcome derivation and its success marker, an exit code the probe declares, decide its fate, and otherwise its
 * exit code and output are left to the step that asked for it, which the runner's output parser does not read. The
 * runner never starts a probe again.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Subprocess Lifecycle &
 * Process-Group Isolation; Process Termination & Cancellation Protocol; Outcome Derivation);
 * {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record);
 * {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Job Budgets).
 *
 * @since 0.1
 */
public final class SubprocessJobRunner implements JobRunner<JobRequest, ProbeCandidate>, JobStarter {

    private static final CuiLogger LOGGER = new CuiLogger(SubprocessJobRunner.class);

    /** The refusal of the engine port for a primitive that names no job. */
    public static final String PRIMITIVE_WITHOUT_JOB = "primitive_without_job";

    /** The refusal of a probe whose kind the operator did not consent to. */
    public static final String CONSENT_REQUIRED = "consent_required";

    /** The directory of the job-private directories below the base directory. */
    static final String JOB_PRIVATE_DIRECTORIES = "run/jobs";

    /** The working directory of a probe below its job-private directory. */
    static final String JOB_PRIVATE_CWD = "cwd";

    /** The mode of a job-private directory, {@code 0700} like everything the runtime creates below its base. */
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    /** How often the job log is read for the launch report after the spawn. */
    static final Duration REPORT_POLL_INTERVAL = Duration.ofMillis(10);

    /**
     * The launcher's own exit for a failed {@code execve}; a launcher that cannot be started at all is settled by the
     * same exit, because the job had no process either way.
     */
    static final int EXECVE_FAILED = 127;

    /** The exit value Java reports for a process ended by a signal is this base plus the signal number. */
    private static final int SIGNAL_EXIT_BASE = 128;

    /** The highest signal number of Linux, its real-time signals included; the numbers of macOS end at 31. */
    private static final int HIGHEST_SIGNAL = 64;

    /** The signal names of the operating system the runtime runs on. */
    private static final SignalTable SIGNALS = SignalTable.of(System.getProperty("os.name", ""));

    /**
     * The closed tables of the names of the signals 1 to 31 by their numbers. The two systems agree on 19 numbers, 1
     * to 6, 8, 9, 11, 13 to 15, 21, 22 and 24 to 28, and name the other 12 differently: 7, 10, 12, 16 to 20, 23 and
     * 29 to 31 (on macOS, for example, 7 is {@code SIGEMT}, 10 {@code SIGBUS}, 12 {@code SIGSYS}, and 30 and 31 are
     * {@code SIGUSR1} and {@code SIGUSR2}). A number above a table, such as a real-time signal of Linux, is named
     * {@code SIG<n>}.
     */
    enum SignalTable {

        /** The numbering of Linux (x86-64 and AArch64). */
        LINUX(List.of("SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP", "SIGABRT", "SIGBUS", "SIGFPE", "SIGKILL",
            "SIGUSR1", "SIGSEGV", "SIGUSR2", "SIGPIPE", "SIGALRM", "SIGTERM", "SIGSTKFLT", "SIGCHLD", "SIGCONT",
            "SIGSTOP", "SIGTSTP", "SIGTTIN", "SIGTTOU", "SIGURG", "SIGXCPU", "SIGXFSZ", "SIGVTALRM", "SIGPROF",
            "SIGWINCH", "SIGIO", "SIGPWR", "SIGSYS")),

        /** The numbering of macOS. */
        MACOS(List.of("SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP", "SIGABRT", "SIGEMT", "SIGFPE", "SIGKILL",
                "SIGBUS", "SIGSEGV", "SIGSYS", "SIGPIPE", "SIGALRM", "SIGTERM", "SIGURG", "SIGSTOP", "SIGTSTP",
                "SIGCONT", "SIGCHLD", "SIGTTIN", "SIGTTOU", "SIGIO", "SIGXCPU", "SIGXFSZ", "SIGVTALRM", "SIGPROF",
                "SIGWINCH", "SIGINFO", "SIGUSR1", "SIGUSR2"));

        private final List<String> names;

        SignalTable(List<String> names) {
            this.names = names;
        }

        /**
         * @param osName the name of the operating system, as the system property {@code os.name} gives it
         * @return the table of macOS for a name that starts with {@code Mac}, otherwise the table of Linux
         */
        static SignalTable of(String osName) {
            return osName.toLowerCase(Locale.ROOT).startsWith("mac") ? MACOS : LINUX;
        }

        /** @return the name of a signal number, {@code SIG<n>} for a number the table does not list */
        String name(int signal) {
            return signal >= 1 && signal <= names.size() ? names.get(signal - 1) : "SIG" + signal;
        }
    }

    private final Path base;
    private final LockManager lockManager;
    private final VerifyNotResolvePipeline pipeline;
    private final PmExecLauncher launcher;
    private final TerminationProtocol termination;
    private final Environment environment;
    private final ProbeEnvironment probeEnvironment;
    private final OutputParser parser;
    private final PrimitiveJobs primitives;
    private final InstantSource clock;
    private final Map<JobId, JobRecordStore> stores = new ConcurrentHashMap<>();
    private final Map<JobId, RunningJob> running = new ConcurrentHashMap<>();
    private final Map<JobId, QueuedJob> queued = new ConcurrentHashMap<>();

    /** Guards every admission decision against {@link #stopAdmitting()}. */
    private final ReentrantLock admission = new ReentrantLock();

    /** Whether the runtime drains; read and written under {@link #admission}. */
    private boolean draining;

    /** The outcome of a job still queued at drain: {@code killed}, row 4, nothing signalled. */
    private static final OutcomeDerivation.Outcome QUEUED_AT_DRAIN =
            OutcomeDerivation.derive(new JobEvidence.NeverStarted(JobEvidence.NeverStarted.Cause.QUEUED_AT_DRAIN));

    /** A job recorded {@code queued} during the drain, never spawned. */
    private record QueuedJob(JobRecord record, JobRecordStore store) {
    }

    /**
     * @param base        the base directory of the runtime, below which the job records of a project lie
     * @param lockManager the lock manager of the runtime
     * @param pipeline    the verify-not-resolve pipeline
     * @param launcher    the launcher {@code pm-exec}
     * @param termination the termination protocol
     * @param environment      the environment of a verified job
     * @param probeEnvironment the environment of a verified probe
     * @param parser           the parser of the job log of an ended job that is no probe
     * @param primitives       the jobs of the primitives the engine port starts
     * @param clock            the clock of the records and the budgets; {@code null} for the system clock
     * @throws NullPointerException if an argument other than the clock is {@code null}
     */
    @Builder
    private SubprocessJobRunner(Path base, LockManager lockManager, VerifyNotResolvePipeline pipeline,
            PmExecLauncher launcher, TerminationProtocol termination, Environment environment,
            ProbeEnvironment probeEnvironment, OutputParser parser, PrimitiveJobs primitives, InstantSource clock) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.termination = Objects.requireNonNull(termination, "termination");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.probeEnvironment = Objects.requireNonNull(probeEnvironment, "probeEnvironment");
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
     * The environment a verified probe runs with: the rows of the job environment for every job type, and a
     * {@code PATH} of only the directories of the probed executable and its interpreter.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface ProbeEnvironment {

        /**
         * @param candidate the candidate record of the verified probe
         * @return the complete environment of the probe
         */
        Map<String, String> of(ProbeCandidate candidate);
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
     * @throws IllegalArgumentException if the kind is a worker, or the scope does not belong to a project; also, after
     *                                  the job is settled as its launcher's exit {@value #EXECVE_FAILED}, if its
     *                                  launch cannot be composed, for example for an environment variable the
     *                                  operating system refuses
     * @throws NullPointerException     after the job is settled as its launcher's exit {@value #EXECVE_FAILED}, if
     *                                  the environment of its launch names a {@code null}
     * @throws UncheckedIOException     if a record cannot be written; a job whose post-spawn record is not written is
     *                                  terminated and settled first
     * @throws StoreRefusal             if a record of the job's store cannot be read; a job whose post-spawn record
     *                                  is refused so is terminated and settled first
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
     * Starts a probe of a program that is not enrolled yet, waiting until its post-spawn record is written. Its
     * allocation, its verification against its candidate record and its first record are one transaction under the
     * lock of its scope.
     *
     * @param request the candidate and whether the operator consented to its kind
     * @return the id of the started probe job, also of one whose launcher failed and which is settled already; the
     *         refusal {@value #CONSENT_REQUIRED} without a record for a probe without consent; or the refusal of its
     *         verification, recorded as the probe's fate {@code refused}
     * @throws UncheckedIOException     if a record or the job-private directory cannot be written, or the base
     *                                  directory cannot be resolved to its real path; a probe whose post-spawn record
     *                                  is not written is terminated and settled first
     * @throws StoreRefusal             if a record of the probe's store cannot be read; a probe whose post-spawn
     *                                  record is refused so is terminated and settled first
     * @throws IllegalArgumentException after the probe is settled as its launcher's exit {@value #EXECVE_FAILED}, if
     *                                  its launch cannot be composed, for example for an environment variable the
     *                                  operating system refuses
     * @throws NullPointerException     after the probe is settled as its launcher's exit {@value #EXECVE_FAILED}, if
     *                                  the environment of its launch names a {@code null}
     */
    @Override
    public JobRunner.StartResult startProbe(ProbeRequest<ProbeCandidate> request) {
        Objects.requireNonNull(request, "request");
        if (!request.consented()) {
            return new JobRunner.StartResult.Refused(CONSENT_REQUIRED);
        }
        var candidate = request.candidate();
        var budget = ProbeBudget.of(candidate.kind());
        var scope = candidate.scope();
        var store = scope.type() == JobRecord.Scope.Type.MACHINE ? JobRecordStore.ofMachine(base)
                : JobRecordStore.ofEntry(base, scope.id());
        JobRecord first;
        ProbeVerification.Result verification;
        RunningJob job = null;
        admission.lock();
        try (var transaction = lockManager.openTransaction()) {
            var jobId = store.allocate(transaction, JobId.Kind.PROBE, scope);
            var jobPrivate = jobPrivateCwd(jobId);
            var workingDirectory = switch (candidate.workingDirectory()) {
                case ProbeCandidate.CheckoutRoot(var root) -> root;
                case ProbeCandidate.JobPrivate _ -> createDirectories(jobPrivate);
            };
            verification = ProbeVerification.verify(candidate, candidate.arguments(), workingDirectory, jobPrivate);
            var now = clock.instant();
            var record = newRecord(jobId, scope, budget, null, now);
            if (verification instanceof ProbeVerification.Refused(var refusal)) {
                var outcome = OutcomeDerivation.derive(new RefusedBeforeSpawn(refusal.reason().code()));
                first = record.status(Status.REFUSED).fate(outcome.fate()).outcomeRow(outcome.row())
                        .refusalReason(refusal.reason().code()).endedAt(now).build();
                store.writeFate(transaction, first);
                stores.put(jobId, store);
            } else {
                record.argvDigest(argvDigest(((ProbeVerification.Verified) verification).argv()));
                first = admit(transaction, store, record);
                if (first.status() == Status.RUNNING) {
                    job = new RunningJob(first, store, budget, candidate.successExitCodes());
                    running.put(jobId, job);
                }
            }
        } finally {
            admission.unlock();
        }
        if (verification instanceof ProbeVerification.Refused(var refusal)) {
            LOGGER.info(INFO.JOB_SETTLED, first.jobId(), first.fate().wireName(), first.outcomeRow());
            return new JobRunner.StartResult.Refused(refusal.reason().code());
        }
        if (job == null) {
            return new JobRunner.StartResult.Started(first.jobId());
        }
        var verified = (ProbeVerification.Verified) verification;
        return run(job, log -> new Launch(verified.workingDirectory(), verified.argv(), probeEnvironment.of(candidate),
                Optional.empty(), log));
    }

    /**
     * The admission of a verified job, under the admission lock and in the transaction that allocated its id: the
     * write-ahead record while the runner admits, otherwise the queued record of a job created during the drain, which
     * is never spawned and settled {@code killed} at exit.
     *
     * @return the record written, {@code running} or {@code queued}
     */
    private JobRecord admit(LockTransaction transaction, JobRecordStore store, JobRecord.JobRecordBuilder record) {
        JobRecord first;
        if (draining) {
            first = record.status(Status.QUEUED).build();
            store.writeQueued(transaction, first);
            queued.put(first.jobId(), new QueuedJob(first, store));
        } else {
            first = record.status(Status.RUNNING).build();
            store.writeAhead(transaction, first);
        }
        stores.put(first.jobId(), store);
        return first;
    }

    /**
     * Cancels a job. A running job's termination is carried out by the virtual thread that waits for it, and the cause
     * decides the fate; a queued job is settled at once without a signal, by its cause, and as {@code killed} for the
     * drain.
     *
     * @param jobId the job
     * @param cause why the job is cancelled
     * @return whether the job was running or queued and this call decided its end; {@code false} for a job that has
     *         ended, is cancelled already or is unknown
     */
    @Override
    public boolean cancel(JobId jobId, CancelCause cause) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(cause, "cause");
        var job = running.get(jobId);
        if (job != null) {
            return job.cancel(termination(cause));
        }
        var waiting = queued.remove(jobId);
        if (waiting == null) {
            return false;
        }
        settleQueued(waiting, cause == CancelCause.DRAIN ? QUEUED_AT_DRAIN
                : new OutcomeDerivation.Outcome(cause.fate(), cause.outcomeRow()));
        return true;
    }

    /**
     * Stops the admission of jobs for the drain: from now on a job whose start is asked for is verified and recorded
     * {@code queued}, and never spawned. The decision is taken under the admission lock, so no start admitted before
     * this call is missing from {@link #runningJobs()} after it.
     */
    public void stopAdmitting() {
        admission.lock();
        try {
            draining = true;
        } finally {
            admission.unlock();
        }
    }

    /** @return the jobs recorded {@code queued} and not settled yet */
    public Set<JobId> queuedJobs() {
        return Set.copyOf(queued.keySet());
    }

    /**
     * Settles every job still {@code queued} as {@code killed} without a signal (outcome row 4), as the drain does at
     * exit.
     *
     * @return the jobs settled by this call
     */
    public List<JobId> settleQueued() {
        var settled = new ArrayList<JobId>();
        for (var jobId : queuedJobs()) {
            var waiting = queued.remove(jobId);
            if (waiting != null) {
                settleQueued(waiting, QUEUED_AT_DRAIN);
                settled.add(jobId);
            }
        }
        return settled;
    }

    private void settleQueued(QueuedJob waiting, OutcomeDerivation.Outcome outcome) {
        settleRecord(waiting.store(), waiting.record().toBuilder().status(Status.ENDED).fate(outcome.fate())
                .outcomeRow(outcome.row()).endedAt(clock.instant()).build());
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
            settled = newRecord(jobId, request.scope(), budget, null, now).status(Status.REFUSED)
                    .fate(outcome.fate()).outcomeRow(outcome.row()).refusalReason(reason).endedAt(now).build();
            store.writeFate(transaction, settled);
        }
        stores.put(settled.jobId(), store);
        LOGGER.info(INFO.JOB_SETTLED, settled.jobId(), settled.fate().wireName(), settled.outcomeRow());
        return new JobRunner.StartResult.Refused(reason);
    }

    /** Writes the write-ahead record, launches the job and writes its post-spawn record. */
    private JobRunner.StartResult launch(JobRunner.StartRequest<JobRequest> request, JobBudget budget,
            JobRecordStore store, VerifiedJob verified) {
        JobRecord first;
        RunningJob job = null;
        admission.lock();
        try (var transaction = lockManager.openTransaction()) {
            var jobId = store.allocate(transaction, request.kind(), request.scope());
            first = admit(transaction, store, newRecord(jobId, request.scope(), budget, argvDigest(verified.argv()),
                    clock.instant()));
            if (first.status() == Status.RUNNING) {
                job = new RunningJob(first, store, budget, Set.of());
                running.put(jobId, job);
            }
        } finally {
            admission.unlock();
        }
        if (job == null) {
            return new JobRunner.StartResult.Started(first.jobId());
        }
        return run(job, log -> Launch.of(verified, environment.of(verified), request.stdin(), log));
    }

    /**
     * Launches a job whose write-ahead record is written, waits for its post-spawn record and hands it to the virtual
     * thread that waits for its end.
     * <p>
     * A job whose launch cannot be composed or whose launcher cannot be started had no process: it is settled as the
     * launcher's exit {@value #EXECVE_FAILED} and leaves the running jobs before this method returns or throws. A
     * launcher that cannot be started answers the started job; an exception of the composition of the launch (the
     * caller's environment, the {@link Launch} it is checked by, the process builder) is passed on to the caller of
     * the start once the job is settled.
     * <p>
     * A job whose process was started leaves the running jobs only settled as well: when its post-spawn record cannot
     * be written, its process group is terminated and the job is settled from its observed end
     * ({@code settleUnsupervised}) before the failure of the write is passed on to the caller.
     */
    private JobRunner.StartResult run(RunningJob job, Function<Path, Launch> launchOf) {
        var jobId = job.record.jobId();
        var log = new JobLog(job.store.logPath(jobId));
        var spawnedAt = clock.instant();
        Launched launched;
        var started = false;
        var explained = false;
        try {
            launched = launcher.launch(launchOf.apply(log.path()));
            started = true;
        } catch (IOException e) {
            explained = true;
            LOGGER.warn(e, WARN.JOB_LAUNCHER_NOT_STARTED, jobId, e.getMessage());
            return new JobRunner.StartResult.Started(jobId);
        } finally {
            if (!started) {
                if (!explained) {
                    LOGGER.warn(WARN.JOB_LAUNCHER_NOT_STARTED, jobId,
                            "the launch could not be composed, the failure is passed on to the caller");
                }
                settleUnlaunched(job, log);
            }
        }
        job.launched(launched.process());
        var deadline = spawnedAt.plus(job.budget.duration());
        var supervised = false;
        var observedLength = 0L;
        JobRecord reported = null;
        try {
            var awaited = awaitReport(launched.process(), log, deadline);
            observedLength = awaited.observedLength();
            if (awaited.report().isPresent()) {
                reported = postSpawnRecord(job.record, awaited.report().get(), spawnedAt);
                writePostSpawn(job, reported);
            }
            var length = observedLength;
            Thread.ofVirtual().name("job-" + jobId).start(() -> supervise(job, launched, spawnedAt, length));
            supervised = true;
        } finally {
            if (!supervised) {
                settleUnsupervised(job, launched, reported, spawnedAt, observedLength);
            }
        }
        return new JobRunner.StartResult.Started(jobId);
    }

    /** The record that replaces the write-ahead record once the launch report is read. */
    private static JobRecord postSpawnRecord(JobRecord writeAhead, LaunchReport report, Instant spawnedAt) {
        return writeAhead.toBuilder().pid(report.pid()).processStartedAt(report.processStartedAt())
                .spawnedAt(spawnedAt).confinement(report.confinement()).landlockAbi(report.landlockAbi()).build();
    }

    /**
     * The post-spawn replace: from it on the job counts as running.
     *
     * @throws UncheckedIOException if the record cannot be written
     * @throws StoreRefusal         if the record of the job is unreadable
     */
    private void writePostSpawn(RunningJob job, JobRecord postSpawn) {
        try (var transaction = lockManager.openTransaction()) {
            job.store.writePostSpawn(transaction, postSpawn);
        }
        job.record = postSpawn;
        LOGGER.info(INFO.JOB_STARTED, postSpawn.jobId(), postSpawn.kind().wireName(), postSpawn.pid(),
                job.budget.seconds());
    }

    /**
     * Settles a job whose process was started and that was never handed to the virtual thread that waits for it,
     * because its post-spawn record could not be written or the thread could not be started. Its process group is
     * terminated, its end is awaited, and it is settled from what the runner observed, as an ended job is, before the
     * failure is passed on to the caller of the start.
     * <p>
     * No termination of the outcome derivation names this end, so none is recorded and the evidence decides: a group
     * that the runner's signal ended is {@code killed} by row 4, a process ended by any other signal, the closest row
     * the specification names; a process that had exited before the signal is decided by its exit like every ended
     * job. A cancel that was answered as deciding the job's end in the meantime decides it by its cause, as it would
     * have on the waiting thread. The settled record carries the PID and the process start of the launch report, also when its post-spawn
     * record was not written, because the process ran.
     *
     * @param reported the post-spawn record of the launch report; {@code null} for a launcher that wrote no report
     */
    private void settleUnsupervised(RunningJob job, Launched launched, JobRecord reported, Instant spawnedAt,
            long observedLength) {
        var interrupted = false;
        try {
            // the PID of the process handle, never the one the job log reports: this path must end the process it
            // started, and a report that differs would leave it running while the wait below has no bound
            var report = termination.terminate(launched.pid());
            if (report.ending() == Ending.SIGNAL_REFUSED) {
                launched.process().destroyForcibly();
            }
            var exit = waitFor(launched.process());
            interrupted = exit.interrupted();
            if (reported != null && !job.record.isSpawnRecorded()) {
                job.record = reported;
            }
            var cancelled = job.cause.isDone() ? Optional.of(job.cause.join()) : Optional.<Termination>empty();
            settle(job, new Ended(spawnedAt, clock.instant(), exit.value(), cancelled, report.cancelBeforeGroup()),
                    observedLength);
        } finally {
            running.remove(job.record.jobId());
            job.settled.complete(job.record);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
        var waitStatus = waitStatus(ended.exitValue(), SIGNALS);
        LogState logState;
        ParseResult parse;
        switch (new JobLog(job.store.logPath(current.jobId())).readWhole(observedLength)) {
            case JobLog.Complete complete -> {
                logState = LogState.READABLE;
                parse = current.kind() == JobId.Kind.PROBE
                        || LaunchReport.isLauncherFailure(complete.reading(), ended.exitValue()) ? ParseResult.NOTHING
                        : parser.parse(current.kind(), complete.reading().jobOutput());
            }
            case JobLog.Unavailable unavailable -> {
                logState = unavailable.state();
                parse = ParseResult.NOTHING;
            }
        }
        var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), ended.termination(), waitStatus,
                logState, parse, job.successExitCodes, true));
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

    /**
     * Settles a job whose launcher could not be started: it had no process, and its fate is the launcher's exit
     * {@value #EXECVE_FAILED}. A missing job log is what a launch that was never started leaves and counts as an empty
     * one; a log that exists and cannot be read still decides row 5.
     */
    private void settleUnlaunched(RunningJob job, JobLog log) {
        try {
            var current = job.record;
            var logState = log.readWhole(0) instanceof JobLog.Unavailable unavailable
                    && unavailable.state() != LogState.MISSING ? unavailable.state() : LogState.READABLE;
            var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), Optional.empty(),
                    new Exited(EXECVE_FAILED), logState, ParseResult.NOTHING, job.successExitCodes, true));
            settle(job, current.toBuilder().status(Status.ENDED).fate(outcome.fate()).outcomeRow(outcome.row())
                    .endedAt(clock.instant()).build());
        } finally {
            running.remove(job.record.jobId());
            job.settled.complete(job.record);
        }
    }

    /** Runs the settlement of a running job's settled record. */
    private void settle(RunningJob job, JobRecord settled) {
        if (settleRecord(job.store, settled)) {
            job.record = settled;
        }
    }

    /**
     * Runs the settlement of a settled record and logs its result.
     *
     * @return whether the fate was recorded
     */
    private boolean settleRecord(JobRecordStore store, JobRecord settled) {
        var report = JobSettlement.of(store, lockManager).settle(new Settled(settled, Optional.empty()));
        var steps = report.steps();
        var recorded = !steps.isEmpty() && steps.getFirst().outcome() instanceof Outcome.Done;
        if (recorded) {
            LOGGER.info(INFO.JOB_SETTLED, settled.jobId(), settled.fate().wireName(), settled.outcomeRow());
        }
        report.stoppedAt().ifPresent(step -> LOGGER.error(ERROR.JOB_SETTLEMENT_STEP_FAILED, settled.jobId(), step,
                ((Outcome.Failed) steps.getLast().outcome()).reason()));
        return recorded;
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

    /**
     * The wait status of an exit value as Java reports it: a signal ended the process for {@code 128 + n}.
     *
     * @param exitValue the exit value
     * @param signals   the signal names of the operating system the process ran on
     * @return the exit code, or the signal by its name
     */
    static WaitStatus waitStatus(int exitValue, SignalTable signals) {
        var signal = exitValue - SIGNAL_EXIT_BASE;
        if (signal < 1 || signal > HIGHEST_SIGNAL) {
            return new Exited(exitValue);
        }
        return new Signalled(signals.name(signal));
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

    /**
     * The {@code cwd/} of the job-private directory {@code <base>/run/jobs/<job_id>/} of a job, below the real path of
     * the base directory. A symbolic link on the way to the base is the operator's and is resolved here, so the
     * {@link ProbeVerification} finds the directory canonical; a link below the base, from {@code run/} on, is not
     * resolved and still refuses the probe.
     *
     * @param jobId the job
     * @return the job-private {@code cwd/} of the job
     * @throws UncheckedIOException if the base directory cannot be resolved to its real path
     */
    Path jobPrivateCwd(JobId jobId) {
        Path realBase;
        try {
            realBase = base.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("The base directory cannot be resolved: " + base, e);
        }
        return realBase.resolve(JOB_PRIVATE_DIRECTORIES).resolve(jobId.value()).resolve(JOB_PRIVATE_CWD);
    }

    private static Path createDirectories(Path directory) {
        try {
            return Files.createDirectories(directory, OWNER_ONLY_DIRECTORY);
        } catch (IOException e) {
            throw new UncheckedIOException("The job-private directory cannot be created: " + directory, e);
        }
    }

    private static JobRecord.JobRecordBuilder newRecord(JobId jobId, JobRecord.Scope scope, JobBudget budget,
            String argvDigest, Instant createdAt) {
        return JobRecord.builder().jobId(jobId).execution(Execution.PROCESS).scope(scope)
                .budgetKey(budget.key()).budgetSeconds(budget.seconds()).budgetDerivation(budget.derivation())
                .argvDigest(argvDigest).confinement(Confinement.UNAVAILABLE).createdAt(createdAt)
                .clampedValues(List.of());
    }

    /** A job between its write-ahead record and its settlement. */
    private static final class RunningJob {

        private final JobRecordStore store;
        private final JobBudget budget;
        private final Set<Integer> successExitCodes;
        private final CompletableFuture<Termination> cause = new CompletableFuture<>();
        private final CompletableFuture<JobRecord> settled = new CompletableFuture<>();
        private volatile JobRecord record;
        private volatile Process process;

        /**
         * @param successExitCodes the exit codes a probe declares as its success; empty for every other job
         */
        RunningJob(JobRecord record, JobRecordStore store, JobBudget budget, Set<Integer> successExitCodes) {
            this.record = record;
            this.store = store;
            this.budget = budget;
            this.successExitCodes = successExitCodes;
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
