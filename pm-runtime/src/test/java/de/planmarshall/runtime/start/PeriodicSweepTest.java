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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;

/**
 * The periodic sweep over the enrolled projects with recording steps, run directly and on a capturing executor. The
 * intervals, the step names and the log texts are the specified contract; the projects, the instant of a run and the
 * failure reasons are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Periodic sweep")
class PeriodicSweepTest {

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Instant olderThan = now.minusSeconds(86_400);
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final String p1 = "first-" + name();
    private final String p2 = "second-" + name();
    private final String p3 = "third-" + name();
    private final String reason = Generators.letterStrings(3, 30).next();

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final CapturingExecutor executor = new CapturingExecutor();

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
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

    /** @return an EasyMock step that records each call as {@code "TAG NAME project olderThan"} and then runs its body */
    private SweepStep step(SweepStep.Name name) {
        return step(name, "step");
    }

    private SweepStep step(SweepStep.Name name, String tag) {
        return step(name, tag,
                (_, _) -> new Outcome.Done(Generators.letterStrings(3, 30).next()));
    }

    private SweepStep step(SweepStep.Name name, String tag, BiFunction<String, Instant, Outcome> body) {
        SweepStep mock = createMock(SweepStep.class);
        expect(mock.name()).andReturn(name).anyTimes();
        expect(mock.run(anyObject(String.class), anyObject(Instant.class))).andAnswer(() -> {
            var arguments = getCurrentArguments();
            calls.add(tag + " " + name + " " + arguments[0] + " " + arguments[1]);
            return body.apply((String) arguments[0], (Instant) arguments[1]);
        }).anyTimes();
        replay(mock);
        return mock;
    }

    private PeriodicSweep sweep(EnrolledProjects enrolled, Predicate<String> recovered,
            SweepStep... steps) {
        return new PeriodicSweep(enrolled, recovered, List.of(steps), clock);
    }

    private String call(String tag, String projectId) {
        return tag + " PENDING_ENTRY_RECORDS " + projectId + " " + olderThan;
    }

    private static void assertFinishedLogged(int swept, int skipped, int failed) {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.PERIODIC_SWEEP_FINISHED.resolveIdentifierString());
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                "Periodic sweep finished: %d projects swept, %d skipped, %d failed".formatted(swept, skipped, failed));
    }

    @Test
    @DisplayName("the interval is 3600 seconds and the pending entry maximum age 86400 seconds (specified constants)")
    void shouldHaveCompiledConstants() {
        assertAll(
                () -> assertEquals(PeriodicSweep.INTERVAL, Duration.ofSeconds(3600),
                        "the sweep interval must be 3600 seconds"),
                () -> assertEquals(PeriodicSweep.PENDING_ENTRY_MAX_AGE, Duration.ofSeconds(86_400),
                        "the pending entry maximum age must be 86400 s"));
    }

    @Test
    @DisplayName("the steps of the sweep are the technical residue removals, and no step of fenced generations")
    void shouldRegisterOnlyResidueSteps() {
        // PM-ARCH-10 bullet 4: the periodic sweep removes technical residue and never work history. The pending entry
        // records are the residue of a start, and no step removes anything else.
        var names = Arrays.stream(SweepStep.Name.values()).map(Enum::name).toList();

        assertEquals(List.of("PENDING_ENTRY_RECORDS"), names, "the pending entry records must be the only step");
    }

    @Test
    @DisplayName("a run passes now minus the maximum age to every step, in order, for every enrolled project")
    void shouldRunEveryStepForEveryProject() {
        var summary = sweep(() -> List.of(p1, p2), _ -> true,
                step(SweepStep.Name.PENDING_ENTRY_RECORDS, "first"),
                step(SweepStep.Name.PENDING_ENTRY_RECORDS, "second")).runOnce(now);

        assertAll(
                () -> assertEquals(List.of(call("first", p1), call("second", p1), call("first", p2),
                        call("second", p2)), calls, "every step must run for every project in order"),
                () -> assertEquals(new PeriodicSweep.Summary(2, 0, 0), summary, "both projects must be swept"),
                () -> assertFinishedLogged(2, 0, 0));
    }

    @Test
    @DisplayName("a project whose recovery has not finished is skipped")
    void shouldSkipProjectNotRecovered() {
        var summary = sweep(() -> List.of(p1, p2), p1::equals, step(SweepStep.Name.PENDING_ENTRY_RECORDS))
                .runOnce(now);

        assertAll(
                () -> assertEquals(List.of(call("step", p1)), calls, "only the recovered project must be swept"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 1, 0), summary,
                        "one project swept and one project skipped"),
                () -> assertFinishedLogged(1, 1, 0));
    }

    @Test
    @DisplayName("a failing step stops neither the other steps nor the other projects")
    void shouldGoOnAfterFailingStep() {
        var diskGone = Generators.letterStrings(3, 30).next();
        var failing = step(SweepStep.Name.PENDING_ENTRY_RECORDS, "failing", (projectId, _) -> {
            if (p1.equals(projectId)) {
                return new Outcome.Failed(reason);
            }
            if (p2.equals(projectId)) {
                throw new UncheckedIOException(diskGone, new IOException(diskGone));
            }
            return new Outcome.Done(Generators.letterStrings(3, 30).next());
        });

        var summary = sweep(() -> List.of(p1, p2, p3), _ -> true, failing,
                step(SweepStep.Name.PENDING_ENTRY_RECORDS, "second")).runOnce(now);

        assertAll(
                () -> assertEquals(6, calls.size(), "both steps must run for all three projects"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 2), summary,
                        "one project swept and two projects failed"),
                () -> assertEquals(2, warnings(WARN.PERIODIC_SWEEP_STEP_FAILED.resolveIdentifierString()),
                        "each failed step must be logged by its record"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project '" + p1 + "' failed in step 'PENDING_ENTRY_RECORDS': " + reason),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project '" + p2 + "' failed in step 'PENDING_ENTRY_RECORDS': the step threw"),
                () -> assertFinishedLogged(1, 0, 2));
    }

    @Test
    @DisplayName("a step throwing an unchecked exception is failed, and the following steps and projects still run")
    void shouldGoOnAfterUncheckedStepFailure() {
        var throwing = step(SweepStep.Name.PENDING_ENTRY_RECORDS, "throwing", (projectId, _) -> {
            if (p1.equals(projectId)) {
                throw new IllegalStateException(reason);
            }
            return new Outcome.Done(Generators.letterStrings(3, 30).next());
        });

        var summary = sweep(() -> List.of(p1, p2), _ -> true, throwing,
                step(SweepStep.Name.PENDING_ENTRY_RECORDS, "second")).runOnce(now);

        assertAll(
                () -> assertEquals(List.of(call("throwing", p1), call("second", p1), call("throwing", p2),
                        call("second", p2)), calls, "the steps after the throwing one must still run"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 1), summary,
                        "one project swept and one project failed"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.PERIODIC_SWEEP_STEP_FAILED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Periodic sweep of project '" + p1 + "' failed in step 'PENDING_ENTRY_RECORDS': the step threw "
                                + "java.lang.IllegalStateException: " + reason));
    }

    private static int warnings(String text) {
        return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN, text).size();
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
                    throw new IllegalStateException(reason);
                }
                return List.of(p1);
            };
            var sweep = sweep(flaky, _ -> true, step(SweepStep.Name.PENDING_ENTRY_RECORDS));
            sweep.start(executor);

            assertDoesNotThrow(executor.command::run, "a failing scheduled run must not throw");
            executor.command.run();

            assertAll(
                    () -> assertEquals(List.of(call("step", p1)), calls, "the next scheduled run must still sweep"),
                    () -> assertFalse(executor.future.isDone(), "the schedule must stay alive after a failure"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.PERIODIC_SWEEP_RUN_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Periodic sweep run failed, the next run takes place at the interval: "
                                    + "java.lang.IllegalStateException: " + reason));
        }

        @Test
        @DisplayName("a scheduled run whose recovered predicate throws is logged, and the next scheduled run still sweeps")
        void shouldKeepScheduleAfterThrowingPredicate() {
            var first = new AtomicBoolean(true);
            Predicate<String> flaky = _ -> {
                if (first.getAndSet(false)) {
                    throw new IllegalStateException(reason);
                }
                return true;
            };
            var sweep = sweep(() -> List.of(p1), flaky, step(SweepStep.Name.PENDING_ENTRY_RECORDS));
            sweep.start(executor);

            assertDoesNotThrow(executor.command::run, "a failing scheduled run must not throw");
            executor.command.run();

            assertAll(
                    () -> assertEquals(List.of(call("step", p1)), calls, "the next scheduled run must still sweep"),
                    () -> assertFalse(executor.future.isDone(), "the schedule must stay alive after a failure"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.PERIODIC_SWEEP_RUN_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "java.lang.IllegalStateException: " + reason));
        }

        @Test
        @DisplayName("a scheduled run fires at the interval, with the instant of the clock")
        void shouldScheduleAtInterval() {
            var sweep = sweep(() -> List.of(p1), _ -> true, step(SweepStep.Name.PENDING_ENTRY_RECORDS));

            sweep.start(executor);
            executor.command.run();

            assertAll(
                    () -> assertEquals(3_600_000L, executor.initialDelay, "the first run must be one interval away"),
                    () -> assertEquals(3_600_000L, executor.period, "the period must be the interval of 3600 s"),
                    () -> assertEquals(TimeUnit.MILLISECONDS, executor.unit, "the schedule must count milliseconds"),
                    () -> assertEquals(List.of(call("step", p1)), calls, "the run must use the instant of the clock"),
                    () -> assertFinishedLogged(1, 0, 0));
        }

        @Test
        @DisplayName("stop cancels the schedule; a second start is refused, a stop without start does nothing")
        void shouldStopSchedule() {
            var sweep = sweep(EnrolledProjects.NONE, _ -> true);
            sweep.stop();

            sweep.start(executor);
            sweep.stop();

            assertAll(
                    () -> assertTrue(executor.future.isCancelled(), "stop must cancel the scheduled runs"),
                    () -> assertThrows(IllegalStateException.class, () -> sweep.start(executor),
                            "a second start must be refused"));
        }
    }

    @Nested
    @DisplayName("with the per-project recovery")
    class WithRecovery {

        @Test
        @DisplayName("a project counts as recovered only once its recovery finished without a failure")
        void shouldTakeUpRecoveredProjectsOnly() {
            var verified = Generators.letterStrings(3, 30).next();
            var recovery = new ProjectRecovery(() -> List.of(p1, p2),
                    RecoveryStepFixtures.completeSteps(new RecoveryStep() {

                        @Override
                        public Name name() {
                            return Name.ENROLMENT_INTEGRITY;
                        }

                        @Override
                        public Outcome run(String projectId) {
                            return p2.equals(projectId) ? new Outcome.Failed(reason) : new Outcome.Done(verified);
                        }
                    }), Runnable::run);
            var sweep = sweep(() -> List.of(p1, p2), recovery::isRecovered,
                    step(SweepStep.Name.PENDING_ENTRY_RECORDS));

            var beforeRecovery = sweep.runOnce(now);
            recovery.startBackgroundPass();
            var afterRecovery = sweep.runOnce(now);

            assertAll(
                    () -> assertEquals(new PeriodicSweep.Summary(0, 2, 0), beforeRecovery,
                            "both projects are skipped before the recovery"),
                    () -> assertEquals(new PeriodicSweep.Summary(1, 1, 0), afterRecovery,
                            "the failed project is still skipped afterwards"),
                    () -> assertFalse(recovery.isRecovered(p2), "a failed recovery is not recovered"));
        }
    }
}
