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

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The native parse of a TAP (Test Anything Protocol) log, ported from {@code build-npm/_npm_parse_tap.py}.
 * <p>
 * Every {@code not ok N - <name>} line is a {@link Category#TEST_FAILURE} diagnostic. The YAML block that may follow it
 * supplies the message ({@code error:}), the location ({@code location:}) and the stack ({@code stack:}); without a block
 * the test name stands as the message. The {@code # tests} / {@code # pass} / {@code # fail} / {@code # skipped} summary
 * supplies the counts.
 * <p>
 * Two behaviours are deliberate changes against the original (ADR {@code core-build/003}): the original reads the first
 * occurrence of each summary line, and it trusts {@code # tests} over its parts. This port sums every summary block of a
 * multi-file run, and a total that is not the sum of its parts makes the population {@link TestCounts.Unmeasured}.
 * A measured zero is a measurement: {@code # fail 0} never produces a {@code no_tests_ran} verdict, because TAP prints no
 * positive "no tests" marker: a measured zero stays a measurement.
 *
 * @since 0.1
 */
public final class TapOutputParser {

    private TapOutputParser() {
    }

    private static final String YAML_START = "---";

    private static final String YAML_END = "...";

    private static final String ERROR_KEY = "error:";

    private static final String LOCATION_KEY = "location:";

    private static final String STACK_KEY = "stack:";

    private static final String BLOCK_STACK = "|";

    private static final String STACK_INDENT = "        ";

    private static final Pattern NOT_OK = Pattern.compile("^\\s*not ok\\s+\\d+\\s*-\\s*(.+)$");

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
        BuildStatus status = diagnostics.isEmpty() ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
        return new ParsedBuild(diagnostics, status, false, testCounts,
                ParsedBuild.noTestsRanFrom(status, false, testCounts, false));
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        int index = 0;
        while (index < lines.size()) {
            Matcher notOk = NOT_OK.matcher(lines.get(index));
            if (!notOk.find()) {
                index++;
                continue;
            }
            String testName = notOk.group(1).strip();
            var cursor = new Cursor(index + 1);
            var block = yamlBlock(lines, cursor);
            String message = block.errorMessage().isEmpty() ? testName : block.errorMessage();
            Location location = location(block.location());
            var diagnostic = new Diagnostic(location.file(), location.line(), null,
                    DiagnosticCollector.stored(message), Category.TEST_FAILURE, Severity.ERROR,
                    block.stackTrace());
            if (seen.add(DiagnosticCollector.key(diagnostic))) {
                diagnostics.add(diagnostic);
            }
            index = cursor.index;
        }
        return List.copyOf(diagnostics);
    }

    private static YamlBlock yamlBlock(List<String> lines, Cursor cursor) {
        String errorMessage = "";
        String location = "";
        var stackLines = new ArrayList<String>();
        boolean inYaml = false;
        boolean inStack = false;
        while (cursor.index < lines.size()) {
            String yamlLine = lines.get(cursor.index);
            String stripped = yamlLine.strip();
            if (YAML_START.equals(stripped)) {
                inYaml = true;
            } else if (YAML_END.equals(stripped)) {
                break;
            } else if (inYaml) {
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
                    break;
                }
            } else {
                break;
            }
            cursor.index++;
        }
        String stackTrace = stackLines.isEmpty() ? null : String.join("\n", stackLines);
        return new YamlBlock(errorMessage, location, stackTrace);
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
        return Optional.of(total != passed + failed + skipped
                ? TestCounts.unmeasured()
                : TestCounts.from(passed, failed, skipped, null));
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

    private static List<String> withoutCarriageReturns(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return result;
    }

    private record YamlBlock(String errorMessage, String location, String stackTrace) {
    }

    private record Location(@Nullable String file, @Nullable Integer line) {
    }

    private static final class Cursor {

        private int index;

        private Cursor(int index) {
            this.index = index;
        }
    }
}
