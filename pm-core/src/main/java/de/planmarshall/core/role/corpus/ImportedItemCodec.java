/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static de.planmarshall.core.role.corpus.CorpusJson.construct;
import static de.planmarshall.core.role.corpus.CorpusJson.date;
import static de.planmarshall.core.role.corpus.CorpusJson.number;
import static de.planmarshall.core.role.corpus.CorpusJson.wireValue;
import static de.planmarshall.core.role.corpus.CorpusJson.writeStringList;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Input;
import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.ImportedItem.Provenance;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes an imported item (PM-WORK-6): one JSON object per file in the corpus item format, the closed
 * schema {@code imported-item.schema.json} of this package.
 * <p>
 * The writer is deterministic: the members in the order of the schema, an optional member only when it has a value.
 * The reader accepts exactly that shape: {@code schema_version} 1 checked first, then every required member exactly
 * once, an optional member at most once and never empty, and no undeclared member; a document the writer wrote reads
 * back to an equal item, and writing it again gives the same bytes. Every refusal is a {@link StoreRefusal} that names
 * the file.
 *
 * @since 0.1
 */
@UtilityClass
public class ImportedItemCodec {

    /** The {@code schema_version} of the corpus item format an imported item is in. */
    public static final int SCHEMA_VERSION = 1;

    private static final String ITEM = "item";
    private static final String FIELD_SCHEMA_VERSION = "schema_version";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TOPIC = "topic";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_GROUP = "group";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_QUESTION = "question";
    private static final String FIELD_FACTS = "facts";
    private static final String FIELD_CHOICES = "choices";
    private static final String FIELD_ANSWER_SCHEMA = "answer_schema";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_ANSWER = "answer";
    private static final String FIELD_RATIONALE = "rationale";
    private static final String FIELD_ALTERNATIVES = "alternatives";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_EXPECTED_EMPTY = "expected_empty";
    private static final String FIELD_GRADING = "grading";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_JUDGE = "judge";
    private static final String FIELD_PASS_THRESHOLD = "pass_threshold";
    private static final String FIELD_PROVENANCE = "provenance";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_ORIGIN = "origin";
    private static final String FIELD_REPOSITORY = "repository";
    private static final String FIELD_COMMIT = "commit";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_LINE = "line";
    private static final String FIELD_HISTORY = "history";
    private static final String FIELD_REVIEW = "review";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_BY = "by";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_NOTES = "notes";
    private static final String FIELD_CONTAMINATION = "contamination";
    private static final String FIELD_CANARY = "canary";

