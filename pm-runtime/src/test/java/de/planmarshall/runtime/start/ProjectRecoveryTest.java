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

import java.time.Duration;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.runtime.start.ProjectRecovery.ProjectOutcome;
import de.planmarshall.runtime.start.ProjectRecovery.Result;
import de.planmarshall.runtime.start.ProjectRecovery.StepResult;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The per-project recovery (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Per-Project Recovery)
 * with recording steps on an executor of the test.
 */
@EnableTestLogger
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("Per-project recovery")
class ProjectRecoveryTest {

    private static final Duration BOUND = Duration.ofSeconds(10);
    private static final Executor INLINE = Runnable::run;

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
                        "Recovery of project 'p1' finished: 10 steps done"));
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
                    () -> assertEquals(List.of(Name.ENROLMENT_INTEGRITY, Name.JOBS_AND_WORKERS, Name.LEASES),
                            failed.steps().stream().map(StepResult::name).toList()),
                    () -> assertEquals(new Outcome.Failed("boom"), failed.steps().getLast().outcome()),
                    () -> assertFalse(other.failed()),
                    () -> assertEquals(Name.values().length, other.steps().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Recovery of project 'p1' failed in step 'LEASES': boom"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project 'p1' finished: failed in LEASES"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Recovery of project 'p2' finished: 10 steps done"));
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
                    () -> assertEquals(4, outcome.steps().size()),
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
