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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.service.AuditEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnableTestLogger
@DisplayName("Relay version-mismatch detection")
class RelayVersionCheckTest {

    private static final String RUNTIME_VERSION = "0.4.0";
    private static final String OLD_RELAY = "0.3.2";
    private static final String OTHER_RELAY = "0.3.1";

    private final List<AuditEvent> records = new CopyOnWriteArrayList<>();
    private final RelayVersionCheck check = new RelayVersionCheck(RUNTIME_VERSION, records::add);

    private static AuditEvent expectedRecord(String relayVersion) {
        return new AuditEvent(RelayVersionCheck.RELAY_VERSION_MISMATCH, RelayVersionCheck.CATEGORY_OPERATOR,
                RelayVersionCheck.OUTCOME_WARNING, Map.of(RelayVersionCheck.DETAIL_RELAY_VERSION, relayVersion,
                        RelayVersionCheck.DETAIL_RUNTIME_VERSION, RUNTIME_VERSION));
    }

    @Test
    @DisplayName("the header is named PM-MCP-Relay-Version")
    void shouldNameHeader() {
        assertEquals("PM-MCP-Relay-Version", RelayVersionCheck.HEADER);
    }

    @Nested
    @DisplayName("without a mismatch")
    class WithoutMismatch {

        @Test
        @DisplayName("an equal version records nothing and sets no status data")
        void shouldIgnoreEqualVersion() {
            check.observe(RUNTIME_VERSION);

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record"),
                    () -> assertEquals(Optional.empty(), check.mismatch()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RelayVersionCheck.class));
        }

        @ParameterizedTest(name = "header [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("a missing or blank header records nothing and sets no status data")
        void shouldIgnoreMissingOrBlankHeader(String header) {
            check.observe(header);

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record"),
                    () -> assertEquals(Optional.empty(), check.mismatch()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RelayVersionCheck.class));
        }

