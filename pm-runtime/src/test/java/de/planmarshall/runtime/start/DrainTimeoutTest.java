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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * The drain timeout read from {@code machine-config.json} (doc/specification/timeouts/02-runtime-leases-operator-web
 * .adoc, Drain timeout): 600 seconds by default, {@code 0} accepted, an invalid value falling back to the default
 * with a WARN log.
 */
@EnableTestLogger
@DisplayName("Drain timeout")
class DrainTimeoutTest {

    private static final String ABSENT = "<absent>";

    @TempDir
    Path base;

    @ParameterizedTest(name = "{0} -> {2} s, invalid logged: {3}")
    @CsvSource(delimiter = '|', textBlock = """
            absent file      | <absent>                                     | 600 | false
            absent key       | {"web": {"enabled": false}}                  | 600 | false
            zero             | {"drain_timeout_seconds": 0}                 | 0   | false
            the default      | {"drain_timeout_seconds": 600}               | 600 | false
            42               | {"web": {}, "drain_timeout_seconds": 42}     | 42  | false
            negative         | {"drain_timeout_seconds": -1}                | 600 | true
            fraction         | {"drain_timeout_seconds": 1.5}               | 600 | true
            string           | {"drain_timeout_seconds": "42"}              | 600 | true
            beyond a long    | {"drain_timeout_seconds": 99999999999999999999} | 600 | true
            the maximum      | {"drain_timeout_seconds": 3153600000}        | 3153600000 | false
            maximum plus one | {"drain_timeout_seconds": 3153600001}        | 600 | true
            the largest long | {"drain_timeout_seconds": 9223372036854775807} | 600 | true
            not an object    | [42]                                         | 600 | true
            unreadable JSON  | {"drain_timeout_seconds": 4                  | 600 | true
            trailing content | {"drain_timeout_seconds": 42} []             | 600 | true
            """)
    @DisplayName("reads drain_timeout_seconds or falls back to the default")
    void shouldReadDrainTimeout(String name, String document, long expectedSeconds, boolean invalid)
            throws Exception {
        if (!ABSENT.equals(document)) {
            Files.writeString(base.resolve(DrainTimeout.MACHINE_CONFIG), document);
        }

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(expectedSeconds), timeout, name);
        if (invalid) {
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    "drain_timeout_seconds in '%s' is invalid".formatted(base.resolve(DrainTimeout.MACHINE_CONFIG)));
        } else {
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, DrainTimeout.class);
        }
    }

    @Test
    @DisplayName("names a value beyond the maximum as too large and falls back to the default")
    void shouldRejectTooLargeValue() throws Exception {
        var tooLarge = DrainTimeout.MAX_SECONDS + 1;
        Files.writeString(base.resolve(DrainTimeout.MACHINE_CONFIG), "{\"drain_timeout_seconds\": %d}".formatted(tooLarge));

        var timeout = DrainTimeout.read(base);

        assertEquals(Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS), timeout);
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, "too large: " + tooLarge);
    }

    @Test
    @DisplayName("the maximum added to the current instant can be represented")
    void shouldKeepMaximumRepresentable() {
        var now = Instant.now();

        var deadline = now.plus(Duration.ofSeconds(DrainTimeout.MAX_SECONDS));

        assertTrue(deadline.isAfter(now));
    }
}
