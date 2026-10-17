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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobLog.Complete;
import de.planmarshall.runtime.job.JobLog.Lines;
import de.planmarshall.runtime.job.JobLog.Unavailable;

/**
 * The job log (PM-EXEC-4): it is read while the job still writes it, a read continues at the offset the previous read
 * answered, the launch report is stripped exactly once, and a log that cannot be read is one of the
 * states of row 5 of the outcome derivation, never an exception.
 */
@DisplayName("Job log")
class JobLogTest {

    private static final String REPORT = "{\"pid\":4242,\"process_started_at\":\"2026-10-17T10:00:05Z\","
            + "\"confinement\":\"unavailable\",\"landlock_abi\":null}";

    @TempDir
    Path directory;

    private Path path;
    private JobLog log;

    @BeforeEach
    void createLog() {
        path = directory.resolve("b-0123456789abcdef.log");
        log = new JobLog(path);
    }

    private void append(String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Nested
    @DisplayName("while the job runs")
    class WhileRunning {

        @Test
        @DisplayName("answers the complete lines written so far and continues at the offset after them")
        void readsWhileAppending() throws Exception {
            append(REPORT + "\n[INFO] first\n[INFO] sec");

            var first = assertInstanceOf(Lines.class, log.readLines(0));
            append("ond\n[INFO] third\n");
            var second = assertInstanceOf(Lines.class, log.readLines(first.nextOffset()));
            var third = assertInstanceOf(Lines.class, log.readLines(second.nextOffset()));

            assertAll(
                    () -> assertEquals(4242, first.report().orElseThrow().pid()),
                    () -> assertEquals(List.of("[INFO] first"), first.lines()),
                    () -> assertEquals((REPORT + "\n[INFO] first\n").getBytes(StandardCharsets.UTF_8).length,
                            first.nextOffset()),
                    () -> assertEquals(List.of("[INFO] second", "[INFO] third"), second.lines()),
                    () -> assertTrue(second.report().isEmpty()),
                    () -> assertEquals(Files.size(path), second.nextOffset()),
                    () -> assertEquals(List.of(), third.lines()),
                    () -> assertEquals(second.nextOffset(), third.nextOffset()));
        }

        @Test
        @DisplayName("strips the report once: a report-shaped job line read from a later offset is kept")
        void reportStrippedOnce() throws Exception {
            append(REPORT + "\n");
            var first = assertInstanceOf(Lines.class, log.readLines(0));
            append(REPORT + "\n");

            var second = assertInstanceOf(Lines.class, log.readLines(first.nextOffset()));

            assertAll(
                    () -> assertTrue(first.report().isPresent()),
                    () -> assertEquals(List.of(), first.lines()),
                    () -> assertEquals(List.of(REPORT), second.lines()),
                    () -> assertTrue(second.report().isEmpty()));
        }

        @Test
        @DisplayName("keeps a first line that is no report, and waits for an incomplete first line")
        void noReport() throws Exception {
            append("pm-exec: usage");
            var incomplete = assertInstanceOf(Lines.class, log.readLines(0));
            append(" error\n");

            var complete = assertInstanceOf(Lines.class, log.readLines(0));

            assertAll(
                    () -> assertEquals(List.of(), incomplete.lines()),
                    () -> assertEquals(0, incomplete.nextOffset()),
                    () -> assertEquals(List.of("pm-exec: usage error"), complete.lines()),
                    () -> assertTrue(complete.report().isEmpty()));
        }

        @Test
        @DisplayName("is missing before the launcher created it, and truncated when shorter than the offset")
        void missingAndTruncated() throws Exception {
            var missing = assertInstanceOf(Unavailable.class, log.readLines(0));
            append("short\n");

            var truncated = assertInstanceOf(Unavailable.class, log.readLines(100));

            assertAll(
                    () -> assertEquals(LogState.MISSING, missing.state()),
                    () -> assertEquals(LogState.TRUNCATED, truncated.state()),
                    () -> assertThrows(IllegalArgumentException.class, () -> log.readLines(-1)));
        }
    }

    @Nested
    @DisplayName("when the job has ended")
    class Ended {

        @Test
        @DisplayName("readable: the report and the job's output after it")
        void readable() throws Exception {
            append(REPORT + "\n[INFO] BUILD SUCCESS\n");

            var whole = assertInstanceOf(Complete.class, log.readWhole(0));

            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading());
            assertAll(
                    () -> assertEquals("[INFO] BUILD SUCCESS\n", reading.jobOutput()),
                    () -> assertEquals(Files.size(path), whole.length()));
        }

        @Test
        @DisplayName("missing: the log does not exist")
        void missing() {
            var whole = assertInstanceOf(Unavailable.class, log.readWhole(0));

            assertEquals(LogState.MISSING, whole.state());
        }

        @Test
        @DisplayName("truncated: the log is shorter than what was already read from it")
        void truncated() throws Exception {
            append(REPORT + "\n[INFO] first\n");
            var read = assertInstanceOf(Lines.class, log.readLines(0));
            Files.writeString(path, REPORT + "\n");

            var whole = assertInstanceOf(Unavailable.class, log.readWhole(read.nextOffset()));

            assertAll(
                    () -> assertEquals(LogState.TRUNCATED, whole.state()),
                    () -> assertFalse(whole.detail().isBlank()));
        }

        @Test
        @DisplayName("unreadable: the log exists and cannot be read")
        void unreadable() throws Exception {
            Files.createDirectory(path);

            var whole = assertInstanceOf(Unavailable.class, log.readWhole(0));
            var lines = assertInstanceOf(Unavailable.class, log.readLines(0));

            assertAll(
                    () -> assertEquals(LogState.UNREADABLE, whole.state()),
                    () -> assertEquals(LogState.UNREADABLE, lines.state()),
                    () -> assertThrows(IllegalArgumentException.class, () -> log.readWhole(-1)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Unavailable(LogState.READABLE, "readable")));
        }
    }
}
