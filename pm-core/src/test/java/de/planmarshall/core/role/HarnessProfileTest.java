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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;

/**
 * The projection of a client profile into the facts a role reads (PM-CLIENT-2): eligibility, the restricted models, the
 * classes and their tool sets, the efforts, the tiers, the context budget, the idle wakeups, the resumability and the
 * ask-user tool. The test reads a profile document, so a mapping error in {@link HarnessProfile#of} fails here.
 */
@DisplayName("Harness profile projection")
class HarnessProfileTest {

    @Test
    @DisplayName("the profile facts are projected")
    void projectsProfileFacts() {
        var profile = HarnessProfile.of(loaded("""
                {"client_id":"acme-cli",
                 "tools":{"ask_user":"ask_question"},
                 "worker":{"eligible":true,"restricted_models":["other-model"],"idle_wakeups":4},
                 "headless":{
                   "model_tiers":{"frontier":"acme-frontier","standard":"acme-standard","fast":"acme-fast"},
                   "classes":{"writer":{"args":[],"tool_set":"enforced"},
                              "read_only":{"args":[],"tool_set":"advisory"},
                              "zero_tool":{"args":[]}},
                   "model_efforts":{"*":["low"],"acme-standard":["low","high"]},
                   "context_budget_tokens":300000,
                   "resumability":{"hold":true,"max_hold_seconds":300,
                     "resume":{"argv":["--resume","{{session_id}}"],"session_id":"/session","max_age_seconds":86400}}}}
                """));

        assertAll(
                () -> assertEquals("acme-cli", profile.clientId()),
                () -> assertTrue(profile.eligible()),
                () -> assertTrue(profile.restrictedModelsDeclared()),
                () -> assertTrue(profile.allowsRestrictedModel("other-model")),
                () -> assertFalse(profile.allowsRestrictedModel("acme-model")),
                () -> assertEquals(HarnessProfile.ToolSet.ENFORCED, profile.toolSet(RoleClass.WRITER).orElseThrow()),
                () -> assertEquals(HarnessProfile.ToolSet.ADVISORY, profile.toolSet(RoleClass.READ_ONLY).orElseThrow()),
                () -> assertEquals(HarnessProfile.ToolSet.ENFORCED, profile.toolSet(RoleClass.ZERO_TOOL).orElseThrow()),
                () -> assertEquals(List.of("low", "high"), profile.efforts("acme-standard")),
                () -> assertEquals(List.of("low"), profile.efforts("acme-fast")),
                () -> assertEquals("acme-standard", profile.tierModel("standard").orElseThrow()),
                () -> assertEquals(300_000, profile.contextBudgetTokens()),
                () -> assertEquals(4, profile.idleWakeups()),
                () -> assertTrue(profile.resumability().hold()),
                () -> assertEquals(300, profile.resumability().maxHoldSeconds()),
                () -> assertTrue(profile.resumability().resume()),
                () -> assertEquals(86_400, profile.resumability().maxResumeAgeSeconds()),
                () -> assertEquals("ask_question", profile.askUserTool()));
    }

    @Test
    @DisplayName("a harness without a headless block runs no class")
    void harnessWithoutHeadlessRunsNoClass() {
        var profile = HarnessProfile.of(loaded("""
                {"client_id":"acme-cli",
                 "tools":{"ask_user":"ask_user"},
                 "worker":{"eligible":false,"restricted_models":null,"idle_wakeups":null},
                 "headless":null}
                """));

        assertAll(
                () -> assertTrue(profile.runsNoClass()),
                () -> assertTrue(profile.modelEfforts().isEmpty()),
                () -> assertNull(profile.contextBudgetTokens()),
                () -> assertNull(profile.idleWakeups()),
                () -> assertFalse(profile.resumability().hold()),
                () -> assertFalse(profile.resumability().resume()));
    }

    private static LoadedProfile loaded(String document) {
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        return new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, JsonDocument.parse(bytes, "acme-cli.json"), bytes,
                LoadedProfile.State.VERIFIED, null);
    }
}
