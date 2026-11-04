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
import lombok.experimental.UtilityClass;

/**
 * The native parse of a Jest test log, ported from {@code build-npm/_npm_parse_jest.py}.
 * <p>
 * The scanner follows Jest's report shape: a {@code FAIL <suite>} header names the file, a {@code ● <test name>} marker
 * opens a test block, the lines below it are its report, and a blank line after a stack frame closes the block. Each
 * closed block is a {@link Category#TEST_FAILURE} diagnostic carrying the suite, the line of the first stack frame and
 * the collected frames. The {@code Tests:} summary line supplies the counts.
 * <p>
 * Two behaviours are deliberate changes against the original (ADR {@code core-build/003}): the original reads the first
 * {@code Tests:} block only, and it trusts the block's {@code total} over its parts. This port sums every block of a
 * multi-package run, and a block whose total is not the sum of its parts makes the population
 * {@link TestCounts.Unmeasured} rather than a figure that silently disagrees with itself.
 *
 * @since 0.1
 */
@UtilityClass
public class JestOutputParser {

    private static final String TEST_MARKER = "●";

    private static final String STACK_FRAME = "at ";

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern FAIL_HEADER = Pattern.compile("^\\s*FAIL\\s+(.+)$");

    private static final Pattern STACK_LOCATION = Pattern.compile(":(\\d+):\\d+\\)?$");

    @SuppressWarnings({"java:S5843", "java:S8786"}) // ported pattern; the fixtures pin its behaviour
    private static final Pattern SUMMARY =
            Pattern.compile("Tests:\\s+(?:(\\d+)\\s+failed,\\s+)?(?:(\\d+)\\s+skipped,\\s+)?(?:(\\d+)\\s+passed,\\s+)?(\\d+)\\s+total");

    /**
     * Parses a Jest test log.
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
     * Reads and parses a Jest test log.
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
        var stack = new ArrayList<String>();
        String currentFile = null;
        String currentTest = null;
        boolean collecting = false;
        for (String line : lines) {
            Matcher fail = FAIL_HEADER.matcher(line);
            if (fail.matches()) {
                String file = fail.group(1).strip();
                currentFile = file.isBlank() ? null : file;
            } else if (line.strip().startsWith(TEST_MARKER)) {
                add(diagnostics, seen, currentFile, currentTest, stack);
                stack.clear();
                currentTest = line.strip().substring(TEST_MARKER.length()).strip();
                collecting = true;
            } else if (collecting) {
                String stripped = line.strip();
                if (!stripped.isEmpty()) {
                    stack.add(line);
                } else if (currentTest != null && !stack.isEmpty()
                        && stack.stream().anyMatch(frame -> frame.contains(STACK_FRAME))) {
                    add(diagnostics, seen, currentFile, currentTest, stack);
                    stack.clear();
                    collecting = false;
                    currentTest = null;
                }
            }
        }
        add(diagnostics, seen, currentFile, currentTest, stack);
        return List.copyOf(diagnostics);
    }

    private static void add(List<Diagnostic> diagnostics, HashSet<String> seen, @Nullable String file,
            @Nullable String test, List<String> stack) {
        if (test == null || test.isBlank() || stack.isEmpty()) {
            return;
        }
        Integer line = file == null ? null : stackLocation(stack);
        String stackTrace = String.join("\n", stack);
        var diagnostic = new Diagnostic(file, line, null, DiagnosticCollector.stored(test), Category.TEST_FAILURE,
                Severity.ERROR, stackTrace);
        if (seen.add(DiagnosticCollector.key(diagnostic))) {
            diagnostics.add(diagnostic);
        }
    }

    private static @Nullable Integer stackLocation(List<String> stack) {
        for (String frame : stack) {
            Matcher matcher = STACK_LOCATION.matcher(frame);
            if (matcher.find()) {
                try {
                    return Integer.valueOf(matcher.group(1));
                } catch (NumberFormatException _) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Optional<TestCounts> extractTestCounts(List<String> lines) {
        long passed = 0;
        long failed = 0;
        long skipped = 0;
        boolean found = false;
        boolean inconsistent = false;
        boolean unreadSummary = lines.stream()
                .anyMatch(line -> line.strip().startsWith("Tests:") && !SUMMARY.matcher(line).find());
        for (String line : lines) {
            Matcher matcher = SUMMARY.matcher(line);
            while (matcher.find()) {
                long blockFailed = parseCount(matcher.group(1));
                long blockSkipped = parseCount(matcher.group(2));
                long blockPassed = parseCount(matcher.group(3));
                long blockTotal = parseCount(matcher.group(4));
                if (blockPassed < 0 || blockFailed < 0 || blockSkipped < 0 || blockTotal < 0
                        || blockPassed + blockFailed + blockSkipped != blockTotal) {
                    inconsistent = true;
                }
                found = true;
                passed += Math.max(blockPassed, 0);
                failed += Math.max(blockFailed, 0);
                skipped += Math.max(blockSkipped, 0);
            }
        }
        if (!found && !unreadSummary) {
            return Optional.empty();
        }
        return Optional.of(inconsistent || unreadSummary || passed > Integer.MAX_VALUE || failed > Integer.MAX_VALUE
                || skipped > Integer.MAX_VALUE
                ? TestCounts.unmeasured()
                : TestCounts.from(passed, failed, skipped, null));
    }

    private static long parseCount(@Nullable String digits) {
        if (digits == null) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException _) {
            return -1L;
        }
    }

    private static List<String> withoutCarriageReturns(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return result;
    }
}
