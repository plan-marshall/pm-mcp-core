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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link TapOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-tap}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("TapOutputParser")
class TapOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-tap";

    private static final int FIXTURE_FLOOR = 5;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("reads a test failure's YAML block, not the suite that contains it")
    void readsTheYamlBlock() {
        String logText = """
                TAP version 13
                not ok 2 - renders provided text
                  ---
                  location: '/p/App.test.js:15:5'
                  error: 'Expected "Hello" but got "Goodbye"'
                  stack: |
                        at TestContext.<anonymous> (file:///p/App.test.js:15:5)
                  ...
                # tests 1
                # pass 0
                # fail 1
                # skipped 0
                """;

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The not-ok line files one diagnostic"),
                () -> assertEquals("/p/App.test.js", build.diagnostics().getFirst().file(),
                        "The YAML location supplies the file"),
                () -> assertEquals(Integer.valueOf(15), build.diagnostics().getFirst().line(),
                        "The YAML location supplies the line"),
                () -> assertEquals("Expected \"Hello\" but got \"Goodbye",
                        build.diagnostics().getFirst().message(), "The YAML error supplies the message"),
                () -> assertTrue(build.diagnostics().getFirst().stackTrace().contains("App.test.js:15:5"),
                        "The YAML stack supplies a frame of the diagnostic"));
    }

    @Test
    @DisplayName("reads a bare not-ok line as a red status")
    void readsABareNotOkLineAsFailure() {
        ParsedBuild build = TapOutputParser.parse("TAP version 13\n1..1\nnot ok 1\n");

        assertAll(
                () -> assertEquals(0, build.diagnostics().size(), "A test point with no name files nothing"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "The printed marker is a red status"));
    }

    @Test
    @DisplayName("ignores a not-ok line that carries no message at all")
    void ignoresABlankTestName() {
        String logText = String.join("\n", "not ok 1 -   ", "# tests 1", "# pass 0", "# fail 1", "# skipped 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(0, build.diagnostics().size(), "A line with no identity and no message files nothing"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "A printed not-ok marker is a red status"));
    }

    @Test
    @DisplayName("files a nameless not-ok block that carries its own error")
    void filesANamelessBlockWithAnError() {
        String logText = String.join("\n", "not ok 1 -  ", "  ---", "  location: '/p/a.js:4:2'", "  error: 'boom'",
                "  ...", "# tests 1", "# pass 0", "# fail 1", "# skipped 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The block's own error is filed"),
                () -> assertEquals("boom", build.diagnostics().getFirst().message(), "The YAML error is the message"),
                () -> assertEquals("/p/a.js", build.diagnostics().getFirst().file(), "The location names the file"));
    }

    @Test
    @DisplayName("keeps the marker false when the top-level plan line names more tests than the summary ran (TAP wire format)")
    void planLineMismatchKeepsTheMarkerFalse() {
        String logText = String.join("\n", "TAP version 13", "ok 1 - first", "ok 2 - second", "ok 3 - third", "1..5",
                "# tests 3", "# pass 3", "# fail 0", "# skipped 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "An unfinished plan prints no failure"),
                () -> assertFalse(build.successMarker(), "An unfinished plan is no passing summary"),
                () -> assertEquals(TestCounts.from(3, 0, 0, null), build.testCounts(),
                        "The counts stay as the summary measured them"));
    }

    @Test
    @DisplayName("sets the marker when the top-level plan line matches the test points (TAP wire format)")
    void matchingPlanLineSetsTheMarker() {
        String logText = String.join("\n", "TAP version 13", "ok 1 - first", "ok 2 - second", "ok 3 - third", "1..3",
                "# tests 3", "# pass 3", "# fail 0", "# skipped 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "A finished plan prints no failure"),
                () -> assertTrue(build.successMarker(), "A finished passing plan is the success marker"));
    }

    @Test
    @DisplayName("ignores an indented subtest plan line (TAP wire format of node --test)")
    void indentedSubtestPlanLineChangesNothing() {
        String withSubtestPlan = String.join("\n", "TAP version 13", "# Subtest: suite", "    ok 1 - first",
                "    ok 2 - second", "    1..2", "ok 1 - suite", "1..1", "# tests 2", "# pass 2", "# fail 0",
                "# skipped 0");
        String withoutSubtestPlan = withSubtestPlan.replace("    1..2\n", "");

        ParsedBuild build = TapOutputParser.parse(withSubtestPlan);

        assertAll(
                () -> assertTrue(build.successMarker(), "An indented subtest plan keeps the marker"),
                () -> assertEquals(TapOutputParser.parse(withoutSubtestPlan).successMarker(), build.successMarker(),
                        "The subtest plan line changes no marker"),
                () -> assertEquals(TestCounts.from(2, 0, 0, null), build.testCounts(),
                        "The counts stay as the summary measured them"));
    }

    @Test
    @DisplayName("keeps the marker false for a skipped-only run (TAP wire format)")
    void skippedOnlyRunIsNoMarker() {
        String logText = String.join("\n", "TAP version 13", "ok 1 - first # SKIP", "ok 2 - second # SKIP", "1..2",
                "# tests 2", "# pass 0", "# fail 0", "# skipped 2");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "A skipped-only run prints no failure"),
                () -> assertFalse(build.successMarker(), "A skipped-only run is no passing summary"));
    }

    @Test
    @DisplayName("keeps the marker false for a passing summary beside a filed diagnostic (TAP wire format)")
    void passingSummaryBesideADiagnosticIsNoMarker() {
        String logText = String.join("\n", "TAP version 13", "# Subtest: suite", "    not ok 1 - inner",
                "      ---", "      error: 'boom'", "      ...", "    1..1", "ok 1 - suite", "1..1", "# tests 1",
                "# pass 1", "# fail 0", "# skipped 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The subtest failure is filed as a diagnostic"),
                () -> assertFalse(build.successMarker(), "A filed diagnostic keeps the marker false"));
    }

    @Test
    @DisplayName("checks the plan per run, so a later run cannot supply the point an earlier run missed (TAP wire format)")
    void laterRunCannotFinishAnEarlierPlan() {
        String logText = String.join("\n", "1..2", "ok 1 - first", "# tests 1", "# pass 1", "# fail 0",
                "TAP version 13", "ok 1 - second", "# tests 1", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "An unfinished first run prints no failure"),
                () -> assertFalse(build.successMarker(), "An unfinished first run is no passing summary"),
                () -> assertEquals(TestCounts.from(2, 0, 0, null), build.testCounts(),
                        "The counts stay summed over both runs"));
    }

    @Test
    @DisplayName("sets the marker when every run finishes its own plan (TAP wire format)")
    void everyRunFinishedSetsTheMarker() {
        String logText = String.join("\n", "TAP version 13", "1..2", "ok 1 - first", "ok 2 - second", "# tests 2",
                "# pass 2", "# fail 0", "TAP version 13", "1..1", "ok 1 - third", "# tests 1", "# pass 1",
                "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertTrue(build.successMarker(), "Two finished passing runs are the marker"),
                () -> assertEquals(TestCounts.from(3, 0, 0, null), build.testCounts(),
                        "The counts stay summed over both runs"));
    }

    @Test
    @DisplayName("sets the marker when a run without a plan precedes a run whose plan matches (TAP wire format)")
    void runWithoutPlanBeforeAFinishedRunSetsTheMarker() {
        String logText = String.join("\n", "TAP version 13", "ok 1 - first", "# tests 1", "# pass 1", "# fail 0",
                "TAP version 13", "1..1", "ok 1 - second", "# tests 1", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertTrue(build.successMarker(), "A run that announced no plan finished it");
    }

    @Test
    @DisplayName("reads a log without a version line as one run (TAP wire format)")
    void logWithoutVersionLineIsOneRun() {
        String matching = String.join("\n", "1..1", "ok 1 - first", "1..1", "ok 2 - second", "# tests 2",
                "# pass 2", "# fail 0");
        String nonMatching = String.join("\n", "1..2", "ok 1 - first", "1..1", "ok 2 - second", "# tests 2",
                "# pass 2", "# fail 0");

        assertAll(
                () -> assertTrue(TapOutputParser.parse(matching).successMarker(),
                        "The whole-log plan sum matching keeps the marker"),
                () -> assertFalse(TapOutputParser.parse(nonMatching).successMarker(),
                        "The whole-log plan sum differing drops the marker"));
    }

    @Test
    @DisplayName("keeps the marker false when the summed plan counts overflow a long (TAP wire format)")
    void overflowingPlanSumKeepsTheMarkerFalse() {
        // The Long.MAX_VALUE counts and the log shape are the contract: the sum wraps to 1, the one point.
        String logText = String.join("\n", "TAP version 13", "1..9223372036854775807", "1..9223372036854775807",
                "1..3", "ok 1 - first", "# tests 1", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "An overflowed plan prints no failure"),
                () -> assertFalse(build.successMarker(), "An overflowed plan sum is no passing summary"),
                () -> assertEquals(TestCounts.from(1, 0, 0, null), build.testCounts(),
                        "The counts stay as the summary measured them"));
    }

    @Test
    @DisplayName("keeps the marker false when an overflowed run precedes a finished run (TAP wire format)")
    void overflowedRunBeforeAFinishedRunKeepsTheMarkerFalse() {
        // The Long.MAX_VALUE counts and the log shape are the contract: the first run never finishes its plan.
        String logText = String.join("\n", "TAP version 13", "1..9223372036854775807", "1..9223372036854775807",
                "1..3", "ok 1 - first", "# tests 1", "# pass 1", "# fail 0", "TAP version 13", "1..1",
                "ok 1 - second", "# tests 1", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertFalse(build.successMarker(), "An overflowed first run is no passing summary"),
                () -> assertEquals(TestCounts.from(2, 0, 0, null), build.testCounts(),
                        "The counts stay summed over both runs"));
    }

    @Test
    @DisplayName("keeps the marker false for a plan count beyond the long range (TAP wire format)")
    void planCountBeyondTheLongRangeKeepsTheMarkerFalse() {
        // The count above Long.MAX_VALUE and the log shape are the contract: the plan cannot be verified.
        String logText = String.join("\n", "TAP version 13", "1..99999999999999999999", "ok 1 - first",
                "# tests 1", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "An unverifiable plan prints no failure"),
                () -> assertFalse(build.successMarker(), "An unverifiable plan is no passing summary"),
                () -> assertEquals(TestCounts.from(1, 0, 0, null), build.testCounts(),
                        "The counts stay as the summary measured them"));
    }

    @Test
    @DisplayName("sets the marker when two plan counts sum to the test points without overflow (TAP wire format)")
    void planCountsSummingToThePointsSetTheMarker() {
        String logText = String.join("\n", "TAP version 13", "1..2", "ok 1 - first", "ok 2 - second", "1..1",
                "ok 3 - third", "# tests 3", "# pass 3", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertTrue(build.successMarker(), "A plan sum equal to the points is the marker");
    }

    @Test
    @DisplayName("leaves the counts unmeasured when the summed test totals overflow a long (TAP wire format)")
    void overflowingTestsTotalIsUnmeasured() {
        // The Long.MAX_VALUE counts and the log shape are the contract: the totals wrap to 1, the one passed test.
        String logText = String.join("\n", "TAP version 13", "1..1", "ok 1 - first", "# tests 9223372036854775807",
                "# tests 9223372036854775807", "# tests 3", "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "An overflowed total prints no failure"),
                () -> assertFalse(build.successMarker(), "An overflowed total is no passing summary"),
                () -> assertEquals(TestCounts.unmeasured(), build.testCounts(),
                        "An overflowed total is no measured population"));
    }

    @Test
    @DisplayName("leaves the counts unmeasured when the summed pass counts overflow a long (TAP wire format)")
    void overflowingPassSumIsUnmeasured() {
        // The Long.MAX_VALUE counts and the log shape are the contract: the pass counts wrap to 1, the one test.
        String logText = String.join("\n", "TAP version 13", "1..1", "ok 1 - first", "# tests 1",
                "# pass 9223372036854775807", "# pass 9223372036854775807", "# pass 3", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertFalse(build.successMarker(), "An overflowed pass sum is no passing summary"),
                () -> assertEquals(TestCounts.unmeasured(), build.testCounts(),
                        "An overflowed pass sum is no measured population"));
    }

    @Test
    @DisplayName("leaves the counts unmeasured when pass and fail wrap to the printed total (TAP wire format)")
    void partsWrappingToTheTotalAreUnmeasured() {
        // The Long.MAX_VALUE counts and the log shape are the contract: pass + fail + skipped wrap to the total 1.
        String logText = String.join("\n", "TAP version 13", "1..1", "ok 1 - first", "# tests 1",
                "# pass 9223372036854775807", "# fail 9223372036854775807", "# skipped 3");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertFalse(build.successMarker(), "Wrapped parts are no passing summary"),
                () -> assertEquals(TestCounts.unmeasured(), build.testCounts(),
                        "Wrapped parts are no measured population"));
    }

    @Test
    @DisplayName("keeps summing two ordinary summary blocks to a measured population")
    void twoOrdinarySummaryBlocksStayMeasured() {
        int first = Generators.integers(1, 1000).next();
        int second = Generators.integers(1, 1000).next();
        String logText = String.join("\n", "TAP version 13", "# tests " + first, "# pass " + first, "# fail 0",
                "TAP version 13", "# tests " + second, "# pass " + second, "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertTrue(build.successMarker(), "Two passing blocks keep the passing summary"),
                () -> assertEquals(TestCounts.from((long) first + second, 0, 0, null), build.testCounts(),
                        "Two ordinary blocks sum to a measurement"));
    }

    @Test
    @DisplayName("leaves the counts unmeasured for a test total beyond the long range (TAP wire format)")
    void testsTotalBeyondTheLongRangeIsUnmeasured() {
        // The count above Long.MAX_VALUE and the log shape are the contract: the total cannot be read.
        String logText = String.join("\n", "TAP version 13", "1..1", "ok 1 - first", "# tests 99999999999999999999",
                "# pass 1", "# fail 0");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertFalse(build.successMarker(), "An unreadable total is no passing summary"),
                () -> assertEquals(TestCounts.unmeasured(), build.testCounts(),
                        "An unreadable total is no measured population"));
    }

    @Test
    @DisplayName("reads a fail count beyond the long range as a red status (TAP wire format)")
    void failCountBeyondTheLongRangeIsRed() {
        // The count above Long.MAX_VALUE and the log shape are the contract: a printed failing count is red.
        String logText = String.join("\n", "TAP version 13", "1..1", "ok 1 - first", "# tests 1", "# pass 1",
                "# fail 99999999999999999999");

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "A failing count of any size is red"),
                () -> assertFalse(build.successMarker(), "A failing count is no passing summary"),
                () -> assertEquals(TestCounts.unmeasured(), build.testCounts(),
                        "An unreadable fail count is no measurement"));
    }

    @Override
    ParsedBuild parse(String logText) {
        return TapOutputParser.parse(logText);
    }

    @Override
    String directory() {
        return DIRECTORY;
    }

    @Override
    int fixtureFloor() {
        return FIXTURE_FLOOR;
    }

    @Override
    int rosterCases() {
        return ROSTER_CASES;
    }
}
