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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

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

    /**
     * The head of an execution header, {@code --- group:plugin:goal }: the part of the ported
     * {@code ---\s+([\w.\-]+):[\w.\-]+:[\w.\-]+\s.*@.*---} before its {@code .*@.*---} tail. The quantifiers are
     * possessive, which changes no match because each class excludes the character that follows it, and the tail is
     * checked by {@link #executionHeaderRunner(String)} with index scans, so a header costs one pass over its line.
     */
    private static final Pattern EXECUTION_HEADER_HEAD =
            Pattern.compile("---\\s++([\\w.\\-]++):[\\w.\\-]++:[\\w.\\-]++\\s");

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
     * The runner an execution header names, empty when the line carries none.
     * <p>
     * The answer is the ported expression's: the leftmost {@link #EXECUTION_HEADER_HEAD} whose rest of the line, up to
     * the first line terminator {@code .} does not match, holds an {@code @} followed by {@code ---}. The scan is
     * linear in the line's length, also for a line of repeated {@code --- a:b:c @} text: one pass from the line's end
     * records, for every position, the next {@code @} and the last {@code ---} before the next terminator, so each head
     * is decided by two array reads instead of a scan of its rest.
     *
     * @param line the line to inspect
     * @return the runner named by the header, or empty when the line is no execution header
     */
    static Optional<String> executionHeaderRunner(String line) {
        if (line.indexOf('@') < 0) {
            return Optional.empty();
        }
        var length = line.length();
        var nextAt = new int[length + 1];
        var lastClose = new int[length + 1];
        markRunnerBounds(line, nextAt, lastClose);
        Matcher head = EXECUTION_HEADER_HEAD.matcher(line);
        var from = 0;
        while (from < length && head.find(from)) {
            var rest = head.end();
            if (nextAt[rest] >= 0 && lastClose[rest] > nextAt[rest]) {
                return Optional.of(head.group(1));
            }
            from = head.start() + 1;
        }
        return Optional.empty();
    }

    /**
     * The one pass from the line's end: for every position, the next {@code @} and the last {@code ---} before the next
     * line terminator, {@code -1} where there is none.
     *
     * @param line      the line scanned
     * @param nextAt    receives, per position, the index of the next {@code @}; one longer than the line
     * @param lastClose receives, per position, the index of the last {@code ---}; one longer than the line
     */
    private static void markRunnerBounds(String line, int[] nextAt, int[] lastClose) {
        var length = line.length();
        nextAt[length] = -1;
        lastClose[length] = -1;
        for (var index = length - 1; index >= 0; index--) {
            if (BuildLogText.isLineTerminator(line.charAt(index))) {
                nextAt[index] = -1;
                lastClose[index] = -1;
            } else {
                nextAt[index] = line.charAt(index) == '@' ? index : nextAt[index + 1];
                lastClose[index] = lastClose[index + 1] < 0 && line.startsWith("---", index)
                        ? index
                        : lastClose[index + 1];
            }
        }
    }

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
        return parseStripped(BuildLogText.stripAnsi(logText), false);
    }

    /**
     * Reads and parses a Maven build log.
     * <p>
     * A log the read cuts ({@link BuildLogText#readLog(Path)}) reports {@link TestCounts.Unmeasured}: a summary
     * block of a module in the lost head is not in it, so the tail's figure is no count of the whole run, and a cut
     * log never claims that no test ran (ADR {@code core-build/011}).
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        var read = BuildLogText.readLog(logFile);
        return parseStripped(read.text(), read.cut());
    }

    private static ParsedBuild parseStripped(String logText, boolean cut) {
        String bounded = BuildLogText.boundLines(logText);
        List<String> lines = List.of(bounded.split("\n", -1));
        List<Diagnostic> diagnostics = StackTraceCollector.attach(lines, extractDiagnostics(lines));
        TestCounts testCounts = extractTestCounts(bounded, lines, cut).orElse(null);
        BuildStatus status = detectStatus(bounded);
        boolean successMarker = bounded.contains(SUCCESS_MARKER);
        boolean noTestsRan = ParsedBuild.noTestsRanFrom(status, successMarker, testCounts,
                bounded.contains(NO_TESTS_MARKER));
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
        var stored = DiagnosticCollector.storedOrEmpty(message);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Diagnostic.builder().file(location.file()).line(location.line())
                .column(location.column()).message(stored.get())
                .category(categoryOf(message, severity.get())).severity(severity.get()).build());
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

    private static Optional<TestCounts> extractTestCounts(String logText, List<String> lines, boolean cut) {
        if (cut) {
            return Optional.of(TestCounts.unmeasured());
        }
        var scan = new CountScan();
        lines.forEach(scan::accept);
        return scan.result(logText);
    }

    /**
     * The running state of one pass over the count lines of a log: the summary, ambiguous and per-class blocks, and the
     * executed count per runner.
     * <p>
     * Counts are read and summed as {@code long}, so a count or a sum outside the {@code int} range is reported as
     * {@link TestCounts.Unmeasured} instead of wrapping into a figure that passes as a measurement. A count a line
     * carries beyond the {@code int} range marks the scan unrepresentable, and that mark makes the result guard report
     * {@code Unmeasured}; the values a representable scan adds are each at most {@link Integer#MAX_VALUE}, and the
     * input holds fewer than 2^26 count lines (a count line takes more than 40 characters, the {@code Path} overload
     * reads at most {@link BuildLogText#MAX_BYTES} bytes and the {@code String} overload is bounded by the length of a
     * Java {@code String}), so each sum of the added values stays below 2^58.
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
            var runner = executionHeaderRunner(line);
            if (runner.isPresent()) {
                currentRunner = TEST_RUNNERS.get(runner.get());
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
