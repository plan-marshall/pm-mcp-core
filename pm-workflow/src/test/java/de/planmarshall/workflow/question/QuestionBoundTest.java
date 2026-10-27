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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.question.QuestionBound.Axis;
import de.planmarshall.workflow.question.QuestionBound.Key;

/**
 * The question bound (Model Work § Question Bound): at most three questions per state entry and per plan task, re-entries
 * through {@code <state>.asked} continuing the entry, whatever form each question took.
 */
@DisplayName("Question bound")
class QuestionBoundTest {

    private static Key entry(String id) {
        return new Key(Axis.STATE_ENTRY, id);
    }

    private static Key planTask(String id) {
        return new Key(Axis.PLAN_TASK, id);
    }

    @Test
    @DisplayName("the ask link is offered three times and not a fourth")
    void offersThreeTimes() {
        var bound = new QuestionBound();
        var entry = entry("plan-a/execute.build#1");

        assertAll(
                () -> assertTrue(bound.count(entry)),
                () -> assertTrue(bound.count(entry)),
                () -> assertTrue(bound.count(entry)),
                () -> assertFalse(bound.count(entry)),
                () -> assertFalse(bound.mayOffer(entry)),
                () -> assertEquals(QuestionBound.MAX_ASKS, bound.asked(entry)),
                () -> assertEquals(0, bound.remaining(entry)));
    }

    @Test
    @DisplayName("a re-entry through the state's asked state continues the entry's count")
    void reentryContinuesTheEntry() {
        var bound = new QuestionBound();
        var entry = entry("plan-a/execute.build#1");
        bound.count(entry);
        bound.count(entry);

        // the re-entry passes the same entry identity: its third question is still offered, the fourth is not
        assertAll(
                () -> assertTrue(bound.mayOffer(entry)),
                () -> assertTrue(bound.count(entry)),
                () -> assertFalse(bound.mayOffer(entry)));
    }

    @Test
    @DisplayName("a new entry of the same state has its own count")
    void newEntryNewCount() {
        var bound = new QuestionBound();
        bound.count(entry("plan-a/execute.build#1"));
        bound.count(entry("plan-a/execute.build#1"));
        bound.count(entry("plan-a/execute.build#1"));

        assertTrue(bound.mayOffer(entry("plan-a/execute.build#2")));
    }

    @Test
    @DisplayName("a plan task counts its questions over all its envelope tasks")
    void planTaskAxis() {
        var bound = new QuestionBound();
        var task = planTask("TASK-003");

        assertAll(
                () -> assertTrue(bound.count(task)),
                () -> assertTrue(bound.count(task)),
                () -> assertTrue(bound.count(task)),
                () -> assertFalse(bound.mayOffer(task)));
    }

    @Test
    @DisplayName("a question counts on both axes, and either spent axis withholds the link")
    void bothAxes() {
        var bound = new QuestionBound();
        var entry = entry("plan-a/execute.build#1");
        var task = planTask("TASK-003");
        bound.count(planTask("TASK-003"));
        bound.count(planTask("TASK-003"));
        bound.count(planTask("TASK-003"));

        assertAll(
                () -> assertFalse(bound.count(entry, task)),
                () -> assertEquals(0, bound.asked(entry)),
                () -> assertTrue(bound.mayOffer(entry("plan-a/execute.build#2"), planTask("TASK-004"))));
    }

    @Test
    @DisplayName("the two axes do not share a count")
    void axesAreSeparate() {
        var bound = new QuestionBound();
        bound.count(entry("plan-a/execute.build#1"));

        assertAll(
                () -> assertEquals(QuestionBound.MAX_ASKS, bound.remaining(planTask("TASK-003"))),
                () -> assertEquals(QuestionBound.MAX_ASKS - 1, bound.remaining(entry("plan-a/execute.build#1"))));
    }

    @Test
    @DisplayName("a key names its entry or plan task")
    void keyNamesItsTarget() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Key(Axis.STATE_ENTRY, " ")),
                () -> assertThrows(NullPointerException.class, () -> new Key(null, "entry")));
    }
}
