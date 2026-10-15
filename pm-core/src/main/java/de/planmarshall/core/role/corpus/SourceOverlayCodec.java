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
import static de.planmarshall.core.role.corpus.CorpusJson.nullableDate;
import static de.planmarshall.core.role.corpus.CorpusJson.nullableNumber;
import static de.planmarshall.core.role.corpus.CorpusJson.nullableStringList;
import static de.planmarshall.core.role.corpus.CorpusJson.wireValue;
import static de.planmarshall.core.role.corpus.CorpusJson.writeNullableString;
import static de.planmarshall.core.role.corpus.CorpusJson.writeStringList;
import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes the overlay of a corpus source, {@code sources/<name>/overlay.json} (PM-WORK-6), the closed schema
 * {@code source-overlay.schema.json} of this package.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the current {@code format_version} first, then
 * every declared field exactly once and no undeclared field; a value the overlay does not give is a JSON
 * {@code null} or an empty array, never an absent field. Every refusal is a {@link StoreRefusal} that names the file.
 *
 * @since 0.1
 */
@UtilityClass
public class SourceOverlayCodec {

    /** The format version of a source overlay that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String OVERLAY = "overlay";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_FILE = "file";
    private static final String FIELD_KEY_FIELD = "key_field";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_ROWS = "rows";
    private static final String FIELD_KEY = "key";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TOPIC = "topic";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_TAGS = "tags";
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
    private static final String FIELD_REVIEW = "review";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_BY = "by";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_NOTES = "notes";

    /**
     * @param file     the overlay file the document was read from, named by a refusal
     * @param document a source overlay, UTF-8 encoded
     * @return the overlay
     * @throws StoreRefusal if the document declares another format version or is not an overlay
     */
    public static SourceOverlay read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, SourceOverlayCodec::readDocument);
    }

    /**
     * @param overlay the overlay
     * @return the document, UTF-8 encoded, its members in the order of the schema
     */
    public static byte[] write(SourceOverlay overlay) {
        Objects.requireNonNull(overlay, OVERLAY);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeStringField(FIELD_SOURCE, overlay.source());
            generator.writeStringField(FIELD_FILE, overlay.file());
            writeNullableString(generator, FIELD_KEY_FIELD, overlay.keyField());
            generator.writeStringField(FIELD_CREATED, overlay.created().toString());
            generator.writeArrayFieldStart(FIELD_ROWS);
            for (var row : overlay.rows()) {
                writeRow(generator, row);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Source overlay cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeRow(JsonGenerator generator, Row row) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KEY, row.key());
        generator.writeStringField(FIELD_ID, row.slug());
        generator.writeStringField(FIELD_TOPIC, row.topic());
        generator.writeStringField(FIELD_ROLE, row.role());
        generator.writeStringField(FIELD_TITLE, row.title());
        writeStringList(generator, FIELD_TAGS, row.tags());
        generator.writeStringField(FIELD_QUESTION, row.question());
        generator.writeStringField(FIELD_FACTS, row.facts());
        if (row.choices() == null) {
            generator.writeNullField(FIELD_CHOICES);
        } else {
            writeStringList(generator, FIELD_CHOICES, row.choices());
        }
        if (row.answerSchema() == null) {
            generator.writeNullField(FIELD_ANSWER_SCHEMA);
        } else {
            generator.writeFieldName(FIELD_ANSWER_SCHEMA);
            generator.writeRawValue(row.answerSchema().toJson());
        }
        writeTarget(generator, row.target());
        writeGrading(generator, row.grading());
        writeReview(generator, row.review());
        generator.writeEndObject();
    }

    private static void writeTarget(JsonGenerator generator, Target target) throws IOException {
        generator.writeObjectFieldStart(FIELD_TARGET);
        writeNullableString(generator, FIELD_ANSWER, target.answer());
        generator.writeStringField(FIELD_RATIONALE, target.rationale());
        writeStringList(generator, FIELD_ALTERNATIVES, target.alternatives());
        generator.writeArrayFieldStart(FIELD_ITEMS);
        for (var item : target.items()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_ID, item.id());
            generator.writeStringField(FIELD_DESCRIPTION, item.description());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeBooleanField(FIELD_EXPECTED_EMPTY, target.expectedEmpty());
        generator.writeEndObject();
    }

    private static void writeGrading(JsonGenerator generator, Grading grading) throws IOException {
        generator.writeObjectFieldStart(FIELD_GRADING);
        generator.writeStringField(FIELD_METHOD, grading.method().wireName());
        writeNullableString(generator, FIELD_JUDGE, grading.judge());
        if (grading.passThreshold() == null) {
            generator.writeNullField(FIELD_PASS_THRESHOLD);
        } else {
            generator.writeNumberField(FIELD_PASS_THRESHOLD, grading.passThreshold());
        }
        generator.writeEndObject();
    }

    private static void writeReview(JsonGenerator generator, Review review) throws IOException {
        generator.writeObjectFieldStart(FIELD_REVIEW);
        generator.writeStringField(FIELD_STATUS, review.status().wireName());
        writeNullableString(generator, FIELD_BY, review.by());
        writeNullableString(generator, FIELD_DATE, review.date() == null ? null : review.date().toString());
        writeNullableString(generator, FIELD_NOTES, review.notes());
        generator.writeEndObject();
    }

    private static SourceOverlay readDocument(JsonParser parser) throws IOException {
        requireObject(parser, OVERLAY);
        String source = null;
        String file = null;
        String keyField = null;
        LocalDate created = null;
        List<Row> rows = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_SOURCE -> source = string(parser, field);
                case FIELD_FILE -> file = string(parser, field);
                case FIELD_KEY_FIELD -> keyField = nullableString(parser, field);
                case FIELD_CREATED -> created = date(parser, field);
                case FIELD_ROWS -> rows = array(parser, field, SourceOverlayCodec::readRow);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, OVERLAY, FIELD_SOURCE, FIELD_FILE, FIELD_KEY_FIELD, FIELD_CREATED, FIELD_ROWS);
        var overlaySource = source;
        var overlayFile = file;
        var overlayKeyField = keyField;
        var overlayCreated = created;
        var overlayRows = rows;
        return construct(
                () -> new SourceOverlay(overlaySource, overlayFile, overlayKeyField, overlayCreated, overlayRows));
    }

    private static Row readRow(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var values = new RowValues();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KEY -> values.key = string(parser, field);
                case FIELD_ID -> values.slug = string(parser, field);
                case FIELD_TOPIC -> values.topic = string(parser, field);
                case FIELD_ROLE -> values.role = string(parser, field);
                case FIELD_TITLE -> values.title = string(parser, field);
                case FIELD_TAGS -> values.tags = stringList(parser, field);
                case FIELD_QUESTION -> values.question = string(parser, field);
                case FIELD_FACTS -> values.facts = string(parser, field);
                case FIELD_CHOICES -> values.choices = nullableStringList(parser, field);
                case FIELD_ANSWER_SCHEMA -> values.answerSchema = nullableValue(parser);
                case FIELD_TARGET -> values.target = readTarget(parser);
                case FIELD_GRADING -> values.grading = readGrading(parser);
                case FIELD_REVIEW -> values.review = readReview(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_KEY, FIELD_ID, FIELD_TOPIC, FIELD_ROLE, FIELD_TITLE, FIELD_TAGS,
                FIELD_QUESTION, FIELD_FACTS, FIELD_CHOICES, FIELD_ANSWER_SCHEMA, FIELD_TARGET, FIELD_GRADING,
                FIELD_REVIEW);
        return construct(() -> new Row(values.key, values.slug, values.topic, values.role, values.title, values.tags,
                values.question, values.facts, values.choices, values.answerSchema, values.target, values.grading,
                values.review));
    }

    private static JsonValue nullableValue(JsonParser parser) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : JsonValue.read(parser);
    }

    private static Target readTarget(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_TARGET);
        String answer = null;
        String rationale = null;
        List<String> alternatives = null;
        List<RubricItem> items = null;
        var expectedEmpty = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_ANSWER -> answer = nullableString(parser, field);
                case FIELD_RATIONALE -> rationale = string(parser, field);
                case FIELD_ALTERNATIVES -> alternatives = stringList(parser, field);
                case FIELD_ITEMS -> items = array(parser, field, SourceOverlayCodec::readRubricItem);
                case FIELD_EXPECTED_EMPTY -> expectedEmpty = bool(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_TARGET, FIELD_ANSWER, FIELD_RATIONALE, FIELD_ALTERNATIVES, FIELD_ITEMS,
                FIELD_EXPECTED_EMPTY);
        var targetAnswer = answer;
        var targetRationale = rationale;
        var targetAlternatives = alternatives;
        var targetItems = items;
        var targetEmpty = expectedEmpty;
        return construct(
                () -> new Target(targetAnswer, targetRationale, targetAlternatives, targetItems, targetEmpty));
    }

    private static RubricItem readRubricItem(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String id = null;
        String description = null;
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
        GradingMethod method = null;
        String judge = null;
        BigDecimal passThreshold = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_METHOD -> method = wireValue(parser, field, GradingMethod::fromWireName);
                case FIELD_JUDGE -> judge = nullableString(parser, field);
                case FIELD_PASS_THRESHOLD -> passThreshold = nullableNumber(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_GRADING, FIELD_METHOD, FIELD_JUDGE, FIELD_PASS_THRESHOLD);
        var gradingMethod = method;
        var gradingJudge = judge;
        var gradingThreshold = passThreshold;
        return construct(() -> new Grading(gradingMethod, gradingJudge, gradingThreshold));
    }

    private static Review readReview(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_REVIEW);
        ReviewStatus status = null;
        String by = null;
        LocalDate date = null;
        String notes = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_STATUS -> status = wireValue(parser, field, ReviewStatus::fromWireName);
                case FIELD_BY -> by = nullableString(parser, field);
                case FIELD_DATE -> date = nullableDate(parser, field);
                case FIELD_NOTES -> notes = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_REVIEW, FIELD_STATUS, FIELD_BY, FIELD_DATE, FIELD_NOTES);
        return new Review(status, by, date, notes);
    }

    /** The values of a row read so far. */
    private static final class RowValues {
        private String key;
        private String slug;
        private String topic;
        private String role;
        private String title;
        private List<String> tags;
        private String question;
        private String facts;
        private List<String> choices;
        private JsonValue answerSchema;
        private Target target;
        private Grading grading;
        private Review review;
    }
}
