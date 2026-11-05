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
import static de.planmarshall.core.store.QuestionRecordCodecTest.revised;
import static de.planmarshall.core.store.QuestionRecordCodecTest.withdrawn;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;
import de.planmarshall.core.store.QuestionRefusal.Conflict;
import de.planmarshall.core.store.QuestionRefusal.Unreadable;

/**
 * A log that holds a refused line (PM-WF-12, PM-IMPL-2): the state of the questions is unknown, so every append is
 * refused {@code Unreadable} and nothing is added, an {@code asked} line of a new question included. The refused line,
 * the question ids of the set and the refusal codes are the contract; a new question and the texts are generated.
 */
@EnableGeneratorController
@DisplayName("Question log: a log with a refused line")
class QuestionLogRefusedLineTest extends QuestionLogFixture {

    /** The closing line of Q-001 in a newer format version: refused when read, so its question looks open. */
    private static final String NEWER_ANSWER =
            "{\"format_version\":2,\"event\":\"answered\",\"question_id\":\"Q-001\",\"revision\":1}\n";

    private void appendRefusedAnswer() throws IOException {
        Files.write(log.store(), NEWER_ANSWER.getBytes(UTF_8), StandardOpenOption.APPEND);
        assertEquals(List.of(4L), log.read().refused().stream().map(JsonlStoreFile.Refused::line).toList(),
                "the newer answer should be refused as line 4");
    }

    @Test
    @DisplayName("refuses a second closing line unreadable, and nothing is appended")
    void secondClosingLine() throws Exception {
        askSet();
        appendRefusedAnswer();
        var before = content();

        var answer = write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var withdrawal = write(t -> log.withdraw(t, List.of(withdrawn("Q-001"))));

        assertAll(
                () -> assertEquals(Optional.of(new Unreadable("Q-001", 4)), answer,
                        "the answer should be refused unreadable"),
                () -> assertEquals("unreadable", answer.orElseThrow().code(),
                        "the refusal should carry the code unreadable"),
                () -> assertEquals(Optional.of(new Unreadable("Q-001", 4)), withdrawal,
                        "the withdrawal should be refused unreadable"),
                () -> assertArrayEquals(before, content(), "the refused lines should append nothing"));
    }

    @Test
    @DisplayName("refuses a revision unreadable, and nothing is appended")
    void revision() throws Exception {
        askSet();
        appendRefusedAnswer();
        var before = content();

        var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                revised("Q-003", 2))));

        assertAll(
                () -> assertEquals(Optional.of(new Unreadable("Q-001", 4)), refusal,
                        "the revision should be refused unreadable"),
                () -> assertArrayEquals(before, content(), "the refused revision should append nothing"));
    }

    @Test
    @DisplayName("refuses an asked line too, as no refused line can be told apart from one about its question")
    void askedLine() throws Exception {
        askSet();
        appendRefusedAnswer();
        var before = content();
        var question = "Q-%03d".formatted(Generators.integers(4, 999).next());

        var refusal = write(t -> log.ask(t, List.of(asked(question, question))));

        assertAll(
                () -> assertEquals(Optional.of(new Unreadable(question, 4)), refusal,
                        "the new question should be refused unreadable"),
                () -> assertArrayEquals(before, content(), "the refused question should append nothing"));
    }

    @Test
    @DisplayName("without it, a second closing line and an old revision are refused as before")
    void control() throws Exception {
        askSet();
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        write(t -> log.revise(t, List.of(revised("Q-002", 2), revised("Q-003", 2))));
        var before = content();

        var again = write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var old = write(t -> log.answer(t, List.of(answered("Q-002", 1))));

        assertAll(
                () -> assertEquals(List.of(), log.read().refused(), "the log should hold no refused line"),
                () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), again,
                        "a second answer should be already answered"),
                () -> assertEquals(Optional.of(new Conflict("Q-002", 2)), old,
                        "an old revision should be a conflict"),
                () -> assertArrayEquals(before, content(), "the refused answers should append nothing"));
    }

    @Test
    @DisplayName("is named by its line number from 1")
    void refusedLineNumber() {
        // 0 is the highest refused line number, so it is the boundary of the contract and stays a literal
        var line = Generators.longs(Long.MIN_VALUE, -1).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Unreadable("Q-001", 0),
                        "the boundary line number 0 should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Unreadable("Q-001", line),
                        "a line number below 0 should be refused"));
    }
}
