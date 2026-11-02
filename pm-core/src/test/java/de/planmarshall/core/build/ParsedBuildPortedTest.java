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
 * The shape of a ported parser test (PM-TEST-2), shared by the Maven and Gradle parsers: one case per plain-file fixture of
 * the parser's directory, a fixture floor, and the roster closure in both directions.
 * <p>
 * A subclass supplies its directory, the fixture floor, the roster size and its parser; the fixtures themselves come from
 * its own static {@code fixtures()} method source, which JUnit resolves in the concrete test class.
 *
 * @since 0.1
 */
abstract class ParsedBuildPortedTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("parses its fixture as the original's pytest case expects")
    void parsesFixture(BuildLogFixtures.Case fixture) {
        Set<Integer> consumed = new HashSet<>();

        ParsedBuild build = parse(fixture.log());

        PortedExpectations.parsedBuild(fixture, build, consumed);
        BuildLogFixtures.assertConsumed(fixture, consumed);
    }

    @Test
    @DisplayName("holds every fixture the ported and deliberate cases need")
    void populationFloor() {
        List<BuildLogFixtures.Case> fixtures = BuildLogFixtures.load(directory());

        assertTrue(fixtures.size() >= fixtureFloor(), "the directory keeps its fixture population");
        assertTrue(fixtures.stream().anyMatch(BuildLogFixtures.Case::original), "an original-derived fixture exists");
        assertTrue(fixtures.stream().anyMatch(fixture -> !fixture.original()), "a deliberate fixture exists");
    }

    @Test
    @DisplayName("closes the corpus of the original suite in both directions")
    void roster() {
        BuildLogFixtures.assertRoster(directory(), BuildLogFixtures.load(directory()), rosterCases());
    }

    /**
     * Parses one fixture's log with the parser under test.
     *
     * @param logText the log text
     * @return the parsed build
     */
    abstract ParsedBuild parse(String logText);

    /**
     * Returns the class-path directory of this parser's fixtures.
     *
     * @return the fixture directory
     */
    abstract String directory();

    /**
     * Returns the smallest fixture population the directory must keep.
     *
     * @return the fixture floor
     */
    abstract int fixtureFloor();

    /**
     * Returns the number of cases the roster of the original suite holds.
     *
     * @return the roster size
     */
    abstract int rosterCases();
}
