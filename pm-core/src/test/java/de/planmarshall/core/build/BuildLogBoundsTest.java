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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.LimitProperties;
import de.planmarshall.core.store.JobId;

/**
 * The bounds of a build log's parse (R07): the three parsers of this plan cut every line at the per-line limit before a
 * pattern of theirs sees it, and the three patterns that backtrack over a line of repeated tokens answer inside a stated
 * bound on a hostile line.
 * <p>
 * The bound is measured through the parser, not through the expression: a test that proves a pattern is no longer
 * quadratic names its input and uses a preemptive timeout, never a comparison of two wall-clock runs. The hostile line
 * is far longer than the limit, so it is the cut that bounds the parse, and the second group repeats hostile lines at
 * exactly the limit, where no cut helps and only the rewrite does.
 * <p>
 * The two reference expressions are the ported forms the rewrite replaced: the differential tests hold the rewrite to
 * their result over generated lines, so a rewrite that changes a verdict fails here before it reaches a fixture.
 * <p>
 * The copy cost of {@link UnparsedEvidence} on a log at the read's cap is measured here too, as an allocation bounded by
 * a multiple of the log's length.
 */
@EnableGeneratorController
@DisplayName("Build log bounds")
class BuildLogBoundsTest {

    /** The bound a hostile line answers inside; it names the length of the input beside it. */
    private static final Duration HOSTILE_BOUND = Duration.ofSeconds(1);

    /** The generous bound the evidence of a log at the read's cap answers inside; it is no performance target. */
    private static final Duration EVIDENCE_BOUND = Duration.ofSeconds(10);

    /** The log copies the evidence may allocate: the measured five, with room for the JVM's growth policy. */
    private static final int EVIDENCE_COPIES = 8;

    /** The per-line limit the parsers apply. */
    private static final int LIMIT = BuildLogText.MAX_LINE_LENGTH;

    /** The length of the hostile line the parsers are handed: far over the limit, so only the cut can bound it. */
    private static final int LONG_LINE = LIMIT * 32;

    /** The lines repeated at exactly the limit: no cut applies to them, so only a linear rewrite meets the bound. */
    private static final int LINES_AT_THE_LIMIT = 200;

    /** The number of generated lines the differential tests compare. */
    private static final int GENERATED_LINES = 500;

    /** The ported Maven header expression, as it was before the pre-test was added. */
    @SuppressWarnings("java:S8786") // the reference the rewrite is held to: deliberately the ported, super-linear form
    private static final Pattern PORTED_EXECUTION_HEADER =
            Pattern.compile("---\\s+([\\w.\\-]+):[\\w.\\-]+:[\\w.\\-]+\\s.*@.*---");

    /** The ported pytest summary expression, as it was before it was answered in two steps. */
    @SuppressWarnings("java:S8786") // the reference the rewrite is held to: deliberately the ported, super-linear form
    private static final Pattern PORTED_SUMMARY_LINE =
            Pattern.compile("^.*\\b(?:passed|failed|skipped)\\b.*\\bin\\s+[\\d.]+s.*$");

    /** The ported OpenRewrite finding expression, as it was before the pre-test was added. */
    @SuppressWarnings("java:S8786") // the reference the rewrite is held to: deliberately the ported, super-linear form
    private static final Pattern PORTED_FINDING = Pattern.compile(
            "CUI_REWRITE-(10[01]): Finding (?:detected|pre-existing) at (.+?):(\\d+):(\\d+) by (.+?): (.*)$");

    /** The fragments a generated line is built from: the tokens the three rewritten patterns read. */
    private static final List<String> FRAGMENTS = List.of(
            "---", " ", "\t", "a", "a:b:c", ":", "@", "--- a:b:c ", "x.y-z", "CUI_REWRITE-100", "CUI_REWRITE-101",
            "CUI_REWRITE-10", ": Finding detected at ", ": Finding pre-existing at ", " by ", "y", ": ", "1", "12", ".5",
            "s", "passed", "failed", "skipped", " in ", "in 1.5s", "took 2s", "E", "=", "\r", "\u2028", "x");

    /** A line the ported finding expression matches, so the differential test is never vacuous. */
    private static final String VALID_FINDING =
            "CUI_REWRITE-100: Finding detected at src/Foo.java:12:3 by com.example.Recipe: a message";

