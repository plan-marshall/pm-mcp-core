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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The native parse of a Gradle build log, ported from {@code build-gradle/_gradle_cmd_parse.py}.
 * <p>
 * The scanner reads compiler error locations of the form {@code <path>.<java|kt|groovy|scala>:<line>[:<col>]}, the failing
 * tasks and the {@code N tests completed, M failed} summaries through the compiled JVM category table, and derives the
 * status from Gradle's own {@code BUILD SUCCESSFUL} and {@code BUILD FAILED} markers.
 * <p>
 * Gradle's severity follows the category, not a printed level: a compilation, test or dependency category is an error and
 * every other category is a warning. A line whose text matches no category is not filed at all, as in the original.
 * <p>
 * The counts follow the same rule as the Maven parser — every summary block contributes, never the last one alone (a
 * deliberate change against the original, which keeps the last match; ADR {@code core-build/002}). Because a successful
 * Gradle build prints no test count at all, the absence of a count is never read as a verdict: {@code no_tests_ran}
 * needs Gradle's own statement that its filter matched no test.
 *
 * @since 0.1
 */
@UtilityClass
public class GradleOutputParser {

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern COMPLETED_TESTS =
            Pattern.compile("^\\s*(\\d+) tests? completed(?:, (\\d+) failed)?(?:, (\\d+) skipped)?\\s*$");

    private static final Set<Category> ERROR_CATEGORIES =
            Set.of(Category.COMPILATION_ERROR, Category.TEST_FAILURE, Category.DEPENDENCY_ERROR);

    private static final String SUCCESS_MARKER = "BUILD SUCCESSFUL";

    private static final String FAILURE_MARKER = "BUILD FAILED";

    /** Gradle's explicit statement, on a line of its own, that its test filter matched no test. */
    private static final String NO_TESTS_MARKER = "No tests found for given includes";

    private static final String FRAME_PREFIX = "at ";

    private static final String CAUSED_BY_PREFIX = "Caused by:";

    private static final String CONTINUATION_PREFIX = "->";

    private static final String TASK_PREFIX = ">";

    /**
     * Parses a Gradle build log.
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
     * Reads and parses a Gradle build log.
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
        TestCounts testCounts = extractTestCounts(lines).orElse(null);
        BuildStatus status = detectStatus(logText);
        boolean successMarker = logText.contains(SUCCESS_MARKER);
        boolean noTestsRan = ParsedBuild.noTestsRanFrom(status, successMarker, testCounts,
                hasNoTestsMarker(logText));
        return new ParsedBuild(diagnostics, status, successMarker, testCounts, noTestsRan);
    }

    private static boolean hasNoTestsMarker(String logText) {
        return logText.lines().anyMatch(line -> line.strip().startsWith(NO_TESTS_MARKER));
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        return DiagnosticCollector.collect(lines, GradleOutputParser::filedDiagnostic);
    }

    private static Optional<Diagnostic> filedDiagnostic(String line) {
        String stripped = line.strip();
        if (isSkippedLine(stripped)) {
            return Optional.empty();
        }
        Category category = JvmCategoryPatterns.gradle().categorize(stripped);
        if (category == Category.OTHER) {
            return Optional.empty();
        }
        var location = JvmFileLocation.parse(stripped);
        Severity severity = ERROR_CATEGORIES.contains(category) ? Severity.ERROR : Severity.WARNING;
        return Optional.of(new Diagnostic(location.file(), location.line(), location.column(),
                DiagnosticCollector.stored(stripped), category, severity, null));
    }

    private static boolean isSkippedLine(String stripped) {
        return stripped.startsWith(FRAME_PREFIX) || stripped.startsWith(CAUSED_BY_PREFIX)
                || stripped.startsWith(CONTINUATION_PREFIX) || stripped.startsWith(TASK_PREFIX);
    }

    private static Optional<TestCounts> extractTestCounts(List<String> lines) {
        long total = 0;
        long failed = 0;
        long skipped = 0;
        boolean found = false;
        for (String line : lines) {
            Matcher match = COMPLETED_TESTS.matcher(line);
            if (!match.matches()) {
                continue;
            }
            long blockTotal = parseCount(match.group(1));
            long blockFailed = parseCount(match.group(2));
            long blockSkipped = parseCount(match.group(3));
            if (blockTotal < 0 || blockFailed < 0 || blockSkipped < 0 || blockSkipped > blockTotal
                    || blockTotal > Integer.MAX_VALUE - total || blockFailed > Integer.MAX_VALUE - failed
                    || blockSkipped > Integer.MAX_VALUE - skipped) {
                return Optional.of(TestCounts.unmeasured());
            }
            found = true;
            total += blockTotal;
            failed += blockFailed;
            skipped += blockSkipped;
        }
        if (!found) {
            return Optional.empty();
        }
        return Optional.of(TestCounts.from(total - failed - skipped, failed, skipped, null));
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

    private static BuildStatus detectStatus(String logText) {
        if (logText.contains(FAILURE_MARKER)) {
            return BuildStatus.FAILURE;
        }
        return logText.contains(SUCCESS_MARKER) ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
    }
}
