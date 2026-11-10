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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link JudgeScorer.JudgePrompt} builds the value the canonical constructor builds, and a missing item
 * id is refused with the same refusal.
 */
@DisplayName("Judge scorer builder")
class JudgeScorerBuilderContractTest {

    @Test
    @DisplayName("the prompt builder builds the constructor's value and refuses an absent item id alike")
    void shouldBuildPromptLikeTheConstructorAndRefuseAnAbsentItemIdAlike() {
        var canonical = new JudgeScorer.JudgePrompt("judge/q-1", "Is it right?", "Subject", "right", "right answer");
        var built = JudgeScorer.JudgePrompt.builder().itemId(canonical.itemId()).question(canonical.question())
                .facts(canonical.facts()).reference(canonical.reference()).answer(canonical.answer()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JudgeScorer.JudgePrompt(null, "Is it right?", "Subject", "right",
                                    "right answer"),
                            "the constructor refuses a null item id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> JudgeScorer.JudgePrompt.builder().question("Is it right?").facts("Subject")
                                    .reference("right").answer("right answer").build(),
                            "the builder refuses a missing item id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent item id with the same message");
                });
    }
}
