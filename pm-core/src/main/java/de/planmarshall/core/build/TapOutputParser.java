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
 * {@code false}.
 * <p>
 * Two behaviours are deliberate changes against the original (ADR {@code core-build/003}): the original reads the first
 * occurrence of each summary line, and it trusts {@code # tests} over its parts. This port sums every summary block of a
 * multi-file run, and a total that is not the sum of its parts makes the population {@link TestCounts.Unmeasured}.
 * A measured zero is a measurement: {@code # fail 0} never produces a {@code no_tests_ran} verdict, because TAP prints no
 * positive "no tests" marker: a measured zero stays a measurement.
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
        List<String> lines = withoutCarriageReturns(List.of(logText.split("\n", -1)));
        List<Diagnostic> diagnostics = extractDiagnostics(lines);
        TestCounts testCounts = extractTestCounts(lines).orElse(null);
        BuildStatus status = printsFailure(lines) ? BuildStatus.FAILURE : BuildStatus.SUCCESS;
        boolean successMarker = printsPassingSummary(status, testCounts);
        return ParsedBuild.builder().diagnostics(diagnostics).status(status).successMarker(successMarker)
                .testCounts(testCounts)
                .noTestsRan(ParsedBuild.noTestsRanFrom(status, successMarker, testCounts, false)).build();
    }

    /**
     * Whether the log printed TAP's passing summary: a successful status over a measured population with no failed test
     * and at least one passed test. A measured zero and an all-skipped run carry no passed test, so they are no marker
     * either.
     *
     * @param status     the status the tool printed
     * @param testCounts the counts the summary supplied, or {@code null} when it carried none
     * @return whether the passing summary is the positive success marker
     */
    private static boolean printsPassingSummary(BuildStatus status, @Nullable TestCounts testCounts) {
        return status == BuildStatus.SUCCESS && testCounts instanceof TestCounts.Measured measured
                && measured.failed() == 0 && measured.passed() > 0;
    }

    /**
     * Whether the log printed a failure marker: a {@code not ok} line, a {@code Bail out!} line or a {@code # fail}
     * count above zero. The status is what the tool printed, so a failure marker without a filed diagnostic (a
     * {@code not ok} line that names no test and carries no message, or a bailout that stopped the run) is still red.
     *
     * @param lines the log lines
     * @return whether a failure marker was printed
     */
    private static boolean printsFailure(List<String> lines) {
        return lines.stream()
                .anyMatch(line -> NOT_OK_MARKER.matcher(line).find() || BAIL_OUT.matcher(line).find()
                        || count(FAILED, line).orElse(0L) > 0);
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
        long total = 0;
        long passed = 0;
        long failed = 0;
        long skipped = 0;
        boolean found = false;
        for (String line : lines) {
            Optional<Long> blockTests = count(TESTS, line);
            if (blockTests.isPresent()) {
                found = true;
                total += blockTests.get();
            }
            passed += count(PASSED, line).orElse(0L);
            failed += count(FAILED, line).orElse(0L);
            skipped += count(SKIPPED, line).orElse(0L);
        }
        if (!found) {
            return Optional.empty();
        }
        return Optional.of(total == passed + failed + skipped
                ? TestCounts.from(passed, failed, skipped, null)
                : TestCounts.unmeasured());
    }

    private static Optional<Long> count(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
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

    private static final class Cursor {

        private int index;

        private Cursor(int index) {
            this.index = index;
        }
    }
}
