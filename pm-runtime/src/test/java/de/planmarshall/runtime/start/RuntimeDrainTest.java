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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.test.TestBases;

/**
 * The drain of the runtime (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Drain, Stop &amp; Upgrade)
 * with fake participants on a fixed clock, after a real start of steps 1 to 5 on a test base. The phase names, the
 * failure formats and the log texts are the specified contract; the participant names, the jobs, the instant, the
 * drain timeout and the failure messages are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Runtime drain")
class RuntimeDrainTest {

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final Duration timeout = Duration.ofSeconds(Generators.longs(1, DrainTimeout.DEFAULT_SECONDS * 10).next());
    private final String jobs = "jobs-" + name();
    private final String web = "web-" + name();
    private final String lsp = "lsp-" + name();
    private final String workers = "workers-" + name();
    private final String job1 = "job-" + name();
    private final String job2 = "job2-" + name();
    private final String failure = Generators.letterStrings(3, 30).next();

    private final List<String> calls = new CopyOnWriteArrayList<>();

    private MachinePaths paths;
    private RuntimeLock lock;

    @BeforeEach
    void start() throws IOException {
        // Fixture path: the prefix of the test base
        paths = new MachinePaths(TestBases.create("pmd"), MachinePaths.Os.current());
        var ready = (StartupSequence.Outcome.Ready) new StartupSequence(paths, System.getProperty("user.name"),
                AuditChainVerification.NONE, _ -> {
                }).run(new SecureRandom());
        lock = ready.lock();
        Files.writeString(paths.runtimeRecord(), "{}");
        Files.createFile(paths.socket(), PosixModes.fileAttribute());
    }

    @AfterEach
    void deleteBase() throws IOException {
        lock.close();
        TestBases.delete(paths.base());
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private RuntimeDrain drain(Duration drainTimeout, DrainParticipant... participants) {
        return new RuntimeDrain(paths, lock, List.of(participants), drainTimeout, clock);
    }

    private void assertFilesRemovedAndLockReleased() {
        assertAll(
                () -> assertFalse(Files.exists(paths.runtimeRecord()), "the runtime record must be removed"),
                () -> assertFalse(Files.exists(paths.runtimeToken()), "the runtime token must be removed"),
                () -> assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS),
                        "the runtime socket must be removed"),
                () -> assertFalse(lock.isHeld(), "the singleton lock must be released"));
    }

    private void assertParticipantFailureLogged(String participant, String phase, String message) {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.DRAIN_PARTICIPANT_FAILED.resolveIdentifierString());
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                "Drain participant '" + participant + "' failed in " + phase + ": " + message);
    }

    @Test
    @DisplayName("calls the participants in the order of the specification")
    void shouldCallParticipantsInOrder() {
        var first = new FakeParticipant(jobs, true);
        var last = new FakeParticipant(web, true);

        var outcome = drain(timeout, first, last).drain();

        var deadline = now.plus(timeout);
        assertAll(
                () -> assertEquals(List.of(jobs + " stopAdmitting", jobs + " endIdle", web + " stopAdmitting",
                        web + " endIdle", jobs + " awaitQuiet " + deadline, web + " awaitQuiet " + deadline,
                        jobs + " close", web + " close"), calls, "the phases must run in the specified order"),
                () -> assertEquals(new RuntimeDrain.Outcome(0, List.of(jobs, web), List.of(), List.of()), outcome,
                        "both participants must have ended within time"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.RUNTIME_DRAIN_STARTED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drain started, drain timeout " + timeout.toSeconds() + " seconds"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.RUNTIME_DRAINED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drained: [" + jobs + ", " + web + "] ended, [] terminated, exit code 0"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RuntimeDrain.class));
    }

    @Test
    @DisplayName("a participant quiet before the deadline is not terminated")
    void shouldNotTerminateQuietParticipant() {
        var participant = new FakeParticipant(jobs, true, job1);

        var outcome = drain(timeout, participant).drain();

        assertAll(
                () -> assertFalse(calls.contains(jobs + " terminateRemaining"),
                        "a quiet participant must not be terminated"),
                () -> assertEquals(List.of(), outcome.terminated(), "nothing must be terminated after a quiet drain"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                        WARN.DRAIN_TIMEOUT_EXPIRED.resolveIdentifierString()));
    }

    @Test
    @DisplayName("on expiry only the participants that are not quiet are terminated")
    void shouldTerminateOnExpiry() {
        var busy = new FakeParticipant(jobs, false, job1, job2);
        var quiet = new FakeParticipant(lsp, true);

        var outcome = drain(timeout, busy, quiet).drain();

        assertAll(
                () -> assertTrue(calls.contains(jobs + " terminateRemaining"),
                        "the busy participant must be terminated"),
                () -> assertFalse(calls.contains(lsp + " terminateRemaining"),
                        "the quiet participant must not be terminated"),
                () -> assertTrue(calls.indexOf(jobs + " terminateRemaining") < calls.indexOf(jobs + " close"),
                        "the termination must come before the close"),
                () -> assertEquals(List.of(lsp), outcome.ended(), "only the quiet participant has ended"),
                () -> assertEquals(List.of(job1, job2), outcome.terminated(), "both jobs must be terminated"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.DRAIN_TIMEOUT_EXPIRED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Drain timeout of " + timeout.toSeconds() + " seconds expired, terminating what still runs"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drained: [" + lsp + "] ended, [" + job1 + ", " + job2 + "] terminated, exit code 0"));
    }

    @Test
    @DisplayName("a timeout of 0 terminates at once without waiting")
    void shouldTerminateAtOnceForZeroTimeout() {
        var first = new FakeParticipant(jobs, true, job1);
        var second = new FakeParticipant(lsp, true);

        var outcome = drain(Duration.ZERO, first, second).drain();

        assertAll(
                () -> assertTrue(calls.stream().noneMatch(call -> call.contains("awaitQuiet")),
                        "a timeout of 0 must not wait for quiet"),
                () -> assertTrue(calls.containsAll(List.of(jobs + " terminateRemaining", lsp + " terminateRemaining")),
                        "every participant must be terminated at once"),
                () -> assertEquals(List.of(job1), outcome.terminated(), "the running job must be terminated"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drain started, drain timeout 0 seconds"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Drain timeout of 0 seconds expired"));
    }

    @Test
    @DisplayName("removes the runtime record, the token and the socket and releases the singleton lock")
    void shouldRemoveFilesAndReleaseLock() throws Exception {
        drain(timeout, new FakeParticipant(jobs, true)).drain();

        assertFilesRemovedAndLockReleased();
        try (var next = RuntimeLock.tryAcquire(paths.runtimeLock()).orElseThrow()) {
            assertTrue(next.isHeld(), "the next runtime must acquire the singleton lock");
        }
    }

    @Test
    @DisplayName("a second drain waits for the first and returns its outcome without calling a participant again")
    void shouldDrainOnce() {
        var participant = new FakeParticipant(jobs, true);
        var drain = drain(timeout, participant);
        assertFalse(drain.isDraining(), "the runtime must not drain before the drain");

        var first = drain.drain();
        var callsAfterFirst = List.copyOf(calls);
        var second = drain.drain();

        assertAll(
                () -> assertSame(first, second, "the second drain must return the first outcome"),
                () -> assertEquals(callsAfterFirst, calls, "the second drain must not call a participant"),
                () -> assertTrue(drain.isDraining(), "the runtime must count as draining afterwards"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.RUNTIME_DRAIN_STARTED.resolveIdentifierString()));
    }

    @Test
    @DisplayName("a failing participant is recorded, and the drain still removes the files and releases the lock")
    void shouldCompleteDespiteFailingParticipant() {
        var failing = new FakeParticipant(workers, true) {
            @Override
            public void stopAdmitting() throws IOException {
                super.stopAdmitting();
                throw new IOException(failure);
            }
        };

        var outcome = drain(timeout, failing, new FakeParticipant(web, true)).drain();

        assertAll(
                () -> assertEquals(List.of(workers + " stopAdmitting: " + failure), outcome.failures(),
                        "the failure of the participant must be recorded"),
                () -> assertEquals(RuntimeDrain.EXIT_DRAINED, outcome.exitCode(), "the drain must exit with code 0"),
                () -> assertTrue(calls.contains(web + " close"), "the other participant must still be closed"),
                () -> assertParticipantFailureLogged(workers, "stopAdmitting", failure));
        assertFilesRemovedAndLockReleased();
    }

    @ParameterizedTest(name = "unchecked failure in {0}")
    @ValueSource(strings = {"stopAdmitting", "endIdle", "awaitQuiet", "terminateRemaining", "close"})
    @DisplayName("an unchecked failure of a participant is recorded, and the drain still removes the files and releases "
            + "the lock (the specified phases are the cases)")
    void shouldCompleteDespiteUncheckedFailure(String phase) {
        var failing = new ThrowingParticipant(workers, phase);

        var outcome = drain(timeout, failing, new FakeParticipant(web, true)).drain();

        assertAll(
                () -> assertEquals(List.of(workers + " " + phase + ": " + failure + " " + phase), outcome.failures(),
                        "the unchecked failure must be recorded"),
                () -> assertEquals(RuntimeDrain.EXIT_DRAINED, outcome.exitCode(), "the drain must exit with code 0"),
                () -> assertTrue(calls.contains(web + " close"), "the other participant must still be closed"),
                () -> assertParticipantFailureLogged(workers, phase, failure + " " + phase));
        assertFilesRemovedAndLockReleased();
    }

    @Test
    @DisplayName("a drain that ends exceptionally still removes the files and releases the lock, and every caller gets "
            + "the cause")
    void shouldTellEveryCallerTheCauseOfAnExceptionalEnd() {
        var broken = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                throw new IllegalStateException(failure);
            }
        };
        var drain = new RuntimeDrain(paths, lock, List.of(new FakeParticipant(jobs, true)), timeout, broken);

        var first = assertThrows(CompletionException.class, drain::drain, "the first caller must get the failure");
        var second = assertThrows(CompletionException.class, drain::drain, "the second caller must get the failure");

        assertAll(
                () -> assertInstanceOf(IllegalStateException.class, first.getCause(),
                        "the cause must be the failure of the clock"),
                () -> assertSame(first.getCause(), second.getCause(), "both callers must get the same cause"));
        assertFilesRemovedAndLockReleased();
    }

    @Test
    @DisplayName("a concurrent drain waits for the running drain and gets its outcome, an unchecked failure included")
    void shouldNotLeaveConcurrentCallerWaiting() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiting = new FakeParticipant(jobs, true) {
            @Override
            public boolean awaitQuiet(Instant deadline) {
                entered.countDown();
                awaitUninterruptibly(release);
                return true;
            }
        };
        var broken = new FakeParticipant(lsp, true) {
            @Override
            public void close() {
                throw new IllegalStateException(failure);
            }
        };
        var drain = drain(timeout, waiting, broken);
        var firstOutcome = new AtomicReference<RuntimeDrain.Outcome>();
        var first = Thread.ofPlatform().start(() -> firstOutcome.set(drain.drain()));
        assertTrue(entered.await(10, TimeUnit.SECONDS), "the first drain must reach the wait for quiet");

        var second = CompletableFuture.supplyAsync(drain::drain);
        release.countDown();
        first.join(Duration.ofSeconds(10));

        var secondOutcome = second.get(10, TimeUnit.SECONDS);
        assertAll(
                () -> assertSame(firstOutcome.get(), secondOutcome, "both callers must get the same outcome"),
                () -> assertEquals(List.of(lsp + " close: " + failure), secondOutcome.failures(),
                        "the unchecked close failure must be recorded"),
                () -> assertFalse(lock.isHeld(), "the singleton lock must be released"),
                () -> assertParticipantFailureLogged(lsp, "close", failure));
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("leaves the merge-queue and review-bot lease stores byte-identical (PM-WATCH-RUN-6)")
    void shouldLeaveProviderLeasesInPlace() throws Exception {
        var seeded = ProviderLeaseStores.seed(paths.base());

        drain(Duration.ZERO, new FakeParticipant(jobs, false, job1)).drain();

        ProviderLeaseStores.assertUnchanged(seeded);
    }

    @Test
    @DisplayName("a deadline beyond the last representable instant waits until that instant instead of failing")
    void shouldSaturateDeadlineOfFarFutureClock() {
        var farFuture = Clock.fixed(Instant.MAX.minusSeconds(Generators.longs(1, 3600).next()), ZoneOffset.UTC);
        var participant = new FakeParticipant(jobs, true);
        var drain = new RuntimeDrain(paths, lock, List.of(participant), Duration.ofSeconds(DrainTimeout.MAX_SECONDS),
                farFuture);

        var outcome = drain.drain();

        assertAll(
                () -> assertTrue(calls.contains(jobs + " awaitQuiet " + Instant.MAX),
                        "the deadline must saturate at Instant.MAX"),
                () -> assertEquals(List.of(), outcome.failures(), "the far deadline must not fail the drain"),
                () -> assertFalse(lock.isHeld(), "the singleton lock must be released"));
    }

    @Test
    @DisplayName("refuses a negative drain timeout")
    void shouldRefuseNegativeTimeout() {
        var participants = List.<DrainParticipant>of();
        var negative = Duration.ofSeconds(-Generators.longs(1, DrainTimeout.MAX_SECONDS).next());

        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeDrain(paths, lock, participants, negative, clock),
                "a negative drain timeout must be refused");
    }

    /** A participant that notes every call in {@link #calls}. */
    private class FakeParticipant implements DrainParticipant {

        private final String name;
        private final boolean quiet;
        private final List<String> running;

        FakeParticipant(String name, boolean quiet, String... running) {
            this.name = name;
            this.quiet = quiet;
            this.running = List.of(running);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void stopAdmitting() throws IOException {
            calls.add(name + " stopAdmitting");
        }

        @Override
        public void endIdle() {
            calls.add(name + " endIdle");
        }

        @Override
        public boolean awaitQuiet(Instant deadline) {
            calls.add(name + " awaitQuiet " + deadline);
            return quiet;
        }

        @Override
        public List<String> terminateRemaining() {
            calls.add(name + " terminateRemaining");
            return running;
        }

        @Override
        public void close() {
            calls.add(name + " close");
        }
    }

    /** A participant that is never quiet and throws an unchecked exception in one phase. */
    private class ThrowingParticipant extends FakeParticipant {

        private final String failingPhase;

        ThrowingParticipant(String name, String failingPhase) {
            super(name, false, job1);
            this.failingPhase = failingPhase;
        }

        private void failIn(String phase) {
            if (failingPhase.equals(phase)) {
                throw new IllegalStateException(failure + " " + phase);
            }
        }

        @Override
        public void stopAdmitting() throws IOException {
            super.stopAdmitting();
            failIn("stopAdmitting");
        }

        @Override
        public void endIdle() {
            super.endIdle();
            failIn("endIdle");
        }

        @Override
        public boolean awaitQuiet(Instant deadline) {
            var quiet = super.awaitQuiet(deadline);
            failIn("awaitQuiet");
            return quiet;
        }

        @Override
        public List<String> terminateRemaining() {
            var running = super.terminateRemaining();
            failIn("terminateRemaining");
            return running;
        }

        @Override
        public void close() {
            super.close();
            failIn("close");
        }
    }
}
