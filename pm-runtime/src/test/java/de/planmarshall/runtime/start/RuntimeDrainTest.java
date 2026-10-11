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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The drain of the runtime (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Drain, Stop &amp; Upgrade)
 * with fake participants on a fixed clock, after a real start of steps 1 to 5 on a test base.
 */
@EnableTestLogger
@DisplayName("Runtime drain")
class RuntimeDrainTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TIMEOUT = Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS);

    private final List<String> calls = new CopyOnWriteArrayList<>();

    private MachinePaths paths;
    private RuntimeLock lock;

    @BeforeEach
    void start() throws IOException {
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

    private RuntimeDrain drain(Duration timeout, DrainParticipant... participants) {
        return new RuntimeDrain(paths, lock, List.of(participants), timeout, CLOCK);
    }

    @Test
    @DisplayName("calls the participants in the order of the specification")
    void shouldCallParticipantsInOrder() {
        var jobs = new FakeParticipant("jobs", true);
        var web = new FakeParticipant("web", true);

        var outcome = drain(TIMEOUT, jobs, web).drain();

        var deadline = NOW.plus(TIMEOUT);
        assertAll(
                () -> assertEquals(List.of("jobs stopAdmitting", "jobs endIdle", "web stopAdmitting", "web endIdle",
                                "jobs awaitQuiet " + deadline, "web awaitQuiet " + deadline, "jobs close", "web close"),
                        calls),
                () -> assertEquals(new RuntimeDrain.Outcome(0, List.of("jobs", "web"), List.of(), List.of()), outcome),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drain started, drain timeout 600 seconds"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Runtime drained: [jobs, web] ended, [] terminated, exit code 0"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RuntimeDrain.class));
    }

    @Test
    @DisplayName("a participant quiet before the deadline is not terminated")
    void shouldNotTerminateQuietParticipant() {
        var jobs = new FakeParticipant("jobs", true, "job-1");

        var outcome = drain(TIMEOUT, jobs).drain();

        assertAll(
                () -> assertFalse(calls.contains("jobs terminateRemaining"), calls::toString),
                () -> assertEquals(List.of(), outcome.terminated()));
    }

    @Test
    @DisplayName("on expiry only the participants that are not quiet are terminated")
    void shouldTerminateOnExpiry() {
        var jobs = new FakeParticipant("jobs", false, "job-1", "job-2");
        var lsp = new FakeParticipant("lsp", true);

        var outcome = drain(TIMEOUT, jobs, lsp).drain();

        assertAll(
                () -> assertTrue(calls.contains("jobs terminateRemaining"), calls::toString),
                () -> assertFalse(calls.contains("lsp terminateRemaining"), calls::toString),
                () -> assertTrue(calls.indexOf("jobs terminateRemaining") < calls.indexOf("jobs close"),
                        calls::toString),
                () -> assertEquals(List.of("lsp"), outcome.ended()),
                () -> assertEquals(List.of("job-1", "job-2"), outcome.terminated()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Drain timeout of 600 seconds expired, terminating what still runs"));
    }

    @Test
    @DisplayName("a timeout of 0 terminates at once without waiting")
    void shouldTerminateAtOnceForZeroTimeout() {
        var jobs = new FakeParticipant("jobs", true, "job-1");
        var lsp = new FakeParticipant("lsp", true);

        var outcome = drain(Duration.ZERO, jobs, lsp).drain();

        assertAll(
                () -> assertTrue(calls.stream().noneMatch(call -> call.contains("awaitQuiet")), calls::toString),
                () -> assertTrue(calls.containsAll(List.of("jobs terminateRemaining", "lsp terminateRemaining")),
                        calls::toString),
                () -> assertEquals(List.of("job-1"), outcome.terminated()));
    }

    @Test
    @DisplayName("removes the runtime record, the token and the socket and releases the singleton lock")
    void shouldRemoveFilesAndReleaseLock() throws Exception {
        drain(TIMEOUT, new FakeParticipant("jobs", true)).drain();

        assertAll(
                () -> assertFalse(Files.exists(paths.runtimeRecord())),
                () -> assertFalse(Files.exists(paths.runtimeToken())),
                () -> assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS)),
                () -> assertFalse(lock.isHeld()));
        try (var next = RuntimeLock.tryAcquire(paths.runtimeLock()).orElseThrow()) {
            assertTrue(next.isHeld());
        }
    }

    @Test
    @DisplayName("a second drain waits for the first and returns its outcome without calling a participant again")
    void shouldDrainOnce() {
        var jobs = new FakeParticipant("jobs", true);
        var drain = drain(TIMEOUT, jobs);
        assertFalse(drain.isDraining());

        var first = drain.drain();
        var callsAfterFirst = List.copyOf(calls);
        var second = drain.drain();

        assertAll(
                () -> assertSame(first, second),
                () -> assertEquals(callsAfterFirst, calls),
                () -> assertTrue(drain.isDraining()));
    }

    @Test
    @DisplayName("a failing participant is recorded, and the drain still removes the files and releases the lock")
    void shouldCompleteDespiteFailingParticipant() {
        var failing = new FakeParticipant("workers", true) {
            @Override
            public void stopAdmitting() throws IOException {
                super.stopAdmitting();
                throw new IOException("worker registry not writable");
            }
        };

        var outcome = drain(TIMEOUT, failing, new FakeParticipant("web", true)).drain();

        assertAll(
                () -> assertEquals(List.of("workers stopAdmitting: worker registry not writable"), outcome.failures()),
                () -> assertEquals(RuntimeDrain.EXIT_DRAINED, outcome.exitCode()),
                () -> assertTrue(calls.contains("web close"), calls::toString),
                () -> assertFalse(Files.exists(paths.runtimeToken())),
                () -> assertFalse(lock.isHeld()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Drain participant 'workers' failed in stopAdmitting: worker registry not writable"));
    }

    @Test
    @DisplayName("leaves the merge-queue and review-bot lease stores byte-identical (PM-WATCH-RUN-6)")
    void shouldLeaveProviderLeasesInPlace() throws Exception {
        var seeded = ProviderLeaseStores.seed(paths.base());

        drain(Duration.ZERO, new FakeParticipant("jobs", false, "job-1")).drain();

        ProviderLeaseStores.assertUnchanged(seeded);
    }

    @Test
    @DisplayName("refuses a negative drain timeout")
    void shouldRefuseNegativeTimeout() {
        var participants = List.<DrainParticipant>of();
        var negative = Duration.ofSeconds(-1);

        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeDrain(paths, lock, participants, negative, CLOCK));
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
}
