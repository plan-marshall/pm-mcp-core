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

    private static final Pattern COMPLETED_TESTS =
            Pattern.compile("(\\d+) tests? completed(?:, (\\d+) failed)?(?:, (\\d+) skipped)?");

    private static final Set<Category> ERROR_CATEGORIES =
            Set.of(Category.COMPILATION_ERROR, Category.TEST_FAILURE, Category.DEPENDENCY_ERROR);

    private static final String SUCCESS_MARKER = "BUILD SUCCESSFUL";

    private static final String FAILURE_MARKER = "BUILD FAILED";

    /** Gradle's explicit statement that its test filter matched no test. */
    private static final String NO_TESTS_MARKER = "No tests found for given includes";

    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final int DEDUP_MESSAGE_LENGTH = 100;

    /**
     * Parses a Gradle build log.
     *
     * @param logText the log text, ANSI escapes already stripped
     * @return the parsed build
     */
    public static ParsedBuild parse(String logText) {
        Objects.requireNonNull(logText, "logText");
        List<String> lines = List.of(logText.split("\n", -1));
        List<Diagnostic> diagnostics = StackTraceCollector.attach(lines, extractDiagnostics(lines));
        TestCounts testCounts = extractTestCounts(lines);
        BuildStatus status = detectStatus(logText);
        boolean successMarker = logText.contains(SUCCESS_MARKER);
        boolean noTestsRan = status == BuildStatus.SUCCESS
                && successMarker
                && testCounts == null
                && logText.contains(NO_TESTS_MARKER);
        return new ParsedBuild(diagnostics, status, successMarker, testCounts, noTestsRan);
    }

    /**
     * Reads and parses a Gradle build log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parse(BuildLogText.read(logFile));
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("at ") || stripped.startsWith("Caused by:")
                    || stripped.startsWith("->") || stripped.startsWith(">")) {
                continue;
            }
            Category category = JvmCategoryPatterns.gradle().categorize(stripped);
            if (category == Category.OTHER) {
                continue;
            }
            var location = JvmFileLocation.parse(stripped);
            Severity severity = ERROR_CATEGORIES.contains(category) ? Severity.ERROR : Severity.WARNING;
            addDeduped(diagnostics, seen, location, stripped, category, severity);
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

    private static @Nullable TestCounts extractTestCounts(List<String> lines) {
        int total = 0;
        int failed = 0;
        int skipped = 0;
        boolean found = false;
        for (String line : lines) {
            Matcher match = COMPLETED_TESTS.matcher(line);
            if (!match.find()) {
                continue;
            }
            found = true;
            total += Integer.parseInt(match.group(1));
            failed += match.group(2) == null ? 0 : Integer.parseInt(match.group(2));
            skipped += match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
        }
        if (!found) {
            return null;
        }
        return new TestCounts.Measured(total - failed - skipped, failed, skipped, total, null);
    }

    private static BuildStatus detectStatus(String logText) {
        if (logText.contains(FAILURE_MARKER)) {
            return BuildStatus.FAILURE;
        }
        return logText.contains(SUCCESS_MARKER) ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
    }
}
