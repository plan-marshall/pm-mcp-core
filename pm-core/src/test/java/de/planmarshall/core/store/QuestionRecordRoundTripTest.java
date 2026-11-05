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

import static de.planmarshall.core.store.QuestionRecordCodecTest.CHOSEN_JSON;
import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.answeredMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.askedMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.document;
import static de.planmarshall.core.store.QuestionRecordCodecTest.json;
import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.QuestionRecord.AnswerValue;

/**
 * The round trip of a question line (PM-WF-12, PM-IMPL-2): every event read back equal and written again to the same
 * bytes, the order of the schema, and a number answer's scale. The generated texts keep spaces, punctuation, quotes
 * and backslashes, so the round trip covers their escapes; the number texts are the contract of
 * {@link BigDecimal#toString()}.
 */
@EnableGeneratorController
@DisplayName("Question record round trip")
class QuestionRecordRoundTripTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("de.planmarshall.core.store.QuestionRecordCodecTest#everyEvent")
    @DisplayName("every event is read back equal, and written again to the same bytes")
    void everyEventReadBackEqual(String name, QuestionRecord question) {
        var written = QuestionRecordCodec.write(question);

        var read = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, written);

        assertAll(
                () -> assertEquals(question, read, "the line should be read back equal"),
                () -> assertArrayEquals(written, QuestionRecordCodec.write(read),
                        "the line should be written to the same bytes"));
    }

    @Test
    @DisplayName("an asked line is written in the order of the schema, its envelope verbatim")
    void askedShape() {
        assertArrayEquals(document(askedMembers()),
                QuestionRecordCodec.write(asked("Q-001", "Q-001")), "the asked line should follow the schema");
    }

    @Test
    @DisplayName("an answered line writes the device as null for a channel other than web")
    void answeredShape() {
        assertArrayEquals(document(answeredMembers()),
                QuestionRecordCodec.write(answered("Q-001", 1)), "the answered line should write no device");
    }

    @Test
    @DisplayName("a question of the server writes its asking role and its phase as null")
    void serverQuestion() {
        var members = askedMembers();
        members.put("origin", "\"server\"");
        members.put("asking_role", "null");
        members.put("phase", "null");
        members.remove("plan_task_id");

        var question = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, document(members));

        assertArrayEquals(document(members), QuestionRecordCodec.write(question),
                "the server question should round-trip unchanged");
    }

    @Test
    @DisplayName("a text answer with quotes, spaces and a backslash is written escaped and read back verbatim")
    void textAnswer() {
        var value = text(Generators.integers(1, 6).next());
        var members = answeredMembers();
        members.put("answer", CHOSEN_JSON.replace("\"value\":null", "\"value\":" + json(value)));

        var question = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, document(members));

        assertAll(
                () -> assertEquals(new AnswerValue.Text(value), question.answer().value(),
                        "the text value should be read verbatim"),
                () -> assertArrayEquals(document(members), QuestionRecordCodec.write(question),
                        "the text value should be written escaped"));
    }

    @Test
    @DisplayName("a number answer keeps its scale, and is written in the text form of BigDecimal.toString"
            + " (the number texts are the contract)")
    void numberScale() {
        var members = answeredMembers();
        members.put("answer", CHOSEN_JSON.replace("\"value\":null", "\"value\":2.50"));
        var small = answeredMembers();
        small.put("answer", CHOSEN_JSON.replace("\"value\":null", "\"value\":0.0000001"));
        var smallWritten = answeredMembers();
        smallWritten.put("answer", CHOSEN_JSON.replace("\"value\":null", "\"value\":1E-7"));

        var question = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, document(members));
        var smallQuestion = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, document(small));

        assertAll(
                () -> assertEquals(new AnswerValue.Numeric(new BigDecimal("2.50")), question.answer().value(),
                        "the number should keep its scale of two"),
                () -> assertArrayEquals(document(members), QuestionRecordCodec.write(question),
                        "the number should be written with its scale"),
                () -> assertEquals(new AnswerValue.Numeric(new BigDecimal("0.0000001")),
                        smallQuestion.answer().value(), "the small number should be read exactly"),
                () -> assertArrayEquals(document(smallWritten), QuestionRecordCodec.write(smallQuestion),
                        "the small number should be written as 1E-7"),
                () -> assertEquals(smallQuestion, QuestionRecordCodec.read(QuestionRecordCodecTest.FILE,
                        QuestionRecordCodec.write(smallQuestion)), "the small number should round-trip equal"));
    }
}
