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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.question.QuestionBound.Axis;
import de.planmarshall.workflow.question.QuestionBound.Key;

/**
 * The question bound (Model Work § Question Bound): at most three questions per state entry and per plan task, re-entries
 * through {@code <state>.asked} continuing the entry, whatever form each question took.
 */
@EnableGeneratorController
@DisplayName("Question bound")
class QuestionBoundTest {

    private static Key entry(String id) {
        return new Key(Axis.STATE_ENTRY, id);
    }

    private static Key planTask(String id) {
        return new Key(Axis.PLAN_TASK, id);
    }

    private static String entryId(int sequence) {
        return Generators.letterStrings(1, 8).next() + "/" + Generators.letterStrings(1, 8).next() + "#" + sequence;
    }

    private static String taskId() {
        return taskId(100, 999_999);
    }

    private static String taskId(int min, int max) {
        return String.format(Locale.ROOT, "TASK-%03d", Generators.integers(min, max).next());
    }

    @Test
    @DisplayName("the ask link is offered three times and not a fourth")
    void offersThreeTimes() {
        var bound = new QuestionBound();
        var entry = entry(entryId(1));

        assertAll(
                () -> assertTrue(bound.count(entry), "the first question is offered"),
                () -> assertTrue(bound.count(entry), "the second question is offered"),
                () -> assertTrue(bound.count(entry), "the third question is offered"),
                () -> assertFalse(bound.count(entry), "the fourth question is not offered"),
                () -> assertFalse(bound.mayOffer(entry), "the ask link is withheld after three"),
                () -> assertEquals(QuestionBound.MAX_ASKS, bound.asked(entry),
                        "the entry has asked three questions"),
                () -> assertEquals(0, bound.remaining(entry), "no question remains on the entry"));
    }

    @Test
    @DisplayName("a re-entry through the state's asked state continues the entry's count")
    void reentryContinuesTheEntry() {
        var bound = new QuestionBound();
        var entry = entry(entryId(1));
        bound.count(entry);
        bound.count(entry);

        // the re-entry passes the same entry identity: its third question is still offered, the fourth is not
        assertAll(
                () -> assertTrue(bound.mayOffer(entry), "the third question is still offered"),
                () -> assertTrue(bound.count(entry), "the re-entry counts the third question"),
                () -> assertFalse(bound.mayOffer(entry), "the fourth question is withheld"));
    }

    @Test
    @DisplayName("a new entry of the same state has its own count")
    void newEntryNewCount() {
        var bound = new QuestionBound();
        // one prefix, so both entries belong to the same state and only the sequence differs
        var prefix = Generators.letterStrings(1, 8).next() + "/" + Generators.letterStrings(1, 8).next();
        var first = entry(prefix + "#1");
        bound.count(first);
        bound.count(first);
        bound.count(first);

        assertTrue(bound.mayOffer(entry(prefix + "#2")), "a new entry of the state starts afresh");
    }

    @Test
    @DisplayName("a plan task counts its questions over all its envelope tasks")
    void planTaskAxis() {
        var bound = new QuestionBound();
        var task = planTask(taskId());

        assertAll(
                () -> assertTrue(bound.count(task), "the first plan task question is offered"),
                () -> assertTrue(bound.count(task), "the second plan task question is offered"),
                () -> assertTrue(bound.count(task), "the third plan task question is offered"),
                () -> assertFalse(bound.mayOffer(task), "the fourth plan task question is withheld"));
    }

    @Test
    @DisplayName("a question counts on both axes, and either spent axis withholds the link")
    void bothAxes() {
        var bound = new QuestionBound();
        var entry = entry(entryId(1));
        var task = planTask(taskId(100, 499_999));
        bound.count(task);
        bound.count(task);
        bound.count(task);

        assertAll(
                () -> assertFalse(bound.count(entry, task), "a spent plan task withholds the entry ask"),
                () -> assertEquals(0, bound.asked(entry), "the entry itself has asked nothing"),
                () -> assertTrue(bound.mayOffer(entry(entryId(2)), planTask(taskId(500_000, 999_999))),
                        "a fresh entry with a fresh task is offered"));
    }

    @Test
    @DisplayName("the two axes do not share a count")
    void axesAreSeparate() {
        var bound = new QuestionBound();
        var entry = entry(entryId(1));
        bound.count(entry);

        assertAll(
                () -> assertEquals(QuestionBound.MAX_ASKS, bound.remaining(planTask(taskId())),
                        "an untouched plan task keeps its three asks"),
                () -> assertEquals(QuestionBound.MAX_ASKS - 1, bound.remaining(entry),
                        "the entry has one ask left"));
    }

    @Test
    @DisplayName("a key names its entry or plan task")
    void keyNamesItsTarget() {
        var taskId = Generators.letterStrings(1, 8).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Key(Axis.STATE_ENTRY, " "),
                        "a blank entry id is refused"),
                () -> assertThrows(NullPointerException.class, () -> new Key(null, taskId),
                        "a null axis is refused"));
    }
}
