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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The relay version-mismatch detection. The header name, the recommended command and the log texts the tests read
 * are the specified contract; the versions are generated, the runtime version with a major of two digits and every
 * relay version with a major of one digit, so they never meet.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Relay version-mismatch detection")
class RelayVersionCheckTest {

    /** Contract literal: the error text of the simulated refusal of the audit sink. */
    private static final String NOT_WRITABLE = "audit log not writable";

    private final String runtimeVersion = version(10, 99);
    private final String oldRelay = version(0, 4);
    private final String otherRelay = version(5, 9);
    private final List<AuditEvent> records = new CopyOnWriteArrayList<>();
    private final RelayVersionCheck check = new RelayVersionCheck(runtimeVersion, records::add);

    /** @return a generated version {@code major.minor.patch} with a major in the given range */
    private static String version(int lowestMajor, int highestMajor) {
        return "%d.%d.%d".formatted(Generators.integers(lowestMajor, highestMajor).next(),
                Generators.integers(0, 99).next(), Generators.integers(0, 99).next());
    }

    private AuditEvent expectedRecord(String relayVersion) {
        return new AuditEvent(RelayVersionCheck.RELAY_VERSION_MISMATCH, RelayVersionCheck.CATEGORY_OPERATOR,
                RelayVersionCheck.OUTCOME_WARNING, Map.of(RelayVersionCheck.DETAIL_RELAY_VERSION, relayVersion,
                        RelayVersionCheck.DETAIL_RUNTIME_VERSION, runtimeVersion));
    }

