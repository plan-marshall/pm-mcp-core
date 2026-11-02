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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of {@link Diagnostic}: the null contract of the location, the non-blank message and the copy that carries
 * a collected stack trace.
 */
@EnableGeneratorController
@DisplayName("Diagnostic")
class DiagnosticTest {

    @Test
    @DisplayName("stores its components unchanged")
    void storesComponents() {
        var file = Generators.letterStrings(3, 20).next() + ".java";
        var line = Generators.integers(1, 500).next();
        var column = Generators.integers(1, 120).next();
        var message = Generators.letterStrings(10, 40).next();

        var diagnostic = new Diagnostic(file, line, column, message, Category.COMPILATION_ERROR, Severity.ERROR, null);

        assertAll(
                () -> assertEquals(file, diagnostic.file(), "The file is stored unchanged"),
                () -> assertEquals(line, diagnostic.line(), "The line is stored unchanged"),
                () -> assertEquals(column, diagnostic.column(), "The column is stored unchanged"),
                () -> assertEquals(message, diagnostic.message(), "The message is stored unchanged"),
                () -> assertEquals(Category.COMPILATION_ERROR, diagnostic.category(), "The category is stored"),
                () -> assertEquals(Severity.ERROR, diagnostic.severity(), "The severity is stored"),
                () -> assertNull(diagnostic.stackTrace(), "No stack trace was collected"));
    }

    @Test
    @DisplayName("a blank message is refused")
    void refusesBlankMessage() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic(null, null, null, " ", Category.OTHER, Severity.WARNING, null),
                        "A blank message cannot describe a diagnostic"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic(null, null, null, "", Category.OTHER, Severity.WARNING, null),
                        "An empty message cannot describe a diagnostic"));
    }

    @Test
    @DisplayName("a negative location is refused")
    void refusesNegativeLocation() {
        var message = Generators.letterStrings(5, 20).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic("A.java", -1, null, message, Category.OTHER, Severity.ERROR, null),
                        "A line number is never negative"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic("A.java", 1, -2, message, Category.OTHER, Severity.ERROR, null),
                        "A column number is never negative"));
    }

    @Test
    @DisplayName("a location component without its parent is refused")
    void refusesPartialLocation() {
        var message = Generators.letterStrings(5, 20).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic(null, 12, null, message, Category.OTHER, Severity.ERROR, null),
                        "A line without a file is no location"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Diagnostic("A.java", null, 3, message, Category.OTHER, Severity.ERROR, null),
                        "A column without a line is no location"));
    }

    @Test
    @DisplayName("a collected stack trace is copied without touching the location")
    void carriesStackTrace() {
        var message = Generators.letterStrings(5, 20).next();
        var stack = "at probe.CoreTest.fails(CoreTest.java:10)";
        var diagnostic = new Diagnostic("A.java", 4, 2, message, Category.TEST_FAILURE, Severity.ERROR, null);

        var copied = diagnostic.withStackTrace(stack);

        assertAll(
                () -> assertEquals(stack, copied.stackTrace(), "The collected stack trace is carried"),
                () -> assertEquals(diagnostic.file(), copied.file(), "The file survives the copy"),
                () -> assertEquals(diagnostic.line(), copied.line(), "The line survives the copy"),
                () -> assertEquals(diagnostic.message(), copied.message(), "The message survives the copy"),
                () -> assertNull(diagnostic.stackTrace(), "The original stays without a stack trace"));
    }

    @Test
    @DisplayName("derives the wire names of the originals and its located state")
    void derivesNames() {
        var located = new Diagnostic("A.java", 1, 1, "text", Category.COMPILATION_ERROR, Severity.ERROR, null);
        var unlocated = new Diagnostic(null, null, null, "text", Category.UNRECOGNIZED, Severity.WARNING, null);

        assertAll(
                () -> assertEquals("compilation_error", located.categoryName(), "The category name is lower case"),
                () -> assertEquals("error", located.severityName(), "The severity name is lower case"),
                () -> assertTrue(located.isLocated(), "A file makes the diagnostic located"),
                () -> assertFalse(unlocated.isLocated(), "No file leaves the diagnostic unlocated"));
    }
}
