/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Consultation;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.Lease;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Signal;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes one queue task record (Store Schemas § Queue Task Record): the JSON object of
 * {@code queue/<queue_task_id>.json}.
 * <p>
 * The reader accepts exactly one shape through {@code StrictJson}: the {@code format_version} first, then every field
 * at most once, only the declared fields, one JSON type per field, the members the schema requires present, and the
 * value rules of the record. Whatever does not parse is a refusal naming the file.
 * <p>
 * The writer writes every field in the order of the schema, a {@code null} as JSON {@code null}, so the same record
 * always gives the same bytes and a read record is written back unchanged.
 *
 * @since 0.1
 */
@UtilityClass
public class QueueTaskRecordCodec {

    private static final String RECORD = "queue task record";
    private static final String REFUSED_VALUE = "%s: %s";

    private static final String FIELD_QUEUE_TASK_ID = "queue_task_id";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CLASS = "class";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_ENTITY = "entity";
    private static final String FIELD_STATE_ID = "state_id";
    private static final String FIELD_FORM_DOWNGRADED = "form_downgraded";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CONTINUATION = "continuation";
    private static final String FIELD_LEASE = "lease";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final String FIELD_SIGNALS = "signals";
    private static final String FIELD_CONSULTATIONS = "consultations";
    private static final String FIELD_CONSULTATION_OF = "consultation_of";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_QUESTION_ID = "question_id";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_CLOSED_AT = "closed_at";

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ID = "id";
    private static final String FIELD_FORM = "form";
    private static final String FIELD_HOLD_UNTIL = "hold_until";
    private static final String FIELD_SESSION_ID = "session_id";
    private static final String FIELD_SUSPENDED_AT = "suspended_at";
    private static final String FIELD_RESUME_UNTIL = "resume_until";
    private static final String FIELD_SUSPENDED_DIGEST = "suspended_digest";
    private static final String FIELD_SUSPENDED_HEAD = "suspended_head";
    private static final String FIELD_FALLBACK_REASON = "fallback_reason";
    private static final String FIELD_GENERATION_ID = "generation_id";
    private static final String FIELD_JOB_ID = "job_id";
    private static final String FIELD_LEASED_AT = "leased_at";
    private static final String FIELD_ACKNOWLEDGED_AT = "acknowledged_at";
    private static final String FIELD_ATTEMPT = "attempt";
    private static final String FIELD_CONFIGURATION_DIGEST = "configuration_digest";
    private static final String FIELD_OFFERED_AT = "offered_at";
    private static final String FIELD_ENDED_AT = "ended_at";
    private static final String FIELD_END = "end";
    private static final String FIELD_COUNTS = "counts";
    private static final String FIELD_SIGNAL = "signal";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_MEASUREMENT = "measurement";
    private static final String FIELD_THRESHOLD = "threshold";
    private static final String FIELD_AT = "at";
    private static final String FIELD_CONSULTATION_ID = "consultation_id";
    private static final String FIELD_ASKED_BY = "asked_by";
    private static final String FIELD_ASKED_AT = "asked_at";
    private static final String FIELD_ANSWER = "answer";
    private static final String FIELD_DELIVERED_AT = "delivered_at";
    private static final String FIELD_DELIVERED_TO = "delivered_to";

    /** The members of the record in the order of the schema. */
    static final List<String> FIELDS = List.of(FIELD_FORMAT_VERSION, FIELD_QUEUE_TASK_ID, FIELD_KIND, FIELD_ROLE,
            FIELD_CLASS, FIELD_SCOPE, FIELD_ENTITY, FIELD_STATE_ID, FIELD_FORM_DOWNGRADED, FIELD_STATUS,
            FIELD_CONTINUATION, FIELD_LEASE, FIELD_ATTEMPTS, FIELD_SIGNALS, FIELD_CONSULTATIONS,
            FIELD_CONSULTATION_OF, FIELD_OUTCOME, FIELD_REASON, FIELD_QUESTION_ID, FIELD_CREATED_AT, FIELD_CLOSED_AT);

    /** The members every record requires, in the order of the schema; {@code format_version} is checked first. */
    static final List<String> REQUIRED = List.of(FIELD_FORMAT_VERSION, FIELD_QUEUE_TASK_ID, FIELD_KIND, FIELD_ROLE,
            FIELD_CLASS, FIELD_SCOPE, FIELD_ENTITY, FIELD_STATE_ID, FIELD_FORM_DOWNGRADED, FIELD_STATUS, FIELD_LEASE,
            FIELD_CONTINUATION, FIELD_ATTEMPTS, FIELD_SIGNALS, FIELD_CONSULTATIONS, FIELD_CONSULTATION_OF,
            FIELD_OUTCOME, FIELD_REASON, FIELD_QUESTION_ID, FIELD_CREATED_AT, FIELD_CLOSED_AT);

