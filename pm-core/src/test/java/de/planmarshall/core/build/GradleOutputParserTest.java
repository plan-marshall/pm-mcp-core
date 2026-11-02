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

import java.util.List;
import org.junit.jupiter.api.DisplayName;

/**
 * The ported tests of {@link GradleOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/gradle}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@DisplayName("GradleOutputParser")
class GradleOutputParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/gradle";

    private static final int FIXTURE_FLOOR = 5;

    private static final int ROSTER_CASES = 16;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Override
    ParsedBuild parse(String logText) {
        return GradleOutputParser.parse(logText);
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
