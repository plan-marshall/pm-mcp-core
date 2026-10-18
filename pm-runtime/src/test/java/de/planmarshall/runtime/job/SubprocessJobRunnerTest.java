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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.PROJECT;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

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
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.ParseResult;
import de.planmarshall.runtime.job.JobEvidence.Signalled;
import de.planmarshall.runtime.job.JobEvidence.Termination;
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
    private static final ParseResult SUCCESS = new ParseResult(true, 0, false, Optional.empty());

    /** The enrolled {@code git} of the fixture: its first argument selects what the job does. */
    private static final String GIT_SCRIPT = """
            #!/bin/sh
            case "$1" in
              ok) echo done; exit 0 ;;
              fail) echo broken; exit 3 ;;
              sleep) exec @SLEEP@ "$2" ;;
              stubborn) trap '' TERM; echo ready; while :; do @SLEEP@ 0.1; done ;;
            esac
            exit 2
            """;

    private JobEnrolmentFixture fixture;
    private RecordingLocks locks;
    private JobRecordStore jobs;
    private Path home;
    private final List<Observation> observations = new CopyOnWriteArrayList<>();
    private SubprocessJobRunner runner;

    /** What the runner had written and held when it asked for the environment, right before the launch. */
    private record Observation(List<JobRecord> records, boolean enrolmentLockHeld) {
    }

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        Files.writeString(fixture.tools.get(HostToolName.GIT),
                GIT_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString()));
        fixture.enrol();
        locks = new RecordingLocks(fixture.locks);
        jobs = JobRecordStore.ofProject(fixture.base, PROJECT);
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
        return runner(termination, primitives, job -> ENVIRONMENT);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.PrimitiveJobs primitives,
            SubprocessJobRunner.Environment environment) {
        return runner(termination, environment,
                (kind, output) -> output.contains("done") ? SUCCESS : ParseResult.NOTHING, primitives);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives) {
        return runner(termination, environment, parser, primitives, null);
    }

    private SubprocessJobRunner runner(TerminationProtocol termination, SubprocessJobRunner.Environment environment,
            SubprocessJobRunner.OutputParser parser, SubprocessJobRunner.PrimitiveJobs primitives,
            InstantSource clock) {
        return SubprocessJobRunner.builder().base(fixture.base).lockManager(locks).pipeline(fixture.pipeline())
                .launcher(new PmExecLauncher(home)).termination(termination).environment(job -> {
            observations.add(new Observation(jobs.records(),
                    locks.anyOpenHolds(fixture.store.lockKey(PROJECT))));
            return environment.of(job);
        }).probeEnvironment(candidate -> ENVIRONMENT).parser(parser).primitives(primitives).clock(clock).build();
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
                Optional.empty());
    }

    private JobId started(JobRunner.StartResult result) {
        return assertInstanceOf(JobRunner.StartResult.Started.class, result, "the job should be started").jobId();
    }

    private JobRecord settled(JobId jobId) {
        return runner.awaitSettled(jobId, SETTLE_TIMEOUT)
                .orElseThrow(() -> new AssertionError("job %s did not settle".formatted(jobId)));
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
                    () -> assertEquals(jobId, before.getFirst().jobId()),
                    () -> assertEquals(Status.RUNNING, before.getFirst().status()),
                    () -> assertNull(before.getFirst().pid(), "no PID before the launch"),
                    () -> assertFalse(before.getFirst().isSpawnRecorded()));
        }

        @Test
        @DisplayName("a launcher that reports is answered only once the record carries the PID and the process start")
        void runningOnlyAfterPostSpawn() {
            var jobId = started(runner.start(git(30, "sleep", "30")));

            var running = runner.recordOf(jobId).orElseThrow();

            assertAll(
                    () -> assertEquals(Status.RUNNING, running.status()),
                    () -> assertTrue(running.isSpawnRecorded(), "the post-spawn record is written"),
                    () -> assertNotNull(running.processStartedAt()),
                    () -> assertNotNull(running.spawnedAt()),
                    () -> assertTrue(runner.runningJobs().contains(jobId)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.JOB_STARTED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' of kind 'git' started (pid %s, budget 30 seconds)".formatted(jobId,
                                    running.pid())));
        }

        @Test
        @DisplayName("a job that exits 0 with its success marker settles succeeded, its child wall time within its own")
        void childWallWithinWallTime() {
            var jobId = started(runner.start(git(30, "ok")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status()),
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                    () -> assertEquals(7, settled.outcomeRow()),
                    () -> assertEquals(0L, settled.exitCode()),
                    () -> assertNotNull(settled.childWallMs(), "an observed exit records the child's wall time"),
                    () -> assertTrue(settled.childWallMs() <= settled.jobWallTime().orElseThrow().toMillis(),
                            "child %s ms, job %s".formatted(settled.childWallMs(), settled.jobWallTime())),
                    () -> assertEquals(Optional.of(settled), jobs.read(jobId)),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "Job '%s' settled with fate 'succeeded' (outcome row 7)".formatted(jobId)));
        }

        @Test
        @DisplayName("a job that exits non-zero without a diagnostic settles failed by row 10")
        void failedExit() {
            var jobId = started(runner.start(git(30, "fail")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.FAILED, settled.fate()),
                    () -> assertEquals(10, settled.outcomeRow()),
                    () -> assertEquals(3L, settled.exitCode()),
                    () -> assertNull(settled.signal()));
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
                    () -> assertEquals(Fate.CANCELLED, settled.fate()),
                    () -> assertEquals(2, settled.outcomeRow()),
                    () -> assertFalse(runner.runningJobs().contains(jobId)),
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL), "a settled job is not"));
        }

        @Test
        @DisplayName("a job over its budget ends timed_out")
        void budgetExpiry() {
            var jobId = started(runner.start(git(1, "sleep", "60")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.TIMED_OUT, settled.fate()),
                    () -> assertEquals(3, settled.outcomeRow()),
                    () -> assertEquals(1, settled.budgetSeconds()));
        }

        @Test
        @DisplayName("the drain ends the job killed")
        void drain() {
            var jobId = started(runner.start(git(60, "sleep", "60")));

            runner.cancel(jobId, CancelCause.DRAIN);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.KILLED, settled.fate()),
                    () -> assertEquals(4, settled.outcomeRow()));
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
                    () -> assertEquals(Fate.CANCELLED, settled.fate()),
                    () -> assertEquals("SIGKILL", settled.signal()),
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
                    () -> assertEquals(Fate.CANCELLED, settled.fate()),
                    () -> assertEquals("SIGTERM", settled.signal()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_ESCALATED.resolveIdentifierString()));
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
                    request, Optional.empty()));

            var refused = assertInstanceOf(JobRunner.StartResult.Refused.class, result);
            var records = jobs.records();
            assertAll(
                    () -> assertEquals("path_traversal_rejected", refused.reason()),
                    () -> assertTrue(observations.isEmpty(), "nothing was launched"),
                    () -> assertEquals(1, records.size()),
                    () -> assertEquals(Status.REFUSED, records.getFirst().status()),
                    () -> assertEquals(Fate.REFUSED, records.getFirst().fate()),
                    () -> assertEquals(1, records.getFirst().outcomeRow()),
                    () -> assertEquals("path_traversal_rejected", records.getFirst().refusalReason()),
                    () -> assertNull(records.getFirst().childWallMs()),
                    () -> assertNull(records.getFirst().pid()),
                    () -> assertFalse(Files.exists(jobs.logPath(records.getFirst().jobId())), "no job log"),
                    () -> assertEquals(Optional.of(records.getFirst()), runner.recordOf(records.getFirst().jobId())),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            "settled with fate 'refused' (outcome row 1)"));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("the enrolment lock of the verification is released before the launch")
        void enrolmentLockReleased() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);

            settled(started(runner.start(git(30, "ok"))));

            var enrolmentLock = fixture.store.lockKey(PROJECT);
            assertAll(
                    () -> assertTrue(locks.acquired.contains(enrolmentLock), "the verification takes the lock"),
                    () -> assertEquals(1, observations.size()),
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
                    () -> assertEquals(Fate.FAILED, settled.fate()),
                    () -> assertEquals(10, settled.outcomeRow()),
                    () -> assertEquals(127L, settled.exitCode()),
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
                    () -> assertEquals(Status.ENDED, settled.status()),
                    () -> assertEquals(Fate.FAILED, settled.fate()),
                    () -> assertEquals(10, settled.outcomeRow()),
                    () -> assertNull(settled.exitCode(), "no exit was observed"),
                    () -> assertNull(settled.pid()),
                    () -> assertNull(settled.spawnedAt()),
                    () -> assertFalse(runner.runningJobs().contains(jobId)),
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
                        () -> assertFalse(runner.runningJobs().contains(jobId)),
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
            var origin = Instant.parse("2026-10-18T10:00:00Z");
            // every reading of the clock is one second later, so the bound passes after a few polls, not after 10 s
            InstantSource clock = () -> origin.plusSeconds(ticks.getAndIncrement());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), job -> ENVIRONMENT,
                    (kind, output) -> ParseResult.NOTHING, request -> Optional.empty(), clock);

            var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> runner.start(git(3600, "sleep", "3600")));
            var jobId = started(result);
            var waiting = runner.recordOf(jobId).orElseThrow();
            var runningAfterStart = runner.runningJobs().contains(jobId);
            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Status.RUNNING, waiting.status(), "the job was handed to its waiting thread"),
                    () -> assertNull(waiting.pid(), "no post-spawn record without a launch report"),
                    () -> assertTrue(runningAfterStart, "the job runs on after the wait"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate()));
        }

        @Test
        @DisplayName("a cancel during the wait for the launch report ends the wait at once")
        void cancelEndsTheWait() throws Exception {
            var launching = new CountDownLatch(1);
            var launched = new AtomicReference<JobId>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(), job -> {
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
                    () -> assertEquals(launched.get(), jobId),
                    () -> assertEquals(Fate.CANCELLED, settled.fate()),
                    () -> assertEquals(2, settled.outcomeRow()));
        }
    }

    @Nested
    @DisplayName("settles a job whose launch cannot be composed and passes the failure on")
    class LaunchNotComposed {

        @Test
        @DisplayName("an environment that throws settles the job failed by row 10 and leaves no running job")
        void environmentThrows() {
            var failure = new IllegalArgumentException("environment refused");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    job -> {
                        throw failure;
                    });
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request));

            assertComposedFailureSettled(thrown, failure);
        }

        @Test
        @DisplayName("an environment name the operating system refuses settles the job failed by row 10")
        void environmentNameRefused() {
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    job -> Map.of("NAME=VALUE", "x"));
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request));

            assertComposedFailureSettled(thrown, thrown);
        }

        @Test
        @DisplayName("a cancel answered before the launcher cannot be started decides the fate by its cause")
        void cancelBeforeLauncherNotStarted() {
            var cancelled = new AtomicBoolean();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    job -> {
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
            var failure = new IllegalArgumentException("environment refused");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    job -> {
                        cancelled.set(runner.cancel(jobs.records().getFirst().jobId(), CancelCause.ENGINE_CANCEL));
                        throw failure;
                    });
            var request = git(30, "ok");

            var thrown = assertThrows(IllegalArgumentException.class, () -> runner.start(request));

            assertEquals(failure, thrown, "the failure is passed on to the caller");
            assertCancelledWithoutProcess(jobs.records().getFirst().jobId(), cancelled);
        }

        @Test
        @DisplayName("a cancel that arrives while the unlaunched job settles is answered false, the job settles failed")
        void cancelDuringSettlementIsAnsweredFalse() {
            var late = new AtomicReference<Boolean>();
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.empty(),
                    job -> {
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
                            "the cancel did not run, or was answered true although it cannot decide the fate"),
                    () -> assertEquals(jobId, settled.jobId()),
                    () -> assertEquals(Status.ENDED, settled.status()),
                    () -> assertEquals(Fate.FAILED, settled.fate(), "the late cancel did not decide the fate"),
                    () -> assertEquals(10, settled.outcomeRow()),
                    () -> assertNull(settled.exitCode(), "no exit was observed"),
                    () -> assertNull(settled.pid(), "no process was started"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "no job is left running"),
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL), "a settled job is not"));
        }

        private void assertCancelledWithoutProcess(JobId jobId, AtomicBoolean cancelled) {
            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertTrue(cancelled.get(), "the cancel decided the end of the job"),
                    () -> assertEquals(jobId, settled.jobId()),
                    () -> assertEquals(Status.ENDED, settled.status()),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides, not row 10"),
                    () -> assertEquals(2, settled.outcomeRow()),
                    () -> assertNull(settled.pid(), "no process was started"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(jobId, Duration.ZERO)));
        }

        private void assertComposedFailureSettled(IllegalArgumentException thrown, IllegalArgumentException expected) {
            var records = jobs.records();
            assertAll(
                    () -> assertEquals(expected, thrown, "the failure is passed on to the caller"),
                    () -> assertEquals(1, records.size()),
                    () -> assertEquals(Status.ENDED, records.getFirst().status()),
                    () -> assertEquals(Fate.FAILED, records.getFirst().fate()),
                    () -> assertEquals(10, records.getFirst().outcomeRow()),
                    () -> assertNull(records.getFirst().pid()),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(records.getFirst()),
                            runner.awaitSettled(records.getFirst().jobId(), Duration.ZERO)),
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
            return runner(new TerminationProtocol(signals), request -> Optional.empty(), job -> {
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

            assertThrows(UncheckedIOException.class, () -> runner.start(request));

            var records = jobs.records();
            assertEquals(1, records.size(), "one job was recorded");
            var settled = records.getFirst();
            assertAll(
                    () -> assertEquals(Status.ENDED, settled.status(), "the record does not stay running"),
                    () -> assertEquals(Fate.KILLED, settled.fate()),
                    () -> assertEquals(4, settled.outcomeRow()),
                    () -> assertEquals("SIGTERM", settled.signal(), "the runner's signal ended the process"),
                    () -> assertNotNull(settled.pid(), "the process ran, its PID is recorded"),
                    () -> assertEquals(List.of(settled.pid()), signalledGroups.stream().distinct().toList(),
                            "the job's process group was terminated"),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(settled.jobId(), Duration.ZERO)),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(settled.jobId())),
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

            assertThrows(UncheckedIOException.class, () -> runner.start(request));

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertTrue(cancelled.get(), "the cancel decided the end of the job"),
                    () -> assertEquals(Status.ENDED, settled.status()),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cause decides, not row 4"),
                    () -> assertEquals(2, settled.outcomeRow()),
                    () -> assertEquals("SIGTERM", settled.signal()),
                    () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"),
                    () -> assertEquals(Optional.of(settled), runner.awaitSettled(settled.jobId(), Duration.ZERO)));
        }

        @Test
        @DisplayName("a refused signal destroys the process through its handle, so the wait for its end is bounded")
        void signalRefused() {
            runner = recordingSignals(() -> permissions("r-x------"), true);
            var request = git(600, "sleep", "600");

            assertTimeoutPreemptively(SETTLE_TIMEOUT, () -> assertThrows(UncheckedIOException.class,
                    () -> runner.start(request)), "the wait for the process has an end");

            var settled = jobs.records().getFirst();
            assertAll(
                    () -> assertEquals(Fate.KILLED, settled.fate()),
                    () -> assertEquals(4, settled.outcomeRow()),
                    () -> assertEquals("SIGKILL", settled.signal(), "destroyed through its handle"),
                    () -> assertEquals(List.of(settled.pid()), signalledGroups.stream().distinct().toList(),
                            "the group was signalled first"),
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
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
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
            var failure = new IllegalStateException("the parser failed");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), job -> ENVIRONMENT,
                    (kind, output) -> {
                        throw failure;
                    }, request -> Optional.empty());

            var jobId = started(runner.start(git(30, "ok")));

            assertSettledWithoutFinding(jobId, failure);
        }

        @Test
        @DisplayName("a parser that fails with an error: the job is settled the same way")
        void parserFailsWithError() throws Exception {
            var failure = new StackOverflowError("a regular expression recursed too deep");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), job -> ENVIRONMENT,
                    (kind, output) -> {
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
                    () -> assertEquals(8, settled.outcomeRow()),
                    () -> assertEquals(0L, settled.exitCode()),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job left the running jobs"),
                    () -> assertEquals(Optional.of(settled), runner.recordOf(jobId)),
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
                    () -> assertTrue(cancelled),
                    () -> assertEquals(Status.ENDED, settled.status(), "the record does not stay running"),
                    () -> assertEquals(Fate.CANCELLED, settled.fate(), "the cancel's cause decides"),
                    () -> assertEquals(2, settled.outcomeRow()),
                    () -> assertEquals("SIGKILL", settled.signal(), "destroyed through its handle"),
                    () -> assertFalse(alive(settled.pid()), "the process is not left running"),
                    () -> assertFalse(runner.runningJobs().contains(jobId), "the job left the running jobs"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.JOB_TERMINATION_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Supervision of job '%s' failed".formatted(jobId), IllegalStateException.class));
        }

        @Test
        @DisplayName("matched control: a job whose parser and termination work is settled without a failure")
        void nothingFails() {
            var jobId = started(runner.start(git(30, "ok")));

            var settled = settled(jobId);

            assertAll(
                    () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
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
            var result = runner.start(new JobStarter.StartRequest(Optional.empty(), "verify",
                    Generators.letterStrings(4, 12).next()));

            var refused = assertInstanceOf(JobStarter.StartResult.Refused.class, result);
            assertAll(
                    () -> assertEquals(SubprocessJobRunner.PRIMITIVE_WITHOUT_JOB, refused.reason()),
                    () -> assertTrue(observations.isEmpty()),
                    () -> assertTrue(jobs.records().isEmpty()));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("matched control: a primitive with a job spec is started with an id of the port's grammar")
        void knownPrimitive() throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var job = git(30, "ok");
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.of(job));

            var result = runner.start(new JobStarter.StartRequest(Optional.empty(), "verify", "git.status"));

            var started = assertInstanceOf(JobStarter.StartResult.Started.class, result);
            assertAll(
                    () -> assertEquals(JobStarter.JobKind.GIT, started.kind()),
                    () -> assertEquals(Fate.SUCCEEDED, settled(new JobId(started.jobId())).fate()));
        }

        @Test
        @DisplayName("the refusal of a job's verification is the refusal of the port")
        void refusedPrimitive() {
            var job = new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, 30, FLOOR,
                    fixture.gitStatus(fixture.beside.toString()), Optional.<String>empty());
            runner = runner(new TerminationProtocol(ProcessGroupSignals.system()), request -> Optional.of(job));

            var result = runner.start(new JobStarter.StartRequest(Optional.empty(), "verify", "git.status"));

            assertEquals(new JobStarter.StartResult.Refused("path_traversal_rejected"), result);
        }
    }

    @Nested
    @DisplayName("refuses what it does not run")
    class NotRun {

        @Test
        @DisplayName("a worker is not started by the runner")
        void worker() {
            var request = new JobRunner.StartRequest<>(JobId.Kind.LLM, new Scope(Scope.Type.PROJECT, null),
                    BUDGET_KEY, 30, FLOOR, fixture.gitStatus(fixture.root.toString()), Optional.<String>empty());

            assertThrows(IllegalArgumentException.class, () -> runner.start(request));
        }

        @Test
        @DisplayName("an unknown job is neither cancelled nor read")
        void unknownJob() {
            var jobId = JobId.random(JobId.Kind.GIT);

            assertAll(
                    () -> assertFalse(runner.cancel(jobId, CancelCause.ENGINE_CANCEL)),
                    () -> assertEquals(Optional.empty(), runner.recordOf(jobId)),
                    () -> assertEquals(Optional.empty(), runner.awaitSettled(jobId, Duration.ZERO)));
        }
    }

    @Nested
    @DisplayName("derives the record's values from what it observed")
    class Values {

        @ParameterizedTest(name = "exit value {0}")
        @CsvSource({"0, 0", "3, 3", "128, 128", "200, 200"})
        @DisplayName("an exit value outside 129 to 192 is an exit code")
        void exitCode(int exitValue, int code) {
            assertAll(
                    () -> assertEquals(new Exited(code),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX)),
                    () -> assertEquals(new Exited(code),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS)));
        }

        @ParameterizedTest(name = "exit value {0} is {1}")
        @CsvSource({"129, SIGHUP", "134, SIGABRT", "137, SIGKILL", "139, SIGSEGV", "143, SIGTERM", "168, SIG40"})
        @DisplayName("an exit value of 128 plus a signal number that both systems share is that signal")
        void signal(int exitValue, String signal) {
            assertAll(
                    () -> assertEquals(new Signalled(signal),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX)),
                    () -> assertEquals(new Signalled(signal),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS)));
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
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.LINUX)),
                    () -> assertEquals(new Signalled(macos),
                            SubprocessJobRunner.waitStatus(exitValue, SubprocessJobRunner.SignalTable.MACOS)));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"Linux, LINUX", "Mac OS X, MACOS", "macOS, MACOS", "'', LINUX"})
        @DisplayName("the table is chosen by the name of the operating system")
        void signalTableOfOs(String osName, SubprocessJobRunner.SignalTable table) {
            assertEquals(table, SubprocessJobRunner.SignalTable.of(osName));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(CancelCause.class)
        @DisplayName("every cancel cause is the termination of its name and decides the fate of its row")
        void termination(CancelCause cause) {
            var termination = SubprocessJobRunner.termination(cause);
            var outcome = OutcomeDerivation.derive(new JobEvidence.ProcessEnded(JobId.Kind.GIT,
                    Optional.of(termination), new Exited(0), JobEvidence.LogState.READABLE, SUCCESS, Set.of(), true));

            assertAll(
                    () -> assertEquals(cause.fate(), outcome.fate()),
                    () -> assertEquals(cause.outcomeRow(), outcome.row()));
        }

        @Test
        @DisplayName("a pause of the plan that holds now is the termination PAUSE_NOW")
        void pauseNow() {
            assertEquals(Termination.PAUSE_NOW, SubprocessJobRunner.termination(CancelCause.PLAN_PAUSED_NOW));
        }

        @Test
        @DisplayName("the child's wall time runs from its reported start and never exceeds the job's")
        void childWall() {
            var spawnedAt = Instant.parse("2026-10-17T10:00:00Z");
            var endedAt = spawnedAt.plusMillis(1500);

            assertAll(
                    () -> assertNull(SubprocessJobRunner.childWallMs(null, spawnedAt, endedAt), "no reported start"),
                    () -> assertEquals(1400L, SubprocessJobRunner.childWallMs(spawnedAt.plusMillis(100), spawnedAt,
                            endedAt)),
                    () -> assertEquals(1500L, SubprocessJobRunner.childWallMs(spawnedAt.minusSeconds(1), spawnedAt,
                            endedAt), "a start reported before the spawn is bounded by the job's wall time"),
                    () -> assertEquals(0L, SubprocessJobRunner.childWallMs(endedAt.plusMillis(5), spawnedAt,
                            endedAt)));
        }

        @Test
        @DisplayName("the argument digest separates the elements of the vector")
        void argvDigest() {
            var element = Generators.letterStrings(2, 8).next();

            var joined = SubprocessJobRunner.argvDigest(List.of(element + element));
            var split = SubprocessJobRunner.argvDigest(List.of(element, element));

            assertAll(
                    () -> assertTrue(joined.matches("[0-9a-f]{64}"), joined),
                    () -> assertNotEquals(joined, split),
                    () -> assertEquals(split, SubprocessJobRunner.argvDigest(new ArrayList<>(List.of(element,
                            element)))));
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
                    () -> assertEquals(new JobBudget(BUDGET_KEY, seconds, FLOOR), budget),
                    () -> assertEquals(Duration.ofSeconds(seconds), budget.duration()));
        }

        @Test
        @DisplayName("refuses a blank key, a budget below one second and missing parts")
        void refusals() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobBudget(" ", 1, FLOOR)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobBudget(BUDGET_KEY, 0, FLOOR)),
                    () -> assertThrows(NullPointerException.class, () -> new JobBudget(null, 1, FLOOR)),
                    () -> assertThrows(NullPointerException.class, () -> new JobBudget(BUDGET_KEY, 1, null)));
        }
    }

    /** A lock manager that records the keys acquired and knows which transactions are open. */
    private static final class RecordingLocks implements LockManager {

        private final LockManager delegate;
        private final Set<LockTransaction> open = ConcurrentHashMap.newKeySet();
        private final List<LockKey> acquired = new CopyOnWriteArrayList<>();
        private final AtomicReference<Runnable> beforeNext = new AtomicReference<>();

        RecordingLocks(LockManager delegate) {
            this.delegate = delegate;
        }

        /** Runs an action once, right before the next transaction is opened. */
        void beforeNextTransaction(Runnable action) {
            beforeNext.set(action);
        }

        @Override
        public LockTransaction openTransaction() {
            var action = beforeNext.getAndSet(null);
            if (action != null) {
                action.run();
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
