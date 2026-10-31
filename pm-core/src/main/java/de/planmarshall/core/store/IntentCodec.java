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
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.bool;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.IntentRecord.AdrRef;
import de.planmarshall.core.store.IntentRecord.AdrRelation;
import de.planmarshall.core.store.IntentRecord.Assumption;
import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.CapabilityChange;
import de.planmarshall.core.store.IntentRecord.CapabilityLink;
import de.planmarshall.core.store.IntentRecord.Constraint;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.Criterion;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.Deferred;
import de.planmarshall.core.store.IntentRecord.NonGoal;
import de.planmarshall.core.store.IntentRecord.OpenQuestion;
import de.planmarshall.core.store.IntentRecord.Resolver;
import de.planmarshall.core.store.IntentRecord.SourceClaim;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * Reads and writes {@code intent.json} (Store Schemas § Intent Record): the document
 * {@code {"format_version": 1, "current": <revision>, "history": [<revision>, ...]}}, each revision carrying its
 * number, the instant it was accepted, the task whose submission carried it and the record itself.
 * <p>
 * The reader accepts exactly one shape: the current format version, only the declared fields, each at most once,
 * every required field, and a record whose rules hold. Anything else is unreadable, never an empty store, and the
 * refusal names the file.
 *
 * @since 0.1
 */
@UtilityClass
public class IntentCodec {

    private static final String FIELD_CURRENT = "current";
    private static final String FIELD_HISTORY = "history";
    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_SUBMITTED_AT = "submitted_at";
    private static final String FIELD_TASK_ID = "task_id";
    private static final String FIELD_RECORD = "record";
    private static final String FIELD_WHY = "why";
    private static final String FIELD_GOAL = "goal";
    private static final String FIELD_CRITERIA = "criteria";
    private static final String FIELD_CURRENT_BEHAVIOUR = "current_behaviour";
    private static final String FIELD_NON_GOALS = "non_goals";
    private static final String FIELD_CONSTRAINTS = "constraints";
    private static final String FIELD_DECISIONS = "decisions";
    private static final String FIELD_ASSUMPTIONS = "assumptions";
    private static final String FIELD_OPEN_QUESTIONS = "open_questions";
    private static final String FIELD_DEFERRED = "deferred";
    private static final String FIELD_SOURCE_CLAIMS = "source_claims";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_VERIFIED_BY = "verified_by";
    private static final String FIELD_CAPABILITY = "capability";
    private static final String FIELD_REQUIREMENT = "requirement";
    private static final String FIELD_CHANGE = "change";
    private static final String FIELD_BASE = "base";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_EVIDENCE = "evidence";
    private static final String FIELD_OPTIONS_WEIGHED = "options_weighed";
    private static final String FIELD_DECIDER = "decider";
    private static final String FIELD_QUESTION_ID = "question_id";
    private static final String FIELD_ADR = "adr";
    private static final String FIELD_ADR_ID = "adr_id";
    private static final String FIELD_RELATION = "relation";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_RISKIEST = "riskiest";
    private static final String FIELD_TEST = "test";
    private static final String FIELD_RESOLVER = "resolver";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_CLAIM = "claim";
    private static final String FIELD_LANDED_IN = "landed_in";

