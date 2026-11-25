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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
import de.planmarshall.core.queue.SecretRedactor;
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
        @DisplayName("redacts every line the incremental read hands out")
        void redactsTheLinesItHandsOut() throws Exception {
            append(report + "\n" + "token=abcd1234\n");

            var lines = assertInstanceOf(Lines.class, log.readLines(0), "the read answers lines");

            assertAll(
                    () -> assertFalse(lines.lines().getFirst().contains("abcd1234"), "the line carries no secret"),
                    () -> assertTrue(lines.lines().getFirst().contains(SecretRedactor.REDACTED),
                            "the line keeps the redaction marker"));
        }

        @Test
        @DisplayName("masks a key block whose BEGIN line came from an earlier read")
        void masksAKeyBlockWindow() throws Exception {
            append(report + "\n-----END RSA PRIVATE KEY-----\nnext line\n");

            // the scenario is a resumed read: its offset lies inside the key body, so the tail rule applies
            var lines = assertInstanceOf(Lines.class, log.readLines((report + "\n").length()),
                    "the read answers lines");

            var text = String.join("\n", lines.lines());
            assertAll(
                    () -> assertFalse(text.contains("-----END RSA PRIVATE KEY-----"),
                            "the interior block is masked"),
                    () -> assertTrue(text.contains(SecretRedactor.REDACTED), "the window keeps the marker"),
                    () -> assertTrue(text.contains("next line"), "the text after the END stays readable"));
        }

        @Test
        @DisplayName("masks a key-block window whose END line carries an invisible character")
        void masksAKeyBlockWindowWithAnInvisibleEnd() throws Exception {
            // U+200B inside PRIVATE: the window's invisible characters are removed before the marker is searched
            append(report + "\n-----END PRI\u200BVATE KEY-----\nnext line\n");

            // the scenario is a resumed read: its offset lies inside the key body, so the tail rule applies
            var lines = assertInstanceOf(Lines.class, log.readLines((report + "\n").length()),
                    "the read answers lines");

            var text = String.join("\n", lines.lines());
            assertAll(
                    () -> assertFalse(text.contains("PRIVATE KEY-----"), "the split marker is masked"),
                    () -> assertTrue(text.contains(SecretRedactor.REDACTED), "the window keeps the marker"),
                    () -> assertTrue(text.contains("next line"), "the text after the END stays readable"));
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
        @DisplayName("redacts the job output it hands out (the read-out passes the redactor)")
        void redactsTheJobOutput() throws Exception {
            append(report + "\npassword=abcd1234\n");

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "a readable log is complete");
            var reading = whole.reading();

            assertAll(
                    () -> assertFalse(reading.jobOutput().contains("abcd1234"), "the output carries no secret"),
                    () -> assertTrue(reading.jobOutput().contains(SecretRedactor.REDACTED),
                            "the output keeps the redaction marker"));
        }

        @Test
        @DisplayName("redacts a credential in the detail of an unavailable read")
        void redactsTheUnavailableDetail() {
            var secretPath = new JobLog(directory.resolve("token=abcd1234.log"));

            var missing = assertInstanceOf(Unavailable.class, secretPath.readWhole(0), "a missing log is unavailable");

            assertAll(
                    () -> assertFalse(missing.detail().contains("abcd1234"), "the detail carries no secret"),
                    () -> assertTrue(missing.detail().contains(SecretRedactor.REDACTED),
                            "the detail keeps the redaction marker"));
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
                    () -> assertFalse(whole.cut(), "a log at the cap is not cut"),
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
                    () -> assertTrue(whole.cut(), "a log over the cap is reported as cut"),
                    () -> assertTrue(reading.jobOutput().contains(marker), "the tail of the log is read"),
                    () -> assertFalse(reading.jobOutput().contains(middle), "the middle of the log is cut"),
                    () -> assertTrue(reading.jobOutput().startsWith(marker), "the cut starts at a line beginning"));
        }

        @Test
        @DisplayName("over the cap with a first line longer than the cap: the tail is kept, the head is not")
        void headWithoutLineEndKeepsTheTail() throws Exception {
            var marker = "tail marker " + line();
            Files.writeString(path, "y".repeat(JobLog.MAX_BYTES + Generators.integers(1, 64).next()) + "\n" + marker
                    + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Unreported.class, whole.reading(),
                    "a first line over the cap is no report");

            assertAll(
                    () -> assertEquals(marker + "\n", reading.jobOutput(), "the tail of the log is answered"),
                    () -> assertEquals(Files.size(path), whole.length(), "the length is the real file length"));
        }

        @Test
        @DisplayName("a lone END in a whole read keeps the text before it")
        void aLoneEndKeepsTheTextBeforeIt() throws Exception {
            var before = "build output " + line();
            Files.writeString(path, before + "\n-----END PRIVATE KEY-----\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "the log is complete at the head");
            var reading = assertInstanceOf(LaunchReport.Unreported.class, whole.reading(),
                    "a lone END is no launch report");

            assertTrue(reading.jobOutput().contains(before),
                    "the read keeps the text before the lone END");
        }

        @Test
        @DisplayName("a lone END in readLines from the file start keeps the lines before it")
        void aLoneEndInLinesFromTheStartKeepsTheTextBeforeIt() throws Exception {
            var before = "build output " + line();
            Files.writeString(path, before + "\n-----END PRIVATE KEY-----\n", StandardCharsets.UTF_8);

            var lines = assertInstanceOf(JobLog.Lines.class, log.readLines(0), "the lines are answered");

            assertTrue(String.join("\n", lines.lines()).contains(before),
                    "the lines keep the text before the lone END");
        }

        @Test
        @DisplayName("a lone END in readLines from an offset above 0 masks the window it starts")
        void aLoneEndInLinesFromAnOffsetMasksTheWindow() throws Exception {
            var first = "first " + line();
            var second = "second " + line();
            var last = "last " + line();
            var offset = (first + "\n" + second + "\n").getBytes(StandardCharsets.UTF_8).length;
            Files.writeString(path, first + "\n" + second + "\n-----END PRIVATE KEY-----\n" + last + "\n",
                    StandardCharsets.UTF_8);

            var lines = assertInstanceOf(JobLog.Lines.class, log.readLines(offset), "the lines are answered");

            assertEquals(List.of(SecretRedactor.REDACTED, last), lines.lines(),
                    "the window in the key body is masked, the tail stays");
        }

        @Test
        @DisplayName("an over-cap read masks the text before a lone END it starts inside")
        void cappedReadMasksTheTextBeforeALoneEnd() throws Exception {
            var tail = "tail " + line();
            Files.writeString(path, "build output\n-----BEGIN PRIVATE KEY-----\n" + "x".repeat(JobLog.MAX_BYTES)
                    + "\n-----END PRIVATE KEY-----\n" + tail + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Unreported.class, whole.reading(), "a lone END is no report");

            assertAll("the capped read masks the text before the lone END",
                    () -> assertTrue(reading.jobOutput().startsWith(SecretRedactor.REDACTED),
                            "the masked text starts the output"),
                    () -> assertTrue(reading.jobOutput().contains(tail), "the text after END stays"));
        }

        @Test
        @DisplayName("over the cap with a first line past the head bound: the long first line is not kept")
        void firstLinePastTheHeadBoundIsNotKept() throws Exception {
            var head = "z".repeat(JobLog.FIRST_LINE_MAX_BYTES + 1);
            var marker = "tail marker " + line();
            Files.writeString(path, head + "\n" + "x".repeat(JobLog.MAX_BYTES) + "\n" + marker + "\n",
                    StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Unreported.class, whole.reading(),
                    "a first line that is not kept is no report");

            assertAll(
                    () -> assertFalse(reading.jobOutput().contains("z".repeat(64)), "the long first line is not read"),
                    () -> assertEquals(marker + "\n", reading.jobOutput(), "the tail alone is answered"));
        }

        @Test
        @DisplayName("over the cap with the window's only line end at its last byte: the window is kept")
        void windowEndingInItsOnlyLineEndIsKept() throws Exception {
            Files.writeString(path, report + "\n" + "x".repeat(JobLog.MAX_BYTES + Generators.integers(1, 64).next())
                    + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading(),
                    "the report survives the cut");

            assertAll(
                    () -> assertEquals(pid, reading.report().pid(), "the first line is the report"),
                    () -> assertEquals("x".repeat(JobLog.MAX_BYTES - 1) + "\n", reading.jobOutput(),
                            "the whole window follows the report"));
        }

        @Test
        @DisplayName("over the cap by less than the report: the report is kept, the window follows it")
        void reportLongerThanTheOvershootIsKept() throws Exception {
            // the contract of the boundary: an overshoot of report.length - 2 bytes, shorter than the report itself
            Files.writeString(path, report + "\n" + "x".repeat(JobLog.MAX_BYTES - 4) + "\n", StandardCharsets.UTF_8);

            var whole = assertInstanceOf(Complete.class, log.readWhole(0), "an over-cap log is complete");
            var reading = assertInstanceOf(LaunchReport.Reported.class, whole.reading(),
                    "the report longer than the overshoot survives the cut");

            assertAll(
                    () -> assertEquals(pid, reading.report().pid(), "the first line is the report"),
                    () -> assertEquals("x".repeat(JobLog.MAX_BYTES - 4) + "\n", reading.jobOutput(),
                            "the window after the report is kept whole"));
        }

        @Test
        @DisplayName("a log that shrinks under the read is truncated, never complete with a short text")
        void shrinkingLogIsTruncated() throws Exception {
            FileChannel channel = createMock(FileChannel.class);
            expect(channel.size()).andStubReturn(10L);
            expect(channel.read(anyObject(ByteBuffer.class), eq(0L))).andAnswer(() -> {
                ((ByteBuffer) getCurrentArguments()[0]).put("short".getBytes(StandardCharsets.UTF_8));
                return 5;
            });
            expect(channel.read(anyObject(ByteBuffer.class), eq(5L))).andReturn(-1);
            replay(channel);

            var whole = log.readWhole(channel, 0);

            var unavailable = assertInstanceOf(Unavailable.class, whole, "a short read is not a complete log");
            assertAll(
                    () -> assertEquals(LogState.TRUNCATED, unavailable.state(), "the short read is truncated"),
                    () -> assertTrue(unavailable.detail().contains("returned 5 of the 10 bytes"),
                            "the detail states the bytes the read returned"));
        }

        @Test
        @DisplayName("a window whose only line end is its last byte is kept whole")
        void fromLineStartKeepsAWindowEndingInItsOnlyLineEnd() {
            var window = (line() + "\n").getBytes(StandardCharsets.UTF_8);

            assertEquals(new String(window, StandardCharsets.UTF_8),
                    new String(JobLog.fromLineStart(window), StandardCharsets.UTF_8), "the window is not emptied");
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
