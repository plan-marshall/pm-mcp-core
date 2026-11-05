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
import java.util.Locale;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * {@code facts.answers} (Hypermedia Format § Answers to Earlier Questions): the items a continued task or the task of a
 * re-entered state carries, in the specification's columns.
 */
@EnableGeneratorController
@DisplayName("Answer facts")
class AnswersTest {

    private static String questionId() {
        return questionId(100, 999_999);
    }

    private static String questionId(int min, int max) {
        // Locale.ROOT keeps the digits ASCII: AnswerFact's pattern matches [0-9] only
        return String.format(Locale.ROOT, "Q-%03d", Generators.integers(min, max).next());
    }

    private static String taskId() {
        return String.format(Locale.ROOT, "TASK-%03d", Generators.integers(100, 999_999).next());
    }

    private static String branchKey() {
        return "option_" + Generators.letterStrings(1, 8).next().toLowerCase(Locale.ROOT);
    }

    private static AnswerFact fact(String questionId, String planTaskId) {
        return new AnswerFact(questionId, planTaskId, Generators.letterStrings(1, 40).next(),
                branchKey(), Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 40).next(),
                AnswerFact.Kind.CHOSEN);
    }

    @Test
    @DisplayName("an item renders the specification's columns in their order")
    void columns() {
        var askedQuestionId = questionId();
        var askedTaskId = taskId();
        var questionText = Generators.letterStrings(1, 40).next();
        var key = branchKey();
        var label = Generators.letterStrings(1, 20).next();
        var text = Generators.letterStrings(1, 40).next();
        var row = new AnswerFact(askedQuestionId, askedTaskId, questionText, key, label, text,
                AnswerFact.Kind.CHOSEN).cells();

        assertAll(
                () -> assertEquals(AnswerFact.COLUMNS, List.of("question_id", "plan_task_id", "question_text", "branch_key", "label",
                        "text", "kind")),
                () -> assertEquals(List.of(askedQuestionId, askedTaskId, questionText, key, label, text, "chosen"),
                        row));
    }

    @Test
    @DisplayName("a question of another class than task carries no plan task, and no words render empty")
    void optionalColumns() {
        var row = new AnswerFact(questionId(), null, Generators.letterStrings(1, 40).next(), branchKey(),
                Generators.letterStrings(1, 20).next(), null, AnswerFact.Kind.INTERPRETED).cells();

        assertAll(
                () -> assertEquals("", row.get(1)),
                () -> assertEquals("", row.get(5)),
                () -> assertEquals("interpreted", row.get(6)));
    }

    @Test
    @DisplayName("the answers table carries one row per item, in the order the questions closed")
    void table() {
        var first = fact(questionId(100, 499_999), taskId());
        var second = fact(questionId(500_000, 999_999), null);
        var answers = Answers.empty().plus(first).plus(second);

        assertAll(
                () -> assertFalse(answers.isEmpty()),
                () -> assertEquals(2, answers.rows().size()),
                () -> assertEquals(first.questionId(), answers.rows().getFirst().getFirst()),
                () -> assertEquals(second.questionId(), answers.rows().get(1).getFirst()));
    }

    @Test
    @DisplayName("a retried delivery of the same question replaces its item instead of doubling it")
    void idempotentDelivery() {
        var askedQuestionId = questionId();
        var askedTaskId = taskId();
        var retried = fact(askedQuestionId, askedTaskId);
        var answers = Answers.empty().plus(fact(askedQuestionId, askedTaskId)).plus(retried);

        assertAll(
                () -> assertEquals(1, answers.rows().size(), "A retry keeps one answer row"),
                () -> assertEquals(retried.cells(), answers.rows().getFirst(),
                        "A retry keeps the retried answer cells"));
    }

    @Test
    @DisplayName("the answers of two tasks merge by question")
    void merge() {
        var earlier = Answers.empty().plus(fact(questionId(100, 499_999), taskId()));
        var later = Answers.empty().plus(fact(questionId(500_000, 999_999), null));

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
                                AnswerFact.Kind.CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerFact("Q-007", null, "Which?", "Option A", "A", null,
                                AnswerFact.Kind.CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerFact("Q-007", null, "Which?", "option_a+", "A", null,
                                AnswerFact.Kind.CHOSEN)));
    }

    @Test
    @DisplayName("the selected keys of a several answer and the two reserved keys are branch keys")
    void branchKeys() {
        var firstKey = branchKey();
        var secondKey = branchKey();
        while (firstKey.equals(secondKey)) {
            secondKey = branchKey();
        }
        var several = firstKey + "+" + secondKey;
        assertAll(
                () -> assertEquals(several,
                        new AnswerFact(questionId(), null, Generators.letterStrings(1, 40).next(), several,
                                Generators.letterStrings(1, 20).next(), null,
                                AnswerFact.Kind.CHOSEN).branchKey()),
                // "value" and "own" are the two reserved branch keys of the specification
                () -> assertEquals("value",
                        new AnswerFact(questionId(), null, Generators.letterStrings(1, 40).next(), "value",
                                Generators.letterStrings(1, 20).next(), null,
                                AnswerFact.Kind.CHOSEN).branchKey()),
                () -> assertEquals("own",
                        new AnswerFact(questionId(), null, Generators.letterStrings(1, 40).next(), "own",
                                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 40).next(),
                                AnswerFact.Kind.OWN_OPTION).branchKey()));
    }
}
