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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link EffectiveRole} builds the value the canonical constructor builds, and a missing artifact is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Effective role builder")
class EffectiveRoleBuilderContractTest {

    private static final String HARNESS = Generators.letterStrings(3, 10).next();
    private static final String MODEL = Generators.letterStrings(3, 10).next();

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent artifact alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentArtifactAlike() {
        var skill = new RoleSkill("skill://pm/role/implement/SKILL.md", RoleFixtures.SKILL_DIGEST);
        var canonical = new EffectiveRole(RoleFixtures.valid().artifact(), HARNESS, MODEL, new EffortValue("high"),
                List.of(skill), "1.4", "weights-identity", new RoleArtifact.Questions(300, 600), Map.of(), 200_000,
                OptionalInt.of(3));
        var built = EffectiveRole.builder().artifact(canonical.artifact()).harness(canonical.harness())
                .model(canonical.model()).effort(canonical.effort()).skills(canonical.skills())
                .harnessVersion(canonical.harnessVersion()).weightsIdentity(canonical.weightsIdentity())
                .questions(canonical.questions()).offTrack(canonical.offTrack())
                .contextBudgetTokens(canonical.contextBudgetTokens()).idleWakeups(canonical.idleWakeups()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack = Map.of();
                    var idleWakeups = OptionalInt.empty();
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new EffectiveRole(null, HARNESS, MODEL, canonical.effort(), canonical.skills(),
                                    "1.4", null, canonical.questions(), offTrack, 200_000, idleWakeups),
                            "the constructor refuses a null artifact");
                    var incomplete = EffectiveRole.builder().harness(HARNESS).model(MODEL)
                            .effort(canonical.effort()).skills(canonical.skills()).harnessVersion("1.4")
                            .questions(canonical.questions()).offTrack(Map.of())
                            .contextBudgetTokens(200_000).idleWakeups(OptionalInt.empty());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing artifact");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent artifact with the same message");
                });
    }
}
