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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ported tests of {@link OpenRewriteLogParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/openrewrite}, derived by hand from the two originals and their pytest cases.
 * <p>
 * The domain state is an input of the parse, so a fixture declares it with {@code domain active|inactive} and the test
 * passes it in; the fixture's {@code verdict} is the fail-closed verdict the original's cases expect.
 *
 * @since 0.1
 */
@DisplayName("OpenRewriteLogParser")
class OpenRewriteLogParserTest {

    private static final String DIRECTORY = "ported/build/openrewrite";

    private static final int FIXTURE_FLOOR = 12;

    private static final int ROSTER_CASES = 57;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("parses its fixture as the original's pytest case expects")
    void parsesFixture(BuildLogFixtures.Case fixture) {
        Set<Integer> consumed = new HashSet<>();

        RewriteLogResult result = OpenRewriteLogParser.parse(fixture.log(), domainActive(fixture, consumed));

        PortedExpectations.rewriteLog(fixture, result, consumed);
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
    @DisplayName("closes the corpus of both original suites in both directions")
    void roster() {
        BuildLogFixtures.assertRoster(DIRECTORY, fixtures(), ROSTER_CASES);
    }

    private static boolean domainActive(BuildLogFixtures.Case fixture, Set<Integer> consumed) {
        List<BuildLogFixtures.Expectation> expectations = fixture.expectations();
        boolean domainActive = true;
        for (int index = 0; index < expectations.size(); index++) {
            BuildLogFixtures.Expectation expectation = expectations.get(index);
            if ("domain".equals(expectation.keyword())) {
                domainActive = "active".equals(expectation.argument());
                consumed.add(index);
            }
        }
        return domainActive;
    }
}