        @Test
        @DisplayName("an equal version surrounded by whitespace is equal")
        void shouldStripHeader() {
            check.observe(" " + RUNTIME_VERSION + " ");

            assertAll(
                    () -> assertTrue(records.isEmpty(), "no audit record"),
                    () -> assertEquals(Optional.empty(), check.mismatch()));
        }
    }

    @Nested
    @DisplayName("on a mismatch")
    class OnMismatch {

        @Test
        @DisplayName("a different version records one audit record with both versions and sets the status data")
        void shouldRecordMismatch() {
            check.observe(OLD_RELAY);

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(OLD_RELAY)), records),
                    () -> assertEquals(Optional.of(new RelayVersionCheck.Mismatch(OLD_RELAY, RUNTIME_VERSION,
                            "pm-operator drain")), check.mismatch()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Relay version '" + OLD_RELAY + "' differs from runtime version '"
                                    + RUNTIME_VERSION + "', recommended: pm-operator drain"));
        }

        @Test
        @DisplayName("the same mismatch observed 100 times from several threads records once")
        void shouldRecordOncePerVersionUnderConcurrency() throws Exception {
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(8)) {
                for (var i = 0; i < 100; i++) {
                    executor.execute(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        check.observe(OLD_RELAY);
                    });
                }
                start.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "the observing threads ended");
            }

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(OLD_RELAY)), records),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Relay version '" + OLD_RELAY + "'"));
        }

        @Test
        @DisplayName("a second distinct relay version records again and becomes the latest mismatch")
        void shouldRecordEachDistinctVersion() {
            check.observe(OLD_RELAY);
            check.observe(OTHER_RELAY);
            check.observe(OLD_RELAY);

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(OLD_RELAY), expectedRecord(OTHER_RELAY)), records),
                    () -> assertEquals(Optional.of(new RelayVersionCheck.Mismatch(OLD_RELAY, RUNTIME_VERSION,
                            RelayVersionCheck.RECOMMENDED_COMMAND)), check.mismatch(), "the latest mismatch"));
        }

        @Test
        @DisplayName("a record the audit sink refused is handed over again by a request after the retry interval")
        void shouldRetryRefusedRecord() {
            var refuse = new AtomicBoolean(true);
            var accepted = new CopyOnWriteArrayList<AuditEvent>();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(RUNTIME_VERSION, event -> {
                if (refuse.getAndSet(false)) {
                    throw new UncheckedIOException(new IOException("audit log not writable"));
                }
                accepted.add(event);
            }, clock);

            refusing.observe(OLD_RELAY);
            var afterRefusal = List.copyOf(accepted);
            refusing.observe(OLD_RELAY);
            var withinInterval = List.copyOf(accepted);
            clock.advance(RelayVersionCheck.RETRY_INTERVAL);
            refusing.observe(OLD_RELAY);
            refusing.observe(OLD_RELAY);

            assertAll(
                    () -> assertEquals(List.of(), afterRefusal, "nothing recorded while refused"),
                    () -> assertEquals(List.of(), withinInterval, "no retry within the interval"),
                    () -> assertEquals(List.of(expectedRecord(OLD_RELAY)), accepted, "recorded once afterwards"),
                    () -> assertEquals(OLD_RELAY, refusing.mismatch().orElseThrow().relayVersion()));
        }

        @Test
        @DisplayName("a persistently refusing audit sink gets one attempt per retry interval, not one per request")
        void shouldLimitAttemptsOfRefusingSink() {
            var attempts = new AtomicInteger();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(RUNTIME_VERSION, _ -> {
                attempts.incrementAndGet();
                throw new UncheckedIOException(new IOException("audit log not writable"));
            }, clock);

            for (var i = 0; i < 100; i++) {
                refusing.observe(OLD_RELAY);
            }
            var inFirstInterval = attempts.get();
            clock.advance(RelayVersionCheck.RETRY_INTERVAL.minusSeconds(1));
            refusing.observe(OLD_RELAY);
            var justBeforeInterval = attempts.get();
            clock.advance(Duration.ofSeconds(1));
            for (var i = 0; i < 100; i++) {
                refusing.observe(OLD_RELAY);
            }

            assertAll(
                    () -> assertEquals(1, inFirstInterval),
                    () -> assertEquals(1, justBeforeInterval),
                    () -> assertEquals(2, attempts.get()),
                    () -> assertEquals(2, TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(
                            TestLogLevel.WARN,
                            "Relay version '" + OLD_RELAY + "'").size(), "warned once per attempt"));
        }

        @Test
        @DisplayName("concurrent observers with a refusing audit sink attempt once and never lose the retry")
        void shouldNotLoseRetryUnderConcurrency() throws Exception {
            var attempts = new AtomicInteger();
            var accepted = new CopyOnWriteArrayList<AuditEvent>();
            var clock = new MutableClock();
            var refusing = new RelayVersionCheck(RUNTIME_VERSION, event -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new UncheckedIOException(new IOException("audit log not writable"));
                }
                accepted.add(event);
            }, clock);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(8)) {
                for (var i = 0; i < 100; i++) {
                    executor.execute(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        refusing.observe(OLD_RELAY);
                    });
                }
                start.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "the observing threads ended");
            }
            var attemptsOfBurst = attempts.get();

            clock.advance(RelayVersionCheck.RETRY_INTERVAL);
            refusing.observe(OLD_RELAY);

            assertAll(
                    () -> assertEquals(1, attemptsOfBurst, "one attempt for the whole burst"),
                    () -> assertEquals(List.of(expectedRecord(OLD_RELAY)), accepted, "the retry is not lost"));
        }

        @Test
        @DisplayName("a flood of distinct versions records at most the bound, and the latest stays visible")
        void shouldBoundDistinctVersions() {
            var flood = RelayVersionCheck.MAX_RECORDED_VERSIONS + 10;

            for (var i = 0; i < flood; i++) {
                check.observe("0.0." + i);
            }

            assertAll(
                    () -> assertEquals(RelayVersionCheck.MAX_RECORDED_VERSIONS, records.size()),
                    () -> assertEquals(expectedRecord("0.0." + (RelayVersionCheck.MAX_RECORDED_VERSIONS - 1)),
                            records.getLast()),
                    () -> assertEquals("0.0." + (flood - 1), check.mismatch().orElseThrow().relayVersion()),
                    () -> assertEquals(RelayVersionCheck.MAX_RECORDED_VERSIONS,
                            TestLoggerFactory.getTestHandler()
                                    .resolveLogMessagesContaining(TestLogLevel.WARN, "Relay version '").size()));
        }

        @Test
        @DisplayName("a version longer than the bound is truncated to it")
        void shouldTruncateLongVersion() {
            var longVersion = "9".repeat(RelayVersionCheck.MAX_VERSION_LENGTH * 3);
            var truncated = longVersion.substring(0, RelayVersionCheck.MAX_VERSION_LENGTH);

            check.observe(longVersion);
            check.observe(longVersion + "-other-suffix");

            assertAll(
                    () -> assertEquals(List.of(expectedRecord(truncated)), records),
                    () -> assertEquals(truncated, check.mismatch().orElseThrow().relayVersion()));
        }
    }

    /** A clock the test moves forward by hand. */
    private static final class MutableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-11T12:00:00Z");

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
        @DisplayName("a blank runtime version is refused")
        void shouldRefuseBlankRuntimeVersion(String runtimeVersion) {
            assertThrows(IllegalArgumentException.class, () -> new RelayVersionCheck(runtimeVersion, records::add));
        }

        @Test
        @DisplayName("a missing runtime version or audit sink is refused")
        void shouldRefuseNull() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new RelayVersionCheck(null, records::add)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new RelayVersionCheck(RUNTIME_VERSION, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new RelayVersionCheck.Mismatch(null, RUNTIME_VERSION, "x")));
        }
    }
}
