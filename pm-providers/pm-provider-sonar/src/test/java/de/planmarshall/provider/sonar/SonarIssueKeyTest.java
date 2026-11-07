/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The key of an issue on Sonar. The key segments around a refused character are generated. What stays literal is the
 * key grammar and each test says so: the two key shapes Sonar reports (the base64-like key and the UUID key), the
 * one-character boundary key, the empty and the blank key, and the white-space and control characters the key refuses.
 */
@EnableGeneratorController
@DisplayName("SonarIssueKey")
class SonarIssueKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"AYx3kQ9hZ1mB-7tPq_2c", "01fc972e-2a3c-433e-bcae-0bd7f88f5123", "k"})
    @DisplayName("keeps a key as Sonar reports it (the two key shapes and the one-character boundary)")
    void accepts(String value) {
        assertEquals(value, new SonarIssueKey(value).value(), "the key keeps the value Sonar reported");
    }

    @Test
    @DisplayName("keeps a generated key without white space as it is")
    void acceptsGenerated() {
        var value = segment() + "-" + Generators.integers(0, Integer.MAX_VALUE).next() + "_" + segment();

        assertEquals(value, new SonarIssueKey(value).value(), "the key keeps the value Sonar reported");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("refuses an empty or a blank key (the empty and the blank key are the boundary)")
    void refusesEmpty(String value) {
        assertThrows(IllegalArgumentException.class, () -> new SonarIssueKey(value),
                "an empty or blank key is refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n", "\u0000", " ", " ", " "})
    @DisplayName("refuses a key that holds white space or a control character (the characters are the grammar)")
    void refuses(String separator) {
        var value = segment() + separator + segment();

        assertThrows(IllegalArgumentException.class, () -> new SonarIssueKey(value),
                "a key holding the refused character is refused");
    }

    @Test
    @DisplayName("refuses no value")
    void refusesNull() {
        assertThrows(NullPointerException.class, () -> new SonarIssueKey(null), "a missing key value is refused");
    }

    private static String segment() {
        return Generators.letterStrings(1, 12).next();
    }
}
