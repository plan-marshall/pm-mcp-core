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
import static org.junit.jupiter.api.Assertions.assertNull;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link PyprojectOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/pyproject}, derived by hand from the original's log and its two pytest suites.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("PyprojectOutputParser")
class PyprojectOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/pyproject";

    private static final int FIXTURE_FLOOR = 21;

    private static final int ROSTER_CASES = 57;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("names a collection-error path that carries a space")
    void reportsASpacedPath() {
        String logText = """
                ==================================== ERRORS ====================================
                _ ERROR collecting test/broken module/test_x.py _
                test/broken module/test_x.py:3: in <module>
                    raise ImportError('no such thing')
                E   ImportError: no such thing
                =========================== short test summary info ============================
                ERROR test/broken module/test_x.py
                ========================= 5 passed, 1 error in 2.00s ========================
                """;

        ParsedBuild build = PyprojectOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The ERROR line files one diagnostic"),
                () -> assertEquals("test/broken module/test_x.py", build.diagnostics().getFirst().file(),
                        "The whole spaced path is reported"),
                () -> assertEquals(Integer.valueOf(3), build.diagnostics().getFirst().line(),
                        "The deepest frame supplies the line"),
                () -> assertEquals("ImportError: no such thing", build.diagnostics().getFirst().message(),
                        "The gutter supplies the exception line"));
    }

    @Test
    @DisplayName("splits the node id at the first path that ends it")
    void splitsAMessageNamingAPath() {
        String logText = """
                ERROR test/test_alpha.py::test_thing - ValueError: could not import test/test_beta.py::test_other
                ======================== 5 passed, 1 error in 2.00s ========================
                """;

        ParsedBuild build = PyprojectOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The ERROR line files one diagnostic"),
                () -> assertEquals("test/test_alpha.py", build.diagnostics().getFirst().file(),
                        "The path ends at the first .py of the line"),
                () -> assertEquals("ValueError: could not import test/test_beta.py::test_other",
                        build.diagnostics().getFirst().message(), "The message keeps its own path"));
    }

    @Test
    @DisplayName("categorises a mypy import failure through the Python table")
    void categorisesAnImportFailure() {
        ParsedBuild build = PyprojectOutputParser.parse("src/app.py:5: error: No module named 'requests'\n");

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The mypy line files one diagnostic"),
                () -> assertEquals(Category.IMPORT_ERROR, build.diagnostics().getFirst().category(),
                        "The Python pattern table names the import failure"));
    }

    @Test
    @DisplayName("reports no test count for a filter that matched nothing")
    void reportsNoCountForAMatchedNothingFilter() {
        ParsedBuild build = PyprojectOutputParser.parse("================ no tests ran in 0.02s ================\n");

        assertAll(
                () -> assertNull(build.testCounts(), "A filter that matched nothing yields no count"),
                () -> assertFalse(build.noTestsRan(), "No positive success marker was printed"),
                () -> assertEquals(BuildStatus.SUCCESS, build.status(), "The log carries no failing diagnostic"));
    }

    @Test
    @DisplayName("reads a stub-file and a column-numbered mypy line")
    void readsTheWiderMypyForms() {
        ParsedBuild build = PyprojectOutputParser.parse("src/types.pyi:12: error: Missing return statement\n"
                + "src/app.py:12:5: error: Incompatible types in assignment\n");

        assertAll(
                () -> assertEquals(2, build.diagnostics().size(), "Both mypy forms file one diagnostic"),
                () -> assertEquals("src/types.pyi", build.diagnostics().getFirst().file(),
                        "The stub path is reported"),
                () -> assertEquals(Integer.valueOf(12), build.diagnostics().getFirst().line(),
                        "The stub line is carried"),
                () -> assertEquals("src/app.py", build.diagnostics().get(1).file(),
                        "The column form names its file"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "A failing mypy run is red"));
    }

    @Test
    @DisplayName("files a FAILED line whose node id carries a space")
    void filesASpacedFailedId() {
        ParsedBuild build = PyprojectOutputParser.parse(
                "FAILED test/test_api.py::test_case[a b] - AssertionError: boom\n"
                        + "==================== 1 failed in 0.50s ====================\n");

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The spaced node id files one diagnostic"),
                () -> assertEquals("test/test_api.py", build.diagnostics().getFirst().file(), "The path is reported"),
                () -> assertEquals("AssertionError: boom", build.diagnostics().getFirst().message(),
                        "The summary tail is the message"));
    }

    @Override
    ParsedBuild parse(String logText) {
        return PyprojectOutputParser.parse(logText);
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
