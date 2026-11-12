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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.LimitProperties;
import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobLog.Complete;
import de.planmarshall.runtime.job.JobLog.Lines;
import de.planmarshall.runtime.job.JobLog.Unavailable;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The job log (PM-EXEC-4): it is read while the job still writes it, a read continues at the offset the previous read
 * answered, the launch report is stripped exactly once, and a log that cannot be read is one of the
 * states of row 5 of the outcome derivation, never an exception.
 * <p>
 * Contract literals that stay: the wire format of the launch report (its member names, the confinement
 * {@code unavailable} and the null Landlock ABI). The PID and the start instant of the report, the job id of the log
 * file and every output line are generated.
 */
@EnableGeneratorController
@DisplayName("Job log")
class JobLogTest {

    @TempDir
    Path directory;

    private Path path;
    private JobLog log;
    private long pid;
    private String report;

    @BeforeEach
    void createLog() {
        path = directory.resolve(new JobId(RuntimeGenerators.jobIds(JobId.Kind.BUILD).next()).logFileName());
        log = new JobLog(path);
        pid = Generators.integers(2, 4_000_000).next();
        var startedAt = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
        report = "{\"pid\":" + pid + ",\"process_started_at\":\"" + startedAt + "\","
                + "\"confinement\":\"unavailable\",\"landlock_abi\":null}";
    }

    private void append(String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** @return a generated output line without a line break */
    private static String line() {
        return Generators.letterStrings(1, 32).next();
    }

    @Nested
    @DisplayName("while the job runs")
    class WhileRunning {

        @Test
        @DisplayName("answers the complete lines written so far and continues at the offset after them")
        void readsWhileAppending() throws Exception {
            var firstLine = line();
            var secondLine = line() + line();
            var split = secondLine.length() / 2;
            var thirdLine = line();
            append(report + "\n" + firstLine + "\n" + secondLine.substring(0, split));

            var first = assertInstanceOf(Lines.class, log.readLines(0), "the first read answers lines");
            append(secondLine.substring(split) + "\n" + thirdLine + "\n");
            var second = assertInstanceOf(Lines.class, log.readLines(first.nextOffset()),
                    "the second read answers lines");
            var third = assertInstanceOf(Lines.class, log.readLines(second.nextOffset()),
                    "the third read answers lines");

            assertAll(
                    () -> assertEquals(pid, first.report().pid(), "the report's PID is read"),
                    () -> assertEquals(List.of(firstLine), first.lines(), "the first read holds the complete line"),
                    () -> assertEquals((report + "\n" + firstLine + "\n").getBytes(StandardCharsets.UTF_8).length,
                            first.nextOffset(), "the offset follows the complete line"),
                    () -> assertEquals(List.of(secondLine, thirdLine), second.lines(),
                            "the second read joins the split line"),
                    () -> assertNull(second.report(), "a later read carries no report"),
                    () -> assertEquals(Files.size(path), second.nextOffset(), "the second read reaches the end"),
                    () -> assertEquals(List.of(), third.lines(), "a read at the end answers no line"),
                    () -> assertEquals(second.nextOffset(), third.nextOffset(), "a read at the end keeps the offset"));
        }

        @Test
        @DisplayName("strips the report once: a report-shaped job line read from a later offset is kept")
        void reportStrippedOnce() throws Exception {
            append(report + "\n");
            var first = assertInstanceOf(Lines.class, log.readLines(0), "the first read answers lines");
            append(report + "\n");

            var second = assertInstanceOf(Lines.class, log.readLines(first.nextOffset()),
                    "the second read answers lines");

            assertAll(
                    () -> assertNotNull(first.report(), "the first read strips the report"),
                    () -> assertEquals(List.of(), first.lines(), "the report is no line of the job"),
                    () -> assertEquals(List.of(report), second.lines(), "a later report-shaped line is kept"),
                    () -> assertNull(second.report(), "a later read strips no report"));
        }

        @Test
        @DisplayName("keeps a first line that is no report, and waits for an incomplete first line")
        void noReport() throws Exception {
            var start = line();
            var end = line();
            append(start);
            var incomplete = assertInstanceOf(Lines.class, log.readLines(0), "an incomplete read answers lines");
            append(end + "\n");

            var complete = assertInstanceOf(Lines.class, log.readLines(0), "a complete read answers lines");

            assertAll(
                    () -> assertEquals(List.of(), incomplete.lines(), "an incomplete line is not answered"),
                    () -> assertEquals(0, incomplete.nextOffset(), "an incomplete line keeps the offset"),
                    () -> assertEquals(List.of(start + end), complete.lines(), "the completed line is answered"),
                    () -> assertNull(complete.report(), "a first line that is no report is kept"));
        }

        @Test
        @DisplayName("is missing before the launcher created it, and truncated when shorter than the offset")
        void missingAndTruncated() throws Exception {
            var missing = assertInstanceOf(Unavailable.class, log.readLines(0), "a missing log is unavailable");
            var written = line() + "\n";
            append(written);

            var truncated = assertInstanceOf(Unavailable.class,
                    log.readLines(written.length() + Generators.integers(1, 1000).next()),
                    "a read beyond the end is unavailable");

            assertAll(
                    () -> assertEquals(LogState.MISSING, missing.state(), "the job log file is missing"),
                    () -> assertEquals(LogState.TRUNCATED, truncated.state(), "the log is truncated"),
                    () -> assertThrows(IllegalArgumentException.class, () -> log.readLines(-1),
                            "a negative offset is refused"));
        }
    }

    @Nested
    @DisplayName("when the job has ended")
    class Ended {

        @Test
        @DisplayName("readable: the report and the job's output after it")
        void readable() throws Exception {
            var output = line() + "\n";
            append(report + "\n" + output);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "a readable log is complete");

            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading(),
                    "the complete log carries the report");
            assertAll(
                    () -> assertEquals(output, reading.jobOutput(), "the job output follows the report"),
                    () -> assertEquals(Files.size(path), whole.length(), "the length is the size of the log"));
        }

