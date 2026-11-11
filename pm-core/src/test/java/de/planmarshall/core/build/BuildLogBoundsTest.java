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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.LimitProperties;

/**
 * The bounds of a build log's parse (R07): every line is cut at the per-line limit before a parser's patterns see it,
 * and the three patterns that backtrack over a line of repeated tokens answer inside a stated bound on a hostile line.
 * <p>
 * The bound is measured through the parser, not through the expression: a test that proves a pattern is no longer
 * quadratic names its input and uses a preemptive timeout, never a comparison of two wall-clock runs. The hostile line
 * is far longer than the limit, so it is the cut that bounds the parse, and the second group repeats hostile lines at
 * exactly the limit, where no cut helps and only the rewrite does.
 * <p>
 * The two reference expressions are the ported forms the rewrite replaced: the differential tests hold the rewrite to
 * their result over generated lines, so a rewrite that changes a verdict fails here before it reaches a fixture.
 */
@EnableGeneratorController
@DisplayName("Build log bounds")
class BuildLogBoundsTest {

    /** The bound a hostile line answers inside; it names the length of the input beside it. */
    private static final Duration HOSTILE_BOUND = Duration.ofSeconds(1);

    /** The per-line limit the parsers apply. */
    private static final int LIMIT = BuildLogText.MAX_LINE_LENGTH;

    /** The length of the hostile line the parsers are handed: far over the limit, so only the cut can bound it. */
    private static final int LONG_LINE = LIMIT * 32;

    /** The lines repeated at exactly the limit: no cut applies to them, so only a linear rewrite meets the bound. */
    private static final int LINES_AT_THE_LIMIT = 200;

    /** The number of generated lines the differential tests compare. */
    private static final int GENERATED_LINES = 500;

    /** The ported Maven header expression, as it was before the pre-test was added. */
    private static final Pattern PORTED_EXECUTION_HEADER =
            Pattern.compile("---\\s+([\\w.\\-]+):[\\w.\\-]+:[\\w.\\-]+\\s.*@.*---");

    /** The ported pytest summary expression, as it was before it was answered in two steps. */
    private static final Pattern PORTED_SUMMARY_LINE =
            Pattern.compile("^.*\\b(?:passed|failed|skipped)\\b.*\\bin\\s+[\\d.]+s.*$");

    /** The ported OpenRewrite finding expression, as it was before the pre-test was added. */
    private static final Pattern PORTED_FINDING = Pattern.compile(
            "CUI_REWRITE-(10[01]): Finding (?:detected|pre-existing) at (.+?):(\\d+):(\\d+) by (.+?): (.*)$");

    /** The fragments a generated line is built from: the tokens the three rewritten patterns read. */
    private static final List<String> FRAGMENTS = List.of(
            "---", " ", "\t", "a", "a:b:c", ":", "@", "--- a:b:c ", "x.y-z", "CUI_REWRITE-100", "CUI_REWRITE-101",
            "CUI_REWRITE-10", ": Finding detected at ", ": Finding pre-existing at ", " by ", "y", ": ", "1", "12", ".5",
            "s", "passed", "failed", "skipped", " in ", "in 1.5s", "took 2s", "E", "=", "\r", "\u2028", "x");

    private static String fit(String unit, int length) {
        var builder = new StringBuilder(length + unit.length());
        while (builder.length() < length) {
            builder.append(unit);
        }
        return builder.substring(0, length);
    }

    private static String generatedLine() {
        var count = Generators.integers(0, 8).next();
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
                "a Maven line of " + LONG_LINE + " characters is parsed inside the bound");

        assertTrue(parsed.successMarker(), "the status marker is still read");
    }

    @Test
    @DisplayName("hostile Maven header lines at the limit return inside the bound")
    void boundsRepeatedMavenHeaderLines() {
        var hostile = fit("--- a:b:c ", LIMIT);
        MavenOutputParser.parse("BUILD SUCCESS\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> MavenOutputParser.parse("BUILD SUCCESS\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the parser answers " + LINES_AT_THE_LIMIT + " hostile lines of " + LIMIT + " characters");

        assertTrue(parsed.successMarker(), "the status marker is still read");
    }

    @Test
    @DisplayName("a hostile rewrite finding line returns inside the bound")
    void boundsTheRewriteFindingPattern() {
        var hostile = fit("CUI_REWRITE-100: Finding detected at x", LONG_LINE);
        OpenRewriteLogParser.parse("--- rewrite:run (\n", true);

        var result = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> OpenRewriteLogParser.parse("--- rewrite:run (\n" + hostile + "\n", true),
                "a finding line of " + LONG_LINE + " characters is parsed inside the bound");

        assertSame(result.verdict(), RewriteVerdict.PARSE_ERROR, "the hostile line is a parse error");
    }

    @Test
    @DisplayName("a hostile pytest summary line returns inside the bound")
    void boundsThePytestSummaryPattern() {
        var hostile = fit("passed ", LONG_LINE);
        PyprojectOutputParser.parse("=== FAILURES ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> PyprojectOutputParser.parse("=== FAILURES ===\n" + hostile + "\n"),
                "a summary line of " + LONG_LINE + " characters is parsed inside the bound");

        assertTrue(parsed.diagnostics().isEmpty(), "the hostile line locates no diagnostic");
    }

    @Test
    @DisplayName("hostile rewrite finding lines at the limit return inside the bound")
    void boundsRepeatedRewriteFindingLines() {
        var hostile = fit("CUI_REWRITE-100: Finding detected at x", LIMIT);
        OpenRewriteLogParser.parse("--- rewrite:run (\n", true);

        var result = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> OpenRewriteLogParser.parse("--- rewrite:run (\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT),
                        true),
                "the parser answers " + LINES_AT_THE_LIMIT + " hostile lines of " + LIMIT + " characters");

        assertSame(result.verdict(), RewriteVerdict.PARSE_ERROR, "the hostile line is a parse error");
    }

    @Test
    @DisplayName("hostile pytest section and exception lines return inside the bound")
    void boundsThePytestSectionAndExceptionPatterns() {
        var section = fit("= a", LIMIT);
        var exception = "E x" + fit(" ", LIMIT - 3);
        var log = "=== ERRORS ===\n"
                + "____ ERROR at setup of test_x.py ____\n"
                + exception + "\n"
                + section + "\n"
                + "=== short test summary info ===\n"
                + "ERROR at setup of test_x.py\n";
        PyprojectOutputParser.parse("=== ERRORS ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND, () -> PyprojectOutputParser.parse(log),
                "the section and exception patterns answer a hostile line of " + LIMIT + " characters");

        assertEquals(1, parsed.diagnostics().size(), "the error summary line locates one diagnostic");
    }

    @Test
    @DisplayName("hostile pytest summary lines at the limit return inside the bound")
    void boundsRepeatedPytestSummaryLines() {
        var hostile = fit("passed ", LIMIT);
        PyprojectOutputParser.parse("=== FAILURES ===\n");

        var parsed = assertTimeoutPreemptively(HOSTILE_BOUND,
                () -> PyprojectOutputParser.parse("=== FAILURES ===\n" + (hostile + "\n").repeat(LINES_AT_THE_LIMIT)),
                "the parser answers " + LINES_AT_THE_LIMIT + " hostile lines of " + LIMIT + " characters");

        assertTrue(parsed.diagnostics().isEmpty(), "the hostile lines locate no diagnostic");
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
        for (var index = 0; index < GENERATED_LINES; index++) {
            var line = generatedLine();
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
}
