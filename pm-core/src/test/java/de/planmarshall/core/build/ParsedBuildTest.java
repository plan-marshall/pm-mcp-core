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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of {@link ParsedBuild}: the diagnostics are frozen, and the {@code no_tests_ran} verdict needs a
 * successful build with its positive marker and no count at all.
 */
@EnableGeneratorController
@DisplayName("ParsedBuild")
class ParsedBuildTest {

    @Test
    @DisplayName("freezes the diagnostics it was built from")
    void freezesDiagnostics() {
        var message = Generators.letterStrings(5, 20).next();
        var diagnostics = new ArrayList<Diagnostic>();
        diagnostics.add(new Diagnostic(null, null, null, message, Category.OTHER, Severity.WARNING, null));

        var build = new ParsedBuild(diagnostics, BuildStatus.SUCCESS, true, null, false);
        diagnostics.clear();

        assertEquals(1, build.diagnostics().size(), "The frozen list keeps its diagnostic");
        assertThrows(UnsupportedOperationException.class,
                () -> build.diagnostics().clear(),
                "The frozen list cannot be modified");
    }

    @Test
    @DisplayName("accepts the verdict on a green build that reported no count")
    void acceptsNoTestsRan() {
        var build = new ParsedBuild(List.of(), BuildStatus.SUCCESS, true, null, true);

        assertTrue(build.noTestsRan(), "A green build with its marker and no count ran no test");
    }

    @Test
    @DisplayName("refuses the verdict without a successful marked build")
    void refusesUnsatisfiedVerdict() {
        var counts = new TestCounts.Measured(1, 0, 0, 1, null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ParsedBuild(List.of(), BuildStatus.FAILURE, true, null, true),
                        "A failed build cannot have run no test"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ParsedBuild(List.of(), BuildStatus.SUCCESS, false, null, true),
                        "The verdict needs the tool's own success marker"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ParsedBuild(List.of(), BuildStatus.SUCCESS, true, counts, true),
                        "A measured count contradicts the verdict"));
    }

    @Test
    @DisplayName("keeps the green-with-failing-counts contradiction as two facts")
    void keepsTheContradiction() {
        var counts = new TestCounts.Measured(3, 2, 0, 5, null);

        var build = new ParsedBuild(List.of(), BuildStatus.SUCCESS, true, counts, false);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "The status is the tool's marker"),
                () -> assertTrue(build.successMarker(), "The success marker is recorded"),
                () -> assertFalse(build.noTestsRan(), "A count was reported, so no verdict applies"),
                () -> assertEquals(2, assertInstanceOf(TestCounts.Measured.class, build.testCounts()).failed(),
                        "The failing count stays visible beside the status"));
    }
}
