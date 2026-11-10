/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;

/**
 * Proves that the builders of {@link DiffSummary} and its nested {@link DiffSummary.CeilingChange} build the values of
 * their canonical constructors.
 */
@EnableGeneratorController
@DisplayName("DiffSummary builders")
class DiffSummaryRecordsTest {

    @Test
    @DisplayName("the builder builds the diff summary the constructor builds")
    void diffSummaryBuilderEqualsConstructor() {
        var prunedAdded = List.of(new PrunedElement(PrunedElement.Kind.STEP,
                Generators.letterStrings(1, 20).next(), PruneReason.POSTURE, "posture"));
        var previous = new CallValue.IntValue(Generators.integers(0, 50).next());
        var value = new CallValue.IntValue(previous.value() + 1);
        var ceilingChange = new DiffSummary.CeilingChange(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), previous, value);
        var waived = new WaivedGate(Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 20).next());
        var ceilingsChanged = List.of(ceilingChange);
        var gatesWaived = List.of(waived);

        var constructed = new DiffSummary(prunedAdded, List.of(), ceilingsChanged, gatesWaived, List.of());
        var built = DiffSummary.builder()
                .prunedAdded(prunedAdded)
                .prunedRemoved(List.of())
                .ceilingsChanged(ceilingsChanged)
                .gatesWaived(gatesWaived)
                .gatesUnwaived(List.of())
                .build();

        assertEquals(constructed, built, "the builder builds the same diff summary");
    }

    @Test
    @DisplayName("the builder builds the ceiling change the constructor builds")
    void ceilingChangeBuilderEqualsConstructor() {
        var callSite = Generators.letterStrings(1, 20).next();
        var argument = Generators.letterStrings(1, 20).next();
        var previous = new CallValue.IntValue(Generators.integers(0, 50).next());
        var value = new CallValue.IntValue(previous.value() + 1);

        var constructed = new DiffSummary.CeilingChange(callSite, argument, previous, value);
        var built = DiffSummary.CeilingChange.builder()
                .callSite(callSite)
                .argument(argument)
                .previous(previous)
                .value(value)
                .build();

        assertEquals(constructed, built, "the builder builds the same ceiling change");
    }

    @Test
    @DisplayName("the builder refuses a missing ceiling change call site")
    void ceilingChangeBuilderRefusesMissingCallSite() {
        var previous = new CallValue.IntValue(Generators.integers(0, 50).next());
        var value = new CallValue.IntValue(previous.value() + 1);

        var exception = assertThrows(NullPointerException.class, () -> DiffSummary.CeilingChange.builder()
                .argument(Generators.letterStrings(1, 20).next())
                .previous(previous)
                .value(value)
                .build());

        assertEquals("callSite", exception.getMessage(), "the builder names the missing call site");
    }
}
