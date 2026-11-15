/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfileFixtures;

/**
 * The class rule of an endpoint harness (PM-CLIENT-2, Client Profiles § A Model on This Machine): it runs the role class
 * {@code zero_tool} and no other, has no restricted models and no resumability, and its context budget is the endpoint's
 * {@code context_tokens} less its {@code answer_reserve_tokens}.
 */
// This class carries no datum of its own: the fixture's ids, tiers and budget are what the projection asserts.
@DisplayName("Endpoint harness class rule")
class HarnessProfileEndpointTest {

    @Test
    @DisplayName("an endpoint harness runs zero_tool and no other class")
    void runsZeroToolOnly() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertTrue(profile.runs(RoleClass.ZERO_TOOL), "endpoint harness runs the zero_tool class"),
                () -> assertFalse(profile.runs(RoleClass.WRITER), "endpoint harness runs no writer class"),
                () -> assertFalse(profile.runs(RoleClass.READ_ONLY), "endpoint harness runs no read_only class"),
                () -> assertFalse(profile.runsNoClass(), "endpoint harness runs at least one class"),
                () -> assertEquals(Optional.of(HarnessProfile.ToolSet.ENFORCED), profile.toolSet(RoleClass.ZERO_TOOL),
                        "zero_tool carries the enforced tool set"));
    }

    @Test
    @DisplayName("an endpoint harness is a worker host that cannot resume")
    void hostAndNoResumability() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertTrue(profile.eligible(), "endpoint worker is eligible"),
                () -> assertFalse(profile.restrictedModelsDeclared(), "endpoint declares no restricted models"),
                () -> assertTrue(profile.restrictedModels().isEmpty(), "restricted model list is empty"),
                () -> assertFalse(profile.resumability().hold(), "endpoint harness declares no hold"),
                () -> assertFalse(profile.resumability().resume(), "endpoint harness declares no resume"),
                () -> assertEquals("", profile.askUserTool(), "endpoint has no ask_user tool"));
    }

    @Test
    @DisplayName("the context budget is the endpoint budget, and the tiers are the profile's")
    void contextBudgetAndTiers() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertEquals(7168, profile.contextBudgetTokens(), "endpoint context budget less the reserve"),
                () -> assertEquals(Optional.of("big"), profile.tierModel("frontier"), "frontier tier maps to big"),
                () -> assertEquals(Optional.of("mid"), profile.tierModel("standard"), "standard tier maps to mid"),
                () -> assertEquals(Optional.of("small"), profile.tierModel("fast"), "fast tier maps to small"),
                () -> assertEquals(Optional.empty(), profile.tierModel("absent"), "unset tier has no model"));
    }
}
