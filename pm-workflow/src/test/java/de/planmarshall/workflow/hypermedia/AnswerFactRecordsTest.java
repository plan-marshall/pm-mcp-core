/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.hypermedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link AnswerFact} builds the value its canonical constructor builds, including its null
 * refusal for the question id. The generated values stay within the record's column bounds, the identifiers follow
 * their forms and the branch key is the specification's reserved {@code value} key.
 */
@EnableGeneratorController
@DisplayName("AnswerFact builder")
class AnswerFactRecordsTest {

    @Test
    @DisplayName("the builder builds the answer fact the constructor builds")
    void answerFactBuilderEqualsConstructor() {
        var questionId = "Q-001";
        var planTaskId = "TASK-001";
        var questionText = Generators.letterStrings(1, 40).next();
        var branchKey = "value";
        var label = Generators.letterStrings(1, 20).next();
        var text = Generators.letterStrings(1, 40).next();
        var kind = AnswerFact.Kind.CHOSEN;

        var constructed = new AnswerFact(questionId, planTaskId, questionText, branchKey, label, text, kind);
        var built = AnswerFact.builder()
                .questionId(questionId)
                .planTaskId(planTaskId)
                .questionText(questionText)
                .branchKey(branchKey)
                .label(label)
                .text(text)
                .kind(kind)
                .build();

        assertEquals(constructed, built, "the builder builds the same answer fact");
    }

    @Test
    @DisplayName("the builder refuses a missing question id like the constructor")
    void answerFactBuilderRefusesMissingQuestionId() {
        var refusal = assertThrows(NullPointerException.class, () -> AnswerFact.builder().build());

        assertEquals("questionId", refusal.getMessage(), "the refusal names the question id");
    }
}
