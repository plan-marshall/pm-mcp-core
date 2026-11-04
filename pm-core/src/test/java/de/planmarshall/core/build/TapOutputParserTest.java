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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
