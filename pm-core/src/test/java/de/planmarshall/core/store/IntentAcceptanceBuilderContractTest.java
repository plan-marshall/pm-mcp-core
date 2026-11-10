/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link IntentAcceptance.Context} builds the value the canonical constructor builds, and an absent plan
 * id is refused with the same refusal.
 */
@DisplayName("Intent acceptance context builder")
class IntentAcceptanceBuilderContractTest {

    private static final String PLAN_ID = "106-plan-intent-and-outline-records";
    private static final Path TREE_ROOT = Path.of("/pm-base/tree");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent plan id")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentPlanId() {
        var canonical = new IntentAcceptance.Context(PLAN_ID, questionId -> Optional.empty(),
                (capability, requirement) -> Optional.empty(), null, TREE_ROOT);
        var built = IntentAcceptance.Context.builder().planId(canonical.planId())
                .questions(canonical.questions()).capabilities(canonical.capabilities()).epic(canonical.epic())
                .treeRoot(canonical.treeRoot()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentAcceptance.Context(null, questionId -> Optional.empty(),
                                    (capability, requirement) -> Optional.empty(), null, TREE_ROOT),
                            "the constructor refuses a null plan id");
                    var incomplete = IntentAcceptance.Context.builder()
                            .questions(questionId -> Optional.empty())
                            .capabilities((capability, requirement) -> Optional.empty()).treeRoot(TREE_ROOT);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing plan id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent plan id with the same message");
                });
    }
}
