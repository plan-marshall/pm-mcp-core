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
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireArray;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;

/**
 * Reads and writes {@code metrics.json} (Store Schemas § Metrics &amp; Quality Report).
 * <p>
 * This store owns the rows of a task and of a worker's idle turns — the two kinds this milestone writes. A row of
 * another kind ({@code wait}, {@code phase_close}, {@code served_read}) is kept as the raw JSON text it was read with
 * and written back in the same shape, re-emitted token by token: another plan of the milestone owns those rows and
 * writes them through its own codec, so this reader neither invents their schema nor drops them.
 * <p>
 * The reader is as strict as every store of the runtime: exactly the current {@code format_version}, only declared
 * fields, each at most once, every required field, and a {@code total_tokens} that equals the sum of the five token
 * components it is derived from. Anything else is unreadable, never an empty store, and the refusal names the file.
 *
 * @since 0.1
 */
@UtilityClass
public class MetricsCodec {

    private static final String FIELD_ROWS = "rows";
    private static final String PARAMETER_DOCUMENT = "document";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_QUEUE_TASK_ID = "queue_task_id";
    private static final String FIELD_ATTEMPT = "attempt";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_JOB_ID = "job_id";
    private static final String FIELD_GENERATION_ID = "generation_id";
    private static final String FIELD_HARNESS = "harness";
    private static final String FIELD_HARNESS_VERSION = "harness_version";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_FORM = "form";
    private static final String FIELD_PHASE = "phase";
    private static final String FIELD_STEP_ID = "step_id";
    private static final String FIELD_ENVELOPE_ID = "envelope_id";
    private static final String FIELD_PREDICTED_COST = "predicted_cost";
    private static final String FIELD_YIELD_REASON = "yield_reason";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_ATTEMPT_END = "attempt_end";
    private static final String FIELD_CONTINUATION = "continuation";
    private static final String FIELD_CAUSE_CLASS = "cause_class";
    private static final String FIELD_SPEND_CLASS = "spend_class";
    private static final String FIELD_POPULATION = "population";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";
    private static final String FIELD_CACHE_READ = "cache_read_input_tokens";
    private static final String FIELD_CACHE_CREATION = "cache_creation_input_tokens";
    private static final String FIELD_REASONING = "reasoning_tokens";
    private static final String FIELD_TOOL_USES = "tool_uses";
    private static final String FIELD_COST_USD = "cost_usd";
    private static final String FIELD_UNMEASURED_REASON = "unmeasured_reason";
    private static final String FIELD_DURATION_MS = "duration_ms";
    private static final String FIELD_DURATION_BASIS = "duration_basis";
    private static final String FIELD_QUEUED_MS = "queued_ms";
    private static final String FIELD_CONSULTATION_OF = "consultation_of";
    private static final String FIELD_IDLE_WAKEUPS = "idle_wakeups";
    private static final String FIELD_RECORDED_AT = "recorded_at";
    private static final String FIELD_TOKENS = "tokens";
    private static final String FIELD_BASIS = "basis";
    private static final String UNMEASURED = "unmeasured";

    /**
     * The rows of a metrics store: the task rows and the worker-idle rows this milestone owns, and the rows of other
     * kinds as the raw JSON text they were read with. A write re-emits the task rows first, then the idle rows, then
     * the rows of other kinds; a row of another kind keeps its content but not its position among the rows.
     *
     * @param taskRows    the task rows, in their order
     * @param idleRows    the worker-idle rows, in their order
     * @param foreignRows the rows of other kinds, as raw JSON text, in their order
     * @since 0.1
     */
    public record Document(List<MetricsRow> taskRows, List<WorkerIdleRow> idleRows, List<String> foreignRows) {

        /** @throws NullPointerException if a component is {@code null} */
        public Document {
            taskRows = List.copyOf(Objects.requireNonNull(taskRows, "taskRows"));
            idleRows = List.copyOf(Objects.requireNonNull(idleRows, "idleRows"));
            foreignRows = List.copyOf(Objects.requireNonNull(foreignRows, "foreignRows"));
        }

