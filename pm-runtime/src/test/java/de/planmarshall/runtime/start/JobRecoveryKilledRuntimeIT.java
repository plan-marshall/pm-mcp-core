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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.ProcessGroupSignals.NoSuchProcess;
import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.ProjectRecovery.ProjectOutcome;
import de.planmarshall.runtime.start.ProjectRecovery.StepResult;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import de.planmarshall.runtime.test.TestBases;

/**
 * The jobs part of recovery step 3 after a runtime killed with {@code SIGKILL} at each point of the life of a job
 * (PM-ARCH-7, PM-EXEC-4): after the write-ahead record and before the spawn, after the spawn and before the post-spawn
 * replace, while the job runs, and after its fate and before the release of its slot. The killed runtime is a child JVM
 * of {@link KilledRuntimeFixture}, which writes the job records as the runner does and starts the job's process group
 * with {@code setsid}; the next runtime instance recovers the project in this JVM with the real
 * {@link JobRecoveryStep} and the real {@link ProcessGroupSignals}, in place of the seam of
 * {@link Name#JOBS_AND_WORKERS}.
 * <p>
 * The modes, the project and the job of the child are the protocol with it ({@link RuntimeChildProcess}); the outcome
 * row, the signal names and the counts and kinds in the reports are the specified outcome format of the step. The
 * instant of the lease step is generated.
 */
@EnableGeneratorController
@EnabledOnOs(OS.LINUX)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Jobs part of the recovery with a killed runtime")
class JobRecoveryKilledRuntimeIT {

    /** Contract literal: the row of the outcome table of a job killed by a runtime restart. */
    private static final int KILLED_OUTCOME_ROW = 4;

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();
    private final RecordingAuditSink auditSink = new RecordingAuditSink();
    private final ProcessGroupSignals signals = ProcessGroupSignals.system();

    private Path base;
    private FileLockManager lockManager;

    @BeforeEach
    void createBase() throws IOException {
        // A fixture argument, the prefix of the base directory, not test data
        base = Files.createDirectory(TestBases.create("pmjr"));
        lockManager = new FileLockManager(auditSink);
    }

    @AfterEach
    void killChildAndGroupAndDeleteBase() throws InterruptedException, IOException {
        try {
            fixture.close();
            if (Files.exists(RuntimeChildProcess.jobPid(base))) {
                signals.signalGroup(jobPid(), Signal.SIGKILL);
            }
        } finally {
            TestBases.delete(base);
        }
    }

    private void killInside(String mode) throws Exception {
        fixture.start(mode, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(mode));
        fixture.kill();
    }

    private long jobPid() throws IOException {
        return Long.parseLong(Files.readString(RuntimeChildProcess.jobPid(base)).strip());
    }

    private JobRecord theJob() {
        var records = JobRecordStore.ofProject(base, PROJECT_A).records();
        assertEquals(1, records.size(), "the project must hold exactly one job");
        return records.getFirst();
    }

    /** The next runtime instance: the recovery of the project with the real job step and the lease step. */
    private ProjectOutcome recover() throws IOException {
        var leases = new LeaseRecoveryStep(List.of(RecoveryStepFixtures.buildSlots(base, lockManager)),
                LeaseRecoveryStep.AbandonmentEvidence.NONE, Clock.fixed(now, ZoneOffset.UTC));
        var recovery = new ProjectRecovery(() -> List.of(PROJECT_A), RecoveryStepFixtures.completeSteps(
                RuntimeChildProcess.jobStep(base, lockManager, auditSink), leases), Runnable::run);
        return assertInstanceOf(ProjectRecovery.Result.Recovered.class, recovery.awaitRecovered(PROJECT_A),
                "the project must be recovered").outcome();
    }

    private static String report(ProjectOutcome outcome, Name name) {
        var step = outcome.steps().stream().filter(result -> result.name() == name).findFirst().orElseThrow();
        return assertInstanceOf(Outcome.Done.class, step.outcome(), "the step of the recovery must be done")
                .report();
    }

    private static void assertKilledByRestart(JobRecord job, String signal) {
        assertAll(
                () -> assertEquals(Status.ENDED, job.status(), "the settled job must have ended"),
                () -> assertEquals(Fate.KILLED, job.fate(), "the settled job must be killed"),
                () -> assertEquals(KILLED_OUTCOME_ROW, job.outcomeRow(), "the job must carry outcome row 4"),
                () -> assertEquals(signal, job.signal(), "the job must carry the signal of the restart"));
    }

