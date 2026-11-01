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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ported tests of {@link MavenOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/maven}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@DisplayName("MavenOutputParser")
class MavenOutputParserTest {

    private static final String DIRECTORY = "ported/build/maven";

    private static final int FIXTURE_FLOOR = 18;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("parses its fixture as the original's pytest case expects")
    void parsesFixture(BuildLogFixtures.Case fixture) {
        Set<Integer> consumed = new HashSet<>();

        ParsedBuild build = MavenOutputParser.parse(fixture.log());

        PortedExpectations.parsedBuild(fixture, build, consumed);
        BuildLogFixtures.assertConsumed(fixture, consumed);
    }

    @Test
    @DisplayName("holds every fixture the ported and deliberate cases need")
    void populationFloor() {
        List<BuildLogFixtures.Case> fixtures = fixtures();

        assertTrue(fixtures.size() >= FIXTURE_FLOOR, "the directory keeps its fixture population");
        assertTrue(fixtures.stream().anyMatch(BuildLogFixtures.Case::original), "an original-derived fixture exists");
        assertTrue(fixtures.stream().anyMatch(fixture -> !fixture.original()), "a deliberate fixture exists");
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

    @Test
    @DisplayName("closes the corpus of the original suite in both directions")
    void roster() {
        BuildLogFixtures.assertRoster(DIRECTORY, fixtures(), 35);
    }
}
