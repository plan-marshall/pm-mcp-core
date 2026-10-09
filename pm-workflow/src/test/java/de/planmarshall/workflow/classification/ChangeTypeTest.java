/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ChangeType")
class ChangeTypeTest {

    @ParameterizedTest(name = "{0} is {1} on the wire, priority {2}")
    @CsvSource({
            "ANALYSIS, analysis, 1",
            "FEATURE, feature, 2",
            "ENHANCEMENT, enhancement, 3",
            "BUG_FIX, bug_fix, 4",
            "TECH_DEBT, tech_debt, 5",
            "VERIFICATION, verification, 6"})
    @DisplayName("carries its wire name and its priority")
    void values(ChangeType type, String wireName, int priorityOrder) {
        assertEquals(wireName, type.wireName());
        assertEquals(priorityOrder, type.priorityOrder());
    }

    @Test
    @DisplayName("is closed at six values whose priorities are one to six without a gap")
    void closed() {
        var priorities = Arrays.stream(ChangeType.values()).sorted(Comparator.comparingInt(ChangeType::priorityOrder))
                .map(ChangeType::priorityOrder).toList();

        assertEquals(List.of(1, 2, 3, 4, 5, 6), priorities);
    }
}
