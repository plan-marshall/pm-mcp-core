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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The invariants of a coverage item.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageItem")
class CoverageItemTest {

    @Test
    @DisplayName("freezes the missed-method list against later mutation")
    void freezesTheMissedMethods() {
        List<String> missedMethods = new ArrayList<>(List.of("run"));
        CoverageItem item = CoverageItem.builder()
                .name(Generators.nonEmptyStrings().next())
                .line(50)
                .missedMethods(missedMethods)
                .build();

        missedMethods.add("another");

        assertEquals(List.of("run"), item.missedMethods(), "The item keeps its own frozen list");
    }

    @Test
    @DisplayName("keeps an absent file and branch absent")
    void keepsAnAbsentFileAndBranch() {
        CoverageItem item = CoverageItem.builder()
                .name(Generators.nonEmptyStrings().next())
                .line(Generators.integers(0, 100).next().doubleValue())
                .missedMethods(List.of())
                .build();

        assertNull(item.file(), "A class without a file names none");
        assertNull(item.branch(), "A format without a branch metric leaves it null");
        assertTrue(item.missedMethods().isEmpty(), "A fully covered item names no method");
    }

    @Test
    @DisplayName("refuses a blank name or a blank file")
    void refusesABlankNameOrFile() {
        var blankName = CoverageItem.builder().name(" ").line(50).missedMethods(List.of());
        var blankFile = CoverageItem.builder().name("a").file(" ").line(50).missedMethods(List.of());

        assertThrows(IllegalArgumentException.class, blankName::build, "A blank item name is refused");
        assertThrows(IllegalArgumentException.class, blankFile::build, "A blank item file is refused");
    }

    @Test
    @DisplayName("refuses a percentage outside the percentage range")
    void refusesAPercentageOutsideTheRange() {
        var lineAbove = CoverageItem.builder().name("a").line(101).missedMethods(List.of());
        var branchBelow = CoverageItem.builder().name("a").line(50).branch(-1.0).missedMethods(List.of());

        assertThrows(IllegalArgumentException.class, lineAbove::build, "A line above 100 is refused");
        assertThrows(IllegalArgumentException.class, branchBelow::build, "A negative branch is refused");
    }

    @Test
    @DisplayName("refuses a null missed-method list")
    void refusesANullMissedMethodList() {
        var nullMethods = CoverageItem.builder().name("a").line(50).missedMethods(null);

        assertThrows(NullPointerException.class, nullMethods::build, "A null missed-method list is refused");
    }
}
