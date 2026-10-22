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
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Builder;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
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
 * <li>the launch report, the first line of the job log, is awaited for at most {@link #REPORT_WAIT} and never beyond
 * the budget, and the wait ends at once on a cancel; the post-spawn record with {@code pid},
 * {@code process_started_at}, {@code spawned_at}, {@code confinement} and {@code landlock_abi} replaces the
 * write-ahead record, and the start answers the job. A launcher that fails before {@code execve}, or that writes no
 * report within the wait, or whose job is cancelled during it, has no report to record; the start still answers the
 * job, which keeps the write-ahead record (without a PID) and is settled from the launcher's exit, the cancel or the
 * budget. A post-spawn record that cannot be written terminates the job's process group, and the job is settled from
 * its observed end before the failure is passed on.</li>
 * </ol>
 * <p>
 * A virtual thread per job then waits for the end of its process, for its budget or for a cancel, whichever comes
 * first. A cancel names its {@link JobRunner.CancelCause cause}; the cause, or the expiry of the budget, is the
 * termination the {@link TerminationProtocol} carries out against the job's process group, and it decides the fate by
 * the {@link OutcomeDerivation}: an engine cancel and a pause of the plan that holds now give {@code cancelled}, the
 * budget gives {@code timed_out}, the drain gives {@code killed}. When the runner observes the end of the process it
 * closes the cancel door of the job, one atomic point for every job: a cancel answered {@code true} before it is the
 * cause the settlement reads, also when the process had exited by itself in the meantime, and a cancel after it is
 * answered {@code false}. Once the process has ended, the fate is derived from the evidence the runner observed (its
 * wait status, the state of the job log, what the {@link OutputParser} found in it), unless that cause decides it, and
 * the job is settled by {@link JobSettlement} in the fixed write order. A status the job reports about itself is never
 * its outcome.
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
 * {@code <base>/run/jobs/<job_id>/}, below the real path of the base directory. Its budget is the fixed budget of its
 * kind ({@link ProbeBudget}); rows 1 to 4 of the outcome derivation and its success marker, an exit code the probe
 * declares, decide its fate, and otherwise its exit code and output are left to the step that asked for it, which the
 * runner's output parser does not read. The runner never starts a probe again.
 * <p>
 * A probe's descendants that leave its process group are recorded while it runs and ended with it
 * ({@link ProcessTreeTracker}, termination step 3a); a worker's descendants are the supervisor's, which starts the
 * worker.
 * <p>
 * A job leaves the running jobs only once its settlement has run, on every path the runner owns: an exception of the
 * {@link OutputParser}, of the derivation of the settled record or of the {@link TerminationProtocol}, also an
 * {@link Error} on the thread that waits for the job, is followed by a fallback settlement from what the runner observed
 * before the exception is passed on. A fallback that fails as well is followed by a last-resort attempt: the record the
 * fallback was about to write is written again, and a minimal ended record ({@code indeterminate}, row 5) is written
 * when the derivation itself failed, so the job does not stay {@code running}. What is guaranteed is that a settlement was
 * attempted: the job is settled, or its settlement stopped at a failed step ({@code PM_RUNTIME-207}), or a fault of the
 * lock manager inside the settlement itself was passed on; in the last two cases the record stays {@code running} and
 * the recovery of the next runtime start settles it. The failure is logged with {@code PM_RUNTIME-208}, which names the
 * failure and not a guarantee that the settlement ran.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Subprocess Lifecycle &
 * Process-Group Isolation; Process Termination & Cancellation Protocol; Outcome Derivation);
 * {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record);
 * {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Job Budgets).
 *
 * @since 0.1
 */
// java:S6539 does not apply: the runner implements both ports of the job subsystem, JobRunner and JobStarter, by
// design (ADR runtime-job/004), and the dependencies Sonar counts are the pipeline it composes (lock manager, store,
// record, evidence and launch types). Splitting it would put one settlement state machine into two classes that share
// the running-job map and the admission lock.
@SuppressWarnings("java:S6539")
public final class SubprocessJobRunner implements JobRunner<JobRequest, ProbeCandidate>, JobStarter {

    private static final CuiLogger LOGGER = new CuiLogger(SubprocessJobRunner.class);

    /** The refusal of the engine port for a primitive that names no job. */
    public static final String PRIMITIVE_WITHOUT_JOB = "primitive_without_job";

    /** The refusal of a probe whose kind the operator did not consent to. */
    public static final String CONSENT_REQUIRED = "consent_required";

    /** The security audit record of a descendant that outlived the sweep of a job's process tree. */
    public static final String JOB_DESCENDANT_SURVIVED = "job_descendant_survived";

    /** The detail of that record that names the descendant's executable. */
    public static final String DETAIL_EXECUTABLE = "executable";

    /** The detail of that record that names the descendant's PID. */
    public static final String DETAIL_PID = "pid";

    /** The detail of that record that names the job. */
    public static final String DETAIL_JOB_ID = "job_id";

    /** The audit sink of a runner whose caller names none: the sweep still runs, its survivors are not recorded. */
    private static final AuditSink NO_AUDIT = _ -> {
    };

    /**
     * How long a start waits for the launch report of the launcher, from the spawn: the launcher writes it right
     * before {@code execve}, after {@code setpgid} and the confinement. The wait is bounded by this and by the job's
     * budget, whichever is shorter, and ends at once when the job is cancelled.
     */
    static final Duration REPORT_WAIT = Duration.ofSeconds(10);

    /** The name of the {@code request} parameter in a null check. */
    private static final String ARG_REQUEST = "request";

