/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * The lane entry ids and the light gate of {@link LaneEntryIds} are the ids the specification states and the default
 * units declare: a rename of a constant without the matching fixture change fails here, so the holder and the units
 * cannot drift apart. The entries the specification no longer has are declared by no default unit.
 */
// Every datum in this class is a contract: the lane state ids of the specification and the deleted ones.
@DisplayName("LaneEntryIds")
class LaneEntryIdsTest {

    private static List<String> defaultStates() {
        return InvariantFixtures.passingUnits(UnitOrigin.DEFAULT).values().stream()
                .flatMap(unit -> unit.states().keySet().stream()).toList();
    }

    @Test
    @DisplayName("the holder names the deep entry refine.analyze, the light entry outline.light and the light gate"
            + " outline.light-gate")
    void holderNamesTheSpecifiedStates() {
        assertAll(
                () -> assertEquals("refine.analyze", LaneEntryIds.DEEP_ENTRY,
                        "the deep lane enters at refine.analyze"),
                () -> assertEquals("outline.light", LaneEntryIds.LIGHT_ENTRY,
                        "the light lane enters at outline.light"),
                () -> assertEquals("outline.light-gate", LaneEntryIds.LIGHT_GATE,
                        "the light gate is outline.light-gate"));
    }

    @Test
    @DisplayName("the default units declare a state under each lane entry id and the light gate")
    void entriesAreStatesOfTheDefaultUnits() {
        var states = defaultStates();

        assertAll(
                () -> assertTrue(states.contains(LaneEntryIds.DEEP_ENTRY),
                        "a default unit declares the deep entry"),
                () -> assertTrue(states.contains(LaneEntryIds.LIGHT_ENTRY),
                        "a default unit declares the light entry"),
                () -> assertTrue(states.contains(LaneEntryIds.LIGHT_GATE),
                        "a default unit declares the light gate"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"refine.baseline-reconcile", "init.light-gate", "outline.light-regate"})
    @DisplayName("no default unit declares an entry the specification no longer has")
    void deletedEntriesAreGone(String deleted) {
        assertFalse(defaultStates().contains(deleted), "no default unit declares the deleted entry");
    }
}
