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
 * The ported tests of {@link NpmErrorParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-errors}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("NpmErrorParser")
class NpmErrorParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-errors";

    private static final int FIXTURE_FLOOR = 4;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("names the peer dependency of a conflict trailer the original can parse")
    void namesThePeerConflict() {
        String logText = """
                npm ERR! code ERESOLVE
                npm ERR! ERESOLVE unable to resolve dependency tree
                npm ERR! Could not resolve dependency:
                npm ERR! peer react@17.0.2 from react-router-dom@5.3.4
                """;

        ParsedBuild build = NpmErrorParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The whole log is filed as one diagnostic"),
                () -> assertEquals("ERESOLVE: Could not resolve peer dependency react@17.0.2 from react-router-dom@5.3.4",
                        build.diagnostics().getFirst().message(), "The conflict names its peer and its origin"),
                () -> assertEquals(Category.NPM_DEPENDENCY, build.diagnostics().getFirst().category(),
                        "The dependency category is the ERESOLVE one"));
    }

    @Override
    ParsedBuild parse(String logText) {
        return NpmErrorParser.parse(logText);
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