    /** The name of the {@code jobId} parameter in a null check. */
    private static final String ARG_JOB_ID = "jobId";

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
     * same exit, because the job had no process either way, unless a cancel was answered {@code true} before the
     * settlement closed the job's cancel door, whose cause then decides it.
     */
    static final int EXECVE_FAILED = 127;

    /** The exit value Java reports for a process ended by a signal is this base plus the signal number. */
    private static final int SIGNAL_EXIT_BASE = 128;

    /** The highest signal number of Linux, its real-time signals included; the numbers of macOS end at 31. */
    private static final int HIGHEST_SIGNAL = 64;

    /** The signal names of the operating system the runtime runs on. */
    private static final SignalTable SIGNALS = SignalTable.of(System.getProperty("os.name", ""));

    /**
     * The default number of settled records the runner keeps after it pruned their store:
     * {@link #recordOf(JobId)} answers a settled job of this window, and the durable record of an older one is read
     * from its store by the caller. The builder's {@code settledRecordsKept} overrides it.
     */
    static final int SETTLED_RECORD_CACHE = 1024;

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
    private final Supervision supervision;
    private final AuditSink audit;

    /**
     * The store of every job this runner started and whose settlement has not recorded its fate yet. A job whose fate
     * is recorded leaves this map: its record is kept in {@link #settledRecords}, so the map does not grow with every
     * settled job.
     */
    private final Map<JobId, JobRecordStore> stores = new ConcurrentHashMap<>();

