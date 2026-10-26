/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.qualification.JudgeCheck.ItemScore;
import de.planmarshall.core.role.qualification.JudgeCheck.Label;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes a judge check record (Store Schemas § Judge Check Record) in exactly one shape, as
 * {@link QualificationRecordCodec} does for a qualification record. Whatever does not parse is a {@link StoreRefusal}
 * naming the record file.
 *
 * @since 0.1
 */
@UtilityClass
public class JudgeCheckCodec {

    /** The member that carries the format version. */
    static final String FORMAT_VERSION = "format_version";

    private static final String QUALIFICATION_DIGEST = "qualification_digest";
    private static final String HARNESS = "harness";
    private static final String MODEL = "model";
    private static final String CORPUS_REVISION = "corpus_revision";
    private static final String ORACLE_ACCURACY = "oracle_accuracy";
    private static final String BLIND_ACCURACY = "blind_accuracy";
    private static final String CONSTANT_BASELINE = "constant_baseline";
    private static final String UNSCORED = "unscored";
    private static final String CHECKED_AT = "checked_at";
    private static final String ITEMS = "items";

    private static final String ITEM_ID = "item_id";
    private static final String LABEL = "label";
    private static final String ORACLE_SCORE = "oracle_score";
    private static final String BLIND_SCORE = "blind_score";
    private static final String TOTAL_TOKENS = "total_tokens";
    private static final String DURATION_MS = "duration_ms";

    /** The members of a judge check record, in the order of its schema. */
    static final List<String> MEMBERS = List.of(FORMAT_VERSION, QUALIFICATION_DIGEST, HARNESS, MODEL, CORPUS_REVISION,
            ORACLE_ACCURACY, BLIND_ACCURACY, CONSTANT_BASELINE, UNSCORED, CHECKED_AT, ITEMS);

    /** The members of an item score, in the order of its schema. */
    static final List<String> ITEM_MEMBERS = List.of(ITEM_ID, LABEL, ORACLE_SCORE, BLIND_SCORE, TOTAL_TOKENS,
            DURATION_MS);

    /**
     * @param entry a judge check record
     * @return the document, in its one canonical text
     */
    public static byte[] write(JudgeCheck.Record entry) {
        Objects.requireNonNull(entry, "entry");
        return CanonicalJson.write(document(entry)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param file     the record file, named by a refusal
     * @param document the document
     * @return the record
     * @throws StoreRefusal if the document does not have the shape of a judge check record
     */
    public static JudgeCheck.Record read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, JudgeCheck.FORMAT_VERSION,
                JudgeCheckCodec::readRecord);
    }

    private static JudgeCheck.Record readRecord(JsonParser parser) throws IOException {
        requireObject(parser, "judge check record");
        var seen = new HashSet<String>();
        String digest = null;
        String harness = null;
        String model = null;
        String corpusRevision = null;
        BigDecimal oracle = null;
        BigDecimal blind = null;
        BigDecimal baseline = null;
        int unscored = -1;
        Instant checkedAt = null;
        List<ItemScore> items = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FORMAT_VERSION -> version(parser, field);
                case QUALIFICATION_DIGEST -> digest = string(parser, field);
                case HARNESS -> harness = string(parser, field);
                case MODEL -> model = string(parser, field);
                case CORPUS_REVISION -> corpusRevision = string(parser, field);
                case ORACLE_ACCURACY -> oracle = decimal(parser, field);
                case BLIND_ACCURACY -> blind = decimal(parser, field);
                case CONSTANT_BASELINE -> baseline = decimal(parser, field);
                case UNSCORED -> unscored = count(parser, field);
                case CHECKED_AT -> checkedAt = instant(parser, field);
                case ITEMS -> items = StrictJson.array(parser, field, JudgeCheckCodec::readItem);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "judge check record", MEMBERS.toArray(String[]::new));
        try {
            return new JudgeCheck.Record(digest, harness, model, corpusRevision, oracle, blind, baseline, unscored,
                    checkedAt, items);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static ItemScore readItem(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        String itemId = null;
        Label label = null;
        Grade oracleScore = null;
        Grade blindScore = null;
        TokenTotal totalTokens = null;
        Long duration = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case ITEM_ID -> itemId = string(parser, field);
                case LABEL -> label = closed(parser, field, Label::fromWireName);
                case ORACLE_SCORE -> oracleScore = grade(parser, field);
                case BLIND_SCORE -> blindScore = grade(parser, field);
                case TOTAL_TOKENS -> totalTokens = tokens(parser, field);
                case DURATION_MS -> duration = parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, ITEM_MEMBERS.toArray(String[]::new));
        try {
            return new ItemScore(itemId, label, oracleScore, blindScore, totalTokens, duration);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Grade grade(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            var text = string(parser, field);
            if (UNSCORED.equals(text)) {
                return Grade.ungraded();
            }
            throw unreadable("'%s' is a score or 'unscored': %s".formatted(field, text));
        }
        return Grade.scored(decimal(parser, field));
    }

    private static TokenTotal tokens(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            var text = string(parser, field);
            if (TokenTotal.UNMEASURED_MARK.equals(text)) {
                return TokenTotal.unmeasured();
            }
            throw unreadable("'%s' is a count or '%s': %s".formatted(field, TokenTotal.UNMEASURED_MARK, text));
        }
        var value = integer(parser, field);
        if (value < 1) {
            throw unreadable("'%s' must be at least 1: %d".formatted(field, value));
        }
        return TokenTotal.measured(value);
    }

