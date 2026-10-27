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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code facts.answers} (Hypermedia Format § Answers to Earlier Questions): the items a continued task or the task of a
 * re-entered state carries, in the specification's columns.
 */
@DisplayName("Answer facts")
class AnswersTest {

    private static AnswerFact fact(String questionId, String planTaskId) {
        return new AnswerFact(questionId, planTaskId, "Which contract?",
                "option_a", "Option A", "the operator's note", AnswerFact.Kind.CHOSEN);
    }

    @Test
    @DisplayName("an item renders the specification's columns in their order")
    void columns() {
        var row = fact("Q-007", "TASK-003").cells();

        assertAll(
                () -> assertEquals(AnswerFact.COLUMNS, List.of("question_id", "plan_task_id", "question_text", "branch_key", "label",
                        "text", "kind")),
                () -> assertEquals(List.of("Q-007", "TASK-003", "Which contract?", "option_a", "Option A",
                        "the operator's note", "chosen"), row));
    }

    @Test
    @DisplayName("a question of another class than task carries no plan task, and no words render empty")
    void optionalColumns() {
        var row = new AnswerFact("Q-008", null, "Which scope?", "value", "the workspace", null,
                AnswerFact.Kind.INTERPRETED).cells();

        assertAll(
                () -> assertEquals("", row.get(1)),
                () -> assertEquals("", row.get(5)),
                () -> assertEquals("interpreted", row.get(6)));
    }

    @Test
    @DisplayName("the answers table carries one row per item, in the order the questions closed")
    void table() {
        var answers = Answers.empty().plus(fact("Q-007", "TASK-003")).plus(fact("Q-008", null));

        assertAll(
                () -> assertFalse(answers.isEmpty()),
                () -> assertEquals(2, answers.rows().size()),
                () -> assertEquals("Q-007", answers.rows().getFirst().getFirst()),
                () -> assertEquals("Q-008", answers.rows().get(1).getFirst()));
    }

    @Test
    @DisplayName("a retried delivery of the same question replaces its item instead of doubling it")
    void idempotentDelivery() {
        var answers = Answers.empty().plus(fact("Q-007", "TASK-003")).plus(fact("Q-007", "TASK-003"));

        assertEquals(1, answers.rows().size());
    }

    @Test
    @DisplayName("the answers of two tasks merge by question")
    void merge() {
        var earlier = Answers.empty().plus(fact("Q-007", "TASK-003"));
        var later = Answers.empty().plus(fact("Q-008", null));

        assertAll(
                () -> assertEquals(2, earlier.plusAll(later).rows().size()),
                () -> assertTrue(Answers.empty().isEmpty()));
    }

    @Test
    @DisplayName("a question id or a branch key that breaks its form is refused")
    void refusals() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerFact("Q-7", null, "Which?", "option_a", "A", null,
                                AnswerFact.Kind.CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerFact("Q-007", "task-3", "Which?", "option_a", "A", null,
                                AnswerFact.Kind.CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerFact("Q-007", null, " ", "option_a", "A", null,
                                AnswerFact.Kind.CHOSEN)));
    }
}
