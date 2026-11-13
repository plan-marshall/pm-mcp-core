/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The grammar of a role name ({@code id-role-name} of the identifiers registry): two to 32 characters, a lower-case
 * letter first, then lower-case letters, digits or {@code -}, matched in full.
 * <p>
 * The names are literals because each one is the boundary of the contract; the count derived from the text (32 served,
 * 33 refused) is the grammar's own figure.
 */
@EnableGeneratorController
@DisplayName("Role name grammar")
class RoleNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"ab", "implement", "review-code", "triage-review", "a1", "a-b", "ab-", "a--b"})
    @DisplayName("a name of the grammar is accepted")
    void acceptedName(String name) {
        assertTrue(RoleName.matches(name), "A name of the grammar should be accepted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "", "1a", "-a", "Ab", "a_b", "a.b", "a/b", "a b", "ab\n", "äb", "ａｂ"})
    @DisplayName("a name outside the grammar is refused")
    void refusedName(String name) {
        assertFalse(RoleName.matches(name), "A name outside the grammar should be refused");
    }

    @Test
    @DisplayName("32 characters are accepted and 33 refused")
    void lengthBoundary() {
        assertAll(
                () -> assertTrue(RoleName.matches("a".repeat(32)), "The longest name should be accepted"),
                () -> assertFalse(RoleName.matches("a".repeat(33)), "A name one longer should be refused"));
    }

    @Test
    @DisplayName("a null name raises NullPointerException")
    void nullName() {
        assertThrows(NullPointerException.class, () -> RoleName.matches(null),
                "A null name should raise NullPointerException");
    }

    @Test
    @DisplayName("the pattern is the anchored grammar of two to 32 characters")
    void patternText() {
        assertAll(
                () -> assertTrue(RoleName.PATTERN.pattern().startsWith("^"), "The pattern should be anchored at start"),
                () -> assertTrue(RoleName.PATTERN.pattern().endsWith("$"), "The pattern should be anchored at end"),
                () -> assertTrue(RoleName.matches("ab"), "The shortest accepted name has two characters"),
                () -> assertFalse(RoleName.matches("a"), "The shortest refused name has one character"));
    }
}
