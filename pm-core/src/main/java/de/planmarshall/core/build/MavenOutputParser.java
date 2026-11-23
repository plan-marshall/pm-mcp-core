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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.Builder;
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
        return parse(logText, false);
    }

    /**
     * Parses a Maven build log whose text may be the tail of a log a read cut.
     * <p>
     * The caller that owns the read passes the fact; {@link #parse(java.nio.file.Path)} takes it from
     * {@link BuildLogText#readLog(Path)}, and a caller that holds text whose read it cannot see (the daemon's
     * {@code JobLog}) passes it from the length of the file the read took. A cut log reports
     * {@link TestCounts.Unmeasured}, whatever the tail carries: the summary blocks of the modules in the lost head are not
     * in it, so no number of the tail is a count of the whole run. The failing tests the tail still shows are filed as a
     * {@code TEST_FAILURE} diagnostic at error severity (ADR {@code core-build/011}): each execution's visible count,
     * minus the failing tests the error-level {@code Tests run:} lines the collector filed for that execution report, and
     * only when that remainder is above zero (ADR {@code core-build/012} and {@code core-build/013}). An uncut text is
     * parsed as {@link #parse(String)} parses it.
     *
     * @param logText the log text as the caller holds it; ANSI escape sequences are stripped here
     * @param cut     whether the text is the tail alone of a log the read cut
     * @return the parsed build
     * @throws NullPointerException if the log text is null
     */
    public static ParsedBuild parse(String logText, boolean cut) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText), cut);
    }

    /**
     * Reads and parses a Maven build log.
     * <p>
     * A log the read cuts ({@link BuildLogText#readLog(Path)}) reports {@link TestCounts.Unmeasured}: a summary
     * block of a module in the lost head is not in it, so the tail's figure is no count of the whole run, and a cut
     * log never claims that no test ran. The failing tests the tail shows are filed as a {@code TEST_FAILURE} diagnostic
     * at error severity, because a visible failure is evidence whether or not the total is a measurement: each
     * execution's visible count, reduced by the failing tests the error-level count lines the collector filed for that
     * execution report, is added once, and an error-level {@code Tests run:} line of the tail stays its own diagnostic
     * beside it (ADR {@code core-build/011}, {@code core-build/012} and {@code core-build/013}).
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
        List<Diagnostic> filedDiagnostics = extractDiagnostics(lines);
        var scan = new CountScan(filedSeverities(filedDiagnostics));
        lines.forEach(scan::accept);
        List<Diagnostic> diagnostics = StackTraceCollector.attach(lines, filedDiagnostics);
        if (cut) {
            long unfiledFailures = scan.unfiledFailures();
            if (unfiledFailures > 0) {
                diagnostics = withVisibleFailures(diagnostics, unfiledFailures, scan.hasFiledFailingCount());
            }
        }
        TestCounts testCounts = scan.result(bounded, cut).orElse(null);
        BuildStatus status = detectStatus(bounded);
        boolean successMarker = bounded.contains(SUCCESS_MARKER);
        boolean noTestsRan = ParsedBuild.noTestsRanFrom(status, successMarker, testCounts,
                bounded.contains(NO_TESTS_MARKER));
        return new ParsedBuild(diagnostics, status, successMarker, testCounts, noTestsRan);
    }

    /**
     * Adds the one finding a cut log keeps from a tail that reports failing tests: the total is no measurement, but the
     * visible failing count is evidence a consumer must still see (ADR {@code core-build/011}).
     *
     * @param diagnostics       the diagnostics the log filed
     * @param failures          the failing tests the counted blocks report and no filed diagnostic carries yet
     * @param besideFiledCount  whether a filed {@code [ERROR]} count line already reports failing tests, so the message
     *                          names this figure as the remainder beside it
     * @return the diagnostics with the visible failing count filed as an error
     */
    private static List<Diagnostic> withVisibleFailures(List<Diagnostic> diagnostics, long failures,
            boolean besideFiledCount) {
        var plural = failures == 1 ? "" : "s";
        var message = String.format(Locale.ROOT, besideFiledCount
                ? "the cut log's tail reports %d further failing test%s"
                : "the cut log's tail reports %d failing test%s", failures, plural);
        var withFailures = new ArrayList<>(diagnostics);
        withFailures.add(Diagnostic.builder()
                .message(message)
                .category(Category.TEST_FAILURE).severity(Severity.ERROR).build());
        return withFailures;
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        return DiagnosticCollector.collect(lines, MavenOutputParser::filedDiagnostic);
    }

    /**
     * The severity of every diagnostic the collector retained, keyed by its identity.
     * <p>
     * The count scan reads this instead of re-deriving which count lines were filed: the collector keeps the first
     * occurrence of an identity and drops the later ones with their severity, so the retained diagnostic's severity is
     * the one that counts. A count line whose identical text stands at warning level before its error twin is therefore
     * filed at warning level, not at error level.
     *
     * @param diagnostics the diagnostics the collector retained, in log order
     * @return the retained identity's severity
     */
    private static Map<String, Severity> filedSeverities(List<Diagnostic> diagnostics) {
        Map<String, Severity> severities = new LinkedHashMap<>();
        for (Diagnostic diagnostic : diagnostics) {
            severities.put(DiagnosticCollector.key(diagnostic), diagnostic.severity());
        }
        return severities;
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
     * Java {@code String}), so each sum of the added values stays below 2^58. That bound governs the uncut result alone:
     * the cut tail's failing count is added outside it, and a digit count outside the {@code long} range saturates at
     * {@link Long#MAX_VALUE} instead of parsing as zero, so its sums saturate at {@link Long#MAX_VALUE} too.
     */
    private static final class CountScan {

        private final List<Counts> summaryLines = new ArrayList<>();

        private final List<Counts> ambiguousLines = new ArrayList<>();

        private final List<Counts> perClassLines = new ArrayList<>();

        private final List<Counts> countLines = new ArrayList<>();

        private final Map<String, Long> runnerExecuted = new LinkedHashMap<>();

        private final Map<String, Severity> filedSeverities;

        private @Nullable String currentRunner;

        private boolean resultsSeen;

        private boolean unrepresentable;

        private int execution;

        CountScan(Map<String, Severity> filedSeverities) {
            this.filedSeverities = filedSeverities;
        }

        /**
         * The counts of one {@code Tests run:} line.
         *
         * @param total        the collected tests
         * @param failed       the failed tests ({@code Failures} plus {@code Errors}, saturated at
         *                     {@link Long#MAX_VALUE})
         * @param skipped      the skipped tests
         * @param errorLevel   whether the retained diagnostic carries error severity; {@code false} for a line the
         *                     collector dropped as a duplicate of a warning, and for a line it never filed
         * @param execution    the ordinal of the nearest preceding plugin header, so two executions' figures are never
         *                     added together; every plugin header increments it, so under a parallel build the ordinals
         *                     interleave and an ordinal is the log's order, not a module
         * @param message      the line's stored message, the whole of it, so the count key does not collide where the
         *                     collector's first 100 characters do
         * @param collectorKey the identity the collector filed the line under; empty when it filed the line not, so the
         *                     cut tail must not file its count again
         */
        @Builder
        private record Counts(long total, long failed, long skipped, boolean errorLevel, int execution,
        String message, String collectorKey) {
        }

        void accept(String line) {
            var runner = executionHeaderRunner(line);
            if (runner.isPresent()) {
                currentRunner = TEST_RUNNERS.get(runner.get());
                resultsSeen = false;
                execution++;
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
            long failed = saturatedAdd(parse(match.group(2)), parse(match.group(3)));
            long skipped = parse(match.group(4));
            if (skipped > total) {
                unrepresentable = true;
            }
            var collectorKey = filedDiagnostic(line).map(DiagnosticCollector::key)
                    .filter(filedSeverities::containsKey);
            var counts = Counts.builder().total(total).failed(failed).skipped(skipped)
                    .errorLevel(collectorKey.map(filedSeverities::get).orElse(null) == Severity.ERROR)
                    .execution(execution).message(storedMessage(line))
                    .collectorKey(collectorKey.orElse("")).build();
            countLines.add(counts);
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
                // a digit count outside the long range is a count the log carries, so the cut path saturates it rather
                // than reading it as zero; the uncut result is Unmeasured through the same flag either way
                unrepresentable = true;
                return Long.MAX_VALUE;
            }
        }

        /**
         * The failing tests of a cut tail that no filed diagnostic already reports, 0 when there is none.
         * <p>
         * A cut log's tail may hold summary lines whose {@code Results:} marker is in the lost head; they are filed as
         * ambiguous by the scan, and for a cut log they count here too, so a failure the tail shows is never reported as
         * a passing run (ADR {@code core-build/011}). The tail's visible figure is counted per execution — the ordinal
         * of the nearest preceding plugin header, every plugin header incrementing it: the
         * summary side — summary and ambiguous blocks — and the per-class lines of one execution describe the same
         * failures, so the larger of the two sides is that execution's figure, and a per-class line beyond what the
         * execution's summary reports belongs to an execution whose summary the cut removed. The figures of two
         * executions are added, so a failure of an execution that printed no summary is not lost. A tail that carries no
         * execution header is one execution: its two sides cannot be told apart by the log, the larger side is then that
         * execution's figure, and it can be too low for a mix (ADR {@code core-build/012}).
         * <p>
         * The figure is then reduced by the failing tests the diagnostic collector retained at error level, counted AFTER
         * its deduplication: a line the collector dropped as a duplicate of an earlier one adds nothing, because the
         * failures it reports are already filed — and a failure whose identical line was retained as a warning is not
         * reduced, so it reaches the contradiction check as the remainder. The reduction is taken per execution against
         * that execution's own visible figure, and each execution's remainder is floored at zero before the remainders
         * are added: one execution that filed its per-class line and its summary (two identities, one failure) therefore
         * cannot spend its over-subtraction on another execution's failure, and a line of one execution is never reduced
         * against another execution's figure. A line the collector never filed — no {@code [ERROR]} or {@code [WARNING]}
         * marker — carries no identity into this reduction, so the identical error line that follows it still counts as
         * filed. Either side is counted after that same deduplication, so a count line that stands before the
         * {@code Results:} marker and again after it is one figure. The additions saturate at {@link Long#MAX_VALUE}
         * rather than wrap, so a hostile count cannot turn the remainder into zero, and a digit count outside the
         * {@code long} range saturates instead of parsing as zero. The remainder never goes below zero.
         *
         * @return the failing tests of the tail that are not yet a filed diagnostic
         */
        long unfiledFailures() {
            Map<Integer, Long> filed = filedFailures();
            long remainder = 0;
            for (var entry : visibleFailures().entrySet()) {
                long unfiled = Math.max(0, entry.getValue() - filed.getOrDefault(entry.getKey(), 0L));
                remainder = saturatedAdd(remainder, unfiled);
            }
            return remainder;
        }

        /**
         * The failing tests the cut tail shows per execution: within one execution the larger of its summary side and its
         * per-class lines.
         *
         * @return the tail's visible failing count per execution
         */
        private Map<Integer, Long> visibleFailures() {
            Map<Integer, long[]> perExecution = new LinkedHashMap<>();
            addFailures(perExecution,
                    Stream.concat(summaryLines.stream(), ambiguousLines.stream()).toList(), 0);
            addFailures(perExecution, perClassLines, 1);
            Map<Integer, Long> visible = new LinkedHashMap<>();
            for (var entry : perExecution.entrySet()) {
                visible.put(entry.getKey(), Math.max(entry.getValue()[0], entry.getValue()[1]));
            }
            return visible;
        }

        private static void addFailures(Map<Integer, long[]> perExecution, List<Counts> lines, int side) {
            for (Counts counts : distinct(lines)) {
                var sides = perExecution.computeIfAbsent(counts.execution(), _ -> new long[2]);
                sides[side] = saturatedAdd(sides[side], counts.failed());
            }
        }

        /**
         * The failing tests the count lines the collector retained at error level report, per execution and each
         * collector identity counted once. The collector keeps the first occurrence of an identity and drops the
         * later ones with whatever severity they carry, so a failure whose identical line was first filed as a warning is
         * not counted here: it stays in the remainder and reaches the contradiction check as an error. A count line the
         * collector never filed carries no identity here either.
         *
         * @return the failing tests an error diagnostic already reports, per execution
         */
        private Map<Integer, Long> filedFailures() {
            Set<String> seen = new HashSet<>();
            Map<Integer, Long> filed = new LinkedHashMap<>();
            for (Counts counts : countLines) {
                if (!counts.collectorKey().isEmpty() && seen.add(counts.collectorKey()) && counts.errorLevel()) {
                    filed.merge(counts.execution(), counts.failed(), CountScan::saturatedAdd);
                }
            }
            return filed;
        }

        /**
         * Adds two non-negative counts without wrapping: a sum past the {@code long} range saturates at
         * {@link Long#MAX_VALUE}, so a hostile count can neither turn the remainder into zero nor into a negative figure.
         * The count scan saturates a digit count the {@code long} range cannot hold at the same bound, so no overflow
         * enters a sum as zero or as a negative figure.
         *
         * @param left  the first count
         * @param right the second count
         * @return their sum, or {@link Long#MAX_VALUE} when it does not fit
         */
        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }

        /**
         * The count lines of one side counted once each: two lines with the same count key are one figure, so a
         * duplicated line never adds a failure the filed diagnostic does not report. The first of the equal lines in the
         * list's order is kept.
         * <p>
         * The key is the count path's own and is not the collector's identity: it is the execution ordinal plus the
         * whole stored message. Two failing classes whose messages differ only after the collector's first 100
         * characters are two figures here, and identical summary lines of two executions are two figures too. The cost is
         * the longer key (a stored message has at most 500 characters) and the divergence from the collector: two lines
         * this key tells apart are still one diagnostic where the collector's identity collides, so the count can exceed
         * what the collector filed and the difference is filed as the remainder.
         *
         * @param lines the count lines of one side; the summary side passes its summary lines and its ambiguous lines
         *              together, so a line that stands on both sides of the {@code Results:} marker counts once
         * @return the distinct lines
         */
        private static List<Counts> distinct(List<Counts> lines) {
            Map<String, Counts> distinct = new LinkedHashMap<>();
            for (Counts counts : lines) {
                distinct.putIfAbsent(countKey(counts), counts);
            }
            return List.copyOf(distinct.values());
        }

        private static String countKey(Counts counts) {
            return counts.execution() + ":" + counts.message();
        }

        private static String storedMessage(String line) {
            var message = LEVEL_PREFIX.matcher(line.strip()).replaceFirst("");
            return DiagnosticCollector.storedOrEmpty(message).orElse(message);
        }

        /**
         * Whether the collector filed an error-level count line that reports failing tests, so the synthetic finding is
         * a remainder beside it rather than the tail's whole visible count. A count line the collector dropped as a
         * duplicate of a warning is not filed error-level, so the message names no remainder the collector did not file.
         *
         * @return true when a filed error-level count line reports a failing test
         */
        boolean hasFiledFailingCount() {
            return filedFailures().values().stream().anyMatch(filed -> filed > 0);
        }

        Optional<TestCounts> result(String logText, boolean cut) {
            if (cut) {
                return Optional.of(TestCounts.unmeasured());
            }
            if (summaryLines.isEmpty() && ambiguousLines.isEmpty() && perClassLines.isEmpty()) {
                return Optional.empty();
            }
            boolean unmeasured = unrepresentable || !ambiguousLines.isEmpty()
                    || (summaryLines.isEmpty() && !perClassLines.isEmpty());
            if (unmeasured) {
                return Optional.of(TestCounts.unmeasured());
            }
            List<Counts> counted = summaryLines.isEmpty() ? perClassLines : summaryLines;
            long total = counted.stream().mapToLong(Counts::total).sum();
            long failed = counted.stream().mapToLong(Counts::failed).sum();
            long skipped = counted.stream().mapToLong(Counts::skipped).sum();
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
