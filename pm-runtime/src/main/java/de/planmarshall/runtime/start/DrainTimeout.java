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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import lombok.experimental.UtilityClass;

import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The drain timeout: {@code drain_timeout_seconds} of {@code <PM_MCP_BASE>/machine-config.json}
 * (doc/specification/timeouts/02-runtime-leases-operator-web.adoc, Drain timeout).
 * <p>
 * The default of {@value #DEFAULT_SECONDS} seconds applies when the file or the key is absent. The minimum is
 * {@code 0}, which terminates running jobs at once; the maximum is {@value #MAX_SECONDS} seconds (100 years of 365
 * days), so the deadline of the drain, the current instant plus the timeout, can always be represented. A negative,
 * non-integer or larger value, and a file that cannot be read or does not parse as a JSON object, falls back to the
 * default and is logged as invalid; the start and the drain never fail for it.
 *
 * @since 0.1
 */
@UtilityClass
public class DrainTimeout {

    private static final CuiLogger LOGGER = new CuiLogger(DrainTimeout.class);

    /** The drain timeout when none or an invalid one is configured, in seconds. */
    public static final long DEFAULT_SECONDS = 600;

    /**
     * The largest accepted drain timeout, in seconds: 100 years of 365 days. A larger value is invalid, because its
     * deadline could lie beyond the instants that can be represented.
     */
    public static final long MAX_SECONDS = 3_153_600_000L;

    /** The key of the drain timeout in {@code machine-config.json}. */
    public static final String KEY = "drain_timeout_seconds";

    /** The name of the machine configuration file below the base. */
    static final String MACHINE_CONFIG = "machine-config.json";

    private static final JsonFactory JSON = new JsonFactory();

    /**
     * Reads the drain timeout of a base.
     *
     * @param base the {@code PM_MCP_BASE}
     * @return the drain timeout, never negative
     */
    public static Duration read(Path base) {
        var file = base.resolve(MACHINE_CONFIG);
        byte[] document;
        try {
            document = Files.readAllBytes(file);
        } catch (NoSuchFileException _) {
            return Duration.ofSeconds(DEFAULT_SECONDS);
        } catch (IOException e) {
            return invalid(file, "not readable: " + e.getMessage());
        }
        // The whole document is parsed before the value counts: a truncated file is invalid, not a short timeout
        String rejected = null;
        Long seconds = null;
        try (var parser = JSON.createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return invalid(file, "not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                parser.nextToken();
                if (KEY.equals(field)) {
                    rejected = rejection(parser);
                    seconds = rejected == null ? parser.getLongValue() : null;
                }
                parser.skipChildren();
            }
            if (parser.nextToken() != null) {
                return invalid(file, "content after the JSON object");
            }
        } catch (IOException _) {
            return invalid(file, "does not parse as JSON");
        }
        if (rejected != null) {
            return invalid(file, rejected);
        }
        return Duration.ofSeconds(seconds == null ? DEFAULT_SECONDS : seconds);
    }

    /** @return why the value of the key is not a drain timeout, or {@code null} when it is one */
    private static String rejection(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                || parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER) {
            return "not an integer: " + parser.getText();
        }
        if (parser.getLongValue() < 0) {
            return "negative: " + parser.getLongValue();
        }
        if (parser.getLongValue() > MAX_SECONDS) {
            return "too large: " + parser.getLongValue();
        }
        return null;
    }

    private static Duration invalid(Path file, String reason) {
        LOGGER.warn(WARN.DRAIN_TIMEOUT_INVALID, file, reason, DEFAULT_SECONDS);
        return Duration.ofSeconds(DEFAULT_SECONDS);
    }
}
