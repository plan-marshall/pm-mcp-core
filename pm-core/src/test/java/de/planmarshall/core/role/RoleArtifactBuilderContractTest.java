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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link RoleArtifact} builds the value the canonical constructor builds, and a missing name is refused
 * with the same refusal.
 */
@DisplayName("Role artifact builder")
class RoleArtifactBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent name alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentNameAlike() {
        var canonical = RoleFixtures.valid().artifact();
        var built = RoleArtifact.builder().formatVersion(canonical.formatVersion()).name(canonical.name())
                .description(canonical.description()).roleClass(canonical.roleClass()).tools(canonical.tools())
                .harness(canonical.harness()).model(canonical.model()).effort(canonical.effort())
                .form(canonical.form()).count(canonical.count()).skills(canonical.skills())
                .recycling(canonical.recycling()).budgetFloorSeconds(canonical.budgetFloorSeconds())
                .contextBudgetTokens(canonical.contextBudgetTokens()).questions(canonical.questions())
                .offTrack(canonical.offTrack()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new RoleArtifact(canonical.formatVersion(), null, canonical.description(),
                                    canonical.roleClass(), canonical.tools(), canonical.harness(),
                                    canonical.model(), canonical.effort(), canonical.form(), canonical.count(),
                                    canonical.skills(), canonical.recycling(), canonical.budgetFloorSeconds(),
                                    canonical.contextBudgetTokens(), canonical.questions(), canonical.offTrack()),
                            "the constructor refuses a null name");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> RoleArtifact.builder().formatVersion(canonical.formatVersion())
                                    .description(canonical.description()).roleClass(canonical.roleClass())
                                    .tools(canonical.tools()).harness(canonical.harness()).model(canonical.model())
                                    .effort(canonical.effort()).form(canonical.form()).count(canonical.count())
                                    .skills(canonical.skills()).recycling(canonical.recycling())
                                    .budgetFloorSeconds(canonical.budgetFloorSeconds())
                                    .contextBudgetTokens(canonical.contextBudgetTokens())
                                    .questions(canonical.questions()).offTrack(canonical.offTrack()).build(),
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
