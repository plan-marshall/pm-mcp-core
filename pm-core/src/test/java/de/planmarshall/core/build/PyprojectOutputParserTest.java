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

    private static final int FIXTURE_FLOOR = 22;

    private static final int ROSTER_CASES = 57;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("names a collection-error path that carries a space")
    void reportsASpacedPath() {
        String logText = "==================================== ERRORS ====================================\n"
                + "_ ERROR collecting test/broken module/test_x.py _\n"
                + "test/broken module/test_x.py:3: in <module>\n"
                + "    raise ImportError('no such thing')\n"
                + "E   ImportError: no such thing\n"
                + "=========================== short test summary info ============================\n"
                + "ERROR test/broken module/test_x.py\n"
                + "======================== 5 passed, 1 error in 2.00s ========================\n";

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
        String logText = "ERROR test/test_alpha.py::test_thing - "
                + "ValueError: could not import test/test_beta.py::test_other\n"
                + "======================== 5 passed, 1 error in 2.00s ========================\n";

        ParsedBuild build = PyprojectOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The ERROR line files one diagnostic"),
                () -> assertEquals("test/test_alpha.py", build.diagnostics().getFirst().file(),
                        "The path ends at the first .py of the line"),
                () -> assertEquals("ValueError: could not import test/test_beta.py::test_other",
                        build.diagnostics().getFirst().message(), "The message keeps its own path"));
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
