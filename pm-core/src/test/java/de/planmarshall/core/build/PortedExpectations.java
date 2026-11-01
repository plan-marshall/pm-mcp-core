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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Evaluates the fixture directives of the two parsed-build parsers and of the OpenRewrite parser.
 * <p>
 * The directives are the fixture's expectations, never values produced by the implementation: a fixture is derived from
 * the original's log and its pytest case by hand. Every expectation that is evaluated is recorded in the caller's
 * consumed set, so {@link BuildLogFixtures#assertConsumed} catches a directive no test looked at.
 *
 * @since 0.1
 */
final class PortedExpectations {

    private PortedExpectations() {
    }

    /**
     * Evaluates every expectation of a parsed build.
     *
     * @param fixture  the fixture, for its name in a failure
     * @param build    the parsed build
     * @param consumed the indices of the evaluated expectations, amended in place
     */
    static void parsedBuild(BuildLogFixtures.Case fixture, ParsedBuild build, Set<Integer> consumed) {
        List<BuildLogFixtures.Expectation> expectations = fixture.expectations();
        for (int index = 0; index < expectations.size(); index++) {
            BuildLogFixtures.Expectation expectation = expectations.get(index);
            switch (expectation.keyword()) {
                case "status" -> {
                    assertEquals(status(expectation.argument()), build.status(), "the build status matches the fixture");
                    consumed.add(index);
                }
                case "success-marker" -> {
                    assertEquals(Boolean.parseBoolean(expectation.argument()), build.successMarker(),
                            "the positive success marker matches");
                    consumed.add(index);
                }
                case "tests" -> {
                    assertTests(expectation, build);
                    consumed.add(index);
                }
                case "by-runner", "no-by-runner" -> {
                    assertRunner(expectation, build);
                    consumed.add(index);
                }
                case "no-tests-ran" -> {
                    assertEquals(Boolean.parseBoolean(expectation.argument()), build.noTestsRan(),
                            "the no_tests_ran verdict matches");
                    consumed.add(index);
                }
                case "diagnostic-count" -> {
                    assertEquals(Integer.parseInt(expectation.argument()), build.diagnostics().size(),
                            "the diagnostics count matches the fixture");
                    consumed.add(index);
                }
                case "no-diagnostic" -> {
                    assertFalse(messages(build).contains(expectation.argument()),
                            "the fixture line is not filed as a diagnostic");
                    consumed.add(index);
                }
                case "stack-trace-contains" -> {
                    assertStackTrace(expectation, build);
                    consumed.add(index);
                }
                case "diagnostic" -> {
                }
                default -> {
                }
            }
        }
        assertDiagnostics(fixture, build, consumed);
    }

    /**
     * Evaluates the expectations of an OpenRewrite log result.
     *
     * @param fixture  the fixture, for its name in a failure
     * @param result   the parsed result
     * @param consumed the indices of the evaluated expectations, amended in place
     */
    static void rewriteLog(BuildLogFixtures.Case fixture, RewriteLogResult result, Set<Integer> consumed) {
        List<BuildLogFixtures.Expectation> expectations = fixture.expectations();
        for (int index = 0; index < expectations.size(); index++) {
            BuildLogFixtures.Expectation expectation = expectations.get(index);
            switch (expectation.keyword()) {
                case "verdict" -> {
                    assertEquals(RewriteVerdict.valueOf(expectation.argument().toUpperCase(Locale.ROOT)),
                            result.verdict(), "the fail-closed verdict matches the fixture");
                    consumed.add(index);
                }
                case "finding-count" -> {
                    assertEquals(Integer.parseInt(expectation.argument()), result.findings().size(),
                            "the findings count matches the fixture");
                    consumed.add(index);
                }
                case "newly-detected" -> {
                    assertEquals(Integer.parseInt(expectation.argument()), result.newlyDetectedCount(),
                            "the newly detected count matches");
                    consumed.add(index);
                }
                case "pre-existing" -> {
                    assertEquals(Integer.parseInt(expectation.argument()), result.preExistingCount(),
                            "the pre-existing count matches");
                    consumed.add(index);
                }
                case "finding" -> {
                }
                default -> {
                }
            }
        }
        assertFindings(fixture, result, consumed);
    }

    /**
     * Returns the message of every diagnostic of a parsed build.
     *
     * @param build the parsed build
     * @return the messages in log order
     */
    static List<String> messages(ParsedBuild build) {
        return build.diagnostics().stream().map(Diagnostic::message).toList();
    }

    private static void assertTests(BuildLogFixtures.Expectation expectation, ParsedBuild build) {
        List<String> parts = expectation.split(5);
        String shape = parts.getFirst();
        switch (shape) {
            case "none" -> {
                assertEquals(1, parts.size(), "the 'tests none' expectation takes no argument");
                assertNull(build.testCounts(), "the fixture's log carries no test count");
            }
            case "unmeasured" -> {
                assertEquals(1, parts.size(), "the 'tests unmeasured' expectation takes no argument");
                var counts = assertInstanceOf(TestCounts.Unmeasured.class, build.testCounts(),
                        "the unaggregatable population reports unmeasured");
                assertEquals(TestsPopulation.UNMEASURED, counts.population(), "the population is unmeasured");
            }
            case "measured" -> {
                assertEquals(5, parts.size(), "the 'tests measured' expectation takes four numbers");
                var counts = assertInstanceOf(TestCounts.Measured.class, build.testCounts(),
                        "the aggregated population reports its counts");
                assertEquals(Integer.parseInt(parts.get(1)), counts.passed(), "the passed count matches");
                assertEquals(Integer.parseInt(parts.get(2)), counts.failed(), "the failed count matches");
                assertEquals(Integer.parseInt(parts.get(3)), counts.skipped(), "the skipped count matches");
                assertEquals(Integer.parseInt(parts.get(4)), counts.total(), "the collected count matches");
            }
            default -> throw new IllegalArgumentException("unknown 'tests' expectation: " + expectation.argument());
        }
    }

    private static void assertRunner(BuildLogFixtures.Expectation expectation, ParsedBuild build) {
        var counts = assertInstanceOf(TestCounts.Measured.class, build.testCounts(),
                "the population is measured when runners are attributed");
        if ("no-by-runner".equals(expectation.keyword())) {
            assertNull(counts.byRunner(), "the fixture expects no per-runner attribution");
            return;
        }
        List<String> parts = expectation.split(2);
        assertEquals(2, parts.size(), "the by-runner expectation names a runner and a count");
        assertTrue(counts.byRunner() != null && counts.byRunner().containsKey(parts.getFirst()),
                "the runner is attributed in the summary");
        assertEquals(Integer.parseInt(parts.get(1)), counts.byRunner().get(parts.getFirst()),
                "the executed count of the runner matches");
    }

    private static void assertStackTrace(BuildLogFixtures.Expectation expectation, ParsedBuild build) {
        assertFalse(build.diagnostics().isEmpty(), "a stack-trace expectation needs a diagnostic");
        String stackTrace = build.diagnostics().getLast().stackTrace();
        assertTrue(stackTrace != null && stackTrace.contains(expectation.argument()),
                "the collected stack trace holds the expected frame");
    }

    private static void assertDiagnostics(BuildLogFixtures.Case fixture, ParsedBuild build, Set<Integer> consumed) {
        List<BuildLogFixtures.Expectation> expectations = fixture.expectations();
        var expected = new ArrayList<BuildLogFixtures.Expectation>();
        for (int index = 0; index < expectations.size(); index++) {
            if ("diagnostic".equals(expectations.get(index).keyword())) {
                expected.add(expectations.get(index));
                consumed.add(index);
            }
        }
        if (expected.isEmpty()) {
            return;
        }
        if (expected.size() != build.diagnostics().size()) {
            throw new AssertionError("the fixture names " + expected.size() + " diagnostics, the parser filed "
                    + build.diagnostics().size() + ": " + build.diagnostics().stream()
                    .map(diagnostic -> diagnostic.severityName() + "/" + diagnostic.categoryName() + " "
                            + diagnostic.message())
                    .toList());
        }
        for (int position = 0; position < expected.size(); position++) {
            assertDiagnostic(expected.get(position), build.diagnostics().get(position), "diagnostic " + position);
        }
    }

    private static void assertDiagnostic(BuildLogFixtures.Expectation expectation, Diagnostic actual, String label) {
        List<String> parts = expectation.split(6);
        assertEquals(6, parts.size(), label + " names severity, category, file, line, column and message");
        assertEquals(Severity.valueOf(parts.get(0).toUpperCase(Locale.ROOT)), actual.severity(),
                label + ": the severity matches");
        assertEquals(Category.valueOf(parts.get(1).toUpperCase(Locale.ROOT)), actual.category(),
                label + ": the category matches");
        assertEquals(optional(parts.get(2)), actual.file(), label + ": the file matches");
        assertEquals(optionalInteger(parts.get(3)), actual.line(), label + ": the line matches");
        assertEquals(optionalInteger(parts.get(4)), actual.column(), label + ": the column matches");
        assertEquals(parts.get(5), actual.message(), label + ": the message matches");
    }

    private static void assertFindings(BuildLogFixtures.Case fixture, RewriteLogResult result, Set<Integer> consumed) {
        List<BuildLogFixtures.Expectation> expectations = fixture.expectations();
        var expected = new ArrayList<BuildLogFixtures.Expectation>();
        for (int index = 0; index < expectations.size(); index++) {
            if ("finding".equals(expectations.get(index).keyword())) {
                expected.add(expectations.get(index));
                consumed.add(index);
            }
        }
        if (expected.isEmpty()) {
            return;
        }
        assertEquals(expected.size(), result.findings().size(), "the fixture names every finding of its log");
        for (int position = 0; position < expected.size(); position++) {
            List<String> parts = expected.get(position).split(6);
            assertEquals(6, parts.size(), "a finding names six fields");
            RewriteFinding actual = result.findings().get(position);
            assertEquals(RewriteClassification.valueOf(parts.get(0).toUpperCase(Locale.ROOT)), actual.classification(),
                    "finding " + position + ": the classification matches");
            assertEquals(parts.get(1), actual.path(), "finding " + position + ": the path matches");
            assertEquals(Integer.parseInt(parts.get(2)), actual.line(), "finding " + position + ": the line matches");
            assertEquals(Integer.parseInt(parts.get(3)), actual.column(), "finding " + position + ": the column matches");
            assertEquals(parts.get(4), actual.recipe(), "finding " + position + ": the recipe matches");
            assertEquals(parts.get(5), actual.message(), "finding " + position + ": the message matches");
        }
    }

    private static BuildStatus status(String name) {
        return BuildStatus.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static String optional(String value) {
        return "-".equals(value) ? null : value;
    }

    private static Integer optionalInteger(String value) {
        return "-".equals(value) ? null : Integer.valueOf(value);
    }
}