        /** @return the empty document */
        public static Document empty() {
            return new Document(List.of(), List.of(), List.of());
        }
    }

    /**
     * @param document the document to write
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(Document document) {
        Objects.requireNonNull(document, PARAMETER_DOCUMENT);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, MetricsStore.FORMAT_VERSION);
            generator.writeArrayFieldStart(FIELD_ROWS);
            for (var row : document.taskRows()) {
                writeTaskRow(generator, row);
            }
            for (var row : document.idleRows()) {
                writeIdleRow(generator, row);
            }
            for (var row : document.foreignRows()) {
                generator.writeRawValue(row);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Metrics store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document a metrics store document, UTF-8 encoded
     * @return the rows of the document
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, PARAMETER_DOCUMENT);
        return StrictJson.read(file, document, MetricsStore.FORMAT_VERSION, MetricsCodec::readDocument);
    }

    private static void writeTaskRow(JsonGenerator generator, MetricsRow row) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KIND, MetricsRow.KIND);
        generator.writeStringField(FIELD_QUEUE_TASK_ID, row.queueTaskId());
        writeNullableInteger(generator, FIELD_ATTEMPT, row.attempt());
        generator.writeStringField(FIELD_ROLE, row.role());
        writeNullableString(generator, FIELD_JOB_ID, row.jobId());
        writeNullableString(generator, FIELD_GENERATION_ID, row.generationId());
        writeNullableString(generator, FIELD_HARNESS, row.harness());
        writeNullableString(generator, FIELD_HARNESS_VERSION, row.harnessVersion());
        writeNullableString(generator, FIELD_MODEL, row.model());
        writeNullableString(generator, FIELD_EFFORT, row.effort());
        writeEnum(generator, FIELD_FORM, row.form() == null ? null : row.form().wireName());
        writeEnum(generator, FIELD_PHASE, row.phase() == null ? null : row.phase().wireName());
        writeNullableString(generator, FIELD_STEP_ID, row.stepId());
        writeNullableString(generator, FIELD_ENVELOPE_ID, row.envelopeId());
        writePredictedCost(generator, row.predictedCost());
        writeEnum(generator, FIELD_YIELD_REASON, row.yieldReason() == null ? null : row.yieldReason().wireName());
        generator.writeStringField(FIELD_OUTCOME, row.outcome().wireName());
        writeEnum(generator, FIELD_ATTEMPT_END, row.attemptEnd() == null ? null : row.attemptEnd().wireName());
        writeEnum(generator, FIELD_CONTINUATION,
                row.continuation() == null ? null : row.continuation().wireName());
        generator.writeStringField(FIELD_CAUSE_CLASS, row.causeClass().wireName());
        generator.writeStringField(FIELD_SPEND_CLASS, row.spendClass().wireName());
        generator.writeStringField(FIELD_POPULATION, row.population().wireName());
        writeUsage(generator, row.usage());
        writeNullableLong(generator, FIELD_DURATION_MS, row.durationMs());
        writeEnum(generator, FIELD_DURATION_BASIS,
                row.durationBasis() == null ? null : row.durationBasis().wireName());
        writeNullableLong(generator, FIELD_QUEUED_MS, row.queuedMs());
        writeNullableString(generator, FIELD_CONSULTATION_OF, row.consultationOf());
        writeInstant(generator, FIELD_RECORDED_AT, row.recordedAt());
        generator.writeEndObject();
    }

    private static void writeIdleRow(JsonGenerator generator, WorkerIdleRow row) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KIND, WorkerIdleRow.KIND);
        generator.writeStringField(FIELD_JOB_ID, row.jobId());
        generator.writeStringField(FIELD_ROLE, row.role());
        generator.writeStringField(FIELD_HARNESS, row.harness());
        generator.writeStringField(FIELD_HARNESS_VERSION, row.harnessVersion());
        generator.writeStringField(FIELD_MODEL, row.model());
        generator.writeStringField(FIELD_POPULATION, WorkerIdleRow.POPULATION);
        writeUsage(generator, row.usage());
        generator.writeNumberField(FIELD_IDLE_WAKEUPS, row.idleWakeups());
        writeInstant(generator, FIELD_RECORDED_AT, row.recordedAt());
        generator.writeEndObject();
    }

    private static void writeUsage(JsonGenerator generator, HarnessUsage usage) throws IOException {
        if (usage.totalTokens().isPresent()) {
            generator.writeNumberField(FIELD_TOTAL_TOKENS, usage.totalTokens().getAsLong());
        } else {
            generator.writeStringField(FIELD_TOTAL_TOKENS, UNMEASURED);
        }
        writeMeasurement(generator, FIELD_INPUT_TOKENS, usage.inputTokens());
        writeMeasurement(generator, FIELD_OUTPUT_TOKENS, usage.outputTokens());
        writeMeasurement(generator, FIELD_CACHE_READ, usage.cacheReadInputTokens());
        writeMeasurement(generator, FIELD_CACHE_CREATION, usage.cacheCreationInputTokens());
        writeMeasurement(generator, FIELD_REASONING, usage.reasoningTokens());
        writeMeasurement(generator, FIELD_TOOL_USES, usage.toolUses());
        if (usage.costUsd().isPresent()) {
            generator.writeNumberField(FIELD_COST_USD, usage.costUsd().get());
        } else {
            generator.writeStringField(FIELD_COST_USD, UNMEASURED);
        }
        writeEnum(generator, FIELD_UNMEASURED_REASON,
                usage.unmeasuredReason().map(UnmeasuredReason::wireName).orElse(null));
    }

    private static void writeMeasurement(JsonGenerator generator, String field, Measurement measurement)
            throws IOException {
        switch (measurement.state()) {
            case MEASURED -> generator.writeNumberField(field, measurement.value());
            case UNMEASURED -> generator.writeStringField(field, UNMEASURED);
            case NOT_APPLICABLE -> generator.writeStringField(field, UsagePointers.NOT_APPLICABLE);
        }
    }

    private static void writePredictedCost(JsonGenerator generator, MetricsRow.PredictedCost predictedCost)
            throws IOException {
        if (predictedCost == null) {
            generator.writeNullField(FIELD_PREDICTED_COST);
            return;
        }
        generator.writeObjectFieldStart(FIELD_PREDICTED_COST);
        generator.writeNumberField(FIELD_TOKENS, predictedCost.tokens());
        generator.writeStringField(FIELD_BASIS, predictedCost.basis().wireName());
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static void writeNullableInteger(JsonGenerator generator, String field, @Nullable Integer value)
            throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    private static void writeNullableLong(JsonGenerator generator, String field, @Nullable Long value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    private static void writeEnum(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static Document readDocument(JsonParser parser) throws IOException {
        @Nullable List<String> rows = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_ROWS -> rows = readRows(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, PARAMETER_DOCUMENT, FIELD_ROWS);
        var tasks = new ArrayList<MetricsRow>();
        var idle = new ArrayList<WorkerIdleRow>();
        var foreign = new ArrayList<String>();
        for (var row : rows) {
            var kind = kindOf(row).orElse(null);
            if (MetricsRow.KIND.equals(kind)) {
                tasks.add(readTaskRow(row));
            } else if (WorkerIdleRow.KIND.equals(kind)) {
                idle.add(readIdleRow(row));
            } else {
                foreign.add(row);
            }
        }
        return new Document(tasks, idle, foreign);
    }

    private static List<String> readRows(JsonParser parser) throws IOException {
        requireArray(parser, FIELD_ROWS);
        var rows = new ArrayList<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw unreadable("'rows' holds JSON objects only");
            }
            rows.add(copy(parser));
        }
        return rows;
    }

    /** Copies the object the parser stands on, so a row of an unknown kind is kept and written back verbatim. */
    private static String copy(JsonParser parser) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.copyCurrentStructure(parser);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Optional<String> kindOf(String row) {
        try (var parser = StrictJson.factory().createParser(row)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable("a row of 'rows' is not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                parser.nextToken();
                if (FIELD_KIND.equals(field)) {
                    return parser.currentToken() == JsonToken.VALUE_STRING
                            ? Optional.of(parser.getText())
                            : Optional.empty();
                }
                parser.skipChildren();
            }
            return Optional.empty();
        } catch (IOException e) {
            throw unreadable("a row of 'rows' is not readable", e);
        }
    }

