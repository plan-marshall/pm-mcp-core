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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import org.junit.jupiter.api.BeforeEach;
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
 * <p>
 * Contract literals that stay: the signals and their numbers, the errno {@code EPERM} (1) of a refused call, the
 * probe and retry intervals with the attempts they bound, the exit status 137 of a killed shell, the ids the system
 * primitive refuses and the scripts of the real jobs. The PID and the start instant of the recorded runs are
 * generated.
 */
@EnableGeneratorController
@DisplayName("Termination protocol")
class TerminationProtocolTest {

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

        private Instant now;
        private Duration slept = Duration.ZERO;

        FakeTime(Instant start) {
            now = start;
        }

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

        private long pid;
        private FakeTime time;

        @BeforeEach
        void drawJob() {
            pid = Generators.longs(2, Integer.MAX_VALUE).next();
            time = new FakeTime(Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()));
        }

        private Report terminate(RecordingSignals signals) {
            return new TerminationProtocol(signals, time, time).terminate(pid);
        }

        @Test
        @DisplayName("a group that exits within the grace period is never sent SIGKILL")
        void exitsWithinGrace() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED, DELIVERED, ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, false, OptionalInt.empty()), report,
                            "the group exited within the grace period"),
                    () -> assertEquals(new Call(true, pid, Signal.SIGTERM), signals.calls.getFirst(),
                            "the group is sent SIGTERM first"),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL), "the group is never sent SIGKILL"),
                    () -> assertEquals(Duration.ofMillis(200), time.slept(), "two probe intervals were waited"));
        }

        @Test
        @DisplayName("a group with a member alive after the grace period is sent SIGKILL, once, after the period")
        void killedAfterGrace() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(DELIVERED)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_KILLED, false, OptionalInt.empty()), report,
                            "the living group is killed"),
                    () -> assertEquals(TerminationProtocol.GRACE_PERIOD, time.slept(),
                            "the whole grace period was waited"),
                    () -> assertEquals(new Call(true, pid, Signal.SIGKILL), signals.calls.getLast(),
                            "the last call is SIGKILL to the group"),
                    () -> assertEquals(1, signals.count(true, Signal.SIGKILL), "SIGKILL is sent once"),
                    () -> assertEquals(0, signals.count(false, Signal.SIGTERM), "the bare PID is not signalled"));
        }

        @Test
        @DisplayName("a group that vanishes right at the SIGKILL has exited")
        void vanishesAtKill() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertEquals(Ending.GROUP_EXITED, report.ending(), "a group gone at SIGKILL has exited");
        }

        @Test
        @DisplayName("a cancel before setpgid signals the PID, retries the group and then runs the grace period")
        void cancelBeforeGroup() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH, ESRCH, ESRCH,
                    DELIVERED), Signal.PROBE, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, true, OptionalInt.empty()), report,
                            "the cancel before the group is reported"),
                    () -> assertEquals(new Call(false, pid, Signal.SIGTERM), signals.calls.get(1),
                            "the bare PID is sent SIGTERM second"),
                    () -> assertEquals(1, signals.count(false, Signal.SIGTERM), "the bare PID is signalled once"),
                    () -> assertEquals(4, signals.count(true, Signal.SIGTERM), "the group is tried four times"),
                    () -> assertEquals(TerminationProtocol.GROUP_RETRY_INTERVAL.multipliedBy(3), time.slept(),
                            "three retry intervals were waited"));
        }

        @Test
        @DisplayName("a group that never forms is retried every 0.1 s for at most 10 attempts")
        void groupNeverForms() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)), DELIVERED));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_NOT_REACHED, true, OptionalInt.empty()), report,
                            "the group was never reached"),
                    () -> assertEquals(1 + TerminationProtocol.GROUP_RETRY_ATTEMPTS,
                            signals.count(true, Signal.SIGTERM), "the group is tried once plus the retries"),
                    () -> assertEquals(Duration.ofSeconds(1), time.slept(), "the retries take one second"),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL), "no SIGKILL reaches no group"));
        }

        @Test
        @DisplayName("a caller that knows the leader ended skips the bare-PID step: a freed PID may be reused")
        void deadLeaderSkipsTheBarePidStep() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)), DELIVERED));

            var report = new TerminationProtocol(signals, time, time).terminate(pid, false);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_NOT_REACHED, false, OptionalInt.empty()), report,
                            "the group was not reached"),
                    () -> assertEquals(List.of(new Call(true, pid, Signal.SIGTERM)), signals.calls,
                            "only the group is signalled"),
                    () -> assertEquals(0, signals.count(false, Signal.SIGTERM), "the freed PID is not signalled"),
                    () -> assertEquals(Duration.ZERO, time.slept(), "the protocol never waited"));
        }

        @Test
        @DisplayName("a process that has already ended is not in the window: nothing further is signalled")
        void alreadyEnded() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(ESRCH)), ESRCH));

            var report = terminate(signals);

            assertAll(
                    () -> assertEquals(new Report(Ending.GROUP_NOT_REACHED, false, OptionalInt.empty()), report,
                            "an ended process is not reached"),
                    () -> assertEquals(2, signals.calls.size(), "only the group and the PID are tried"),
                    () -> assertEquals(Duration.ZERO, time.slept(), "the protocol never waited"));
        }

        /** The calls that may be refused, each with the answer that refuses it as EPERM: contract of the protocol. */
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

            assertEquals(new Report(Ending.SIGNAL_REFUSED, cancelBeforeGroup, OptionalInt.of(1)), report,
                    "the refused call stops with its errno");
        }

        @Test
        @DisplayName("an interrupt during the grace period goes straight to SIGKILL and keeps the interrupt status")
        void interrupted() {
            var signals = new RecordingSignals(groupAnswers(Map.of(Signal.SIGTERM, List.of(DELIVERED),
                    Signal.PROBE, List.of(DELIVERED), Signal.SIGKILL, List.of(DELIVERED)), DELIVERED));
            TerminationProtocol.Pause interrupting = _ -> {
                throw new InterruptedException(Generators.letterStrings(1, 16).next());
            };

            var report = new TerminationProtocol(signals, time, interrupting).terminate(pid);

            var interruptStatus = Thread.interrupted();
            assertAll(
                    () -> assertEquals(Ending.GROUP_KILLED, report.ending(), "the interrupted group is killed"),
                    () -> assertEquals(1, signals.count(true, Signal.PROBE), "the group is probed once"),
                    () -> assertTrue(interruptStatus, "the interrupt status is kept"));
        }

        @Test
        @DisplayName("a report carries an errno exactly for a refused signal")
        void reportInvariants() {
            var someErrno = OptionalInt.of(Generators.integers(1, 133).next());
            var noErrno = OptionalInt.empty();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Report(Ending.GROUP_KILLED, false, someErrno),
                            "an errno without a refusal is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Report(Ending.SIGNAL_REFUSED, false, noErrno),
                            "a refusal without an errno is refused"),
                    () -> assertEquals(List.of(0, 9, 15), Stream.of(Signal.PROBE, Signal.SIGKILL, Signal.SIGTERM)
                            .map(Signal::number).toList(), "the signals carry their POSIX numbers"));
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
                    () -> assertEquals(new Report(Ending.GROUP_KILLED, false, OptionalInt.empty()), report,
                            "the ignoring group is killed"),
                    () -> assertEquals(1, signals.count(true, Signal.SIGKILL), "the group is sent SIGKILL once"),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD) >= 0,
                            "the kill came after the grace period"),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD.plus(TOLERANCE)) < 0,
                            "the kill came within the tolerance"),
                    () -> assertFalse(descendant.isAlive(), "the descendant was killed with the group"),
                    () -> assertEquals(137, launched.process().exitValue(), "the leader died of SIGKILL"));
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
                    () -> assertEquals(new Report(Ending.GROUP_EXITED, false, OptionalInt.empty()), report,
                            "the obedient group exited"),
                    () -> assertEquals(0, signals.count(true, Signal.SIGKILL), "the group is never sent SIGKILL"),
                    () -> assertTrue(elapsed.compareTo(TerminationProtocol.GRACE_PERIOD) < 0,
                            "the group exited within the grace period"),
                    () -> assertTrue(launched.process().waitFor(10, TimeUnit.SECONDS), "the leader has ended"));
        }

        @Test
        @DisplayName("the system primitive refuses ids that address no job")
        void systemRefusesForeignIds() {
            var system = ProcessGroupSignals.system();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> system.signalGroup(1, Signal.PROBE),
                            "the group of init is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> system.signalProcess(0, Signal.PROBE),
                            "the PID 0 is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> system.signalGroup(Integer.MAX_VALUE + 1L, Signal.PROBE),
                            "a PID beyond the int range is refused"));
        }
    }
}
