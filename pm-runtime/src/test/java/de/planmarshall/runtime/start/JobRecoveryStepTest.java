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

import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_A;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.job.SettlementStep;
import de.planmarshall.runtime.job.TerminationProtocol;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;

/**
 * The jobs part of recovery step 3 (PM-ARCH-7, PM-EXEC-4) over a real job record store: one case per rule of the
 * specification, each asserting at the lowest signalling primitive, a recording {@link ProcessGroupSignals}, which
 * {@code kill} calls the rule made. Rules (a) to (c) and a queued job make none; the matched control (d), a job of
 * this boot with a recorded PID whose group lives, is probed and terminated.
 */
@EnableGeneratorController
@DisplayName("Jobs part of the recovery step JOBS_AND_WORKERS")
class JobRecoveryStepTest {

    private static final Scope PLAN = new Scope(Scope.Type.PLAN, "plan-a");
    private static final Scope WORKER_QUEUE = new Scope(Scope.Type.PROJECT, null);
    private static final Instant BOOT = Instant.parse("2026-10-17T06:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-17T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final String RUNTIME_RESTART = JobRecoveryStep.RUNTIME_RESTART;

    @TempDir
    Path base;

    private FileLockManager lockManager;
    private JobRecordStore store;
    private RecordingSignals signals;
    private RecordingAuditSink audit;

    @BeforeEach
    void createStore() {
        lockManager = new FileLockManager(_ -> {
        });
        store = JobRecordStore.ofProject(base, PROJECT_A);
        signals = new RecordingSignals();
        audit = new RecordingAuditSink();
    }

    /** One {@code kill} call: whether it addressed a group or a process, the id, and the signal. */
    private record Call(boolean group, long id, Signal signal) {
    }

    /**
     * The lowest signalling primitive of the tests: it records every call and answers for one live group, which exits
     * on {@code SIGTERM}. Every other target does not exist, or refuses every signal when told so.
     */
    private static final class RecordingSignals implements ProcessGroupSignals {

        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile long liveGroup;
        private volatile boolean refusing;

        @Override
        public Answer signalGroup(long pgid, Signal signal) {
            calls.add(new Call(true, pgid, signal));
            if (pgid != liveGroup) {
                return new NoSuchProcess();
            }
            if (signal != Signal.PROBE && refusing) {
                return new Refused(1);
            }
            if (signal == Signal.SIGTERM) {
                liveGroup = 0;
            }
            return new Delivered();
        }

        @Override
        public Answer signalProcess(long pid, Signal signal) {
            calls.add(new Call(false, pid, signal));
            return new NoSuchProcess();
        }
    }

    private JobRecoveryStep step(AuditSink sink, JobRecoveryStep.Slots slots) {
        return new JobRecoveryStep(base, lockManager,
                new JobRecoveryStep.Signalling(signals, new TerminationProtocol(signals)), sink, BOOT, slots,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JobRecoveryStep step() {
        return step(audit, JobRecoveryStep.Slots.NONE);
    }

    private static long pid() {
        return Generators.longs(2, 4_000_000).next();
    }

    private static JobRecord.JobRecordBuilder processJob(JobId jobId) {
        return JobRecord.builder().jobId(jobId).execution(Execution.PROCESS).scope(PLAN).budgetKey("maven:verify")
                .budgetSeconds(600).budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .argvDigest("ab".repeat(32)).confinement(Confinement.UNAVAILABLE).createdAt(CREATED_AT)
                .clampedValues(List.of());
    }

    /** Writes the first record of a job, built from its allocated id, in one transaction. */
    private JobRecord write(JobId.Kind kind, Scope scope, Function<JobId, JobRecord> factory) {
        try (var transaction = lockManager.openTransaction()) {
            var job = factory.apply(store.allocate(transaction, kind, scope));
            if (job.status() == Status.QUEUED) {
                store.writeQueued(transaction, job);
            } else {
                store.writeAhead(transaction, job);
            }
            return job;
        }
    }

    private JobRecord queued() {
        return write(JobId.Kind.BUILD, PLAN, jobId -> processJob(jobId).status(Status.QUEUED).build());
    }

    private JobRecord writeAhead() {
        return write(JobId.Kind.BUILD, PLAN, jobId -> processJob(jobId).status(Status.RUNNING).build());
    }

    private JobRecord inProcess() {
        return write(JobId.Kind.GIT, PLAN, jobId -> processJob(jobId).execution(Execution.IN_PROCESS).argvDigest(null)
                .confinement(Confinement.NOT_APPLICABLE).budgetKey("git:status").status(Status.RUNNING).build());
    }

    private JobRecord postSpawn(long pid, Instant spawnedAt) {
        return postSpawn(pid, spawnedAt, spawnedAt);
    }

    private JobRecord postSpawn(long pid, Instant processStartedAt, Instant spawnedAt) {
        var ahead = writeAhead();
        var spawned = ahead.toBuilder().pid(pid).processStartedAt(processStartedAt).spawnedAt(spawnedAt).build();
        try (var transaction = lockManager.openTransaction()) {
            store.writePostSpawn(transaction, spawned);
        }
        return spawned;
    }

    /** @return the PID of the test's own process: a real PID whose start instant the process table answers */
    private static long ownPid() {
        return ProcessHandle.current().pid();
    }

    /** @return the start instant of the test's own process */
    private static Instant ownStart() {
        return ProcessHandle.current().info().startInstant().orElseThrow();
    }

    private JobRecord worker() {
        return write(JobId.Kind.LLM, WORKER_QUEUE, jobId -> processJob(jobId).scope(WORKER_QUEUE).worker("{}")
                .budgetKey("worker:claude").status(Status.RUNNING).build());
    }

    private JobRecord read(JobRecord job) {
        return store.read(job.jobId()).orElseThrow();
    }

    private static void assertKilled(JobRecord settled, String signal) {
        assertAll(
                () -> assertEquals(Status.ENDED, settled.status()),
                () -> assertEquals(Fate.KILLED, settled.fate()),
                () -> assertEquals(4, settled.outcomeRow(), "outcome row 4"),
                () -> assertEquals(NOW, settled.endedAt()),
                () -> assertNull(settled.childWallMs()),
                () -> assertEquals(signal, settled.signal()));
    }

    @Nested
    @DisplayName("the rules that send no signal")
    class NoSignal {

        @Test
        @DisplayName("rule (a): an in-process operation is settled killed with no kill() call and no orphan note")
        void inProcessOperation() {
            var job = inProcess();

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertInstanceOf(Outcome.Done.class, outcome),
                    () -> assertEquals(List.of(), signals.calls, "rule (a) makes zero kill() calls"),
                    () -> assertEquals(List.of(), audit.events(), "no orphan note"),
                    () -> assertKilled(read(job), null),
                    () -> assertEquals(JobRecoveryStep.Rule.IN_PROCESS, step().rule(job)));
        }

        @Test
        @DisplayName("rule (b): a job spawned before the boot of the system is settled without a signal")
        void spawnedBeforeBoot() {
            var job = postSpawn(pid(), BOOT.minusSeconds(3_600));

            step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(), signals.calls),
                    () -> assertEquals(List.of(), audit.events()),
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertEquals(JobRecoveryStep.Rule.BEFORE_BOOT, step().rule(job)));
        }

        @Test
        @DisplayName("rule (c): a job without a PID gets the audit note orphan unidentifiable and no signal")
        void withoutPid() {
            var job = writeAhead();

            step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(), signals.calls),
                    () -> assertEquals(List.of(new AuditEvent(JobRecoveryStep.JOB_RECOVERED,
                            AuditEvent.CATEGORY_STORE, AuditEvent.OUTCOME_SUCCEEDED,
                            Map.of(AuditEvent.DETAIL_PROJECT_ID, PROJECT_A, JobRecoveryStep.DETAIL_JOB_ID,
                                    job.jobId().value(), JobRecoveryStep.DETAIL_NOTE,
                                    JobRecoveryStep.ORPHAN_UNIDENTIFIABLE))), audit.events()),
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertEquals(JobRecoveryStep.Rule.UNIDENTIFIABLE, step().rule(job)));
        }

        @Test
        @DisplayName("a job left queued never started: it is settled killed without a signal")
        void queuedJob() {
            var job = queued();

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(), signals.calls),
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertNull(read(job).pid()),
                    () -> assertEquals(new Outcome.Done("1 jobs settled killed {QUEUED=1}, 0 groups terminated; "
                            + "worker part of 0 workers " + RecoveryStep.NOT_IMPLEMENTED), outcome));
        }
    }

    @Nested
    @DisplayName("rule (d): the group of a recorded PID is probed")
    class Probed {

        @Test
        @DisplayName("matched control: a same-boot job whose group lives is probed and terminated")
        void liveGroupIsTerminated() {
            var pid = ownPid();
            signals.liveGroup = pid;
            var job = postSpawn(pid, ownStart(), BOOT.plusSeconds(60));

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(new Call(true, pid, Signal.PROBE), new Call(true, pid, Signal.SIGTERM),
                            new Call(true, pid, Signal.PROBE)), signals.calls),
                    () -> assertKilled(read(job), Signal.SIGTERM.name()),
                    () -> assertEquals(JobRecoveryStep.Rule.PROBED, step().rule(job)),
                    () -> assertEquals(List.of(), audit.events()),
                    () -> assertEquals(new Outcome.Done("1 jobs settled killed {PROBED=1}, 1 groups terminated; "
                            + "worker part of 0 workers " + RecoveryStep.NOT_IMPLEMENTED), outcome));
        }

        @Test
        @DisplayName("a group that no longer exists is probed once and not terminated")
        void deadGroupIsNotTerminated() {
            var pid = ownPid();
            var job = postSpawn(pid, ownStart(), BOOT.plusSeconds(60));

            step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(new Call(true, pid, Signal.PROBE)), signals.calls),
                    () -> assertKilled(read(job), RUNTIME_RESTART));
        }

        @Test
        @DisplayName("a live group that refuses the termination signal is recorded with the restart signal")
        void refusedTermination() {
            var pid = ownPid();
            signals.liveGroup = pid;
            signals.refusing = true;
            var job = postSpawn(pid, ownStart(), BOOT.plusSeconds(60));

            step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(new Call(true, pid, Signal.PROBE), new Call(true, pid, Signal.SIGTERM)),
                            signals.calls),
                    () -> assertKilled(read(job), RUNTIME_RESTART));
        }

        @Test
        @DisplayName("a recorded PID whose process started at another instant is not signalled: the PID was reused")
        void reusedPidIsNotSignalled() {
            var pid = ownPid();
            signals.liveGroup = pid;
            var job = postSpawn(pid, ownStart().minus(Duration.ofHours(1)), BOOT.plusSeconds(60));

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(List.of(), signals.calls, "a reused PID is not signalled"),
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertEquals(JobRecoveryStep.Rule.UNIDENTIFIABLE, step().rule(job)),
                    () -> assertEquals(List.of(new AuditEvent(JobRecoveryStep.JOB_RECOVERED,
                            AuditEvent.CATEGORY_STORE, AuditEvent.OUTCOME_SUCCEEDED,
                            Map.of(AuditEvent.DETAIL_PROJECT_ID, PROJECT_A, JobRecoveryStep.DETAIL_JOB_ID,
                                    job.jobId().value(), JobRecoveryStep.DETAIL_NOTE,
                                    JobRecoveryStep.ORPHAN_UNIDENTIFIABLE))), audit.events(),
                            "the job is noted as unidentifiable"),
                    () -> assertEquals(new Outcome.Done("1 jobs settled killed {UNIDENTIFIABLE=1}, 0 groups "
                            + "terminated; worker part of 0 workers " + RecoveryStep.NOT_IMPLEMENTED), outcome));
        }

        @Test
        @DisplayName("a recorded PID without the process start it was spawned with is not signalled")
        void pidWithoutAProcessStartIsNotSignalled() {
            // The record refuses a PID without the process start it was spawned with, so the guard's
            // "no start recorded" branch cannot be reached from a stored record; the guard still treats it as
            // unidentifiable, and the record's own invariant is asserted by JobRecordTest.
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> postSpawn(ownPid(), null, BOOT.plusSeconds(60))));
        }
    }

    static Stream<Arguments> firstMatchingRule() {
        return Stream.of(
                Arguments.of("queued", Status.QUEUED, Execution.PROCESS, null, null, JobRecoveryStep.Rule.QUEUED),
                Arguments.of("in-process", Status.RUNNING, Execution.IN_PROCESS, null, null,
                        JobRecoveryStep.Rule.IN_PROCESS),
                Arguments.of("before boot with PID", Status.RUNNING, Execution.PROCESS, 42L, BOOT.minusSeconds(1),
                        JobRecoveryStep.Rule.BEFORE_BOOT),
                Arguments.of("after boot without PID", Status.RUNNING, Execution.PROCESS, null, null,
                        JobRecoveryStep.Rule.UNIDENTIFIABLE),
                Arguments.of("spawned at the boot instant", Status.RUNNING, Execution.PROCESS, 42L, BOOT,
                        JobRecoveryStep.Rule.UNIDENTIFIABLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstMatchingRule")
    @DisplayName("the first matching rule of the specification settles a job")
    void firstMatchingRuleSettles(String name, Status status, Execution execution, Long pid, Instant spawnedAt,
            JobRecoveryStep.Rule expected) {
        var draft = processJob(new JobId("b-0123456789abcdef")).status(status).execution(execution).pid(pid)
                .processStartedAt(spawnedAt).spawnedAt(spawnedAt);
        if (execution == Execution.IN_PROCESS) {
            draft.argvDigest(null).confinement(Confinement.NOT_APPLICABLE);
        }

        assertEquals(expected, step().rule(draft.build()));
    }

    @Nested
    @DisplayName("the settlement")
    class Settlement {

        @Test
        @DisplayName("a settled job is left as it is; no job to settle is reported as such")
        void settledJobsAreSkipped() {
            var job = queued();
            step().run(PROJECT_A);
            var settled = read(job);
            signals.calls.clear();

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertEquals(new Outcome.Done("no job to settle; worker part " + RecoveryStep.NOT_IMPLEMENTED),
                            outcome),
                    () -> assertEquals(settled, read(job)),
                    () -> assertEquals(List.of(), signals.calls));
        }

        @Test
        @DisplayName("a project without job records has no job to settle")
        void noRecords() {
            assertEquals(new Outcome.Done("no job to settle; worker part " + RecoveryStep.NOT_IMPLEMENTED),
                    step().run("project-b"));
        }

        @Test
        @DisplayName("a worker is settled like every other job and counted for the worker seam")
        void workerIsCounted() {
            var job = worker();

            var outcome = step().run(PROJECT_A);

            assertAll(
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertEquals(new Outcome.Done("1 jobs settled killed {UNIDENTIFIABLE=1}, 0 groups terminated;"
                            + " worker part of 1 workers " + RecoveryStep.NOT_IMPLEMENTED), outcome));
        }

        @Test
        @DisplayName("the slot the job held is released after its fate is recorded")
        void slotIsReleased() throws Exception {
            var slots = RecoveryStepFixtures.buildSlots(base, lockManager);
            var lease = RecoveryStepFixtures.claim(slots, "slot-a", PROJECT_A, RecoveryStepFixtures.self());
            var job = queued();
            var step = step(audit, held -> held.jobId().equals(job.jobId())
                    ? Optional.of(new SettlementStep.Slot(slots, lease.key(), lease.owner())) : Optional.empty());

            step.run(PROJECT_A);

            assertAll(
                    () -> assertKilled(read(job), RUNTIME_RESTART),
                    () -> assertEquals(List.of(), slots.snapshot().stream().map(LeaseRecord::key).toList()));
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("unreadable job records fail the step")
        void unreadableRecords() throws Exception {
            var job = queued();
            Files.writeString(store.recordPath(job.jobId()), "{");

            var outcome = step().run(PROJECT_A);

            var failed = assertInstanceOf(Outcome.Failed.class, outcome);
            assertTrue(failed.reason().startsWith("the job records of project " + PROJECT_A + " were not read"),
                    failed.reason());
        }

        @Test
        @DisplayName("an orphan note the audit sink refuses fails the step and leaves the job unsettled")
        void refusedOrphanNote() {
            var job = writeAhead();
            AuditSink refusing = _ -> {
                throw new UncheckedIOException(new IOException("the audit log cannot be written"));
            };

            var outcome = step(refusing, JobRecoveryStep.Slots.NONE).run(PROJECT_A);

            var failed = assertInstanceOf(Outcome.Failed.class, outcome);
            assertAll(
                    () -> assertTrue(failed.reason().startsWith("the orphan note of job " + job.jobId()),
                            failed.reason()),
                    () -> assertEquals(Status.RUNNING, read(job).status()));
        }

        @Test
        @DisplayName("a slot that cannot be released stops the step after the fate was recorded")
        void unreleasableSlot() throws Exception {
            var slots = RecoveryStepFixtures.buildSlots(base, lockManager);
            var lease = RecoveryStepFixtures.claim(slots, "slot-a", PROJECT_A, RecoveryStepFixtures.self());
            Files.writeString(slots.store(), "{");
            var job = queued();
            var step = step(audit, _ -> Optional.of(new SettlementStep.Slot(slots, lease.key(), lease.owner())));

            var outcome = step.run(PROJECT_A);

            var failed = assertInstanceOf(Outcome.Failed.class, outcome);
            assertAll(
                    () -> assertTrue(failed.reason().startsWith("job " + job.jobId()
                            + " was not settled, the settlement stopped at SLOT_RELEASE"), failed.reason()),
                    () -> assertKilled(read(job), RUNTIME_RESTART));
        }
    }

    @Test
    @DisplayName("the step is the jobs part of JOBS_AND_WORKERS, and Slots.NONE names no slot")
    void nameAndNoSlot() {
        var job = processJob(new JobId("b-0123456789abcdef")).status(Status.QUEUED).build();

        assertAll(
                () -> assertEquals(RecoveryStep.Name.JOBS_AND_WORKERS, step().name()),
                () -> assertFalse(JobRecoveryStep.Slots.NONE.of(job).isPresent()));
    }
}
