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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;

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
 * The question record's own guards and the values of its schema (PM-WF-12, PM-IMPL-2): the decision's limit on the
 * evidence it carries, no member that names a deadline or an expiry, and every event and channel found by its wire
 * name. The round trip, the codec's refusals and the members of each event are tested in
 * {@link QuestionRecordRoundTripTest}, {@link QuestionRecordRefusalsTest} and
 * {@link QuestionRecordMembersOnTheirEventsTest}, which share the fixtures of this class. The member names, the event,
 * class, origin, channel and kind wire names, the envelope and the question ids of the set are wire contracts; the
 * texts, the other ids and the instant are generated within the grammar of the record.
 */
@EnableGeneratorController
@DisplayName("Question record codec")
class QuestionRecordCodecTest {

    // Fixture path: the file a refusal names.
    static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/questions.jsonl");
    static final Instant AT = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());

    /** A word in a member name that names a deadline or an expiry, found anywhere in the name. */
    private static final Pattern TIMED_NAME = Pattern.compile("deadline|expir|timeout|ttl|due");

    /** The envelope of a part, kept as the exact text of one object; the space shows it is written back verbatim. */
    static final String ENVELOPE = "{\"part\": 1,\"options\":[\"keep\",\"drop\"]}";

    /** The decision title, with punctuation, a quote and a backslash, so the round trip keeps its escapes. */
    static final String TITLE = text(3);
    static final String HEADER = text(5);
    static final String EVIDENCE_REF = lower(3, 12) + "/" + lower(3, 12) + ".xml";
    static final String SOURCE_ENTITY = id("TASK-%03d", Generators.integers(0, 999).next());
    static final String PLAN_TASK_ID = id("TASK-%04d", Generators.integers(0, 9999).next());
    static final String ASKING_ROLE = lower(2, 32);
    static final String PHASE = Generators.integers(1, 6).next() + "-" + lower(3, 12);
    static final String ADJUDICATION_ID = id("D-%04d", Generators.integers(0, 9999).next());
    static final String ANSWER_DECISION_ID = id("D-%05d", Generators.integers(0, 99_999).next());
    static final String BRANCH_KEY = lower(2, 32);
    static final String WITHDRAW_REASON = lower(3, 20);
    static final String CANCEL_REASON = lower(3, 20) + "_" + lower(3, 20);

    static final String DECISION_JSON = decisionJson(json(TITLE));

    static final String CHOSEN_JSON = "{\"kind\":\"chosen\",\"branch_keys\":[" + json(BRANCH_KEY) + "],\"value\":null,"
            + "\"own_words\":null,\"note\":null,\"interpretation\":null,\"after_chat\":false}";

    /** @return the number formatted into the identifier pattern with ASCII digits, whatever the host's locale */
    static String id(String pattern, int number) {
        return String.format(Locale.ROOT, pattern, number);
    }

    /** @return lower-case letters within the given lengths, the form of a role, a branch key and a reason */
    static String lower(int min, int max) {
        return Generators.letterStrings(min, max).next().toLowerCase(Locale.ROOT);
    }

    /** @return generated words with spaces, punctuation, a quoted word and a backslash */
    static String text(int words) {
        var text = IntStream.range(0, words).mapToObj(index -> Generators.letterStrings(1, 8).next())
                .collect(Collectors.joining(", "));
        return text + " \"" + Generators.letterStrings(1, 8).next() + "\" \\ " + Generators.letterStrings(1, 8).next()
                + "?";
    }

    /** @return the text as a JSON string, its quotes and backslashes escaped */
    static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** @return the decision as it is written, with the given JSON value as its title */
    static String decisionJson(String title) {
        return "{\"title\":" + title + ",\"header\":" + json(HEADER) + ",\"description\":\"\",\"evidence\":[{\"kind\":"
                + "\"file\",\"ref\":" + json(EVIDENCE_REF) + "}],\"blocks\":\"plan\",\"reversibility\":\"reversible\"}";
    }

    static Decision decision() {
        return new Decision(TITLE, HEADER, "", List.of(new Evidence(EvidenceKind.FILE, EVIDENCE_REF, null)),
                Blocks.PLAN, Reversibility.REVERSIBLE);
    }

    /** @return the asked line of a question of the set, asked by a plan task; the decision on the first question */
    static QuestionRecord asked(String questionId, String questionSet) {
        return QuestionRecord.builder().event(QuestionEvent.ASKED).questionId(questionId)
                .questionClass(QuestionClass.TASK).origin(Origin.MODEL).sourceEntity(SOURCE_ENTITY)
                .askingRole(ASKING_ROLE).phase(PHASE).questionSet(questionSet).planTaskId(PLAN_TASK_ID)
                .revision(1).decision(questionId.equals(questionSet) ? decision() : null).envelope(ENVELOPE).at(AT)
                .build();
    }

    static QuestionRecord revised(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.REVISED).questionId(questionId).revision(revision)
                .envelope(ENVELOPE).at(AT).build();
    }

    static QuestionRecord adjudicated(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.ADJUDICATED).questionId(questionId).revision(revision)
                .decisionId(ADJUDICATION_ID).at(AT).build();
    }

    static QuestionRecord delivered(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.DELIVERED).questionId(questionId).revision(revision)
                .at(AT).build();
    }

    /** @return an answer from the terminal client that took the option {@link #BRANCH_KEY} */
    static QuestionRecord answered(String questionId, int revision) {
        return QuestionRecord.builder().event(QuestionEvent.ANSWERED).questionId(questionId).revision(revision)
                .decisionId(ANSWER_DECISION_ID).answer(new Answer(AnswerKind.CHOSEN, List.of(BRANCH_KEY), null, null,
                null, null, false))
                .channel(QuestionChannel.CLI).at(AT).build();
    }

    static QuestionRecord withdrawn(String questionId) {
        return QuestionRecord.builder().event(QuestionEvent.WITHDRAWN).questionId(questionId).reason(WITHDRAW_REASON)
                .at(AT).build();
    }

    static QuestionRecord cancelled(String questionId) {
        return QuestionRecord.builder().event(QuestionEvent.CANCELLED).questionId(questionId).reason(CANCEL_REASON)
                .at(AT).build();
    }

    /** @return the members of the asked line of {@code Q-001}, each with its JSON value, in the order of the schema */
    static Map<String, String> askedMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"asked\"");
        members.put("question_id", "\"Q-001\"");
        members.put("class", "\"task\"");
        members.put("origin", "\"model\"");
        members.put("source_entity", json(SOURCE_ENTITY));
        members.put("asking_role", json(ASKING_ROLE));
        members.put("phase", json(PHASE));
        members.put("question_set", "\"Q-001\"");
        members.put("plan_task_id", json(PLAN_TASK_ID));
        members.put("revision", "1");
        members.put("decision", DECISION_JSON);
        members.put("envelope", ENVELOPE);
        members.put("at", json(AT.toString()));
        return members;
    }

    /** @return the members of the answered line of {@code Q-001} at revision 1, in the order of the schema */
    static Map<String, String> answeredMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"answered\"");
        members.put("question_id", "\"Q-001\"");
        members.put("revision", "1");
        members.put("decision_id", json(ANSWER_DECISION_ID));
        members.put("answer", CHOSEN_JSON);
        members.put("channel", "\"cli\"");
        members.put("device_id", "null");
        members.put("at", json(AT.toString()));
        return members;
    }

    static Map<String, String> deliveredMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"delivered\"");
        members.put("question_id", "\"Q-001\"");
        members.put("revision", "1");
        members.put("at", json(AT.toString()));
        return members;
    }

    static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    static StoreRefusal assertRefused(Map<String, String> members) {
        var document = document(members);
        var refusal = assertThrows(StoreRefusal.class, () -> QuestionRecordCodec.read(FILE, document),
                "the codec should refuse the question line");
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal should be unreadable"),
                () -> assertEquals(FILE, refusal.file(), "the refusal should name the file of the line"),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()), "the message names the file"));
        return refusal;
    }

    static Stream<Arguments> everyEvent() {
        var interpreted = new Answer(AnswerKind.INTERPRETED, List.of(BRANCH_KEY), new AnswerValue.Numeric(
                        BigDecimal.valueOf(Generators.integers(0, 100_000).next(), 2)), text(4), text(2),
                new Interpretation(InterpretationResult.OPTION_WITH_CONDITION, text(3),
                        List.of(Generators.letterStrings(1, 40).next()),
                        "qt-%016x".formatted(Generators.longs(0, Long.MAX_VALUE).next())),
                true);
        return Stream.of(
                Arguments.of("asked, first question of the set", asked("Q-001", "Q-001")),
                Arguments.of("asked, a further part of the set", asked("Q-002", "Q-001")),
                Arguments.of("asked by the server, outside a plan", asked("Q-001", "Q-001").toBuilder()
                        .questionClass(QuestionClass.SETUP).origin(Origin.SERVER).sourceEntity(lower(3, 20))
                        .askingRole(null).phase(null).planTaskId(null).build()),
                Arguments.of("revised, with the decision", revised("Q-001", 2).toBuilder().decision(decision())
                        .build()),
                Arguments.of("adjudicated", adjudicated("Q-001", 1)),
                Arguments.of("delivered", delivered("Q-001", 3)),
                Arguments.of("answered from the terminal client", answered("Q-001", 1)),
                Arguments.of("answered from the web, creating an amendment", answered("Q-001", 1).toBuilder()
                        .channel(QuestionChannel.WEB)
                        .deviceId("dev-%012x".formatted(Generators.longs(0, 0xFFFF_FFFF_FFFFL).next()))
                        .amendmentId("A-" + Generators.integers(1, 9999).next()).build()),
                Arguments.of("answered by interpretation, with a number", answered("Q-001", 1).toBuilder()
                        .answer(interpreted).build()),
                Arguments.of("answered with a text by the quorum", answered("Q-001", 1).toBuilder()
                        .channel(QuestionChannel.QUORUM).answer(new Answer(AnswerKind.CHOSEN, List.of(),
                        new AnswerValue.Text(text(2)), null, null, null, false))
                        .build()),
                Arguments.of("withdrawn without a reason", withdrawn("Q-001").toBuilder().reason(null).build()),
                Arguments.of("cancelled", cancelled("Q-001")));
    }

    @Test
    @DisplayName("a decision carries at most 50 pieces of evidence: 50 are kept, a list of 51 is refused"
            + " (the message is the contract)")
    void evidenceCount() {
        var fifty = IntStream.rangeClosed(1, 50)
                .mapToObj(index -> new Evidence(EvidenceKind.FILE, lower(1, 20), null)).toList();
        var fiftyOne = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new Evidence(EvidenceKind.FILE, lower(1, 20), null)).toList();
        var title = lower(1, 120);
        var header = lower(1, 300);

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new Decision(title, header, "", fiftyOne, Blocks.PLAN, Reversibility.REVERSIBLE),
                "a decision with 51 pieces should be refused");

        assertAll(
                () -> assertEquals("A decision has at most 50 pieces of evidence, not 51", thrown.getMessage(),
                        "the refusal should name the limit and count"),
                () -> assertEquals(50, new Decision(title, header, "", fifty, Blocks.PLAN, Reversibility.REVERSIBLE)
                        .evidence().size(), "a decision should keep its 50 pieces"));
    }

    @Test
    @DisplayName("no member of the schema names a deadline or an expiry, and no event closes a question by time")
    void noDeadline() {
        var timed = QuestionRecordCodec.DECLARED_FIELDS.stream()
                .filter(field -> TIMED_NAME.matcher(field.toLowerCase(Locale.ROOT)).find())
                .toList();

        assertAll(
                () -> assertEquals(List.of(), timed, "no declared member should name a deadline"),
                () -> assertTrue(QuestionRecordCodec.DECLARED_FIELDS.containsAll(QuestionRecordCodec.REQUIRED_BY_EVENT
                                .values().stream().flatMap(List::stream).toList()),
                        "every required member should be declared"),
                () -> assertEquals(EnumSet.of(QuestionEvent.ANSWERED, QuestionEvent.WITHDRAWN,
                        QuestionEvent.CANCELLED), EnumSet.copyOf(Stream.of(QuestionEvent.values())
                        .filter(QuestionEvent::closes).toList()), "only the three closing events should close"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QuestionEvent.class)
    @DisplayName("every event and channel is found by its wire name, and an unknown name by none (wire names)")
    void wireNames(QuestionEvent event) {
        assertAll(
                () -> assertEquals(event, QuestionEvent.fromWireName(event.wireName()).orElseThrow(),
                        "the event should be found by its wire name"),
                () -> assertTrue(QuestionEvent.fromWireName("expired").isEmpty(),
                        "no event should be named expired"),
                () -> assertEquals(QuestionChannel.QUORUM, QuestionChannel.fromWireName("quorum").orElseThrow(),
                        "the quorum channel should be found by name"),
                () -> assertTrue(QuestionChannel.fromWireName("mail").isEmpty(),
                        "no channel should be named mail"),
                () -> assertEquals(AnswerKind.OWN_OPTION, QuestionRecord.fromWireName(AnswerKind.class, "own_option")
                        .orElseThrow(), "the own option kind should be found by name"),
                () -> assertTrue(QuestionRecord.fromWireName(Origin.class, "MODEL").isEmpty(),
                        "a constant name is no wire name of an origin"));
    }
}
