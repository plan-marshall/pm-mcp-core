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

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link HarnessProfile} and of its nested {@link HarnessProfile.Resumability} build the values the
 * canonical constructors build.
 */
@EnableGeneratorController
@DisplayName("Harness profile builders")
class HarnessProfileBuilderContractTest {

    private static final String CLIENT_ID = Generators.letterStrings(3, 10).next();
    private static final String MODEL = Generators.letterStrings(3, 10).next();

    @Test
    @DisplayName("the profile builder builds the constructor's value")
    void shouldBuildProfileLikeTheConstructor() {
        var canonical = new HarnessProfile(CLIENT_ID, true, true, Set.of("acme/*"),
                Map.of(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED), Map.of(MODEL, List.of("medium", "high")),
                Map.of("standard", MODEL), 200_000, 3,
                new HarnessProfile.Resumability(true, 300, true, 600), "ask_user");
        var built = HarnessProfile.builder().clientId(canonical.clientId()).eligible(canonical.eligible())
                .restrictedModelsDeclared(canonical.restrictedModelsDeclared())
                .restrictedModels(canonical.restrictedModels()).classes(canonical.classes())
                .modelEfforts(canonical.modelEfforts()).modelTiers(canonical.modelTiers())
                .contextBudgetTokens(canonical.contextBudgetTokens()).idleWakeups(canonical.idleWakeups())
                .resumability(canonical.resumability()).askUserTool(canonical.askUserTool()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the resumability builder builds the constructor's value")
    void shouldBuildResumabilityLikeTheConstructor() {
        var canonical = new HarnessProfile.Resumability(true, 300, true, 600);
        var built = HarnessProfile.Resumability.builder().hold(canonical.hold())
                .maxHoldSeconds(canonical.maxHoldSeconds()).resume(canonical.resume())
                .maxResumeAgeSeconds(canonical.maxResumeAgeSeconds()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