    private static MetricsRow readTaskRow(String row) throws IOException {
        try (var parser = StrictJson.factory().createParser(row)) {
            parser.nextToken();
            return readTaskRow(parser);
        }
    }

    private static MetricsRow readTaskRow(JsonParser parser) throws IOException {
        @Nullable String kind = null;
        @Nullable String queueTaskId = null;
        @Nullable Integer attempt = null;
        @Nullable String role = null;
        @Nullable String jobId = null;
        @Nullable String generationId = null;
        @Nullable String harness = null;
        @Nullable String harnessVersion = null;
        @Nullable String model = null;
        @Nullable String effort = null;
        MetricsRow.@Nullable Form form = null;
        MetricsRow.@Nullable Phase phase = null;
        @Nullable String stepId = null;
        @Nullable String envelopeId = null;
        MetricsRow.@Nullable PredictedCost predictedCost = null;
        MetricsRow.@Nullable YieldReason yieldReason = null;
        MetricsRow.@Nullable Outcome outcome = null;
        @Nullable AttemptEnd attemptEnd = null;
        MetricsRow.@Nullable ContinuationMark continuation = null;
        MetricsRow.@Nullable CauseClass causeClass = null;
        MetricsRow.@Nullable SpendClass spendClass = null;
        MetricsRow.@Nullable Population population = null;
        var usageState = new UsageState();
        @Nullable Long durationMs = null;
        MetricsRow.@Nullable DurationBasis durationBasis = null;
        @Nullable Long queuedMs = null;
        @Nullable String consultationOf = null;
        @Nullable Instant recordedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = string(parser, field);
                case FIELD_QUEUE_TASK_ID -> queueTaskId = string(parser, field);
                case FIELD_ATTEMPT -> attempt = nullableInteger(parser, field);
                case FIELD_ROLE -> role = string(parser, field);
                case FIELD_JOB_ID -> jobId = nullableString(parser, field);
                case FIELD_GENERATION_ID -> generationId = nullableString(parser, field);
                case FIELD_HARNESS -> harness = nullableString(parser, field);
                case FIELD_HARNESS_VERSION -> harnessVersion = nullableString(parser, field);
                case FIELD_MODEL -> model = nullableString(parser, field);
                case FIELD_EFFORT -> effort = nullableString(parser, field);
                case FIELD_FORM -> form = enumValue(parser, field, MetricsRow.Form::fromWireName).orElse(null);
                case FIELD_PHASE -> phase = enumValue(parser, field, MetricsRow.Phase::fromWireName).orElse(null);
                case FIELD_STEP_ID -> stepId = nullableString(parser, field);
                case FIELD_ENVELOPE_ID -> envelopeId = nullableString(parser, field);
                case FIELD_PREDICTED_COST -> predictedCost = nullablePredictedCost(parser, field).orElse(null);
                case FIELD_YIELD_REASON -> yieldReason =
                        enumValue(parser, field, MetricsRow.YieldReason::fromWireName).orElse(null);
                case FIELD_OUTCOME -> outcome = enumValue(parser, field, MetricsRow.Outcome::fromWireName).orElse(null);
                case FIELD_ATTEMPT_END -> attemptEnd = enumValue(parser, field, AttemptEnd::fromWireName).orElse(null);
                case FIELD_CONTINUATION -> continuation = enumValue(parser, field,
                        MetricsRow.ContinuationMark::fromWireName).orElse(null);
                case FIELD_CAUSE_CLASS -> causeClass =
                        enumValue(parser, field, MetricsRow.CauseClass::fromWireName).orElse(null);
                case FIELD_SPEND_CLASS -> spendClass =
                        enumValue(parser, field, MetricsRow.SpendClass::fromWireName).orElse(null);
                case FIELD_POPULATION -> population =
                        enumValue(parser, field, MetricsRow.Population::fromWireName).orElse(null);
                case FIELD_TOTAL_TOKENS, FIELD_INPUT_TOKENS, FIELD_OUTPUT_TOKENS, FIELD_CACHE_READ,
                    FIELD_CACHE_CREATION, FIELD_REASONING, FIELD_TOOL_USES, FIELD_COST_USD,
                    FIELD_UNMEASURED_REASON ->
                    usageState.read(parser, field);
                case FIELD_DURATION_MS -> durationMs = nullableLong(parser, field);
                case FIELD_DURATION_BASIS -> durationBasis = enumValue(parser, field,
                        MetricsRow.DurationBasis::fromWireName).orElse(null);
                case FIELD_QUEUED_MS -> queuedMs = nullableLong(parser, field);
                case FIELD_CONSULTATION_OF -> consultationOf = nullableString(parser, field);
                case FIELD_RECORDED_AT -> recordedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "task row", FIELD_KIND, FIELD_QUEUE_TASK_ID, FIELD_ATTEMPT, FIELD_ROLE, FIELD_JOB_ID,
                FIELD_GENERATION_ID, FIELD_HARNESS, FIELD_HARNESS_VERSION, FIELD_MODEL, FIELD_EFFORT, FIELD_FORM,
                FIELD_PHASE, FIELD_STEP_ID, FIELD_ENVELOPE_ID, FIELD_PREDICTED_COST, FIELD_OUTCOME,
                FIELD_ATTEMPT_END, FIELD_CONTINUATION, FIELD_CAUSE_CLASS, FIELD_SPEND_CLASS, FIELD_POPULATION, FIELD_TOTAL_TOKENS,
                FIELD_INPUT_TOKENS, FIELD_OUTPUT_TOKENS, FIELD_CACHE_READ, FIELD_CACHE_CREATION, FIELD_REASONING,
                FIELD_TOOL_USES, FIELD_COST_USD, FIELD_UNMEASURED_REASON, FIELD_DURATION_MS, FIELD_DURATION_BASIS,
                FIELD_QUEUED_MS, FIELD_CONSULTATION_OF, FIELD_RECORDED_AT);
        if (!MetricsRow.KIND.equals(kind)) {
            throw unreadable("a task row declares the kind 'task'");
        }
        var usage = usageState.build().orElse(null);
        requireCompleteUsage(usage);
        if (outcome == null || causeClass == null || spendClass == null || population == null) {
            throw unreadable("a task row declares a non-null outcome, cause_class, spend_class and population");
        }
        try {
            return MetricsRow.builder().queueTaskId(queueTaskId).attempt(attempt).role(role).jobId(jobId)
                    .generationId(generationId).harness(harness).harnessVersion(harnessVersion).model(model)
                    .effort(effort).form(form).phase(phase).stepId(stepId).envelopeId(envelopeId)
                    .predictedCost(predictedCost).yieldReason(yieldReason).outcome(outcome).attemptEnd(attemptEnd)
                    .continuation(continuation).causeClass(causeClass).spendClass(spendClass)
                    .population(population).usage(usage)
                    .durationMs(durationMs).durationBasis(durationBasis).queuedMs(queuedMs)
                    .consultationOf(consultationOf).recordedAt(recordedAt).build();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static WorkerIdleRow readIdleRow(String row) throws IOException {
        try (var parser = StrictJson.factory().createParser(row)) {
            parser.nextToken();
            return readIdleRow(parser);
        }
    }

    private static WorkerIdleRow readIdleRow(JsonParser parser) throws IOException {
        @Nullable String kind = null;
        @Nullable String jobId = null;
        @Nullable String role = null;
        @Nullable String harness = null;
        @Nullable String harnessVersion = null;
        @Nullable String model = null;
        @Nullable String population = null;
        var usageState = new UsageState();
        @Nullable Integer idleWakeups = null;
        @Nullable Instant recordedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = string(parser, field);
                case FIELD_JOB_ID -> jobId = string(parser, field);
                case FIELD_ROLE -> role = string(parser, field);
                case FIELD_HARNESS -> harness = string(parser, field);
                case FIELD_HARNESS_VERSION -> harnessVersion = string(parser, field);
                case FIELD_MODEL -> model = string(parser, field);
                case FIELD_POPULATION -> population = string(parser, field);
                case FIELD_TOTAL_TOKENS, FIELD_INPUT_TOKENS, FIELD_OUTPUT_TOKENS, FIELD_CACHE_READ,
                    FIELD_CACHE_CREATION, FIELD_REASONING, FIELD_TOOL_USES, FIELD_COST_USD,
                    FIELD_UNMEASURED_REASON ->
                    usageState.read(parser, field);
                case FIELD_IDLE_WAKEUPS -> idleWakeups = intValue(parser, field);
                case FIELD_RECORDED_AT -> recordedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "worker-idle row", FIELD_KIND, FIELD_JOB_ID, FIELD_ROLE, FIELD_HARNESS,
                FIELD_HARNESS_VERSION, FIELD_MODEL, FIELD_POPULATION, FIELD_TOTAL_TOKENS, FIELD_INPUT_TOKENS,
                FIELD_OUTPUT_TOKENS, FIELD_CACHE_READ, FIELD_CACHE_CREATION, FIELD_REASONING, FIELD_TOOL_USES,
                FIELD_COST_USD, FIELD_UNMEASURED_REASON, FIELD_IDLE_WAKEUPS, FIELD_RECORDED_AT);
        if (!WorkerIdleRow.KIND.equals(kind) || !WorkerIdleRow.POPULATION.equals(population)) {
            throw unreadable("a worker-idle row declares the kind 'worker_idle' and its population");
        }
        var usage = usageState.build().orElse(null);
        requireCompleteUsage(usage);
        try {
            return new WorkerIdleRow(jobId, role, harness, harnessVersion, model, usage, idleWakeups, recordedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    /** The usage fields read so far; the row is accepted only when every one of them was read. */
    private static final class UsageState {

        private OptionalLong totalTokens = OptionalLong.empty();
        private Measurement inputTokens;
        private Measurement outputTokens;
        private Measurement cacheReadInputTokens;
        private Measurement cacheCreationInputTokens;
        private Measurement reasoningTokens;
        private Measurement toolUses;
        private Optional<BigDecimal> costUsd = Optional.empty();
        private Optional<UnmeasuredReason> unmeasuredReason = Optional.empty();

        void read(JsonParser parser, String field) throws IOException {
            switch (field) {
                case FIELD_TOTAL_TOKENS -> totalTokens = readTotal(parser, field);
                case FIELD_INPUT_TOKENS -> inputTokens = measurement(parser, field);
                case FIELD_OUTPUT_TOKENS -> outputTokens = measurement(parser, field);
                case FIELD_CACHE_READ -> cacheReadInputTokens = measurement(parser, field);
                case FIELD_CACHE_CREATION -> cacheCreationInputTokens = measurement(parser, field);
                case FIELD_REASONING -> reasoningTokens = measurement(parser, field);
                case FIELD_TOOL_USES -> toolUses = measurements(parser, field);
                case FIELD_COST_USD -> costUsd = cost(parser, field);
                case FIELD_UNMEASURED_REASON ->
                    unmeasuredReason = nullableEnum(parser, field, UnmeasuredReason::fromWireName);
                default -> throw undeclared(field);
            }
        }

        private static OptionalLong readTotal(JsonParser parser, String field) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                if (!UNMEASURED.equals(string(parser, field))) {
                    throw unreadable("'%s' is an integer or 'unmeasured'".formatted(field));
                }
                return OptionalLong.empty();
            }
            var value = integer(parser, field);
            if (value < 1) {
                throw unreadable("'%s' is at least 1 when measured".formatted(field));
            }
            return OptionalLong.of(value);
        }

        private static Measurement measurement(JsonParser parser, String field) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                var text = string(parser, field);
                if (UNMEASURED.equals(text)) {
                    return Measurement.unmeasured();
                }
                if (UsagePointers.NOT_APPLICABLE.equals(text)) {
                    return Measurement.notApplicable();
                }
                throw unreadable("'%s' is an integer, 'unmeasured' or 'not_applicable'".formatted(field));
            }
            var value = integer(parser, field);
            if (value < 0) {
                throw unreadable("'%s' must not be negative".formatted(field));
            }
            return Measurement.measured(value);
        }

