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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.sha256Digests;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.build.UnparsedEvidence;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.JobRunner.CancelCause;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.ParseResult;
import de.planmarshall.runtime.job.JobEvidence.Signalled;
import de.planmarshall.runtime.job.JobEvidence.Termination;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;
import de.planmarshall.runtime.job.SettlementStep.Name;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The subprocess runner (PM-EXEC-4, PM-ARCH-7) over a real enrolment, a real job record store and the fake launcher
 * {@link FakePmExec}: the write-ahead record exists before the launch, a start whose launcher reports is answered once
 * the post-spawn record carries the PID while one whose launcher reports nothing in time is answered with the
 * write-ahead record, a cancel decides the fate by its cause, the budget ends a job {@code timed_out}, a refused
 * verification spawns nothing, and the enrolment lock is released before the launch. The enrolled host tool
 * {@code git} of the fixture is a shell script whose first argument selects what the job does.
 *
 * <p>
 * The data of the tests are generated. The literals that stay are the contract: the fixture script's job selectors
 * ({@code ok}, {@code fail}, {@code sleep}, ...), the outcome rows, fates, signal names and wait-status values, the
 * refusal and audit codes, the logged message texts, the build-log resource URI, the host {@code PATH}
 * {@code /usr/bin:/bin} the launched processes need, and the budgets in seconds that bound how long a job may run.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("SubprocessJobRunner")
class SubprocessJobRunnerTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(20);
    private static final Scope WORKSPACE = new Scope(Scope.Type.WORKSPACE, null);
    private static final String BUDGET_KEY = "git:status";
    private static final BudgetDerivation FLOOR = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");
    private static final ParseResult SUCCESS = new ParseResult(true, 0, false, null);

    /** The enrolled {@code git} of the fixture: its first argument selects what the job does. */
    private static final String GIT_SCRIPT = """
            #!/bin/sh
            case "$1" in
              ok) echo done; exit 0 ;;
              fail) echo broken; exit 3 ;;
              sleep) exec @SLEEP@ "$2" ;;
              stubborn) trap '' TERM; echo ready; while :; do @SLEEP@ 0.1; done ;;
              release) while [ ! -e "@RELEASE@" ]; do @SLEEP@ 0.05; done; echo done; exit 0 ;;
            esac
            exit 2
            """;

    private JobEnrolmentFixture fixture;
    private RecordingLocks locks;
    private JobRecordStore jobs;
    private Path home;

    /** The file whose existence lets the job {@code release} of the fixture's {@code git} end by itself. */
    private Path release;
    private final List<Observation> observations = new CopyOnWriteArrayList<>();
    private SubprocessJobRunner runner;

    /** What the runner had written and held when it asked for the environment, right before the launch. */
    private record Observation(List<JobRecord> records, boolean enrolmentLockHeld) {
    }

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        release = fixture.base.resolve("release");
        Files.writeString(fixture.tools.get(HostToolName.GIT),
                GIT_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString()).replace("@RELEASE@",
                        release.toString()));
        fixture.enrol();
        locks = new RecordingLocks(fixture.locks);
        jobs = JobRecordStore.ofProject(fixture.base, fixture.project);
        home = fixture.base.resolve("home");
        runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty());
    }

    @AfterEach
    void tearDown() {
        if (runner != null) {
            for (var jobId : runner.runningJobs()) {
                runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
                runner.awaitSettled(jobId, SETTLE_TIMEOUT);
            }
        }
        fixture.close();
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.PrimitiveJobs primitives) {
        return runner(termination, primitives, (job, ignoredJobId) -> ENVIRONMENT);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.PrimitiveJobs primitives,
            SubprocessJobRunner.Environment environment) {
        return runner(termination, environment,
                (job, output) -> output.contains("done") ? SUCCESS : ParseResult.NOTHING, primitives);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives) {
        return runner(termination, environment, parser, primitives, null);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives,
            InstantSource clock) {
        return runner(termination, environment, parser, primitives, clock, Map.of());
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives,
            InstantSource clock, Map<Name, SettlementStep> settlementSteps) {
        return runner(termination, environment, parser, primitives, clock, settlementSteps, fixture.pipeline());
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives,
            InstantSource clock, Map<Name, SettlementStep> settlementSteps, VerifyNotResolvePipeline pipeline) {
        return SubprocessJobRunner.builder().base(fixture.base).lockManager(locks).pipeline(pipeline)
                .launcher(new PmExecLauncher(home)).termination(termination).environment((job, ignoredJobId) -> {
            observations.add(new Observation(jobs.records(),
                    locks.anyOpenHolds(fixture.store.lockKey(fixture.project))));
            return environment.of(job, ignoredJobId);
        }).probeEnvironment((candidate, jobId) -> ENVIRONMENT).parser(parser).primitives(primitives).clock(clock)
                .settlementSteps(settlementSteps).build();
    }

    /**
     * Tells when a message is logged on another thread: the handler of the thread that waits for a job logs its failure
     * after the job has left the running jobs. It is registered on the root logger before the test starts the job, so
     * after the handler of the test logger, which has recorded the message when this one is told; the test then waits
     * on {@link #await()} instead of polling the recorded messages.
     */
    private static final class LoggedWatch extends Handler {

        private final TestLogLevel level;
        private final String text;
        private final CompletableFuture<Void> logged = new CompletableFuture<>();

        LoggedWatch(TestLogLevel level, String text) {
            this.level = level;
            this.text = text;
            Logger.getLogger("").addHandler(this);
        }

        @Override
        public void publish(LogRecord logRecord) {
            if (!TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(level, text).isEmpty()) {
                logged.complete(null);
            }
        }

        @Override
        public void flush() {
            // nothing is buffered
        }

        @Override
        public void close() {
            Logger.getLogger("").removeHandler(this);
        }

        /** Waits until the message is logged, at most ten seconds. */
        void await() throws InterruptedException, ExecutionException, TimeoutException {
            logged.get(10, TimeUnit.SECONDS);
        }
    }

    /** @return whether the process of a PID still lives; a reaped process does not */
    private static boolean alive(Long pid) {
        return pid != null && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private JobRunner.StartRequest<JobRequest> git(long budgetSeconds, String... arguments) {
        var request = fixture.request(fixture.root.toString(),
                new JobRequest.HostToolCall(HostToolName.GIT, List.of(arguments)));
        return new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, budgetSeconds, FLOOR, request,
                null);
    }

    private JobId started(JobRunner.StartResult result) {
        return assertInstanceOf(JobRunner.StartResult.Started.class, result, "the job should be started").jobId();
    }

    private JobRecord settled(JobId jobId) {
        return runner.awaitSettled(jobId, SETTLE_TIMEOUT)
                .orElseThrow(() -> new AssertionError("job %s did not settle".formatted(jobId)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("the launch carries the composed confinement to the launcher")
    void confinementIsPassedToTheLauncher() throws Exception {
        FakePmExec.install(home, FakePmExec.Mode.REPORT_ARGV);

        var jobId = started(runner.start(git(3600, "true")));
        settled(jobId);

        var log = Files.readString(jobs.logPath(jobId));
        assertAll(
                () -> assertTrue(log.contains("ARGV:--write "), "the launcher received a write option"),
                () -> assertTrue(log.contains("--deny-read "), "the launcher received a deny-read option"),
                () -> assertTrue(log.contains(fixture.base.toString()), "the runtime base is named to the launcher"),
                () -> assertTrue(log.contains("-- "), "the launcher received the separator"));
    }

    /** Waits until the job's output holds a line, so a signal meets the job only once it is ready for it. */
    private void awaitOutput(JobId jobId, String line) throws IOException, InterruptedException {
        var log = new JobLog(jobs.logPath(jobId));
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        try (var watcher = FileSystems.getDefault().newWatchService()) {
            // registered before the first read, so a write after it is an event, never missed
            jobs.directoryPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            while (!(log.readLines(0) instanceof JobLog.Lines lines && lines.lines().contains(line))) {
                var changed = watcher.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (changed == null) {
                    throw new AssertionError("job %s did not write '%s'".formatted(jobId, line));
                }
                changed.pollEvents();
                changed.reset();
            }
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("records a job at its fixed points")
    class RecordPoints {

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
        }

        @Test
        @DisplayName("the write-ahead record exists, running without a PID, before the launch")
        void writeAheadBeforeLaunch() {
            var jobId = started(runner.start(git(30, "ok")));

            settled(jobId);
            assertEquals(1, observations.size(), "the environment is asked once, right before the launch");
            var before = observations.getFirst().records();
            assertAll(
                    () -> assertEquals(1, before.size(), "one record before the launch"),
                    () -> assertEquals(jobId, before.getFirst().jobId(), "the record is the started job's"),
                    () -> assertEquals(Status.RUNNING, before.getFirst().status(), "the write-ahead record is running"),
                    () -> assertNull(before.getFirst().pid(), "no PID before the launch"),
                    () -> assertFalse(before.getFirst().isSpawnRecorded(), "no spawn is recorded before launch"));
        }

        @Test
        @DisplayName("a launcher that reports is answered only once the record carries the PID and the process start")
        void runningOnlyAfterPostSpawn() {
            var jobId = started(runner.start(git(30, "sleep", "30")));

            var running = runner.recordOf(jobId).orElseThrow();

            assertAll(
                    () -> assertEquals(Status.RUNNING, running.status(), "the started job is running"),
                    () -> assertTrue(running.isSpawnRecorded(), "the post-spawn record is written"),
                    () -> assertNotNull(running.processStartedAt(), "the process start is recorded"),
                    () -> assertNotNull(running.spawnedAt(), "the spawn instant is recorded"),
                    () -> assertTrue(runner.runningJobs().contains(jobId), "the job is among the running jobs"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.JOB_STARTED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' of kind 'git' started (pid %s, budget 30 seconds)".formatted(jobId,
                                    running.pid())));
        }

        @Test
        @DisplayName("the store map is pruned when the fate is recorded; the settled record stays readable")
        void storeMapIsPruned() {
            var jobId = started(runner.start(git(60, "sleep", "60")));
            var heldWhileRunning = runner.storesHeld();

            var cancelled = runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);

            assertAll(
                    () -> assertTrue(cancelled, "the running job is cancelled"),
                    () -> assertEquals(1, heldWhileRunning, "the running job's store is held"),
                    () -> assertEquals(0, runner.storesHeld(), "the settled job's store is pruned"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(jobId),
                            "the settled record is still read"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(jobId, Duration.ZERO),
                            "the settled record is awaited at once"));
        }

        @Test
        @DisplayName("a write-ahead record that cannot be written leaves no running job and no store")
        void writeAheadFailureRegistersNothing() throws Exception {
            var directory = Files.createDirectories(jobs.directoryPath());
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
            try {
                var request = git(30, "ok");
                assertThrows(UncheckedIOException.class, () -> runner.start(request),
                        "an unwritable store fails the start");

                assertAll(
                        () -> assertTrue(runner.runningJobs().isEmpty(), "the job was never registered"),
                        () -> assertEquals(0, runner.storesHeld(), "no store was left behind"),
                        () -> assertTrue(jobs.records().isEmpty(), "no record was written"));
            } finally {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            }
        }

        @Test
        @DisplayName("a queued job settled by the drain has its store pruned too")
        void queuedJobStoreIsPruned() {
            runner.stopAdmitting();
            var jobId = started(runner.start(git(30, "ok")));
            var heldWhileQueued = runner.storesHeld();

            var settledIds = runner.settleQueued();
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(List.of(jobId), settledIds, "the drain settled the queued job"),
                    () -> assertEquals(1, heldWhileQueued, "the queued job's store is held"),
                    () -> assertEquals(0, runner.storesHeld(), "the settled queued job's store is pruned"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the queued job has ended"),
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the drained queued job is killed"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(jobId),
                            "the settled record is still read"));
        }

        @Test
        @DisplayName("a settled record beyond the cache bound is no longer answered by recordOf")
        void settledRecordWindowIsBounded() {
            runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(locks)
                    .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                    .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                    .environment((job, ignoredJobId) -> ENVIRONMENT).probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                    .parser((job, output) -> SUCCESS).primitives(request -> Optional.empty())
                    .settledRecordsKept(1).build();
            var first = started(runner.start(git(30, "ok")));
            settled(first);
            var second = started(runner.start(git(30, "ok")));
            var secondSettled = settled(second);

            assertAll(
                    () -> assertEquals(Optional.empty(), runner.recordOf(first),
                            "the oldest settled record left the window"),
                    () -> assertEquals(Optional.of(secondSettled), runner.recordOf(second),
                            "the newest settled record is answered"),
                    () -> assertEquals(0, runner.storesHeld(), "both stores are pruned"));
        }

        @Test
        @DisplayName("a job that exits 0 with its success marker settles succeeded, its child wall time within its own")
        void childWallWithinWallTime() {
            var jobId = started(runner.start(git(30, "ok")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the job record has ended"),
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the job settles as succeeded"),
                    () -> assertEquals(7, settled.outcomeRow(), "row 7 decided the success"),
                    () -> assertEquals(0L, settled.exitCode(), "the exit code 0 is recorded"),
                    () -> assertNotNull(settled.childWallMs(), "an observed exit records the child's wall time"),
                    () -> assertTrue(settled.childWallMs() <= settled.jobWallTime().orElseThrow().toMillis(),
                            "the child's wall time is within the job's"),
                    () -> assertEquals(Optional.of(settled), jobs.read(jobId), "the settled record is stored"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' settled with fate 'succeeded' (outcome row 7)".formatted(jobId)));
        }

        @Test
        @DisplayName("a job that exits non-zero without a diagnostic settles failed by row 10")
        void failedExit() {
            var jobId = started(runner.start(git(30, "fail")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.FAILED, settled.fate(), "the failing job failed"),
                    () -> assertEquals(10, settled.outcomeRow(), "row 10 decided the failure"),
                    () -> assertEquals(3L, settled.exitCode(), "the script's exit code 3 is recorded"),
                    () -> assertNull(settled.signal(), "an exited job carries no signal"));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("terminates a job for the cause that decides its fate")
    class Terminations {

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
        }

        @Test
        @DisplayName("a pause of the plan that holds now ends the job cancelled")
        void pauseNow() {
            var jobId = started(runner.start(git(60, "sleep", "60")));

            var cancelled = runner.cancel(jobId, CancelCause.PLAN_PAUSED_NOW);
            var settled = settled(jobId);

            assertAll(
                    () -> assertTrue(cancelled, "the running job is cancelled by this call"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "a paused job is cancelled"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the cancel"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job is no longer running"),
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL),
                            "a settled job is not cancelled again"));
        }

        @Test
        @DisplayName("a job over its budget ends timed_out")
        void budgetExpiry() {
            var jobId = started(runner.start(git(1, "sleep", "60")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.TIMED_OUT, settled.fate(), "the job over budget timed out"),
                    () -> assertEquals(3, settled.outcomeRow(), "row 3 decided the timeout"),
                    () -> assertEquals(1, settled.budgetSeconds(), "the budget of one second is recorded"));
        }

        @Test
        @DisplayName("the drain ends the job killed")
        void drain() {
            var jobId = started(runner.start(git(60, "sleep", "60")));

            runner.cancel(jobId, CancelCause.DRAIN);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the drained job is killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "row 4 decided the kill"));
        }

        @Test
        @DisplayName("a group that outlives the grace period is sent SIGKILL and logged; the cause decides the fate")
        void escalated() throws Exception {
            var now = new AtomicReference<>(Instant.now());
            InstantSource clock = now::get;
            TerminationProtocol.Pause pause = duration -> now.updateAndGet(instant -> instant.plus(duration));
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system(), clock, pause),
                    request -> Optional.empty());
            var jobId = started(runner.start(git(60, "stubborn")));
            awaitOutput(jobId, "ready");

            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides the fate cancelled"),
                    () -> assertEquals("SIGKILL", settled.signal(), "the escalated job ended by SIGKILL"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_ESCALATED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            "(cause 'ENGINE_CANCEL')"));
        }

        @Test
        @DisplayName("matched control: a job that ends on SIGTERM is never escalated")
        void notEscalated() {
            var jobId = started(runner.start(git(60, "sleep", "60")));

            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cancelled job is cancelled"),
                    () -> assertEquals("SIGTERM", settled.signal(), "the obedient job ended by SIGTERM"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_ESCALATED.resolveIdentifierString()));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("terminates the process it started, never the one a report names")
    class ReportedPidIsNotTrusted {

        @BeforeEach
        void install() {
            // each test installs the launcher it needs
        }

        @Test
        @DisplayName("a cancel signals the started process, not the PID the launch report names")
        void cancelSignalsTheStartedProcess() throws Exception {
            var foreign = new ProcessBuilder(FakePmExec.tool("setsid").toString(),
                    FakePmExec.tool("sleep").toString(), "30").start();
            try {
                FakePmExec.installReportingPid(home, foreign.pid());
                var jobId = started(runner.start(git(60, "sleep", "5")));

                var cancelled = runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
                var settled = settled(jobId);

                assertAll(
                        () -> assertTrue(cancelled, "the job was running and the cancel decided its end"),
                        () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cancelled job is cancelled"),
                        () -> assertEquals(foreign.pid(), settled.pid(), "the record carries the reported PID"),
                        () -> assertTrue(foreign.isAlive(),
                                "the process group of the reported PID was not signalled"));
            } finally {
                foreign.destroyForcibly();
                foreign.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    @Nested
    @DisplayName("verifies a job before anything is launched")
    class Verification {

        @Test
        @DisplayName("a refused verification spawns nothing and records the fate refused by row 1")
        void refusedSpawnsNothing() {
            var request = fixture.gitStatus(fixture.beside.toString());

            var result = runner.start(new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, 30, FLOOR,
                    request, null));

            var refused = assertInstanceOf(JobRunner.StartResult.Refused.class, result,
                    "the start of the job is refused");
            var records = jobs.records();
            assertAll(
                    () -> assertEquals("path_traversal_rejected", refused.reason(),
                            "the refusal is path_traversal_rejected"),
                    () -> assertTrue(observations.isEmpty(), "nothing was launched"),
                    () -> assertEquals(1, records.size(), "one refusal record is written"),
                    () -> assertEquals(Status.REFUSED, records.getFirst().status(), "the record's status is refused"),
                    () -> assertEquals(Fate.REFUSED, records.getFirst().fate(), "the record's fate is refused"),
                    () -> assertEquals(1, records.getFirst().outcomeRow(), "row 1 decided the refusal"),
                    () -> assertEquals("path_traversal_rejected", records.getFirst().refusalReason(),
                            "the record names the refusal reason"),
                    () -> assertNull(records.getFirst().childWallMs(), "a refused job has no child wall time"),
                    () -> assertNull(records.getFirst().pid(), "a refused job has no PID"),
                    () -> assertFalse(Files.exists(jobs.logPath(records.getFirst().jobId())),
                            "a refused job leaves no job log"),
                    () -> assertEquals(Optional.of(records.getFirst()), runner.recordOf(records.getFirst().jobId()),
                            "the refused record is answered"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "settled with fate 'refused' (outcome row 1)"));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("the enrolment lock of the verification is released before the launch")
        void enrolmentLockReleased() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);

            settled(started(runner.start(git(30, "ok"))));

            var enrolmentLock = fixture.store.lockKey(fixture.project);
            assertAll(
                    () -> assertTrue(locks.acquired.contains(enrolmentLock), "the verification takes the lock"),
                    () -> assertEquals(1, observations.size(), "the environment is asked once"),
                    () -> assertFalse(observations.getFirst().enrolmentLockHeld(),
                            "no transaction holds the enrolment lock at the launch"));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("settles a job whose launcher failed from the launcher's exit")
    class LauncherFailure {

        @Test
        @DisplayName("a launcher that exits 127 without a report leaves no PID and settles failed")
        void launcherExit() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.EXIT_127);

            var settled = settled(started(runner.start(git(30, "ok"))));

            assertAll(
                    () -> assertEquals(Fate.FAILED, settled.fate(), "the job of a failed launcher failed"),
                    () -> assertEquals(10, settled.outcomeRow(), "row 10 decided the launcher failure"),
                    () -> assertEquals(127L, settled.exitCode(), "the record carries the launcher exit 127"),
                    () -> assertNull(settled.pid(), "no post-spawn record without a launch report"),
                    () -> assertNull(settled.childWallMs(), "no reported process start"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.JOB_STARTED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a launcher that cannot be started settles the job failed by row 10 without a process")
        void launcherMissing() {
            var jobId = started(runner.start(git(30, "ok")));

            var settled = runner.recordOf(jobId).orElseThrow();

            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the unlaunched job has ended"),
                    () -> assertEquals(Fate.FAILED, settled.fate(), "the unlaunched job has failed"),
                    () -> assertEquals(10, settled.outcomeRow(), "row 10 decided the missing launcher"),
                    () -> assertNull(settled.exitCode(), "no exit was observed"),
                    () -> assertNull(settled.pid(), "an unlaunched job carries no PID"),
                    () -> assertNull(settled.spawnedAt(), "an unlaunched job has no spawn time"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the unlaunched job is not running"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_LAUNCHER_NOT_STARTED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            "Launcher of job '%s' not started".formatted(jobId)));
        }

        @Test
        @DisplayName("a fate that cannot be written stops the settlement and is logged at ERROR")
        void fateNotWritten() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var jobId = started(runner.start(git(60, "sleep", "60")));
            var directory = jobs.directoryPath();
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
            try {
                runner.cancel(jobId, CancelCause.ENGINE_CANCEL);

                var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT);

                assertAll(
                        () -> assertEquals(Optional.empty(), settled, "the fate was not recorded"),
                        () -> assertFalse(runner.runningJobs().contains(jobId), "the stopped job is no longer running"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                                ERROR.JOB_SETTLEMENT_STEP_FAILED.resolveIdentifierString()),
                        () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                                "Settlement of job '%s' stopped at step 'FATE'".formatted(jobId)),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                                INFO.JOB_SETTLED.resolveIdentifierString()));
            } finally {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            }
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("bounds the wait for the launch report apart from the budget")
    class ReportWait {

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.NO_REPORT);
        }

        @Test
        @DisplayName("a launcher that never writes its report does not hold the start for the budget")
        void silentLauncherDoesNotHoldTheStart() {
            var ticks = new AtomicLong();
            var origin = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
            // every reading of the clock is one second later, so the bound passes after a few polls, not after 10 s
            InstantSource clock = () -> origin.plusSeconds(ticks.getAndIncrement());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), (job, ignoredJobId) -> ENVIRONMENT,
                    (job, output) -> ParseResult.NOTHING, request -> Optional.empty(), clock);

            var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> runner.start(git(3600, "sleep", "3600")), "the start returns before the bound");
            var jobId = started(result);
            var waiting = runner.recordOf(jobId).orElseThrow();
            var runningAfterStart = runner.runningJobs().contains(jobId);
            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Status.RUNNING, waiting.status(), "the job was handed to its waiting thread"),
                    () -> assertNull(waiting.pid(), "no post-spawn record without a launch report"),
                    () -> assertTrue(runningAfterStart, "the job runs on after the wait"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the silent job ends by the cancel"));
        }

        @Test
        @DisplayName("a cancel during the wait for the launch report ends the wait at once")
        void cancelEndsTheWait() throws Exception {
            var launching = new CountDownLatch(1);
            var launched = new AtomicReference<JobId>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(), (job, ignoredJobId) -> {
                launched.set(jobs.records().getFirst().jobId());
                launching.countDown();
                return ENVIRONMENT;
            });
            var start = CompletableFuture.supplyAsync(() -> runner.start(git(3600, "sleep", "3600")));

            assertTrue(launching.await(10, TimeUnit.SECONDS), "the job reaches its launch");
            var cancelled = runner.cancel(launched.get(), CancelCause.ENGINE_CANCEL);
            var jobId = started(start.get(5, TimeUnit.SECONDS));
            var settled = settled(jobId);

            assertAll(
                    () -> assertTrue(cancelled, "the cancel decided the end of the job"),
                    () -> assertEquals(launched.get(), jobId, "the start answers the launched job"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the job cancelled in its wait"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the engine cancel"));
        }
    }

    @Nested
    @DisplayName("settles a job whose launch cannot be composed and passes the failure on")
    class LaunchNotComposed {

        @Test
        @DisplayName("an environment that throws settles the job failed by row 10 and leaves no running job")
        void environmentThrows() {
            var failure = new IllegalArgumentException(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    (job, ignoredJobId) -> {
                        throw failure;
                    });
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request),
                    "the composing failure reaches the caller");

            assertComposedFailureSettled(thrown, failure);
        }

        @Test
        @DisplayName("an environment name the operating system refuses settles the job failed by row 10")
        void environmentNameRefused() {
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    (job, ignoredJobId) -> Map.of("NAME=VALUE", "x"));
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request),
                    "the refused environment name reaches the caller");

            assertComposedFailureSettled(thrown, thrown);
        }

        @Test
        @DisplayName("a cancel answered before the launcher cannot be started decides the fate by its cause")
        void cancelBeforeLauncherNotStarted() {
            var cancelled = new AtomicBoolean();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    (job, ignoredJobId) -> {
                        cancelled.set(runner.cancel(jobs.records().getFirst().jobId(), CancelCause.ENGINE_CANCEL));
                        return ENVIRONMENT;
                    });

            var jobId = started(runner.start(git(30, "ok")));

            assertCancelledWithoutProcess(jobId, cancelled);
        }

        @Test
        @DisplayName("a cancel answered before the launch cannot be composed decides the fate by its cause")
        void cancelBeforeLaunchNotComposed() {
            var cancelled = new AtomicBoolean();
            var failure = new IllegalArgumentException(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    (job, ignoredJobId) -> {
                        cancelled.set(runner.cancel(jobs.records().getFirst().jobId(), CancelCause.ENGINE_CANCEL));
                        throw failure;
                    });
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request),
                    "the composing failure reaches the caller");

            assertEquals(failure, thrown, "the failure is passed on to the caller");
            assertCancelledWithoutProcess(jobs.records().getFirst().jobId(), cancelled);
        }

        @Test
        @DisplayName("a cancel that arrives while the unlaunched job settles is answered false, the job settles failed")
        void cancelDuringSettlementIsAnsweredFalse() {
            var late = new AtomicReference<Boolean>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    (job, ignoredJobId) -> {
                        var jobId = jobs.records().getFirst().jobId();
                        // the settlement of the unlaunched job opens its transaction after it has taken the cause of
                        // the cancels and before the job leaves the running jobs: the window of a late cancel
                        locks.beforeNextTransaction(() -> late.set(runner.cancel(jobId, CancelCause.ENGINE_CANCEL)));
                        return ENVIRONMENT;
                    });

            var jobId = started(runner.start(git(30, "ok")));

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertEquals(Boolean.FALSE, late.get(),
                            "the late cancel ran and was answered false"),
                    () -> assertEquals(jobId, settled.jobId(), "the record belongs to the started job"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the unlaunched job has ended"),
                    () -> assertEquals(Fate.FAILED, settled.fate(), "the late cancel did not decide the fate"),
                    () -> assertEquals(10, settled.outcomeRow(), "row 10 decided the unlaunched job"),
                    () -> assertNull(settled.exitCode(), "no exit was observed"),
                    () -> assertNull(settled.pid(), "no process was started"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "no job is left running"),
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL),
                            "a settled job is not cancelled again"));
        }

        private void assertCancelledWithoutProcess(JobId jobId, AtomicBoolean cancelled) {
            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertTrue(cancelled.get(), "the cancel decided the end of the job"),
                    () -> assertEquals(jobId, settled.jobId(), "the record belongs to the started job"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the cancelled job has ended"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides, not row 10"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the engine cancel"),
                    () -> assertNull(settled.pid(), "no process was started"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(jobId, Duration.ZERO),
                            "the settled record is answered at once"));
        }

        private void assertComposedFailureSettled(IllegalArgumentException thrown, IllegalArgumentException expected) {
            var records = jobs.records();
            assertAll(
                    () -> assertEquals(expected, thrown, "the failure is passed on to the caller"),
                    () -> assertEquals(1, records.size(), "one job record is written"),
                    () -> assertEquals(Status.ENDED, records.getFirst().status(), "the uncomposed job has ended"),
                    () -> assertEquals(Fate.FAILED, records.getFirst().fate(), "the uncomposed job has failed"),
                    () -> assertEquals(10, records.getFirst().outcomeRow(), "row 10 decided the uncomposed job"),
                    () -> assertNull(records.getFirst().pid(), "an uncomposed job carries no PID"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(records.getFirst()),
                            runner.awaitSettled(records.getFirst().jobId(), Duration.ZERO),
                            "the settled record is answered at once"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_LAUNCHER_NOT_STARTED.resolveIdentifierString()));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("settles a job whose post-spawn record cannot be written and passes the failure on")
    class PostSpawnNotWritten {

        /** The {@code errno} of a refused signal. */
        private static final int EPERM = 1;

        private final List<Long> signalledGroups = new CopyOnWriteArrayList<>();

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
        }

        @AfterEach
        void writable() throws IOException {
            if (Files.isDirectory(jobs.directoryPath())) {
                Files.setPosixFilePermissions(jobs.directoryPath(), PosixFilePermissions.fromString("rwx------"));
            }
        }

        /**
         * A runner whose signals are recorded. With {@code refusePostSpawn}, the directory of the records is made
         * read-only right before the post-spawn write, the first transaction after the environment of the launch is
         * asked for, and writable again with the first signal of the termination, so only the post-spawn write fails.
         */
        private SubprocessJobRunner recordingSignals(boolean refusePostSpawn) {
            return recordingSignals(refusePostSpawn ? () -> permissions("r-x------") : null, false);
        }

        /**
         * A runner whose signals are recorded.
         *
         * @param beforePostSpawn what runs right before the post-spawn write; {@code null} for nothing
         * @param refuseSignals   whether every signal is refused with {@code EPERM} instead of sent
         */
        private SubprocessJobRunner recordingSignals(Runnable beforePostSpawn, boolean refuseSignals) {
            var system = ProcessGroupSignals.system();
            var signals = new ProcessGroupSignals() {

                @Override
                public Answer signalGroup(long pgid, Signal signal) {
                    permissions("rwx------");
                    signalledGroups.add(pgid);
                    return refuseSignals ? new Refused(EPERM) : system.signalGroup(pgid, signal);
                }

                @Override
                public Answer signalProcess(long pid, Signal signal) {
                    permissions("rwx------");
                    return refuseSignals ? new Refused(EPERM) : system.signalProcess(pid, signal);
                }
            };
            return runner(new TerminationProtocol(signals), request -> Optional.empty(), (job, ignoredJobId) -> {
                if (beforePostSpawn != null) {
                    locks.beforeNextTransaction(beforePostSpawn);
                }
                return ENVIRONMENT;
            });
        }

        private void permissions(String mode) {
            try {
                Files.setPosixFilePermissions(jobs.directoryPath(), PosixFilePermissions.fromString(mode));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Test
        @DisplayName("the job is terminated, settled killed by row 4 and out of the running jobs before the failure")
        void settledBeforeFailure() {
            runner = recordingSignals(true);
            var request = git(60, "sleep", "60");

            assertThrows(UncheckedIOException.class, () -> runner.start(request),
                    "the I/O failure of the start reaches the caller");

            var records = jobs.records();
            assertEquals(1, records.size(), "one job was recorded");
            var settled = records.getFirst();
            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the record does not stay running"),
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the terminated job was killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "row 4 decided the killed job"),
                    () -> assertEquals("SIGTERM", settled.signal(), "the runner's signal ended the process"),
                    () -> assertNotNull(settled.pid(), "the process ran, its PID is recorded"),
                    () -> assertEquals(List.of(settled.pid()), signalledGroups.stream().distinct().toList(),
                            "the job's process group was terminated"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(settled.jobId(), Duration.ZERO),
                            "the settled record is answered at once"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(settled.jobId()),
                            "the record of the job is the settled one"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' settled with fate 'killed' (outcome row 4)".formatted(settled.jobId())),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.JOB_STARTED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a cancel answered before the post-spawn write failed decides the fate by its cause")
        void cancelInTheMeantime() {
            var cancelled = new AtomicBoolean();
            runner = recordingSignals(() -> {
                cancelled.set(runner.cancel(jobs.records().getFirst().jobId(), CancelCause.ENGINE_CANCEL));
                permissions("r-x------");
            }, false);
            var request = git(60, "sleep", "60");

            assertThrows(UncheckedIOException.class, () -> runner.start(request),
                    "the I/O failure of the start reaches the caller");

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertTrue(cancelled.get(), "the cancel decided the end of the job"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the cancelled job has ended"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides, not row 4"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the engine cancel"),
                    () -> assertEquals("SIGTERM", settled.signal(), "the cancelled job ended by SIGTERM"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(settled.jobId(), Duration.ZERO),
                            "the settled record is answered at once"));
        }

        @Test
        @DisplayName("a refused signal destroys the process through its handle, so the wait for its end is bounded")
        void signalRefused() {
            runner = recordingSignals(() -> permissions("r-x------"), true);
            var request = git(600, "sleep", "600");

            assertTimeoutPreemptively(SETTLE_TIMEOUT, () -> assertThrows(UncheckedIOException.class,
                            () -> runner.start(request), "the I/O failure of the start reaches the caller"),
                    "the wait for the process has an end");

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the destroyed job was killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "row 4 decided the killed job"),
                    () -> assertEquals("SIGKILL", settled.signal(), "destroyed through its handle"),
                    () -> assertEquals(List.of(settled.pid()), signalledGroups.stream().distinct().toList(),
                            "the group was signalled first"),
                    () -> assertFalse(alive(settled.pid()), "the process is not left running"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"));
        }

        @Test
        @DisplayName("an Error of the termination step still settles the job before it is passed on")
        void terminationFailsWithError() {
            var signals = new ProcessGroupSignals() {

                @Override
                public Answer signalGroup(long pgid, Signal signal) {
                    permissions("rwx------");
                    throw new StackOverflowError("kill(-%d) failed".formatted(pgid));
                }

                @Override
                public Answer signalProcess(long pid, Signal signal) {
                    permissions("rwx------");
                    throw new StackOverflowError("kill(%d) failed".formatted(pid));
                }
            };
            runner = runner(new TerminationProtocol(signals), request -> Optional.empty(), (job, ignoredJobId) -> {
                locks.beforeNextTransaction(() -> permissions("r-x------"));
                return ENVIRONMENT;
            });
            var request = git(600, "sleep", "600");

            assertThrows(UncheckedIOException.class, () -> runner.start(request),
                    "the I/O failure of the start reaches the caller");

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the job is not left running unsettled"),
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the destroyed job was killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "row 4 decided the killed job"),
                    () -> assertEquals("SIGKILL", settled.signal(), "destroyed through its handle"),
                    () -> assertFalse(alive(settled.pid()), "the process is not left running"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"));
        }

        @Test
        @DisplayName("matched control: a job whose post-spawn record is written is started and settled by its exit")
        void writtenPostSpawn() {
            runner = recordingSignals(false);

            var jobId = started(runner.start(git(30, "ok")));
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the written job succeeded by its exit"),
                    () -> assertTrue(signalledGroups.isEmpty(), "nothing was signalled"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.JOB_STARTED.resolveIdentifierString()));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("settles a job whose waiting thread fails and leaves no process running")
    class SupervisionFails {

        private LoggedWatch supervisionFailureLogged;

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            supervisionFailureLogged = new LoggedWatch(TestLogLevel.ERROR,
                    ERROR.JOB_SUPERVISION_FAILED.resolveIdentifierString());
        }

        @AfterEach
        void removeWatch() {
            supervisionFailureLogged.close();
        }

        @Test
        @DisplayName("a parser that throws: the job is settled without its finding, by row 8 for exit 0")
        void parserThrows() throws Exception {
            var failure = new IllegalStateException(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), (job, ignoredJobId) -> ENVIRONMENT,
                    (job, output) -> {
                        throw failure;
                    }, request -> Optional.empty());

            var jobId = started(runner.start(git(30, "ok")));

            assertSettledWithoutFinding(jobId, failure);
        }

        @Test
        @DisplayName("a parser that fails with an error: the job is settled the same way")
        void parserFailsWithError() throws Exception {
            var failure = new StackOverflowError(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), (job, ignoredJobId) -> ENVIRONMENT,
                    (job, output) -> {
                        throw failure;
                    }, request -> Optional.empty());

            var jobId = started(runner.start(git(30, "ok")));

            assertSettledWithoutFinding(jobId, failure);
        }

        /** A job that exited 0 and whose parser failed: indeterminate by row 8, the failure logged as it was thrown. */
        private void assertSettledWithoutFinding(JobId jobId, Throwable failure)
                throws InterruptedException, ExecutionException, TimeoutException {
            var settled = settled(jobId);
            supervisionFailureLogged.await();

            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the record does not stay running"),
                    () -> assertEquals(Fate.INDETERMINATE, settled.fate(), "exit 0 without the parser's marker"),
                    () -> assertEquals(8, settled.outcomeRow(), "row 8 decided the unparsed exit 0"),
                    () -> assertEquals(0L, settled.exitCode(), "the record carries the exit 0 of the job"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job left the running jobs"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(jobId),
                            "the record of the job is the settled one"),
                    () -> assertFalse(alive(settled.pid()), "the process is not left running"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' settled with fate 'indeterminate' (outcome row 8)".formatted(jobId)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Supervision of job '%s' failed".formatted(jobId), failure));
        }

        @Test
        @DisplayName("a termination that throws: the process is destroyed through its handle and the cause decides")
        void terminationThrows() throws Exception {
            var signals = new ProcessGroupSignals() {

                @Override
                public Answer signalGroup(long pgid, Signal signal) {
                    throw new IllegalStateException("kill(-%d) failed".formatted(pgid));
                }

                @Override
                public Answer signalProcess(long pid, Signal signal) {
                    throw new IllegalStateException("kill(%d) failed".formatted(pid));
                }
            };
            runner = runner(new TerminationProtocol(signals), request -> Optional.empty());
            var jobId = started(runner.start(git(60, "sleep", "60")));

            var cancelled = runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);
            supervisionFailureLogged.await();

            assertAll(
                    () -> assertTrue(cancelled, "the cancel decided the end of the job"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the record does not stay running"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cancel's cause decides"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the engine cancel"),
                    () -> assertEquals("SIGKILL", settled.signal(), "destroyed through its handle"),
                    () -> assertFalse(alive(settled.pid()), "the process is not left running"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job left the running jobs"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Supervision of job '%s' failed".formatted(jobId), IllegalStateException.class));
        }

        @Test
        @DisplayName("a fallback that fails as well: the job is still settled, by the last-resort record")
        void fallbackFails() throws Exception {
            var failure = new IllegalStateException(Generators.nonEmptyStrings().next());
            var lockFailure = new IllegalStateException(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), (job, ignoredJobId) -> ENVIRONMENT,
                    (job, output) -> {
                        locks.failNextTransaction(lockFailure);
                        throw failure;
                    }, request -> Optional.empty());

            var jobId = started(runner.start(git(30, "ok")));
            var settled = settled(jobId);
            supervisionFailureLogged.await();

            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(),
                            "a failing fallback does not leave the job unsettled"),
                    () -> assertEquals(Fate.INDETERMINATE, settled.fate(), "the unparsed job is indeterminate"),
                    () -> assertEquals(8, settled.outcomeRow(), "the fallback's record was written on the retry"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job left the running jobs"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(jobId),
                            "the record of the job is the settled one"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR,
                            ERROR.JOB_LAST_RESORT_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a last-resort write that fails as well is logged and leaves the record running for the recovery")
        void lastResortFails() throws Exception {
            var failure = new IllegalStateException(Generators.nonEmptyStrings().next());
            var fallbackLockFailure = new IllegalStateException(Generators.nonEmptyStrings().next());
            var lastResortLockFailure = new IllegalStateException(Generators.nonEmptyStrings().next());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), (job, ignoredJobId) -> ENVIRONMENT,
                    (job, output) -> {
                        // the fallback's record write opens the next transaction, the last-resort retry the one after
                        locks.beforeNextTransaction(() -> {
                            locks.failNextTransaction(fallbackLockFailure);
                            locks.beforeNextTransaction(() -> locks.failNextTransaction(lastResortLockFailure));
                        });
                        throw failure;
                    }, request -> Optional.empty());

            var jobId = started(runner.start(git(30, "ok")));
            supervisionFailureLogged.await();

            var stored = jobs.read(jobId).orElseThrow();
            assertAll(
                    () -> assertEquals(Status.RUNNING, stored.status(), "the unwritten job stays running"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.JOB_LAST_RESORT_FAILED.resolveIdentifierString(), IllegalStateException.class),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                            lastResortLockFailure.getMessage()));
        }

        @Test
        @DisplayName("a thread that cannot be started terminates and settles the job before the failure is passed on")
        void threadCannotBeStarted() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var failure = new IllegalStateException(Generators.nonEmptyStrings().next());
            runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(locks)
                    .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                    .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                    .environment((job, ignoredJobId) -> ENVIRONMENT).probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                    .parser((job, output) -> SUCCESS).primitives(request -> Optional.empty())
                    .supervision((name, onFailure, task) -> {
                        throw failure;
                    }).build();
            var request = git(600, "sleep", "600");

            var thrown = assertThrows(IllegalStateException.class, () -> runner.start(request),
                    "the supervision failure reaches the caller");

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertEquals(failure, thrown, "the failure is passed on to the caller"),
                    () -> assertEquals(Status.ENDED, settled.status(), "the job does not stay running"),
                    () -> assertEquals(Fate.KILLED, settled.fate(), "the unsupervised job was killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "row 4 decided the killed job"),
                    () -> assertEquals("SIGTERM", settled.signal(), "the started process was terminated"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(0, runner.storesHeld(), "the settled job's store is pruned"));
        }

        @Test
        @DisplayName("matched control: a job whose parser and termination work is settled without a failure")
        void nothingFails() {
            var jobId = started(runner.start(git(30, "ok")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the supervised job succeeded"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR,
                            ERROR.JOB_SUPERVISION_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_FAILED.resolveIdentifierString()));
        }
    }

    @Nested
    @DisplayName("serves the engine port")
    class EnginePort {

        @Test
        @DisplayName("a primitive without a job spec is refused")
        void unknownPrimitive() {
            var result = runner.start(new JobStarter.StartRequest(null, "verify",
                    Generators.letterStrings(4, 12).next()));

            var refused = assertInstanceOf(JobStarter.StartResult.Refused.class, result,
                    "a primitive without a job spec is refused");
            assertAll(
                    () -> assertEquals(SubprocessJobRunner.PRIMITIVE_WITHOUT_JOB, refused.reason(),
                            "the refusal names the missing job spec"),
                    () -> assertTrue(observations.isEmpty(), "nothing was launched for the primitive"),
                    () -> assertTrue(jobs.records().isEmpty(), "no record is written for the primitive"));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("matched control: a primitive with a job spec is started with an id of the port's grammar")
        void knownPrimitive() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var job = git(30, "ok");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.of(job));

            var result = runner.start(new JobStarter.StartRequest(null, "verify",
                    Generators.letterStrings(4, 12).next()));

            var started = assertInstanceOf(JobStarter.StartResult.Started.class, result,
                    "a primitive with a job spec is started");
            assertAll(
                    () -> assertEquals(JobStarter.JobKind.GIT, started.kind(), "the started job is a git job"),
                    () -> assertEquals(Fate.SUCCEEDED, settled(new JobId(started.jobId())).fate(),
                            "the started primitive job succeeded"));
        }

        @Test
        @DisplayName("the refusal of a job's verification is the refusal of the port")
        void refusedPrimitive() {
            var job = new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, 30, FLOOR,
                    fixture.gitStatus(fixture.beside.toString()), null);
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.of(job));

            var result = runner.start(new JobStarter.StartRequest(null, "verify",
                    Generators.letterStrings(4, 12).next()));

            // path_traversal_rejected is the reason code of the verification
            assertEquals(new JobStarter.StartResult.Refused("path_traversal_rejected"), result,
                    "the port refuses with the verification's reason");
        }
    }

    @Nested
    @DisplayName("refuses what it does not run")
    class NotRun {

        @Test
        @DisplayName("a worker is not started by the runner")
        void worker() {
            var request = new JobRunner.StartRequest<>(JobId.Kind.LLM, new Scope(Scope.Type.PROJECT, null),
                    BUDGET_KEY, 30, FLOOR, fixture.gitStatus(fixture.root.toString()), null);

            assertThrows(IllegalArgumentException.class, () -> runner.start(request),
                    "a worker job is refused by the runner");
        }

        @Test
        @DisplayName("an unknown job is neither cancelled nor read")
        void unknownJob() {
            var jobId = JobId.random(JobId.Kind.GIT);

            assertAll(
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL),
                            "an unknown job is not cancelled"),
                    () -> assertEquals(Optional.empty(), runner.recordOf(jobId), "an unknown job has no record"),
                    () -> assertEquals(Optional.empty(), runner.awaitSettled(jobId, Duration.ZERO),
                            "an unknown job is never settled"));
        }
    }

    @Nested
    @DisplayName("derives the record's values from what it observed")
    class Values {

        // the exit values, signal numbers and signal names below are the wait-status contract of the two systems
        @ParameterizedTest(name = "exit value {0}")
        @CsvSource({"0, 0", "3, 3", "128, 128", "200, 200"})
        @DisplayName("an exit value outside 129 to 192 is an exit code")
        void exitCode(int exitValue, int code) {
            assertAll(
                    () -> assertEquals(new Exited(code),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX),
                            "Linux reads the exit value as an exit code"),
                    () -> assertEquals(new Exited(code),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS),
                            "macOS reads the exit value as an exit code"));
        }

        @ParameterizedTest(name = "exit value {0} is {1}")
        @CsvSource({"129, SIGHUP", "134, SIGABRT", "137, SIGKILL", "139, SIGSEGV", "143, SIGTERM", "168, SIG40"})
        @DisplayName("an exit value of 128 plus a signal number that both systems share is that signal")
        void signal(int exitValue, String signal) {
            assertAll(
                    () -> assertEquals(new Signalled(signal),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX),
                            "Linux names the shared signal the same"),
                    () -> assertEquals(new Signalled(signal),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS),
                            "macOS names the shared signal the same"));
        }

        @ParameterizedTest(name = "signal {0}: Linux {1}, macOS {2}")
        @CsvSource({"7, SIGBUS, SIGEMT", "10, SIGUSR1, SIGBUS", "12, SIGUSR2, SIGSYS", "16, SIGSTKFLT, SIGURG",
                "17, SIGCHLD, SIGSTOP", "18, SIGCONT, SIGTSTP", "19, SIGSTOP, SIGCONT", "20, SIGTSTP, SIGCHLD",
                "23, SIGURG, SIGIO", "29, SIGIO, SIGINFO", "30, SIGPWR, SIGUSR1", "31, SIGSYS, SIGUSR2",
                "32, SIG32, SIG32"})
        @DisplayName("a signal number the two systems name differently is named by the table of the system")
        void divergentSignal(int signal, String linux, String macos) {
            var exitValue = 128 + signal;

            assertAll(
                    () -> assertEquals(new Signalled(linux),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX),
                            "the Linux table names the signal"),
                    () -> assertEquals(new Signalled(macos),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS),
                            "the macOS table names the signal"));
        }

        // the names are the values of the system property os.name
        @ParameterizedTest(name = "{0}")
        @CsvSource({"Linux, LINUX", "Mac OS X, MACOS", "macOS, MACOS", "'', LINUX"})
        @DisplayName("the table is chosen by the name of the operating system")
        void signalTableOfOs(String osName, SubprocessJobRunner.SignalTable table) {
            assertEquals(table, SubprocessJobRunner.SignalTable.of(osName), "the OS name selects the signal table");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(CancelCause.class)
        @DisplayName("every cancel cause is the termination of its name and decides the fate of its row")
        void termination(CancelCause cause) {
            var termination = SubprocessJobRunner.termination(cause);
            var outcome = OutcomeDerivation.derive(new JobEvidence.ProcessEnded(JobId.Kind.GIT,
                    termination, new Exited(0), JobEvidence.LogState.READABLE, SUCCESS, Set.of(), true));

            assertAll(
                    () -> assertEquals(cause.fate(), outcome.fate(), "the termination decides the cause's fate"),
                    () -> assertEquals(cause.outcomeRow(), outcome.row(), "the termination decides the cause's row"));
        }

        @Test
        @DisplayName("a pause of the plan that holds now is the termination PAUSE_NOW")
        void pauseNow() {
            assertEquals(Termination.PAUSE_NOW, SubprocessJobRunner.termination(CancelCause.PLAN_PAUSED_NOW),
                    "a pause now is the termination PAUSE_NOW");
        }

        @Test
        @DisplayName("a reused PID is not signalled, at the primitive; the started process and an unowned PID are")
        void pidReuse() throws Exception {
            var first = new ProcessBuilder(FakePmExec.tool("setsid").toString(), FakePmExec.tool("sleep").toString(),
                    "30").start();
            // the start instant has the clock tick's granularity: a later start makes the two distinguishable
            first.waitFor(50, TimeUnit.MILLISECONDS);
            var second = new ProcessBuilder(FakePmExec.tool("setsid").toString(), FakePmExec.tool("sleep").toString(),
                    "30").start();
            try {
                var recorded = new CopyOnWriteArrayList<Long>();
                ProcessGroupSignals signals = new ProcessGroupSignals() {

                    @Override
                    public Answer signalGroup(long pgid, Signal signal) {
                        recorded.add(pgid);
                        return new NoSuchProcess();
                    }

                    @Override
                    public Answer signalProcess(long pid, Signal signal) {
                        recorded.add(pid);
                        return new NoSuchProcess();
                    }
                };
                runner = runner(new TerminationProtocol(signals), request -> Optional.empty());
                var reused = new PmExecLauncher.Launched(second, first.pid());

                var cancelBeforeGroup = runner.terminateByHandle(JobId.random(JobId.Kind.GIT), reused);

                assertAll(
                        () -> assertTrue(SubprocessJobRunner.pidReused(reused),
                                "the first's PID is not owned by the second's handle"),
                        () -> assertFalse(cancelBeforeGroup, "a reused PID is not terminated by its handle"),
                        () -> assertTrue(recorded.isEmpty(), "a reused PID is never signalled"),
                        () -> assertFalse(SubprocessJobRunner.pidReused(
                                        new PmExecLauncher.Launched(first, first.pid())),
                                "the process the runner started is not a reused PID"),
                        () -> assertFalse(SubprocessJobRunner.pidReused(new PmExecLauncher.Launched(
                                        new StubProcess(() -> false), 1_999_999_999L)),
                                "an unowned PID leaves its group to be terminated"));
            } finally {
                first.destroyForcibly();
                second.destroyForcibly();
                first.waitFor(10, TimeUnit.SECONDS);
                second.waitFor(10, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("a descendant that outlived the sweep is written as a security audit record")
        void survivingDescendantIsAudited() {
            var events = new CopyOnWriteArrayList<AuditEvent>();
            runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(locks)
                    .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                    .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                    .environment((job, ignoredJobId) -> ENVIRONMENT).probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                    .parser((job, output) -> SUCCESS).primitives(request -> Optional.empty())
                    .audit(events::add).build();
            var jobId = JobId.random(JobId.Kind.PROBE);
            var jobRecord = JobRecord.builder().jobId(jobId).execution(JobRecord.Execution.PROCESS)
                    .scope(new Scope(Scope.Type.MACHINE, null))
                    .status(Status.RUNNING).budgetKey(BUDGET_KEY).budgetSeconds(30).budgetDerivation(FLOOR)
                    .argvDigest(sha256Digests().next()).confinement(JobRecord.Confinement.UNAVAILABLE)
                    .createdAt(Instant.now()).clampedValues(List.of()).build();
            var job = new SubprocessJobRunner.RunningJob(jobRecord, null, new JobBudget(BUDGET_KEY, 30, FLOOR),
                    Set.of());

            int pid = Generators.integers(2, 4_000_000).next();
            var executable = "/" + Generators.letterStrings(3, 12).next();

            runner.auditSurvivors(job, List.of(new ProcessTreeTracker.Survivor(pid, executable),
                    new ProcessTreeTracker.Survivor(pid + 1, null)));

            // security and failed are the category and outcome of the audit record's schema
            assertAll(
                    () -> assertEquals(2, events.size(), "one audit record per surviving descendant"),
                    () -> assertEquals(SubprocessJobRunner.JOB_DESCENDANT_SURVIVED, events.get(0).event(),
                            "the record names the surviving descendant"),
                    () -> assertEquals("security", events.get(0).category(), "the record is a security record"),
                    () -> assertEquals("failed", events.get(0).outcome(), "the record's outcome is failed"),
                    () -> assertEquals(Long.valueOf(pid), events.get(0).details().get(SubprocessJobRunner.DETAIL_PID),
                            "the record names the survivor's PID"),
                    () -> assertEquals(jobId.value(), events.get(0).details().get(SubprocessJobRunner.DETAIL_JOB_ID),
                            "the record names the job of the survivor"),
                    () -> assertEquals(executable,
                            events.get(0).details().get(SubprocessJobRunner.DETAIL_EXECUTABLE),
                            "the record names the survivor's executable"),
                    () -> assertFalse(events.get(1).details()
                                    .containsKey(SubprocessJobRunner.DETAIL_EXECUTABLE),
                            "no executable is known for the survivor"));
        }

        @Test
        @DisplayName("an audit sink that refuses a survivor's record is logged per survivor and stops nothing")
        void refusedSurvivorAuditIsLogged() {
            var failureMessage = Generators.nonEmptyStrings().next();
            runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(locks)
                    .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                    .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                    .environment((job, ignoredJobId) -> ENVIRONMENT).probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                    .parser((job, output) -> SUCCESS).primitives(request -> Optional.empty())
                    .audit(event -> {
                        throw new IllegalStateException(failureMessage);
                    }).build();
            var jobId = JobId.random(JobId.Kind.PROBE);
            var jobRecord = JobRecord.builder().jobId(jobId).execution(JobRecord.Execution.PROCESS)
                    .scope(new Scope(Scope.Type.MACHINE, null))
                    .status(Status.RUNNING).budgetKey(BUDGET_KEY).budgetSeconds(30).budgetDerivation(FLOOR)
                    .argvDigest(sha256Digests().next()).confinement(JobRecord.Confinement.UNAVAILABLE)
                    .createdAt(Instant.now()).clampedValues(List.of()).build();
            var job = new SubprocessJobRunner.RunningJob(jobRecord, null, new JobBudget(BUDGET_KEY, 30, FLOOR),
                    Set.of());
            int pid = Generators.integers(2, 4_000_000).next();

            runner.auditSurvivors(job, List.of(new ProcessTreeTracker.Survivor(pid, null),
                    new ProcessTreeTracker.Survivor(pid + 1, null)));

            var logged = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    WARN.JOB_DESCENDANT_AUDIT_FAILED.resolveIdentifierString());
            assertAll(
                    () -> assertEquals(2, logged.size(), "one refused audit record is logged per survivor"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, failureMessage),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "the refused audit starts no job"));
        }

        @Test
        @DisplayName("a sweep that throws is logged and does not keep the job from being settled")
        void failingSweepDoesNotAbortTheSettlement() {
            var start = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
            var failureMessage = Generators.nonEmptyStrings().next();
            ProcessGroupSignals throwing = new ProcessGroupSignals() {

                @Override
                public Answer signalGroup(long pgid, Signal signal) {
                    throw new IllegalStateException(failureMessage);
                }

                @Override
                public Answer signalProcess(long pid, Signal signal) {
                    throw new IllegalStateException(failureMessage);
                }
            };
            int root = Generators.integers(2, 4_000_000).next();
            var tracker = new ProcessTreeTracker(root, ignoredRoot -> Set.of(new ProcessTreeTracker.Member(root + 1,
                            start)),
                    pid -> Optional.of(start), throwing, InstantSource.system(), duration -> {
                    });
            tracker.refresh();
            var jobId = JobId.random(JobId.Kind.PROBE);
            var jobRecord = JobRecord.builder().jobId(jobId).execution(JobRecord.Execution.PROCESS)
                    .scope(new Scope(Scope.Type.MACHINE, null)).status(Status.RUNNING).budgetKey(BUDGET_KEY)
                    .budgetSeconds(30).budgetDerivation(FLOOR).argvDigest(sha256Digests().next())
                    .confinement(JobRecord.Confinement.UNAVAILABLE).createdAt(Instant.now())
                    .clampedValues(List.of()).build();
            var job = new SubprocessJobRunner.RunningJob(jobRecord, null, new JobBudget(BUDGET_KEY, 30, FLOOR),
                    Set.of());
            job.tracks(tracker);

            var survivors = job.sweepTree();

            assertAll(
                    () -> assertEquals(List.of(), survivors, "the sweep answers no survivor"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "the test runner starts no job"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_DESCENDANT_SWEEP_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            "The descendant sweep of job '%s' failed".formatted(jobId)));
        }

        @Test
        @DisplayName("the child's wall time runs from its reported start and never exceeds the job's")
        void childWall() {
            var spawnedAt = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
            var endedAt = spawnedAt.plusMillis(1500);

            assertAll(
                    () -> assertTrue(SubprocessJobRunner.childWallMs(null, spawnedAt, endedAt).isEmpty(),
                            "no reported start yields no wall time"),
                    () -> assertEquals(1400L, SubprocessJobRunner.childWallMs(spawnedAt.plusMillis(100), spawnedAt,
                            endedAt).orElseThrow(), "a reported start after the spawn bounds the wall"),
                    () -> assertEquals(1500L, SubprocessJobRunner.childWallMs(spawnedAt.minusSeconds(1), spawnedAt,
                            endedAt).orElseThrow(), "a start before the spawn is bounded by the job wall"),
                    () -> assertEquals(0L, SubprocessJobRunner.childWallMs(endedAt.plusMillis(5), spawnedAt,
                            endedAt).orElseThrow(), "a start after the end is bounded to zero"));
        }

        @Test
        @DisplayName("the argument digest separates the elements of the vector")
        void argvDigest() {
            var element = Generators.letterStrings(2, 8).next();

            var joined = SubprocessJobRunner.argvDigest(List.of(element + element));
            var split = SubprocessJobRunner.argvDigest(List.of(element, element));

            assertAll(
                    () -> assertTrue(joined.matches("[0-9a-f]{64}"), "the digest is 64 lower-case hex digits"),
                    () -> assertNotEquals(joined, split, "a joined and a split vector differ in digest"),
                    () -> assertEquals(split, SubprocessJobRunner.argvDigest(new ArrayList<>(List.of(element,
                            element))), "the digest depends on the elements, not the list"));
        }
    }

    @Nested
    @DisplayName("JobBudget")
    class Budget {

        @Test
        @DisplayName("is the budget a start request names, counted in seconds")
        void ofRequest() {
            var seconds = Generators.integers(1, 3600).next();

            var budget = JobBudget.of(git(seconds, "ok"));

            assertAll(
                    () -> assertEquals(new JobBudget(BUDGET_KEY, seconds, FLOOR), budget,
                            "the budget is the one the request names"),
                    () -> assertEquals(Duration.ofSeconds(seconds), budget.duration(),
                            "the budget is counted in seconds"));
        }

        @Test
        @DisplayName("refuses a blank key, a budget below one second and missing parts")
        void refusals() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobBudget(" ", 1, FLOOR),
                            "a blank budget key is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobBudget(BUDGET_KEY, 0, FLOOR),
                            "a budget below one second is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new JobBudget(null, 1, FLOOR),
                            "a missing budget key is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new JobBudget(BUDGET_KEY, 1, null),
                            "a missing budget derivation is refused"));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("answers a cancel of a job with a process by one arbiter, its cancel door")
    class CancelDoorOfLaunchedJob {

        @BeforeEach
        void install() throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
        }

        /** Waits until the process of a PID is reaped, so the waiting thread finds its exit when it first looks. */
        private void awaitReaped(Long pid) {
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (alive(pid)) {
                assertTrue(System.nanoTime() < deadline, "the process %s did not end".formatted(pid));
                Thread.onSpinWait();
            }
        }

        @Test
        @DisplayName("a cancel answered true decides the fate by its cause although the process exited by itself first")
        void cancelAnsweredBeforeTheExitDecidesTheFate() {
            var cancelled = new AtomicBoolean();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(), (job, ignoredJobId) -> {
                var jobId = jobs.records().getFirst().jobId();
                // The post-spawn write is the first transaction after the launch, before the waiting thread starts: the
                // report is read and the job lives, so the cancel is answered true; the job is then released, ends by
                // itself, and is reaped before the waiting thread looks at it for the first time.
                locks.beforeNextTransaction(() -> {
                    cancelled.set(runner.cancel(jobId, CancelCause.ENGINE_CANCEL));
                    var lines = assertInstanceOf(JobLog.Lines.class, new JobLog(jobs.logPath(jobId)).readLines(0),
                            "the job log answers lines");
                    try {
                        Files.createFile(release);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    awaitReaped(lines.report().pid());
                });
                return ENVIRONMENT;
            });

            var jobId = started(runner.start(git(30, "release")));
            var settled = settled(jobId);

            assertAll(
                    () -> assertTrue(cancelled.get(), "the cancel was answered true: it decided the end of the job"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides, not the exit"),
                    () -> assertEquals(2, settled.outcomeRow(), "row 2 decided the engine cancel"),
                    () -> assertEquals(0L, settled.exitCode(), "the job ended by itself"),
                    () -> assertNull(settled.signal(), "the process was not signalled"));
        }

        @Test
        @DisplayName("matched control: a cancel that arrives while the settlement runs is answered false, the exit decides")
        void cancelDuringSettlementIsAnsweredFalse() {
            var late = new AtomicReference<Boolean>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(), (job, ignoredJobId) -> {
                var jobId = jobs.records().getFirst().jobId();
                // the first hook fires at the post-spawn write and arms the second for the next transaction, which is
                // the settlement of the ended job, after its cancel door was closed
                locks.beforeNextTransaction(() -> locks.beforeNextTransaction(
                        () -> late.set(runner.cancel(jobId, CancelCause.ENGINE_CANCEL))));
                return ENVIRONMENT;
            });

            var jobId = started(runner.start(git(30, "ok")));
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Boolean.FALSE, late.get(), "the cancel ran, and was answered false"),
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the late cancel did not decide the fate"),
                    () -> assertEquals(7, settled.outcomeRow(), "row 7 decided the successful exit"));
        }
    }

    /**
     * The window between the liveness check of a cancel and the completion of its cause is a few instructions wide, so
     * these tests drive the package-private {@code RunningJob} and {@code awaitEndOrCause} with a process whose
     * liveness they control.
     */
    @Nested
    @DisplayName("closes the cancel door when the waiting thread observes the end of the process")
    class CancelDoorSeam {

        private SubprocessJobRunner.RunningJob job() {
            return new SubprocessJobRunner.RunningJob(null, null, new JobBudget(BUDGET_KEY, 30, FLOOR), Set.of());
        }

        @Test
        @DisplayName("a cancel in flight when the exit is observed is answered true and is the cause that is read")
        void cancelInFlightIsTheCauseRead() {
            var inside = new CountDownLatch(1);
            var proceed = new CountDownLatch(1);
            var process = new StubProcess(() -> {
                inside.countDown();
                assertTrue(await(proceed), "the cancel is let proceed");
                return true;
            });
            process.ended();
            var job = job();
            job.launched(process);
            var cancel = CompletableFuture.supplyAsync(() -> job.cancel(Termination.ENGINE_CANCEL));
            assertTrue(await(inside), "the cancel checks liveness while holding the job");
            var waiting = CompletableFuture.supplyAsync(() -> runner.awaitEndOrCause(job, process,
                    Instant.now().plusSeconds(30)));

            proceed.countDown();

            assertAll(
                    () -> assertTrue(cancel.get(10, TimeUnit.SECONDS), "the cancel decided the end of the job"),
                    () -> assertEquals(Termination.ENGINE_CANCEL,
                            waiting.get(10, TimeUnit.SECONDS).cause(), "the exit does not hide an answered cancel"));
        }

        @Test
        @DisplayName("an exit observed together with an answered cancel returns the cause of the cancel")
        void exitDoesNotHideAnAnsweredCancel() {
            var process = new StubProcess(() -> true);
            var job = job();
            job.launched(process);
            var cancelled = job.cancel(Termination.ENGINE_CANCEL);
            process.ended();

            var awaited = runner.awaitEndOrCause(job, process, Instant.now().plusSeconds(30));

            assertAll(
                    () -> assertTrue(cancelled, "the cancel decided the end of the job"),
                    () -> assertEquals(Termination.ENGINE_CANCEL, awaited.cause(),
                            "an exit before the first look leaves the cause"));
        }

        @Test
        @DisplayName("a cancel after the exit was observed is answered false although its liveness check is stale")
        void cancelAfterTheExitIsAnsweredFalse() {
            var process = new StubProcess(() -> true);
            process.ended();
            var job = job();
            job.launched(process);

            var awaited = runner.awaitEndOrCause(job, process, Instant.now().plusSeconds(30));
            var late = job.cancel(Termination.ENGINE_CANCEL);

            assertAll(
                    () -> assertNull(awaited.cause(), "nothing decided the end: the exit does"),
                    () -> assertFalse(late, "the door is closed, the fate is no longer open to a cancel"));
        }

        /** @return whether the latch was released within ten seconds; an interrupted wait is not */
        private static boolean await(CountDownLatch latch) {
            try {
                return latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Nested
    @DisplayName("keeps the interrupt status of every wait and ends the wait with it")
    class Interrupts {

        @TempDir
        Path temp;

        private SubprocessJobRunner.RunningJob job() {
            return new SubprocessJobRunner.RunningJob(null, null, new JobBudget(BUDGET_KEY, 30, FLOOR), Set.of());
        }

        @Test
        @DisplayName("the wait for the process reports the interrupt and still returns the exit")
        void waitForReportsInterrupt() {
            var exit = SubprocessJobRunner.waitFor(new StubProcess(() -> true, true));

            assertAll(
                    () -> assertEquals(7, exit.value(), "the exit of the stub process is returned"),
                    () -> assertTrue(exit.interrupted(), "the interrupt of the wait is reported"));
        }

        @Test
        @DisplayName("the wait for the end of the process reports the interrupt; the exit still ends it")
        void awaitEndOrCauseReportsInterrupt() {
            var process = new StubProcess(() -> true);
            var job = job();
            job.launched(process);
            Thread.currentThread().interrupt();
            var ending = CompletableFuture.runAsync(process::ended,
                    CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));

            var awaited = runner.awaitEndOrCause(job, process, Instant.now().plusSeconds(30));
            ending.join();

            assertAll(
                    () -> assertTrue(awaited.interrupted(), "the interrupt of the wait is reported"),
                    () -> assertNull(awaited.cause(), "the exit ended the wait, no cause did"));
        }

        @Test
        @DisplayName("the wait for the launch report reports the interrupt by ending without one")
        void awaitReportReportsInterrupt() throws Exception {
            var logPath = temp.resolve("job.log");
            Files.writeString(logPath, "");
            var process = new StubProcess(() -> true);
            var job = job();
            job.launched(process);
            Thread.currentThread().interrupt();

            var awaited = runner.awaitReport(job, process, new JobLog(logPath), Instant.now().plusSeconds(30));
            var interruptStatus = Thread.interrupted();

            assertAll(
                    () -> assertNull(awaited.report(), "the interrupted wait ends without a report"),
                    () -> assertEquals(0L, awaited.observedLength(), "nothing of the empty log was observed"),
                    () -> assertTrue(interruptStatus, "the interrupt is restored for the caller"));
        }

        @Test
        @DisplayName("records the descendants of a probe and of a worker, and of no other kind")
        void tracksDescendantsOfProbesAndWorkers() {
            for (var kind : JobId.Kind.values()) {
                assertEquals(kind == JobId.Kind.PROBE || kind == JobId.Kind.LLM,
                        SubprocessJobRunner.tracksDescendants(kind),
                        () -> "descendants tracked only for probe and LLM: " + kind.wireName());
            }
        }
    }

    /**
     * The seams of the runner that the parser and the settlement open: the parser receives the job record, the runner
     * accepts replacement settlement steps, and a failed build keeps its raw log and yields the bounded evidence the
     * later failure gathering wraps.
     */
    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("opens the parser and settlement seams")
    class ParserAndSettlementSeams {

        private SubprocessJobRunner.Environment environment() {
            return (job, ignoredJobId) -> ENVIRONMENT;
        }

        /** The plan whose scope the build jobs of these tests run in, drawn per test. */
        private String planId;

        @BeforeEach
        void drawPlan() {
            planId = "plan-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        }

        private JobId startedBuild(String budgetKey, JobRequest.Target target) {
            var result = runner.start(new JobRunner.StartRequest<>(JobId.Kind.BUILD,
                    new Scope(Scope.Type.PLAN, planId), budgetKey, 30, FLOOR,
                    fixture.request(fixture.root.toString(), target), null));
            return assertInstanceOf(JobRunner.StartResult.Started.class, result,
                    "the build job of the budget key starts").jobId();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(BuildSystem.class)
        @DisplayName("the parser receives the job record of every build system")
        void parserReceivesTheRecord(BuildSystem system) throws Exception {
            var budgetKey = system.wireName() + ":verify";
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var seen = new AtomicReference<JobRecord>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), environment(),
                    (job, output) -> {
                        seen.set(job);
                        return SUCCESS;
                    }, request -> Optional.empty(), null);

            var jobId = startedBuild(budgetKey,
                    new JobRequest.HostToolCall(HostToolName.GIT, List.of("ok")));
            settled(jobId);

            assertAll(
                    () -> assertNotNull(seen.get(), "the parser ran for the ended job"),
                    () -> assertEquals(budgetKey, seen.get().budgetKey(), "the parser received the job key"),
                    () -> assertEquals(JobId.Kind.BUILD, seen.get().kind(), "the parser received the kind"));
        }

        @Test
        @DisplayName("a replacement settlement step runs in the runner's settlement")
        void settlementStepsReachTheRunner() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var ran = new AtomicBoolean();
            SettlementStep replacement = createMock(SettlementStep.class);
            expect(replacement.name()).andStubReturn(Name.CHANGE_LEDGER_ROW);
            expect(replacement.run(anyObject(SettlementStep.Settled.class))).andStubAnswer(() -> {
                ran.set(true);
                return new SettlementStep.Outcome.Done(Generators.letterStrings(1, 32).next());
            });
            replay(replacement);
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), environment(),
                    (job, output) -> SUCCESS, request -> Optional.empty(), null,
                    Map.of(Name.CHANGE_LEDGER_ROW, replacement));

            var jobId = started(runner.start(git(30, "ok")));
            settled(jobId);

            assertTrue(ran.get(), "the replacement step ran in the settlement");
        }

        @Test
        @DisplayName("refuses a misnamed settlement step at construction")
        void refusesAMisnamedSettlementStep() {
            SettlementStep misnamed = createMock(SettlementStep.class);
            expect(misnamed.name()).andStubReturn(Name.TIMING_OBSERVATION);
            replay(misnamed);

            var termination = new TerminationProtocol(ProcessGroupSignals.system());
            var env = environment();
            Map<Name, SettlementStep> steps = Map.of(Name.METRICS_ROW, misnamed);

            assertThrows(IllegalArgumentException.class, () -> runner(termination, env,
                            (job, output) -> SUCCESS, request -> Optional.empty(), null, steps),
                    "a step filed under another name is refused");
        }

        @Test
        @DisplayName("an unparsed build keeps its raw log and yields the bounded evidence")
        void failedUnparsedKeepsTheRawLogAndYieldsTheEvidence() throws Exception {
            var wrapper = fixture.root.resolve(JobEnrolmentFixture.MVNW);
            var failureLine = Generators.letterStrings(8, 24).next();
            // a POSIX shell script ends its lines with LF: the platform line separator %n would break the shebang
            Files.writeString(wrapper, "#!/bin/sh\necho '" + failureLine + "'\nexit 3\n");
            Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwx------"));
            fixture.enrol();
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            DefaultTemplates verifyTemplate = (buildSystem, verb) -> "verify".equals(verb)
                    ? Optional.of(new Argv(List.of(new Argv.Literal("verify")))) : Optional.empty();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), environment(),
                    new BuildOutputParser(), request -> Optional.empty(), null, Map.of(),
                    fixture.pipeline(verifyTemplate));

            var jobId = startedBuild("maven:verify", new WrapperBuild(WrapperName.MVNW, "verify", null));
            var settledJob = settled(jobId);
            var rawLog = Files.readString(jobs.logPath(jobId));
            var evidence = UnparsedEvidence.of(planId, jobId, rawLog);

            // pm://plan/{plan}/builds/{job}/log is the resource URI of the build log
            assertAll(
                    () -> assertEquals(Fate.FAILED_UNPARSED, settledJob.fate(), "the build ended failed_unparsed"),
                    () -> assertEquals(10, settledJob.outcomeRow(), "row 10 decided the fate"),
                    () -> assertTrue(rawLog.contains(failureLine), "the raw log is kept beside the record"),
                    () -> assertTrue(evidence.excerpt().contains(failureLine), "the excerpt carries the tail"),
                    () -> assertEquals("pm://plan/" + planId + "/builds/" + jobId.value() + "/log",
                            evidence.excerptUri(), "the evidence names the full log"));
        }
    }

    /** A process whose exit and liveness the test controls. */
    private static final class StubProcess extends Process {
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        private final BooleanSupplier alive;
        private final boolean interruptOnFirstWait;
        private int waits;

        StubProcess(BooleanSupplier alive) {
            this(alive, false);
        }

        StubProcess(BooleanSupplier alive, boolean interruptOnFirstWait) {
            this.alive = alive;
            this.interruptOnFirstWait = interruptOnFirstWait;
        }

        /** Completes the exit of the process. */
        void ended() {
            exit.complete(this);
        }

        @Override
        public boolean isAlive() {
            return alive.getAsBoolean();
        }

        @Override
        public CompletableFuture<Process> onExit() {
            return exit;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            if (interruptOnFirstWait && waits++ == 0) {
                throw new InterruptedException("the test interrupts the wait");
            }
            return 7;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // nothing runs
        }
    }

    /** A lock manager that records the keys acquired and knows which transactions are open. */
    private static final class RecordingLocks implements LockManager {

        private final LockManager delegate;
        private final Set<LockTransaction> open = ConcurrentHashMap.newKeySet();
        private final List<LockKey> acquired = new CopyOnWriteArrayList<>();
        private final AtomicReference<Runnable> beforeNext = new AtomicReference<>();
        private final AtomicReference<RuntimeException> failNext = new AtomicReference<>();

        RecordingLocks(LockManager delegate) {
            this.delegate = delegate;
        }

        /** Runs an action once, right before the next transaction is opened. */
        void beforeNextTransaction(Runnable action) {
            beforeNext.set(action);
        }

        /** Fails the next transaction when it is opened, before it reaches the delegate. */
        void failNextTransaction(RuntimeException failure) {
            failNext.set(failure);
        }

        @Override
        public LockTransaction openTransaction() {
            var action = beforeNext.getAndSet(null);
            if (action != null) {
                action.run();
            }
            var failure = failNext.getAndSet(null);
            if (failure != null) {
                throw failure;
            }
            var transaction = delegate.openTransaction();
            var recording = new LockTransaction() {

                @Override
                public void acquire(LockKey key) {
                    acquired.add(key);
                    transaction.acquire(key);
                }

                @Override
                public boolean holds(LockKey key) {
                    return transaction.holds(key);
                }

                @Override
                public void close() {
                    open.remove(this);
                    transaction.close();
                }
            };
            open.add(recording);
            return recording;
        }

        boolean anyOpenHolds(LockKey key) {
            return open.stream().anyMatch(transaction -> transaction.holds(key));
        }
    }
}
