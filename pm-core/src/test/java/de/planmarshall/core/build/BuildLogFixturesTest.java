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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The loader of the ported fixtures refuses a malformed fixture instead of skipping it, so a broken scenario fails the
 * suite rather than passing quietly (PM-TEST-2).
 */
@EnableGeneratorController
@DisplayName("BuildLogFixtures")
class BuildLogFixturesTest {

    private static final String ORIGINAL_HEADER = "original: plan-marshall@" + BuildLogFixtures.PIN;

    @Test
    @DisplayName("enumerates the fixtures of every directory from the class path")
    void enumeratesDirectories() {
        List<BuildLogFixtures.Case> maven = BuildLogFixtures.load("ported/build/maven");

        assertAll(
                () -> assertFalse(maven.isEmpty(), "The Maven directory holds fixtures"),
                () -> assertTrue(maven.stream().noneMatch(fixture -> fixture.name().startsWith("original-cases")),
                        "The roster is no fixture"),
                () -> assertTrue(maven.stream().allMatch(fixture -> !fixture.log().isEmpty()),
                        "Every fixture assembles a log"));
    }

    @Test
    @DisplayName("refuses a fixture without a header or without a directive")
    void refusesIncompleteFixtures(@TempDir Path directory) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken", "# only a comment\n", directory),
                        "A fixture needs a header"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken", ORIGINAL_HEADER + " a.py::test_x\n", directory),
                        "A fixture needs a directive"));
    }

    @Test
    @DisplayName("refuses a header of another pin or of an unknown shape")
    void refusesForeignHeaders(@TempDir Path directory) {
        var otherPin = "original: plan-marshall@" + Generators.letterStrings(40, 40).next() + " a.py::test_x\nlog x\n";

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken", otherPin, directory),
                        "A fixture names the pinned commit"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken", "header: x\nlog x\n", directory),
                        "An unknown header is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken", "deliberate: other\nlog x\n", directory),
                        "A deliberate header names its file"));
    }

    @Test
    @DisplayName("refuses a directive the grammar does not declare or of the wrong arity")
    void refusesMalformedDirectives(@TempDir Path directory) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken",
                                ORIGINAL_HEADER + " a.py::test_x\nunknown-directive x\n", directory),
                        "An undeclared directive is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.parse("broken",
                                ORIGINAL_HEADER + " a.py::test_x\nstatus\nlog x\n", directory),
                        "A non-rest directive keeps its arity"));
    }

    @Test
    @DisplayName("refuses a log-file the fixture directory does not hold")
    void refusesMissingLogFile(@TempDir Path directory) {
        var fixture = ORIGINAL_HEADER + " a.py::test_x\nlog-file absent.log\nstatus FAILURE\n";

        var refusal = assertThrows(IllegalArgumentException.class,
                () -> BuildLogFixtures.parse("broken", fixture, directory),
                "An unreadable log file is refused by name");

        assertTrue(refusal.getMessage().contains("absent.log"), "The refusal names the missing log");
    }

    @Test
    @DisplayName("assembles inline lines, a referenced log and the shaping directives")
    void assemblesLog(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("part.log"), "second\r\n");
        var text = ORIGINAL_HEADER + " a.py::test_x\nlog first\nlog-file part.log\ncrlf\nstatus FAILURE\n";

        var fixture = BuildLogFixtures.parse("assembled", text, directory);

        assertAll(
                () -> assertEquals(1, fixture.expectations().size(), "Only the status is an expectation"),
                () -> assertTrue(fixture.log().contains("first\r\n"), "The inline line takes the CRLF shape"),
                () -> assertTrue(fixture.log().contains("second\r\n"), "The referenced log is appended"),
                () -> assertFalse(fixture.log().contains("\n\n\n"), "No empty line is inserted"));
    }

    @Test
    @DisplayName("parses the roster of the original suite and refuses a malformed one")
    void parsesRoster() {
        var suite = "suite: plan-marshall@" + BuildLogFixtures.PIN + " a.py\n";
        var mapped = BuildLogFixtures.rosterOf(suite + "case test_one -> fixture-one\n");
        var excluded = BuildLogFixtures.rosterOf(suite + "case test_two excluded: a stated reason\n");

        assertAll(
                () -> assertEquals(Set.of("fixture-one"),
                        mapped.cases().get("a.py::test_one").fixtures(), "The mapped case names its fixture"),
                () -> assertTrue(excluded.cases().get("a.py::test_two").reason().startsWith("a stated"),
                        "The excluded case states its reason"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.rosterOf("case test_one -> fixture-one\n"),
                        "A case before its suite is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.rosterOf(suite + "case test_one -> f\ncase test_one -> f\n"),
                        "A case listed twice is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> BuildLogFixtures.rosterOf(suite + "case test_one\n"),
                        "A case without a mapping or a reason is refused"));
    }
}
