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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link MavenOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/maven}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@DisplayName("MavenOutputParser")
class MavenOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/maven";

    private static final int FIXTURE_FLOOR = 18;

    private static final int ROSTER_CASES = 35;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("strips the colour codes of a log before matching its patterns")
    void stripsAnsiEscapes() {
        String logText = "\u001B[1;31m[ERROR]\u001B[0m /p/Foo.java:[7,3] cannot find symbol\n";

        ParsedBuild build = MavenOutputParser.parse(logText);

        assertEquals(1, build.diagnostics().size(), "The coloured error line is filed once");
        assertEquals(Category.COMPILATION_ERROR, build.diagnostics().getFirst().category(),
                "The stripped line keeps its category");
    }

    @Override
    ParsedBuild parse(String logText) {
        return MavenOutputParser.parse(logText);
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
