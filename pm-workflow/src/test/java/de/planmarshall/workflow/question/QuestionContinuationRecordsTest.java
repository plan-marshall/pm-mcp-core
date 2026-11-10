/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link QuestionContinuation.Decision} builds the value its canonical constructor builds,
 * including its null refusal for the form. The hold limit is generated and the form is the specification's hold form.
 */
@EnableGeneratorController
@DisplayName("QuestionContinuation.Decision builder")
class QuestionContinuationRecordsTest {

    @Test
    @DisplayName("the builder builds the decision the constructor builds")
    void decisionBuilderEqualsConstructor() {
        var form = QuestionContinuation.Form.HOLD;
        var holdUntil = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());

        var constructed = new QuestionContinuation.Decision(form, holdUntil, null, null);
        var built = QuestionContinuation.Decision.builder()
                .form(form)
                .holdUntil(holdUntil)
                .build();

        assertEquals(constructed, built, "the builder builds the same decision");
    }

    @Test
    @DisplayName("the builder refuses a missing form like the constructor")
    void decisionBuilderRefusesMissingForm() {
        var refusal = assertThrows(NullPointerException.class, () -> QuestionContinuation.Decision.builder().build());

        assertEquals("form", refusal.getMessage(), "the refusal names the decision form");
    }
}
