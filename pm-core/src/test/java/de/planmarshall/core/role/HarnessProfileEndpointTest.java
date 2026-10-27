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
@DisplayName("Endpoint harness class rule")
class HarnessProfileEndpointTest {

    @Test
    @DisplayName("an endpoint harness runs zero_tool and no other class")
    void runsZeroToolOnly() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertTrue(profile.runs(RoleClass.ZERO_TOOL)),
                () -> assertFalse(profile.runs(RoleClass.WRITER)),
                () -> assertFalse(profile.runs(RoleClass.READ_ONLY)),
                () -> assertFalse(profile.runsNoClass()),
                () -> assertEquals(Optional.of(HarnessProfile.ToolSet.ENFORCED), profile.toolSet(RoleClass.ZERO_TOOL)));
    }

    @Test
    @DisplayName("an endpoint harness is no worker host and cannot resume")
    void noHostAndNoResumability() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertFalse(profile.eligible()),
                () -> assertFalse(profile.restrictedModelsDeclared()),
                () -> assertTrue(profile.restrictedModels().isEmpty()),
                () -> assertFalse(profile.resumability().hold()),
                () -> assertFalse(profile.resumability().resume()),
                () -> assertEquals("", profile.askUserTool()));
    }

    @Test
    @DisplayName("the context budget is the endpoint budget, and the tiers are the profile's")
    void contextBudgetAndTiers() {
        var profile = HarnessProfile.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertEquals(7168, profile.contextBudgetTokens()),
                () -> assertEquals(Optional.of("big"), profile.tierModel("frontier")),
                () -> assertEquals(Optional.of("mid"), profile.tierModel("standard")),
                () -> assertEquals(Optional.of("small"), profile.tierModel("fast")),
                () -> assertEquals(Optional.empty(), profile.tierModel("absent")));
    }
}