    /**
     * @param store the store
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(IntentStore store) {
        Objects.requireNonNull(store, "store");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, IntentStore.FORMAT_VERSION);
            generator.writeFieldName(FIELD_CURRENT);
            writeRevision(generator, store.current());
            generator.writeArrayFieldStart(FIELD_HISTORY);
            for (var revision : store.history()) {
                writeRevision(generator, revision);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Intent store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document an intent store document, UTF-8 encoded
     * @return the store
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static IntentStore read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, IntentStore.FORMAT_VERSION, IntentCodec::readDocument);
    }

    private static void writeRevision(JsonGenerator generator, Revision revision) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_REVISION, revision.revision());
        writeInstant(generator, FIELD_SUBMITTED_AT, revision.submittedAt());
        generator.writeStringField(FIELD_TASK_ID, revision.taskId());
        generator.writeFieldName(FIELD_RECORD);
        writeRecord(generator, revision.intentRecord());
        generator.writeEndObject();
    }

    private static void writeRecord(JsonGenerator generator, IntentRecord intentRecord) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_WHY, intentRecord.why());
        generator.writeStringField(FIELD_GOAL, intentRecord.goal());
        generator.writeArrayFieldStart(FIELD_CRITERIA);
        for (var criterion : intentRecord.criteria()) {
            writeCriterion(generator, criterion);
        }
        generator.writeEndArray();
        if (intentRecord.currentBehaviour() == null) {
            generator.writeNullField(FIELD_CURRENT_BEHAVIOUR);
        } else {
            generator.writeStringField(FIELD_CURRENT_BEHAVIOUR, intentRecord.currentBehaviour());
        }
        generator.writeArrayFieldStart(FIELD_NON_GOALS);
        for (var nonGoal : intentRecord.nonGoals()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_ID, nonGoal.id());
            generator.writeStringField(FIELD_TEXT, nonGoal.text());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_CONSTRAINTS);
        for (var constraint : intentRecord.constraints()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_ID, constraint.id());
            generator.writeStringField(FIELD_TEXT, constraint.text());
            generator.writeStringField(FIELD_SOURCE, constraint.source().wireName());
            writeNullableString(generator, FIELD_EVIDENCE, constraint.evidence());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_DECISIONS);
        for (var decision : intentRecord.decisions()) {
            writeDecision(generator, decision);
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_ASSUMPTIONS);
        for (var assumption : intentRecord.assumptions()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_ID, assumption.id());
            generator.writeStringField(FIELD_TEXT, assumption.text());
            generator.writeStringField(FIELD_STATUS, assumption.status().wireName());
            generator.writeBooleanField(FIELD_RISKIEST, assumption.riskiest());
            writeNullableString(generator, FIELD_TEST, assumption.test());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_OPEN_QUESTIONS);
        for (var question : intentRecord.openQuestions()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_TEXT, question.text());
            generator.writeStringField(FIELD_RESOLVER, question.resolver().wireName());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_DEFERRED);
        for (var deferred : intentRecord.deferred()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_ID, deferred.id());
            generator.writeStringField(FIELD_TEXT, deferred.text());
            generator.writeStringField(FIELD_REASON, deferred.reason());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_SOURCE_CLAIMS);
        for (var claim : intentRecord.sourceClaims()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_CLAIM, claim.claim());
            generator.writeStringField(FIELD_LANDED_IN, claim.landedIn());
            writeNullableString(generator, FIELD_REASON, claim.reason());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeCriterion(JsonGenerator generator, Criterion criterion) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_ID, criterion.id());
        generator.writeStringField(FIELD_TEXT, criterion.text());
        generator.writeStringField(FIELD_KIND, criterion.kind().wireName());
        generator.writeStringField(FIELD_VERIFIED_BY, criterion.verifiedBy().wireName());
        if (criterion.capability() == null) {
            generator.writeNullField(FIELD_CAPABILITY);
        } else {
            var capability = criterion.capability();
            generator.writeObjectFieldStart(FIELD_CAPABILITY);
            generator.writeStringField(FIELD_CAPABILITY, capability.capability());
            generator.writeStringField(FIELD_REQUIREMENT, capability.requirement());
            generator.writeStringField(FIELD_CHANGE, capability.change().wireName());
            writeNullableString(generator, FIELD_BASE, capability.base());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeDecision(JsonGenerator generator, Decision decision) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_ID, decision.id());
        generator.writeStringField(FIELD_TEXT, decision.text());
        generator.writeArrayFieldStart(FIELD_OPTIONS_WEIGHED);
        for (var option : decision.optionsWeighed()) {
            generator.writeString(option);
        }
        generator.writeEndArray();
        generator.writeStringField(FIELD_DECIDER, decision.decider().wireName());
        writeNullableString(generator, FIELD_QUESTION_ID, decision.questionId());
        writeNullableString(generator, FIELD_EVIDENCE, decision.evidence());
        if (decision.adr() == null) {
            generator.writeNullField(FIELD_ADR);
        } else {
            generator.writeObjectFieldStart(FIELD_ADR);
            generator.writeStringField(FIELD_ADR_ID, decision.adr().adrId());
            generator.writeStringField(FIELD_RELATION, decision.adr().relation().wireName());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static IntentStore readDocument(JsonParser parser) throws IOException {
        @Nullable Revision current = null;
        List<Revision> history = List.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_CURRENT -> current = readRevision(parser, FIELD_CURRENT);
                case FIELD_HISTORY -> history = array(parser, FIELD_HISTORY, IntentCodec::readRevision);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "document", FIELD_CURRENT, FIELD_HISTORY);
        try {
            return new IntentStore(current, history);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Revision readRevision(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable Long revision = null;
        @Nullable Instant submittedAt = null;
        @Nullable String taskId = null;
        @Nullable IntentRecord intentRecord = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_REVISION -> revision = integer(parser, field);
                case FIELD_SUBMITTED_AT -> submittedAt = StrictJson.instant(parser, field);
                case FIELD_TASK_ID -> taskId = string(parser, field);
                case FIELD_RECORD -> intentRecord = readRecord(parser, FIELD_RECORD);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_REVISION, FIELD_SUBMITTED_AT, FIELD_TASK_ID, FIELD_RECORD);
        try {
            return new Revision(Math.toIntExact(revision), submittedAt, taskId, intentRecord);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static IntentRecord readRecord(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String why = null;
        @Nullable String goal = null;
        @Nullable List<Criterion> criteria = null;
        @Nullable String currentBehaviour = null;
        @Nullable List<NonGoal> nonGoals = null;
        @Nullable List<Constraint> constraints = null;
        @Nullable List<Decision> decisions = null;
        @Nullable List<Assumption> assumptions = null;
        @Nullable List<OpenQuestion> openQuestions = null;
        @Nullable List<Deferred> deferred = null;
        @Nullable List<SourceClaim> sourceClaims = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_WHY -> why = string(parser, field);
                case FIELD_GOAL -> goal = string(parser, field);
                case FIELD_CRITERIA -> criteria = array(parser, field, IntentCodec::readCriterion);
                case FIELD_CURRENT_BEHAVIOUR -> currentBehaviour = nullableString(parser, field).orElse(null);
                case FIELD_NON_GOALS -> nonGoals = array(parser, field, IntentCodec::readNonGoal);
                case FIELD_CONSTRAINTS -> constraints = array(parser, field, IntentCodec::readConstraint);
                case FIELD_DECISIONS -> decisions = array(parser, field, IntentCodec::readDecision);
                case FIELD_ASSUMPTIONS -> assumptions = array(parser, field, IntentCodec::readAssumption);
                case FIELD_OPEN_QUESTIONS -> openQuestions = array(parser, field, IntentCodec::readOpenQuestion);
                case FIELD_DEFERRED -> deferred = array(parser, field, IntentCodec::readDeferred);
                case FIELD_SOURCE_CLAIMS -> sourceClaims = array(parser, field, IntentCodec::readSourceClaim);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_WHY, FIELD_GOAL, FIELD_CRITERIA, FIELD_CURRENT_BEHAVIOUR,
                FIELD_NON_GOALS, FIELD_CONSTRAINTS, FIELD_DECISIONS, FIELD_ASSUMPTIONS, FIELD_OPEN_QUESTIONS,
                FIELD_DEFERRED, FIELD_SOURCE_CLAIMS);
        try {
            return new IntentRecord(why, goal, criteria, currentBehaviour, nonGoals, constraints, decisions,
                    assumptions, openQuestions, deferred, sourceClaims);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Criterion readCriterion(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        @Nullable CriterionKind kind = null;
        @Nullable VerifiedBy verifiedBy = null;
        @Nullable CapabilityLink capability = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_KIND -> kind = required(CriterionKind.fromWireName(string(parser, field)), field);
                case FIELD_VERIFIED_BY ->
                    verifiedBy = required(VerifiedBy.fromWireName(string(parser, field)), field);
                case FIELD_CAPABILITY -> capability = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readCapability(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT, FIELD_KIND, FIELD_VERIFIED_BY, FIELD_CAPABILITY);
        try {
            return new Criterion(id, text, kind, verifiedBy, capability);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static CapabilityLink readCapability(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String capability = null;
        @Nullable String requirement = null;
        @Nullable CapabilityChange change = null;
        @Nullable String base = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CAPABILITY -> capability = string(parser, field);
                case FIELD_REQUIREMENT -> requirement = string(parser, field);
                case FIELD_CHANGE -> change = required(CapabilityChange.fromWireName(string(parser, field)), field);
                case FIELD_BASE -> base = nullableString(parser, field).orElse(null);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_CAPABILITY, FIELD_REQUIREMENT, FIELD_CHANGE, FIELD_BASE);
        try {
            return new CapabilityLink(capability, requirement, change, base);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static NonGoal readNonGoal(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT);
        try {
            return new NonGoal(id, text);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Constraint readConstraint(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        @Nullable ConstraintSource source = null;
        @Nullable String evidence = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_SOURCE -> source = required(ConstraintSource.fromWireName(string(parser, field)), field);
                case FIELD_EVIDENCE -> evidence = nullableString(parser, field).orElse(null);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT, FIELD_SOURCE, FIELD_EVIDENCE);
        try {
            return new Constraint(id, text, source, evidence);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Decision readDecision(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        List<String> options = List.of();
        @Nullable Decider decider = null;
        @Nullable String questionId = null;
        @Nullable String evidence = null;
        @Nullable AdrRef adr = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_OPTIONS_WEIGHED -> options = stringList(parser, field);
                case FIELD_DECIDER -> decider = required(Decider.fromWireName(string(parser, field)), field);
                case FIELD_QUESTION_ID -> questionId = nullableString(parser, field).orElse(null);
                case FIELD_EVIDENCE -> evidence = nullableString(parser, field).orElse(null);
                case FIELD_ADR -> adr = parser.currentToken() == JsonToken.VALUE_NULL ? null : readAdr(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT, FIELD_OPTIONS_WEIGHED, FIELD_DECIDER, FIELD_QUESTION_ID,
                FIELD_EVIDENCE, FIELD_ADR);
        try {
            return new Decision(id, text, options, decider, questionId, evidence, adr);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static AdrRef readAdr(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String adrId = null;
        @Nullable AdrRelation relation = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ADR_ID -> adrId = string(parser, field);
                case FIELD_RELATION -> relation = required(AdrRelation.fromWireName(string(parser, field)), field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ADR_ID, FIELD_RELATION);
        try {
            return new AdrRef(adrId, relation);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Assumption readAssumption(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        @Nullable AssumptionStatus status = null;
        @Nullable Boolean riskiest = null;
        @Nullable String test = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_STATUS -> status = required(AssumptionStatus.fromWireName(string(parser, field)), field);
                case FIELD_RISKIEST -> riskiest = bool(parser, field);
                case FIELD_TEST -> test = nullableString(parser, field).orElse(null);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT, FIELD_STATUS, FIELD_RISKIEST, FIELD_TEST);
        var flagged = riskiest;
        try {
            return new Assumption(id, text, status, Boolean.TRUE.equals(flagged), test);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static OpenQuestion readOpenQuestion(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String text = null;
        @Nullable Resolver resolver = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_RESOLVER -> resolver = required(Resolver.fromWireName(string(parser, field)), field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_TEXT, FIELD_RESOLVER);
        try {
            return new OpenQuestion(text, resolver);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Deferred readDeferred(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String text = null;
        @Nullable String reason = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_REASON -> reason = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_TEXT, FIELD_REASON);
        try {
            return new Deferred(id, text, reason);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static SourceClaim readSourceClaim(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String claim = null;
        @Nullable String landedIn = null;
        @Nullable String reason = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CLAIM -> claim = string(parser, field);
                case FIELD_LANDED_IN -> landedIn = string(parser, field);
                case FIELD_REASON -> reason = nullableString(parser, field).orElse(null);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_CLAIM, FIELD_LANDED_IN, FIELD_REASON);
        try {
            return new SourceClaim(claim, landedIn, reason);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static <T> T required(Optional<T> value, String field) {
        return value.orElseThrow(() -> unreadable("'%s' names no known value".formatted(field)));
    }
}