    private static String fit(String unit, int length) {
        var builder = new StringBuilder(length + unit.length());
        while (builder.length() < length) {
            builder.append(unit);
        }
        return builder.substring(0, length);
    }

    private static String generatedLine() {
        var count = Generators.integers(0, 12).next();
        var builder = new StringBuilder();
        for (var index = 0; index < count; index++) {
            builder.append(FRAGMENTS.get(Generators.integers(0, FRAGMENTS.size() - 1).next()));
        }
        return builder.toString();
    }

    @Test
    @DisplayName("a line at the bound is kept whole")
    void keepsALineAtTheBound() {
        var line = fit("x", LIMIT);

        assertEquals(line, BuildLogText.boundLines(line), "a line at the bound is not cut");
    }

    @Test
    @DisplayName("a line over the bound is cut at its head")
    void cutsALineOverTheBound() {
        var line = fit("xyz", LIMIT + 1);

        var bounded = BuildLogText.boundLines(line);

        assertEquals(line.substring(0, LIMIT), bounded, "the line keeps its head up to the bound");
    }

    @Test
    @DisplayName("every line of a log is cut, and its line ends stay")
    void cutsEveryLineOfALog() {
        var longLine = fit("x", LIMIT + 5);
        var log = "short\n" + longLine + "\nshort";

        var bounded = BuildLogText.boundLines(log);

        assertEquals("short\n" + fit("x", LIMIT) + "\nshort", bounded, "only the long line is cut");
    }

    @Test
    @DisplayName("the per-line limit is read from the module's resource")
    void lineLimitIsReadFromTheResource() {
        assertEquals(BuildLogText.MAX_LINE_LENGTH,
                LimitProperties.read(BuildLogText.LIMITS, BuildLogText.MAX_LINE_LENGTH_KEY),
                "the line bound must come from the resource");
    }

    @Test
    @DisplayName("an alternate resource yields its own line limit")
    void lineLimitOfAnAlternateResource() {
        var alternate = "/de/planmarshall/core/build/alternate-build-limits.properties";

        assertEquals(64, LimitProperties.read(alternate, BuildLogText.MAX_LINE_LENGTH_KEY),
                "the reader must return the value of the resource");
    }

    @Test
    @DisplayName("a hostile Maven header line returns inside the bound")
    void boundsTheMavenHeaderPattern() {
        var hostile = fit("--- a:b:c ", LONG_LINE);
        MavenOutputParser.parse("BUILD SUCCESS\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> MavenOutputParser.parse("BUILD SUCCESS\n" + hostile + "\n"),
                "a long Maven header line is parsed inside the bound");

        assertTrue(parsed.successMarker(), "the status marker is still read");
    }

    @Test
    @DisplayName("hostile Maven header lines at the limit return inside the bound")
    void boundsRepeatedMavenHeaderLines() {
        var hostile = fit("--- a:b:c ", LIMIT);
        MavenOutputParser.parse("BUILD SUCCESS\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> MavenOutputParser.parse("BUILD SUCCESS\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the Maven parser answers the hostile lines in time");

        assertTrue(parsed.successMarker(), "the status marker is still read");
    }

    @Test
    @DisplayName("a hostile rewrite finding line returns inside the bound")
    void boundsTheRewriteFindingPattern() {
        var hostile = fit("CUI_REWRITE-100: Finding detected at x", LONG_LINE);
        OpenRewriteLogParser.parse("--- rewrite:run (\n", true);

        var result = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> OpenRewriteLogParser.parse("--- rewrite:run (\n" + hostile + "\n", true),
                "a long rewrite finding line is parsed inside the bound");

        assertSame(RewriteVerdict.PARSE_ERROR, result.verdict(), "the hostile line is a parse error");
    }

    @Test
    @DisplayName("a hostile pytest summary line returns inside the bound")
    void boundsThePytestSummaryPattern() {
        var hostile = fit("passed ", LONG_LINE);
        PyprojectOutputParser.parse("=== FAILURES ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> PyprojectOutputParser.parse("=== FAILURES ===\n" + hostile + "\n"),
                "a long pytest summary line is parsed inside the bound");

        assertTrue(parsed.diagnostics().isEmpty(), "the hostile line locates no diagnostic");
    }

