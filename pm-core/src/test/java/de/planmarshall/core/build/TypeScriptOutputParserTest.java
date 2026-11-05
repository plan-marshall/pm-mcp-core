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
import static org.junit.jupiter.api.Assertions.assertNull;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link TypeScriptOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-typescript}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("TypeScriptOutputParser")
class TypeScriptOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-typescript";

    private static final int FIXTURE_FLOOR = 3;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("reads the colon-separated form when it is the only form present")
    void readsColonSeparatedForm() {
        ParsedBuild build = TypeScriptOutputParser.parse(
                "src/b.ts:9:4 - error TS2339: Property 'x' does not exist on type 'Y'.\n");

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The colon form files one diagnostic"),
                () -> assertEquals(Category.COMPILATION_ERROR, build.diagnostics().getFirst().category(),
                        "The diagnostic is a compilation error"),
                () -> assertEquals("src/b.ts", build.diagnostics().getFirst().file(), "The file precedes the line"),
                () -> assertEquals(Integer.valueOf(9), build.diagnostics().getFirst().line(),
                        "The line number of the diagnostic is carried"),
                () -> assertEquals(Integer.valueOf(4), build.diagnostics().getFirst().column(), "The column is carried"),
                () -> assertEquals("TS2339: Property 'x' does not exist on type 'Y'.",
                        build.diagnostics().getFirst().message(), "The code prefixes the message"));
    }

    @Test
    @DisplayName("drops a position that does not fit an int")
    void dropsAnUnrepresentablePosition() {
        ParsedBuild build = TypeScriptOutputParser.parse(
                "src/a.ts(99999999999999999999,7): error TS2322: Type 'string' is not assignable to type 'number'.\n");

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The diagnostic is still filed"),
                () -> assertEquals("src/a.ts", build.diagnostics().getFirst().file(), "The file is kept"),
                () -> assertNull(build.diagnostics().getFirst().line(), "An unrepresentable line is absent"),
                () -> assertNull(build.diagnostics().getFirst().column(), "The column follows its line"));
    }

    @Override
    ParsedBuild parse(String logText) {
        return TypeScriptOutputParser.parse(logText);
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
