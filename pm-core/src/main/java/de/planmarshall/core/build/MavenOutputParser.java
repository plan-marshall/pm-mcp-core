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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The native parse of a Maven build log, ported from {@code build-maven/_maven_cmd_parse.py}.
 * <p>
 * The parse is a pure function of the log text: a line scanner over the {@code [ERROR]} and {@code [WARNING]} lines with
 * the compiled JVM category table, stack-trace collection, deduplication across the reactor summary and the per-module
 * output, the Surefire and Failsafe summaries aggregated over every block, and the build status from Maven's own markers.
 * <p>
 * Three behaviours are deliberate changes against the original, each with its own fixture (ADR {@code core-build/002}):
 * <ul>
 * <li>An {@code [ERROR]} line no pattern recognises is {@link Category#UNRECOGNIZED}, never a real category. A warning
 * line without a match keeps the original's {@link Category#OTHER}.</li>
 * <li>A test population the parser cannot aggregate is {@link TestCounts.Unmeasured} and carries no number; the original
 * keeps the summed counts of an unmeasured population.</li>
 * <li>A successful build with no count line and Maven's own <code>No tests to run.</code> marker reports
 * {@code noTestsRan}; the original has no such verdict.</li>
 * </ul>
 * The failure trailer is <em>not</em> a change: the original drops the same lines with the same full match, and the port
 * holds that behaviour with fixtures of its own. A passing {@code Tests run:} line — zero failures and zero errors — is
 * not a diagnostic there either.
 *
 * @since 0.1
 */
@UtilityClass
public class MavenOutputParser {

    private static final Pattern LEVEL_PREFIX = Pattern.compile("^\\[(?:INFO|ERROR|WARNING)\\]\\s*");

    private static final Pattern TESTS_RUN = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    private static final Pattern EXECUTION_HEADER =
            Pattern.compile("---\\s+([\\w.\\-]+):[\\w.\\-]+:[\\w.\\-]+\\s.*@.*---");

    private static final Pattern RESULTS_MARKER = Pattern.compile("^(?:\\[(?:INFO|ERROR|WARNING)\\]\\s*)?Results\\s*:\\s*$");

    private static final Map<String, String> TEST_RUNNERS = Map.of(
            "surefire", "surefire",
            "maven-surefire-plugin", "surefire",
            "failsafe", "failsafe",
            "maven-failsafe-plugin", "failsafe");

    /**
     * Maven's failure epilogue: fixed advice, report pointers and banners Maven and Surefire print at error level after a
     * failed build. Each is matched whole against the prefix-stripped message.
     */
    private static final List<Pattern> EPILOGUE = List.of(
            Pattern.compile("To see the full stack trace of the errors, re-run Maven with the '?-e'? switch\\.?"),
            Pattern.compile("Re-run Maven using the '?-X'? switch to enable (full debug logging|verbose output)\\.?"),
            Pattern.compile(
                    "For more information about the errors and possible solutions, please read the following articles:"),
            Pattern.compile("\\[Help \\d+\\] https?://\\S+"),
            Pattern.compile("After correcting the problems, you can resume the build with the command"),
            Pattern.compile("mvn (<args>|\\[args\\]) -r(f :\\S+)?"),
            Pattern.compile("(Please refer to|See) .+ for the individual test results\\."),
            Pattern.compile("(Please refer to|See) dump files \\(if any exist\\) .+"),
            Pattern.compile("\\S.* \\.{3,} (SUCCESS|FAILURE|SKIPPED)( \\[.+\\])?"),
            Pattern.compile("BUILD FAILURE"));

    private static final String PARALLEL_BUILD_BANNER = "MultiThreadedBuilder";

    private static final String SUCCESS_MARKER = "BUILD SUCCESS";

    private static final String FAILURE_MARKER = "BUILD FAILURE";

    private static final String ERROR_MARKER = "[ERROR]";

    /** Maven's positive marker that an execution ran no test; the only source of a {@code no_tests_ran} verdict. */
    private static final String NO_TESTS_MARKER = "No tests to run.";

    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final int DEDUP_MESSAGE_LENGTH = 100;

    /**
     * Parses a Maven build log.
     *
     * @param logText the log text as the caller holds it; ANSI escape sequences are stripped here, so a colour-coded log
     *                matches the same plain-text patterns as an uncoloured one
     * @return the parsed build
     * @throws NullPointerException if the log text is null
     */
    public static ParsedBuild parse(String logText) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText));
    }

    /**
     * Reads and parses a Maven build log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static ParsedBuild parseStripped(String logText) {
        List<String> lines = List.of(logText.split("\n", -1));
        List<Diagnostic> diagnostics = StackTraceCollector.attach(lines, extractDiagnostics(lines));
        TestCounts testCounts = extractTestCounts(logText, lines);
        BuildStatus status = detectStatus(logText);
        boolean successMarker = logText.contains(SUCCESS_MARKER);
        boolean noTestsRan = status == BuildStatus.SUCCESS
                && successMarker
                && testCounts == null
                && logText.contains(NO_TESTS_MARKER);
        return new ParsedBuild(diagnostics, status, successMarker, testCounts, noTestsRan);
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("at ") || stripped.startsWith("Caused by:")) {
                continue;
            }
            @Nullable Severity severity = line.contains(ERROR_MARKER)
                    ? Severity.ERROR
                    : line.contains("[WARNING]") ? Severity.WARNING : null;
            if (severity == null) {
                continue;
            }
            String message = LEVEL_PREFIX.matcher(stripped).replaceFirst("");
            if (message.isEmpty() || message.startsWith("->")) {
                continue;
            }
            if (isEpilogue(message) || isPassingTestCount(message)) {
                continue;
            }
            var location = JvmFileLocation.parse(line);
            Category category = JvmCategoryPatterns.maven().categorize(message);
            if (category == Category.OTHER && severity == Severity.ERROR) {
                category = Category.UNRECOGNIZED;
            }
            addDeduped(diagnostics, seen, location, message, category, severity);
        }
        return diagnostics;
    }

    private static void addDeduped(List<Diagnostic> diagnostics, Set<String> seen, JvmFileLocation.Location location,
            String message, Category category, Severity severity) {
        String key = category.name() + ":" + location.file() + ":" + location.line() + ":"
                + message.substring(0, Math.min(message.length(), DEDUP_MESSAGE_LENGTH));
        if (!seen.add(key)) {
            return;
        }
        String stored = message.substring(0, Math.min(message.length(), MAX_MESSAGE_LENGTH));
        diagnostics.add(new Diagnostic(location.file(), location.line(), location.column(), stored, category, severity,
                null));
    }

    private static boolean isEpilogue(String message) {
        return EPILOGUE.stream().anyMatch(pattern -> pattern.matcher(message).matches());
    }

    private static boolean isPassingTestCount(String message) {
        Matcher match = TESTS_RUN.matcher(message);
        return match.find() && "0".equals(match.group(2)) && "0".equals(match.group(3));
    }

    private static BuildStatus detectStatus(String logText) {
        if (logText.contains(FAILURE_MARKER) || logText.contains(ERROR_MARKER)) {
            return BuildStatus.FAILURE;
        }
        return logText.contains(SUCCESS_MARKER) ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
    }

    private static @Nullable TestCounts extractTestCounts(String logText, List<String> lines) {
        var summaryLines = new ArrayList<int[]>();
        var ambiguousLines = new ArrayList<int[]>();
        var perClassLines = new ArrayList<int[]>();
        var runnerExecuted = new LinkedHashMap<String, Integer>();
        String currentRunner = null;
        boolean resultsSeen = false;
        for (String line : lines) {
            Matcher header = EXECUTION_HEADER.matcher(line);
            if (header.find()) {
                currentRunner = TEST_RUNNERS.get(header.group(1));
                resultsSeen = false;
                continue;
            }
            if (RESULTS_MARKER.matcher(line.strip()).matches()) {
                resultsSeen = true;
                continue;
            }
            Matcher match = TESTS_RUN.matcher(line);
            if (!match.find()) {
                continue;
            }
            int total = Integer.parseInt(match.group(1));
            int failed = Integer.parseInt(match.group(2)) + Integer.parseInt(match.group(3));
            int skipped = Integer.parseInt(match.group(4));
            int[] counts = {total, failed, skipped};
            if (line.contains("Time elapsed")) {
                perClassLines.add(counts);
            } else if (resultsSeen) {
                summaryLines.add(counts);
                if (currentRunner != null) {
                    runnerExecuted.merge(currentRunner, total - skipped, Integer::sum);
                }
            } else {
                ambiguousLines.add(counts);
            }
        }
        if (summaryLines.isEmpty() && ambiguousLines.isEmpty() && perClassLines.isEmpty()) {
            return null;
        }
        boolean unmeasured = !ambiguousLines.isEmpty() || (summaryLines.isEmpty() && !perClassLines.isEmpty());
        if (unmeasured) {
            return TestCounts.unmeasured();
        }
        List<int[]> counted = summaryLines.isEmpty() ? perClassLines : summaryLines;
        int total = counted.stream().mapToInt(counts -> counts[0]).sum();
        int failed = counted.stream().mapToInt(counts -> counts[1]).sum();
        int skipped = counted.stream().mapToInt(counts -> counts[2]).sum();
        Map<String, Integer> byRunner = runnerExecuted.isEmpty() || logText.contains(PARALLEL_BUILD_BANNER)
                ? null
                : runnerExecuted;
        return new TestCounts.Measured(total - failed - skipped, failed, skipped, total, byRunner);
    }
}
