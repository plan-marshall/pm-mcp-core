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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.runtime.job.ProcessGroupSignals.Answer;
import de.planmarshall.runtime.job.ProcessGroupSignals.Delivered;
import de.planmarshall.runtime.job.ProcessGroupSignals.NoSuchProcess;
import de.planmarshall.runtime.job.ProcessGroupSignals.Refused;
import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.job.TerminationProtocol.Ending;
import de.planmarshall.runtime.job.TerminationProtocol.Report;

/**
 * The termination protocol of a job (PM-EXEC-4): {@code SIGTERM} to the group, the grace period, {@code SIGKILL} to
 * the group when a member lives, and the cancel before the group exists. The decisive assertions run against the
 * recording signalling primitive on every platform; on Linux a job started through {@link FakePmExec} shows the
 * protocol against real process groups, waiting no longer than the compiled grace period plus a bounded tolerance.
 */
@DisplayName("Termination protocol")
class TerminationProtocolTest {

    private static final long PID = 4242;
    private static final Answer DELIVERED = new Delivered();
    private static final Answer ESRCH = new NoSuchProcess();

    /** One {@code kill} call: against the group or the PID, with its signal. */
    record Call(boolean group, long id, Signal signal) {
    }

    /** The signalling primitive that records every call and answers what the test scripts. */
    static final class RecordingSignals implements ProcessGroupSignals {

        final List<Call> calls = new CopyOnWriteArrayList<>();
        private final Function<Call, Answer> answers;

        RecordingSignals(Function<Call, Answer> answers) {
            this.answers = answers;
        }

        @Override
        public Answer signalGroup(long pgid, Signal signal) {
            return capture(new Call(true, pgid, signal));
        }

        @Override
        public Answer signalProcess(long pid, Signal signal) {
            return capture(new Call(false, pid, signal));
        }

        private Answer capture(Call call) {
            calls.add(call);
            return answers.apply(call);
        }

        long count(boolean group, Signal signal) {
            return calls.stream().filter(call -> call.group() == group && call.signal() == signal).count();
        }
    }

    /** A clock that advances only by the pauses of the protocol. */
    static final class FakeTime implements InstantSource, TerminationProtocol.Pause {

        private Instant now = Instant.parse("2026-10-17T10:00:00Z");
        private Duration slept = Duration.ZERO;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
            slept = slept.plus(duration);
        }

