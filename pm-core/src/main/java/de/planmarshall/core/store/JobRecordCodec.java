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
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.ClampedValue;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.SelfReportContradiction;
import de.planmarshall.core.store.JobRecord.Status;

/**
 * Reads and writes the job record {@code jobs/<job_id>.json} (PM-EXEC-4, PM-IMPL-2).
 * <p>
 * The reader checks the {@code format_version} first, then reads every member strictly: each at most once, only the
 * declared members, every required member, one JSON type per member, the closed values of every enumeration, and the
 * rules of {@link JobRecord}. The {@code kind} must be the kind the {@code job_id} names. The members {@code worker} and
 * {@code gate_facts} must be JSON objects or {@code null} and are captured as the exact text they were read with.
 * Whatever does not parse is a {@link StoreRefusal} naming the file.
 * <p>
 * The writer writes every member in the order of the schema, {@code worker} and {@code gate_facts} as the text they
 * were read with, so what a supervisor or a gate wrote is re-emitted byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class JobRecordCodec {

    private static final String JOB_ID = "job_id";
    private static final String KIND = "kind";
    private static final String EXECUTION = "execution";
    private static final String SCOPE = "scope";
    private static final String WORKER = "worker";
    private static final String BUDGET_KEY = "budget_key";
    private static final String BUDGET_SECONDS = "budget_seconds";
    private static final String BUDGET_DERIVATION = "budget_derivation";
    private static final String ARGV_DIGEST = "argv_digest";
    private static final String STATUS = "status";
    private static final String FATE = "fate";
    private static final String PID = "pid";
    private static final String PROCESS_STARTED_AT = "process_started_at";
    private static final String CONFINEMENT = "confinement";
    private static final String CREATED_AT = "created_at";
    private static final String SPAWNED_AT = "spawned_at";
    private static final String ENDED_AT = "ended_at";
    private static final String CHILD_WALL_MS = "child_wall_ms";
    private static final String EXIT_CODE = "exit_code";
    private static final String SIGNAL = "signal";
    private static final String LANDLOCK_ABI = "landlock_abi";
    private static final String OUTCOME_ROW = "outcome_row";
    private static final String REFUSAL_REASON = "refusal_reason";
    private static final String CANCEL_BEFORE_GROUP = "cancel_before_group";
    private static final String SELF_REPORT_CONTRADICTION = "self_report_contradiction";
    private static final String CLAMPED_VALUES = "clamped_values";
    private static final String GATE_FACTS = "gate_facts";

    private static final String TYPE = "type";
    private static final String ID = "id";
    private static final String OBSERVATIONS = "observations";
    private static final String REPORTED_STATUS = "reported_status";
    private static final String PARSE_RESULT = "parse_result";
    private static final String KEY = "key";
    private static final String CONFIGURED = "configured";
    private static final String USED = "used";
    private static final String SOURCE = "source";

    /** The members of the record, in the order of the schema. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, JOB_ID, KIND, EXECUTION, SCOPE, WORKER,
            BUDGET_KEY, BUDGET_SECONDS, BUDGET_DERIVATION, ARGV_DIGEST, STATUS, FATE, PID, PROCESS_STARTED_AT,
            CONFINEMENT, CREATED_AT, SPAWNED_AT, ENDED_AT, CHILD_WALL_MS, EXIT_CODE, SIGNAL, LANDLOCK_ABI, OUTCOME_ROW,
            REFUSAL_REASON, CANCEL_BEFORE_GROUP, SELF_REPORT_CONTRADICTION, CLAMPED_VALUES, GATE_FACTS);

    /** The members of the scope, in the order of the schema. */
    static final List<String> SCOPE_MEMBERS = List.of(TYPE, ID);

    /** The members of the budget derivation, in the order of the schema; only {@code kind} is required. */
    static final List<String> BUDGET_DERIVATION_MEMBERS = List.of(KIND, OBSERVATIONS);

    /** The members of the self-report contradiction, in the order of the schema. */
    static final List<String> CONTRADICTION_MEMBERS = List.of(REPORTED_STATUS, EXIT_CODE, PARSE_RESULT);

    /** The members of a clamped value, in the order of the schema. */
    static final List<String> CLAMPED_VALUE_MEMBERS = List.of(KEY, CONFIGURED, USED, SOURCE);

    /**
     * @param job a job record
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(JobRecord job) {
        Objects.requireNonNull(job, "job");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, JobRecord.FORMAT_VERSION);
            generator.writeStringField(JOB_ID, job.jobId().value());
            generator.writeStringField(KIND, job.kind().wireName());
            generator.writeStringField(EXECUTION, job.execution().wireName());
            writeScope(generator, job.scope());
            writeRaw(generator, WORKER, job.worker());
            generator.writeStringField(BUDGET_KEY, job.budgetKey());
            generator.writeNumberField(BUDGET_SECONDS, job.budgetSeconds());
            writeBudgetDerivation(generator, job.budgetDerivation());
            writeNullable(generator, ARGV_DIGEST, job.argvDigest());
            generator.writeStringField(STATUS, job.status().wireName());
            writeNullable(generator, FATE, job.fate() == null ? null : job.fate().wireName());
            writeNullable(generator, PID, job.pid());
            writeInstant(generator, PROCESS_STARTED_AT, job.processStartedAt());
            generator.writeStringField(CONFINEMENT, job.confinement().wireName());
            writeInstant(generator, CREATED_AT, job.createdAt());
            writeInstant(generator, SPAWNED_AT, job.spawnedAt());
            writeInstant(generator, ENDED_AT, job.endedAt());
            writeNullable(generator, CHILD_WALL_MS, job.childWallMs());
            writeNullable(generator, EXIT_CODE, job.exitCode());
            writeNullable(generator, SIGNAL, job.signal());
            writeNullable(generator, LANDLOCK_ABI, job.landlockAbi() == null ? null : job.landlockAbi().longValue());
            writeNullable(generator, OUTCOME_ROW, job.outcomeRow() == null ? null : job.outcomeRow().longValue());
            writeNullable(generator, REFUSAL_REASON, job.refusalReason());
            generator.writeBooleanField(CANCEL_BEFORE_GROUP, job.cancelBeforeGroup());
            writeContradiction(generator, job.selfReportContradiction());
            writeClampedValues(generator, job.clampedValues());
            writeRaw(generator, GATE_FACTS, job.gateFacts());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Job record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the record file the document was read from, named by a refusal
     * @param document a job record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the document declares another format version or does not have the one shape of a job
     *                      record
     */
    public static JobRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, JobRecord.FORMAT_VERSION, parser -> {
            try {
                return readDocument(parser, document);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    private static JobRecord readDocument(JsonParser parser, byte[] document) throws IOException {
        var job = JobRecord.builder();
        JobId.Kind kind = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case JOB_ID -> job.jobId(new JobId(string(parser, field)));
                case KIND -> kind = closed(parser, field, JobId.Kind::fromWireName);
                case EXECUTION -> job.execution(closed(parser, field, Execution::fromWireName));
                case SCOPE -> job.scope(readScope(parser));
                case WORKER -> job.worker(rawObjectOrNull(parser, document, field).orElse(null));
                case BUDGET_KEY -> job.budgetKey(string(parser, field));
                case BUDGET_SECONDS -> job.budgetSeconds(integer(parser, field));
                case BUDGET_DERIVATION -> job.budgetDerivation(readBudgetDerivation(parser));
                case ARGV_DIGEST -> job.argvDigest(nullableString(parser, field));
                case STATUS -> job.status(closed(parser, field, Status::fromWireName));
                case FATE -> job.fate(isNull(parser) ? null : closed(parser, field, Fate::fromWireName));
                case PID -> job.pid(nullableInteger(parser, field));
                case PROCESS_STARTED_AT -> job.processStartedAt(nullableInstant(parser, field));
                case CONFINEMENT -> job.confinement(closed(parser, field, Confinement::fromWireName));
                case CREATED_AT -> job.createdAt(instant(parser, field));
                case SPAWNED_AT -> job.spawnedAt(nullableInstant(parser, field));
                case ENDED_AT -> job.endedAt(nullableInstant(parser, field));
                case CHILD_WALL_MS -> job.childWallMs(nullableInteger(parser, field));
                case EXIT_CODE -> job.exitCode(nullableInteger(parser, field));
                case SIGNAL -> job.signal(nullableString(parser, field));
                case LANDLOCK_ABI -> job.landlockAbi(nullableInt(parser, field));
                case OUTCOME_ROW -> job.outcomeRow(nullableInt(parser, field));
                case REFUSAL_REASON -> job.refusalReason(nullableString(parser, field));
                case CANCEL_BEFORE_GROUP -> job.cancelBeforeGroup(bool(parser, field));
                case SELF_REPORT_CONTRADICTION -> job.selfReportContradiction(
                        isNull(parser) ? null : readContradiction(parser));
                case CLAMPED_VALUES -> job.clampedValues(
                        StrictJson.array(parser, field, (element, name) -> readClampedValue(element, name, document)));
                case GATE_FACTS -> job.gateFacts(rawObjectOrNull(parser, document, field).orElse(null));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "job record", MEMBERS.toArray(String[]::new));
        var built = job.build();
        if (built.kind() != kind) {
            throw unreadable("'%s' is '%s', not the kind '%s' the job id '%s' names".formatted(KIND,
                    Objects.requireNonNull(kind).wireName(), built.kind().wireName(), built.jobId()));
        }
        return built;
    }

    private static Scope readScope(JsonParser parser) throws IOException {
        requireObject(parser, SCOPE);
        Scope.Type type = null;
        String id = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case TYPE -> type = closed(parser, "scope.type", Scope.Type::fromWireName);
                case ID -> id = nullableString(parser, "scope.id");
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, SCOPE, SCOPE_MEMBERS.toArray(String[]::new));
        return new Scope(type, id);
    }

    private static BudgetDerivation readBudgetDerivation(JsonParser parser) throws IOException {
        requireObject(parser, BUDGET_DERIVATION);
        BudgetDerivation.Kind kind = null;
        Long observations = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case KIND -> kind = closed(parser, "budget_derivation.kind", BudgetDerivation.Kind::fromWireName);
                case OBSERVATIONS -> observations = integer(parser, "budget_derivation.observations");
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, BUDGET_DERIVATION, KIND);
        return new BudgetDerivation(kind, observations);
    }

    private static SelfReportContradiction readContradiction(JsonParser parser) throws IOException {
        requireObject(parser, SELF_REPORT_CONTRADICTION);
        String reportedStatus = null;
        Long exitCode = null;
        String parseResult = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case REPORTED_STATUS -> reportedStatus = string(parser, field);
                case EXIT_CODE -> exitCode = nullableInteger(parser, field);
                case PARSE_RESULT -> parseResult = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, SELF_REPORT_CONTRADICTION, CONTRADICTION_MEMBERS.toArray(String[]::new));
        return new SelfReportContradiction(reportedStatus, exitCode, parseResult);
    }

    private static ClampedValue readClampedValue(JsonParser parser, String name, byte[] document) throws IOException {
        requireObject(parser, name);
        String key = null;
        String configured = null;
        BigDecimal used = null;
        ClampedValue.Source source = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case KEY -> key = string(parser, field);
                case CONFIGURED -> configured = configured(parser, document, name + "." + field);
                case USED -> used = number(parser, name + "." + field);
                case SOURCE -> source = closed(parser, name + "." + field, ClampedValue.Source::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, CLAMPED_VALUE_MEMBERS.toArray(String[]::new));
        return new ClampedValue(key, configured, used, source);
    }

    private static String configured(JsonParser parser, byte[] document, String name) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> WorkflowStatusCodec.raw(parser, document);
            case null, default -> throw unreadable("'%s' must be a number, a string or null".formatted(name));
        };
    }

    private static BigDecimal number(JsonParser parser, String name) throws IOException {
        var token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw unreadable("'%s' must be a number".formatted(name));
        }
        return parser.getDecimalValue();
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    private static Optional<String> rawObjectOrNull(JsonParser parser, byte[] document, String field) throws IOException {
        if (isNull(parser)) {
            return Optional.empty();
        }
        requireObject(parser, field);
        return Optional.of(WorkflowStatusCodec.raw(parser, document));
    }

    private static Long nullableInteger(JsonParser parser, String field) throws IOException {
        return isNull(parser) ? null : integer(parser, field);
    }

    private static Integer nullableInt(JsonParser parser, String field) throws IOException {
        var value = nullableInteger(parser, field);
        if (value != null && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)) {
            throw unreadable("'%s' is out of range: %d".formatted(field, value));
        }
        return value == null ? null : value.intValue();
    }

    private static boolean isNull(JsonParser parser) {
        return parser.currentToken() == JsonToken.VALUE_NULL;
    }

    private static void writeScope(JsonGenerator generator, Scope scope) throws IOException {
        generator.writeObjectFieldStart(SCOPE);
        generator.writeStringField(TYPE, scope.type().wireName());
        writeNullable(generator, ID, scope.id());
        generator.writeEndObject();
    }

    private static void writeBudgetDerivation(JsonGenerator generator, BudgetDerivation derivation)
            throws IOException {
        generator.writeObjectFieldStart(BUDGET_DERIVATION);
        generator.writeStringField(KIND, derivation.kind().wireName());
        if (derivation.observations() != null) {
            generator.writeNumberField(OBSERVATIONS, derivation.observations());
        }
        generator.writeEndObject();
    }

    private static void writeContradiction(JsonGenerator generator, SelfReportContradiction contradiction)
            throws IOException {
        if (contradiction == null) {
            generator.writeNullField(SELF_REPORT_CONTRADICTION);
            return;
        }
        generator.writeObjectFieldStart(SELF_REPORT_CONTRADICTION);
        generator.writeStringField(REPORTED_STATUS, contradiction.reportedStatus());
        writeNullable(generator, EXIT_CODE, contradiction.exitCode());
        generator.writeStringField(PARSE_RESULT, contradiction.parseResult());
        generator.writeEndObject();
    }

    private static void writeClampedValues(JsonGenerator generator, List<ClampedValue> values) throws IOException {
        generator.writeArrayFieldStart(CLAMPED_VALUES);
        for (var value : values) {
            generator.writeStartObject();
            generator.writeStringField(KEY, value.key());
            generator.writeFieldName(CONFIGURED);
            if (value.configured() == null) {
                generator.writeNull();
            } else {
                generator.writeRawValue(value.configured());
            }
            generator.writeFieldName(USED);
            generator.writeNumber(value.used().toPlainString());
            generator.writeStringField(SOURCE, value.source().wireName());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeRaw(JsonGenerator generator, String field, String raw) throws IOException {
        generator.writeFieldName(field);
        if (raw == null) {
            generator.writeNull();
        } else {
            generator.writeRawValue(raw);
        }
    }

    private static void writeNullable(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static void writeNullable(JsonGenerator generator, String field, Long value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }
}
