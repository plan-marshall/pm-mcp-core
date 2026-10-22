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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.QuestionRecord.AnswerValue;

/**
 * The round trip of a question line (PM-WF-12, PM-IMPL-2): every event read back equal and written again to the same
 * bytes, the order of the schema, and a number answer's scale.
 */
@DisplayName("Question record round trip")
class QuestionRecordRoundTripTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("de.planmarshall.core.store.QuestionRecordCodecTest#everyEvent")
    @DisplayName("every event is read back equal, and written again to the same bytes")
    void everyEventReadBackEqual(String name, QuestionRecord question) {
        var written = QuestionRecordCodec.write(question);

        var read = QuestionRecordCodec.read(QuestionRecordCodecTest.FILE, written);

        assertAll(
                () -> assertEquals(question, read),
                () -> assertArrayEquals(written, QuestionRecordCodec.write(read)));
    }

    @Test
    @DisplayName("an asked line is written in the order of the schema, its envelope verbatim")
    void askedShape() {
        assertArrayEquals(document(askedMembers()),
                QuestionRecordCodec.write(asked("Q-001", "Q-001")));
    }

    @Test
    @DisplayName("an answered line writes the device as null for a channel other than web")
    void answeredShape() {
        assertArrayEquals(document(answeredMembers()),
                QuestionRecordCodec.write(answered("Q-001", 1)));
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

        assertArrayEquals(document(members), QuestionRecordCodec.write(question));
    }

    @Test
    @DisplayName("a number answer keeps its scale, and is written in the text form of BigDecimal.toString")
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
                () -> assertEquals(new AnswerValue.Numeric(new BigDecimal("2.50")), question.answer().value()),
                () -> assertArrayEquals(document(members), QuestionRecordCodec.write(question)),
                () -> assertEquals(new AnswerValue.Numeric(new BigDecimal("0.0000001")),
                        smallQuestion.answer().value()),
                () -> assertArrayEquals(document(smallWritten), QuestionRecordCodec.write(smallQuestion)),
                () -> assertEquals(smallQuestion, QuestionRecordCodec.read(QuestionRecordCodecTest.FILE,
                        QuestionRecordCodec.write(smallQuestion))));
    }
}
