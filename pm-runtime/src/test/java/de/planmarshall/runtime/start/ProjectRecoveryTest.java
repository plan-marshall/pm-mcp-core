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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.TerminationProtocol;
import de.planmarshall.runtime.start.ProjectRecovery.ProjectOutcome;
import de.planmarshall.runtime.start.ProjectRecovery.Result;
import de.planmarshall.runtime.start.ProjectRecovery.StepResult;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;

/**
 * The per-project recovery (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Per-Project Recovery)
 * with recording steps on an executor of the test.
 */
@EnableTestLogger
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("Per-project recovery")
class ProjectRecoveryTest {

    private static final Duration BOUND = Duration.ofSeconds(10);
    private static final Executor INLINE = Runnable::run;
    private static final String TASK = "task";

    private final List<String> runs = new CopyOnWriteArrayList<>();
    private final CountDownLatch gate = new CountDownLatch(1);
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void releaseAndStop() {
        gate.countDown();
        threads.close();
    }

    /** A step that records each run as {@code "NAME project"} and then does what its body says. */
    private record RecordingStep(Name name, List<String> runs, Function<String, Outcome> body) implements RecoveryStep {

        @Override
        public Outcome run(String projectId) {
            runs.add(name + " " + projectId);
            return body.apply(projectId);
        }
    }

    /** One recording step per name, in the order of the specification; the bodies given replace the done one. */
    private List<RecoveryStep> steps(Map<Name, Function<String, Outcome>> bodies) {
        var steps = new ArrayList<RecoveryStep>();
        for (var name : Name.values()) {
            steps.add(new RecordingStep(name, runs, bodies.getOrDefault(name, _ -> new Outcome.Done("done"))));
        }
        return steps;
    }

    private List<String> allRuns(String projectId) {
        return Arrays.stream(Name.values()).map(name -> name + " " + projectId).toList();
    }

