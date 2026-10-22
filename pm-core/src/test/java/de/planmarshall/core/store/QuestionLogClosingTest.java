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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;

/**
 * The closing lines of a question (PM-WF-12, PM-IMPL-2): exactly one {@code answered}, {@code withdrawn} or
 * {@code cancelled} line per question, a second one refused with nothing appended.
 */
@DisplayName("Question log: a closing line")
class QuestionLogClosingTest extends QuestionLogFixture {

    @Test
    @DisplayName("a second one is refused already_answered, and nothing is appended")
    void secondClosingLine() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var before = content();

        var again = write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var withdrawal = write(t -> log.withdraw(t, List.of(withdrawn("Q-001"))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), again),
                () -> assertEquals(AlreadyAnswered.ERROR_CODE, again.orElseThrow().code()),
                () -> assertEquals("already_answered", AlreadyAnswered.ERROR_CODE),
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), withdrawal),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("two of them in one append are refused, and neither is appended")
    void twoInOneAppend() throws Exception {
        askSet();
        var before = content();

        var refusal = write(t -> log.append(t, List.of(answered("Q-002", 1), withdrawn("Q-002"))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal),
                () -> assertArrayEquals(before, content()));
    }

    @Test
    @DisplayName("an answer after cancelled is refused, and nothing is appended")
    void answerAfterCancelled() throws Exception {
        askSet();
        write(t -> log.cancel(t, List.of(cancelled("Q-001"), cancelled("Q-002"), cancelled("Q-003"))));
        var before = content();

        var refusal = write(t -> log.answer(t, List.of(answered("Q-001", 1))));

        assertAll(
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), refusal),
                () -> assertArrayEquals(before, content()),
                () -> assertTrue(log.read().isClosed("Q-001")));
    }
}
