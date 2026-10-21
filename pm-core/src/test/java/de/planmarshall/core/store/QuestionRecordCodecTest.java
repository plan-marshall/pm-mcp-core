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
 * {@link QuestionRecordMembersOnTheirEventsTest}, which share the fixtures of this class.
 */
@DisplayName("Question record codec")
class QuestionRecordCodecTest {

    static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/questions.jsonl");
    static final Instant AT = Instant.parse("2026-10-17T09:00:00Z");

    /** A word in a member name that names a deadline or an expiry, found anywhere in the name. */
    private static final Pattern TIMED_NAME = Pattern.compile("deadline|expir|timeout|ttl|due");

    /** The envelope of a part, kept as the exact text of one object; the space shows it is written back verbatim. */
    static final String ENVELOPE = "{\"part\": 1,\"options\":[\"keep\",\"drop\"]}";

    static final String DECISION_JSON = "{\"title\":\"Keep the cache\",\"header\":\"The build cache outlives"
            + " the plan\",\"description\":\"\",\"evidence\":[{\"kind\":\"file\",\"ref\":\"pm-core/pom.xml\"}],"
            + "\"blocks\":\"plan\",\"reversibility\":\"reversible\"}";

    static final String CHOSEN_JSON = "{\"kind\":\"chosen\",\"branch_keys\":[\"keep\"],\"value\":null,"
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
    static Map<String, String> askedMembers() {
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
    static Map<String, String> answeredMembers() {
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

    static Map<String, String> deliveredMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"delivered\"");
        members.put("question_id", "\"Q-001\"");
        members.put("revision", "1");
        members.put("at", "\"2026-10-17T09:00:00Z\"");
        return members;
    }

    static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    static StoreRefusal assertRefused(Map<String, String> members) {
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

    @Test
    @DisplayName("a decision carries at most 50 pieces of evidence: 50 are kept, a list of 51 is refused")
    void evidenceCount() {
        var fifty = IntStream.rangeClosed(1, 50)
                .mapToObj(index -> new Evidence(EvidenceKind.FILE, "f" + index, null)).toList();
        var fiftyOne = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new Evidence(EvidenceKind.FILE, "f" + index, null)).toList();

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new Decision("t", "h", "", fiftyOne, Blocks.PLAN, Reversibility.REVERSIBLE));

        assertAll(
                () -> assertEquals("A decision has at most 50 pieces of evidence, not 51", thrown.getMessage()),
                () -> assertEquals(50, new Decision("t", "h", "", fifty, Blocks.PLAN, Reversibility.REVERSIBLE)
                        .evidence().size()));
    }

    @Test
    @DisplayName("no member of the schema names a deadline or an expiry, and no event closes a question by time")
    void noDeadline() {
        var timed = QuestionRecordCodec.DECLARED_FIELDS.stream()
                .filter(field -> TIMED_NAME.matcher(field.toLowerCase(Locale.ROOT)).find())
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
