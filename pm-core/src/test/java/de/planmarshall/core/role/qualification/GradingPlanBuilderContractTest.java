/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;

/**
 * The builder of {@link GradingPlan} builds the value the canonical constructor builds, and a missing role is refused
 * with the same refusal.
 */
@DisplayName("Grading plan builder")
class GradingPlanBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent role alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentRoleAlike() {
        var judge = new JudgeConfiguration(
                QualificationFixtures.role("judge", RoleClass.READ_ONLY, RoleForm.FRESH), Set.of(), List.of());
        var canonical = new GradingPlan(
                QualificationFixtures.role("review-code", RoleClass.READ_ONLY, RoleForm.FRESH), List.of(),
                new ImportResult(List.of(), List.of()), "corpus-1", URI.create("https://github.com/example/project"),
                GradingProfile.single(), judge, ItemCheckout.none(), null);
        var built = GradingPlan.builder().role(canonical.role()).items(canonical.items())
                .importResult(canonical.importResult()).corpusRevision(canonical.corpusRevision())
                .projectRepository(canonical.projectRepository()).profile(canonical.profile())
                .judge(canonical.judge()).checkouts(canonical.checkouts()).previous(canonical.previous()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new GradingPlan(null, canonical.items(), canonical.importResult(),
                                    canonical.corpusRevision(), canonical.projectRepository(), canonical.profile(),
                                    canonical.judge(), canonical.checkouts(), null),
                            "the constructor refuses a null role");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> GradingPlan.builder().items(canonical.items())
                                    .importResult(canonical.importResult())
                                    .corpusRevision(canonical.corpusRevision())
                                    .projectRepository(canonical.projectRepository()).profile(canonical.profile())
                                    .judge(canonical.judge()).checkouts(canonical.checkouts()).build(),
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }
}