        @Test
        @DisplayName("missing: the log does not exist")
        void missing() {
            var whole = assertInstanceOf(Unavailable.class, log.readWhole(0), "a missing log is unavailable");

            assertEquals(LogState.MISSING, whole.state(), "the ended log is missing");
        }

        @Test
        @DisplayName("at the cap: the whole log is answered, never a cut")
        void atTheCap() throws Exception {
            var marker = "last line " + line();
            var filler = "x".repeat(JobLog.MAX_BYTES - report.length() - marker.length() - 3);
            Files.writeString(path, report + "\n" + filler + "\n" + marker + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "a log at the cap is complete");
            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading(), "the launch report is read");

            assertAll(
                    () -> assertEquals(JobLog.MAX_BYTES, Files.size(path), "the log is exactly at the cap"),
                    () -> assertEquals(Files.size(path), whole.length(), "the length is the size of the log"),
                    () -> assertEquals(filler + "\n" + marker + "\n", reading.jobOutput(),
                            "the whole log follows the report, uncut"));
        }

        @Test
        @DisplayName("over the cap: the first line and the last bytes, never truncated")
        void overTheCap() throws Exception {
            var middle = "middle marker " + line();
            var marker = "tail marker " + line();
            Files.writeString(path, report + "\n" + middle + "\n" + "x".repeat(JobLog.MAX_BYTES) + "\n" + marker
                    + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(Files.size(path)), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading(),
                    "the report survives the cut");

            assertAll(
                    () -> assertEquals(pid, reading.report().pid(), "the first line is the report"),
                    () -> assertEquals(Files.size(path), whole.length(), "the length is the real file length"),
                    () -> assertTrue(reading.jobOutput().contains(marker), "the tail of the log is read"),
                    () -> assertFalse(reading.jobOutput().contains(middle), "the middle of the log is cut"),
                    () -> assertTrue(reading.jobOutput().startsWith(marker), "the cut starts at a line beginning"));
        }

        @Test
        @DisplayName("the cap is read from the module's limits resource")
        void capIsReadFromTheLimitsResource() {
            assertEquals(JobLog.MAX_BYTES, LimitProperties.read(JobLog.LIMITS, JobLog.MAX_BYTES_KEY),
                    "the cap must come from the resource");
        }

        @Test
        @DisplayName("truncated: the log is shorter than what was already read from it")
        void truncated() throws Exception {
            append(report + "\n" + line() + "\n");
            var read = assertInstanceOf(Lines.class, log.readLines(0), "the first read answers lines");
            Files.writeString(path, report + "\n");

            var whole = assertInstanceOf(Unavailable.class, log.readWhole(read.nextOffset()),
                    "a shortened log is unavailable");

            assertAll(
                    () -> assertEquals(LogState.TRUNCATED, whole.state(), "the shortened log is truncated"),
                    () -> assertFalse(whole.detail().isBlank(), "the truncation carries a detail"));
        }

        @Test
        @DisplayName("unreadable: the log exists and cannot be read")
        void unreadable() throws Exception {
            Files.createDirectory(path);

            var whole = assertInstanceOf(Unavailable.class, log.readWhole(0), "a directory log is unavailable");
            var lines = assertInstanceOf(Unavailable.class, log.readLines(0), "a directory log has no lines");
            var detail = line();

            assertAll(
                    () -> assertEquals(LogState.UNREADABLE, whole.state(), "the whole read is unreadable"),
                    () -> assertEquals(LogState.UNREADABLE, lines.state(), "the line read is unreadable"),
                    () -> assertThrows(IllegalArgumentException.class, () -> log.readWhole(-1),
                            "a negative offset is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Unavailable(LogState.READABLE, detail),
                            "a readable state is no unavailability"));
        }
    }
}
