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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The grammar of a harness id ({@code id-harness-id} of the identifiers registry): the text of {@link ShortName}, two
 * to 32 characters, a lower-case letter first, then lower-case letters, digits or {@code -}, matched in full.
 * <p>
 * The names are literals because each one is the boundary of the contract; the count derived from the text (32 served,
 * 33 refused) is the grammar's own figure.
 */
@EnableGeneratorController
@DisplayName("Harness id grammar")
class HarnessIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"ab", "opencode", "claude-code", "a1", "a-b", "ab-", "a--b"})
    @DisplayName("a name of the grammar is accepted")
    void acceptedName(String name) {
        assertTrue(HarnessId.matches(name), "A name of the grammar should be accepted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "", "1a", "-a", "Ab", "a_b", "a.b", "a/b", "a b", "ab\n", "äb", "ａｂ"})
    @DisplayName("a name outside the grammar is refused")
    void refusedName(String name) {
        assertFalse(HarnessId.matches(name), "A name outside the grammar should be refused");
    }

    @Test
    @DisplayName("32 characters are accepted and 33 refused")
    void lengthBoundary() {
        assertAll(
                () -> assertTrue(HarnessId.matches("a".repeat(32)), "The longest name should be accepted"),
                () -> assertFalse(HarnessId.matches("a".repeat(33)), "A name one longer should be refused"));
    }

    @Test
    @DisplayName("a null name raises NullPointerException")
    void nullName() {
        assertThrows(NullPointerException.class, () -> HarnessId.matches(null),
                "A null name should raise NullPointerException");
    }

    @Test
    @DisplayName("the pattern is the text of ShortName, shared with the role name")
    void sameTextAsRoleName() {
        assertAll(
                () -> assertEquals(ShortName.TEXT, HarnessId.PATTERN.pattern(),
                        "The harness id should read the text of ShortName"),
                () -> assertEquals(ShortName.TEXT, RoleName.PATTERN.pattern(),
                        "The role name should read the text of ShortName"));
    }
}