    private static int count(JsonParser parser, String field) throws IOException {
        var value = integer(parser, field);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw unreadable("'%s' is a count: %d".formatted(field, value));
        }
        return (int) value;
    }

    private static void version(JsonParser parser, String field) throws IOException {
        var value = integer(parser, field);
        if (value != JudgeCheck.FORMAT_VERSION) {
            throw unreadable("'%s' must be %d: %d".formatted(field, JudgeCheck.FORMAT_VERSION, value));
        }
    }

    private static BigDecimal decimal(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
            throw unreadable("'%s' must be a number".formatted(field));
        }
        return parser.getDecimalValue();
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    private static JsonNode document(JudgeCheck.Record entry) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(FORMAT_VERSION, JsonNode.integer(JudgeCheck.FORMAT_VERSION));
        members.put(QUALIFICATION_DIGEST, JsonNode.string(entry.qualificationDigest()));
        members.put(HARNESS, JsonNode.string(entry.harness()));
        members.put(MODEL, JsonNode.string(entry.model()));
        members.put(CORPUS_REVISION, JsonNode.string(entry.corpusRevision()));
        members.put(ORACLE_ACCURACY, JsonNode.decimal(entry.oracleAccuracy()));
        members.put(BLIND_ACCURACY, JsonNode.decimal(entry.blindAccuracy()));
        members.put(CONSTANT_BASELINE, JsonNode.decimal(entry.constantBaseline()));
        members.put(UNSCORED, JsonNode.integer(entry.unscored()));
        members.put(CHECKED_AT, JsonNode.string(entry.checkedAt().toString()));
        members.put(ITEMS, JsonNode.array(entry.items().stream().map(JudgeCheckCodec::item).toList()));
        return JsonNode.object(members);
    }

    private static JsonNode item(ItemScore score) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(ITEM_ID, JsonNode.string(score.itemId()));
        members.put(LABEL, JsonNode.string(score.label().wireName()));
        members.put(ORACLE_SCORE, grade(score.oracleScore()));
        members.put(BLIND_SCORE, grade(score.blindScore()));
        members.put(TOTAL_TOKENS, score.totalTokens().measured()
                ? JsonNode.integer(score.totalTokens().tokens())
                : JsonNode.string(TokenTotal.UNMEASURED_MARK));
        members.put(DURATION_MS, score.durationMs() == null
                ? JsonNode.nullValue()
                : JsonNode.integer(score.durationMs()));
        return JsonNode.object(members);
    }

    private static JsonNode grade(Grade grade) {
        return grade.isUngraded() ? JsonNode.string(UNSCORED) : JsonNode.decimal(grade.score());
    }
}
