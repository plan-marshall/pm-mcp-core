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
 * The four runs a project setup records (State Catalogue § Project Setup, {@code setup.entry}): each value reads back
 * from its own name, and a name outside the closed list reads as none.
 */
@DisplayName("Setup run")
class SetupRunTest {

    @Test
    @DisplayName("Should read every run back from its own name")
    void everyRunReadsBack() {
        for (var run : SetupRun.values()) {
            assertTrue(SetupRun.of(run.wireName()).filter(run::equals).isPresent(),
                    "The run reads back from its name");
        }
    }

    @Test
    @DisplayName("Should name exactly the four entries of project.setup")
    void namesTheClosedEntries() {
        var names = Arrays.stream(SetupRun.values()).map(SetupRun::wireName).toList();

        assertEquals(List.of("first_run", "integrations", "reconcile", "aspect"), names,
                "The setup's closed entry list is stated once, in order");
    }

    @Test
    @DisplayName("Should read no run from a name outside the closed list")
    void unknownNameReadsAsNone() {
        assertTrue(SetupRun.of("maybe").isEmpty(), "An unknown setup run reads as none");
    }
}