    private static int warnings(String text) {
        return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN, text).size();
    }

    @Test
    @DisplayName("the header is named PM-MCP-Relay-Version (the specified header name)")
    void shouldNameHeader() {
        assertEquals("PM-MCP-Relay-Version", RelayVersionCheck.HEADER, "the header must carry its specified name");
    }

    @Nested
    @DisplayName("without a mismatch")
    class WithoutMismatch {

        @Test
        @DisplayName("an equal version records nothing and sets no status data")
        void shouldIgnoreEqualVersion() {
            check.observe(runtimeVersion);

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record must be written"),
                    () -> assertEquals(Optional.empty(), check.mismatch(), "an equal version sets no status data"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RelayVersionCheck.class));
        }

        @ParameterizedTest(name = "header [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("a missing or blank header records nothing and sets no status data (the blank forms are the cases)")
        void shouldIgnoreMissingOrBlankHeader(String header) {
            check.observe(header);

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record must be written"),
                    () -> assertEquals(Optional.empty(), check.mismatch(), "a blank header sets no status data"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RelayVersionCheck.class));
        }

        @Test
        @DisplayName("an equal version surrounded by whitespace is equal")
        void shouldStripHeader() {
            check.observe(" " + runtimeVersion + " ");

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record must be written"),
                    () -> assertEquals(Optional.empty(), check.mismatch(),
                            "a stripped equal version sets no status data"));
        }
    }

    @Nested
    @DisplayName("on a mismatch")
    class OnMismatch {

        @Test
        @DisplayName("a different version records one audit record with both versions and sets the status data")
        void shouldRecordMismatch() {
            check.observe(oldRelay);

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(oldRelay)), records,
                            "one audit record must carry both versions"),
                    // Contract literal: the recommended command of the specification
                    () -> assertEquals(Optional.of(new RelayVersionCheck.Mismatch(oldRelay, runtimeVersion,
                            "pm-operator drain")), check.mismatch(), "the mismatch must be the status data"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.RELAY_VERSION_MISMATCH.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Relay version '" + oldRelay + "' differs from runtime version '"
                                    + runtimeVersion + "', recommended: pm-operator drain"));
        }

        @Test
        @DisplayName("the same mismatch observed many times from several threads records once")
        void shouldRecordOncePerVersionUnderConcurrency() throws Exception {
            var start = new CountDownLatch(1);
            var observations = Generators.integers(20, 200).next();
            try (var executor = Executors.newFixedThreadPool(8)) {
                for (var i = 0; i < observations; i++) {
                    executor.execute(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        check.observe(oldRelay);
                    });
                }
                start.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "the observing threads ended");
            }

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(oldRelay)), records,
                            "the mismatch must be recorded once"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.RELAY_VERSION_MISMATCH.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Relay version '" + oldRelay + "'"));
        }

        @Test
        @DisplayName("a second distinct relay version records again and becomes the latest mismatch")
        void shouldRecordEachDistinctVersion() {
            check.observe(oldRelay);
            check.observe(otherRelay);
            check.observe(oldRelay);

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(oldRelay), expectedRecord(otherRelay)), records,
                            "each distinct version must be recorded once"),
                    () -> assertEquals(Optional.of(new RelayVersionCheck.Mismatch(oldRelay, runtimeVersion,
                                    RelayVersionCheck.RECOMMENDED_COMMAND)), check.mismatch(),
                            "the latest mismatch must be the status data"));
        }

        @Test
        @DisplayName("a record the audit sink refused is handed over again by a request after the retry interval")
        void shouldRetryRefusedRecord() {
            var refuse = new AtomicBoolean(true);
            var accepted = new CopyOnWriteArrayList<AuditEvent>();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(runtimeVersion, event -> {
                if (refuse.getAndSet(false)) {
                    throw new UncheckedIOException(new IOException(NOT_WRITABLE));
                }
                accepted.add(event);
            }, clock);

            refusing.observe(oldRelay);
            var afterRefusal = List.copyOf(accepted);
            refusing.observe(oldRelay);
            var withinInterval = List.copyOf(accepted);
            clock.advance(RelayVersionCheck.RETRY_INTERVAL);
            refusing.observe(oldRelay);
            refusing.observe(oldRelay);

            assertAll(
                    () -> assertEquals(List.of(), afterRefusal, "nothing recorded while refused"),
                    () -> assertEquals(List.of(), withinInterval, "no retry within the interval"),
                    () -> assertEquals(List.of(expectedRecord(oldRelay)), accepted, "recorded once afterwards"),
                    () -> assertEquals(oldRelay, refusing.mismatch().orElseThrow().relayVersion(),
                            "the mismatch must name the relay version"));
        }

        @Test
        @DisplayName("a persistently refusing audit sink gets one attempt per retry interval, not one per request")
        void shouldLimitAttemptsOfRefusingSink() {
            var attempts = new AtomicInteger();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(runtimeVersion, _ -> {
                attempts.incrementAndGet();
                throw new UncheckedIOException(new IOException(NOT_WRITABLE));
            }, clock);
            var requests = Generators.integers(2, 200).next();

            for (var i = 0; i < requests; i++) {
                refusing.observe(oldRelay);
            }
            var inFirstInterval = attempts.get();
            clock.advance(RelayVersionCheck.RETRY_INTERVAL.minusSeconds(1));
            refusing.observe(oldRelay);
            var justBeforeInterval = attempts.get();
            clock.advance(Duration.ofSeconds(1));
            for (var i = 0; i < requests; i++) {
                refusing.observe(oldRelay);
            }

            assertAll(
                    () -> assertEquals(1, inFirstInterval, "one attempt in the first interval"),
                    () -> assertEquals(1, justBeforeInterval, "no attempt just before the interval ends"),
                    () -> assertEquals(2, attempts.get(), "a second attempt after the interval"),
                    () -> assertEquals(2, warnings(WARN.RELAY_VERSION_MISMATCH.resolveIdentifierString()),
                            "the record must be logged once per attempt"),
                    () -> assertEquals(2, warnings("Relay version '" + oldRelay + "'"), "warned once per attempt"));
        }

        @Test
        @DisplayName("concurrent observers with a refusing audit sink attempt once and never lose the retry")
        void shouldNotLoseRetryUnderConcurrency() throws Exception {
            var attempts = new AtomicInteger();
            var accepted = new CopyOnWriteArrayList<AuditEvent>();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(runtimeVersion, event -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new UncheckedIOException(new IOException(NOT_WRITABLE));
                }
                accepted.add(event);
            }, clock);
            var start = new CountDownLatch(1);
            var observations = Generators.integers(20, 200).next();
            try (var executor = Executors.newFixedThreadPool(8)) {
                for (var i = 0; i < observations; i++) {
                    executor.execute(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        refusing.observe(oldRelay);
                    });
                }
                start.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "the observing threads ended");
            }
            var attemptsOfBurst = attempts.get();

            clock.advance(RelayVersionCheck.RETRY_INTERVAL);
            refusing.observe(oldRelay);

            assertAll(
                    () -> assertEquals(1, attemptsOfBurst, "one attempt for the whole burst"),
                    () -> assertEquals(List.of(expectedRecord(oldRelay)), accepted, "the retry is not lost"));
        }

        @Test
        @DisplayName("a flood of distinct versions records at most the bound, and the latest stays visible")
        void shouldBoundDistinctVersions() {
            var flood = RelayVersionCheck.MAX_RECORDED_VERSIONS + Generators.integers(1, 20).next();
            var prefix = "%d.%d.".formatted(Generators.integers(0, 9).next(), Generators.integers(0, 99).next());

            for (var i = 0; i < flood; i++) {
                check.observe(prefix + i);
            }

            assertAll(
                    () -> assertEquals(RelayVersionCheck.MAX_RECORDED_VERSIONS, records.size(),
                            "at most the bound of versions is recorded"),
                    () -> assertEquals(expectedRecord(prefix + (RelayVersionCheck.MAX_RECORDED_VERSIONS - 1)),
                            records.getLast(), "the last recorded version is the bound"),
                    () -> assertEquals(prefix + (flood - 1), check.mismatch().orElseThrow().relayVersion(),
                            "the latest version must stay visible"),
                    () -> assertEquals(RelayVersionCheck.MAX_RECORDED_VERSIONS,
                            warnings(WARN.RELAY_VERSION_MISMATCH.resolveIdentifierString()),
                            "each recorded version is logged once"));
        }

        @Test
        @DisplayName("a version longer than the bound is truncated to it")
        void shouldTruncateLongVersion() {
            var length = RelayVersionCheck.MAX_VERSION_LENGTH * 3;
            var longVersion = Generators.strings("0123456789", length, length).next();
            var truncated = longVersion.substring(0, RelayVersionCheck.MAX_VERSION_LENGTH);

            check.observe(longVersion);
            check.observe(longVersion + "-" + Generators.letterStrings(3, 12).next());

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(truncated)), records,
                            "the truncated version must be recorded once"),
                    () -> assertEquals(truncated, check.mismatch().orElseThrow().relayVersion(),
                            "the mismatch must carry the truncated version"));
        }
    }

    /** A clock the test moves forward by hand, from a generated instant. */
    private static final class MutableClock extends Clock {

        private volatile Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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
            return now;
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest(name = "runtime version [{0}]")
        @ValueSource(strings = {"", "  "})
        @DisplayName("a blank runtime version is refused (the blank forms are the cases)")
        void shouldRefuseBlankRuntimeVersion(String blank) {
            assertThrows(IllegalArgumentException.class, () -> new RelayVersionCheck(blank, records::add),
                    "a blank runtime version must be refused");
        }

        @Test
        @DisplayName("a missing runtime version or audit sink is refused")
        void shouldRefuseNull() {
            var command = Generators.letterStrings(3, 20).next();

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new RelayVersionCheck(null, records::add),
                            "a missing runtime version must be refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new RelayVersionCheck(runtimeVersion, null), "a missing audit sink must be refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new RelayVersionCheck.Mismatch(null, runtimeVersion, command),
                            "a mismatch without a relay version is refused"));
        }
    }
}
