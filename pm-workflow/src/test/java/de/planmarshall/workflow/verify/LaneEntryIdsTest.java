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
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * The two lane entry ids of {@link LaneEntryIds} are the ids the default plan unit declares: a rename of either
 * constant without the matching fixture change fails here, so the holder and the unit cannot drift apart.
 */
@DisplayName("LaneEntryIds")
class LaneEntryIdsTest {

    @Test
    @DisplayName("the default plan unit declares a state under each lane entry id")
    void entriesAreStatesOfTheDefaultPlanUnit() {
        var plan = InvariantFixtures.passingUnits(UnitOrigin.DEFAULT).get("plan");

        assertAll(
                () -> assertTrue(plan.states().containsKey(LaneEntryIds.DEEP_ENTRY), LaneEntryIds.DEEP_ENTRY),
                () -> assertTrue(plan.states().containsKey(LaneEntryIds.LIGHT_ENTRY), LaneEntryIds.LIGHT_ENTRY));
    }
}
