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
import static de.planmarshall.core.store.QuestionRecordCodecTest.DECISION_JSON;
import static de.planmarshall.core.store.QuestionRecordCodecTest.askedMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.assertRefused;
import static de.planmarshall.core.store.QuestionRecordCodecTest.deliveredMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.document;
import static de.planmarshall.core.store.QuestionRecordCodecTest.id;
import static de.planmarshall.core.store.QuestionRecordCodecTest.json;
import static de.planmarshall.core.store.QuestionRecordCodecTest.lower;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which members belong to which event of the question line (PM-WF-12, PM-IMPL-2): a member of another event is
 * refused on the event it is written on, and the members the record requires and forbids on an asked line. The event,
 * class and origin wire names and the member table are the contract of the schema; the other values are generated.
 */
@EnableGeneratorController
@DisplayName("Question record members on their events")
class QuestionRecordMembersOnTheirEventsTest {

    private static final Path FILE = QuestionRecordCodecTest.FILE;

    @ParameterizedTest(name = "{0} on a delivered line")
    @CsvSource(delimiter = '|', textBlock = """
            class         | "task"
            origin        | "model"
            source_entity | "TASK-003"
            asking_role   | "implementer"
            phase         | "5-execute"
            question_set  | "Q-001"
            plan_task_id  | "TASK-003"
            envelope      | {"part":1}
            decision_id   | "D-0001"
            channel       | "cli"
            device_id     | "dev-0123456789ab"
            amendment_id  | "A-1"
            reason        | "superseded"
            """)
    @DisplayName("a member of another event is refused on a delivered line, naming it (the table is the contract)")
    void foreignMemberOnDelivered(String member, String value) {
        var members = deliveredMembers();
        members.put(member, value);

        assertTrue(assertRefused(members).getMessage().contains(member),
                "the refusal should name the foreign member");
    }

    @Test
    @DisplayName("a decision and an answer are refused on a delivered line")
    void decisionAndAnswerOnDelivered() {
        var decision = deliveredMembers();
        decision.put("decision", DECISION_JSON);
        var answer = deliveredMembers();
        answer.put("answer", CHOSEN_JSON);

        assertAll(
                () -> assertTrue(assertRefused(decision).getMessage().contains("decision"),
                        "the refusal should name the decision member"),
                () -> assertTrue(assertRefused(answer).getMessage().contains("answer"),
                        "the refusal should name the answer member"));
    }

    @Test
    @DisplayName("a revision is refused on a closing line of the server")
    void revisionOnClosingLine() {
        var members = deliveredMembers();
        members.put("event", "\"cancelled\"");
        members.put("reason", json(lower(3, 20)));

        assertTrue(assertRefused(members).getMessage().contains("revision"),
                "the refusal should name the revision member");
    }

    @Test
    @DisplayName("only a question of the class task names its plan task")
    void planTaskOnlyForTask() {
        var members = askedMembers();
        members.put("class", "\"clarification\"");

        assertTrue(assertRefused(members).getMessage().contains("plan task"),
                "the refusal should name the plan task");
    }

    @Test
    @DisplayName("only a question a task created names its asking role")
    void askingRoleOnlyForModel() {
        var server = askedMembers();
        server.put("origin", "\"server\"");
        var model = askedMembers();
        model.put("asking_role", "null");

        assertAll(
                () -> assertTrue(assertRefused(server).getMessage().contains("asking role"),
                        "a server question should name no asking role"),
                () -> assertTrue(assertRefused(model).getMessage().contains("asking role"),
                        "a model question should name its asking role"));
    }

    @Test
    @DisplayName("only the first question of the set carries the decision, and it always does")
    void decisionOnlyOnFirstQuestion() {
        var part = askedMembers();
        part.put("question_id", json(id("Q-%03d", Generators.integers(2, 999).next())));
        var first = askedMembers();
        first.remove("decision");

        assertAll(
                () -> assertRefused(part),
                () -> assertRefused(first));
    }

    @Test
    @DisplayName("a withdrawn line has its reason member, which may be null but not of another form")
    void withdrawnReason() {
        var withoutReason = deliveredMembers();
        withoutReason.put("event", "\"withdrawn\"");
        withoutReason.remove("revision");
        var upperCase = new LinkedHashMap<>(withoutReason);
        upperCase.put("reason", json(Generators.letterStrings(1, 1).next().toUpperCase(Locale.ROOT) + lower(2, 20)));
        var asNull = new LinkedHashMap<>(withoutReason);
        asNull.put("reason", "null");

        assertAll(
                () -> assertTrue(assertRefused(withoutReason).getMessage().contains("reason"),
                        "the refusal should name the reason member"),
                () -> assertRefused(upperCase),
                () -> assertNull(QuestionRecordCodec.read(FILE, document(asNull)).reason(),
                        "a null reason should be read as none"));
    }
}
