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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRecord.Blocks;
import de.planmarshall.core.store.QuestionRecord.Decision;
import de.planmarshall.core.store.QuestionRecord.Reversibility;
import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;

/**
 * The revision of a question set (PM-WF-12, PM-IMPL-2): every open question of the set revised at once with the next
 * revision, the decision carried by the first question only, a closed question refused, and a partial revision a fault
 * of the caller. The question ids of the set, the revision counters that pin the order and the fragments of the fault
 * messages are the contract; the revised decision and a skipped revision are generated.
 */
@EnableGeneratorController
@DisplayName("Question log: a revision")
class QuestionLogRevisionTest extends QuestionLogFixture {

    /** @return a decision of the revision, its title and header generated */
    private static Decision revisedDecision() {
        return new Decision(QuestionRecordCodecTest.text(Generators.integers(1, 4).next()),
                QuestionRecordCodecTest.text(Generators.integers(1, 8).next()), "", List.of(), Blocks.TASK,
                Reversibility.COSTLY);
    }

    @Test
    @DisplayName("appends a revised line for every question of its set, all with the new revision")
    void revisesWholeSet() throws Exception {
        askSet();
        var before = content();

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal, "the log should accept the revision"),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length),
                        "the asked lines should stay as they were"),
                () -> assertEquals(6, lines.records().size(), "the log should hold six lines"),
                () -> assertEquals(List.of(OptionalInt.of(2), OptionalInt.of(2), OptionalInt.of(2)),
                        List.of(lines.currentRevision("Q-001"), lines.currentRevision("Q-002"),
                                lines.currentRevision("Q-003")), "every question should be at revision 2"),
                () -> assertEquals(Optional.of(revised("Q-003", 2)), lines.current("Q-003"),
                        "the revised line of Q-003 should be current"));
    }

    @Test
    @DisplayName("of a set with a closed question revises the open ones only")
    void revisesOpenQuestions() {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-002", 1))));

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertEquals(Optional.empty(), refusal, "the log should accept the revision"),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-002"),
                        "the closed question should stay at 1"),
                () -> assertEquals(OptionalInt.of(2), log.read().currentRevision("Q-003"),
                        "the open question should be at revision 2"));
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
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal,
                        "the closed question should be refused"),
                () -> assertArrayEquals(before, content(), "the refused revision should append nothing"));
    }

    @Test
    @DisplayName("carries the decision on the first question of the set, and only there")
    void decisionOnlyOnTheFirstQuestion() throws Exception {
        askSet();
        var decision = revisedDecision();
        var onTheFirst = revised("Q-001", 2).toBuilder().decision(decision).build();
        var onAPart = revised("Q-002", 2).toBuilder().decision(decision).build();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(onTheFirst, onAPart, revised("Q-003", 2)))),
                "a decision on a part should be a fault");
        var refusedNothing = log.read().records().size();
        var before = content();
        var accepted = write(t -> log.revise(t, List.of(onTheFirst, revised("Q-002", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("first question of the set"),
                        "the fault should name the first question"),
                () -> assertEquals(3, refusedNothing, "a refused revision adds nothing"),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length),
                        "the accepted revision follows the asked lines"),
                () -> assertEquals(Optional.empty(), accepted, "the log should accept the revision"),
                () -> assertEquals(Optional.of(onTheFirst), log.read().current("Q-001"),
                        "the first question carries the new decision"));
    }

    @Test
    @DisplayName("of a set whose first question is closed carries no decision: a changed decision needs a new one")
    void decisionAfterTheFirstQuestionClosed() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var onAPart = revised("Q-002", 2).toBuilder().decision(revisedDecision()).build();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(onAPart, revised("Q-003", 2)))),
                "a decision on a part should be a fault");
        var before = content();
        var accepted = write(t -> log.revise(t, List.of(revised("Q-002", 2), revised("Q-003", 2))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("first question of the set"),
                        "the fault should name the first question"),
                () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length),
                        "the refused revision adds nothing"),
                () -> assertEquals(Optional.empty(), accepted, "the log should accept the revision"),
                () -> assertEquals(OptionalInt.of(2), log.read().currentRevision("Q-002"),
                        "the open part should be at revision 2"));
    }

    @Test
    @DisplayName("of a part of the set, with two revisions, or not above the current one is a fault of the caller")
    void malformedRevision() throws Exception {
        askSet();
        var before = content();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2)))),
                        "a revision of a part of the set is a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 3),
                                revised("Q-003", 2)))), "a revision with two numbers should be a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.revise(t, List.of(revised("Q-001", 1), revised("Q-002", 1),
                                revised("Q-003", 1)))), "a revision not above the current is a fault"),
                () -> assertArrayEquals(before, content(), "the faulty revisions should append nothing"));
    }

    @Test
    @DisplayName("that skips a revision is a fault of the caller naming the next revision, and appends nothing")
    void skippedRevision() throws Exception {
        askSet();
        var before = content();
        // 3 is the first skipped revision, so it is the boundary of the contract and stays a literal
        var firstSkipped = reviseSkipping(3);
        var skipped = reviseSkipping(Generators.integers(4, 100).next());

        assertAll(
                () -> assertTrue(firstSkipped.getMessage().contains("next revision 2"),
                        "the first skipped revision should name revision 2"),
                () -> assertTrue(skipped.getMessage().contains("next revision 2"),
                        "the fault should name the next revision 2"),
                () -> assertArrayEquals(before, content(), "the skipped revision should append nothing"));
    }

    /** @return the fault of revising every question of the set to the given revision, which is not the next one */
    private IllegalArgumentException reviseSkipping(int revision) {
        return assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.revise(t, List.of(revised("Q-001", revision), revised("Q-002", revision),
                        revised("Q-003", revision)))), "a skipped revision should be a fault");
    }

    @Test
    @DisplayName("that revises every open question and then answers one of them is appended whole")
    void revisesWholeSetThenAnswers() {
        askSet();

        var refusal = write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2), answered("Q-002", 2))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal, "the log should accept the append"),
                () -> assertEquals(7, lines.records().size(), "the log should hold seven lines"),
                () -> assertTrue(lines.isClosed("Q-002"), "the answered question should be closed"),
                () -> assertFalse(lines.isClosed("Q-003"), "the revised question should stay open"),
                () -> assertEquals(OptionalInt.of(2), lines.currentRevision("Q-003"),
                        "the open question should be at revision 2"));
    }

    @Test
    @DisplayName("that revises every open question and then withdraws one of them is appended whole")
    void revisesWholeSetThenWithdraws() {
        askSet();

        var refusal = write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2), withdrawn("Q-003"))));

        var lines = log.read();
        assertAll(
                () -> assertEquals(Optional.empty(), refusal, "the log should accept the append"),
                () -> assertEquals(7, lines.records().size(), "the log should hold seven lines"),
                () -> assertEquals(Optional.of(withdrawn("Q-003")), lines.current("Q-003"),
                        "the withdrawal of Q-003 should be current"),
                () -> assertFalse(lines.isClosed("Q-001"), "the first question should stay open"));
    }

    @Test
    @DisplayName("that revises part of the open set and then answers the rest is a fault, and appends nothing")
    void revisesPartThenAnswers() throws Exception {
        askSet();
        var before = content();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                                answered("Q-003", 1)))), "answering the unrevised rest is a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.append(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                                answered("Q-002", 2)))), "a partial revision then answer is a fault"),
                () -> assertArrayEquals(before, content(), "the faulty appends should append nothing"));
    }
}
