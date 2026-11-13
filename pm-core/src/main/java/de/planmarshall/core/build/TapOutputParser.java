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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native parse of a TAP (Test Anything Protocol) log, ported from {@code build-npm/_npm_parse_tap.py}.
 * <p>
 * Every {@code not ok N - <name>} line is a {@link Category#TEST_FAILURE} diagnostic. The YAML block that may follow it
 * supplies the message ({@code error:}), the location ({@code location:}) and the stack ({@code stack:}); without a block
 * the test name stands as the message. The {@code # tests} / {@code # pass} / {@code # fail} / {@code # skipped} summary
 * supplies the counts.
 * <p>
 * A successful run whose summary reports at least one passed test and no failed test is the tool's positive success
 * marker ({@link #printsPassingSummary}). An unreadable or {@link TestCounts.Unmeasured unmeasured} summary, a measured
 * zero, an all-skipped run, a printed failing count, a {@code Bail out!} line and a filed diagnostic all keep the marker
 * {@code false}. So does a top-level plan line {@code 1..N} whose {@code N} differs from the number of top-level test
 * points (ADR {@code core-build/009}): the run did not finish its plan, the same evidence class as a bailout, so it is no
 * passing summary even with exit 0. The counts stay as the summary measured them, summed over every run. A plan line
 * that matches its test points, an indented (subtest) plan line and a log without a top-level plan line change nothing.
 * The plan is checked per run: a run starts at a line that starts with {@code TAP version} (the lines before the first
 * one, or the whole log when it carries none, are one run), the counts of several top-level plan lines of one run are
 * summed and compared with that run's own top-level test points, and the log finished its plan only when every run did,
 * so a later run of concatenated runs cannot supply the point an earlier run is missing. A plan count too large for a
 * {@code long}, or plan counts whose sum overflows a {@code long}, leave the run unfinished (no passing summary): an
 * announced plan that cannot be verified never reads as finished. The plan is compared with the test points, not with the
 * {@code # tests} total, because {@code node --test} plans one top-level point per suite and counts the tests inside the
 * suites in {@code # tests}.
 * <p>
 * Two behaviours are deliberate changes against the original (ADR {@code core-build/003}): the original reads the first
 * occurrence of each summary line, and it trusts {@code # tests} over its parts. This port sums every summary block of a
 * multi-file run, and a total that is not the sum of its parts makes the population {@link TestCounts.Unmeasured}.
 * A summary count too large for a {@code long}, or summary counts whose sum overflows a {@code long}, leave the
 * population {@link TestCounts.Unmeasured} as well, beside the plan-count rule above: a wrapped sum never reads as a
 * measured population, and a {@code # fail} count too large for a {@code long} is still a printed failing count, so the
 * status is red. A measured zero is a measurement: {@code # fail 0} never produces a {@code no_tests_ran} verdict, because TAP prints no
 * positive "no tests" marker: a measured zero stays a measurement.
 * <p>
 * Every line is cut at {@link BuildLogText#MAX_LINE_LENGTH} characters by {@link BuildLogText#boundLines(String)} before
 * any pattern sees it, at the one entry both {@code parse} overloads share (ADR {@code core-build/007}).
 *
 * @since 0.1
 */
@UtilityClass
public class TapOutputParser {

    private static final String YAML_START = "---";

    private static final String YAML_END = "...";

    private static final String ERROR_KEY = "error:";

    private static final String LOCATION_KEY = "location:";

    private static final String STACK_KEY = "stack:";

    private static final String BLOCK_STACK = "|";

    private static final String STACK_INDENT = "        ";

    /** The start of a TAP version line, which begins a new run of a log that concatenates several. */
    private static final String VERSION_LINE = "TAP version";

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern NOT_OK = Pattern.compile("^\\s*not ok\\s+\\d+\\s*-\\s*(.+)$");

    /** A `not ok` test point, with or without its description: TAP permits both. */
    private static final Pattern NOT_OK_MARKER = Pattern.compile("^\\s*not ok\\b");

    /** A TAP bailout: the run stopped before its plan was complete, whatever the summaries say. */
    private static final Pattern BAIL_OUT = Pattern.compile("^\\s*Bail out!");

    private static final Pattern LOCATION = Pattern.compile("^(.+):(\\d+):\\d+");

    private static final Pattern TESTS = Pattern.compile("^#\\s*tests\\s+(\\d+)");

    private static final Pattern PASSED = Pattern.compile("^#\\s*pass\\s+(\\d+)");

    private static final Pattern FAILED = Pattern.compile("^#\\s*fail\\s+(\\d+)");

    private static final Pattern SKIPPED = Pattern.compile("^#\\s*skipped\\s+(\\d+)");

    /** A top-level plan line: at the start of the line, so an indented subtest plan is not one. */
    private static final Pattern PLAN = Pattern.compile("^1\\.\\.(\\d+)");

    /** A top-level test point, {@code ok} or {@code not ok}: at the start of the line, as its plan line is. */
    private static final Pattern TOP_LEVEL_POINT = Pattern.compile("^(?:not )?ok\\b");

    /**
     * Parses a TAP test log.
     *
     * @param logText the log text as the caller holds it; ANSI escape sequences are stripped here
     * @return the parsed build
     * @throws NullPointerException if the log text is null
     */
    public static ParsedBuild parse(String logText) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText));
    }

    /**
     * Reads and parses a TAP test log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static ParsedBuild parseStripped(String logText) {
        List<String> lines = withoutCarriageReturns(List.of(BuildLogText.boundLines(logText).split("\n", -1)));
        List<Diagnostic> diagnostics = extractDiagnostics(lines);
        TestCounts testCounts = extractTestCounts(lines).orElse(null);
        BuildStatus status = printsFailure(lines) ? BuildStatus.FAILURE : BuildStatus.SUCCESS;
        boolean successMarker = printsPassingSummary(status, testCounts, finishedPlan(lines));
        return ParsedBuild.builder().diagnostics(diagnostics).status(status).successMarker(successMarker)
                .testCounts(testCounts)
                .noTestsRan(ParsedBuild.noTestsRanFrom(status, successMarker, testCounts, false)).build();
    }

    /**
     * Whether the log printed TAP's passing summary: a successful status over a measured population with no failed test
     * and at least one passed test, in a run that finished its plan. A measured zero and an all-skipped run carry no
     * passed test, so they are no marker either; a run whose top-level plan lines {@code 1..N} sum to a count other than
     * its number of top-level test points, or whose plan count or summed count overflows a {@code long}, is an unfinished
     * plan, so it is no marker however the counts read.
     *
     * @param status       the status the tool printed
     * @param testCounts   the counts the summary supplied, or {@code null} when it carried none
     * @param finishedPlan whether the run finished its plan, see {@link #finishedPlan}
     * @return whether the passing summary is the positive success marker
     */
    private static boolean printsPassingSummary(BuildStatus status, @Nullable TestCounts testCounts,
            boolean finishedPlan) {
        return finishedPlan && status == BuildStatus.SUCCESS && testCounts instanceof TestCounts.Measured measured
                && measured.failed() == 0 && measured.passed() > 0;
    }

    /**
     * Whether every run of the log finished the plan it announced. A run starts at every line that starts with
     * {@code TAP version}; the lines before the first such line, or the whole log when it carries none, are one run, so
     * concatenated runs ({@code npm test --workspaces}) are checked one by one and a later run cannot supply the test point
     * an earlier run is missing. A run finished its plan when it announced no top-level plan line, or when the summed
     * count of its own top-level plan lines equals its own number of top-level test points ({@code ok} / {@code not ok}
     * at the start of a line). A top-level plan line whose count is too large for a {@code long}, or whose count makes
     * the run's sum overflow one, leaves that run unfinished. An indented (subtest) plan line and an indented test point belong to a subtest and are not
     * read. The plan counts test points, not the {@code # tests} total: {@code node --test} prints one top-level point
     * per suite and counts the tests inside them in {@code # tests}.
     *
     * @param lines the log lines
     * @return whether no run's top-level plan lines contradict that run's top-level test points
     */
    private static boolean finishedPlan(List<String> lines) {
        var run = new PlanRun();
        for (String line : lines) {
            if (line.startsWith(VERSION_LINE)) {
                if (!run.finished()) {
                    return false;
                }
                run = new PlanRun();
            }
            Matcher plan = PLAN.matcher(line);
            if (plan.find()) {
                run.announce(plan.group(1));
            } else if (TOP_LEVEL_POINT.matcher(line).find()) {
                run.point();
            }
        }
        return run.finished();
    }

    /**
     * Whether the log printed a failure marker: a {@code not ok} line, a {@code Bail out!} line or a {@code # fail}
     * count above zero, including one too large for a {@code long}. The status is what the tool printed, so a failure
     * marker without a filed diagnostic (a {@code not ok} line that names no test and carries no message, or a bailout
     * that stopped the run) is still red.
     *
     * @param lines the log lines
     * @return whether a failure marker was printed
     */
    private static boolean printsFailure(List<String> lines) {
        return lines.stream()
                .anyMatch(line -> NOT_OK_MARKER.matcher(line).find() || BAIL_OUT.matcher(line).find()
                        || digits(FAILED, line).filter(TapOutputParser::aboveZero).isPresent());
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        int index = 0;
        while (index < lines.size()) {
            Matcher notOk = NOT_OK.matcher(lines.get(index));
            if (notOk.find()) {
                index = addDiagnostic(lines, index, notOk.group(1), diagnostics, seen);
            } else {
                index++;
            }
        }
        return List.copyOf(diagnostics);
    }

    private static int addDiagnostic(List<String> lines, int index, String rawName, List<Diagnostic> diagnostics,
            Set<String> seen) {
        String testName = rawName.strip();
        var cursor = new Cursor(index + 1);
        YamlBlock block = yamlBlock(lines, cursor);
        String message = block.errorMessage().isEmpty() ? testName : block.errorMessage();
        if (!message.isBlank()) {
            Location location = location(block.location());
            var diagnostic = Diagnostic.builder().file(location.file()).line(location.line())
                    .message(DiagnosticCollector.stored(message)).category(Category.TEST_FAILURE)
                    .severity(Severity.ERROR).stackTrace(block.stackTrace()).build();
            if (seen.add(DiagnosticCollector.key(diagnostic))) {
                diagnostics.add(diagnostic);
            }
        }
        return cursor.index;
    }

    private static YamlBlock yamlBlock(List<String> lines, Cursor cursor) {
        var block = new YamlBlock();
        while (cursor.index < lines.size() && block.accept(lines.get(cursor.index))) {
            cursor.index++;
        }
        return block;
    }

    private static Location location(String rawLocation) {
        if (rawLocation.isEmpty()) {
            return new Location(null, null);
        }
        Matcher matcher = LOCATION.matcher(rawLocation);
        if (!matcher.find()) {
            return new Location(null, null);
        }
        try {
            return new Location(matcher.group(1), Integer.valueOf(matcher.group(2)));
        } catch (NumberFormatException _) {
            return new Location(matcher.group(1), null);
        }
    }

    private static Optional<TestCounts> extractTestCounts(List<String> lines) {
        var sum = new SummarySum();
        lines.forEach(sum::accept);
        return sum.result();
    }

    /**
     * The digits of a summary count, or empty when the line is no summary line of that kind.
     *
     * @param pattern the summary pattern, whose first group is the count
     * @param line    the line to inspect
     * @return the count's digits as printed
     */
    private static Optional<String> digits(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Whether printed count digits name a count above zero. The answer needs no parse, so a count too large for a
     * {@code long} is above zero as well.
     *
     * @param digits the count's digits
     * @return whether a digit other than {@code 0} is printed
     */
    private static boolean aboveZero(String digits) {
        return digits.chars().anyMatch(digit -> digit != '0');
    }

    private static List<String> withoutCarriageReturns(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return result;
    }

    private record Location(@Nullable String file, @Nullable Integer line) {
    }

    /** The YAML block of one {@code not ok} line, read line by line. */
    private static final class YamlBlock {

        private String errorMessage = "";

        private String location = "";

        private final List<String> stackLines = new ArrayList<>();

        private boolean inYaml;

        private boolean inStack;

        /**
         * Accepts one line of the block.
         *
         * @param yamlLine the line to inspect
         * @return whether the block continues with the next line
         */
        boolean accept(String yamlLine) {
            String stripped = yamlLine.strip();
            if (YAML_START.equals(stripped)) {
                inYaml = true;
                return true;
            }
            if (YAML_END.equals(stripped) || !inYaml) {
                return false;
            }
            if (stripped.startsWith(ERROR_KEY)) {
                errorMessage = stripQuotes(stripped.substring(ERROR_KEY.length()).strip());
            } else if (stripped.startsWith(LOCATION_KEY)) {
                location = stripQuotes(stripped.substring(LOCATION_KEY.length()).strip());
            } else if (stripped.startsWith(STACK_KEY)) {
                inStack = true;
                String stackValue = stripped.substring(STACK_KEY.length()).strip();
                if (!stackValue.isEmpty() && !BLOCK_STACK.equals(stackValue)) {
                    stackLines.add(stackValue);
                }
            } else if (inStack && yamlLine.startsWith(STACK_INDENT)) {
                stackLines.add(stripped);
            } else if (!yamlLine.startsWith(" ")) {
                return false;
            }
            return true;
        }

        String errorMessage() {
            return errorMessage;
        }

        String location() {
            return location;
        }

        @Nullable
        String stackTrace() {
            return stackLines.isEmpty() ? null : String.join("\n", stackLines);
        }

        private static String stripQuotes(String value) {
            int start = 0;
            int end = value.length();
            while (start < end && (value.charAt(start) == '\'' || value.charAt(start) == '"')) {
                start++;
            }
            while (end > start && (value.charAt(end - 1) == '\'' || value.charAt(end - 1) == '"')) {
                end--;
            }
            return value.substring(start, end);
        }
    }

    /**
     * The plan of one TAP run: the summed count of its top-level plan lines and its top-level test points. A plan count
     * too large for a {@code long}, or a sum of counts that overflows one, is an announced plan that cannot be verified,
     * so the run stays unfinished instead of wrapping to a count that could match its points. The points counter needs
     * no such guard, because the size of the input bounds it: the {@code Path} overload reads at most
     * {@link BuildLogText#MAX_BYTES} bytes, an {@code int}, the {@code String} overload is bounded by the length limit of
     * a Java {@code String}, and a test point takes at least three characters ({@code ok} and its line end), so fewer
     * than 2^31 points can be counted, far below the 2^63 a {@code long} needs to overflow. The cut of every line at
     * {@link BuildLogText#MAX_LINE_LENGTH} characters bounds the length of a line, not the number of lines, so it is not
     * that bound.
     */
    private static final class PlanRun {

        private long planned;

        private long points;

        private boolean announced;

        private boolean overflowed;

        void announce(String digits) {
            announced = true;
            if (overflowed) {
                return;
            }
            try {
                planned = Math.addExact(planned, Long.parseLong(digits));
            } catch (NumberFormatException | ArithmeticException _) {
                overflowed = true;
            }
        }

        void point() {
            points++;
        }

        boolean finished() {
            return !announced || (!overflowed && planned == points);
        }
    }

    /**
     * The summed {@code # tests}, {@code # pass}, {@code # fail} and {@code # skipped} counts of every summary block of a
     * log. Each count is added with {@link Math#addExact(long, long)}: a summary count too large for a {@code long}, or a
     * sum that overflows one, is a population nobody can verify, so the counts are {@link TestCounts.Unmeasured} instead
     * of wrapping into a figure that could pass as a measurement. The check that the total is the sum of its parts adds
     * exactly as well, so parts that wrap to the printed total are no measurement either.
     */
    private static final class SummarySum {

        private long total;

        private long passed;

        private long failed;

        private long skipped;

        private boolean found;

        private boolean overflowed;

        void accept(String line) {
            Optional<String> tests = digits(TESTS, line);
            if (tests.isPresent()) {
                found = true;
                total = add(total, tests.get());
            }
            passed = digits(PASSED, line).map(count -> add(passed, count)).orElse(passed);
            failed = digits(FAILED, line).map(count -> add(failed, count)).orElse(failed);
            skipped = digits(SKIPPED, line).map(count -> add(skipped, count)).orElse(skipped);
        }

        Optional<TestCounts> result() {
            if (!found) {
                return Optional.empty();
            }
            if (overflowed) {
                return Optional.of(TestCounts.unmeasured());
            }
            try {
                long parts = Math.addExact(Math.addExact(passed, failed), skipped);
                return Optional.of(total == parts
                        ? TestCounts.from(passed, failed, skipped, null)
                        : TestCounts.unmeasured());
            } catch (ArithmeticException _) {
                return Optional.of(TestCounts.unmeasured());
            }
        }

        private long add(long sum, String digits) {
            if (overflowed) {
                return sum;
            }
            try {
                return Math.addExact(sum, Long.parseLong(digits));
            } catch (NumberFormatException | ArithmeticException _) {
                overflowed = true;
                return sum;
            }
        }
    }

    private static final class Cursor {

        private int index;

        private Cursor(int index) {
            this.index = index;
        }
    }
}
