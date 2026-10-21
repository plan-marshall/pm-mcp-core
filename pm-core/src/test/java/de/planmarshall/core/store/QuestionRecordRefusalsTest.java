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
import static de.planmarshall.core.store.QuestionRecordCodecTest.answeredMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.askedMembers;
import static de.planmarshall.core.store.QuestionRecordCodecTest.assertRefused;
import static de.planmarshall.core.store.QuestionRecordCodecTest.document;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The refusals of the question-line codec (PM-WF-12, PM-IMPL-2): every value outside the closed schema refused as a
 * {@link StoreRefusal} naming the file, each member of another event refused on the event it is written on, and no
 * member that names a deadline or an expiry.
 */
@DisplayName("Question record codec refusals")
class QuestionRecordRefusalsTest {

    private static final Path FILE = QuestionRecordCodecTest.FILE;

    @ParameterizedTest(name = "format_version {0}")
    @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
    @DisplayName("a record of another format version is refused as such, with the version found")
    void otherVersion(int version, Outcome outcome) {
        var members = askedMembers();
        members.put("format_version", Integer.toString(version));
        var document = document(members);

        var refusal = assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, document));

        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(OptionalInt.of(version), refusal.found()));
    }

    @ParameterizedTest(name = "asked without {0}")
    @ValueSource(strings = {"event", "question_id", "at", "class", "origin", "source_entity", "asking_role",
            "phase", "question_set", "revision", "envelope"})
    @DisplayName("an asked line without a member it requires is refused naming it")
    void askedWithoutMember(String member) {
        var members = askedMembers();
        members.remove(member);

        assertTrue(assertRefused(members).getMessage().contains(member));
    }

    @ParameterizedTest(name = "answered without {0}")
    @ValueSource(strings = {"revision", "decision_id", "answer", "channel", "device_id"})
    @DisplayName("an answered line without a member it requires is refused naming it")
    void answeredWithoutMember(String member) {
        var members = answeredMembers();
        members.remove(member);

        assertTrue(assertRefused(members).getMessage().contains(member));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            unknown event                  | event          | "expired"
            question id without Q-         | question_id    | "001"
            question id of two digits      | question_id    | "Q-01"
            question id of seven digits    | question_id    | "Q-1234567"
            unknown class                  | class          | "other"
            unknown origin                 | origin         | "operator"
            empty source entity            | source_entity  | ""
            asking role in upper case      | asking_role    | "Implementer"
            empty phase                    | phase          | ""
            question set no question id    | question_set   | "S-001"
            plan task of two digits        | plan_task_id   | "TASK-03"
            revision 0                     | revision       | 0
            asked at revision 2            | revision       | 2
            revision as string             | revision       | "1"
            envelope as array              | envelope       | [1]
            envelope as string             | envelope       | "{}"
            decision without its blocks    | decision       | {"title":"t","header":"h","description":"","evidence":[],"reversibility":"costly"}
            evidence of unknown kind       | decision       | {"title":"t","header":"h","description":"","evidence":[{"kind":"commit","ref":"x"}],"blocks":"task","reversibility":"costly"}
            instant with an offset         | at             | "2026-10-17T11:00:00+02:00"
            version as string              | format_version | "1"
            """)
    @DisplayName("a member of an asked line outside its one shape is refused")
    void askedMemberShape(String name, String member, String value) {
        var members = askedMembers();
        members.put(member, value);

        assertRefused(members);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            unknown channel                | channel      | "mail"
            web without its device         | channel      | "web"
            device on the terminal client  | device_id    | "dev-0123456789ab"
            decision id of two digits      | decision_id  | "D-12"
            decision id as null            | decision_id  | null
            amendment id without A-        | amendment_id | "B-1"
            unknown answer kind            | answer       | {"kind":"maybe","branch_keys":[],"value":null,"own_words":null,"note":null,"interpretation":null,"after_chat":false}
            branch key twice               | answer       | {"kind":"chosen","branch_keys":["keep","keep"],"value":null,"own_words":null,"note":null,"interpretation":null,"after_chat":false}
            branch key in upper case       | answer       | {"kind":"chosen","branch_keys":["Keep"],"value":null,"own_words":null,"note":null,"interpretation":null,"after_chat":false}
            value as boolean               | answer       | {"kind":"chosen","branch_keys":[],"value":true,"own_words":null,"note":null,"interpretation":null,"after_chat":false}
            answer without after_chat      | answer       | {"kind":"chosen","branch_keys":[],"value":null,"own_words":null,"note":null,"interpretation":null}
            interpreted without its result | answer       | {"kind":"interpreted","branch_keys":["keep"],"value":null,"own_words":"keep","note":null,"interpretation":null,"after_chat":false}
            chosen with an interpretation  | answer       | {"kind":"chosen","branch_keys":["keep"],"value":null,"own_words":null,"note":null,"interpretation":{"result":"option","condition":null,"passages":["keep"],"queue_task_id":"qt-0123456789abcdef"},"after_chat":false}
            queue task of the wrong form   | answer       | {"kind":"delegated","branch_keys":[],"value":null,"own_words":null,"note":null,"interpretation":{"result":"delegation","condition":null,"passages":[],"queue_task_id":"qt-0123"},"after_chat":false}
            """)
    @DisplayName("a member of an answered line outside its one shape is refused")
    void answeredMemberShape(String name, String member, String value) {
        var members = answeredMembers();
        members.put(member, value);

        assertRefused(members);
    }

    @Test
    @DisplayName("a decision title of 120 characters is read, one of 121 refused")
    void titleLength() {
        var longest = askedMembers();
        longest.put("decision", DECISION_JSON.replace("Keep the cache", "t".repeat(120)));
        var tooLong = askedMembers();
        tooLong.put("decision", DECISION_JSON.replace("Keep the cache", "t".repeat(121)));

        assertAll(
                () -> assertEquals(120, QuestionRecordCodec.read(FILE, document(longest)).decision().title()
                        .length()),
                () -> assertRefused(tooLong));
    }

    @ParameterizedTest(name = "{0} supplementary characters, accepted: {1}")
    @CsvSource({"61, true", "120, true", "121, false"})
    @DisplayName("a decision title is counted in characters, as the schema counts them, not in UTF-16 units")
    void titleLengthInCodePoints(int characters, boolean accepted) {
        var title = WaiverRecordCodecTest.SUPPLEMENTARY.repeat(characters);
        var members = askedMembers();
        members.put("decision", DECISION_JSON.replace("Keep the cache", title));

        if (accepted) {
            assertEquals(title, QuestionRecordCodec.read(FILE, document(members)).decision().title());
        } else {
            assertRefused(members);
        }
    }

    @Test
    @DisplayName("an undeclared member, a deadline or an expiry among them, is refused naming it")
    void undeclaredMember() {
        var deadline = askedMembers();
        deadline.put("deadline", "\"2026-10-18T09:00:00Z\"");
        var expiry = answeredMembers();
        expiry.put("expires_at", "\"2026-10-18T09:00:00Z\"");
        var inAnswer = answeredMembers();
        inAnswer.put("answer", CHOSEN_JSON.replace("}", ",\"timeout\":5}"));

        assertAll(
                () -> assertTrue(assertRefused(deadline).getMessage().contains("deadline")),
                () -> assertTrue(assertRefused(expiry).getMessage().contains("expires_at")),
                () -> assertTrue(assertRefused(inAnswer).getMessage().contains("timeout")));
    }

    @ParameterizedTest(name = "{index}")
    @ValueSource(strings = {"{\"a\":[\n{\"event\":\"cancelled\"}\n]}", "{\"a\":[\r{\"event\":\"cancelled\"}\r]}",
            "{\n}", "{\"a\":1}\n"})
    @DisplayName("an envelope with a line break is refused, so it cannot split the line of the log")
    void envelopeWithLineBreak(String envelope) {
        var question = asked("Q-001", "Q-001").toBuilder().envelope(envelope);

        assertThrows(IllegalArgumentException.class, question::build);
    }

    @Test
    @DisplayName("a member twice and content after the record are refused")
    void duplicateAndTrailing() {
        var once = new String(document(answeredMembers()), UTF_8);
        var twice = once.replace("\"channel\":\"cli\",", "\"channel\":\"cli\",\"channel\":\"cli\",")
                .getBytes(UTF_8);
        var trailing = (once + once).getBytes(UTF_8);

        assertAll(
                () -> assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, twice)),
                () -> assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, trailing)));
    }
}
