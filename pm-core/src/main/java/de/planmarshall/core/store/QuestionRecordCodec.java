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

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

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
import de.planmarshall.core.store.QuestionRecord.WireName;

/**
 * Reads and writes one line of a question log (PM-WF-12, PM-IMPL-2): a {@link QuestionRecord} as the JSON object of one
 * line of {@code questions.jsonl}.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the {@code format_version} first, then every field
 * at most once, only the declared fields, one JSON type per field, the members each event requires present, and the
 * value rules of the record. A member the schema allows to be {@code null} may be written as {@code null}. The
 * envelope is read as the exact JSON text of its object. Whatever does not parse is a {@link StoreRefusal} naming the
 * file; a reader of the log adds the line ({@link QuestionLog}).
 * <p>
 * The writer writes every field in the order of the schema: the members the event requires always, a {@code null} as
 * {@code null}, and every other member only when the record holds it. The envelope is written back verbatim, so the
 * same record always gives the same bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class QuestionRecordCodec {

    private static final String RECORD = "question record";

    /** The message of a refused value: what was read, and why its record refused it. */
    private static final String REFUSED_VALUE = "%s: %s";

    private static final String FIELD_EVENT = "event";
    private static final String FIELD_QUESTION_ID = "question_id";
    private static final String FIELD_CLASS = "class";
    private static final String FIELD_ORIGIN = "origin";
    private static final String FIELD_SOURCE_ENTITY = "source_entity";
    private static final String FIELD_ASKING_ROLE = "asking_role";
    private static final String FIELD_PHASE = "phase";
    private static final String FIELD_QUESTION_SET = "question_set";
    private static final String FIELD_PLAN_TASK_ID = "plan_task_id";
    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_DECISION = "decision";
    private static final String FIELD_ENVELOPE = "envelope";
    private static final String FIELD_DECISION_ID = "decision_id";
    private static final String FIELD_ANSWER = "answer";
    private static final String FIELD_CHANNEL = "channel";
    private static final String FIELD_DEVICE_ID = "device_id";
    private static final String FIELD_AMENDMENT_ID = "amendment_id";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_AT = "at";

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_HEADER = "header";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_EVIDENCE = "evidence";
    private static final String FIELD_BLOCKS = "blocks";
    private static final String FIELD_REVERSIBILITY = "reversibility";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_LABEL = "label";
    private static final String FIELD_BRANCH_KEYS = "branch_keys";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_OWN_WORDS = "own_words";
    private static final String FIELD_NOTE = "note";
    private static final String FIELD_INTERPRETATION = "interpretation";
    private static final String FIELD_AFTER_CHAT = "after_chat";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_CONDITION = "condition";
    private static final String FIELD_PASSAGES = "passages";
    private static final String FIELD_QUEUE_TASK_ID = "queue_task_id";

    /** The members every line requires, beside the ones its event requires; {@code format_version} is checked first. */
    static final List<String> REQUIRED_BY_RECORD = List.of(FIELD_EVENT, FIELD_QUESTION_ID, FIELD_AT);

    /** The members each event requires besides the ones every line has; a member listed here may be {@code null}. */
    static final Map<QuestionEvent, List<String>> REQUIRED_BY_EVENT = Map.of(
            QuestionEvent.ASKED, List.of(FIELD_CLASS, FIELD_ORIGIN, FIELD_SOURCE_ENTITY, FIELD_ASKING_ROLE, FIELD_PHASE,
                    FIELD_QUESTION_SET, FIELD_ENVELOPE, FIELD_REVISION),
            QuestionEvent.REVISED, List.of(FIELD_ENVELOPE, FIELD_REVISION),
            QuestionEvent.ADJUDICATED, List.of(FIELD_DECISION_ID, FIELD_REVISION),
            QuestionEvent.DELIVERED, List.of(FIELD_REVISION),
            QuestionEvent.ANSWERED, List.of(FIELD_REVISION, FIELD_ANSWER, FIELD_CHANNEL, FIELD_DEVICE_ID,
                    FIELD_DECISION_ID),
            QuestionEvent.WITHDRAWN, List.of(FIELD_REASON),
            QuestionEvent.CANCELLED, List.of(FIELD_REASON));

    /** The members the schema declares for a line, in its order. */
    static final List<String> DECLARED_FIELDS = List.of(FIELD_FORMAT_VERSION, FIELD_EVENT, FIELD_QUESTION_ID,
            FIELD_CLASS, FIELD_ORIGIN, FIELD_SOURCE_ENTITY, FIELD_ASKING_ROLE, FIELD_PHASE, FIELD_QUESTION_SET,
            FIELD_PLAN_TASK_ID, FIELD_REVISION, FIELD_DECISION, FIELD_ENVELOPE, FIELD_DECISION_ID, FIELD_ANSWER,
            FIELD_CHANNEL, FIELD_DEVICE_ID, FIELD_AMENDMENT_ID, FIELD_REASON, FIELD_AT);

    /**
     * @param question a question record
     * @return the record as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(QuestionRecord question) {
        Objects.requireNonNull(question, "question");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            write(generator, question);
        } catch (IOException e) {
            throw new UncheckedIOException("Question record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the record was read from, named by a refusal
     * @param document one record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the record declares another format version or does not have the one shape of a question
     *                      record
     */
    public static QuestionRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, QuestionRecord.FORMAT_VERSION, parser -> readObject(parser, document));
    }

    private static void write(JsonGenerator generator, QuestionRecord question) throws IOException {
        var required = Set.copyOf(REQUIRED_BY_EVENT.get(question.event()));
        generator.writeStartObject();
        generator.writeNumberField(FIELD_FORMAT_VERSION, QuestionRecord.FORMAT_VERSION);
        generator.writeStringField(FIELD_EVENT, question.event().wireName());
        generator.writeStringField(FIELD_QUESTION_ID, question.questionId());
        writeText(generator, required, FIELD_CLASS, wire(question.questionClass()));
        writeText(generator, required, FIELD_ORIGIN, wire(question.origin()));
        writeText(generator, required, FIELD_SOURCE_ENTITY, question.sourceEntity());
        writeText(generator, required, FIELD_ASKING_ROLE, question.askingRole());
        writeText(generator, required, FIELD_PHASE, question.phase());
        writeText(generator, required, FIELD_QUESTION_SET, question.questionSet());
        writeText(generator, required, FIELD_PLAN_TASK_ID, question.planTaskId());
        if (question.revision() != null) {
            generator.writeNumberField(FIELD_REVISION, question.revision());
        }
        if (question.decision() != null) {
            writeDecision(generator, question.decision());
        }
        if (question.envelope() != null) {
            generator.writeFieldName(FIELD_ENVELOPE);
            generator.writeRawValue(question.envelope());
        }
        writeText(generator, required, FIELD_DECISION_ID, question.decisionId());
        if (question.answer() != null) {
            writeAnswer(generator, question.answer());
        }
        writeText(generator, required, FIELD_CHANNEL,
                question.channel() == null ? null : question.channel().wireName());
        writeText(generator, required, FIELD_DEVICE_ID, question.deviceId());
        writeText(generator, required, FIELD_AMENDMENT_ID, question.amendmentId());
        writeText(generator, required, FIELD_REASON, question.reason());
        writeInstant(generator, FIELD_AT, question.at());
        generator.writeEndObject();
    }

    private static void writeDecision(JsonGenerator generator, Decision decision) throws IOException {
        generator.writeObjectFieldStart(FIELD_DECISION);
        generator.writeStringField(FIELD_TITLE, decision.title());
        generator.writeStringField(FIELD_HEADER, decision.header());
        generator.writeStringField(FIELD_DESCRIPTION, decision.description());
        generator.writeArrayFieldStart(FIELD_EVIDENCE);
        for (var evidence : decision.evidence()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_KIND, evidence.kind().wireName());
            generator.writeStringField(FIELD_REF, evidence.ref());
            if (evidence.label() != null) {
                generator.writeStringField(FIELD_LABEL, evidence.label());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeStringField(FIELD_BLOCKS, decision.blocks().wireName());
        generator.writeStringField(FIELD_REVERSIBILITY, decision.reversibility().wireName());
        generator.writeEndObject();
    }

    private static void writeAnswer(JsonGenerator generator, Answer answer) throws IOException {
        generator.writeObjectFieldStart(FIELD_ANSWER);
        generator.writeStringField(FIELD_KIND, answer.kind().wireName());
        generator.writeArrayFieldStart(FIELD_BRANCH_KEYS);
        for (var key : answer.branchKeys()) {
            generator.writeString(key);
        }
        generator.writeEndArray();
        generator.writeFieldName(FIELD_VALUE);
        switch (answer.value()) {
            case null -> generator.writeNull();
            case AnswerValue.Text(var text) -> generator.writeString(text);
            case AnswerValue.Numeric(var number) -> generator.writeNumber(number);
        }
        writeNullable(generator, FIELD_OWN_WORDS, answer.ownWords());
        writeNullable(generator, FIELD_NOTE, answer.note());
        var interpretation = answer.interpretation();
        if (interpretation == null) {
            generator.writeNullField(FIELD_INTERPRETATION);
        } else {
            generator.writeObjectFieldStart(FIELD_INTERPRETATION);
            generator.writeStringField(FIELD_RESULT, interpretation.result().wireName());
            writeNullable(generator, FIELD_CONDITION, interpretation.condition());
            generator.writeArrayFieldStart(FIELD_PASSAGES);
            for (var passage : interpretation.passages()) {
                generator.writeString(passage);
            }
            generator.writeEndArray();
            generator.writeStringField(FIELD_QUEUE_TASK_ID, interpretation.queueTaskId());
            generator.writeEndObject();
        }
        generator.writeBooleanField(FIELD_AFTER_CHAT, answer.afterChat());
        generator.writeEndObject();
    }

    /** Writes a text member: always when the event requires it, a {@code null} as {@code null}; else only if held. */
    private static void writeText(JsonGenerator generator, Set<String> required, String field, String value)
            throws IOException {
        if (value != null) {
            generator.writeStringField(field, value);
        } else if (required.contains(field)) {
            generator.writeNullField(field);
        }
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static String wire(WireName value) {
        return value == null ? null : value.wireName();
    }

    private static QuestionRecord readObject(JsonParser parser, byte[] document) throws IOException {
        requireObject(parser, RECORD);
        var question = QuestionRecord.builder();
        var seen = new HashSet<String>();
        @Nullable QuestionEvent event = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_EVENT -> {
                    event = WaiverRecordCodec.enumValue(string(parser, field), QuestionEvent::fromWireName, field);
                    question.event(event);
                }
                case FIELD_QUESTION_ID -> question.questionId(string(parser, field));
                case FIELD_CLASS -> question.questionClass(closed(parser, field, QuestionClass.class));
                case FIELD_ORIGIN -> question.origin(closed(parser, field, Origin.class));
                case FIELD_SOURCE_ENTITY -> question.sourceEntity(string(parser, field));
                case FIELD_ASKING_ROLE -> question.askingRole(nullableString(parser, field));
                case FIELD_PHASE -> question.phase(nullableString(parser, field));
                case FIELD_QUESTION_SET -> question.questionSet(string(parser, field));
                case FIELD_PLAN_TASK_ID -> question.planTaskId(nullableString(parser, field));
                case FIELD_REVISION -> question.revision(revision(parser, field));
                case FIELD_DECISION -> question.decision(readDecision(parser));
                case FIELD_ENVELOPE -> {
                    requireObject(parser, field);
                    question.envelope(WorkflowStatusCodec.raw(parser, document));
                }
                case FIELD_DECISION_ID -> question.decisionId(string(parser, field));
                case FIELD_ANSWER -> question.answer(readAnswer(parser));
                case FIELD_CHANNEL -> question.channel(
                        WaiverRecordCodec.enumValue(string(parser, field), QuestionChannel::fromWireName, field));
                case FIELD_DEVICE_ID -> question.deviceId(nullableString(parser, field));
                case FIELD_AMENDMENT_ID -> question.amendmentId(nullableString(parser, field));
                case FIELD_REASON -> question.reason(nullableString(parser, field));
                case FIELD_AT -> question.at(instant(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RECORD, FIELD_FORMAT_VERSION);
        requireFields(seen, RECORD, REQUIRED_BY_RECORD.toArray(String[]::new));
        requireFields(seen, "%s line".formatted(Objects.requireNonNull(event, FIELD_EVENT).wireName()),
                REQUIRED_BY_EVENT.get(event).toArray(String[]::new));
        try {
            return question.build();
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(RECORD, e.getMessage()), e);
        }
    }

    private static Decision readDecision(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_DECISION);
        @Nullable String title = null;
        @Nullable String header = null;
        @Nullable String description = null;
        @Nullable List<Evidence> evidence = null;
        @Nullable Blocks blocks = null;
        @Nullable Reversibility reversibility = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_TITLE -> title = string(parser, field);
                case FIELD_HEADER -> header = string(parser, field);
                case FIELD_DESCRIPTION -> description = string(parser, field);
                case FIELD_EVIDENCE -> evidence = StrictJson.array(parser, field, QuestionRecordCodec::readEvidence);
                case FIELD_BLOCKS -> blocks = closed(parser, field, Blocks.class);
                case FIELD_REVERSIBILITY -> reversibility = closed(parser, field, Reversibility.class);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_DECISION, FIELD_TITLE, FIELD_HEADER, FIELD_DESCRIPTION, FIELD_EVIDENCE,
                FIELD_BLOCKS, FIELD_REVERSIBILITY);
        try {
            return new Decision(title, header, description, evidence, blocks, reversibility);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_DECISION, e.getMessage()), e);
        }
    }

    private static Evidence readEvidence(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable EvidenceKind kind = null;
        @Nullable String ref = null;
        @Nullable String label = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = closed(parser, field, EvidenceKind.class);
                case FIELD_REF -> ref = string(parser, field);
                case FIELD_LABEL -> label = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_KIND, FIELD_REF);
        try {
            return new Evidence(kind, ref, label);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(name, e.getMessage()), e);
        }
    }

    private static Answer readAnswer(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_ANSWER);
        @Nullable AnswerKind kind = null;
        @Nullable List<String> branchKeys = null;
        @Nullable AnswerValue value = null;
        @Nullable String ownWords = null;
        @Nullable String note = null;
        @Nullable Interpretation interpretation = null;
        var afterChat = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = closed(parser, field, AnswerKind.class);
                case FIELD_BRANCH_KEYS -> branchKeys = stringList(parser, field);
                case FIELD_VALUE -> value = readValue(parser);
                case FIELD_OWN_WORDS -> ownWords = nullableString(parser, field);
                case FIELD_NOTE -> note = nullableString(parser, field);
                case FIELD_INTERPRETATION -> interpretation = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readInterpretation(parser);
                case FIELD_AFTER_CHAT -> afterChat = bool(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_ANSWER, FIELD_KIND, FIELD_BRANCH_KEYS, FIELD_VALUE, FIELD_OWN_WORDS, FIELD_NOTE,
                FIELD_INTERPRETATION, FIELD_AFTER_CHAT);
        try {
            return new Answer(kind, branchKeys, value, ownWords, note, interpretation, afterChat);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_ANSWER, e.getMessage()), e);
        }
    }

    private static AnswerValue readValue(JsonParser parser) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> new AnswerValue.Text(parser.getText());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new AnswerValue.Numeric(parser.getDecimalValue());
            case null, default -> throw unreadable("'%s' must be a string, a number or null".formatted(FIELD_VALUE));
        };
    }

    private static Interpretation readInterpretation(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_INTERPRETATION);
        @Nullable InterpretationResult result = null;
        @Nullable String condition = null;
        @Nullable List<String> passages = null;
        @Nullable String queueTaskId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_RESULT -> result = closed(parser, field, InterpretationResult.class);
                case FIELD_CONDITION -> condition = nullableString(parser, field);
                case FIELD_PASSAGES -> passages = stringList(parser, field);
                case FIELD_QUEUE_TASK_ID -> queueTaskId = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_INTERPRETATION, FIELD_RESULT, FIELD_CONDITION, FIELD_PASSAGES, FIELD_QUEUE_TASK_ID);
        try {
            return new Interpretation(result, condition, passages, queueTaskId);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_INTERPRETATION, e.getMessage()), e);
        }
    }

    private static Integer revision(JsonParser parser, String field) throws IOException {
        var value = integer(parser, field);
        if (value < 1 || value > Integer.MAX_VALUE) {
            throw unreadable("'%s' counts from 1: %d".formatted(field, value));
        }
        return (int) value;
    }

    private static <E extends Enum<E> & WireName> E closed(JsonParser parser, String field, Class<E> type)
            throws IOException {
        return WaiverRecordCodec.enumValue(string(parser, field), text -> QuestionRecord.fromWireName(type, text),
                field);
    }
}
