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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.job.JobEvidence.NeverStarted;
import de.planmarshall.runtime.job.JobSettlement;
import de.planmarshall.runtime.job.OutcomeDerivation;
import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.ProcessGroupSignals.Delivered;
import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.job.SettlementStep;
import de.planmarshall.runtime.job.TerminationProtocol;

/**
 * The jobs part of step 3 of the per-project recovery ({@link RecoveryStep.Name#JOBS_AND_WORKERS}; PM-ARCH-7,
 * PM-EXEC-4): every job a previous runtime instance left unsettled in the project's job records is settled
 * {@code ended}/{@code killed} (outcome row 4). A job is never adopted and never executed again.
 * <ul>
 * <li>A record left {@code queued} never started: nothing is signalled.</li>
 * <li>A record left {@code running} is settled by the first matching rule:
 * <ol type="a">
 * <li>its {@code execution} is {@code in_process}: the operation died with the previous runtime; nothing is signalled
 * and no orphan note is written;</li>
 * <li>its {@code spawned_at} lies before the boot time of the system: the process cannot have survived the reboot;
 * nothing is signalled;</li>
 * <li>its {@code pid} is {@code null}: the runtime ended between the spawn and the post-spawn replace; nothing is
 * signalled, and an audit record ({@value #JOB_RECOVERED}) notes {@value #ORPHAN_UNIDENTIFIABLE};</li>
 * <li>the recorded {@code pid} is held by a live process that started at another instant than the recorded
 * {@code process_started_at}: the PID was reused after the job ended, so neither its group nor the PID may be
 * signalled, and the audit record notes {@value #ORPHAN_UNIDENTIFIABLE};</li>
 * <li>otherwise the process group is probed with {@code kill(-pgid, 0)}: a group exists while any of its members
 * lives, and within one boot its id is not reused while it lives, so a live group of the recorded id is the job's
 * group, also when its leader has exited, and is terminated with steps 1 to 3 of the {@link TerminationProtocol}; a
 * group that no longer exists needs no termination.</li>
 * </ol>
 * </li>
 * </ul>
 * The settlement ({@link JobSettlement}) records the fate first and then releases the slot the caller's {@link Slots}
 * names for the job. The signal recorded is {@value #RUNTIME_RESTART} for a job nothing was sent to, or the last
 * signal the termination sent; an in-process operation records none. The worker part of the step (the fence of a
 * worker's generation, the revocation of its token, the release of its task lease and the removal of its job-private
 * directory) belongs to the launch of workers and is a seam: a worker's record is settled like every other, and the
 * report names the seam.
 * <p>
 * Every signal goes through {@link ProcessGroupSignals}, so a test asserts at the lowest signalling call that the rules
 * which send none, and a reused PID, send none. A step that cannot read the job records, hand over the orphan note or record a fate fails, and
 * the recovery of its project stops there; a job whose fate was not recorded is settled by the next recovery.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process Termination &
 * Cancellation Protocol, step 6, Live Orphans after a Runtime Crash);
 * {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record, Recovery);
 * {@code doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc} (Per-Project Recovery, step 3).
 *
 * @since 0.1
 */
public final class JobRecoveryStep implements RecoveryStep {

    /** The note of the audit record of a job whose record carries no PID, or one whose PID was reused. */
    public static final String ORPHAN_UNIDENTIFIABLE = "orphan unidentifiable";

    /** The event of the audit record of a job the recovery settled with a note. */
    public static final String JOB_RECOVERED = "job_recovered";

    /** The detail of the audit record that names the job. */
    public static final String DETAIL_JOB_ID = "job_id";

    /** The detail of the audit record that holds its note. */
    public static final String DETAIL_NOTE = "note";

    /** The signal recorded for a job of a previous runtime that nothing was sent to. */
    public static final String RUNTIME_RESTART = "runtime_restart";

    /** The outcome of every job the recovery settles: {@code killed} by row 4. */
    private static final OutcomeDerivation.Outcome KILLED =
            OutcomeDerivation.derive(new NeverStarted(NeverStarted.Cause.RUNTIME_RESTART));

    /**
     * How far the recorded {@code process_started_at} and the start instant the process table answers for a PID may
     * differ: the launch report carries the operating system's coarse start (whole seconds), so an exact match is not
     * available, while a PID reused by another process started at a different instant beyond this tolerance.
     */
    static final Duration START_TOLERANCE = Duration.ofSeconds(1);

