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

import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * The result of parsing one build log: its diagnostics, its status, its counts and the verdict on the test population.
 * <p>
 * The record separates three facts a consumer must not conflate:
 * <ul>
 * <li>{@link #status()} is what the tool printed; {@link #successMarker()} is the positive marker it was derived from,
 * so a green banner over a failing test stays visible instead of being resolved here.</li>
 * <li>{@link #testCounts()} is {@code null} when the log carried no count line at all, {@link TestCounts.Unmeasured}
 * when it carried counts the parser could not aggregate, and {@link TestCounts.Measured} otherwise. Nothing reports a
 * zero in place of an absence (the aggregation defect PM-WATCH-JOB-10 recorded, closed by this package and deleted from
 * the watch document).</li>
 * <li>{@link #noTestsRan()} is a verdict, not the complement of a count: it holds only when the build reported success,
 * the tool printed its own positive "no tests" marker, and the log carried no count line. A measured count of zero never
 * produces it (PM-WATCH-JOB-11).</li>
 * </ul>
 *
 * @param diagnostics   the diagnostics in log order, never null
 * @param status        the status derived from the tool's markers, never null
 * @param successMarker whether the tool printed its positive success marker
 * @param testCounts    the test counts, or {@code null} when the log carried none
 * @param noTestsRan    whether the build positively reported that it ran no tests
 * @since 0.1
 */
@Builder
public record ParsedBuild(
List<Diagnostic> diagnostics,
BuildStatus status,
boolean successMarker,
@Nullable TestCounts testCounts,
boolean noTestsRan) {

    /**
     * Validates the verdict and freezes the diagnostics.
     *
     * @throws IllegalArgumentException if {@code noTestsRan} is claimed without a successful build carrying no count
     * @throws NullPointerException     if the diagnostics, the status or an element of the diagnostics is null
     */
    public ParsedBuild {
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(status, "status");
        if (noTestsRan
                && (status != BuildStatus.SUCCESS || !successMarker || testCounts != null)) {
            throw new IllegalArgumentException(
                    "no_tests_ran needs a successful build with its success marker and no test count");
        }
    }

    /**
     * Decides the {@code no_tests_ran} verdict of a parse: the rule is stated here once, beside the constructor that
     * enforces it, so no parser can restate it differently.
     *
     * @param status        the status the tool printed
     * @param successMarker whether the tool printed its positive success marker
     * @param testCounts    the test counts of the parse, {@code null} when the log carried no count line
     * @param noTestsMarker whether the tool printed its own positive "no tests" marker
     * @return whether the build positively reported that it ran no test
     */
    public static boolean noTestsRanFrom(BuildStatus status, boolean successMarker,
            @Nullable TestCounts testCounts, boolean noTestsMarker) {
        return status == BuildStatus.SUCCESS && successMarker && testCounts == null && noTestsMarker;
    }
}
