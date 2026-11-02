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

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ported tests of {@link EslintOutputParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-eslint}, derived by hand from the original's log and its pytest case.
 * <p>
 * The class does not extend {@link ParsedBuildPortedTest} because a parse yields an {@link EslintResult}: the problem
 * summary of the log is a fact beside the parsed build.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("EslintOutputParser")
class EslintOutputParserTest {

    private static final String DIRECTORY = "ported/build/npm-eslint";

    private static final int FIXTURE_FLOOR = 2;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("parses its fixture as the original's pytest case expects")
    void parsesFixture(BuildLogFixtures.Case fixture) {
        Set<Integer> consumed = new HashSet<>();

        EslintResult result = EslintOutputParser.parse(fixture.log());

        PortedExpectations.eslint(fixture, result, consumed);
        BuildLogFixtures.assertConsumed(fixture, consumed);
    }

    @Test
    @DisplayName("holds every fixture the ported and deliberate cases need")
    void populationFloor() {
        List<BuildLogFixtures.Case> fixtures = BuildLogFixtures.load(DIRECTORY);

        assertTrue(fixtures.size() >= FIXTURE_FLOOR, "the directory keeps its fixture population");
        assertTrue(fixtures.stream().anyMatch(BuildLogFixtures.Case::original), "an original-derived fixture exists");
        assertTrue(fixtures.stream().anyMatch(fixture -> !fixture.original()), "a deliberate fixture exists");
    }

    @Test
    @DisplayName("closes the corpus of the original suite in both directions")
    void roster() {
        BuildLogFixtures.assertRoster(DIRECTORY, BuildLogFixtures.load(DIRECTORY), ROSTER_CASES);
    }
}
