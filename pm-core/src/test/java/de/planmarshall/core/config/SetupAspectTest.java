/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The aspects a single-aspect setup run offers (State Catalogue § Project Setup, {@code setup.entry}): the closed list,
 * its order, and the {@code aspect:<name>} link names.
 */
@DisplayName("Setup aspect")
class SetupAspectTest {

    @Test
    @DisplayName("Should name the six aspects in the order the run offers them")
    void namesTheAspectsInOrder() {
        var names = Arrays.stream(SetupAspect.values()).map(SetupAspect::wireName).toList();

        assertEquals(List.of("team_settings", "build_map", "providers", "finalize", "qualification", "architecture"),
                names, "The aspect list is stated once, in the order of the run");
    }

    @Test
    @DisplayName("Should read every aspect back from its own link name")
    void everyAspectReadsBack() {
        for (var aspect : SetupAspect.values()) {
            assertTrue(SetupAspect.of(aspect.wireName()).filter(aspect::equals).isPresent(),
                    "The aspect reads back from its link name");
        }
    }

    @Test
    @DisplayName("Should chain the aspects and end after the last one")
    void chainsTheAspects() {
        assertEquals(SetupAspect.BUILD_MAP, SetupAspect.TEAM_SETTINGS.next().orElseThrow(),
                "The run offers the build map after the team settings");
        assertTrue(SetupAspect.ARCHITECTURE.next().isEmpty(), "The last aspect has no successor");
    }

    @Test
    @DisplayName("Should read no aspect from a name outside the closed list")
    void unknownNameReadsAsNone() {
        assertTrue(SetupAspect.of("everything").isEmpty(), "An unknown aspect reads as none");
    }
}