    @Test
    @DisplayName("killed after the write-ahead record, before the spawn: settled killed with the orphan note")
    void killedBeforeSpawn() throws Exception {
        killInside(RuntimeChildProcess.JOB_WRITE_AHEAD);
        var left = theJob();

        var outcome = recover();

        assertAll(
                () -> assertEquals(Status.RUNNING, left.status(), "the killed runtime left the write-ahead record"),
                () -> assertFalse(left.isSpawnRecorded(), "the killed runtime recorded no spawn"),
                () -> assertFalse(outcome.failed(), "the recovery of the project must not fail"),
                () -> assertKilledByRestart(theJob(), JobRecoveryStep.RUNTIME_RESTART),
                () -> assertEquals(List.of(JobRecoveryStep.JOB_RECOVERED), auditSink.eventNames(),
                        "exactly one orphan note must be handed over"),
                // Contract literal: the specified report of the step, its counts and the kind of evidence
                () -> assertTrue(report(outcome, Name.JOBS_AND_WORKERS).startsWith(
                        "1 jobs settled killed {UNIDENTIFIABLE=1}, 0 groups terminated"),
                        "the report must count one unidentifiable job"));
    }

    @Test
    @DisplayName("killed after the spawn, before the post-spawn replace: the live orphan is noted, never signalled")
    void killedBeforePostSpawn() throws Exception {
        killInside(RuntimeChildProcess.JOB_SPAWNED);
        var orphan = jobPid();
        var left = theJob();

        var outcome = recover();

        assertAll(
                () -> assertFalse(left.isSpawnRecorded(), "the post-spawn replace was not written"),
                () -> assertFalse(outcome.failed(), "the recovery of the project must not fail"),
                () -> assertKilledByRestart(theJob(), JobRecoveryStep.RUNTIME_RESTART),
                () -> assertEquals(List.of(JobRecoveryStep.JOB_RECOVERED), auditSink.eventNames(),
                        "exactly one orphan note must be handed over"),
                () -> assertFalse(signals.signalGroup(orphan, Signal.PROBE) instanceof NoSuchProcess,
                        "the unidentifiable orphan was not signalled and still lives"));
    }

    @Test
    @DisplayName("killed while the job runs: its live group is probed and terminated, the job settled killed")
    void killedWhileRunning() throws Exception {
        killInside(RuntimeChildProcess.JOB_RUNNING);
        var group = jobPid();
        var liveBefore = !(signals.signalGroup(group, Signal.PROBE) instanceof NoSuchProcess);
        var left = theJob();

        var outcome = recover();

        var settled = theJob();
        assertAll(
                () -> assertTrue(liveBefore, "the group outlived the killed runtime"),
                () -> assertEquals(group, left.pid(), "the left record must name the group of the job"),
                () -> assertFalse(outcome.failed(), "the recovery of the project must not fail"),
                () -> assertEquals(Fate.KILLED, settled.fate(), "the settled job must be killed"),
                () -> assertEquals(KILLED_OUTCOME_ROW, settled.outcomeRow(), "the job must carry outcome row 4"),
                () -> assertTrue(List.of(Signal.SIGTERM.name(), Signal.SIGKILL.name()).contains(settled.signal()),
                        "the job must carry SIGTERM or SIGKILL"),
                () -> assertEquals(List.of(), auditSink.eventNames(), "no orphan note may be handed over"),
                // Contract literal: the specified report of the step, its counts and the kind of evidence
                () -> assertTrue(report(outcome, Name.JOBS_AND_WORKERS).startsWith(
                        "1 jobs settled killed {PROBED=1}, 1 groups terminated"),
                        "the report must count one probed, terminated job"),
                () -> assertTrue(signals.signalGroup(group, Signal.PROBE) instanceof NoSuchProcess
                        || awaitGroupEnd(group), "the group was terminated"));
    }

    @Test
    @DisplayName("killed after the fate, before the slot release: the fate stands and the slot lease is orphaned")
    void killedBeforeSlotRelease() throws Exception {
        killInside(RuntimeChildProcess.JOB_FATE);
        var left = theJob();

        var outcome = recover();

        assertAll(
                () -> assertEquals(Fate.CANCELLED, left.fate(), "the killed runtime recorded the fate"),
                () -> assertFalse(outcome.failed(), "the recovery of the project must not fail"),
                () -> assertEquals(left, theJob(), "a settled job is never settled again"),
                // Contract literals: the specified reports of the jobs and the lease step
                () -> assertTrue(report(outcome, Name.JOBS_AND_WORKERS).startsWith("no job to settle"),
                        "the jobs step must find no job to settle"),
                () -> assertTrue(report(outcome, Name.LEASES).contains("0 kept, 1 orphaned, 0 removed"),
                        "the lease step must orphan the slot lease"),
                () -> assertEquals(Name.values().length, outcome.steps().size(),
                        "every recovery step must have run"),
                () -> assertEquals(List.of(Name.JOBS_AND_WORKERS, Name.LEASES),
                        outcome.steps().subList(2, 4).stream().map(StepResult::name).toList(),
                        "the jobs are settled in step 3, before the leases"));
    }

    /** Waits for the end of a group whose leader was terminated and is not yet reaped by its new parent. */
    private boolean awaitGroupEnd(long group) {
        var handle = ProcessHandle.of(group);
        if (handle.isEmpty()) {
            return true;
        }
        try {
            handle.get().onExit().get(KilledRuntimeFixture.PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException _) {
            return false;
        }
        return signals.signalGroup(group, Signal.PROBE) instanceof NoSuchProcess;
    }
}