    /**
     * The most settled records of jobs whose store was pruned, newest last, read by {@link #recordOf(JobId)}. Bounded:
     * the durable record stays in the job's store, which a caller that needs an older one reads itself.
     */
    private final Map<JobId, JobRecord> settledRecords = Collections.synchronizedMap(new LinkedHashMap<>());
    private final int settledRecordsKept;
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
    private record QueuedJob(JobRecord job, JobRecordStore store) {
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
     * @param supervision      how the thread that waits for a job is started; {@code null} for a virtual thread per job
     * @param audit            where a surviving descendant of a probe is written; {@code null} for no audit record
     * @param settledRecordsKept how many settled records the runner keeps after it pruned their store; {@code null} for
     *                         {@value #SETTLED_RECORD_CACHE}
     * @throws NullPointerException if an argument other than the clock, the supervision, the audit sink and the
     *                              settled-record window is {@code null}
     */
    @Builder
    private SubprocessJobRunner(Path base, LockManager lockManager, VerifyNotResolvePipeline pipeline,
            PmExecLauncher launcher, TerminationProtocol termination, Environment environment,
            ProbeEnvironment probeEnvironment, OutputParser parser, PrimitiveJobs primitives, InstantSource clock,
            Supervision supervision, AuditSink audit, Integer settledRecordsKept) {
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
        this.supervision = supervision == null ? Supervision.VIRTUAL_THREADS : supervision;
        this.audit = audit == null ? NO_AUDIT : audit;
        this.settledRecordsKept = settledRecordsKept == null ? SETTLED_RECORD_CACHE : settledRecordsKept;
        if (this.settledRecordsKept < 1) {
            throw new IllegalArgumentException("The settled-record cache keeps at least one record: "
                    + this.settledRecordsKept);
        }
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
     * How the thread that waits for a job is started. It is a seam for the failure of the start itself, whose path
     * terminates the started process and settles the job before the failure is passed on to the caller.
     * <p>
     * An implementation starts the task asynchronously and returns once it runs. It must not run the task on the
     * caller's thread and must not start it and then throw: the runner settles the job when {@link #start} throws, so
     * a task that already runs would be settled twice.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface Supervision {

        /** Starts one virtual thread per job; the only implementation the runtime uses. */
        Supervision VIRTUAL_THREADS = (name, onFailure, task) -> Thread.ofVirtual().name(name)
                .uncaughtExceptionHandler((thread, failure) -> onFailure.accept(failure)).start(task);

        /**
         * @param name       the name of the thread
         * @param onFailure  what the thread's uncaught-exception handler runs, with the failure
         * @param task       what the thread runs
         */
        void start(String name, Consumer<Throwable> onFailure, Runnable task);
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
     * Starts a job of a project, waiting for its launch report for at most {@link #REPORT_WAIT}: the post-spawn record
     * is written when the report arrives in time, otherwise the job keeps its write-ahead record (without a PID).
     *
     * @param request what to start
     * @return the id of the started job, also of one whose launcher failed and which is settled already; or the
     *         refusal of its verification, recorded as the job's fate {@code refused}
     * @throws IllegalArgumentException if the kind is a worker, or the scope does not belong to a project; also, after
     *                                  the job is settled as its launcher's exit {@value #EXECVE_FAILED} (or by the
     *                                  cause of a cancel that decided its end first), if its launch cannot be
     *                                  composed, for example for an environment variable the operating system refuses
     * @throws NullPointerException     after the job is settled as its launcher's exit {@value #EXECVE_FAILED} (or by
     *                                  the cause of a cancel that decided its end first), if the environment of its
     *                                  launch names a {@code null}
     * @throws UncheckedIOException     if a record cannot be written; a job whose post-spawn record is not written is
     *                                  terminated and settled first
     * @throws StoreRefusal             if a record of the job's store cannot be read; a job whose post-spawn record
     *                                  is refused so is terminated and settled first
     */
    @Override
    public JobRunner.StartResult start(JobRunner.StartRequest<JobRequest> request) {
        Objects.requireNonNull(request, ARG_REQUEST);
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
     * Starts a probe of a program that is not enrolled yet, waiting for its launch report for at most
     * {@link #REPORT_WAIT} like {@link #start(JobRunner.StartRequest)}. Its allocation, its verification against its
     * candidate record and its first record are one transaction under the lock of its scope.
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
     * @throws IllegalArgumentException after the probe is settled as its launcher's exit {@value #EXECVE_FAILED} (or by
     *                                  the cause of a cancel that decided its end first), if its launch cannot be
     *                                  composed, for example for an environment variable the operating system refuses
     * @throws NullPointerException     after the probe is settled as its launcher's exit {@value #EXECVE_FAILED} (or by
     *                                  the cause of a cancel that decided its end first), if the environment of its
     *                                  launch names a {@code null}
     */
    @Override
    public JobRunner.StartResult startProbe(ProbeRequest<ProbeCandidate> request) {
        Objects.requireNonNull(request, ARG_REQUEST);
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
        try {
            try (var transaction = lockManager.openTransaction()) {
                var jobId = store.allocate(transaction, JobId.Kind.PROBE, scope);
                var jobPrivate = jobPrivateCwd(jobId);
                var workingDirectory = switch (candidate.workingDirectory()) {
                    case ProbeCandidate.CheckoutRoot(var root) -> root;
                    case ProbeCandidate.JobPrivate _ -> createDirectories(jobPrivate);
                };
                // The requested arguments are the candidate's own: the runner launches the declared vector by
                // construction, so argument_rejected cannot fire here; it is a property of verify for other callers.
                verification = ProbeVerification.verify(candidate, candidate.arguments(), workingDirectory,
                        jobPrivate);
                var now = clock.instant();
                var draft = newRecord(jobId, scope, budget, null, now);
                if (verification instanceof ProbeVerification.Refused(var refusal)) {
                    var outcome = OutcomeDerivation.derive(new RefusedBeforeSpawn(refusal.reason().code()));
                    first = draft.status(Status.REFUSED).fate(outcome.fate()).outcomeRow(outcome.row())
                            .refusalReason(refusal.reason().code()).endedAt(now).build();
                    store.writeFate(transaction, first);
                    remember(first);
                } else {
                    draft.argvDigest(argvDigest(((ProbeVerification.Verified) verification).argv()));
                    first = admit(transaction, store, draft);
                }
            }
            job = register(first, store, budget, candidate.successExitCodes());
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
    private JobRecord admit(LockTransaction transaction, JobRecordStore store, JobRecord.JobRecordBuilder draft) {
        JobRecord first;
        if (draining) {
            first = draft.status(Status.QUEUED).build();
            store.writeQueued(transaction, first);
            queued.put(first.jobId(), new QueuedJob(first, store));
        } else {
            first = draft.status(Status.RUNNING).build();
            store.writeAhead(transaction, first);
        }
        stores.put(first.jobId(), store);
        return first;
    }

    /**
     * Registers an admitted job among the running jobs, under the admission lock and only once the transaction of its
     * write-ahead record is closed, so a failure of that transaction leaves no job running that nothing launches,
     * waits for or settles.
     *
     * @return the running job; {@code null} for a job that was refused or recorded {@code queued}
     */
    private RunningJob register(JobRecord first, JobRecordStore store, JobBudget budget,
            Set<Integer> successExitCodes) {
        if (first.status() != Status.RUNNING) {
            return null;
        }
        var job = new RunningJob(first, store, budget, successExitCodes);
        running.put(first.jobId(), job);
        return job;
    }

    /**
     * Cancels a job. A running job's termination is carried out by the virtual thread that waits for it, and the cause
     * decides the fate; a queued job is settled at once without a signal, by its cause, and as {@code killed} for the
     * drain. For every running job one atomic point, its cancel door, decides which cancel is answered {@code true}: the
     * runner closes the door when it has observed the end of the job's process (or, for a job whose launch failed and
     * which has no process, as the first thing its settlement does) and reads the cause then. A cancel answered
     * {@code true} before the door closes is the cause the settlement reads, and it decides the fate by the outcome
     * derivation whatever the exit status (cancelled, row 2; timed out, row 3; killed, row 4), also when the process
     * exited by itself in the meantime; a cancel that arrives after the door closed is answered {@code false} and
     * cannot change the fate.
     *
     * @param jobId the job
     * @param cause why the job is cancelled
     * @return whether the job was running or queued and this call decided its end, so that the fate follows the cause;
     *         {@code false} for a job that has ended, is cancelled already or is unknown, and for a job
     *         whose cancel door is closed
     */
    @Override
    public boolean cancel(JobId jobId, CancelCause cause) {
        Objects.requireNonNull(jobId, ARG_JOB_ID);
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
        var settled = waiting.job().toBuilder().status(Status.ENDED).fate(outcome.fate())
                .outcomeRow(outcome.row()).endedAt(clock.instant()).build();
        if (settleRecord(waiting.store(), settled)) {
            remember(settled);
            stores.remove(settled.jobId());
        }
    }

    /**
     * Reads the record of a job this runner started: a settled record from the runner's bounded window of them, a job
     * whose fate is not recorded yet from its store.
     *
     * @param jobId the job
     * @return the record; empty for a job this runner did not start, or for a settled one outside the window
     * @throws StoreRefusal         if the record is unreadable
     * @throws UncheckedIOException if the record cannot be read
     */
    @Override
    public Optional<JobRecord> recordOf(JobId jobId) {
        Objects.requireNonNull(jobId, ARG_JOB_ID);
        var settled = settledRecords.get(jobId);
        if (settled != null) {
            return Optional.of(settled);
        }
        return Optional.ofNullable(stores.get(jobId)).flatMap(store -> store.read(jobId));
    }

    /** @return the stores the runner holds for jobs whose settlement has not recorded their fate yet */
    int storesHeld() {
        return stores.size();
    }

    /** Keeps a settled record for {@link #recordOf(JobId)}, evicting the oldest beyond the configured bound. */
    private void remember(JobRecord settled) {
        synchronized (settledRecords) {
            settledRecords.put(settled.jobId(), settled);
            while (settledRecords.size() > settledRecordsKept) {
                var eldest = settledRecords.keySet().iterator().next();
                settledRecords.remove(eldest);
            }
        }
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
        Objects.requireNonNull(request, ARG_REQUEST);
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
        Objects.requireNonNull(jobId, ARG_JOB_ID);
        Objects.requireNonNull(timeout, "timeout");
        var job = running.get(jobId);
        if (job == null) {
            return recordOf(jobId).filter(found -> found.status().isSettled());
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
        remember(settled);
        LOGGER.info(INFO.JOB_SETTLED, settled.jobId(), settled.fate().wireName(), settled.outcomeRow());
        return new JobRunner.StartResult.Refused(reason);
    }

    /**
     * Writes the write-ahead record, launches the job and, when its launch report arrives in time, writes its
     * post-spawn record.
     */
    private JobRunner.StartResult launch(JobRunner.StartRequest<JobRequest> request, JobBudget budget,
            JobRecordStore store, VerifiedJob verified) {
        JobRecord first;
        RunningJob job;
        admission.lock();
        try {
            try (var transaction = lockManager.openTransaction()) {
                var jobId = store.allocate(transaction, request.kind(), request.scope());
                first = admit(transaction, store, newRecord(jobId, request.scope(), budget,
                        argvDigest(verified.argv()), clock.instant()));
            }
            job = register(first, store, budget, Set.of());
        } finally {
            admission.unlock();
        }
        if (job == null) {
            return new JobRunner.StartResult.Started(first.jobId());
        }
        return run(job, log -> Launch.of(verified, environment.of(verified), request.stdin(), log));
    }

    /**
     * Launches a job whose write-ahead record is written, waits for its launch report for at most
     * {@link #REPORT_WAIT}, writes the post-spawn record when the report arrived and hands the job to the virtual
     * thread that waits for its end.
     * <p>
     * A job whose launch cannot be composed or whose launcher cannot be started had no process: it is settled as the
     * launcher's exit {@value #EXECVE_FAILED}, unless a cancel was answered {@code true} before its settlement closed
     * the cancel door, which settles it by the cause of that cancel as on every other path
     * ({@code settleUnlaunched}); a cancel after the door closed is answered {@code false}. It leaves the running jobs
     * before this method returns or throws. A launcher that cannot be started answers the started job; an
     * exception of the composition of the launch (the caller's environment, the {@link Launch} it is checked by, the
     * process builder) is passed on to the caller of the start once the job is settled.
     * <p>
     * A job whose process was started leaves the running jobs only settled as well: when its post-spawn record cannot
     * be written, or the thread that waits for it cannot be started, its process group is terminated and the job is
     * settled from its observed end ({@code settleUnsupervised}) before the failure is passed on to the caller. A
     * failure of the thread that waits for the job ({@code supervise}) is logged with {@code PM_RUNTIME-208} after that
     * thread's fallback settlement has been attempted.
     */
    private JobRunner.StartResult run(RunningJob job, Function<Path, Launch> launchOf) {
        var jobId = job.latest.jobId();
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
        var supervised = false;
        var observedLength = 0L;
        JobRecord reported = null;
        try {
            job.launched(launched.process());
            if (job.latest.kind() == JobId.Kind.PROBE) {
                // step 3a: the descendants a probe may leave the group with, recorded while it runs
                job.tracks(new ProcessTreeTracker(launched.pid(), termination.signals()));
                job.refreshTree();
            }
            var awaited = awaitReport(job, launched.process(), log, spawnedAt.plus(reportWait(job.budget)));
            observedLength = awaited.observedLength();
            if (awaited.report().isPresent()) {
                reported = postSpawnRecord(job.latest, awaited.report().get(), spawnedAt);
                writePostSpawn(job, reported);
            }
            var length = observedLength;
            supervision.start("job-" + jobId,
                    failure -> LOGGER.error(failure, ERROR.JOB_SUPERVISION_FAILED, jobId, failure),
                    () -> supervise(job, launched, spawnedAt, length));
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
        job.latest = postSpawn;
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
     * job. A cancel that was answered {@code true} before the cancel door closed, which {@link #terminateAndSettle} does
     * when it has observed the end of the process, decides it by its cause, as it would have on the waiting thread; a
     * cancel after the door closed is answered {@code false}. The settled record carries the PID and the process start
     * of the launch report, also when its post-spawn record was not written, because the process ran.
     *
     * @param reported the post-spawn record of the launch report; {@code null} for a launcher that wrote no report
     */
    private void settleUnsupervised(RunningJob job, Launched launched, JobRecord reported, Instant spawnedAt,
            long observedLength) {
        var interrupted = false;
        try {
            if (reported != null && !job.latest.isSpawnRecorded()) {
                job.latest = reported;
            }
            interrupted = terminateAndSettle(job, launched, spawnedAt, observedLength);
        } finally {
            leave(job, interrupted);
        }
    }

    /**
     * Ends the process of a job whose end the runner has not observed, waits for it and settles the job from what it
     * observed then. No termination is recorded except the cause the cancel door of the job returns when it is closed
     * after the end of the process was observed, that of a cancel answered {@code true} before it closed or of the
     * budget, which decides the fate as it would have on the waiting thread; otherwise the evidence decides.
     *
     * @return whether the wait for the process was interrupted
     */
    private boolean terminateAndSettle(RunningJob job, Launched launched, Instant spawnedAt, long observedLength) {
        var cancelBeforeGroup = terminateByHandle(job.latest.jobId(), launched);
        var exit = waitFor(launched.process());
        var endedAt = clock.instant();
        auditSurvivors(job, job.sweepTree());
        // the end of the process is observed: the cancel door closes here and the cause it returns is the one read
        settleEnded(job, new Ended(spawnedAt, endedAt, exit.value(), job.closeCancelDoor(),
                cancelBeforeGroup), observedLength);
        return exit.interrupted();
    }

    /**
     * Terminates the process group of a job by the PID of its process handle, never by the one its log reports: this
     * path must end the process the runner started, and a report that differs would leave it running while the wait
     * that follows has no bound. A PID whose live process started elsewhere was reused and is not signalled; a group
     * that outlived its leader is still the job's and is terminated with steps 1 to 3, and step 4 (the bare PID) is
     * skipped because the leader has ended. When the termination protocol does not reach the job (its signal is
     * refused, or it fails with an exception or an error), the process is destroyed through its handle, so it is never
     * left running; the failure of the protocol is logged with {@code PM_RUNTIME-132} and not passed on, so it does not
     * replace a failure this path follows.
     *
     * @return whether the termination reached the job before its group existed
     */
    // java:S1181 does not apply: an Error of the signalling primitive is caught only so the process is destroyed
    // through its handle and the job can still be settled, not to swallow it; the failure is logged.
    @SuppressWarnings("java:S1181")
    boolean terminateByHandle(JobId jobId, Launched launched) {
        var reached = false;
        var cancelBeforeGroup = false;
        try {
            // A PID reused by another process is not signalled; a group that outlived its leader is still the job's.
            if (!pidReused(launched)) {
                var report = termination.terminate(launched.pid(), launched.process().isAlive());
                reached = report.ending() != Ending.SIGNAL_REFUSED;
                cancelBeforeGroup = report.cancelBeforeGroup();
            }
        } catch (RuntimeException | Error e) {
            // a PID the protocol refuses, a signal call that failed (ProcessGroupSignals), or an error of the primitive
            LOGGER.warn(e, WARN.JOB_TERMINATION_FAILED, jobId, e.getMessage());
        } finally {
            if (!reached) {
                launched.process().destroyForcibly();
            }
        }
        return cancelBeforeGroup;
    }

    /**
     * Whether the PID of a started process now names another process, so that neither its group nor the PID itself may
     * be signalled. A PID that no process owns any more was not reused: a process group still living under that id is
     * the job's surviving group, and a group that is gone answers {@code ESRCH}. A live process of that PID that
     * started at another instant is a reused PID. A caller that must end the job anyway destroys the started process
     * through its handle, never by its PID.
     */
    static boolean pidReused(Launched launched) {
        var handle = ProcessHandle.of(launched.pid());
        if (handle.isEmpty()) {
            return false;
        }
        var current = handle.flatMap(found -> found.info().startInstant());
        var own = launched.process().toHandle().info().startInstant();
        return current.isEmpty() || own.isEmpty() || !own.get().equals(current.get());
    }

    /**
     * Writes the security audit record {@value #JOB_DESCENDANT_SURVIVED} of every descendant that outlived the sweep of
     * a job's process tree, with its PID and executable. A record the sink refuses is logged and does not stop the
     * settlement.
     */
    // java:S1181 does not apply: the audit sink is the caller's and a fault of it must not abort the settlement;
    // the failure is logged.
    @SuppressWarnings("java:S1181")
    void auditSurvivors(RunningJob job, List<ProcessTreeTracker.Survivor> survivors) {
        for (var survivor : survivors) {
            var details = new HashMap<String, Object>();
            details.put(DETAIL_JOB_ID, job.latest.jobId().value());
            details.put(DETAIL_PID, survivor.pid());
            survivor.command().ifPresent(command -> details.put(DETAIL_EXECUTABLE, command));
            try {
                audit.append(new AuditEvent(JOB_DESCENDANT_SURVIVED, AuditEvent.CATEGORY_SECURITY,
                        AuditEvent.OUTCOME_FAILED, details));
            } catch (RuntimeException | Error e) {
                LOGGER.warn(e, WARN.JOB_DESCENDANT_AUDIT_FAILED, job.latest.jobId(), e.getMessage());
            }
        }
    }

    /** Takes a job out of the running jobs and completes the wait for its settlement with its record. */
    private void leave(RunningJob job, boolean interrupted) {
        var jobId = job.latest.jobId();
        running.remove(jobId);
        if (job.latest.status().isSettled()) {
            remember(job.latest);
            stores.remove(jobId);
        }
        job.settled.complete(job.latest);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** The launch report and how much of the log was read for it. */
    record AwaitedReport(Optional<LaunchReport> report, long observedLength) {
    }

    /**
     * How long the start waits for the launch report: {@link #REPORT_WAIT}, and never longer than the job's budget.
     */
    private static Duration reportWait(JobBudget budget) {
        return REPORT_WAIT.compareTo(budget.duration()) < 0 ? REPORT_WAIT : budget.duration();
    }

    /**
     * Reads the job log until its first line is complete, the process has ended, the job is cancelled or the deadline
     * has passed. The deadline is that of the report wait, not the budget of the job: a launcher that hangs before it
     * writes its report must not hold the start for the whole budget, and a cancel ends the wait at once.
     */
    AwaitedReport awaitReport(RunningJob job, Process process, JobLog log, Instant deadline) {
        var interrupted = false;
        var nextRefresh = clock.instant().plus(ProcessTreeTracker.REFRESH_INTERVAL);
        try {
            while (true) {
                var alive = process.isAlive();
                // a descendant may detach during the report wait: refresh at least once per second, as the sweep expects
                if (alive && !clock.instant().isBefore(nextRefresh)) {
                    job.refreshTree();
                    nextRefresh = clock.instant().plus(ProcessTreeTracker.REFRESH_INTERVAL);
                }
                if (log.readLines(0) instanceof JobLog.Lines(var lines, var nextOffset, var report)
                        && (report.isPresent() || !lines.isEmpty())) {
                    return new AwaitedReport(report, nextOffset);
                }
                if (!alive || interrupted || job.cause.isDone() || !clock.instant().isBefore(deadline)) {
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

    /**
     * Waits for the end of the job, terminates it for a cancel or its budget, and settles it; runs on the virtual
     * thread of the job.
     * <p>
     * The job leaves the running jobs only once its settlement has run, also when this thread fails. A failure after
     * the end of the process was observed (the {@link OutputParser}, the settled record) is followed by the settlement
     * without the parser's finding ({@code settleEnded}); a failure before it (the wait, the termination protocol) by
     * the termination of the process through its handle, the wait for its end and the settlement from what was
     * observed then ({@code terminateAndSettle}). An {@link Error}, such as a {@link StackOverflowError} of a regular
     * expression in the parser, takes the same path, because only {@code finally} blocks carry it. The failure is
     * passed on to the thread's handler, which logs it with {@code PM_RUNTIME-208}; a failure of the fallback
     * settlement itself is followed by a last-resort ended record, so the job is not left {@code running}. A fate that
     * cannot be written stops the settlement at its failed step ({@code PM_RUNTIME-207}), and a fault of the lock
     * manager inside the settlement is passed on: in both cases the record stays {@code running} and the recovery of
     * the next runtime start settles it.
     */
    private void supervise(RunningJob job, Launched launched, Instant spawnedAt, long observedLength) {
        var interrupted = false;
        var ended = false;
        try {
            var process = launched.process();
            var deadline = spawnedAt.plus(job.budget.duration());
            Termination cause = null;
            TerminationProtocol.Report report = null;
            var awaited = awaitEndOrCause(job, process, deadline);
            interrupted = awaited.interrupted();
            if (awaited.cause().isPresent()) {
                cause = awaited.cause().get();
                // the handle's PID, never the one a launch report names: this path ends the process the runner started
                var pid = launched.pid();
                // a PID reused by another process is not signalled; a group that outlived its leader is still the job's
                if (pidReused(launched)) {
                    // the PID is never signalled, but the started process must not outlive the cause: the wait has no bound
                    process.destroyForcibly();
                } else {
                    report = termination.terminate(pid, process.isAlive());
                    if (report.ending() == Ending.GROUP_KILLED) {
                        LOGGER.warn(WARN.JOB_TERMINATION_ESCALATED, job.latest.jobId(), pid, cause.name());
                    } else if (report.ending() == Ending.SIGNAL_REFUSED) {
                        process.destroyForcibly();
                    }
                }
                job.sweepTree();
            }
            var exit = waitFor(process);
            interrupted |= exit.interrupted();
            var endedAt = clock.instant();
            auditSurvivors(job, job.sweepTree());
            ended = true;
            settleEnded(job, new Ended(spawnedAt, endedAt, exit.value(), Optional.ofNullable(cause),
                    report != null && report.cancelBeforeGroup()), observedLength);
        } finally {
            try {
                if (!ended) {
                    interrupted |= terminateAndSettle(job, launched, spawnedAt, observedLength);
                }
            } finally {
                leave(job, interrupted);
            }
        }
    }

    /**
     * What ended the wait for a job: its exit, or the cause of its termination.
     *
     * @param cause       the termination that decides the fate: the cause of a cancel that was answered {@code true},
     *                    or of the budget that expired; empty for a job nothing decided, which is settled by its exit
     * @param interrupted whether the waiting thread was interrupted meanwhile
     */
    record Awaited(Optional<Termination> cause, boolean interrupted) {
    }

    /**
     * Waits for the end of the process or for a cause that terminates it. A process whose end is observed closes the
     * cancel door of the job ({@code RunningJob.closeCancelDoor}) and returns the cause that door read, so a cancel
     * answered {@code true} before the door closed is never dropped because the exit was seen first, and one that
     * arrives after is answered {@code false}.
     */
    // java:S2142 does not apply: the interrupt is deferred, not swallowed. Re-interrupting inside the loop would make the
    // next wait throw at once; the flag travels in Awaited.interrupted() to leave(), which restores it once the job is
    // settled.
    @SuppressWarnings("java:S2142")
    Awaited awaitEndOrCause(RunningJob job, Process process, Instant deadline) {
        var exit = process.onExit();
        var interrupted = false;
        while (true) {
            if (process.isAlive()) {
                // only while the job's own process lives: a reused root PID would enrol the strangers below it
                job.refreshTree();
            }
            if (exit.isDone()) {
                return new Awaited(job.closeCancelDoor(), interrupted);
            }
            if (job.cause.isDone()) {
                return new Awaited(Optional.of(job.cause.join()), interrupted);
            }
            var remaining = Duration.between(clock.instant(), deadline);
            if (!remaining.isPositive()) {
                job.cause.complete(Termination.BUDGET_EXPIRED);
                continue;
            }
            var bound = remaining.compareTo(ProcessTreeTracker.REFRESH_INTERVAL) < 0 ? remaining
                    : ProcessTreeTracker.REFRESH_INTERVAL;
            try {
                CompletableFuture.anyOf(exit, job.cause).get(Math.max(1, bound.toMillis()), TimeUnit.MILLISECONDS);
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
    record Exit(int value, boolean interrupted) {
    }

    // java:S2142 does not apply: the interrupt is deferred, not swallowed. Re-interrupting inside the loop would make the
    // next waitFor throw at once, so the wait for the process end would never complete; the flag is returned in
    // Exit.interrupted() and every caller passes it to leave(), which restores it once the job is settled.
    @SuppressWarnings("java:S2142")
    static Exit waitFor(Process process) {
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

    /**
     * Derives the fate of an ended process job and settles it. When the derivation fails before the settlement started
     * (the {@link OutputParser}, which this runner does not own, or the settled record), the job is settled from the
     * same observation without the parser's finding, so the parser is never called again, and the failure is passed on.
     * A failure of that fallback is followed by a last-resort ended record.
     */
    // java:S1181 does not apply: an Error is caught only to settle the job from what was observed before it is passed
    // on, as the class contract requires; it is rethrown.
    @SuppressWarnings("java:S1181")
    private void settleEnded(RunningJob job, Ended ended, long observedLength) {
        try {
            settle(job, ended, observedLength, true);
        } catch (RuntimeException | Error failure) {
            if (job.settling) {
                // the primary settlement reached its record write and failed there (PM_RUNTIME-207); a fate replaces
                // only a record that is not settled yet, so a second attempt would not help
                throw failure;
            }
            try {
                settle(job, ended, observedLength, false);
            } catch (RuntimeException | Error fallbackFailure) {
                failure.addSuppressed(fallbackFailure);
                lastResort(job, failure);
            }
            throw failure;
        }
    }

    /**
     * Settles a job whose fallback settlement failed as well: the record the fallback was about to write is written
     * again, so a fault of the write is retried once; when the derivation itself failed, a minimal ended record with the
     * table's outcome for evidence the runner could not read ({@code indeterminate}, row 5) is written instead, so the
     * job is not left {@code running} until the next runtime start. A write that fails here too is logged and left to
     * the recovery.
     */
    // java:S1181 does not apply: the last-resort write is the final attempt to settle the job; an Error is logged and
    // the record stays running for the recovery instead of aborting the settlement path.
    @SuppressWarnings("java:S1181")
    private void lastResort(RunningJob job, Throwable failure) {
        var current = job.latest;
        var settled = job.pendingSettled != null ? job.pendingSettled
                : current.toBuilder().status(Status.ENDED).fate(Fate.INDETERMINATE).outcomeRow(5)
                        .endedAt(clock.instant()).build();
        try {
            settle(job, settled);
        } catch (RuntimeException | Error writeFailure) {
            failure.addSuppressed(writeFailure);
            LOGGER.error(writeFailure, ERROR.JOB_LAST_RESORT_FAILED, current.jobId(), writeFailure.getMessage());
        }
    }

    /**
     * Derives the fate of an ended process job and settles it.
     *
     * @param withParser whether the output parser reads the job's output; without it, nothing is found in it
     */
    private void settle(RunningJob job, Ended ended, long observedLength, boolean withParser) {
        var current = job.latest;
        var waitStatus = waitStatus(ended.exitValue(), SIGNALS);
        LogState logState;
        ParseResult parse;
        var launcherFailure = false;
        switch (new JobLog(job.store.logPath(current.jobId())).readWhole(observedLength)) {
            case JobLog.Complete complete -> {
                logState = LogState.READABLE;
                launcherFailure = LaunchReport.isLauncherFailure(complete.reading(), ended.exitValue());
                parse = !withParser || current.kind() == JobId.Kind.PROBE || launcherFailure ? ParseResult.NOTHING
                        : parser.parse(current.kind(), complete.reading().jobOutput());
            }
            case JobLog.Unavailable unavailable -> {
                logState = unavailable.state();
                parse = ParseResult.NOTHING;
            }
        }
        // A launcher that failed before it started the command has no exit of its own: the exit codes the probe
        // declares are codes of a probe that ran, so they do not apply to a launcher failure (127 included).
        var declaredExitCodes = launcherFailure ? Set.<Integer>of() : job.successExitCodes;
        var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), ended.termination(), waitStatus,
                logState, parse, declaredExitCodes, true));
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
     * Settles a job whose launcher could not be started: it had no process, and the launcher's exit
     * {@value #EXECVE_FAILED} decides its fate, unless a cancel decided the end of the job first. The first act is to
     * close the cancel door of the job ({@code RunningJob.closeCancelDoor}), one atomic point that decides which cancel
     * was answered {@code true} and which cause is read here: a cancel answered {@code true} before the door closed
     * (while the launch was composed or started) has decided the end, and its cause decides the fate as on every other
     * path of the runner ({@code cancelled}, outcome row 2; {@code timed_out}, row 3, for the budget; {@code killed},
     * row 4, for the drain); a cancel that arrives after the door closed is answered
     * {@code false}, because the fate is no longer open to it. The answer {@code true} and the fate by the cause are
     * therefore the same fact. A missing job log is what a launch that was never started leaves and counts as an empty
     * one; a log that exists and cannot be read still decides row 5.
     */
    private void settleUnlaunched(RunningJob job, JobLog log) {
        try {
            var cause = job.closeCancelDoor();
            var current = job.latest;
            var logState = log.readWhole(0) instanceof JobLog.Unavailable unavailable
                    && unavailable.state() != LogState.MISSING ? unavailable.state() : LogState.READABLE;
            var outcome = OutcomeDerivation.derive(new ProcessEnded(current.kind(), cause,
                    new Exited(EXECVE_FAILED), logState, ParseResult.NOTHING, Set.of(), true));
            settle(job, current.toBuilder().status(Status.ENDED).fate(outcome.fate()).outcomeRow(outcome.row())
                    .endedAt(clock.instant()).build());
        } finally {
            leave(job, false);
        }
    }

    /** Runs the settlement of a running job's settled record; from here on no fallback settles the job again. */
    private void settle(RunningJob job, JobRecord settled) {
        job.settling = true;
        job.pendingSettled = settled;
        if (settleRecord(job.store, settled)) {
            job.latest = settled;
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

    /**
     * A job between its write-ahead record and its settlement. It is package-private for the test of the cancel door,
     * whose window is a few instructions wide and has no other deterministic seam.
     */
    static final class RunningJob {

        private final JobRecordStore store;
        private final JobBudget budget;
        private final Set<Integer> successExitCodes;
        private final CompletableFuture<Termination> cause = new CompletableFuture<>();
        private final CompletableFuture<JobRecord> settled = new CompletableFuture<>();
        // java:S3077 does not apply to either volatile field: each is a reference that is replaced as a whole and never
        // mutated in place. JobRecord is an immutable record (every change builds a new one), so volatile publishes it
        // safely between the starting thread and the virtual thread that settles the job; Process is a thread-safe
        // handle that only launched() assigns, once.
        @SuppressWarnings("java:S3077")
        private volatile JobRecord latest;
        @SuppressWarnings("java:S3077")
        private volatile Process process;

        /** The process tree of a probe, refreshed while it runs so its descendants are ended with it; null otherwise. */
        // java:S3077 does not apply: the reference is replaced as a whole and never mutated in place (ProcessTreeTracker
        // is used through its own thread-safe state), so volatile publishes it safely.
        @SuppressWarnings("java:S3077")
        private volatile ProcessTreeTracker tree;

        /** The record a settlement attempt was about to write, for the last-resort retry of a failed write; null before. */
        // java:S3077 does not apply: JobRecord is immutable and the reference is replaced as a whole, never mutated.
        @SuppressWarnings("java:S3077")
        private volatile JobRecord pendingSettled;

        /**
         * Whether the settlement of the job has started; a fallback settlement runs only before it, because a fate
         * replaces only a record that is not settled yet. Read and written by the one thread that settles the job.
         */
        private boolean settling;

        /**
         * Whether the runner observed the end of the job (or found that its launch failed) and
         * {@link #closeCancelDoor()} read its cause; guarded by the monitor of this job, like the completion of
         * {@link #cause} by {@link #cancel(Termination)}.
         */
        private boolean cancelDoorClosed;

        /**
         * @param successExitCodes the exit codes a probe declares as its success; empty for every other job
         */
        RunningJob(JobRecord latest, JobRecordStore store, JobBudget budget, Set<Integer> successExitCodes) {
            this.latest = latest;
            this.store = store;
            this.budget = budget;
            this.successExitCodes = successExitCodes;
        }

        void launched(Process launched) {
            this.process = launched;
        }

        /** Records the tree of the job; {@code null} for a job whose descendants are not tracked. */
        void tracks(ProcessTreeTracker tracker) {
            this.tree = tracker;
        }

        /** Remembers the descendants the job has now; nothing for a job without a tree. */
        void refreshTree() {
            var current = tree;
            if (current != null) {
                current.refresh();
            }
        }

        /** Ends the remembered descendants still alive; nothing for a job without a tree. */
        // java:S1181 does not apply: a sweep that fails must not prevent the settlement; the failure is logged.
        @SuppressWarnings("java:S1181")
        List<ProcessTreeTracker.Survivor> sweepTree() {
            var current = tree;
            if (current == null) {
                return List.of();
            }
            try {
                return current.sweep();
            } catch (RuntimeException | Error e) {
                LOGGER.warn(e, WARN.JOB_DESCENDANT_SWEEP_FAILED, latest.jobId(), e.getMessage());
                return List.of();
            }
        }

        /**
         * The check that the process lives and the completion of the cause are one step under the monitor of this job,
         * so {@link #closeCancelDoor()} either waits for a cancel in flight and then reads its cause, or closes the door
         * before it, and the cancel is answered {@code false}.
         *
         * @return whether this call decided the termination of the job; {@code false} once the cancel door is closed,
         *         because the cause of a job is read once, when the runner observed its end, and no later cancel can
         *         be applied; also {@code false} for a process that has ended or a job a cause decided already
         */
        synchronized boolean cancel(Termination termination) {
            if (cancelDoorClosed) {
                return false;
            }
            var current = process;
            return (current == null || current.isAlive()) && cause.complete(termination);
        }

        /**
         * Closes the door of a job whose end the runner observed, or whose launch failed: from this call on
         * {@link #cancel(Termination)} answers {@code false}, and the cause this call returns is the last one a cancel
         * can have decided. The one atomic point, for every job, that settles both which cancel was answered
         * {@code true} and which cause the settlement applies; the monitor is held for the flag and the read of the
         * future only, never across I/O or a wait for the process.
         *
         * @return the cause of the cancel that was answered {@code true} before the door closed, or of the budget that
         *         expired; empty if none was
         */
        synchronized Optional<Termination> closeCancelDoor() {
            cancelDoorClosed = true;
            return cause.isDone() ? Optional.of(cause.join()) : Optional.empty();
        }
    }
}