    private final Path base;
    private final LockManager lockManager;
    private final ProcessGroupSignals signals;
    private final TerminationProtocol termination;
    private final AuditSink audit;
    private final Instant bootTime;
    private final Slots slots;
    private final InstantSource clock;

    /**
     * The slot a job of the previous runtime held, which its settlement releases.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface Slots {

        /** No slot is known for any job: nothing is released. */
        Slots NONE = job -> Optional.empty();

        /**
         * @param job the record of the job being settled
         * @return the slot it held; empty when it held none
         */
        Optional<SettlementStep.Slot> of(JobRecord job);
    }

    /**
     * The rule that settled a job, in the order of the specification.
     */
    enum Rule {
        /** Left {@code queued}. */
        QUEUED,
        /** Rule (a): an in-process operation. */
        IN_PROCESS,
        /** Rule (b): spawned before the boot of the system. */
        BEFORE_BOOT,
        /** Rule (c): no PID recorded. */
        UNIDENTIFIABLE,
        /** Rule (d): the group was probed. */
        PROBED
    }

    /**
     * The two collaborators that signal a job group in rule (d): the probe and the termination of a live group.
     *
     * @param signals     the lowest signalling primitive, for the probe of rule (d)
     * @param termination the termination protocol for a live group of rule (d)
     * @since 0.1
     */
    public record Signalling(ProcessGroupSignals signals, TerminationProtocol termination) {

        /** @throws NullPointerException if a component is {@code null} */
        public Signalling {
            Objects.requireNonNull(signals, "signals");
            Objects.requireNonNull(termination, "termination");
        }
    }

    /**
     * What the recovery decided for one unsettled job: the rule that settles it, the signal its record will carry, and
     * whether a live group was terminated for it.
     *
     * @param rule       the rule that settled the job
     * @param signal     the signal to record; {@code null} for an in-process operation, which records none
     * @param terminated whether a live group was terminated
     * @param failure    why the job cannot be settled; empty when it can
     */
    private record Handling(Rule rule, String signal, boolean terminated, Optional<String> failure) {

        static Handling failed(Rule rule, String reason) {
            return new Handling(rule, null, false, Optional.of(reason));
        }
    }

    /**
     * @param base        the base directory of the runtime, below which the job records of a project lie
     * @param lockManager the lock manager of the runtime
     * @param signalling  the signalling collaborators, for the probe and the termination of rule (d)
     * @param audit       where the orphan note of rule (c) is handed over
     * @param bootTime    the boot time of the system
     * @param slots       the slots of the jobs, released by their settlement
     * @param clock       the clock of the settled records
     * @throws NullPointerException if an argument is {@code null}
     */
    public JobRecoveryStep(Path base, LockManager lockManager, Signalling signalling, AuditSink audit,
            Instant bootTime, Slots slots, InstantSource clock) {
        this.base = Objects.requireNonNull(base, "base");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        Objects.requireNonNull(signalling, "signalling");
        this.signals = signalling.signals();
        this.termination = signalling.termination();
        this.audit = Objects.requireNonNull(audit, "audit");
        this.bootTime = Objects.requireNonNull(bootTime, "bootTime");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Name name() {
        return Name.JOBS_AND_WORKERS;
    }

    /**
     * Settles every unsettled job of the project.
     *
     * @param projectId the id of the enrolled project
     * @return the jobs settled per rule and the groups terminated; a failure when the records cannot be read, an
     *         orphan note cannot be handed over or a fate cannot be recorded
     */
    @Override
    public Outcome run(String projectId) {
        var store = JobRecordStore.ofProject(base, projectId);
        List<JobRecord> records;
        try {
            records = store.records();
        } catch (UncheckedIOException | StoreRefusal e) {
            return new Outcome.Failed("the job records of project %s were not read: %s".formatted(projectId,
                    e.getMessage()));
        }
        var counts = new EnumMap<Rule, Integer>(Rule.class);
        var terminated = 0;
        var workers = 0;
        for (var job : records) {
            if (job.status().isSettled()) {
                continue;
            }
            var handling = handle(projectId, job);
            if (handling.failure().isPresent()) {
                return new Outcome.Failed(handling.failure().get());
            }
            var rule = handling.rule();
            if (handling.terminated()) {
                terminated++;
            }
            var settled = job.toBuilder().status(Status.ENDED).fate(KILLED.fate()).outcomeRow(KILLED.row())
                    .endedAt(clock.instant()).signal(handling.signal()).childWallMs(null).build();
            var report = JobSettlement.of(store, lockManager).settle(new SettlementStep.Settled(settled,
                    slots.of(job)));
            if (report.stoppedAt().isPresent()) {
                return new Outcome.Failed("job %s was not settled, the settlement stopped at %s: %s".formatted(
                        job.jobId(), report.stoppedAt().get(), report.steps().getLast().outcome()));
            }
            counts.merge(rule, 1, Integer::sum);
            if (job.kind() == JobId.Kind.LLM) {
                workers++;
            }
        }
        return new Outcome.Done(report(counts, terminated, workers));
    }

    /**
     * Applies the rule of a job left unsettled: the orphan note of rule (c), the probe and the termination of rule (d).
     *
     * @return the rule, the signal to record and whether a group was terminated, or why the orphan note failed
     */
    private Handling handle(String projectId, JobRecord job) {
        var rule = rule(job);
        if (rule == Rule.UNIDENTIFIABLE) {
            try {
                audit.append(orphanNote(projectId, job.jobId()));
            } catch (UncheckedIOException e) {
                return Handling.failed(rule, "the orphan note of job %s was not handed over: %s".formatted(
                        job.jobId(), e.getMessage()));
            }
        } else if (rule == Rule.PROBED && signals.signalGroup(job.pid(), Signal.PROBE) instanceof Delivered) {
            var report = termination.terminate(job.pid());
            var signal = switch (report.ending()) {
                case GROUP_KILLED -> Signal.SIGKILL.name();
                case GROUP_EXITED -> Signal.SIGTERM.name();
                case GROUP_NOT_REACHED, SIGNAL_REFUSED -> RUNTIME_RESTART;
            };
            return new Handling(rule, signal, true, Optional.empty());
        }
        return new Handling(rule, job.execution() == Execution.IN_PROCESS ? null : RUNTIME_RESTART, false,
                Optional.empty());
    }

    /** @return the rule that settles a job left unsettled, the first that matches */
    Rule rule(JobRecord job) {
        if (job.status() == Status.QUEUED) {
            return Rule.QUEUED;
        }
        if (job.execution() == Execution.IN_PROCESS) {
            return Rule.IN_PROCESS;
        }
        if (job.spawnedAt() != null && job.spawnedAt().isBefore(bootTime)) {
            return Rule.BEFORE_BOOT;
        }
        if (job.pid() == null) {
            return Rule.UNIDENTIFIABLE;
        }
        if (pidReused(job)) {
            return Rule.UNIDENTIFIABLE;
        }
        return Rule.PROBED;
    }

    /**
     * Whether the recorded PID now names a process other than the job's, so that neither its group nor the PID itself
     * may be signalled. A PID that no process owns any more was not reused: a process group that still lives under
     * that id is the job's surviving group (the kernel does not hand out a PID that is held as a process-group id), and
     * a group that is gone answers {@code ESRCH}. Only a live process that started at another instant is a reused PID.
     *
     * @param job the record of a job with a PID and a recorded process start
     * @return whether the recorded PID was reused by another process
     */
    static boolean pidReused(JobRecord job) {
        if (job.processStartedAt() == null) {
            return true;
        }
        return ProcessHandle.of(job.pid()).flatMap(handle -> handle.info().startInstant())
                .map(start -> Duration.between(job.processStartedAt(), start).abs()
                        .compareTo(START_TOLERANCE) > 0)
                .orElse(false);
    }

    /**
     * @param projectId the project
     * @param jobId     the job whose record carries no PID
     * @return the audit record that notes {@value #ORPHAN_UNIDENTIFIABLE}
     */
    static AuditEvent orphanNote(String projectId, JobId jobId) {
        return new AuditEvent(JOB_RECOVERED, AuditEvent.CATEGORY_STORE, AuditEvent.OUTCOME_SUCCEEDED,
                Map.of(AuditEvent.DETAIL_PROJECT_ID, projectId, DETAIL_JOB_ID, jobId.value(), DETAIL_NOTE,
                        ORPHAN_UNIDENTIFIABLE));
    }

    private static String report(Map<Rule, Integer> counts, int terminated, int workers) {
        var settled = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (settled == 0) {
            return "no job to settle; worker part " + NOT_IMPLEMENTED;
        }
        return "%d jobs settled killed %s, %d groups terminated; worker part of %d workers %s".formatted(settled,
                counts, terminated, workers, NOT_IMPLEMENTED);
    }
}
