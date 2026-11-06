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

import static de.planmarshall.core.store.QuestionRecordCodecTest.adjudicated;
import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.revised;
import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRecord.Answer;
import de.planmarshall.core.store.QuestionRecord.AnswerKind;
import de.planmarshall.core.store.QuestionRefusal.Conflict;

/**
 * The revision an answer, an adjudication and a delivery name (PM-WF-12, PM-IMPL-2): the current one of their
 * question, another one refused as a {@link Conflict} carrying the current revision. The question ids of the set, the
 * revision counters that pin the order and the refusal code are the contract; the answer texts are generated.
 */
@EnableGeneratorController
@DisplayName("Question log: the revision of an answer")
class QuestionLogAnswerRevisionTest extends QuestionLogFixture {

    @Test
    @DisplayName("of an older revision is refused conflict, carrying the current revision")
    void olderRevision() throws Exception {
        askSet();
        write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2), revised("Q-003", 2))));
        var before = content();

        var refusal = write(t -> log.answer(t, List.of(answered("Q-001", 1))));

        assertAll(
                () -> assertEquals(Optional.of(new Conflict("Q-001", 2)), refusal,
                        "the answer should conflict with revision 2"),
                () -> assertEquals("conflict", refusal.orElseThrow().code(),
                        "the refusal should carry the code conflict"),
                () -> assertArrayEquals(before, content(), "a refused answer should append nothing"),
                () -> assertFalse(log.read().isClosed("Q-001"), "the question should stay open"));
    }

    @Test
    @DisplayName("of a revision not yet made, an adjudication or a delivery of an old one are refused conflict")
    void otherRevisions() {
        askSet();
        write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2), revised("Q-003", 2))));
        var future = Generators.integers(3, 100).next();

        assertAll(
                () -> assertEquals(Optional.of(new Conflict("Q-001", 2)),
                        write(t -> log.answer(t, List.of(answered("Q-001", future)))),
                        "an answer to a future revision should conflict"),
                () -> assertEquals(Optional.of(new Conflict("Q-002", 2)),
                        write(t -> log.adjudicate(t, List.of(adjudicated("Q-002", 1)))),
                        "an old adjudication should conflict"),
                () -> assertEquals(Optional.of(new Conflict("Q-003", 2)),
                        write(t -> log.deliver(t, List.of(delivered("Q-003", 1)))),
                        "an old delivery should conflict"));
    }

    @Test
    @DisplayName("of the current revision is appended with its adjudication in one append")
    void adjudicatedAndAnswered() {
        askSet();
        var answer = answered("Q-001", 1).toBuilder().answer(new Answer(AnswerKind.CHOSEN,
                List.of(QuestionRecordCodecTest.BRANCH_KEY), null, null, text(Generators.integers(1, 6).next()), null,
                false)).build();

        var refusal = write(t -> log.append(t, List.of(adjudicated("Q-001", 1), answer)));

        assertAll(
                () -> assertEquals(Optional.empty(), refusal, "the log should accept the append"),
                () -> assertEquals(Optional.of(answer), log.read().current("Q-001"),
                        "the answer with its note should be current"));
    }

    @Test
    @DisplayName("a conflict names a revision from 1")
    void conflictRevision() {
        // 0 is the highest refused revision, so it is the boundary of the contract and stays a literal
        var revision = Generators.integers(Integer.MIN_VALUE, -1).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Conflict("Q-001", 0),
                        "the boundary revision 0 should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Conflict("Q-001", revision),
                        "a revision below 0 should be refused"));
    }
}