    @Test
    @DisplayName("hostile rewrite finding lines at the limit return inside the bound")
    void boundsRepeatedRewriteFindingLines() {
        var log = "--- rewrite:run (\n" + hostileFindingLine() + "\n";
        OpenRewriteLogParser.parse(log, true);

        var verdicts = assertTimeoutPreemptively(HOSTILE_BOUND, () -> {
            var answered = new ArrayList<RewriteVerdict>();
            for (var index = 0; index < LINES_AT_THE_LIMIT; index++) {
                answered.add(OpenRewriteLogParser.parse(log, true).verdict());
            }
            return answered;
        }, "the rewrite parser answers the hostile lines in time");

        assertAll(
                () -> assertEquals(LINES_AT_THE_LIMIT, verdicts.size(), "every hostile line is parsed once"),
                () -> assertTrue(verdicts.stream().allMatch(RewriteVerdict.PARSE_ERROR::equals),
                        "every hostile line is a parse error"));
    }

    /**
     * A finding line at the limit that carries the head, the identifier and many locations but no {@code ": "} after
     * the first location: the ported template tries every location as the path's end and scans the rest for the
     * recipe's {@code ": "} from each, which costs the square of the line's length.
     */
    private static String hostileFindingLine() {
        var head = "CUI_REWRITE-100: Finding detected at ";
        return head + fit("x:1:2 by y", LIMIT - head.length());
    }

