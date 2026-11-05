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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The drain timeout read from {@code machine-config.json} (doc/specification/timeouts/02-runtime-leases-operator-web
 * .adoc, Drain timeout): 600 seconds by default, {@code 0} accepted, an invalid value falling back to the default
 * with a WARN log. The documents of the table are the specified wire format and its boundaries; an ordinary value is
 * generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Drain timeout")
class DrainTimeoutTest {

    private static final String ABSENT = "<absent>";

    @TempDir
    Path base;

    private Path machineConfig() {
        return base.resolve(DrainTimeout.MACHINE_CONFIG);
    }

    private void assertInvalidLogged() {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.DRAIN_TIMEOUT_INVALID.resolveIdentifierString());
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                "drain_timeout_seconds in '%s' is invalid".formatted(machineConfig()));
    }

    @ParameterizedTest(name = "{0} -> {2} s, invalid logged: {3}")
    @CsvSource(delimiter = '|', textBlock = """
            absent file      | <absent>                                     | 600 | false
            absent key       | {"web": {"enabled": false}}                  | 600 | false
            zero             | {"drain_timeout_seconds": 0}                 | 0   | false
            the default      | {"drain_timeout_seconds": 600}               | 600 | false
            negative         | {"drain_timeout_seconds": -1}                | 600 | true
            fraction         | {"drain_timeout_seconds": 1.5}               | 600 | true
            beyond a long    | {"drain_timeout_seconds": 99999999999999999999} | 600 | true
            the maximum      | {"drain_timeout_seconds": 3153600000}        | 3153600000 | false
            maximum plus one | {"drain_timeout_seconds": 3153600001}        | 600 | true
            the largest long | {"drain_timeout_seconds": 9223372036854775807} | 600 | true
            not an object    | [42]                                         | 600 | true
            unreadable JSON  | {"drain_timeout_seconds": 4                  | 600 | true
            trailing content | {"drain_timeout_seconds": 42} []             | 600 | true
            """)
    @DisplayName("reads drain_timeout_seconds or falls back to the default (wire-format boundary cases)")
    void shouldReadDrainTimeout(String name, String document, long expectedSeconds, boolean invalid)
            throws Exception {
        if (!ABSENT.equals(document)) {
            Files.writeString(machineConfig(), document);
        }

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(expectedSeconds), timeout, "the drain timeout of the case " + name);
        if (invalid) {
            assertInvalidLogged();
        } else {
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, DrainTimeout.class);
        }
    }

    @Test
    @DisplayName("reads a generated value beside another key of the machine configuration")
    void shouldReadGeneratedValue() throws Exception {
        var seconds = Generators.longs(1, DrainTimeout.MAX_SECONDS).next();
        Files.writeString(machineConfig(), "{\"web\": {}, \"drain_timeout_seconds\": %d}".formatted(seconds));

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(seconds), timeout, "the generated drain timeout must be read");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, DrainTimeout.class);
    }

    @Test
    @DisplayName("refuses a generated value written as a JSON string and falls back to the default")
    void shouldRejectStringValue() throws Exception {
        var seconds = Generators.longs(1, DrainTimeout.MAX_SECONDS).next();
        Files.writeString(machineConfig(), "{\"drain_timeout_seconds\": \"%d\"}".formatted(seconds));

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS), timeout,
                "a string value must fall back to the default");
        assertInvalidLogged();
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, "not an integer: " + seconds);
    }

    @Test
    @DisplayName("names a value beyond the maximum as too large and falls back to the default")
    void shouldRejectTooLargeValue() throws Exception {
        var tooLarge = DrainTimeout.MAX_SECONDS + Generators.longs(1, 1_000_000).next();
        Files.writeString(machineConfig(), "{\"drain_timeout_seconds\": %d}".formatted(tooLarge));

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS), timeout,
                "a too large value must fall back to the default");
        assertInvalidLogged();
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, "too large: " + tooLarge);
    }

    @Test
    @DisplayName("names a generated negative value as negative and falls back to the default")
    void shouldRejectNegativeValue() throws Exception {
        var negative = -Generators.longs(1, DrainTimeout.MAX_SECONDS).next();
        Files.writeString(machineConfig(), "{\"drain_timeout_seconds\": %d}".formatted(negative));

        var timeout = DrainTimeout.read(base);

        assertAll(
                () -> assertEquals(Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS), timeout,
                        "a negative value must fall back to the default"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, "negative: " + negative));
        assertInvalidLogged();
    }

    @Test
    @DisplayName("the maximum added to the current instant can be represented")
    void shouldKeepMaximumRepresentable() {
        var now = Instant.now();

        var deadline = now.plus(Duration.ofSeconds(DrainTimeout.MAX_SECONDS));

        assertTrue(deadline.isAfter(now), "the deadline of the maximum must lie ahead");
    }
}