    /**
     * @param file     the item file the document was read from, named by a refusal
     * @param document an imported item, UTF-8 encoded
     * @return the item
     * @throws StoreRefusal if the document is not an imported item
     */
    public static ImportedItem read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        try {
            requireSchemaVersion(document);
            try (var parser = StrictJson.factory().createParser(document)) {
                parser.nextToken();
                var item = readDocument(parser);
                if (parser.nextToken() != null) {
                    throw unreadable("content after the document");
                }
                return item;
            }
        } catch (StrictJson.Malformed e) {
            throw StoreRefusal.unreadable(file, SCHEMA_VERSION, e.getMessage(), e.getCause());
        } catch (IOException e) {
            throw StoreRefusal.unreadable(file, SCHEMA_VERSION, describe(e), e);
        }
    }

    /**
     * @param item the item
     * @return the document, UTF-8 encoded, its members in the order of the schema
     */
    public static byte[] write(ImportedItem item) {
        Objects.requireNonNull(item, ITEM);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeNumberField(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
            generator.writeStringField(FIELD_ID, item.id());
            generator.writeStringField(FIELD_TOPIC, item.topic());
            generator.writeStringField(FIELD_ROLE, item.role());
            generator.writeStringField(FIELD_TITLE, item.title());
            writeOptionalList(generator, FIELD_TAGS, item.tags());
            writeOptionalString(generator, FIELD_GROUP, item.group());
            writeInput(generator, item.input());
            writeTarget(generator, item.target());
            writeGrading(generator, item.grading());
            writeProvenance(generator, item.provenance());
            writeReview(generator, item.review());
            writeOptionalString(generator, FIELD_CONTAMINATION, item.contamination());
            generator.writeStringField(FIELD_CANARY, item.canary());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Imported item cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeInput(JsonGenerator generator, Input input) throws IOException {
        generator.writeObjectFieldStart(FIELD_INPUT);
        generator.writeStringField(FIELD_QUESTION, input.question());
        generator.writeStringField(FIELD_FACTS, input.facts());
        if (input.choices() != null) {
            writeStringList(generator, FIELD_CHOICES, input.choices());
        }
        if (input.answerSchema() != null) {
            generator.writeFieldName(FIELD_ANSWER_SCHEMA);
            generator.writeRawValue(input.answerSchema().toJson());
        }
        generator.writeEndObject();
    }

    private static void writeTarget(JsonGenerator generator, Target target) throws IOException {
        generator.writeObjectFieldStart(FIELD_TARGET);
        writeOptionalString(generator, FIELD_ANSWER, target.answer());
        generator.writeStringField(FIELD_RATIONALE, target.rationale());
        writeOptionalList(generator, FIELD_ALTERNATIVES, target.alternatives());
        if (!target.items().isEmpty()) {
            generator.writeArrayFieldStart(FIELD_ITEMS);
            for (var item : target.items()) {
                generator.writeStartObject();
                generator.writeStringField(FIELD_ID, item.id());
                generator.writeStringField(FIELD_DESCRIPTION, item.description());
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
        if (target.expectedEmpty()) {
            generator.writeBooleanField(FIELD_EXPECTED_EMPTY, true);
        }
        generator.writeEndObject();
    }

    private static void writeGrading(JsonGenerator generator, Grading grading) throws IOException {
        generator.writeObjectFieldStart(FIELD_GRADING);
        generator.writeStringField(FIELD_METHOD, grading.method().wireName());
        writeOptionalString(generator, FIELD_JUDGE, grading.judge());
        if (grading.passThreshold() != null) {
            generator.writeNumberField(FIELD_PASS_THRESHOLD, grading.passThreshold());
        }
        generator.writeEndObject();
    }

    private static void writeProvenance(JsonGenerator generator, Provenance provenance) throws IOException {
        generator.writeObjectFieldStart(FIELD_PROVENANCE);
        generator.writeStringField(FIELD_KIND, Provenance.KIND);
        generator.writeStringField(FIELD_CREATED, provenance.created().toString());
        var origin = provenance.origin();
        generator.writeObjectFieldStart(FIELD_ORIGIN);
        generator.writeStringField(FIELD_REPOSITORY, origin.repository().toString());
        generator.writeStringField(FIELD_COMMIT, origin.commit());
        generator.writeStringField(FIELD_PATH, origin.path());
        generator.writeNumberField(FIELD_LINE, origin.line());
        generator.writeEndObject();
        writeStringList(generator, FIELD_HISTORY, provenance.history());
        generator.writeEndObject();
    }

    private static void writeReview(JsonGenerator generator, Review review) throws IOException {
        generator.writeObjectFieldStart(FIELD_REVIEW);
        generator.writeStringField(FIELD_STATUS, review.status().wireName());
        writeOptionalString(generator, FIELD_BY, review.by());
        writeOptionalString(generator, FIELD_DATE, review.date() == null ? null : review.date().toString());
        writeOptionalString(generator, FIELD_NOTES, review.notes());
        generator.writeEndObject();
    }

    private static void writeOptionalString(JsonGenerator generator, String field, String value) throws IOException {
        if (value != null) {
            generator.writeStringField(field, value);
        }
    }

    private static void writeOptionalList(JsonGenerator generator, String field, List<String> values)
            throws IOException {
        if (!values.isEmpty()) {
            writeStringList(generator, field, values);
        }
    }

    /** Finds the schema version before anything else is read: exactly one integer {@code schema_version} of 1. */
    private static void requireSchemaVersion(byte[] document) throws IOException {
        @Nullable Long found = null;
        try (var parser = StrictJson.factory().createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable("not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                var token = parser.nextToken();
                if (!FIELD_SCHEMA_VERSION.equals(field)) {
                    parser.skipChildren();
                } else if (found != null || token != JsonToken.VALUE_NUMBER_INT) {
                    throw unreadable("'schema_version' must be one integer");
                } else {
                    found = parser.getLongValue();
                }
            }
        }
        if (found == null) {
            throw unreadable("'schema_version' is missing");
        }
        if (found != SCHEMA_VERSION) {
            throw unreadable(String.format(Locale.ROOT, "'schema_version' %d is not %d", found, SCHEMA_VERSION));
        }
    }

    private static ImportedItem readDocument(JsonParser parser) throws IOException {
        requireObject(parser, ITEM);
        var values = new ItemValues();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SCHEMA_VERSION -> parser.skipChildren();
                case FIELD_ID -> values.id = string(parser, field);
                case FIELD_TOPIC -> values.topic = string(parser, field);
                case FIELD_ROLE -> values.role = string(parser, field);
                case FIELD_TITLE -> values.title = string(parser, field);
                case FIELD_TAGS -> values.tags = nonEmptyStringList(parser, field);
                case FIELD_GROUP -> values.group = string(parser, field);
                case FIELD_INPUT -> values.input = readInput(parser);
                case FIELD_TARGET -> values.target = readTarget(parser);
                case FIELD_GRADING -> values.grading = readGrading(parser);
                case FIELD_PROVENANCE -> values.provenance = readProvenance(parser);
                case FIELD_REVIEW -> values.review = readReview(parser);
                case FIELD_CONTAMINATION -> values.contamination = string(parser, field);
                case FIELD_CANARY -> values.canary = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, ITEM, FIELD_SCHEMA_VERSION, FIELD_ID, FIELD_TOPIC, FIELD_ROLE, FIELD_TITLE, FIELD_INPUT,
                FIELD_TARGET, FIELD_GRADING, FIELD_PROVENANCE, FIELD_REVIEW, FIELD_CANARY);
        return construct(() -> new ImportedItem(values.id, values.topic, values.role, values.title, values.tags,
                values.group, values.input, values.target, values.grading, values.provenance, values.review,
                values.contamination, values.canary));
    }

    private static Input readInput(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_INPUT);
        @Nullable String question = null;
        @Nullable String facts = null;
        @Nullable List<String> choices = null;
        @Nullable JsonValue answerSchema = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_QUESTION -> question = string(parser, field);
                case FIELD_FACTS -> facts = string(parser, field);
                case FIELD_CHOICES -> choices = stringList(parser, field);
                case FIELD_ANSWER_SCHEMA -> answerSchema = nonEmptyValue(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_INPUT, FIELD_QUESTION, FIELD_FACTS);
        var inputQuestion = question;
        var inputFacts = facts;
        var inputChoices = choices;
        var inputAnswerSchema = answerSchema;
        return construct(() -> new Input(inputQuestion, inputFacts, inputChoices, inputAnswerSchema));
    }

    private static Target readTarget(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_TARGET);
        @Nullable String answer = null;
        @Nullable String rationale = null;
        List<String> alternatives = List.of();
        List<RubricItem> items = List.of();
        var expectedEmpty = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ANSWER -> answer = string(parser, field);
                case FIELD_RATIONALE -> rationale = string(parser, field);
                case FIELD_ALTERNATIVES -> alternatives = nonEmptyStringList(parser, field);
                case FIELD_ITEMS -> {
                    items = array(parser, field, ImportedItemCodec::readRubricItem);
                    if (items.isEmpty()) {
                        throw unreadable("'items' is present but empty");
                    }
                }
                case FIELD_EXPECTED_EMPTY -> {
                    if (!bool(parser, field)) {
                        throw unreadable("'expected_empty' is present but not true");
                    }
                    expectedEmpty = true;
                }
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_TARGET, FIELD_RATIONALE);
        return new Target(answer, rationale, alternatives, items, expectedEmpty);
    }

    private static RubricItem readRubricItem(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String id = null;
        @Nullable String description = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ID -> id = string(parser, field);
                case FIELD_DESCRIPTION -> description = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_ID, FIELD_DESCRIPTION);
        return new RubricItem(id, description);
    }

    private static Grading readGrading(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_GRADING);
        @Nullable GradingMethod method = null;
        @Nullable String judge = null;
        @Nullable BigDecimal passThreshold = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_METHOD -> method = wireValue(parser, field, GradingMethod::fromWireName);
                case FIELD_JUDGE -> judge = string(parser, field);
                case FIELD_PASS_THRESHOLD -> passThreshold = number(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_GRADING, FIELD_METHOD);
        var gradingMethod = method;
        var gradingJudge = judge;
        var gradingThreshold = passThreshold;
        return construct(() -> new Grading(gradingMethod, gradingJudge, gradingThreshold));
    }

    private static Provenance readProvenance(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_PROVENANCE);
        @Nullable LocalDate created = null;
        @Nullable Origin origin = null;
        @Nullable List<String> history = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> {
                    var kind = string(parser, field);
                    if (!Provenance.KIND.equals(kind)) {
                        throw unreadable("'kind' must be '%s': %s".formatted(Provenance.KIND, kind));
                    }
                }
                case FIELD_CREATED -> created = date(parser, field);
                case FIELD_ORIGIN -> origin = readOrigin(parser);
                case FIELD_HISTORY -> history = stringList(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_PROVENANCE, FIELD_KIND, FIELD_CREATED, FIELD_ORIGIN, FIELD_HISTORY);
        var provenanceCreated = created;
        var provenanceOrigin = origin;
        var provenanceHistory = history;
        return construct(() -> new Provenance(provenanceCreated, provenanceOrigin, provenanceHistory));
    }

    private static Origin readOrigin(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_ORIGIN);
        @Nullable String repository = null;
        @Nullable String commit = null;
        @Nullable String path = null;
        long line = 0;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_REPOSITORY -> repository = string(parser, field);
                case FIELD_COMMIT -> commit = string(parser, field);
                case FIELD_PATH -> path = string(parser, field);
                case FIELD_LINE -> line = integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_ORIGIN, FIELD_REPOSITORY, FIELD_COMMIT, FIELD_PATH, FIELD_LINE);
        // Checked on the long, before the cast: a line below 1 or beyond int would otherwise wrap into the int range.
        if (line < 1 || line > Integer.MAX_VALUE) {
            throw unreadable("'line' is outside 1 to " + Integer.MAX_VALUE + ": " + line);
        }
        var originRepository = repository;
        var originCommit = commit;
        var originPath = path;
        var originLine = (int) line;
        return construct(() -> new Origin(URI.create(originRepository), originCommit, originPath, originLine));
    }

    private static Review readReview(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_REVIEW);
        @Nullable ReviewStatus status = null;
        @Nullable String by = null;
        @Nullable LocalDate date = null;
        @Nullable String notes = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_STATUS -> status = wireValue(parser, field, ReviewStatus::fromWireName);
                case FIELD_BY -> by = string(parser, field);
                case FIELD_DATE -> date = date(parser, field);
                case FIELD_NOTES -> notes = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_REVIEW, FIELD_STATUS);
        return new Review(status, by, date, notes);
    }

    private static List<String> nonEmptyStringList(JsonParser parser, String field) throws IOException {
        var values = stringList(parser, field);
        if (values.isEmpty()) {
            throw unreadable("'%s' is present but empty".formatted(field));
        }
        return values;
    }

    private static JsonValue nonEmptyValue(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            throw unreadable("'%s' is present but empty".formatted(field));
        }
        JsonValue value;
        try {
            value = JsonValue.read(parser);
        } catch (MalformedJson e) {
            throw unreadable("'%s' is not one JSON value: %s".formatted(field, e.getMessage()), e);
        }
        if (!(value instanceof JsonValue.JsonObject)) {
            throw unreadable("'%s' must be an object or null".formatted(field));
        }
        return value;
    }

    /** Names where the document stops being JSON, without the source excerpt the parser may append. */
    private static String describe(IOException e) {
        if (e instanceof JsonProcessingException malformed && malformed.getLocation() != null) {
            return String.format(Locale.ROOT, "%s (line %d, column %d)", malformed.getOriginalMessage(),
                    malformed.getLocation().getLineNr(), malformed.getLocation().getColumnNr());
        }
        return String.valueOf(e.getMessage());
    }

    /** The values of an item read so far. */
    private static final class ItemValues {
        private String id;
        private String topic;
        private String role;
        private String title;
        private List<String> tags = List.of();
        private String group;
        private Input input;
        private Target target;
        private Grading grading;
        private Provenance provenance;
        private Review review;
        private String contamination;
        private String canary;
    }
}
