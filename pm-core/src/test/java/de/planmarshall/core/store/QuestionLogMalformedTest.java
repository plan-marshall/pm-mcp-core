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

import java.nio.file.Files;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A line malformed for the question log (PM-WF-12, PM-IMPL-2): a question asked twice, a part asked into a set the log
 * holds, a set the log does not hold, a line of another event or through a transaction of another scope. Each is a
 * fault of the caller, an `IllegalArgumentException`, and none appends a line.
 */
@DisplayName("Question log: a line malformed for the log")
class QuestionLogMalformedTest extends QuestionLogFixture {

    @Test
    @DisplayName("a question asked twice, or a line about a question the log has not asked, is refused")
    void unknownOrRepeatedQuestion() {
        askSet();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-002"))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.answer(t, List.of(answered("Q-009", 1))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked("Q-005", "Q-004"))))));
    }

    @Test
    @DisplayName("asking a further part into a set the log already holds is a fault, and appends nothing")
    void partIntoExistingSet() throws Exception {
        askSet();
        var before = content();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-004", "Q-001")))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("asked already"), fault.getMessage()),
                () -> assertArrayEquals(before, content()),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-001"),
                        "the set stays revisable as it was asked"));
    }

    @Test
    @DisplayName("a part that names a set the log does not hold is refused")
    void partNamesASetTheLogDoesNotHold() throws Exception {
        askSet();
        var before = content();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-004", "Q-009")))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("which is not asked"), fault.getMessage()),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("a part that names a question which is no first question of a set is refused")
    void partNamesNoFirstQuestion() throws Exception {
        askSet();
        var before = content();

        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-005", "Q-002")))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("no first question of a set"), fault.getMessage()),
                () -> assertTrue(fault.getMessage().contains("Q-005"), fault.getMessage()),
                () -> assertTrue(fault.getMessage().contains("Q-002"), fault.getMessage()),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("asking a part after a revision of its set began in the same append is refused, and nothing is"
            + " appended")
    void partAfterRevisionInOneAppend() {
        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-001", "Q-001"), revised("Q-001", 2),
                        asked("Q-002", "Q-001")))));
        var accepted = write(t -> log.append(t, List.of(asked("Q-003", "Q-003"), asked("Q-004", "Q-003"))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("revised already"), fault.getMessage()),
                () -> assertEquals(2, log.read().records().size(),
                        "the refused append added nothing, the accepted one asked its whole set"),
                () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-003")));
    }

    @Test
    @DisplayName("asking a part after the first question of its set was closed in the same append is refused")
    void partAfterFirstQuestionClosedInOneAppend() {
        var fault = assertThrows(IllegalArgumentException.class,
                () -> write(t -> log.append(t, List.of(asked("Q-001", "Q-001"), answered("Q-001", 1),
                        asked("Q-002", "Q-001")))));

        assertAll(
                () -> assertTrue(fault.getMessage().contains("closed already"), fault.getMessage()),
                () -> assertFalse(Files.exists(log.store()), "the refused append adds nothing"));
    }

    @Test
    @DisplayName("a set not named by its first question, a line of another event, or no line is refused")
    void wrongShape() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-001"), asked("Q-001", "Q-001"))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> write(t -> log.answer(t, List.of(delivered("Q-001", 1))))),
                () -> assertThrows(IllegalArgumentException.class, () -> write(t -> log.append(t, List.of()))),
                () -> assertFalse(Files.exists(log.store())));
    }

    @Test
    @DisplayName("an append through a transaction of another scope is refused")
    void otherScope() throws Exception {
        var other = JournalScope.plan(base, PROJECT, "other-plan");
        Files.createDirectories(other.journalFile().getParent());
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(other.lockKey());
            var write = ScopeTransaction.open(transaction, other, audit, CLOCK);
            var questions = List.of(asked("Q-001", "Q-001"));

            assertThrows(IllegalArgumentException.class, () -> log.ask(write, questions));
        }
    }
}
