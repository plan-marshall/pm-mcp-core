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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.QuestionRecord.Answer;
import de.planmarshall.core.store.QuestionRecord.AnswerKind;
import de.planmarshall.core.store.QuestionRecord.AnswerValue;
import de.planmarshall.core.store.QuestionRecord.Blocks;
import de.planmarshall.core.store.QuestionRecord.Decision;
import de.planmarshall.core.store.QuestionRecord.Evidence;
import de.planmarshall.core.store.QuestionRecord.EvidenceKind;
import de.planmarshall.core.store.QuestionRecord.Interpretation;
import de.planmarshall.core.store.QuestionRecord.InterpretationResult;
import de.planmarshall.core.store.QuestionRecord.Origin;
import de.planmarshall.core.store.QuestionRecord.QuestionClass;
import de.planmarshall.core.store.QuestionRecord.Reversibility;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The line of the question log (PM-WF-12, PM-IMPL-2): written in the order of its schema, read back equal per event,
 * every value outside the closed schema of the question record refused as a {@link StoreRefusal} naming the file, each
 * member only on the events the schema names for it, and no member that names a deadline or an expiry.
 */
@DisplayName("Question record codec")
class QuestionRecordCodecTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/questions.jsonl");
    static final Instant AT = Instant.parse("2026-10-17T09:00:00Z");

    /** The envelope of a part, kept as the exact text of one object; the space shows it is written back verbatim. */
    static final String ENVELOPE = "{\"part\": 1,\"options\":[\"keep\",\"drop\"]}";

    private static final String DECISION_JSON = "{\"title\":\"Keep the cache\",\"header\":\"The build cache outlives"
            + " the plan\",\"description\":\"\",\"evidence\":[{\"kind\":\"file\",\"ref\":\"pm-core/pom.xml\"}],"
            + "\"blocks\":\"plan\",\"reversibility\":\"reversible\"}";

    private static final String CHOSEN_JSON = "{\"kind\":\"chosen\",\"branch_keys\":[\"keep\"],\"value\":null,"
            + "\"own_words\":null,\"note\":null,\"interpretation\":null,\"after_chat\":false}";

    static Decision decision() {
        return new Decision("Keep the cache", "The build cache outlives the plan", "",
                List.of(new Evidence(EvidenceKind.FILE, "pm-core/pom.xml", null)), Blocks.PLAN,
                Reversibility.REVERSIBLE);
    }

    /** @return the asked line of a question of the set, asked by a plan task; the decision on the first question */
    static QuestionRecord asked(String questionId, String questionSet) {
        return QuestionRecord.builder().event(QuestionEvent.ASKED).questionId(questionId)
                .questionClass(QuestionClass.TASK).origin(Origin.MODEL).sourceEntity("TASK-003")
                .askingRole("implementer").phase("5-execute").questionSet(questionSet).planTaskId("TASK-003")
                .revision(1).decision(questionId.equals(questionSet) ? decision() : null).envelope(ENVELOPE).at(AT)
                .build();
    }

    static QuestionRecord revised(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.REVISED).questionId(questionId).revision(revision)
                .envelope(ENVELOPE).at(AT).build();
    }

    static QuestionRecord adjudicated(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.ADJUDICATED).questionId(questionId).revision(revision)
                .decisionId("D-0001").at(AT).build();
    }

    static QuestionRecord delivered(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.DELIVERED).questionId(questionId).revision(revision)
                .at(AT).build();
    }

    /** @return an answer from the terminal client that took the option {@code keep} */
    static QuestionRecord answered(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.ANSWERED).questionId(questionId).revision(revision)
                .decisionId("D-0002").answer(new Answer(AnswerKind.CHOSEN, List.of("keep"), null, null, null, null,
                false))
                .channel(QuestionChannel.CLI).at(AT).build();
    }

    static QuestionRecord withdrawn(String questionId) {
        return QuestionRecord.builder().event(QuestionEvent.WITHDRAWN).questionId(questionId).reason("superseded")
                .at(AT).build();
    }

    static QuestionRecord cancelled(String questionId) {
        return QuestionRecord.builder().event(QuestionEvent.CANCELLED).questionId(questionId).reason("scope_ended")
                .at(AT).build();
    }

    /** @return the members of the asked line of {@code Q-001}, each with its JSON value, in the order of the schema */
    private static Map<String, String> askedMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"asked\"");
        members.put("question_id", "\"Q-001\"");
        members.put("class", "\"task\"");
        members.put("origin", "\"model\"");
        members.put("source_entity", "\"TASK-003\"");
        members.put("asking_role", "\"implementer\"");
        members.put("phase", "\"5-execute\"");
        members.put("question_set", "\"Q-001\"");
        members.put("plan_task_id", "\"TASK-003\"");
        members.put("revision", "1");
        members.put("decision", DECISION_JSON);
        members.put("envelope", ENVELOPE);
        members.put("at", "\"2026-10-17T09:00:00Z\"");
        return members;
    }

    /** @return the members of the answered line of {@code Q-001} at revision 1, in the order of the schema */
    private static Map<String, String> answeredMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"answered\"");
        members.put("question_id", "\"Q-001\"");
        members.put("revision", "1");
        members.put("decision_id", "\"D-0002\"");
        members.put("answer", CHOSEN_JSON);
        members.put("channel", "\"cli\"");
        members.put("device_id", "null");
        members.put("at", "\"2026-10-17T09:00:00Z\"");
        return members;
    }

    private static Map<String, String> deliveredMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"delivered\"");
        members.put("question_id", "\"Q-001\"");
        members.put("revision", "1");
        members.put("at", "\"2026-10-17T09:00:00Z\"");
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Map<String, String> members) {
        var document = document(members);
        var refusal = assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, document));
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()), "the message names the file"));
        return refusal;
    }

    static Stream<Arguments> everyEvent() {
        var interpreted = new Answer(AnswerKind.INTERPRETED, List.of("keep"), new AnswerValue.Numeric(
                        new BigDecimal("2.50")), "keep it, but only for this plan", "asked twice", new Interpretation(
                        InterpretationResult.OPTION_WITH_CONDITION, "only for this plan", List.of("only for this"),
                        "qt-0123456789abcdef"),
                true);
        return Stream.of(
                Arguments.of("asked, first question of the set", asked("Q-001", "Q-001")),
                Arguments.of("asked, a further part of the set", asked("Q-002", "Q-001")),
                Arguments.of("asked by the server, outside a plan", asked("Q-001", "Q-001").toBuilder()
                        .questionClass(QuestionClass.SETUP).origin(Origin.SERVER).sourceEntity("setup")
                        .askingRole(null).phase(null).planTaskId(null).build()),
                Arguments.of("revised, with the decision", revised("Q-001", 2).toBuilder().decision(decision())
                        .build()),
                Arguments.of("adjudicated", adjudicated("Q-001", 1)),
                Arguments.of("delivered", delivered("Q-001", 3)),
                Arguments.of("answered from the terminal client", answered("Q-001", 1)),
                Arguments.of("answered from the web, creating an amendment", answered("Q-001", 1).toBuilder()
                        .channel(QuestionChannel.WEB).deviceId("dev-0123456789ab").amendmentId("A-7").build()),
                Arguments.of("answered by interpretation, with a number", answered("Q-001", 1).toBuilder()
                        .answer(interpreted).build()),
                Arguments.of("answered with a text by the quorum", answered("Q-001", 1).toBuilder()
                        .channel(QuestionChannel.QUORUM).answer(new Answer(AnswerKind.CHOSEN, List.of(),
                        new AnswerValue.Text("v2"), null, null, null, false))
                        .build()),
                Arguments.of("withdrawn without a reason", withdrawn("Q-001").toBuilder().reason(null).build()),
                Arguments.of("cancelled", cancelled("Q-001")));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.QuestionRecordCodecTest#everyEvent")
        @DisplayName("every event is read back equal, and written again to the same bytes")
        void everyEventReadBackEqual(String name, QuestionRecord question) {
            var written = QuestionRecordCodec.write(question);

            var read = QuestionRecordCodec.read(FILE, written);

            assertAll(
                    () -> assertEquals(question, read),
                    () -> assertArrayEquals(written, QuestionRecordCodec.write(read)));
        }

        @Test
        @DisplayName("an asked line is written in the order of the schema, its envelope verbatim")
        void askedShape() {
            assertArrayEquals(document(askedMembers()), QuestionRecordCodec.write(asked("Q-001", "Q-001")));
        }

        @Test
        @DisplayName("an answered line writes the device as null for a channel other than web")
        void answeredShape() {
            assertArrayEquals(document(answeredMembers()), QuestionRecordCodec.write(answered("Q-001", 1)));
        }

        @Test
        @DisplayName("a question of the server writes its asking role and its phase as null")
        void serverQuestion() {
            var members = askedMembers();
            members.put("origin", "\"server\"");
            members.put("asking_role", "null");
            members.put("phase", "null");
            members.remove("plan_task_id");

            var question = QuestionRecordCodec.read(FILE, document(members));

            assertArrayEquals(document(members), QuestionRecordCodec.write(question));
        }

        @Test
        @DisplayName("a number answer keeps its scale")
        void numberScale() {
            var members = answeredMembers();
            members.put("answer", CHOSEN_JSON.replace("\"value\":null", "\"value\":2.50"));

            var question = QuestionRecordCodec.read(FILE, document(members));

            assertAll(
                    () -> assertEquals(new AnswerValue.Numeric(new BigDecimal("2.50")), question.answer().value()),
                    () -> assertArrayEquals(document(members), QuestionRecordCodec.write(question)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

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
            var twice = once.replace("\"channel\":\"cli\",", "\"channel\":\"cli\",\"channel\":\"cli\",");

            assertAll(
                    () -> assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, twice.getBytes(UTF_8))),
                    () -> assertThrows(StoreRefusal.class,
                            () -> QuestionRecordCodec.read(FILE, (once + once).getBytes(UTF_8))));
        }
    }

    @Nested
    @DisplayName("members on their events only")
    class MembersOnTheirEvents {

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
        @DisplayName("a member of another event is refused on a delivered line, naming it")
        void foreignMemberOnDelivered(String member, String value) {
            var members = deliveredMembers();
            members.put(member, value);

            assertTrue(assertRefused(members).getMessage().contains(member));
        }

        @Test
        @DisplayName("a decision and an answer are refused on a delivered line")
        void decisionAndAnswerOnDelivered() {
            var decision = deliveredMembers();
            decision.put("decision", DECISION_JSON);
            var answer = deliveredMembers();
            answer.put("answer", CHOSEN_JSON);

            assertAll(
                    () -> assertTrue(assertRefused(decision).getMessage().contains("decision")),
                    () -> assertTrue(assertRefused(answer).getMessage().contains("answer")));
        }

        @Test
        @DisplayName("a revision is refused on a closing line of the server")
        void revisionOnClosingLine() {
            var members = deliveredMembers();
            members.put("event", "\"cancelled\"");
            members.put("reason", "\"scope_ended\"");

            assertTrue(assertRefused(members).getMessage().contains("revision"));
        }

        @Test
        @DisplayName("only a question of the class task names its plan task")
        void planTaskOnlyForTask() {
            var members = askedMembers();
            members.put("class", "\"clarification\"");

            assertTrue(assertRefused(members).getMessage().contains("plan task"));
        }

        @Test
        @DisplayName("only a question a task created names its asking role")
        void askingRoleOnlyForModel() {
            var server = askedMembers();
            server.put("origin", "\"server\"");
            var model = askedMembers();
            model.put("asking_role", "null");

            assertAll(
                    () -> assertTrue(assertRefused(server).getMessage().contains("asking role")),
                    () -> assertTrue(assertRefused(model).getMessage().contains("asking role")));
        }

        @Test
        @DisplayName("only the first question of the set carries the decision, and it always does")
        void decisionOnlyOnFirstQuestion() {
            var part = askedMembers();
            part.put("question_id", "\"Q-002\"");
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
            upperCase.put("reason", "\"Superseded\"");
            var asNull = new LinkedHashMap<>(withoutReason);
            asNull.put("reason", "null");

            assertAll(
                    () -> assertTrue(assertRefused(withoutReason).getMessage().contains("reason")),
                    () -> assertRefused(upperCase),
                    () -> assertNull(QuestionRecordCodec.read(FILE, document(asNull)).reason()));
        }
    }

    @Test
    @DisplayName("no member of the schema names a deadline or an expiry, and no event closes a question by time")
    void noDeadline() {
        var timed = QuestionRecordCodec.DECLARED_FIELDS.stream()
                .filter(field -> field.toLowerCase(Locale.ROOT).matches(".*(deadline|expir|timeout|ttl|due).*"))
                .toList();

        assertAll(
                () -> assertEquals(List.of(), timed),
                () -> assertTrue(QuestionRecordCodec.DECLARED_FIELDS.containsAll(QuestionRecordCodec.REQUIRED_BY_EVENT
                        .values().stream().flatMap(List::stream).toList())),
                () -> assertEquals(EnumSet.of(QuestionEvent.ANSWERED, QuestionEvent.WITHDRAWN,
                        QuestionEvent.CANCELLED), EnumSet.copyOf(Stream.of(QuestionEvent.values())
                        .filter(QuestionEvent::closes).toList())));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QuestionEvent.class)
    @DisplayName("every event and channel is found by its wire name, and an unknown name by none")
    void wireNames(QuestionEvent event) {
        assertAll(
                () -> assertEquals(event, QuestionEvent.fromWireName(event.wireName()).orElseThrow()),
                () -> assertTrue(QuestionEvent.fromWireName("expired").isEmpty()),
                () -> assertEquals(QuestionChannel.QUORUM, QuestionChannel.fromWireName("quorum").orElseThrow()),
                () -> assertTrue(QuestionChannel.fromWireName("mail").isEmpty()),
                () -> assertEquals(AnswerKind.OWN_OPTION, QuestionRecord.fromWireName(AnswerKind.class, "own_option")
                        .orElseThrow()),
                () -> assertTrue(QuestionRecord.fromWireName(Origin.class, "MODEL").isEmpty()));
    }
}
