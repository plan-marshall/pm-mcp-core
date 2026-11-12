/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.LimitProperties;

/**
 * The bounded read of a coverage report file: a report at or below the cap reads unchanged, one past it is refused
 * naming the cap, and both limits are data of {@code coverage-limits.properties}.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageReportStream")
class CoverageReportStreamTest {

    @Test
    @DisplayName("reads a report below the bound unchanged")
    void readsAReportBelowTheBound() throws IOException {
        byte[] report = text(1, 200);

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), report.length + 1L)) {
            assertArrayEquals(report, stream.readAllBytes(), "A report below the bound reads unchanged");
        }
    }

    @Test
    @DisplayName("reads a report exactly at the bound unchanged")
    void readsAReportExactlyAtTheBound() throws IOException {
        byte[] report = text(1, 200);

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), report.length)) {
            assertArrayEquals(report, stream.readAllBytes(), "A report at the bound reads unchanged");
        }
    }

    @Test
    @DisplayName("refuses a report past the bound, naming the limit")
    void refusesAReportPastTheBound() throws IOException {
        byte[] report = text(2, 200);
        long limit = report.length - 1L;

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), limit)) {
            IOException refusal = assertThrows(IOException.class, stream::readAllBytes, "A longer report is refused");
            assertTrue(refusal.getMessage().contains("limit of " + limit + " bytes"),
                    "The refusal names the limit in bytes");
        }
    }

    @Test
    @DisplayName("refuses a byte-wise read past the bound and every read after it")
    void refusesAByteWiseReadPastTheBound() throws IOException {
        byte[] report = text(2, 50);

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), report.length - 1L)) {
            for (int i = 0; i < report.length - 1; i++) {
                assertEquals(report[i], (byte) stream.read(), "Each byte below the bound is read");
            }
            assertAll(
                    () -> assertThrows(IOException.class, stream::read, "The byte past the bound is refused"),
                    () -> assertThrows(IOException.class, stream::read, "A read after the refusal is refused"),
                    () -> assertThrows(IOException.class, () -> stream.skip(1), "A skip after the refusal is refused"));
        }
    }

    @Test
    @DisplayName("counts skipped bytes against the bound")
    void countsSkippedBytesAgainstTheBound() throws IOException {
        byte[] report = text(2, 50);

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), report.length - 1L)) {
            assertThrows(IOException.class, () -> stream.skip(report.length), "Skipping past the bound is refused");
        }
    }

    @Test
    @DisplayName("ends a report at the bound with end of stream")
    void endsAReportAtTheBound() throws IOException {
        byte[] report = text(1, 50);

        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(report), report.length)) {
            assertEquals(report.length, stream.skip(report.length), "Every byte of the report is skipped");
            assertEquals(-1, stream.read(), "The stream ends after the last byte");
        }
    }

    @Test
    @DisplayName("supports no mark, so the count cannot run behind the bytes read")
    void supportsNoMark() throws IOException {
        try (InputStream stream = new CoverageReportStream(new ByteArrayInputStream(text(1, 20)))) {
            assertFalse(stream.markSupported(), "The stream offers no mark");
            assertThrows(IOException.class, stream::reset, "A reset is refused without a mark");
        }
    }

    // The two values are the contract: 256 MiB and 8192 characters, read from the resource, never a literal default.
    @Test
    @DisplayName("reads the contract limits 268435456 bytes and 8192 characters from the resource")
    void readsTheLimitsFromTheResource() {
        assertAll(
                () -> assertEquals(268_435_456L, CoverageReportStream.MAX_BYTES, "The byte cap is 256 MiB"),
                () -> assertEquals(8192, CoverageReportStream.MAX_LINE_LENGTH, "The line limit is 8192 characters"),
                () -> assertEquals(LimitProperties.read(CoverageReportStream.LIMITS, CoverageReportStream.MAX_BYTES_KEY),
                        CoverageReportStream.MAX_BYTES, "The byte cap comes from the resource"),
                () -> assertEquals(LimitProperties.read(CoverageReportStream.LIMITS,
                                CoverageReportStream.MAX_LINE_LENGTH_KEY),
                        (long) CoverageReportStream.MAX_LINE_LENGTH, "The line limit comes from the resource"));
    }

    private static byte[] text(int minimum, int maximum) {
        return Generators.letterStrings(minimum, maximum).next().getBytes(StandardCharsets.UTF_8);
    }
}