    private Outcome blockOnGate() {
        try {
            assertTrue(gate.await(BOUND.toMillis(), TimeUnit.MILLISECONDS), "gate not opened");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
        return new Outcome.Done("released");
    }

    private static EnrolledProjects enrolled(String... projectIds) {
        return () -> List.of(projectIds);
    }

    private static ProjectOutcome recovered(Result result) {
        return assertInstanceOf(Result.Recovered.class, result).outcome();
    }

    @Test
    @DisplayName("the lazy trigger and the background pass race on one project and the steps run exactly once")
    void shouldRecoverOnceWhenTriggersRace() throws Exception {
        var recovery = new ProjectRecovery(enrolled("p1"),
                steps(Map.of(Name.ENROLMENT_INTEGRITY, _ -> blockOnGate())), threads, BOUND);
        var start = new CountDownLatch(1);
        var lazyCalls = new ArrayList<CompletableFuture<Result>>();
        for (var i = 0; i < 8; i++) {
            lazyCalls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
                return recovery.awaitRecovered("p1");
            }, threads));
        }

        start.countDown();
        recovery.startBackgroundPass();
        gate.countDown();

        var outcomes = new ArrayList<ProjectOutcome>();
        for (var call : lazyCalls) {
            outcomes.add(recovered(call.get(BOUND.toMillis(), TimeUnit.MILLISECONDS)));
        }
        assertAll(
                () -> assertEquals(allRuns("p1"), runs),
                () -> assertEquals(1, outcomes.stream().distinct().count(), outcomes::toString),
                () -> assertEquals(Name.values().length, outcomes.getFirst().steps().size()));
    }

    @Test
    @DisplayName("the steps run in the order of the specification, whatever order they are given in")
    void shouldRunStepsInSpecificationOrder() {
        var reversed = new ArrayList<>(steps(Map.of()));
        Collections.reverse(reversed);
        var recovery = new ProjectRecovery(enrolled("p1"), reversed, INLINE);

        var outcome = recovered(recovery.awaitRecovered("p1"));

        assertAll(
                () -> assertEquals(allRuns("p1"), runs),
                () -> assertEquals(Arrays.asList(Name.values()),
                        outcome.steps().stream().map(StepResult::name).toList()),
                () -> assertFalse(outcome.failed()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Recovery of project 'p1' finished: 11 steps done"));
    }

    @Test
    @DisplayName("the names are the eleven steps of the specification, in its order")
    void namesInSpecificationOrder() {
        assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT, Name.JOBS_AND_WORKERS, Name.LEASES,
                        Name.JOURNAL_ROLL_FORWARD, Name.CROSS_SCOPE_RECONCILIATION, Name.WORKTREE_PRESENCE,
                        Name.ENGINE_DEADLINES, Name.RESUME_DRIVE, Name.PENDING_RECORD_SWEEP, Name.BASELINE_WORKTREE_SWEEP),
                Arrays.asList(Name.values()));
    }

    /**
     * A caller that passes the step list written before the enrolment store, without the two enrolment steps, keeps
     * working: each missing enrolment step is a seam that does nothing.
     */
    @Nested
    @DisplayName("a step list without the enrolment steps")
    class WithoutEnrolmentSteps {

        @Test
        @DisplayName("without the owed-audit step it constructs and runs eleven steps, the owed-audit one a seam")
        void withoutOwedAuditStep() {
            var steps = new ArrayList<>(steps(Map.of()));
            steps.removeIf(step -> step.name() == Name.OWED_ENROLMENT_AUDIT);
            var recovery = new ProjectRecovery(enrolled("p1"), steps, INLINE);

            var outcome = recovered(recovery.awaitRecovered("p1"));

            assertAll(
                    () -> assertFalse(outcome.failed()),
                    () -> assertEquals(Arrays.asList(Name.values()),
                            outcome.steps().stream().map(StepResult::name).toList()),
                    () -> assertEquals(new StepResult(Name.OWED_ENROLMENT_AUDIT,
                            new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED)), outcome.steps().get(1)));
        }

        @Test
        @DisplayName("without both enrolment steps it constructs and runs, both seams present")
        void withoutBothEnrolmentSteps() {
            var steps = new ArrayList<>(steps(Map.of()));
            steps.removeIf(step -> step.name() == Name.OWED_ENROLMENT_AUDIT || step.name() == Name.ENROLMENT_INTEGRITY);
            var recovery = new ProjectRecovery(enrolled("p1"), steps, INLINE, () -> runs.add(TASK));

            recovery.startBackgroundPass();
            var outcome = recovered(recovery.awaitRecovered("p1"));

            assertAll(
                    () -> assertEquals(Name.values().length, outcome.steps().size()),
                    () -> assertEquals(new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED),
                            outcome.steps().getFirst().outcome()),
                    () -> assertEquals(TASK, runs.getLast()));
        }
    }

    @Nested
    @DisplayName("the background task")
    class BackgroundTask {

        @Test
        @DisplayName("runs once on the executor, after the enrolled projects were submitted")
        void runsOnceAfterTheProjects() {
            var submitted = new ArrayList<Runnable>();
            var task = (Runnable) () -> runs.add(TASK);
            var recovery = new ProjectRecovery(enrolled("p1", "p2"), steps(Map.of()), submitted::add, task);

            recovery.startBackgroundPass();

            assertAll(
                    () -> assertEquals(3, submitted.size()),
                    () -> assertEquals(task, submitted.getLast()),
                    () -> assertTrue(runs.isEmpty(), "nothing runs before the executor runs it"));
            submitted.forEach(Runnable::run);
            assertEquals(List.of(TASK), runs.stream().filter(TASK::equals).toList());
        }

        @Test
        @DisplayName("never runs from the lazy trigger")
        void notFromLazyTrigger() {
            var taskRuns = new ArrayList<String>();
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of()), INLINE, () -> taskRuns.add(TASK));

            recovered(recovery.awaitRecovered("p1"));
            var afterLazy = List.copyOf(taskRuns);
            recovery.startBackgroundPass();

            assertAll(
                    () -> assertEquals(List.of(), afterLazy),
                    () -> assertEquals(List.of(TASK), taskRuns));
        }

        @Test
        @DisplayName("the three-argument constructor submits no task")
        void noTaskByDefault() {
            var submitted = new ArrayList<Runnable>();
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of()), submitted::add);

            recovery.startBackgroundPass();

            assertEquals(1, submitted.size());
        }

        @Test
        @DisplayName("an executor that rejects the task propagates the rejection to the caller")
        void rejectedTask() {
            Executor rejecting = _ -> {
                throw new RejectedExecutionException("shut down");
            };
            var recovery = new ProjectRecovery(EnrolledProjects.NONE, steps(Map.of()), rejecting, () -> {
            });

            assertThrows(RejectedExecutionException.class, recovery::startBackgroundPass);
        }
    }

    @Nested
    @DisplayName("bounded wait of the lazy trigger")
    class BoundedWait {

        @Test
        @DisplayName("the project recovery wait is 10 seconds")
        void shouldWaitTenSeconds() {
            assertEquals(ProjectRecovery.RECOVERY_WAIT, Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("a step blocked past the wait yields Recovering; a repeated call after the step finished is served")
        void shouldAnswerRecoveringThenServe() throws Exception {
            var lastStepDone = new CountDownLatch(1);
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of(
                    Name.JOBS_AND_WORKERS, _ -> blockOnGate(),
                    Name.BASELINE_WORKTREE_SWEEP, _ -> {
                        lastStepDone.countDown();
                        return new Outcome.Done("swept");
                    })), threads, Duration.ofMillis(200));

            var first = recovery.awaitRecovered("p1");
            gate.countDown();
            assertTrue(lastStepDone.await(BOUND.toMillis(), TimeUnit.MILLISECONDS), "recovery did not finish");
            var repeated = recovery.awaitRecovered("p1");

            assertAll(
                    () -> assertInstanceOf(Result.Recovering.class, first),
                    () -> assertEquals(Name.values().length, recovered(repeated).steps().size()),
                    () -> assertEquals(allRuns("p1"), runs, "the repeated call does not recover again"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Recovery of project 'p1' not finished within PT0.2S, answering runtime_recovering"));
        }

        @Test
        @DisplayName("an interrupted wait yields Recovering and keeps the interrupt")
        void shouldAnswerRecoveringWhenInterrupted() {
            var recovery = new ProjectRecovery(enrolled("p1"),
                    steps(Map.of(Name.ENROLMENT_INTEGRITY, _ -> blockOnGate())), threads, BOUND);

            Thread.currentThread().interrupt();
            var result = recovery.awaitRecovered("p1");

            assertAll(
                    () -> assertTrue(Thread.interrupted(), "interrupt kept"),
                    () -> assertInstanceOf(Result.Recovering.class, result));
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("a failed step stops the recovery of its project only")
        void shouldBlockOnlyTheFailedProject() {
            var recovery = new ProjectRecovery(enrolled("p1", "p2"), steps(Map.of(Name.LEASES,
                    projectId -> "p1".equals(projectId) ? new Outcome.Failed("boom") : new Outcome.Done("done"))),
                    INLINE);

            recovery.startBackgroundPass();
            var failed = recovered(recovery.awaitRecovered("p1"));
            var other = recovered(recovery.awaitRecovered("p2"));

            assertAll(
                    () -> assertTrue(failed.failed()),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT,
                                    Name.JOBS_AND_WORKERS, Name.LEASES),
                            failed.steps().stream().map(StepResult::name).toList()),
                    () -> assertEquals(new Outcome.Failed("boom"), failed.steps().getLast().outcome()),
                    () -> assertFalse(other.failed()),
                    () -> assertEquals(Name.values().length, other.steps().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Recovery of project 'p1' failed in step 'LEASES': boom"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project 'p1' finished: failed in LEASES"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project 'p2' finished: 11 steps done"));
        }

        @Test
        @DisplayName("a step that throws is recorded as the failed step")
        void shouldRecordThrowingStepAsFailed() {
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of(Name.JOURNAL_ROLL_FORWARD, _ -> {
                throw new IllegalStateException("broken");
            })), INLINE);

            var outcome = recovered(recovery.awaitRecovered("p1"));

            assertAll(
                    () -> assertTrue(outcome.failed()),
                    () -> assertEquals(new StepResult(Name.JOURNAL_ROLL_FORWARD,
                                    new Outcome.Failed("the step threw java.lang.IllegalStateException: broken")),
                            outcome.steps().getLast()),
                    () -> assertEquals(5, outcome.steps().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Recovery of project 'p1' failed in step 'JOURNAL_ROLL_FORWARD'"));
        }

        @Test
        @DisplayName("two steps with the same name are refused")
        void shouldRefuseDuplicateStepNames() {
            var steps = List.<RecoveryStep>of(RecoveryStep.notImplemented(Name.LEASES),
                    RecoveryStep.notImplemented(Name.LEASES));
            var enrolledProjects = enrolled("p1");

            assertThrows(IllegalArgumentException.class, () -> new ProjectRecovery(enrolledProjects, steps, INLINE));
        }

        @Test
        @DisplayName("a step list without a step of every name is refused, naming the missing ones")
        void shouldRefuseMissingStepNames() {
            var steps = new ArrayList<>(steps(Map.of()));
            steps.removeIf(step -> step.name() == Name.LEASES || step.name() == Name.RESUME_DRIVE);
            var enrolledProjects = enrolled("p1");

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> new ProjectRecovery(enrolledProjects, steps, INLINE));

            assertEquals("no recovery step named [LEASES, RESUME_DRIVE]", refused.getMessage());
        }

        @Test
        @DisplayName("an executor that rejects the recovery yields a failed outcome, never an IllegalStateException")
        void shouldRecordRejectedExecutionAsFailed() {
            Executor rejecting = _ -> {
                throw new RejectedExecutionException("shut down");
            };
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of()), rejecting);

            var first = recovered(recovery.awaitRecovered("p1"));
            var second = recovered(recovery.awaitRecovered("p1"));

            assertAll(
                    () -> assertTrue(first.failed()),
                    () -> assertEquals(new StepResult(Name.ENROLMENT_INTEGRITY, new Outcome.Failed(
                                    "the recovery was not started: java.util.concurrent.RejectedExecutionException: shut down")),
                            first.steps().getLast()),
                    () -> assertEquals(first, second),
                    () -> assertFalse(recovery.isRecovered("p1")),
                    () -> assertEquals(List.of(), runs));
        }

        @Test
        @DisplayName("a failure after the last step is recorded as failed in the last step")
        void shouldRecordFailureAfterLastStep() {
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of()), INLINE);
            var done = new ArrayList<StepResult>();
            for (var name : Name.values()) {
                done.add(new StepResult(name, new Outcome.Done("done")));
            }

            var outcome = recovery.brokenStep("p1", done, new IllegalStateException("log sink gone"));

            assertAll(
                    () -> assertTrue(outcome.failed()),
                    () -> assertEquals(Name.values().length + 1, outcome.steps().size()),
                    () -> assertEquals(new StepResult(Name.BASELINE_WORKTREE_SWEEP, new Outcome.Failed(
                                    "the recovery failed after its last step: java.lang.IllegalStateException: log sink gone")),
                            outcome.steps().getLast()));
        }
    }

    @Nested
    @DisplayName("population and triggers")
    class Triggers {

        @Test
        @DisplayName("a project that is not enrolled is not recovered")
        void shouldNotRecoverUnenrolledProject() {
            var recovery = new ProjectRecovery(enrolled("p1"), steps(Map.of()), INLINE);

            var result = recovery.awaitRecovered("stranger");
            recovery.startBackgroundPass();

            assertAll(
                    () -> assertInstanceOf(Result.NotEnrolled.class, result),
                    () -> assertEquals(allRuns("p1"), runs),
                    () -> assertInstanceOf(Result.NotEnrolled.class,
                            new ProjectRecovery(EnrolledProjects.NONE, steps(Map.of()), INLINE).awaitRecovered("p1")));
        }

        @Test
        @DisplayName("nothing runs at construction; the background pass starts each enrolled project once")
        void shouldRecoverOnlyAfterTrigger() {
            var submitted = new ArrayList<Runnable>();
            var recovery = new ProjectRecovery(enrolled("p1", "p2"), steps(Map.of()), submitted::add);
            var atConstruction = List.copyOf(submitted);

            recovery.startBackgroundPass();
            recovery.startBackgroundPass();
            var afterPasses = submitted.size();
            submitted.forEach(Runnable::run);

            assertAll(
                    () -> assertEquals(List.of(), atConstruction, "no work before the bind"),
                    () -> assertEquals(2, afterPasses, "each enrolled project once"),
                    () -> assertEquals(Name.values().length * 2, runs.size()),
                    () -> assertEquals(Name.values().length, recovered(recovery.awaitRecovered("p2")).steps().size()));
        }

        @Test
        @DisplayName("the lazy trigger alone recovers its project and leaves the others to the background pass")
        void shouldRecoverOnFirstLazyCall() {
            var recovery = new ProjectRecovery(enrolled("p1", "p2"), steps(Map.of()), INLINE);

            var outcome = recovered(recovery.awaitRecovered("p2"));

            assertAll(
                    () -> assertEquals("p2", outcome.projectId()),
                    () -> assertEquals(allRuns("p2"), runs));
        }
    }

    /**
     * The recovery over a real enrolment store, key, lock manager and audit sink: the enrolment integrity and the owed
     * enrolment audit records as steps 1 and 2, the population from the store, the other nine steps as seams.
     */
    @Nested
    @DisplayName("end to end over a real enrolment store")
    class EndToEnd {

        private static final String CLEAN = "clean-project";
        private static final String TAMPERED = "tampered-project";
        private static final String OWING = "owing-project";

        @TempDir
        Path base;

        private EnrolmentFixture fixture;

        @BeforeEach
        void enrolThreeProjects() throws IOException {
            fixture = new EnrolmentFixture(base);
            fixture.enrol(CLEAN, false);
            fixture.enrol(TAMPERED, false);
            fixture.mode = EnrolmentFixture.SinkMode.REFUSING;
            fixture.enrol(OWING, false);
            fixture.mode = EnrolmentFixture.SinkMode.WORKING;
            Files.delete(fixture.store.sidecar(TAMPERED));
        }

        private ProjectRecovery recovery() {
            var seams = Arrays.stream(Name.values()).map(RecoveryStep::notImplemented).toList();
            return ProjectRecovery.withEnrolment(fixture.store, fixture.check, fixture.enrolments, fixture.locks,
                    seams, INLINE);
        }

        @Test
        @DisplayName("a clean project finishes, a tampered one fails at step 1 only, an owing one hands over in step 2")
        void recoversThroughTheEnrolmentSteps() {
            var recovery = recovery();

            recovery.startBackgroundPass();
            var clean = recovered(recovery.awaitRecovered(CLEAN));
            var tampered = recovered(recovery.awaitRecovered(TAMPERED));
            var owing = recovered(recovery.awaitRecovered(OWING));

            assertAll(
                    () -> assertFalse(clean.failed()),
                    () -> assertEquals(Name.values().length, clean.steps().size()),
                    () -> assertTrue(tampered.failed()),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY),
                            tampered.steps().stream().map(StepResult::name).toList()),
                    () -> assertFalse(owing.failed()),
                    () -> assertEquals(new StepResult(Name.OWED_ENROLMENT_AUDIT,
                            new Outcome.Done("1 owed audit records handed over")), owing.steps().get(1)),
                    () -> assertFalse(Files.exists(fixture.store.auditIntent(OWING))),
                    () -> assertInstanceOf(Result.NotEnrolled.class, recovery.awaitRecovered("not-enrolled")),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project '" + CLEAN + "' finished: 11 steps done"));
        }

        /** A signalling primitive the jobs step must not use for a queued job. */
        private static final ProcessGroupSignals NO_SIGNALS = new ProcessGroupSignals() {

            @Override
            public Answer signalGroup(long pgid, Signal signal) {
                throw new AssertionError("no kill() for a queued job");
            }

            @Override
            public Answer signalProcess(long pid, Signal signal) {
                throw new AssertionError("no kill() for a queued job");
            }
        };

        private JobRecoveryStep jobStep() {
            return new JobRecoveryStep(base, fixture.locks, NO_SIGNALS, new TerminationProtocol(NO_SIGNALS),
                    fixture.sink, Instant.EPOCH, JobRecoveryStep.Slots.NONE, Clock.systemUTC());
        }

        /** Leaves a job of the clean project queued, as a runtime that ended before its slot was acquired does. */
        private JobRecord queuedJob() {
            var store = JobRecordStore.ofProject(base, CLEAN);
            var scope = new JobRecord.Scope(JobRecord.Scope.Type.WORKSPACE, null);
            try (var transaction = fixture.locks.openTransaction()) {
                var job = JobRecord.builder().jobId(store.allocate(transaction, JobId.Kind.BUILD, scope))
                        .execution(JobRecord.Execution.PROCESS).scope(scope).budgetKey("maven:verify")
                        .budgetSeconds(600).budgetDerivation(new JobRecord.BudgetDerivation(
                        JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                        .status(JobRecord.Status.QUEUED).confinement(JobRecord.Confinement.UNAVAILABLE)
                        .createdAt(Instant.EPOCH.plusSeconds(1)).clampedValues(List.of()).build();
                store.writeQueued(transaction, job);
                return job;
            }
        }

        private List<RecoveryStep> seamsWithRecordingJobsStep() {
            var steps = new ArrayList<RecoveryStep>();
            for (var name : Name.values()) {
                steps.add(name == Name.JOBS_AND_WORKERS ? new RecordingStep(name, runs, _ -> new Outcome.Done("seam"))
                        : RecoveryStep.notImplemented(name));
            }
            return steps;
        }

        @Test
        @DisplayName("the overload with the jobs step runs it as step 3, after the enrolment steps, before the leases")
        void jobStepRunsThird() {
            var job = queuedJob();
            var recovery = ProjectRecovery.withEnrolment(fixture.store, fixture.check, fixture.enrolments,
                    fixture.locks, jobStep(), seamsWithRecordingJobsStep(), INLINE);

            var clean = recovered(recovery.awaitRecovered(CLEAN));

            var settled = JobRecordStore.ofProject(base, CLEAN).read(job.jobId()).orElseThrow();
            assertAll(
                    () -> assertFalse(clean.failed()),
                    () -> assertEquals(Arrays.asList(Name.values()),
                            clean.steps().stream().map(StepResult::name).toList()),
                    () -> assertEquals(new StepResult(Name.JOBS_AND_WORKERS, new Outcome.Done(
                            "1 jobs settled killed {QUEUED=1}, 0 groups terminated; worker part of 0 workers "
                                    + RecoveryStep.NOT_IMPLEMENTED)), clean.steps().get(2)),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT),
                            clean.steps().subList(0, 2).stream().map(StepResult::name).toList()),
                    () -> assertEquals(Name.LEASES, clean.steps().get(3).name()),
                    () -> assertEquals(List.of(), runs, "the seam of the list was replaced"),
                    () -> assertEquals(JobRecord.Fate.KILLED, settled.fate()),
                    () -> assertEquals(4, settled.outcomeRow()));
        }

        @Test
        @DisplayName("the overload without the jobs step keeps the JOBS_AND_WORKERS step of the list unchanged")
        void existingSignatureKeepsListStep() {
            var job = queuedJob();
            var recovery = ProjectRecovery.withEnrolment(fixture.store, fixture.check, fixture.enrolments,
                    fixture.locks, seamsWithRecordingJobsStep(), INLINE);

            var clean = recovered(recovery.awaitRecovered(CLEAN));

            assertAll(
                    () -> assertFalse(clean.failed()),
                    () -> assertEquals(new StepResult(Name.JOBS_AND_WORKERS, new Outcome.Done("seam")),
                            clean.steps().get(2)),
                    () -> assertEquals(List.of(Name.JOBS_AND_WORKERS + " " + CLEAN), runs),
                    () -> assertEquals(JobRecord.Status.QUEUED,
                            JobRecordStore.ofProject(base, CLEAN).read(job.jobId()).orElseThrow().status()));
        }

        @Test
        @DisplayName("an unenrolled project is not recovered by a new recovery")
        void unenrolledNotRecovered() {
            fixture.enrolments.unenrol(CLEAN);

            var restarted = recovery();

            assertInstanceOf(Result.NotEnrolled.class, restarted.awaitRecovered(CLEAN));
        }
    }

    @Test
    @DisplayName("a seam not implemented yet does nothing and says so")
    void shouldReportNotImplementedSeam() {
        var step = RecoveryStep.notImplemented(Name.ENGINE_DEADLINES);

        assertAll(
                () -> assertEquals(Name.ENGINE_DEADLINES, step.name()),
                () -> assertEquals(new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED), step.run("p1")),
                () -> assertFalse(new ProjectOutcome("p1", List.of()).failed()));
    }
}
