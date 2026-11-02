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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private static final String RUNNER_SUREFIRE = "surefire";

    private static final String RUNNER_FAILSAFE = "failsafe";

    private static final String SUCCESS_MARKER = "BUILD SUCCESS";

    private static final String FAILURE_MARKER = "BUILD FAILURE";

    private static final String ERROR_MARKER = "[ERROR]";

    private static final String WARNING_MARKER = "[WARNING]";

    private static final String TIME_ELAPSED = "Time elapsed";

    /** Maven's positive marker that an execution ran no test; the only source of a {@code no_tests_ran} verdict. */
    private static final String NO_TESTS_MARKER = "No tests to run.";

    private static final String PARALLEL_BUILD_BANNER = "MultiThreadedBuilder";

    /** The stack-frame prefixes of a test failure, which the stack-trace collector reads instead. */
    private static final String FRAME_PREFIX = "at ";

    private static final String CAUSED_BY_PREFIX = "Caused by:";

    private static final String CONTINUATION_PREFIX = "->";

    private static final Pattern LEVEL_PREFIX = Pattern.compile("^\\[(?:INFO|ERROR|WARNING)\\]\\s*");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern TESTS_RUN = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern EXECUTION_HEADER =
            Pattern.compile("---\\s+([\\w.\\-]+):[\\w.\\-]+:[\\w.\\-]+\\s.*@.*---");

    private static final Pattern RESULTS_MARKER = Pattern.compile("^(?:\\[(?:INFO|ERROR|WARNING)\\]\\s*)?Results\\s*:\\s*$");

    private static final Map<String, String> TEST_RUNNERS = Map.of(
            RUNNER_SUREFIRE, RUNNER_SUREFIRE,
            "maven-surefire-plugin", RUNNER_SUREFIRE,
            RUNNER_FAILSAFE, RUNNER_FAILSAFE,
            "maven-failsafe-plugin", RUNNER_FAILSAFE);

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
            Pattern.compile(FAILURE_MARKER));

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
        TestCounts testCounts = extractTestCounts(logText, lines).orElse(null);
        BuildStatus status = detectStatus(logText);
        boolean successMarker = logText.contains(SUCCESS_MARKER);
        boolean noTestsRan = ParsedBuild.noTestsRanFrom(status, successMarker, testCounts,
                logText.contains(NO_TESTS_MARKER));
        return new ParsedBuild(diagnostics, status, successMarker, testCounts, noTestsRan);
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        return DiagnosticCollector.collect(lines, MavenOutputParser::filedDiagnostic);
    }

    private static Optional<Diagnostic> filedDiagnostic(String line) {
        String stripped = line.strip();
        if (stripped.startsWith(FRAME_PREFIX) || stripped.startsWith(CAUSED_BY_PREFIX)) {
            return Optional.empty();
        }
        Optional<Severity> severity = printedSeverity(line);
        if (severity.isEmpty()) {
            return Optional.empty();
        }
        String message = LEVEL_PREFIX.matcher(stripped).replaceFirst("");
        if (message.isEmpty() || message.startsWith(CONTINUATION_PREFIX) || isEpilogue(message)
                || isPassingTestCount(message)) {
            return Optional.empty();
        }
        var location = JvmFileLocation.parse(line);
        return Optional.of(new Diagnostic(location.file(), location.line(), location.column(),
                DiagnosticCollector.stored(message), categoryOf(message, severity.get()), severity.get(), null));
    }

    private static Optional<Severity> printedSeverity(String line) {
        if (line.contains(ERROR_MARKER)) {
            return Optional.of(Severity.ERROR);
        }
        return line.contains(WARNING_MARKER) ? Optional.of(Severity.WARNING) : Optional.empty();
    }

    private static Category categoryOf(String message, Severity severity) {
        Category category = JvmCategoryPatterns.maven().categorize(message);
        return category == Category.OTHER && severity == Severity.ERROR ? Category.UNRECOGNIZED : category;
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

    private static Optional<TestCounts> extractTestCounts(String logText, List<String> lines) {
        var scan = new CountScan();
        lines.forEach(scan::accept);
        return scan.result(logText);
    }

    /**
     * The running state of one pass over the count lines of a log: the summary, ambiguous and per-class blocks, and the
     * executed count per runner.
     * <p>
     * Counts are read and summed as {@code long}, so a count or a sum outside the {@code int} range is reported as
     * {@link TestCounts.Unmeasured} instead of wrapping into a figure that passes as a measurement.
     */
    private static final class CountScan {

        private final List<long[]> summaryLines = new ArrayList<>();

        private final List<long[]> ambiguousLines = new ArrayList<>();

        private final List<long[]> perClassLines = new ArrayList<>();

        private final Map<String, Long> runnerExecuted = new LinkedHashMap<>();

        private @Nullable String currentRunner;

        private boolean resultsSeen;

        private boolean unrepresentable;

        void accept(String line) {
            Matcher header = EXECUTION_HEADER.matcher(line);
            if (header.find()) {
                currentRunner = TEST_RUNNERS.get(header.group(1));
                resultsSeen = false;
            } else if (RESULTS_MARKER.matcher(line.strip()).matches()) {
                resultsSeen = true;
            } else {
                acceptCountLine(line);
            }
        }

        private void acceptCountLine(String line) {
            Matcher match = TESTS_RUN.matcher(line);
            if (!match.find()) {
                return;
            }
            long total = parse(match.group(1));
            long failed = parse(match.group(2)) + parse(match.group(3));
            long skipped = parse(match.group(4));
            if (skipped > total) {
                unrepresentable = true;
            }
            long[] counts = {total, failed, skipped};
            if (line.contains(TIME_ELAPSED)) {
                perClassLines.add(counts);
            } else if (resultsSeen) {
                summaryLines.add(counts);
                if (currentRunner != null) {
                    runnerExecuted.merge(currentRunner, total - skipped, Long::sum);
                }
            } else {
                ambiguousLines.add(counts);
            }
        }

        private long parse(String digits) {
            try {
                long value = Long.parseLong(digits);
                if (value > Integer.MAX_VALUE) {
                    unrepresentable = true;
                }
                return value;
            } catch (NumberFormatException _) {
                unrepresentable = true;
                return 0L;
            }
        }

        Optional<TestCounts> result(String logText) {
            if (summaryLines.isEmpty() && ambiguousLines.isEmpty() && perClassLines.isEmpty()) {
                return Optional.empty();
            }
            boolean unmeasured = unrepresentable || !ambiguousLines.isEmpty()
                    || (summaryLines.isEmpty() && !perClassLines.isEmpty());
            if (unmeasured) {
                return Optional.of(TestCounts.unmeasured());
            }
            List<long[]> counted = summaryLines.isEmpty() ? perClassLines : summaryLines;
            long total = counted.stream().mapToLong(counts -> counts[0]).sum();
            long failed = counted.stream().mapToLong(counts -> counts[1]).sum();
            long skipped = counted.stream().mapToLong(counts -> counts[2]).sum();
            Optional<Map<String, Integer>> byRunner = runnerExecuted.isEmpty()
                    || logText.contains(PARALLEL_BUILD_BANNER)
                            ? Optional.empty()
                            : runnerCounts();
            return Optional.of(TestCounts.from(total - failed - skipped, failed, skipped, byRunner.orElse(null)));
        }

        private Optional<Map<String, Integer>> runnerCounts() {
            var counts = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Long> entry : runnerExecuted.entrySet()) {
                if (entry.getValue() < 0 || entry.getValue() > Integer.MAX_VALUE) {
                    return Optional.empty();
                }
                counts.put(entry.getKey(), entry.getValue().intValue());
            }
            return Optional.of(counts);
        }
    }
}