        Duration slept() {
            return slept;
        }
    }

    /** @return answers in the order given for one signal against the group, the last one repeated */
    private static Function<Call, Answer> groupAnswers(Map<Signal, List<Answer>> group, Answer process) {
        var queues = new EnumMap<Signal, ArrayDeque<Answer>>(Signal.class);
        group.forEach((signal, answers) -> queues.put(signal, new ArrayDeque<>(answers)));
        return call -> {
            if (!call.group()) {
                return process;
            }
            var queue = queues.get(call.signal());
            return queue.size() > 1 ? queue.poll() : queue.peek();
        };
    }

    @Nested
    @DisplayName("against the recording primitive")
    class Recorded {

        private final FakeTime time = new FakeTime();

        private Report terminate(RecordingSignals signals) {
            return new TerminationProtocol(signals, time, time).terminate(PID);
        }

        @Test
        @DisplayName("a group that exits within the grace period is never sent SIGKILL")
        void exitsWithinGrace() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED, DELIVERED, ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, false, OptionalInt.empty()), report),
                    () -> assertEquals(new Call(true, PID, Signal.SIGTERM), signals.calls.getFirst()),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL)),
                    () -> assertEquals(Duration.ofMillis(200), time.slept()));
        }

        @Test
        @DisplayName("a group with a member alive after the grace period is sent SIGKILL, once, after the period")
        void killedAfterGrace() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(DELIVERED)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_KILLED, false, OptionalInt.empty()), report),
                    () -> assertEquals(TerminationProtocol.GRACE_PERIOD, time.slept()),
                    () -> assertEquals(new Call(true, PID, Signal.SIGKILL), signals.calls.getLast()),
                    () -> assertEquals(1, signals.count(true, Signal.SIGKILL)),
                    () -> assertEquals(0, signals.count(false, Signal.SIGTERM)));
        }

        @Test
        @DisplayName("a group that vanishes right at the SIGKILL has exited")
        void vanishesAtKill() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertEquals(Ending.GROUP_EXITED, report.ending());
        }

        @Test
        @DisplayName("a cancel before setpgid signals the PID, retries the group and then runs the grace period")
        void cancelBeforeGroup() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH, ESRCH, ESRCH,
                    DELIVERED), Signal.PROBE, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, true, OptionalInt.empty()), report),
                    () -> assertEquals(new Call(false, PID, Signal.SIGTERM), signals.calls.get(1)),
                    () -> assertEquals(1, signals.count(false, Signal.SIGTERM)),
                    () -> assertEquals(4, signals.count(true, Signal.SIGTERM)),
                    () -> assertEquals(TerminationProtocol.GROUP_RETRY_INTERVAL.multipliedBy(3), time.slept()));
        }

        @Test
        @DisplayName("a group that never forms is retried every 0.1 s for at most 10 attempts")
        void groupNeverForms() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_NOT_REACHED, true, OptionalInt.empty()), report),
                    () -> assertEquals(1 + TerminationProtocol.GROUP_RETRY_ATTEMPTS,
                            signals.count(true, Signal.SIGTERM)),
                    () -> assertEquals(Duration.ofSeconds(1), time.slept()),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL)));
        }

        @Test
        @DisplayName("a process that has already ended is not in the window: nothing further is signalled")
        void alreadyEnded() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)), ESRCH));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_NOT_REACHED, false, OptionalInt.empty()), report),
                    () -> assertEquals(2, signals.calls.size()),
                    () -> assertEquals(Duration.ZERO, time.slept()));
        }

        static Stream<Arguments> refusals() {
            var eperm = new Refused(1);
            return Stream.of(
                    Arguments.of("SIGTERM to the group", groupAnswers(Map.of(Signal.SIGTERM, List.of(eperm)),
                            DELIVERED), false),
                    Arguments.of("SIGTERM to the PID", groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)),
                            eperm), true),
                    Arguments.of("a retry against the group", groupAnswers(Map.of(Signal.SIGTERM,
                            List.of(ESRCH, eperm)), DELIVERED), true),
                    Arguments.of("SIGKILL to the group", groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                            Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(eperm)), DELIVERED), false));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusals")
        @DisplayName("a call failing with another errno stops the protocol and reports the errno")
        void refused(String call, Function<Call, Answer> answers, boolean cancelBeforeGroup) {
            var report = terminate(new RecordingSignals(answers));

            assertEquals(new Report(Ending.SIGNAL_REFUSED, cancelBeforeGroup, OptionalInt.of(1)), report);
        }

        @Test
        @DisplayName("an interrupt during the grace period goes straight to SIGKILL and keeps the interrupt status")
        void interrupted() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(DELIVERED)), DELIVERED));
            TerminationProtocol.Pause interrupting = _ -> {
                throw new InterruptedException("drain");
            };

            var report = new TerminationProtocol(signals, time, interrupting).terminate(PID);

            var interruptStatus = Thread.interrupted();
            assertAll(
                    () -> assertEquals(Ending.GROUP_KILLED, report.ending()),
                    () -> assertEquals(1, signals.count(true, Signal.PROBE)),
                    () -> assertTrue(interruptStatus));
        }

        @Test
        @DisplayName("a report carries an errno exactly for a refused signal")
        void reportInvariants() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Report(Ending.GROUP_KILLED, false, OptionalInt.of(1))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Report(Ending.SIGNAL_REFUSED, false, OptionalInt.empty())),
                    () -> assertEquals(List.of(0, 9, 15), Stream.of(Signal.PROBE, Signal.SIGKILL, Signal.SIGTERM)
                            .map(Signal::number).toList()));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("against real process groups")
    class Real {

        /** The tolerance of the wall-clock assertions beyond the compiled grace period. */
        private static final Duration TOLERANCE = Duration.ofSeconds(3);

        @TempDir
        Path home;

        @TempDir
        Path work;

        private final RecordingSignals signals = new RecordingSignals(new Function<>() {
            private final ProcessGroupSignals system = ProcessGroupSignals.system();

            @Override
            public Answer apply(Call call) {
                return call.group() ? system.signalGroup(call.id(), call.signal())
                        : system.signalProcess(call.id(), call.signal());
            }
        });

        private PmExecLauncher.Launched launched;

        @AfterEach
        void killLeftovers() throws InterruptedException {
            if (launched != null && launched.process().isAlive()) {
                ProcessGroupSignals.system().signalGroup(launched.pid(), Signal.SIGKILL);
                launched.process().waitFor(10, TimeUnit.SECONDS);
            }
        }

        private JobLog start(String script) throws Exception {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            var log = work.resolve("job.log");
            launched = new PmExecLauncher(home).launch(new PmExecLauncher.Launch(work,
                    List.of(FakePmExec.tool("sh").toString(), "-c", script), Map.of(), Optional.empty(), log));
            return new JobLog(log);
        }

        @Test
        @DisplayName("a job ignoring SIGTERM and its descendant are killed with the group after the grace period")
        void ignoringSigtermKilledWithGroup() throws Exception {
            var log = start("trap '' TERM; " + FakePmExec.tool("sleep") + " 60 & echo \"descendant $!\"; wait");
            var lines = FakePmExec.awaitLines(log, launched.process(), 1);
            var descendant = ProcessHandle.of(Long.parseLong(lines.lines().getFirst().substring(11))).orElseThrow();
            var started = System.nanoTime();

            var report = new TerminationProtocol(signals).terminate(launched.pid());

            var elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertTrue(launched.process().waitFor(10, TimeUnit.SECONDS), "the leader should have ended");
            descendant.onExit().get(10, TimeUnit.SECONDS);
            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_KILLED, false, OptionalInt.empty()), report),
                    () -> assertEquals(1, signals.count(true, Signal.SIGKILL)),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD) >= 0, elapsed::toString),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD.plus(TOLERANCE)) < 0,
                            elapsed::toString),
                    () -> assertFalse(descendant.isAlive()),
                    () -> assertEquals(137, launched.process().exitValue()));
        }

        @Test
        @DisplayName("matched control: a job exiting on SIGTERM is never sent SIGKILL")
        void exitingOnSigtermNotKilled() throws Exception {
            var log = start("echo started; exec " + FakePmExec.tool("sleep") + " 60");
            FakePmExec.awaitLines(log, launched.process(), 1);
            var started = System.nanoTime();

            var report = new TerminationProtocol(signals).terminate(launched.pid());

            var elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, false, OptionalInt.empty()), report),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL)),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD) < 0, elapsed::toString),
                    () -> assertTrue(launched.process().waitFor(10, TimeUnit.SECONDS)));
        }

        @Test
        @DisplayName("the system primitive refuses ids that address no job")
        void systemRefusesForeignIds() {
            var system = ProcessGroupSignals.system();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> system.signalGroup(1, Signal.PROBE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> system.signalProcess(0, Signal.PROBE)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> system.signalGroup(Integer.MAX_VALUE + 1L, Signal.PROBE)));
        }
    }
}
