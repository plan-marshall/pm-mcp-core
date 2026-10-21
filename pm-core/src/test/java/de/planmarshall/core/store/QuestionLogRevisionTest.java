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

import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.revised;
import static de.planmarshall.core.store.QuestionRecordCodecTest.withdrawn;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;

/**
 * The revision of a question set (PM-WF-12, PM-IMPL-2): every open question of the set revised at once with the next
 * revision, the decision carried by the first question only, a closed question refused, and a partial revision a fault
 * of the caller.
 */
@DisplayName("Question log: a revision")
class QuestionLogRevisionTest extends QuestionLogFixture {

    @Test
    @DisplayName("appends a revised line for every question of its set, all with the new revision")
    void revisesWholeSet() throws Exception {
        askSet();
        var before = content();

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length)),
                () -> assertEquals(6, lines.records().size()),
                () -> assertEquals(List.of(OptionalInt.of(2), OptionalInt.of(2), OptionalInt.of(2)),
                        List.of(lines.currentRevision("Q-001"), lines.currentRevision("Q-002"),
                                lines.currentRevision("Q-003"))),
                () -> assertEquals(Optional.of(revised("Q-003", 2)), lines.current("Q-003")));
    }

    @Test
    @DisplayName("of a set with a closed question revises the open ones only")
    void revisesOpenQuestions() {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-002", 1))));

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertEquals(Optional.empty(), refusal),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-002")),
                () -> assertEquals(OptionalInt.of(2), log.read().currentRevision("Q-003")));
    }

    @Test
    @DisplayName("of a closed question is refused already_answered, and nothing is appended")
    void closedQuestion() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-002", 1))));
        var before = content();

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("carries the decision on the first question of the set, and only there")
    void decisionOnlyOnTheFirstQuestion() throws Exception {
        askSet();
        var onTheFirst = revised("Q-001", 2).toBuilder().decision(QuestionRecordCodecTest.decision()).build();
        var onAPart = revised("Q-002", 2).toBuilder().decision(QuestionRecordCodecTest.decision()).build();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(onTheFirst, onAPart, revised("Q-003", 2)))));
        var refusedNothing = log.read().records().size();
        var before = content();
        var accepted = write(t -> log.revise(t, List.of(onTheFirst, revised("Q-002", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("first question of the set"), fault.getMessage()),
                () -> assertEquals(3, refusedNothing, "a refused revision adds nothing"),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length),
                        "the accepted revision follows the untouched asked lines"),
                () -> assertEquals(Optional.empty(), accepted),
                () -> assertEquals(Optional.of(onTheFirst), log.read().current("Q-001"),
                        "the first question carries the decision of the revision"));
    }

    @Test
    @DisplayName("of a set whose first question is closed carries no decision: a changed decision needs a new one")
    void decisionAfterTheFirstQuestionClosed() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var onAPart = revised("Q-002", 2).toBuilder().decision(QuestionRecordCodecTest.decision()).build();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(onAPart, revised("Q-003", 2)))));
        var before = content();
        var accepted = write(t -> log.revise(t, List.of(revised("Q-002", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("first question of the set"), fault.getMessage()),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length),
                        "the refused revision adds nothing"),
                () -> assertEquals(Optional.empty(), accepted),
                () -> assertEquals(OptionalInt.of(2), log.read().currentRevision("Q-002")));
    }

    @Test
    @DisplayName("of a part of the set, with two revisions, or not above the current one is a fault of the caller")
    void malformedRevision() throws Exception {
        askSet();
        var before = content();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 3),
                                revised("Q-003", 2))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 1), revised("Q-002", 1),
                                revised("Q-003", 1))))),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("that skips a revision is a fault of the caller naming the next revision, and appends nothing")
    void skippedRevision() throws Exception {
        askSet();
        var before = content();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(revised("Q-001", 3), revised("Q-002", 3),
                        revised("Q-003", 3)))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("next revision 2"), fault.getMessage()),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("that revises every open question and then answers one of them is appended whole")
    void revisesWholeSetThenAnswers() {
        askSet();

        var refusal = write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2), answered("Q-002", 2))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal),
                () -> assertEquals(7, lines.records().size()),
                () -> assertTrue(lines.isClosed("Q-002")),
                () -> assertFalse(lines.isClosed("Q-003")),
                () -> assertEquals(OptionalInt.of(2), lines.currentRevision("Q-003")));
    }

    @Test
    @DisplayName("that revises every open question and then withdraws one of them is appended whole")
    void revisesWholeSetThenWithdraws() {
        askSet();

        var refusal = write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2), withdrawn("Q-003"))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal),
                () -> assertEquals(7, lines.records().size()),
                () -> assertEquals(Optional.of(withdrawn("Q-003")), lines.current("Q-003")),
                () -> assertFalse(lines.isClosed("Q-001")));
    }

    @Test
    @DisplayName("that revises part of the open set and then answers the rest is a fault, and appends nothing")
    void revisesPartThenAnswers() throws Exception {
        askSet();
        var before = content();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                                answered("Q-003", 1))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                                answered("Q-002", 2))))),
                () -> assertArrayEquals(before, content()));
    }
}
