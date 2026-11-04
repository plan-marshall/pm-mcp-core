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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link JestOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-jest}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("JestOutputParser")
class JestOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-jest";

    private static final int FIXTURE_FLOOR = 3;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("closes a failure block before the summary line that follows it")
    void closesTheFailureBlock() {
        String logText = """
                FAIL  src/a.test.ts
                  ● a › fails

                      at Object.<anonymous> (src/a.test.ts:7:9)

                Tests:       1 failed, 2 passed, 3 total
                """;

        ParsedBuild build = JestOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "One block is filed as one diagnostic"),
                () -> assertEquals("src/a.test.ts", build.diagnostics().getFirst().file(),
                        "The suite header names the reported file"),
                () -> assertEquals(Integer.valueOf(7), build.diagnostics().getFirst().line(),
                        "The first stack frame supplies the line"),
                () -> assertEquals("a › fails", build.diagnostics().getFirst().message(),
                        "The test marker supplies the message"),
                () -> assertTrue(build.diagnostics().getFirst().stackTrace().endsWith("src/a.test.ts:7:9)"),
                        "The stack ends at the failure frame"));
    }

    @Test
    @DisplayName("ignores a FAIL header that names no suite")
    void ignoresABlankSuite() {
        String logText = String.join("\n", "FAIL   ", "  ● a › b", "",
                "      at Object.<anonymous> (src/a.ts:3:1)", "",
                "Tests:       1 failed, 0 passed, 1 total");

        ParsedBuild build = JestOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The block is still filed as one diagnostic"),
                () -> assertNull(build.diagnostics().getFirst().file(), "A blank suite header names no file"));
    }

    @Test
    @DisplayName("reads a failed Tests summary as a red status without a filed diagnostic")
    void readsAFailedSummaryAsFailure() {
        ParsedBuild build = JestOutputParser.parse("PASS src/a.test.ts\nTests:       1 failed, 2 passed, 3 total\n");

        assertAll(
                () -> assertEquals(0, build.diagnostics().size(), "The log carries no FAIL block"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "The printed failure count is red"));
    }

    @Test
    @DisplayName("reports a Tests summary the pattern cannot read as unmeasured")
    void reportsAnUnreadSummaryAsUnmeasured() {
        ParsedBuild build = JestOutputParser.parse("PASS src/a.test.ts\nTests:       1 failed, 1 todo, 2 total\n");

        assertAll(
                () -> assertInstanceOf(TestCounts.Unmeasured.class, build.testCounts(),
                        "A summary the pattern cannot read is no measurement"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "Its printed failure count is red"),
                () -> assertFalse(build.noTestsRan(), "An unmeasured population is no no-tests verdict"));
    }

    @Override
    ParsedBuild parse(String logText) {
        return JestOutputParser.parse(logText);
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
