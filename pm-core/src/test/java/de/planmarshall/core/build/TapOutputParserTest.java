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
 * The ported tests of {@link TapOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code build/npm-tap}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("TapOutputParser")
class TapOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-tap";

    private static final int FIXTURE_FLOOR = 4;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("reads a test failure's YAML block, not the suite that contains it")
    void readsTheYamlBlock() {
        String logText = "TAP version 13\n"
                + "not ok 2 - renders provided text\n"
                + "  ---\n"
                + "  location: '/p/App.test.js:15:5'\n"
                + "  error: 'Expected \"Hello\" but got \"Goodbye\"'\n"
                + "  stack: |\n"
                + "    at TestContext.<anonymous> (file:///p/App.test.js:15:5)\n"
                + "  ...\n"
                + "# tests 1\n"
                + "# pass 0\n"
                + "# fail 1\n"
                + "# skipped 0\n";

        ParsedBuild build = TapOutputParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The not-ok line files one diagnostic"),
                () -> assertEquals("/p/App.test.js", build.diagnostics().getFirst().file(),
                        "The YAML location supplies the file"),
                () -> assertEquals(Integer.valueOf(15), build.diagnostics().getFirst().line(),
                        "The YAML location supplies the line"),
                () -> assertEquals("Expected \"Hello\" but got \"Goodbye",
                        build.diagnostics().getFirst().message(), "The YAML error supplies the message"));
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
