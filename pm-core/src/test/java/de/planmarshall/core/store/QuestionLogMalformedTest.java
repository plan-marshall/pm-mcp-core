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
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.revised;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A line malformed for the question log (PM-WF-12, PM-IMPL-2): a question asked twice, a part asked into a set the log
 * holds, a set the log does not hold, a line of another event or through a transaction of another scope. Each is a
 * fault of the caller, an `IllegalArgumentException`, and none appends a line. The malformed lines and the fragments of
 * the fault messages are the contract; the questions outside the set and the other plan are generated.
 */
@EnableGeneratorController
@DisplayName("Question log: a line malformed for the log")
class QuestionLogMalformedTest extends QuestionLogFixture {

    /** @return a question id outside the set Q-001, between the given numbers */
    private static String outsideTheSet(int from, int to) {
        return "Q-%03d".formatted(Generators.integers(from, to).next());
    }

    @Test
    @DisplayName("a question asked twice, or a line about a question the log has not asked, is refused")
    void unknownOrRepeatedQuestion() {
        askSet();
        var notAsked = outsideTheSet(4, 999);
        var part = outsideTheSet(4, 500);
        var set = outsideTheSet(501, 999);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-002")))),
                        "a question asked twice should be a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.answer(t, List.of(answered(notAsked, 1)))),
                        "an answer to an unknown question is a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked(part, set)))),
                        "a part of an unknown set should be a fault"));
    }

    @Test
    @DisplayName("asking a further part into a set the log already holds is a fault, and appends nothing")
    void partIntoExistingSet() throws Exception {
        askSet();
        var before = content();
        var part = outsideTheSet(4, 999);

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked(part, "Q-001")))),
                "a part into a held set should be a fault");

        assertAll(
                () -> assertTrue(fault.getMessage().contains("asked already"),
                        "the fault should say the set is asked already"),
                () -> assertArrayEquals(before, content(), "the faulty append should add nothing"),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-001"),
                        "the set stays revisable as it was asked"));
    }

    @Test
    @DisplayName("a part that names a set the log does not hold is refused")
    void partNamesASetTheLogDoesNotHold() throws Exception {
        askSet();
        var before = content();
        var part = outsideTheSet(4, 500);
        var set = outsideTheSet(501, 999);

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked(part, set)))),
                "a part of an unknown set should be a fault");

        assertAll(
                () -> assertTrue(fault.getMessage().contains("which is not asked"),
                        "the fault should say the set is not asked"),
                () -> assertArrayEquals(before, content(), "the faulty append should add nothing"));
    }

    @Test
    @DisplayName("a part that names a question which is no first question of a set is refused")
    void partNamesNoFirstQuestion() throws Exception {
        askSet();
        var before = content();
        var part = outsideTheSet(4, 999);

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked(part, "Q-002")))),
                "a part of a part should be a fault");

        assertAll(
                () -> assertTrue(fault.getMessage().contains("no first question of a set"),
                        "the fault should name no first question"),
                () -> assertTrue(fault.getMessage().contains(part), "the fault should name the asked part"),
                () -> assertTrue(fault.getMessage().contains("Q-002"), "the fault should name the named question"),
                () -> assertArrayEquals(before, content(), "the faulty append should add nothing"));
    }

    @Test
    @DisplayName("asking a part after a revision of its set began in the same append is refused, and nothing is"
            + " appended")
    void partAfterRevisionInOneAppend() {
        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-001", "Q-001"), revised("Q-001", 2),
                        asked("Q-002", "Q-001")))), "a part after a revision should be a fault");
        write(t -> log.append(t, List.of(asked("Q-003", "Q-003"), asked("Q-004", "Q-003"))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("revised already"),
                        "the fault should say the set is revised"),
                () -> assertEquals(2, log.read().records().size(),
                        "only the accepted append should hold lines"),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-003"),
                        "the accepted set should be at revision 1"));
    }

    @Test
    @DisplayName("asking a part after the first question of its set was closed in the same append is refused")
    void partAfterFirstQuestionClosedInOneAppend() {
        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-001", "Q-001"), answered("Q-001", 1),
                        asked("Q-002", "Q-001")))), "a part after the close should be a fault");

        assertAll(
                () -> assertTrue(fault.getMessage().contains("closed already"),
                        "the fault should say the set is closed"),
                () -> assertFalse(Files.exists(log.store()), "the refused append adds nothing"));
    }

    @Test
    @DisplayName("a set not named by its first question, a line of another event, or no line is refused")
    void wrongShape() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-001"), asked("Q-001", "Q-001")))),
                        "a set not led by its first question is a fault"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.answer(t, List.of(delivered("Q-001", 1)))),
                        "a delivered line as an answer is a fault"),
                () -> assertThrows(IllegalArgumentException.class, () -> write(t -> log.append(t, List.of())),
                        "an append without a line should be a fault"),
                () -> assertFalse(Files.exists(log.store()), "the faulty appends should create no log"));
    }

    @Test
    @DisplayName("an append through a transaction of another scope is refused")
    void otherScope() throws Exception {
        var other = JournalScope.plan(base, PROJECT,
                "other-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT));
        Files.createDirectories(other.journalFile().getParent());
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(other.lockKey());
            var write = ScopeTransaction.open(transaction, other, audit, CLOCK);
            var questions = List.of(asked("Q-001", "Q-001"));

            assertThrows(IllegalArgumentException.class, () -> log.ask(write, questions),
                    "a transaction of another scope is a fault");
        }
    }
}