        /** A figure of the schema's `measure`: an integer or {@code unmeasured}, never {@code not_applicable}. */
        private static Measurement measurements(JsonParser parser, String field) throws IOException {
            var measurement = measurement(parser, field);
            if (measurement.isNotApplicable()) {
                throw unreadable("'%s' is an integer or 'unmeasured', never 'not_applicable'".formatted(field));
            }
            return measurement;
        }

        private static <T> Optional<T> nullableEnum(JsonParser parser, String field,
                Function<String, Optional<T>> of) throws IOException {
            return enumValue(parser, field, of);
        }

        private static Optional<BigDecimal> cost(JsonParser parser, String field) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                if (!UNMEASURED.equals(string(parser, field))) {
                    throw unreadable("'%s' is a number or 'unmeasured'".formatted(field));
                }
                return Optional.empty();
            }
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                    && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
                throw unreadable("'%s' is a number or 'unmeasured'".formatted(field));
            }
            var value = parser.getDecimalValue();
            if (value.signum() < 0) {
                throw unreadable("'%s' must not be negative".formatted(field));
            }
            return Optional.of(value);
        }

        Optional<HarnessUsage> build() {
            if (inputTokens == null || outputTokens == null || cacheReadInputTokens == null
                    || cacheCreationInputTokens == null || reasoningTokens == null || toolUses == null) {
                return Optional.empty();
            }
            final HarnessUsage usage;
            try {
                usage = new HarnessUsage(inputTokens, outputTokens, cacheReadInputTokens, cacheCreationInputTokens,
                        reasoningTokens, toolUses, costUsd, unmeasuredReason);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
            requireDerivedTotal(totalTokens, usage);
            return Optional.of(usage);
        }

        /**
         * The total is derived from the five components, never parsed, so a stored total that does not equal the sum
         * of the components it was written from is a corrupt row and not a figure to trust.
         */
        private static void requireDerivedTotal(OptionalLong stored, HarnessUsage usage) {
            var derived = usage.totalTokens();
            if (stored.isPresent() != derived.isPresent()
                    || stored.isPresent() && stored.getAsLong() != derived.getAsLong()) {
                throw unreadable("'%s' does not equal the sum of the token components".formatted(FIELD_TOTAL_TOKENS));
            }
        }
    }

    private static void requireCompleteUsage(HarnessUsage usage) {
        if (usage == null) {
            throw unreadable("the usage columns of a row are incomplete");
        }
    }

    private static Optional<MetricsRow.PredictedCost> nullablePredictedCost(JsonParser parser, String field)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return Optional.empty();
        }
        requireObject(parser, field);
        @Nullable Long tokens = null;
        MetricsRow.PredictedCost.@Nullable Basis basis = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = nextField(parser, seen);
            switch (name) {
                case FIELD_TOKENS -> tokens = integer(parser, name);
                case FIELD_BASIS -> basis =
                        enumValue(parser, name, MetricsRow.PredictedCost.Basis::fromWireName).orElse(null);
                default -> throw undeclared(name);
            }
        }
        requireFields(seen, field, FIELD_TOKENS, FIELD_BASIS);
        if (basis == null) {
            throw unreadable("'%s.basis' is missing".formatted(field));
        }
        try {
            return Optional.of(new MetricsRow.PredictedCost(tokens, basis));
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Integer nullableInteger(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : intValue(parser, field);
    }

    /** Reads an integer that must fit an {@code int}; an out-of-range value is a refusal, never an overflow. */
    private static int intValue(JsonParser parser, String field) throws IOException {
        var value = integer(parser, field);
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException e) {
            throw unreadable("'%s' lies outside the int range: %d".formatted(field, value), e);
        }
    }

    private static Long nullableLong(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
    }

    private static String nullableString(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : string(parser, field);
    }

    private static <T> Optional<T> enumValue(JsonParser parser, String field, Function<String, Optional<T>> of)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return Optional.empty();
        }
        var name = string(parser, field);
        return of.apply(name)
                .or(() -> {
                    throw unreadable("'%s' has the undeclared value '%s'".formatted(field, name));
                });
    }
}