    /**
     * @param task a queue task record
     * @return the record as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(QueueTaskRecord task) {
        Objects.requireNonNull(task, "task");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory()
                     .createGenerator(out, JsonEncoding.UTF8)) {
            write(generator, task);
        } catch (IOException e) {
            throw new UncheckedIOException("Queue task record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the record was read from, named by a refusal
     * @param document one record, UTF-8 encoded
     * @return the record
     * @throws de.planmarshall.core.store.StoreRefusal if the record declares another format version or does not have
     *                                                the one shape of a queue task record
     */
    public static QueueTaskRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, QueueTaskRecord.FORMAT_VERSION,
                QueueTaskRecordCodec::readObject);
    }

    private static void write(JsonGenerator generator, QueueTaskRecord task) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_FORMAT_VERSION, QueueTaskRecord.FORMAT_VERSION);
        generator.writeStringField(FIELD_QUEUE_TASK_ID, task.queueTaskId());
        generator.writeStringField(FIELD_KIND, task.kind().wireName());
        generator.writeStringField(FIELD_ROLE, task.role());
        writeNullable(generator, FIELD_CLASS, task.taskClass() == null ? null : task.taskClass().wireName());
        writeScope(generator, task.scope());
        generator.writeStringField(FIELD_ENTITY, task.entity());
        generator.writeStringField(FIELD_STATE_ID, task.stateId());
        generator.writeBooleanField(FIELD_FORM_DOWNGRADED, task.formDowngraded());
        generator.writeStringField(FIELD_STATUS, task.status().wireName());
        if (task.continuation() == null) {
            generator.writeNullField(FIELD_CONTINUATION);
        } else {
            writeContinuation(generator, task.continuation());
        }
        if (task.lease() == null) {
            generator.writeNullField(FIELD_LEASE);
        } else {
            writeLease(generator, task.lease());
        }
        writeAttempts(generator, task.attempts());
        writeSignals(generator, task.signals());
        writeConsultations(generator, task.consultations());
        writeNullable(generator, FIELD_CONSULTATION_OF, task.consultationOf());
        writeNullable(generator, FIELD_OUTCOME, task.outcome() == null ? null : task.outcome().wireName());
        writeNullable(generator, FIELD_REASON, task.reason() == null ? null : task.reason().wireName());
        writeNullable(generator, FIELD_QUESTION_ID, task.questionId());
        writeInstant(generator, FIELD_CREATED_AT, task.createdAt());
        writeInstant(generator, FIELD_CLOSED_AT, task.closedAt());
        generator.writeEndObject();
    }

    private static void writeScope(JsonGenerator generator, Scope scope) throws IOException {
        generator.writeObjectFieldStart(FIELD_SCOPE);
        generator.writeStringField(FIELD_TYPE, scope.type().wireName());
        writeNullable(generator, FIELD_ID, scope.id());
        generator.writeEndObject();
    }

    private static void writeContinuation(JsonGenerator generator, Continuation continuation) throws IOException {
        generator.writeObjectFieldStart(FIELD_CONTINUATION);
        generator.writeStringField(FIELD_QUESTION_ID, continuation.questionId());
        generator.writeStringField(FIELD_FORM, continuation.form().wireName());
        writeInstant(generator, FIELD_HOLD_UNTIL, continuation.holdUntil());
        writeNullable(generator, FIELD_SESSION_ID, continuation.sessionId());
        writeInstant(generator, FIELD_SUSPENDED_AT, continuation.suspendedAt());
        writeInstant(generator, FIELD_RESUME_UNTIL, continuation.resumeUntil());
        writeNullable(generator, FIELD_SUSPENDED_DIGEST, continuation.suspendedDigest());
        writeNullable(generator, FIELD_SUSPENDED_HEAD, continuation.suspendedHead());
        writeNullable(generator, FIELD_FALLBACK_REASON,
                continuation.fallbackReason() == null ? null : continuation.fallbackReason().wireName());
        generator.writeEndObject();
    }

    private static void writeLease(JsonGenerator generator, Lease lease) throws IOException {
        generator.writeObjectFieldStart(FIELD_LEASE);
        generator.writeStringField(FIELD_GENERATION_ID, lease.generationId());
        generator.writeStringField(FIELD_JOB_ID, lease.jobId());
        writeInstant(generator, FIELD_LEASED_AT, lease.leasedAt());
        writeInstant(generator, FIELD_ACKNOWLEDGED_AT, lease.acknowledgedAt());
        generator.writeEndObject();
    }

    private static void writeAttempts(JsonGenerator generator, List<Attempt> attempts) throws IOException {
        generator.writeArrayFieldStart(FIELD_ATTEMPTS);
        for (var attempt : attempts) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_ATTEMPT, attempt.attempt());
            generator.writeStringField(FIELD_GENERATION_ID, attempt.generationId());
            generator.writeStringField(FIELD_JOB_ID, attempt.jobId());
            generator.writeStringField(FIELD_CONFIGURATION_DIGEST, attempt.configurationDigest());
            writeInstant(generator, FIELD_OFFERED_AT, attempt.offeredAt());
            writeInstant(generator, FIELD_ACKNOWLEDGED_AT, attempt.acknowledgedAt());
            writeInstant(generator, FIELD_ENDED_AT, attempt.endedAt());
            writeNullable(generator, FIELD_END, attempt.end() == null ? null : attempt.end().wireName());
            generator.writeBooleanField(FIELD_COUNTS, attempt.counts());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeSignals(JsonGenerator generator, List<Signal> signals) throws IOException {
        generator.writeArrayFieldStart(FIELD_SIGNALS);
        for (var signal : signals) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_SIGNAL, signal.signal().wireName());
            generator.writeNumberField(FIELD_ATTEMPT, signal.attempt());
            generator.writeStringField(FIELD_ACTION, signal.action().wireName());
            generator.writeNumberField(FIELD_MEASUREMENT, signal.measurement());
            generator.writeNumberField(FIELD_THRESHOLD, signal.threshold());
            writeInstant(generator, FIELD_AT, signal.at());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeConsultations(JsonGenerator generator, List<Consultation> consultations)
            throws IOException {
        generator.writeArrayFieldStart(FIELD_CONSULTATIONS);
        for (var consultation : consultations) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_CONSULTATION_ID, consultation.consultationId());
            generator.writeStringField(FIELD_ROLE, consultation.role());
            generator.writeStringField(FIELD_ASKED_BY, consultation.askedBy());
            writeInstant(generator, FIELD_ASKED_AT, consultation.askedAt());
            generator.writeStringField(FIELD_STATUS, consultation.status().wireName());
            writeNullable(generator, FIELD_ANSWER, consultation.answer());
            writeInstant(generator, FIELD_CLOSED_AT, consultation.closedAt());
            writeInstant(generator, FIELD_DELIVERED_AT, consultation.deliveredAt());
            writeNullable(generator, FIELD_DELIVERED_TO,
                    consultation.deliveredTo() == null ? null : consultation.deliveredTo().wireName());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    @SuppressWarnings("java:S3776") // one case per schema field, in schema order, by design
    private static QueueTaskRecord readObject(JsonParser parser) throws IOException {
        requireObject(parser, RECORD);
        var builder = QueueTaskRecord.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_QUEUE_TASK_ID -> builder.queueTaskId(string(parser, field));
                case FIELD_KIND -> builder.kind(closed(parser, field, Kind::fromWireName));
                case FIELD_ROLE -> builder.role(string(parser, field));
                case FIELD_CLASS -> builder.taskClass(
                        parser.currentToken() == JsonToken.VALUE_NULL ? null : closed(parser, field,
                                TaskClass::fromWireName));
                case FIELD_SCOPE -> builder.scope(readScope(parser));
                case FIELD_ENTITY -> builder.entity(string(parser, field));
                case FIELD_STATE_ID -> builder.stateId(string(parser, field));
                case FIELD_FORM_DOWNGRADED -> builder.formDowngraded(bool(parser, field));
                case FIELD_STATUS -> builder.status(closed(parser, field, Status::fromWireName));
                case FIELD_CONTINUATION -> builder.continuation(parser.currentToken() == JsonToken.VALUE_NULL
                        ? null : readContinuation(parser));
                case FIELD_LEASE -> builder.lease(parser.currentToken() == JsonToken.VALUE_NULL
                        ? null : readLease(parser));
                case FIELD_ATTEMPTS -> builder.attempts(readArray(parser, field, QueueTaskRecordCodec::readAttempt));
                case FIELD_SIGNALS -> builder.signals(readArray(parser, field, QueueTaskRecordCodec::readSignal));
                case FIELD_CONSULTATIONS -> builder.consultations(
                        readArray(parser, field, QueueTaskRecordCodec::readConsultation));
                case FIELD_CONSULTATION_OF -> builder.consultationOf(nullableString(parser, field));
                case FIELD_OUTCOME -> builder.outcome(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, Outcome::fromWireName));
                case FIELD_REASON -> builder.reason(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, LostReason::fromWireName));
                case FIELD_QUESTION_ID -> builder.questionId(nullableString(parser, field));
                case FIELD_CREATED_AT -> builder.createdAt(instant(parser, field));
                case FIELD_CLOSED_AT -> builder.closedAt(nullableInstant(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RECORD, FIELD_FORMAT_VERSION);
        requireFields(seen, RECORD, REQUIRED.toArray(String[]::new));
        try {
            return validated(builder.build(), RECORD);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(RECORD, e.getMessage()), e);
        }
    }

    private static QueueTaskRecord validated(QueueTaskRecord task, String name) {
        try {
            task.validate();
            return task;
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(name, e.getMessage()), e);
        }
    }

    private static Scope readScope(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_SCOPE);
        @Nullable ScopeType type = null;
        @Nullable String id = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_TYPE -> type = closed(parser, field, ScopeType::fromWireName);
                case FIELD_ID -> id = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_SCOPE, FIELD_TYPE, FIELD_ID);
        try {
            return new Scope(type, id);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_SCOPE, e.getMessage()), e);
        }
    }

    private static Continuation readContinuation(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_CONTINUATION);
        @Nullable String questionId = null;
        @Nullable ContinuationForm form = null;
        @Nullable Instant holdUntil = null;
        @Nullable String sessionId = null;
        @Nullable Instant suspendedAt = null;
        @Nullable Instant resumeUntil = null;
        @Nullable String suspendedDigest = null;
        @Nullable String suspendedHead = null;
        @Nullable FallbackReason fallbackReason = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_QUESTION_ID -> questionId = string(parser, field);
                case FIELD_FORM -> form = closed(parser, field, ContinuationForm::fromWireName);
                case FIELD_HOLD_UNTIL -> holdUntil = nullableInstant(parser, field);
                case FIELD_SESSION_ID -> sessionId = nullableString(parser, field);
                case FIELD_SUSPENDED_AT -> suspendedAt = nullableInstant(parser, field);
                case FIELD_RESUME_UNTIL -> resumeUntil = nullableInstant(parser, field);
                case FIELD_SUSPENDED_DIGEST -> suspendedDigest = nullableString(parser, field);
                case FIELD_SUSPENDED_HEAD -> suspendedHead = nullableString(parser, field);
                case FIELD_FALLBACK_REASON -> fallbackReason = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, FallbackReason::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_CONTINUATION, FIELD_QUESTION_ID, FIELD_FORM, FIELD_HOLD_UNTIL, FIELD_SESSION_ID,
                FIELD_SUSPENDED_AT, FIELD_RESUME_UNTIL, FIELD_SUSPENDED_DIGEST, FIELD_SUSPENDED_HEAD,
                FIELD_FALLBACK_REASON);
        try {
            return new Continuation(questionId, form, holdUntil, sessionId, suspendedAt, resumeUntil,
                    suspendedDigest, suspendedHead, fallbackReason);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_CONTINUATION, e.getMessage()), e);
        }
    }

    private static Lease readLease(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_LEASE);
        @Nullable String generationId = null;
        @Nullable String jobId = null;
        @Nullable Instant leasedAt = null;
        @Nullable Instant acknowledgedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_GENERATION_ID -> generationId = string(parser, field);
                case FIELD_JOB_ID -> jobId = string(parser, field);
                case FIELD_LEASED_AT -> leasedAt = instant(parser, field);
                case FIELD_ACKNOWLEDGED_AT -> acknowledgedAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_LEASE, FIELD_GENERATION_ID, FIELD_JOB_ID, FIELD_LEASED_AT, FIELD_ACKNOWLEDGED_AT);
        try {
            return new Lease(generationId, jobId, leasedAt, acknowledgedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_LEASE, e.getMessage()), e);
        }
    }

    private static Attempt readAttempt(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var attempt = 0;
        @Nullable String generationId = null;
        @Nullable String jobId = null;
        @Nullable String configurationDigest = null;
        @Nullable Instant offeredAt = null;
        @Nullable Instant acknowledgedAt = null;
        @Nullable Instant endedAt = null;
        @Nullable AttemptEnd end = null;
        var counts = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ATTEMPT -> attempt = Math.toIntExact(integer(parser, field));
                case FIELD_GENERATION_ID -> generationId = string(parser, field);
                case FIELD_JOB_ID -> jobId = string(parser, field);
                case FIELD_CONFIGURATION_DIGEST -> configurationDigest = string(parser, field);
                case FIELD_OFFERED_AT -> offeredAt = instant(parser, field);
                case FIELD_ACKNOWLEDGED_AT -> acknowledgedAt = nullableInstant(parser, field);
                case FIELD_ENDED_AT -> endedAt = nullableInstant(parser, field);
                case FIELD_END -> end = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, AttemptEnd::fromWireName);
                case FIELD_COUNTS -> counts = bool(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ATTEMPT, FIELD_GENERATION_ID, FIELD_JOB_ID, FIELD_CONFIGURATION_DIGEST,
                FIELD_OFFERED_AT, FIELD_ACKNOWLEDGED_AT, FIELD_ENDED_AT, FIELD_END, FIELD_COUNTS);
        try {
            return new Attempt(attempt, generationId, jobId, configurationDigest, offeredAt, acknowledgedAt, endedAt,
                    end, counts);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(name, e.getMessage()), e);
        }
    }

    private static Signal readSignal(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable SignalName signal = null;
        var attempt = 0;
        @Nullable SignalAction action = null;
        double measurement = 0;
        double threshold = 0;
        @Nullable Instant at = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SIGNAL -> signal = closed(parser, field, SignalName::fromWireName);
                case FIELD_ATTEMPT -> attempt = Math.toIntExact(integer(parser, field));
                case FIELD_ACTION -> action = closed(parser, field, SignalAction::fromWireName);
                case FIELD_MEASUREMENT -> measurement = number(parser, field);
                case FIELD_THRESHOLD -> threshold = number(parser, field);
                case FIELD_AT -> at = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_SIGNAL, FIELD_ATTEMPT, FIELD_ACTION, FIELD_MEASUREMENT, FIELD_THRESHOLD,
                FIELD_AT);
        try {
            return new Signal(signal, attempt, action, measurement, threshold, at);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(name, e.getMessage()), e);
        }
    }

    private static Consultation readConsultation(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String consultationId = null;
        @Nullable String role = null;
        @Nullable String askedBy = null;
        @Nullable Instant askedAt = null;
        @Nullable ConsultationStatus status = null;
        @Nullable String answer = null;
        @Nullable Instant closedAt = null;
        @Nullable Instant deliveredAt = null;
        @Nullable DeliveredTo deliveredTo = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CONSULTATION_ID -> consultationId = string(parser, field);
                case FIELD_ROLE -> role = string(parser, field);
                case FIELD_ASKED_BY -> askedBy = string(parser, field);
                case FIELD_ASKED_AT -> askedAt = instant(parser, field);
                case FIELD_STATUS -> status = closed(parser, field, ConsultationStatus::fromWireName);
                case FIELD_ANSWER -> answer = nullableString(parser, field);
                case FIELD_CLOSED_AT -> closedAt = nullableInstant(parser, field);
                case FIELD_DELIVERED_AT -> deliveredAt = nullableInstant(parser, field);
                case FIELD_DELIVERED_TO -> deliveredTo = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, DeliveredTo::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_CONSULTATION_ID, FIELD_ROLE, FIELD_ASKED_BY, FIELD_ASKED_AT, FIELD_STATUS,
                FIELD_ANSWER, FIELD_CLOSED_AT, FIELD_DELIVERED_AT, FIELD_DELIVERED_TO);
        try {
            return new Consultation(consultationId, role, askedBy, askedAt, status, answer, closedAt, deliveredAt,
                    deliveredTo);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(name, e.getMessage()), e);
        }
    }

    private static <V> List<V> readArray(JsonParser parser, String field,
            StrictJson.ValueReader<V> reader) throws IOException {
        return StrictJson.array(parser, field, reader);
    }

    private static double number(JsonParser parser, String field) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getDoubleValue();
            case null, default -> throw unreadable("'%s' must be a number".formatted(field));
        };
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> values)
            throws IOException {
        var name = string(parser, field);
        return values.apply(name).orElseThrow(() -> unreadable("'%s' has the unknown value '%s'".formatted(field,
                name)));
    }
}
