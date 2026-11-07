/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Word generator")
@EnableGeneratorController
class WordGeneratorTest {

    @Test
    @DisplayName("Should yield lower-case words within the length range of the factory")
    void shouldYieldLowerCaseWordsInRange() {
        var word = RuntimeGenerators.words().next();
        var shortWord = RuntimeGenerators.shortWords().next();

        // The ranges are the contract of the two factory methods: 1 to 16 and 2 to 10 letters
        assertAll("generated words",
                () -> assertTrue(word.matches("[a-z]{1,16}"), "A word has 1 to 16 lower-case letters"),
                () -> assertTrue(shortWord.matches("[a-z]{2,10}"), "A short word has 2 to 10 lower-case letters"));
    }

    @Test
    @DisplayName("Should keep a fixed length when the range has one value")
    void shouldKeepAFixedLength() {
        var word = new WordGenerator(5, 5).next();

        assertEquals(5, word.length(), "A one-value range fixes the word length");
    }

    @Test
    @DisplayName("Should refuse an empty or inverted length range")
    void shouldRefuseAnInvalidRange() {
        assertAll("invalid ranges",
                () -> assertThrows(IllegalArgumentException.class, () -> new WordGenerator(0, 3),
                        "A word of no letters is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WordGenerator(4, 3),
                        "A maximum below the minimum is refused"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(String.class, RuntimeGenerators.words().getType(), "The generator names the string type");
    }
}
