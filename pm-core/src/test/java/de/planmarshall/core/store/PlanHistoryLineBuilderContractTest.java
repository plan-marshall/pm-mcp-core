/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.PlanHistoryLine.Event;

@DisplayName("Plan history line builder contract")
class PlanHistoryLineBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's history line")
    void builderBuildsHistoryLine() {
        var canonical = new PlanHistoryLine(PlanHistoryLine.FORMAT_VERSION, 1, Event.ENTERED, "init.start",
                List.of(), null, null, 1, Instant.parse("2026-10-22T10:00:00Z"));

        var built = PlanHistoryLine.builder().formatVersion(canonical.formatVersion()).seq(canonical.seq())
                .event(canonical.event()).node(canonical.node()).callPath(canonical.callPath())
                .phase(canonical.phase()).outcome(canonical.outcome())
                .instanceVersion(canonical.instanceVersion()).at(canonical.at()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new PlanHistoryLine(canonical.formatVersion(), canonical.seq(), null, canonical.node(),
                        canonical.callPath(), canonical.phase(), canonical.outcome(),
                        canonical.instanceVersion(), canonical.at()),
                "the constructor refuses an absent event");
        var incomplete = PlanHistoryLine.builder().formatVersion(canonical.formatVersion()).seq(canonical.seq())
                .node(canonical.node()).callPath(canonical.callPath()).phase(canonical.phase())
                .outcome(canonical.outcome()).instanceVersion(canonical.instanceVersion())
                .at(canonical.at());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent event");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent event with the same message"));
    }
}
