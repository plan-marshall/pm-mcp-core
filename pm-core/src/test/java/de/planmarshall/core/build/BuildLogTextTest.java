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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.LimitProperties;

/**
 * Reads build-log text and strips the terminal control sequences a colour-coded log carries.
 */
@EnableGeneratorController
@DisplayName("BuildLogText")
class BuildLogTextTest {

    @Test
    @DisplayName("strips SGR colour codes and leaves plain text unchanged")
    void stripsAnsiEscapes() {
        var word = Generators.letterStrings(4, 12).next();
        var coloured = "\u001B[1;31m[ERROR]\u001B[0m " + word;
        var linked = "\u001B]8;;file:///p/Foo.java\u0007[ERROR] /p/Foo.java:[7,3] cannot find symbol\u001B]8;;\u0007";
        var linkedEsc = "\u001B]8;;file:///p/Foo.java\u001B\\[ERROR] /p/Foo.java:[7,3] cannot find symbol"
                + "\u001B]8;;\u001B\\";

        assertAll(
                () -> assertEquals("[ERROR] " + word, BuildLogText.stripAnsi(coloured),
                        "The colour codes are removed"),
                () -> assertEquals(word, BuildLogText.stripAnsi(word), "Plain text is returned unchanged"),
                () -> assertEquals("[WARNING] x", BuildLogText.stripAnsi("[WARNING] x"),
                        "Text without escapes is untouched"),
                () -> assertEquals("[ERROR] /p/Foo.java:[7,3] cannot find symbol",
                        BuildLogText.stripAnsi(linked), "A BEL-terminated hyperlink is removed"),
                () -> assertEquals("[ERROR] /p/Foo.java:[7,3] cannot find symbol",
                        BuildLogText.stripAnsi(linkedEsc), "An ESC-backslash hyperlink is removed"));
    }

    @Test
    @DisplayName("reads a log and replaces undecodable bytes")
    void readsAndReplacesBrokenBytes(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("build.log");
        byte[] content = {'a', (byte) 0xFF, 'b', '\n'};
        Files.write(log, content);

        String text = BuildLogText.read(log);

        assertAll(
                () -> assertTrue(text.startsWith("a"), "The readable prefix is kept"),
                () -> assertTrue(text.endsWith("b\n"), "The readable suffix is kept"),
                () -> assertEquals(4, text.length(), "The broken byte becomes one replacement character"));
    }

    @Test
    @DisplayName("refuses a log file that cannot be read")
    void refusesMissingFile(@TempDir Path directory) {
        var missing = directory.resolve("absent.log");

        var failure = assertThrows(UncheckedIOException.class, () -> BuildLogText.read(missing),
                "A missing log is reported as an unreadable log");

        assertTrue(failure.getMessage().contains("absent.log"), "The refusal names the missing log");
    }

    @Test
    @DisplayName("the cap is read from the module's limits resource")
    void capIsReadFromTheLimitsResource() {
        assertEquals(BuildLogText.MAX_BYTES, LimitProperties.read(BuildLogText.LIMITS, BuildLogText.MAX_BYTES_KEY),
                "the cap must come from the resource");
    }

    @Test
    @DisplayName("an alternate resource yields its own cap, never a fixed one")
    void capOfAnAlternateResource() {
        var alternate = "/de/planmarshall/core/build/alternate-build-limits.properties";

        assertEquals(42, LimitProperties.read(alternate, BuildLogText.MAX_BYTES_KEY),
                "the reader must return the value of the resource");
    }

