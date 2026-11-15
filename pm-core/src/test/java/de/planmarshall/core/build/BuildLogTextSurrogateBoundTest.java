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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The per-line cut of {@link BuildLogText#boundLines(String)} never splits a surrogate pair: a cut that would keep the
 * high half of a pair stops one character earlier, as {@link UnparsedEvidence} guards its tail cut.
 */
@EnableGeneratorController
@DisplayName("BuildLogText per-line surrogate bound")
class BuildLogTextSurrogateBoundTest {

    @Test
    @DisplayName("a long line is cut at the line bound")
    void cutsALongLineAtTheBound() {
        var line = "x".repeat(BuildLogText.MAX_LINE_LENGTH + 100);

        var bounded = BuildLogText.boundLines(line);

        assertEquals(BuildLogText.MAX_LINE_LENGTH, bounded.length(), "The line is cut at the bound");
    }

    @Test
    @DisplayName("a cut that would split a surrogate pair stops before the pair")
    void neverSplitsASurrogatePairAtTheCut() {
        // the emoji sits where the cut would land: its high half would be the last kept character
        var line = "x".repeat(BuildLogText.MAX_LINE_LENGTH - 1) + "😀" + "tail";

        var bounded = BuildLogText.boundLines(line);

        assertAll("surrogate pair at the cut",
                () -> assertEquals(BuildLogText.MAX_LINE_LENGTH - 1, bounded.length(),
                        "The cut stops before the pair it would split"),
                () -> assertFalse(Character.isHighSurrogate(bounded.charAt(bounded.length() - 1)),
                        "The bounded text ends on no lone high surrogate"));
    }
}