    @Test
    @DisplayName("hostile Maven header lines carrying the token return inside the bound")
    void boundsRepeatedMavenHeaderLinesCarryingTheToken() {
        var hostile = fit("--- a:b:c ", LIMIT - 1) + "@";
        MavenOutputParser.parse("BUILD SUCCESS\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> MavenOutputParser.parse("BUILD SUCCESS\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the header lines carrying an @ answer in time");

        assertAll(
                () -> assertTrue(parsed.successMarker(), "the status marker is still read"),
                () -> assertTrue(MavenOutputParser.executionHeaderRunner(hostile).isEmpty(),
                        "a header without a closing --- names no runner"));
    }

    @Test
    @DisplayName("hostile pytest lines carrying a line terminator return inside the bound")
    void boundsRepeatedPytestSummaryLinesCarryingATerminator() {
        var hostile = fit("passed ", LIMIT - 2) + "\rx";
        PyprojectOutputParser.parse("=== FAILURES ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> PyprojectOutputParser.parse("=== FAILURES ===\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the terminator-carrying pytest lines answer in time");

        assertAll(
                () -> assertTrue(parsed.diagnostics().isEmpty(), "the hostile lines locate no diagnostic"),
                () -> assertFalse(PyprojectOutputParser.isSummaryLine(hostile),
                        "a line without seconds is no summary line"));
    }

    @Test
    @DisplayName("the finding match answers the ported expression")
    void findingMatchKeepsThePortedResult() {
        var lines = new ArrayList<String>();
        lines.add(VALID_FINDING);
        lines.add("CUI_REWRITE-100: Finding detected at :1:2 by x :3:4 by r: m");
        lines.add("CUI_REWRITE-101: Finding pre-existing at a:1:2:3 by r: m");
        lines.add("CUI_REWRITE-100: Finding detected at a\r CUI_REWRITE-100: Finding detected at b:1:2 by r: m");
        for (var index = 0; index < GENERATED_LINES; index++) {
            lines.add(generatedLine());
        }

        for (var line : lines) {
            var ported = PORTED_FINDING.matcher(line);
            var found = ported.find();
            var rewritten = OpenRewriteLogParser.findingMatch(line);
            assertEquals(found, rewritten.isPresent(), () -> "the finding match differs from the ported: " + line);
            if (found) {
                var match = rewritten.orElseThrow();
                for (var group = 1; group <= 6; group++) {
                    var index = group;
                    assertEquals(ported.group(index), match.group(index),
                            () -> "a finding group differs from the ported: " + line);
                }
            }
        }
    }

    @Test
    @DisplayName("hostile pytest section and exception lines return inside the bound")
    void boundsThePytestSectionAndExceptionPatterns() {
        var section = "=" + " ".repeat(4) + "a" + " ".repeat(LIMIT - 7) + "=";
        var exception = "E x" + "y".repeat(LIMIT - 3);
        var log = "=== ERRORS ===\n"
                + "____ test_a ____\n"
                + exception + "\n"
                + section + "\n"
                + "=== short test summary info ===\n"
                + "ERROR test_x.py::test_a\n";
        PyprojectOutputParser.parse("=== ERRORS ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND, () -> PyprojectOutputParser.parse(log),
                "the section and exception patterns answer in time");

        assertAll(
                () -> assertEquals(1, parsed.diagnostics().size(), "the error summary line locates one diagnostic"),
                () -> assertTrue(parsed.diagnostics().getFirst().message().startsWith("xy"),
                        "the exception line of the block supplies the message"));
    }

    @Test
    @DisplayName("hostile pytest summary lines at the limit return inside the bound")
    void boundsRepeatedPytestSummaryLines() {
        var hostile = fit("passed ", LIMIT);
        PyprojectOutputParser.parse("=== FAILURES ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> PyprojectOutputParser.parse("=== FAILURES ===\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the pytest parser answers the hostile lines in time");

        assertTrue(parsed.diagnostics().isEmpty(), "the hostile lines locate no diagnostic");
    }

    /**
     * The copy cost of the unparsed evidence on a log at the read's cap (PLAN-V2-163, item 43(a)). Measured on
     * 2026-11-21: 83,219,096 bytes allocated (4.96 times the 16 MiB log) in 0.44 s, which is the escape strip and the
     * frame reduction each copying the log once more and their builders growing; the allocation is asserted against
     * a multiple of the log's length, so a copy that stops being bounded by the log's size fails here.
     */
    @Test
    @DisplayName("the unparsed evidence of a 16 MiB log allocates about five times the log (79 MiB, 0.44 s measured)")
    void boundsTheUnparsedEvidenceCopyOfALogAtTheCap() {
        var log = colouredLogOf(BuildLogText.MAX_BYTES);
        var allocation = assertInstanceOf(ThreadMXBean.class, ManagementFactory.getThreadMXBean(),
                "the JVM must account the thread's allocation");
        assertTrue(allocation.isThreadAllocatedMemoryEnabled(), "thread allocation accounting must be enabled");
        UnparsedEvidence.of("plan-one", new JobId("b-0123456789abcdef"), log);

        var measured = assertTimeoutPreemptively(EVIDENCE_BOUND, () -> {
            var before = allocation.getCurrentThreadAllocatedBytes();
            var started = System.nanoTime();
            var evidence = UnparsedEvidence.of("plan-one", new JobId("b-0123456789abcdef"), log);
            var nanos = System.nanoTime() - started;
            return new long[]{allocation.getCurrentThreadAllocatedBytes() - before, nanos,
                    evidence.excerpt().lines().count()};
        }, "the evidence of a log at the cap is taken in time");

        assertAll(
                () -> assertEquals(UnparsedEvidence.MAX_LINES, measured[2], "the excerpt keeps fifty lines"),
                () -> assertTrue(measured[0] > 0, "the allocation of the evidence is measured"),
                () -> assertTrue(measured[0] < (long) EVIDENCE_COPIES * log.length(),
                        "the evidence allocates at most eight log copies"));
    }

    /**
     * A log of exactly {@code length} ASCII characters, every line a generated text in an SGR colour pair and with one
     * carriage-return frame before it, so both full-length passes of the evidence (the escape strip and the frame
     * reduction) copy the whole log. Contract literals: the SGR escapes {@code ESC[31m} and {@code ESC[0m}.
     */
    private static String colouredLogOf(int length) {
        var log = new StringBuilder(length + 512);
        while (log.length() < length) {
            log.append(Generators.letterStrings(4, 16).next()).append('\r')
                    .append("\u001B[31m").append(Generators.letterStrings(20, 120).next()).append("\u001B[0m\n");
        }
        log.setLength(length);
        return log.toString();
    }

    @Test
    @DisplayName("the header pre-test answers the ported expression")
    void headerPreTestKeepsThePortedResult() {
        for (var index = 0; index < GENERATED_LINES; index++) {
            var line = generatedLine();
            assertEquals(PORTED_EXECUTION_HEADER.matcher(line).find(),
                    MavenOutputParser.executionHeaderRunner(line).isPresent(),
                    () -> "the header pre-test differs from the ported result: " + line);
        }
    }

    @Test
    @DisplayName("the finding pre-test never hides a finding")
    void findingPreTestNeverHidesAFinding() {
        assertTrue(PORTED_FINDING.matcher(VALID_FINDING).find(), "the valid finding line must match the ported form");
        var lines = new ArrayList<String>();
        lines.add(VALID_FINDING);
        for (var index = 0; index < GENERATED_LINES; index++) {
            lines.add(generatedLine());
        }

        for (var line : lines) {
            if (PORTED_FINDING.matcher(line).find()) {
                assertTrue(OpenRewriteLogParser.mayBeFinding(line),
                        () -> "a finding line must pass the pre-test: " + line);
            }
        }
    }

    @Test
    @DisplayName("the two-step summary answers the ported expression")
    void summaryLineKeepsThePortedResult() {
        for (var index = 0; index < GENERATED_LINES; index++) {
            var line = generatedLine();
            assertEquals(PORTED_SUMMARY_LINE.matcher(line).matches(), PyprojectOutputParser.isSummaryLine(line),
                    () -> "the summary line differs from the ported result: " + line);
        }
    }

    @Test
    @DisplayName("the summary edge cases answer the ported expression")
    void summaryLineKeepsThePortedResultOnEdgeCases() {
        var cases = List.of("", "passed", "passed in 1.5s", "1 failed, 2 passed in 0.5s",
                "x=passed in 1.5s", "passed in 1.5s extra", "passed\r", "passed in\r0.12s",
                "passed\r in 1.5s", "passed in 1.5s\r", "skipped in 1s", "passed in \u20281s",
                "passed in 1s and in 2s", "failed in x s", "passed in 1.5");

        for (var line : cases) {
            assertEquals(PORTED_SUMMARY_LINE.matcher(line).matches(), PyprojectOutputParser.isSummaryLine(line),
                    () -> "the edge case differs from the ported result: " + line);
        }
    }

    /**
     * The six npm and Gradle parsers cut every line at the limit too. Each case parses a fixed log, a million blank and
     * whitespace lines followed by one marker line, twice: once with the marker inside a line of exactly the limit, where
     * it is read, and once behind a lead of thirty-two times the limit, where the cut drops it. The bound
     * is asserted on the result, not on a clock: a parser that read the whole hostile line would report the marker.
     * <p>
     * Contract literals that stay: the marker lines, which are the golden output formats of Gradle, npm, tsc, ESLint, Jest
     * and TAP.
     */
    @Nested
    @DisplayName("the npm and Gradle parsers")
    class NpmAndGradleParsers {

        /** A million lines, every other one blank and every other one horizontal whitespace. */
        private static final String BLANK_RUN = "\n \t\n".repeat(500_000);

        /** An ESLint file header, which the rows below it are filed under. */
        private static final String ESLINT_FILE = "/web/src/a.js";

        /** The head of an ESLint row up to its message. */
        private static final String ESLINT_ROW_HEAD = "  1:10  error  ";

        /** The rule that ends an ESLint row, set off by two spaces as the stylish formatter prints it. */
        private static final String ESLINT_RULE = "  no-var";

        private static String atTheLimit(String marker) {
            return fit(" ", LIMIT - marker.length()) + marker;
        }

        private static String beyondTheLimit(String marker) {
            return fit(" ", LONG_LINE) + marker;
        }

        @Test
        @DisplayName("Gradle reads a count line at the limit and drops one beyond it")
        void gradle() {
            var counts = "3 tests completed, 1 failed";

            var atLimit = GradleOutputParser.parse(BLANK_RUN + atTheLimit(counts) + "\nBUILD FAILED\n");
            var beyond = GradleOutputParser.parse(BLANK_RUN + beyondTheLimit(counts) + "\nBUILD FAILED\n");

            assertAll(
                    () -> assertEquals(TestCounts.from(2, 1, 0, null), atLimit.testCounts(),
                            "a count line at the limit is read whole"),
                    () -> assertNull(beyond.testCounts(), "a count behind the cut is never read"),
                    () -> assertSame(BuildStatus.FAILURE, beyond.status(), "the status marker is still read"));
        }

        @Test
        @DisplayName("npm reads an install line at the limit and drops one beyond it")
        void npm() {
            var install = "added 3 packages in 1s";

            var atLimit = NpmErrorParser.parse(BLANK_RUN + atTheLimit(install) + "\n");
            var beyond = NpmErrorParser.parse(BLANK_RUN + beyondTheLimit(install) + "\n");

            assertAll(
                    () -> assertTrue(atLimit.successMarker(), "an install line at the limit is the marker"),
                    () -> assertFalse(beyond.successMarker(), "an install line behind the cut is no marker"),
                    () -> assertTrue(beyond.diagnostics().isEmpty(), "the blank run files no npm error"));
        }

        @Test
        @DisplayName("tsc reads an error line at the limit and drops one beyond it")
        void typeScript() {
            var error = "(1,2): error TS2304: Cannot find name 'x'.";

            var atLimit = TypeScriptOutputParser.parse(BLANK_RUN + fit("x", LIMIT - error.length()) + error + "\n");
            var beyond = TypeScriptOutputParser.parse(BLANK_RUN + fit("x", LONG_LINE) + error + "\n");

            assertAll(
                    () -> assertEquals(1, atLimit.diagnostics().size(), "an error line at the limit is filed"),
                    () -> assertTrue(beyond.diagnostics().isEmpty(), "an error behind the cut is never filed"),
                    () -> assertSame(BuildStatus.SUCCESS, beyond.status(), "no filed error leaves the run green"));
        }

        @Test
        @DisplayName("ESLint reads a row at the limit and drops a rule beyond it")
        void eslint() {
            var atLimit = EslintOutputParser.parse(ESLINT_FILE + BLANK_RUN + ESLINT_ROW_HEAD
                    + fit("m", LIMIT - ESLINT_ROW_HEAD.length() - ESLINT_RULE.length()) + ESLINT_RULE + "\n");
            var beyond = EslintOutputParser.parse(
                    ESLINT_FILE + BLANK_RUN + ESLINT_ROW_HEAD + fit("m", LONG_LINE) + ESLINT_RULE + "\n");

            assertAll(
                    () -> assertEquals(1, atLimit.build().diagnostics().size(), "a row at the limit is filed"),
                    () -> assertTrue(atLimit.build().diagnostics().getFirst().message().startsWith("no-var: m"),
                            "the row at the limit keeps its rule"),
                    () -> assertTrue(beyond.build().diagnostics().isEmpty(), "a rule behind the cut is never read"));
        }

        @Test
        @DisplayName("Jest reads a summary at the limit and drops one beyond it")
        void jest() {
            var summary = "Tests:       3 passed, 3 total";

            var atLimit = JestOutputParser.parse(BLANK_RUN + atTheLimit(summary) + "\n");
            var beyond = JestOutputParser.parse(BLANK_RUN + beyondTheLimit(summary) + "\n");

            assertAll(
                    () -> assertEquals(TestCounts.from(3, 0, 0, null), atLimit.testCounts(),
                            "a summary at the limit is read whole"),
                    () -> assertTrue(atLimit.successMarker(), "the summary at the limit is the marker"),
                    () -> assertNull(beyond.testCounts(), "a summary behind the cut is never read"),
                    () -> assertFalse(beyond.successMarker(), "no summary read is no marker"));
        }

        @Test
        @DisplayName("TAP reads a failed point at the limit and drops one beyond it")
        void tap() {
            var failed = "not ok 1 - adds";

            var atLimit = TapOutputParser.parse(BLANK_RUN + atTheLimit(failed) + "\n");
            var beyond = TapOutputParser.parse(BLANK_RUN + beyondTheLimit(failed) + "\n");

            assertAll(
                    () -> assertSame(BuildStatus.FAILURE, atLimit.status(), "a failed point at the limit is read"),
                    () -> assertEquals(1, atLimit.diagnostics().size(), "the failed point at the limit is filed"),
                    () -> assertSame(BuildStatus.SUCCESS, beyond.status(), "a failed point behind the cut is unread"),
                    () -> assertTrue(beyond.diagnostics().isEmpty(), "a point behind the cut files nothing"));
        }
    }
}
