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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import java.util.Locale;
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
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.ProjectRecovery.ProjectOutcome;
import de.planmarshall.runtime.start.ProjectRecovery.Result;
import de.planmarshall.runtime.start.ProjectRecovery.StepResult;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;

/**
 * The per-project recovery (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Per-Project Recovery)
 * with recording steps on an executor of the test. The failure reasons and log texts the tests compare are the
 * specified formats of the recovery; the data inside them is generated.
 */
@EnableGeneratorController
@EnableTestLogger
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("Per-project recovery")
class ProjectRecoveryTest {

    /** How long the test waits for a thread of its own; a bound of the test, not test data. */
    private static final Duration BOUND = Duration.ofSeconds(10);
    private static final Executor INLINE = Runnable::run;

    private final List<String> runs = new CopyOnWriteArrayList<>();
    private final CountDownLatch gate = new CountDownLatch(1);
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private final String p1 = "first-" + name();
    private final String p2 = "second-" + name();
    private final String task = "task-" + name();

    @AfterEach
    void releaseAndStop() {
        gate.countDown();
        threads.close();
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String message() {
        return Generators.letterStrings(3, 30).next();
    }

    private static Outcome.Done done() {
        return new Outcome.Done(message());
    }

    /** @return an EasyMock step that records each run as {@code "NAME project"} and then runs its body */
    private RecoveryStep step(Name name, Function<String, Outcome> body) {
        RecoveryStep mock = createMock(RecoveryStep.class);
        expect(mock.name()).andReturn(name).anyTimes();
        expect(mock.run(anyObject(String.class))).andAnswer(() -> {
            var projectId = (String) getCurrentArguments()[0];
            runs.add(name + " " + projectId);
            return body.apply(projectId);
        }).anyTimes();
        replay(mock);
        return mock;
    }

    /** One step with a recording body per name, in the order of the specification; the bodies given replace the done one. */
    private List<RecoveryStep> steps(Map<Name, Function<String, Outcome>> bodies) {
        var steps = new ArrayList<RecoveryStep>();
        var report = done();
        for (var name : Name.values()) {
            steps.add(step(name, bodies.getOrDefault(name, _ -> report)));
        }
        return steps;
    }

    private List<String> allRuns(String projectId) {
        return Arrays.stream(Name.values()).map(name -> name + " " + projectId).toList();
    }

    private Outcome blockOnGate() {
        try {
            assertTrue(gate.await(BOUND.toMillis(), TimeUnit.MILLISECONDS),
                    "the gate must open within the bound");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
        return done();
    }

    private static EnrolledProjects enrolled(String... projectIds) {
        return () -> List.of(projectIds);
    }

    private static ProjectOutcome recovered(Result result) {
        return assertInstanceOf(Result.Recovered.class, result, "the project must be recovered").outcome();
    }

    /** @return the specified finish text of a recovery whose every step was done */
    private static String finishedDone(String projectId) {
        return "Recovery of project '" + projectId + "' finished: " + Name.values().length + " steps done";
    }

    @Test
    @DisplayName("the lazy trigger and the background pass race on one project and the steps run exactly once")
    void shouldRecoverOnceWhenTriggersRace() throws Exception {
        var recovery = new ProjectRecovery(enrolled(p1),
                steps(Map.of(Name.ENROLMENT_INTEGRITY, _ -> blockOnGate())), threads, BOUND);
        var start = new CountDownLatch(1);
        var lazyCalls = new ArrayList<CompletableFuture<Result>>();
        var callers = Generators.integers(2, 12).next();
        for (var i = 0; i < callers; i++) {
            lazyCalls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
                return recovery.awaitRecovered(p1);
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
                () -> assertEquals(allRuns(p1), runs, "every step must run exactly once"),
                () -> assertEquals(1, outcomes.stream().distinct().count(), "every caller must get the same outcome"),
                () -> assertEquals(Name.values().length, outcomes.getFirst().steps().size(),
                        "the outcome must hold every step"));
    }

    @Test
    @DisplayName("the steps run in the order of the specification, whatever order they are given in")
    void shouldRunStepsInSpecificationOrder() {
        var reversed = new ArrayList<>(steps(Map.of()));
        Collections.reverse(reversed);
        var recovery = new ProjectRecovery(enrolled(p1), reversed, INLINE);

        var outcome = recovered(recovery.awaitRecovered(p1));

        assertAll(
                () -> assertEquals(allRuns(p1), runs, "the steps must run in the specified order"),
                () -> assertEquals(Arrays.asList(Name.values()),
                        outcome.steps().stream().map(StepResult::name).toList(),
                        "the outcome must list the steps in order"),
                () -> assertFalse(outcome.failed(), "the recovery must not fail"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.PROJECT_RECOVERY_FINISHED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, finishedDone(p1)));
    }

    @Test
    @DisplayName("the names are the eleven steps of the specification, in its order")
    void namesInSpecificationOrder() {
        assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT, Name.JOBS_AND_WORKERS, Name.LEASES,
                        Name.JOURNAL_ROLL_FORWARD, Name.CROSS_SCOPE_RECONCILIATION, Name.WORKTREE_PRESENCE,
                        Name.ENGINE_DEADLINES, Name.RESUME_DRIVE, Name.PENDING_RECORD_SWEEP, Name.BASELINE_WORKTREE_SWEEP),
                Arrays.asList(Name.values()), "the names must be the eleven specified steps");
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
            var recovery = new ProjectRecovery(enrolled(p1), steps, INLINE);

            var outcome = recovered(recovery.awaitRecovered(p1));

            assertAll(
                    () -> assertFalse(outcome.failed(), "the recovery must not fail"),
                    () -> assertEquals(Arrays.asList(Name.values()),
                            outcome.steps().stream().map(StepResult::name).toList(),
                            "the outcome must list every step in order"),
                    () -> assertEquals(new StepResult(Name.OWED_ENROLMENT_AUDIT,
                                    new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED)), outcome.steps().get(1),
                            "the missing step must be a seam"));
        }

        @Test
        @DisplayName("without both enrolment steps it constructs and runs, both seams present")
        void withoutBothEnrolmentSteps() {
            var steps = new ArrayList<>(steps(Map.of()));
            steps.removeIf(step -> step.name() == Name.OWED_ENROLMENT_AUDIT || step.name() == Name.ENROLMENT_INTEGRITY);
            var recovery = new ProjectRecovery(enrolled(p1), steps, INLINE, () -> runs.add(task));

            recovery.startBackgroundPass();
            var outcome = recovered(recovery.awaitRecovered(p1));

            assertAll(
                    () -> assertEquals(Name.values().length, outcome.steps().size(),
                            "the outcome must hold every step"),
                    () -> assertEquals(new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED),
                            outcome.steps().getFirst().outcome(), "the first step must be a seam"),
                    () -> assertEquals(task, runs.getLast(), "the background task must run last"));
        }
    }

    @Nested
    @DisplayName("the background task")
    class BackgroundTask {

        @Test
        @DisplayName("runs once on the executor, after the enrolled projects were submitted")
        void runsOnceAfterTheProjects() {
            var submitted = new ArrayList<Runnable>();
            var background = (Runnable) () -> runs.add(task);
            var recovery = new ProjectRecovery(enrolled(p1, p2), steps(Map.of()), submitted::add, background);

            recovery.startBackgroundPass();

            assertAll(
                    () -> assertEquals(3, submitted.size(), "two projects and the task must be submitted"),
                    () -> assertEquals(background, submitted.getLast(), "the task must be submitted last"),
                    () -> assertTrue(runs.isEmpty(), "nothing runs before the executor runs it"));
            submitted.forEach(Runnable::run);
            assertEquals(List.of(task), runs.stream().filter(task::equals).toList(),
                    "the background task must run once");
        }

        @Test
        @DisplayName("never runs from the lazy trigger")
        void notFromLazyTrigger() {
            var taskRuns = new ArrayList<String>();
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of()), INLINE, () -> taskRuns.add(task));

            recovered(recovery.awaitRecovered(p1));
            var afterLazy = List.copyOf(taskRuns);
            recovery.startBackgroundPass();

            assertAll(
                    () -> assertEquals(List.of(), afterLazy, "the lazy trigger must not run the task"),
                    () -> assertEquals(List.of(task), taskRuns, "the background pass must run the task"));
        }

        @Test
        @DisplayName("the three-argument constructor submits no task")
        void noTaskByDefault() {
            var submitted = new ArrayList<Runnable>();
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of()), submitted::add);

            recovery.startBackgroundPass();

            assertEquals(1, submitted.size(), "only the project must be submitted");
        }

        @Test
        @DisplayName("an executor that rejects the task propagates the rejection to the caller")
        void rejectedTask() {
            var reason = message();
            Executor rejecting = _ -> {
                throw new RejectedExecutionException(reason);
            };
            var recovery = new ProjectRecovery(EnrolledProjects.NONE, steps(Map.of()), rejecting, () -> {
            });

            assertThrows(RejectedExecutionException.class, recovery::startBackgroundPass,
                    "the rejection must reach the caller");
        }
    }

    @Nested
    @DisplayName("bounded wait of the lazy trigger")
    class BoundedWait {

        @Test
        @DisplayName("the project recovery wait is 10 seconds (the specified bound)")
        void shouldWaitTenSeconds() {
            assertEquals(ProjectRecovery.RECOVERY_WAIT, Duration.ofSeconds(10),
                    "the recovery wait must be the specified bound");
        }

        @Test
        @DisplayName("a step blocked past the wait yields Recovering; a repeated call after the step finished is served")
        void shouldAnswerRecoveringThenServe() throws Exception {
            var lastStepDone = new CountDownLatch(1);
            var wait = Duration.ofMillis(Generators.integers(100, 500).next());
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of(
                    Name.JOBS_AND_WORKERS, _ -> blockOnGate(),
                    Name.BASELINE_WORKTREE_SWEEP, _ -> {
                        lastStepDone.countDown();
                        return done();
                    })), threads, wait);

            var first = recovery.awaitRecovered(p1);
            gate.countDown();
            assertTrue(lastStepDone.await(BOUND.toMillis(), TimeUnit.MILLISECONDS), "recovery did not finish");
            var repeated = recovery.awaitRecovered(p1);

            assertAll(
                    () -> assertInstanceOf(Result.Recovering.class, first, "the first call must answer Recovering"),
                    () -> assertEquals(Name.values().length, recovered(repeated).steps().size(),
                            "the repeated call must be served"),
                    () -> assertEquals(allRuns(p1), runs, "the repeated call does not recover again"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.PROJECT_RECOVERY_WAIT_EXPIRED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Recovery of project '" + p1 + "' not finished within " + wait
                                    + ", answering runtime_recovering"));
        }

        @Test
        @DisplayName("an interrupted wait yields Recovering and keeps the interrupt")
        void shouldAnswerRecoveringWhenInterrupted() {
            var recovery = new ProjectRecovery(enrolled(p1),
                    steps(Map.of(Name.ENROLMENT_INTEGRITY, _ -> blockOnGate())), threads, BOUND);

            Thread.currentThread().interrupt();
            var result = recovery.awaitRecovered(p1);

            assertAll(
                    () -> assertTrue(Thread.interrupted(), "the interrupt flag must stay set"),
                    () -> assertInstanceOf(Result.Recovering.class, result,
                            "an interrupted wait must answer Recovering"));
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("a failed step stops the recovery of its project only")
        void shouldBlockOnlyTheFailedProject() {
            var reason = message();
            var report = done();
            var recovery = new ProjectRecovery(enrolled(p1, p2), steps(Map.of(Name.LEASES,
                    projectId -> p1.equals(projectId) ? new Outcome.Failed(reason) : report)), INLINE);

            recovery.startBackgroundPass();
            var failed = recovered(recovery.awaitRecovered(p1));
            var other = recovered(recovery.awaitRecovered(p2));

            assertAll(
                    () -> assertTrue(failed.failed(), "the recovery of the project must fail"),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT,
                                    Name.JOBS_AND_WORKERS, Name.LEASES),
                            failed.steps().stream().map(StepResult::name).toList(),
                            "the recovery must stop at the failed step"),
                    () -> assertEquals(new Outcome.Failed(reason), failed.steps().getLast().outcome(),
                            "the failed step must carry its reason"),
                    () -> assertFalse(other.failed(), "another project must not fail"),
                    () -> assertEquals(Name.values().length, other.steps().size(),
                            "another project must run every step"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.PROJECT_RECOVERY_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Recovery of project '" + p1 + "' failed in step 'LEASES': " + reason),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.PROJECT_RECOVERY_FINISHED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project '" + p1 + "' finished: failed in LEASES"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, finishedDone(p2)));
        }

        @Test
        @DisplayName("a step that throws is recorded as the failed step")
        void shouldRecordThrowingStepAsFailed() {
            var reason = message();
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of(Name.JOURNAL_ROLL_FORWARD, _ -> {
                throw new IllegalStateException(reason);
            })), INLINE);

            var outcome = recovered(recovery.awaitRecovered(p1));

            assertAll(
                    () -> assertTrue(outcome.failed(), "the recovery must fail"),
                    () -> assertEquals(new StepResult(Name.JOURNAL_ROLL_FORWARD,
                                    new Outcome.Failed("the step threw java.lang.IllegalStateException: " + reason)),
                            outcome.steps().getLast(), "the throwing step must be recorded as failed"),
                    () -> assertEquals(5, outcome.steps().size(), "the recovery must stop at the fifth step"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.PROJECT_RECOVERY_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Recovery of project '" + p1 + "' failed in step 'JOURNAL_ROLL_FORWARD'"));
        }

        @Test
        @DisplayName("two steps with the same name are refused")
        void shouldRefuseDuplicateStepNames() {
            var steps = List.<RecoveryStep>of(RecoveryStep.notImplemented(Name.LEASES),
                    RecoveryStep.notImplemented(Name.LEASES));
            var enrolledProjects = enrolled(p1);

            assertThrows(IllegalArgumentException.class, () -> new ProjectRecovery(enrolledProjects, steps, INLINE),
                    "two steps of one name must be refused");
        }

        @Test
        @DisplayName("a step list without a step of every name is refused, naming the missing ones")
        void shouldRefuseMissingStepNames() {
            var steps = new ArrayList<>(steps(Map.of()));
            steps.removeIf(step -> step.name() == Name.LEASES || step.name() == Name.RESUME_DRIVE);
            var enrolledProjects = enrolled(p1);

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> new ProjectRecovery(enrolledProjects, steps, INLINE), "missing steps must be refused");

            assertEquals("no recovery step named [LEASES, RESUME_DRIVE]", refused.getMessage(),
                    "the refusal must name the missing steps");
        }

        @Test
        @DisplayName("an executor that rejects the recovery yields a failed outcome, never an IllegalStateException")
        void shouldRecordRejectedExecutionAsFailed() {
            var reason = message();
            Executor rejecting = _ -> {
                throw new RejectedExecutionException(reason);
            };
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of()), rejecting);

            var first = recovered(recovery.awaitRecovered(p1));
            var second = recovered(recovery.awaitRecovered(p1));

            assertAll(
                    () -> assertTrue(first.failed(), "a rejected recovery must fail"),
                    () -> assertEquals(new StepResult(Name.ENROLMENT_INTEGRITY, new Outcome.Failed(
                                    "the recovery was not started: java.util.concurrent.RejectedExecutionException: "
                                            + reason)),
                            first.steps().getLast(), "the rejection must be recorded in the first step"),
                    () -> assertEquals(first, second, "a second call must get the same outcome"),
                    () -> assertFalse(recovery.isRecovered(p1), "the project must not count as recovered"),
                    () -> assertEquals(List.of(), runs, "no recovery step must run at all"));
        }

        @Test
        @DisplayName("a failure after the last step is recorded as failed in the last step")
        void shouldRecordFailureAfterLastStep() {
            var reason = message();
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of()), INLINE);
            var done = new ArrayList<StepResult>();
            for (var name : Name.values()) {
                done.add(new StepResult(name, done()));
            }

            var outcome = recovery.brokenStep(p1, done, new IllegalStateException(reason));

            assertAll(
                    () -> assertTrue(outcome.failed(), "the recovery must fail"),
                    () -> assertEquals(Name.values().length + 1, outcome.steps().size(),
                            "the failure must follow every step"),
                    () -> assertEquals(new StepResult(Name.BASELINE_WORKTREE_SWEEP, new Outcome.Failed(
                                    "the recovery failed after its last step: java.lang.IllegalStateException: "
                                            + reason)),
                            outcome.steps().getLast(), "the failure must be recorded in the last step"));
        }
    }

    @Nested
    @DisplayName("population and triggers")
    class Triggers {

        @Test
        @DisplayName("a project that is not enrolled is not recovered")
        void shouldNotRecoverUnenrolledProject() {
            var recovery = new ProjectRecovery(enrolled(p1), steps(Map.of()), INLINE);

            var result = recovery.awaitRecovered("stranger-" + name());
            recovery.startBackgroundPass();

            assertAll(
                    () -> assertInstanceOf(Result.NotEnrolled.class, result,
                            "a project not enrolled must not be recovered"),
                    () -> assertEquals(allRuns(p1), runs, "only the enrolled project must be recovered"),
                    () -> assertInstanceOf(Result.NotEnrolled.class,
                            new ProjectRecovery(EnrolledProjects.NONE, steps(Map.of()), INLINE).awaitRecovered(p1),
                            "no project is enrolled with the seam NONE"));
        }

        @Test
        @DisplayName("nothing runs at construction; the background pass starts each enrolled project once")
        void shouldRecoverOnlyAfterTrigger() {
            var submitted = new ArrayList<Runnable>();
            var recovery = new ProjectRecovery(enrolled(p1, p2), steps(Map.of()), submitted::add);
            var atConstruction = List.copyOf(submitted);

            recovery.startBackgroundPass();
            recovery.startBackgroundPass();
            var afterPasses = submitted.size();
            submitted.forEach(Runnable::run);

            assertAll(
                    () -> assertEquals(List.of(), atConstruction, "no work before the bind"),
                    () -> assertEquals(2, afterPasses, "each enrolled project once"),
                    () -> assertEquals(Name.values().length * 2, runs.size(),
                            "every step must run for both projects"),
                    () -> assertEquals(Name.values().length, recovered(recovery.awaitRecovered(p2)).steps().size(),
                            "the second project must be recovered"));
        }

        @Test
        @DisplayName("the lazy trigger alone recovers its project and leaves the others to the background pass")
        void shouldRecoverOnFirstLazyCall() {
            var recovery = new ProjectRecovery(enrolled(p1, p2), steps(Map.of()), INLINE);

            var outcome = recovered(recovery.awaitRecovered(p2));

            assertAll(
                    () -> assertEquals(p2, outcome.projectId(), "the outcome must name the triggered project"),
                    () -> assertEquals(allRuns(p2), runs, "only the triggered project must be recovered"));
        }
    }

    /**
     * The recovery over a real enrolment store, key, lock manager and audit sink: the enrolment integrity and the owed
     * enrolment audit records as steps 1 and 2, the population from the store, the other nine steps as seams.
     */
    @Nested
    @DisplayName("end to end over a real enrolment store")
    class EndToEnd {

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

        @TempDir
        Path base;

        private final String clean = "clean-" + name();
        private final String tampered = "tampered-" + name();
        private final String owing = "owing-" + name();
        private EnrolmentFixture fixture;

        @BeforeEach
        void enrolThreeProjects() throws IOException {
            fixture = new EnrolmentFixture(base);
            fixture.enrol(clean, false);
            fixture.enrol(tampered, false);
            fixture.mode = EnrolmentFixture.SinkMode.REFUSING;
            fixture.enrol(owing, false);
            fixture.mode = EnrolmentFixture.SinkMode.WORKING;
            Files.delete(fixture.store.sidecar(tampered));
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
            var cleanOutcome = recovered(recovery.awaitRecovered(clean));
            var tamperedOutcome = recovered(recovery.awaitRecovered(tampered));
            var owingOutcome = recovered(recovery.awaitRecovered(owing));

            assertAll(
                    () -> assertFalse(cleanOutcome.failed(), "the clean project must not fail"),
                    () -> assertEquals(Name.values().length, cleanOutcome.steps().size(),
                            "the clean project must run every step"),
                    () -> assertTrue(tamperedOutcome.failed(), "the tampered project must fail"),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY),
                            tamperedOutcome.steps().stream().map(StepResult::name).toList(),
                            "the tampered project must stop at step 1"),
                    () -> assertFalse(owingOutcome.failed(), "the owing project must not fail"),
                    () -> assertEquals(new StepResult(Name.OWED_ENROLMENT_AUDIT,
                                    new Outcome.Done("1 owed audit records handed over")), owingOutcome.steps().get(1),
                            "the owing project must hand over in step 2"),
                    () -> assertFalse(Files.exists(fixture.store.auditIntent(owing)),
                            "the owed intent must be gone"),
                    () -> assertInstanceOf(Result.NotEnrolled.class, recovery.awaitRecovered("absent-" + name()),
                            "a project not enrolled must not be recovered"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, finishedDone(clean)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.PROJECT_RECOVERY_FAILED.resolveIdentifierString()));
        }

        private JobRecoveryStep jobStep() {
            return new JobRecoveryStep(base, fixture.locks,
                    new JobRecoveryStep.Signalling(NO_SIGNALS, new TerminationProtocol(NO_SIGNALS)), fixture.sink,
                    Instant.EPOCH, JobRecoveryStep.Slots.NONE, Clock.systemUTC());
        }

        /** Leaves a job of the clean project queued, as a runtime that ended before its slot was acquired does. */
        private JobRecord queuedJob() {
            var store = JobRecordStore.ofProject(base, clean);
            var scope = new JobRecord.Scope(JobRecord.Scope.Type.WORKSPACE, null);
            try (var transaction = fixture.locks.openTransaction()) {
                var job = JobRecord.builder().jobId(store.allocate(transaction, JobId.Kind.BUILD, scope))
                        .execution(JobRecord.Execution.PROCESS).scope(scope).budgetKey("maven:" + name())
                        .budgetSeconds(Generators.integers(300, 3_600).next())
                        .budgetDerivation(new JobRecord.BudgetDerivation(
                                JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                        .status(JobRecord.Status.QUEUED).confinement(JobRecord.Confinement.UNAVAILABLE)
                        .createdAt(Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000_000).next()))
                        .clampedValues(List.of()).build();
                store.writeQueued(transaction, job);
                return job;
            }
        }

        private List<RecoveryStep> seamsWithRecordingJobsStep(Outcome.Done seam) {
            var steps = new ArrayList<RecoveryStep>();
            for (var name : Name.values()) {
                steps.add(name == Name.JOBS_AND_WORKERS ? step(name, _ -> seam)
                        : RecoveryStep.notImplemented(name));
            }
            return steps;
        }

        @Test
        @DisplayName("the overload with the jobs step runs it as step 3, after the enrolment steps, before the leases")
        void jobStepRunsThird() {
            var job = queuedJob();
            var recovery = ProjectRecovery.withEnrolment(fixture.store, fixture.check, fixture.enrolments,
                    fixture.locks, jobStep(), seamsWithRecordingJobsStep(done()), INLINE);

            var cleanOutcome = recovered(recovery.awaitRecovered(clean));

            var settled = JobRecordStore.ofProject(base, clean).read(job.jobId()).orElseThrow();
            assertAll(
                    () -> assertFalse(cleanOutcome.failed(), "the clean project must not fail"),
                    () -> assertEquals(Arrays.asList(Name.values()),
                            cleanOutcome.steps().stream().map(StepResult::name).toList(),
                            "the outcome must list every step in order"),
                    () -> assertEquals(new StepResult(Name.JOBS_AND_WORKERS, new Outcome.Done(
                                    "1 jobs settled killed {QUEUED=1}, 0 groups terminated; worker part of 0 workers "
                                            + RecoveryStep.NOT_IMPLEMENTED)), cleanOutcome.steps().get(2),
                            "the jobs step must settle the queued job"),
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.OWED_ENROLMENT_AUDIT),
                            cleanOutcome.steps().subList(0, 2).stream().map(StepResult::name).toList(),
                            "the enrolment steps must run first"),
                    () -> assertEquals(Name.LEASES, cleanOutcome.steps().get(3).name(),
                            "the leases step must follow the jobs step"),
                    () -> assertEquals(List.of(), runs, "the seam of the list was replaced"),
                    () -> assertEquals(JobRecord.Fate.KILLED, settled.fate(), "the queued job must be killed"),
                    () -> assertEquals(4, settled.outcomeRow(), "outcome row 4 of the specification"));
        }

        @Test
        @DisplayName("the overload without the jobs step keeps the JOBS_AND_WORKERS step of the list unchanged")
        void existingSignatureKeepsListStep() {
            var job = queuedJob();
            var seam = done();
            var recovery = ProjectRecovery.withEnrolment(fixture.store, fixture.check, fixture.enrolments,
                    fixture.locks, seamsWithRecordingJobsStep(seam), INLINE);

            var cleanOutcome = recovered(recovery.awaitRecovered(clean));

            assertAll(
                    () -> assertFalse(cleanOutcome.failed(), "the clean project must not fail"),
                    () -> assertEquals(new StepResult(Name.JOBS_AND_WORKERS, seam), cleanOutcome.steps().get(2),
                            "the step of the list must run unchanged"),
                    () -> assertEquals(List.of(Name.JOBS_AND_WORKERS + " " + clean), runs,
                            "the step of the list must run once"),
                    () -> assertEquals(JobRecord.Status.QUEUED,
                            JobRecordStore.ofProject(base, clean).read(job.jobId()).orElseThrow().status(),
                            "the queued job must stay queued"));
        }

        @Test
        @DisplayName("an unenrolled project is not recovered by a new recovery")
        void unenrolledNotRecovered() {
            fixture.enrolments.unenrol(clean);

            var restarted = recovery();

            assertInstanceOf(Result.NotEnrolled.class, restarted.awaitRecovered(clean),
                    "an unenrolled project must not be recovered");
        }
    }

    @Test
    @DisplayName("a seam not implemented yet does nothing and says so")
    void shouldReportNotImplementedSeam() {
        var step = RecoveryStep.notImplemented(Name.ENGINE_DEADLINES);

        assertAll(
                () -> assertEquals(Name.ENGINE_DEADLINES, step.name(), "the seam must carry its name"),
                () -> assertEquals(new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED), step.run(p1),
                        "the seam must say it is not implemented"),
                () -> assertFalse(new ProjectOutcome(p1, List.of()).failed(),
                        "an outcome without steps has not failed"));
    }
}
