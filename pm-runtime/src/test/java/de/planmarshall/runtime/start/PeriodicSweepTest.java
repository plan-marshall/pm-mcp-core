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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The periodic sweep over the enrolled projects with recording steps, run directly and on a capturing executor.
 */
@EnableTestLogger
@DisplayName("Periodic sweep")
class PeriodicSweepTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant OLDER_THAN = NOW.minusSeconds(86_400);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final CapturingExecutor executor = new CapturingExecutor();

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    /** A step that records each call as {@code "NAME project olderThan"} and then does what its body says. */
    private record RecordingStep(SweepStep.Name name, List<String> calls,
    BiFunction<String, Instant, Outcome> body) implements SweepStep {

        @Override
        public Outcome run(String projectId, Instant olderThan) {
            calls.add(name + " " + projectId + " " + olderThan);
            return body.apply(projectId, olderThan);
        }
    }

    /** An executor that keeps what was scheduled at a fixed rate, so a test can run it by hand. */
    private static final class CapturingExecutor extends ScheduledThreadPoolExecutor {

        private Runnable command;
        private long initialDelay;
        private long period;
        private TimeUnit unit;
        private ScheduledFuture<?> future;

        CapturingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                TimeUnit unit) {
            this.command = command;
            this.initialDelay = initialDelay;
            this.period = period;
            this.unit = unit;
            future = super.scheduleAtFixedRate(command, initialDelay, period, unit);
            return future;
        }
    }

    private RecordingStep step(SweepStep.Name name) {
        return new RecordingStep(name, calls, (_, _) -> new Outcome.Done("done"));
    }

    private PeriodicSweep sweep(EnrolledProjects enrolled, Predicate<String> recovered,
            SweepStep... steps) {
        return new PeriodicSweep(enrolled, recovered, List.of(steps), CLOCK);
    }

    @Test
    @DisplayName("the interval is 3600 seconds and the pending entry maximum age 86400 seconds")
    void shouldHaveCompiledConstants() {
        assertAll(
                () -> assertEquals(PeriodicSweep.INTERVAL, Duration.ofSeconds(3600)),
                () -> assertEquals(PeriodicSweep.PENDING_ENTRY_MAX_AGE, Duration.ofSeconds(86_400)));
    }

    @Test
    @DisplayName("a run passes now minus the maximum age to every step, for every enrolled project")
    void shouldRunEveryStepForEveryProject() {
        var summary = sweep(() -> List.of("p1", "p2"), _ -> true, step(SweepStep.Name.PENDING_ENTRY_RECORDS),
                step(SweepStep.Name.FENCED_SESSION_GENERATIONS)).runOnce(NOW);

        assertAll(
                () -> assertEquals(List.of(
                                "PENDING_ENTRY_RECORDS p1 " + OLDER_THAN, "FENCED_SESSION_GENERATIONS p1 " + OLDER_THAN,
                                "PENDING_ENTRY_RECORDS p2 " + OLDER_THAN, "FENCED_SESSION_GENERATIONS p2 " + OLDER_THAN),
                        calls),
                () -> assertEquals(new PeriodicSweep.Summary(2, 0, 0), summary),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Periodic sweep finished: 2 projects swept, 0 skipped, 0 failed"));
    }

    @Test
    @DisplayName("a project whose recovery has not finished is skipped")
    void shouldSkipProjectNotRecovered() {
        var summary = sweep(() -> List.of("p1", "p2"), "p1"::equals, step(SweepStep.Name.PENDING_ENTRY_RECORDS))
                .runOnce(NOW);

        assertAll(
                () -> assertEquals(List.of("PENDING_ENTRY_RECORDS p1 " + OLDER_THAN), calls),
                () -> assertEquals(new PeriodicSweep.Summary(1, 1, 0), summary));
    }

    @Test
    @DisplayName("a failing step stops neither the other steps nor the other projects")
    void shouldGoOnAfterFailingStep() {
        var failing = new RecordingStep(SweepStep.Name.PENDING_ENTRY_RECORDS, calls, (projectId, _) -> {
            if ("p1".equals(projectId)) {
                return new Outcome.Failed("store gone");
            }
            if ("p2".equals(projectId)) {
                throw new UncheckedIOException("disk gone", new IOException("disk gone"));
            }
            return new Outcome.Done("done");
        });

        var summary = sweep(() -> List.of("p1", "p2", "p3"), _ -> true, failing,
                step(SweepStep.Name.FENCED_SESSION_GENERATIONS)).runOnce(NOW);

        assertAll(
                () -> assertEquals(6, calls.size(), calls::toString),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 2), summary),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project 'p1' failed in step 'PENDING_ENTRY_RECORDS': store gone"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project 'p2' failed in step 'PENDING_ENTRY_RECORDS': the step threw"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Periodic sweep finished: 1 projects swept, 0 skipped, 2 failed"));
    }

    @Test
    @DisplayName("a step throwing an unchecked exception is failed, and the following steps and projects still run")
    void shouldGoOnAfterUncheckedStepFailure() {
        var throwing = new RecordingStep(SweepStep.Name.PENDING_ENTRY_RECORDS, calls, (projectId, _) -> {
            if ("p1".equals(projectId)) {
                throw new IllegalStateException("contract broken");
            }
            return new Outcome.Done("done");
        });

        var summary = sweep(() -> List.of("p1", "p2"), _ -> true, throwing,
                step(SweepStep.Name.FENCED_SESSION_GENERATIONS)).runOnce(NOW);

        assertAll(
                () -> assertEquals(List.of(
                                "PENDING_ENTRY_RECORDS p1 " + OLDER_THAN, "FENCED_SESSION_GENERATIONS p1 " + OLDER_THAN,
                                "PENDING_ENTRY_RECORDS p2 " + OLDER_THAN, "FENCED_SESSION_GENERATIONS p2 " + OLDER_THAN),
                        calls),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 1), summary),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project 'p1' failed in step 'PENDING_ENTRY_RECORDS': the step threw "
                                + "java.lang.IllegalStateException: contract broken"));
    }

    @Nested
    @DisplayName("schedule")
    class Schedule {

        @Test
        @DisplayName("a scheduled run whose enrolled projects throw is logged, and the next scheduled run still sweeps")
        void shouldKeepScheduleAfterThrowingProjectIds() {
            var first = new AtomicBoolean(true);
            EnrolledProjects flaky = () -> {
                if (first.getAndSet(false)) {
                    throw new IllegalStateException("enrolments unreadable");
                }
                return List.of("p1");
            };
            var sweep = sweep(flaky, _ -> true, step(SweepStep.Name.PENDING_ENTRY_RECORDS));
            sweep.start(executor);

            assertDoesNotThrow(executor.command::run);
            executor.command.run();

            assertAll(
                    () -> assertEquals(List.of("PENDING_ENTRY_RECORDS p1 " + OLDER_THAN), calls),
                    () -> assertFalse(executor.future.isDone(), "the schedule stays alive"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Periodic sweep run failed, the next run takes place at the interval: "
                                    + "java.lang.IllegalStateException: enrolments unreadable"));
        }

        @Test
        @DisplayName("a scheduled run whose recovered predicate throws is logged, and the next scheduled run still sweeps")
        void shouldKeepScheduleAfterThrowingPredicate() {
            var first = new AtomicBoolean(true);
            Predicate<String> flaky = _ -> {
                if (first.getAndSet(false)) {
                    throw new IllegalStateException("recovery state gone");
                }
                return true;
            };
            var sweep = sweep(() -> List.of("p1"), flaky, step(SweepStep.Name.PENDING_ENTRY_RECORDS));
            sweep.start(executor);

            assertDoesNotThrow(executor.command::run);
            executor.command.run();

            assertAll(
                    () -> assertEquals(List.of("PENDING_ENTRY_RECORDS p1 " + OLDER_THAN), calls),
                    () -> assertFalse(executor.future.isDone(), "the schedule stays alive"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "java.lang.IllegalStateException: recovery state gone"));
        }

        @Test
        @DisplayName("a scheduled run fires at the interval, with the instant of the clock")
        void shouldScheduleAtInterval() {
            var sweep = sweep(() -> List.of("p1"), _ -> true, step(SweepStep.Name.PENDING_ENTRY_RECORDS));

            sweep.start(executor);
            executor.command.run();

            assertAll(
                    () -> assertEquals(3_600_000L, executor.initialDelay),
                    () -> assertEquals(3_600_000L, executor.period),
                    () -> assertEquals(TimeUnit.MILLISECONDS, executor.unit),
                    () -> assertEquals(List.of("PENDING_ENTRY_RECORDS p1 " + OLDER_THAN), calls));
        }

        @Test
        @DisplayName("stop cancels the schedule; a second start is refused, a stop without start does nothing")
        void shouldStopSchedule() {
            var sweep = sweep(EnrolledProjects.NONE, _ -> true);
            sweep.stop();

            sweep.start(executor);
            sweep.stop();

            assertAll(
                    () -> assertTrue(executor.future.isCancelled()),
                    () -> assertThrows(IllegalStateException.class, () -> sweep.start(executor)));
        }
    }

    @Nested
    @DisplayName("with the per-project recovery")
    class WithRecovery {

        @Test
        @DisplayName("a project counts as recovered only once its recovery finished without a failure")
        void shouldTakeUpRecoveredProjectsOnly() {
            var recovery = new ProjectRecovery(() -> List.of("p1", "p2"),
                    RecoveryStepFixtures.completeSteps(new RecoveryStep() {

                        @Override
                        public Name name() {
                            return Name.ENROLMENT_INTEGRITY;
                        }

                        @Override
                        public Outcome run(String projectId) {
                            return "p2".equals(projectId) ? new Outcome.Failed("breach")
                                    : new Outcome.Done("verified");
                        }
                    }), Runnable::run);
            var sweep = sweep(() -> List.of("p1", "p2"), recovery::isRecovered,
                    step(SweepStep.Name.PENDING_ENTRY_RECORDS));

            var beforeRecovery = sweep.runOnce(NOW);
            recovery.startBackgroundPass();
            var afterRecovery = sweep.runOnce(NOW);

            assertAll(
                    () -> assertEquals(new PeriodicSweep.Summary(0, 2, 0), beforeRecovery),
                    () -> assertEquals(new PeriodicSweep.Summary(1, 1, 0), afterRecovery),
                    () -> assertFalse(recovery.isRecovered("p2"), "a failed recovery is not recovered"));
        }
    }

    @Test
    @DisplayName("a seam not implemented yet does nothing and says so")
    void shouldReportNotImplementedSeam() {
        var seam = SweepStep.notImplemented(SweepStep.Name.FENCED_SESSION_GENERATIONS);

        assertAll(
                () -> assertEquals(SweepStep.Name.FENCED_SESSION_GENERATIONS, seam.name()),
                () -> assertEquals(new Outcome.Done(RecoveryStep.NOT_IMPLEMENTED), seam.run("p1", OLDER_THAN)));
    }
}
