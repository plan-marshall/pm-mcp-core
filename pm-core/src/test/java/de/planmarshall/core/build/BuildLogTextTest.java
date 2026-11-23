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
    @DisplayName("a cut log keeps a failure whose execution reports only a passing summary")
    void cutMavenLogKeepsAFailureWhoseSummaryIsPassing(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the failing execution's own summary lies in the lost head; the visible summary belongs to a later module
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [INFO] Results:
                [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-passing-summary-failing-class.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.severity() == Severity.ERROR
                                        && diagnostic.message().contains("1 failing test")),
                        "the per-class failure stays an error"));
    }

    @Test
    @DisplayName("a cut log files no second failure for a per-class line already filed")
    void cutMavenLogFilesNoSecondFailureForAFiledPerClassLine(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-filed-per-class-failing.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, build.diagnostics().stream()
                                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR
                                        && diagnostic.category() == Category.TEST_FAILURE).count(),
                        "the filed per-class failure is the only one"));
    }

    @Test
    @DisplayName("a cut log files no second failure for the per-class line its filed summary repeats")
    void cutMavenLogFilesNoSecondFailureForThePerClassLineItsSummaryRepeats(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // one failure, printed per class and again in the summary whose Results marker is in the tail
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [ERROR] Results:
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-per-class-and-filed-summary.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, errorTestFailures(build),
                        "the filed summary is the only error of one failure"));
    }

    @Test
    @DisplayName("a cut log keeps each execution's failures when one reports only per-class lines")
    void cutMavenLogKeepsTheFailuresOfAnExecutionWithoutSummary(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // execution A's summary lies in an ambiguous block, execution B reports its failures per class only
        var failingTail = """
                --- surefire:3.5.0:test @ module-a ---
                [WARNING] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
                --- surefire:3.5.0:test @ module-b ---
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                """.formatted(Generators.letterStrings(3, 12).next() + "Test",
                Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-two-executions.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.severity() == Severity.ERROR
                                        && diagnostic.category() == Category.TEST_FAILURE
                                        && diagnostic.message().contains("2 further failing tests")),
                        "the summary's two failures are still filed"));
    }

    @Test
    @DisplayName("a cut log subtracts a duplicated error count line once, as the collector files it")
    void cutMavenLogSubtractsADuplicatedErrorCountLineOnce(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the same count line twice at error level: the collector files one diagnostic, so one failure is filed
        var failingTail = """
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-duplicated-error-count.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, errorTestFailures(build),
                        "the deduplicated count line is the only error"),
                () -> assertFalse(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains("the cut log's tail reports")),
                        "the filed failure leaves no remainder"));
    }

    @Test
    @DisplayName("a cut log names one remaining failing test in the singular")
    void cutMavenLogNamesOneRemainingFailureInTheSingular(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Results:
                [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-singular-remainder.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertEquals("the cut log's tail reports 1 further failing test",
                build.diagnostics().stream()
                        .filter(diagnostic -> diagnostic.message().contains("the cut log's tail reports"))
                        .findFirst().orElseThrow().message(),
                "one remainder is named in the singular");
    }

    @Test
    @DisplayName("a cut log keeps an error for a failure whose identical line was filed as a warning first")
    void cutMavenLogKeepsAnErrorForAWarningFirstDuplicate(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the same count line at WARNING before the marker and at ERROR after it: the collector retains the warning
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Results:
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-warning-first-duplicate.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertEquals(1, errorTestFailures(build),
                        "no error diagnostic would leave the failure unseen"),
                () -> assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                                "the cut log's tail reports 1 failing test".equals(diagnostic.message())),
                        "the collector filed no error count line"));
    }

    @Test
    @DisplayName("a cut log's failing count saturates instead of wrapping to a passing run")
    void cutMavenLogSaturatesAnOverflowingFailingCount(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // two executions whose failing counts sum past the long range; a wrapped sum would report no failure
        var failingTail = """
                --- surefire:3.5.0:test @ module-a ---
                [WARNING] Tests run: 1, Failures: 9223372036854775807, Errors: 0, Skipped: 0
                --- surefire:3.5.0:test @ module-b ---
                [WARNING] Tests run: 2, Failures: 9223372036854775807, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-overflowing-count.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && diagnostic.category() == Category.TEST_FAILURE),
                "an overflowing sum still files the failure");
    }

    @Test
    @DisplayName("a second execution's failure is not swallowed by the first execution's filed lines")
    void cutMavenLogKeepsASecondExecutionsFailureBesideAnOverSubtractingFirst(@TempDir Path directory)
            throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // execution A files a per-class line and its summary: 2 filed against 1 visible, so a global subtraction
        // spends A's over-subtraction on B and B's warning-only failure is never filed
        var failingTail = """
                --- surefire:3.5.0:test @ module-a ---
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                --- surefire:3.5.0:test @ module-b ---
                [WARNING] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-second-execution-failure.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && "the cut log's tail reports 1 further failing test".equals(diagnostic.message())),
                "the second execution's failure is filed");
    }

    @Test
    @DisplayName("a winning summary side does not hide another execution's failure")
    void cutMavenLogKeepsAnotherExecutionsFailureWhenTheSummarySideWins(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // execution A's summary side wins while its per-class line is counted twice in the filed figure
        var failingTail = """
                --- surefire:3.5.0:test @ module-a ---
                [ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %s
                [ERROR] Results:
                [ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
                --- surefire:3.5.0:test @ module-b ---
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """.formatted(Generators.letterStrings(3, 12).next() + "Test");
        Path log = directory.resolve("cut-winning-summary-side.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && "the cut log's tail reports 1 further failing test".equals(diagnostic.message())),
                "the other execution's failure is filed");
    }

    @Test
    @DisplayName("two failing classes that differ after the key's hundredth character are two failures")
    void cutMavenLogCountsTwoClassesThatDifferAfterTheHundredthCharacter(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // the stored message spends 90 characters on its prefix, so the two class names differ past the 100th one
        var packagePrefix = "com.example." + "a".repeat(80) + ".";
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %sFooTest
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %sBarTest
                """.formatted(packagePrefix, packagePrefix);
        Path log = directory.resolve("cut-two-classes-past-the-key.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && "the cut log's tail reports 2 failing tests".equals(diagnostic.message())),
                "two classes past the key's cut are two failures");
    }

    @Test
    @DisplayName("two failing classes that differ after the stored bound are two failures")
    void cutMavenLogCountsTwoClassesThatDifferAfterTheStoredBound(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // the stored message is cut at 500 characters, so the two class names differ past that bound
        var packagePrefix = "com.example." + "a".repeat(500) + ".";
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %sFooTest
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in %sBarTest
                """.formatted(packagePrefix, packagePrefix);
        Path log = directory.resolve("cut-two-classes-past-the-stored-bound.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && "the cut log's tail reports 2 failing tests".equals(diagnostic.message())),
                "two classes past the stored bound are two failures");
    }

    @Test
    @DisplayName("two modules' identical summary lines are two executions' failures")
    void cutMavenLogCountsIdenticalSummaryLinesOfTwoExecutions(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        var failingTail = """
                --- surefire:3.5.0:test @ module-a ---
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                --- surefire:3.5.0:test @ module-b ---
                [WARNING] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-identical-summaries.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && "the cut log's tail reports 2 failing tests".equals(diagnostic.message())),
                "the two modules' failures are both counted");
    }

    @Test
    @DisplayName("an unmarked count line does not hide the failure its error duplicate files")
    void cutMavenLogCountsTheErrorLineAnUnmarkedDuplicatePrecedes(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // the unmarked line is no diagnostic, so the collector files the later identical error line
        var failingTail = """
                Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-unmarked-count-before-error.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, errorTestFailures(build),
                        "only the filed error count line is an error"),
                () -> assertFalse(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().startsWith("the cut log's tail reports")),
                        "the filed error line leaves no remainder"));
    }

    @Test
    @DisplayName("a twenty-digit failing count saturates and still files a failure")
    void cutMavenLogSaturatesATwentyDigitFailingCount(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the Maven log-format lines are the parser's contract fixture
        // a count above the long range must not parse as zero and leave the failure unfiled
        var failingTail = """
                [WARNING] Tests run: 1, Failures: 99999999999999999999, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-twenty-digit-count.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertTrue(build.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == Severity.ERROR
                                && diagnostic.category() == Category.TEST_FAILURE),
                "an overflowing count still files the failure");
    }

    private static long errorTestFailures(ParsedBuild build) {
        return build.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR
                        && diagnostic.category() == Category.TEST_FAILURE)
                .count();
    }

    @Test
    @DisplayName("a cut log counts one failure once when the same line stands on both sides of the marker")
    void cutMavenLogCountsTheSameLineOnceAcrossTheMarker(@TempDir Path directory) throws Exception {
        var filler = "[INFO] " + Generators.letterStrings(4, 12).next() + "\n";
        // the read cut between one module's Results marker and its summary line; both print the same count line
        var failingTail = """
                [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] Results:
                [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
                """;
        Path log = directory.resolve("cut-across-the-marker.log");
        Files.writeString(log, filler.repeat(BuildLogText.MAX_BYTES / filler.length() + 1) + failingTail,
                StandardCharsets.UTF_8);

        var build = MavenOutputParser.parse(log);

        assertAll(
                () -> assertSame(TestCounts.Unmeasured.INSTANCE, build.testCounts(),
                        "a cut log's count is unmeasured"),
                () -> assertEquals(1, errorTestFailures(build),
                        "the collector files the two lines as one error"),
                () -> assertFalse(build.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains("the cut log's tail reports")),
                        "the filed failure leaves no remainder"));
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
