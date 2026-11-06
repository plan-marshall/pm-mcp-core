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
import static de.planmarshall.core.store.QuestionRecordCodecTest.cancelled;
import static de.planmarshall.core.store.QuestionRecordCodecTest.withdrawn;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;

/**
 * The closing lines of a question (PM-WF-12, PM-IMPL-2): exactly one {@code answered}, {@code withdrawn} or
 * {@code cancelled} line per question, a second one refused with nothing appended. The question ids of the set and the
 * error code {@code already_answered} are the contract; the reasons of the closing lines are generated.
 */
@EnableGeneratorController
@DisplayName("Question log: a closing line")
class QuestionLogClosingTest extends QuestionLogFixture {

    @Test
    @DisplayName("a second one is refused already_answered, and nothing is appended")
    void secondClosingLine() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var before = content();

        var again = write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var reason = Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        var withdrawal = write(t -> log.withdraw(t, List.of(withdrawn("Q-001").toBuilder().reason(reason).build())));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), again,
                        "a second answer should be refused"),
                () -> assertEquals(AlreadyAnswered.ERROR_CODE, again.orElseThrow().code(),
                        "the refusal should carry its error code"),
                () -> assertEquals("already_answered", AlreadyAnswered.ERROR_CODE,
                        "the error code should be already_answered"),
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), withdrawal,
                        "a withdrawal after the answer should be refused"),
                () -> assertArrayEquals(before, content(), "the refused lines should append nothing"));
    }

    @Test
    @DisplayName("two of them in one append are refused, and neither is appended")
    void twoInOneAppend() throws Exception {
        askSet();
        var before = content();

        var refusal = write(t -> log.append(t, List.of(answered("Q-002", 1), withdrawn("Q-002"))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal,
                        "two closing lines should be refused"),
                () -> assertArrayEquals(before, content(), "the refused append should add nothing"));
    }

    @Test
    @DisplayName("an answer after cancelled is refused, and nothing is appended")
    void answerAfterCancelled() throws Exception {
        askSet();
        write(t -> log.cancel(t, List.of(cancelled("Q-001"), cancelled("Q-002"), cancelled("Q-003"))));
        var before = content();

        var refusal = write(t -> log.answer(t, List.of(answered("Q-001", 1))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), refusal,
                        "an answer after cancel should be refused"),
                () -> assertArrayEquals(before, content(), "the refused answer should append nothing"),
                () -> assertTrue(log.read().isClosed("Q-001"), "the cancelled question should be closed"));
    }
}