    @Test
    @DisplayName("a log one byte under the cap is read whole")
    void readsALogUnderTheCapWhole(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES - 2);
        Path log = directory.resolve("under.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertEquals(BuildLogText.MAX_BYTES - 1, text.length(), "the whole log is read"),
                () -> assertTrue(text.startsWith("h"), "the head of the log is kept"),
                () -> assertTrue(text.endsWith("a"), "the end of the log is kept"));
    }

    @Test
    @DisplayName("a log exactly at the cap is read whole")
    void readsALogAtTheCapWhole(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES - 1);
        Path log = directory.resolve("at.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertEquals(content, text, "a log at the cap is read as it was written");
    }

    @Test
    @DisplayName("a cut that begins at a line start keeps that line")
    void cutAtALineStartKeepsTheLine(@TempDir Path directory) throws Exception {
        var content = "x\n" + "a".repeat(100) + "\n" + "a".repeat(BuildLogText.MAX_BYTES - 101);
        Path log = directory.resolve("aligned.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertEquals(BuildLogText.MAX_BYTES, text.length(), "the whole window is read"),
                () -> assertTrue(text.startsWith("a".repeat(100)), "the line at the cut's start is kept"));
    }

    @Test
    @DisplayName("a log one byte over the cap is read from its end")
    void readsALogOverTheCapFromItsEnd(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES);
        Path log = directory.resolve("over.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertEquals(BuildLogText.MAX_BYTES, text.length(), "at most the cap is read"),
                () -> assertFalse(text.startsWith("h"), "the head of the log is cut"),
                () -> assertTrue(text.endsWith("a"), "the end of the log is kept"));
    }

    @Test
    @DisplayName("a cut log starts at a line beginning")
    void cutLogStartsAtALineBeginning(@TempDir Path directory) throws Exception {
        var line = "line " + Generators.letterStrings(1, 8).next() + "\n";
        var content = "head\n" + line.repeat(BuildLogText.MAX_BYTES / line.length() + 2);
        Path log = directory.resolve("lines.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertTrue(text.getBytes(StandardCharsets.UTF_8).length <= BuildLogText.MAX_BYTES,
                        "at most the cap is read"),
                () -> assertTrue(text.startsWith("line "), "the cut starts at a line beginning"),
                () -> assertFalse(text.contains("head"), "the head of the log is cut"),
                () -> assertTrue(text.endsWith(line), "the last line is kept whole"));
    }

    @Test
    @DisplayName("a read of a log over the cap says it was cut")
    void readOfALogOverTheCapSaysItWasCut(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES);
        Path log = directory.resolve("cut.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var read = BuildLogText.readLog(log);

        assertAll(
                () -> assertTrue(read.cut(), "a log over the cap is reported as cut"),
                () -> assertEquals(BuildLogText.MAX_BYTES, read.text().length(), "the read keeps the cap's tail"));
    }

    @Test
    @DisplayName("a read of a log at the cap says it was not cut")
    void readOfALogAtTheCapSaysItWasNotCut(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES - 1);
        Path log = directory.resolve("whole.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var read = BuildLogText.readLog(log);

        assertAll(
                () -> assertFalse(read.cut(), "a log at the cap is reported as whole"),
                () -> assertEquals(content, read.text(), "a whole log is read as it was written"));
    }

    /** The tail of a Maven log: one summary block and the success banner (Maven's golden output format). */
    private static final String MAVEN_TAIL = """
            [INFO] Results:
            [INFO]\s
            [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
            [INFO] BUILD SUCCESS
            """;

    @Test
    @DisplayName("a cut Maven log reports its count as unmeasured")
    void cutMavenLogReportsUnmeasured(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var head = "[INFO] Results:\n[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0\n";
        Path log = directory.resolve("cut-maven.log");
        Files.writeString(log, head + filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + MAVEN_TAIL,
                StandardCharsets.UTF_8);

        var counts = MavenOutputParser.parse(log).testCounts();

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, counts, "a cut log's count is unmeasured"),
                () -> assertEquals(TestsPopulation.UNMEASURED, counts.population(),
                        "the cut log's population is unmeasured"));
    }

    @Test
    @DisplayName("a cut log never reads as no tests ran, whatever its tail says")
    void cutMavenLogNeverReadsAsNoTestsRan(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var noTestsTail = "[INFO] No tests to run.\n[INFO] BUILD SUCCESS\n";
        Path log = directory.resolve("cut-no-tests.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + noTestsTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertFalse(build.noTestsRan(), "a cut log never reports that no test ran"));
    }

    @Test
    @DisplayName("a cut log keeps a visible failing count as an error diagnostic")
    void cutMavenLogKeepsAVisibleFailingCount(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [INFO] Results:
                [INFO] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """;
        Path log = directory.resolve("cut-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(
                                diagnostic -> diagnostic.severity() == Severity.ERROR
                                        && diagnostic.message().contains("2 failing tests")),
                        "the visible failing count is still an error"));
    }

    @Test
    @DisplayName("a cut log counts the failing tests of a summary whose Results marker was cut")
    void cutMavenLogCountsAnAmbiguousFailingSummary(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // no Results marker: it lies in the lost head, the failing summary line is in the tail
        var failingTail = """
                [WARNING] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """;
        Path log = directory.resolve("cut-ambiguous-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.severity() == Severity.ERROR
                                        && diagnostic.message().contains("2 failing tests")),
                        "the ambiguous failing count is still an error"));
    }

    @Test
    @DisplayName("a cut log files the failing count once, beside the count line it already filed")
    void cutMavenLogFilesTheFailingCountOnce(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [ERROR] Results:
                [ERROR] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-filed-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, build.diagnostics().stream()
                                .filter(diagnostic -> diagnostic.category() == Category.TEST_FAILURE).count(),
                        "the failing count is one test-failure diagnostic"));
    }

    @Test
    @DisplayName("a cut log counts a per-class line and its summary line once")
    void cutMavenLogCountsAPerClassLineAndItsSummaryOnce(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // a testFailureIgnore run: the per-class line carries Time elapsed, the summary's Results marker was cut
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-per-class-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains("1 failing test")
                                        && !diagnostic.message().contains("2 failing tests")),
                        "the failing test is counted once, not twice"));
    }

    @Test
    @DisplayName("a cut log files no second finding for its own error count line")
    void cutMavenLogFilesNoSecondFindingForItsErrorCountLine(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """;
        Path log = directory.resolve("cut-error-count.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, build.diagnostics().stream()
                                .filter(diagnostic -> diagnostic.category() == Category.TEST_FAILURE).count(),
                        "the error count line is the only test failure"));
    }

    @Test
    @DisplayName("a cut log files the failing tests its own error line does not report")
    void cutMavenLogFilesTheFailuresItsErrorLineDoesNotReport(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Results:
                [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-mixed-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(2, build.diagnostics().stream()
                                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR
                                        && diagnostic.category() == Category.TEST_FAILURE).count(),
                        "each failing block is an error once"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains("1 further failing test")),
                        "the unfiled block is named as the remainder"));
    }

    @Test
    @DisplayName("a whole Maven log reports its count as the whole run's")
    void wholeMavenLogReportsTheWholeRun(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("whole-maven.log");
        Files.writeString(log, MAVEN_TAIL, StandardCharsets.UTF_8);

        var counts = MavenOutputParser.parse(log).testCounts();

        var measured = assertInstanceOf(TestCounts.Measured.class, counts, "the summary block is a measurement");
        assertEquals(3, measured.passed(), "a whole log counts its summary block");
    }

    @Test
    @DisplayName("a cut whose only line end is its last byte keeps the window")
    void cutWhoseOnlyLineEndIsItsLastByteKeepsTheWindow(@TempDir Path directory) throws Exception {
        var window = "a".repeat(BuildLogText.MAX_BYTES - 1) + "\n";
        Path log = directory.resolve("last-byte.log");
        Files.writeString(log, "h" + window, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertEquals(window, text, "the window is kept, never emptied");
    }

    @Test
    @DisplayName("a window whose only line end is its last byte is kept whole")
    void fromLineStartKeepsAWindowEndingInItsOnlyLineEnd() {
        var window = (Generators.letterStrings(1, 40).next() + "\n").getBytes(StandardCharsets.UTF_8);

        var kept = BuildLogText.fromLineStart(window);

        assertEquals(new String(window, StandardCharsets.UTF_8), new String(kept, StandardCharsets.UTF_8),
                "the window ending in its only line end is kept");
    }
}
