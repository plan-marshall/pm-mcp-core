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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;

/**
 * The builder of {@link GradingOffer} builds the value the canonical constructor builds, and a missing role is refused
 * with the same refusal.
 */
@DisplayName("Grading offer builder")
class GradingOfferBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent role alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentRoleAlike() {
        var canonical = new GradingOffer("review-code", List.of(Topic.OWN), 1, List.of(RoleForm.FRESH), 1, 0, 4_000);
        var built = GradingOffer.builder().role(canonical.role()).topics(canonical.topics())
                .items(canonical.items()).forms(canonical.forms()).runsPerItem(canonical.runsPerItem())
                .judgeItems(canonical.judgeItems()).expectedTokens(canonical.expectedTokens()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new GradingOffer(null, canonical.topics(), canonical.items(), canonical.forms(),
                                    canonical.runsPerItem(), canonical.judgeItems(), canonical.expectedTokens()),
                            "the constructor refuses a null role");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> GradingOffer.builder().topics(canonical.topics()).items(canonical.items())
                                    .forms(canonical.forms()).runsPerItem(canonical.runsPerItem())
                                    .judgeItems(canonical.judgeItems())
                                    .expectedTokens(canonical.expectedTokens()).build(),
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }
}
