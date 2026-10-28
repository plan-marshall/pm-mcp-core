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
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.qualification.QualificationRecord.FormResult;
import de.planmarshall.core.role.qualification.QualificationRecord.ItemResult;
import de.planmarshall.core.role.qualification.QualificationRecord.TopicResult;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes a qualification record (Store Schemas § Role Qualification Record) in exactly one shape: only the
 * declared members, each at most once, every required member, one JSON type per member, and the closed values of every
 * enumeration and pattern. Whatever does not parse is a {@link StoreRefusal} naming the record file.
 * <p>
 * The writer emits the document through {@link CanonicalJson}, so one record has one text.
 *
 * @since 0.1
 */
@UtilityClass
public class QualificationRecordCodec {

    /** The member that carries the format version. */
    static final String FORMAT_VERSION = "format_version";

    /** The one word an item's score writes instead of a score. */
    static final String UNGRADED = "ungraded";

    private static final String ROLE = "role";
    private static final String QUALIFICATION_DIGEST = "qualification_digest";
    private static final String CONFIGURATION = "configuration";
    private static final String CORPUS_REVISION = "corpus_revision";
    private static final String MISSED_SOURCES = "missed_sources";
    private static final String FORMS = "forms";
    private static final String WARM_FRESH_GAP = "warm_fresh_gap";
    private static final String GRADED_AT = "graded_at";
    private static final String ITEMS = "items";

    private static final String HARNESS = "harness";
    private static final String HARNESS_VERSION = "harness_version";
    private static final String MODEL = "model";
    private static final String EFFORT = "effort";
    private static final String TOOLS = "tools";
    private static final String CLASS = "class";
    private static final String SKILL_DIGESTS = "skill_digests";

    private static final String TOPICS = "topics";
    private static final String FORM_ITEMS = "items";
    private static final String TOTAL_TOKENS = "total_tokens";
    private static final String TOKENS_IN = "tokens_in";
    private static final String TOKENS_OUT = "tokens_out";
    private static final String DURATION_MS = "duration_ms";

    private static final String AGREEMENT = "agreement";
    private static final String CONSTANT_BASELINE = "constant_baseline";

    private static final String ITEM_ID = "item_id";
    private static final String FORM = "form";
    private static final String CHECKOUT = "checkout";
    private static final String ANSWER = "answer";
    private static final String SCORE = "score";

    /** The members of a record, in the order of its schema. */
    static final List<String> MEMBERS = List.of(FORMAT_VERSION, ROLE, QUALIFICATION_DIGEST, CONFIGURATION,
            CORPUS_REVISION, MISSED_SOURCES, FORMS, WARM_FRESH_GAP, UNGRADED, GRADED_AT, ITEMS);

    /** The members of the configuration block, in the order of its schema. */
    static final List<String> CONFIGURATION_MEMBERS = List.of(HARNESS, HARNESS_VERSION, MODEL, EFFORT, TOOLS, CLASS,
            SKILL_DIGESTS);

    /** The members of a form result, in the order of its schema. */
    static final List<String> FORM_MEMBERS = List.of(TOPICS, FORM_ITEMS, TOTAL_TOKENS, TOKENS_IN, TOKENS_OUT,
            DURATION_MS);

    /** The members of a topic result, in the order of its schema. */
    static final List<String> TOPIC_MEMBERS = List.of(AGREEMENT, CONSTANT_BASELINE, FORM_ITEMS);

    /** The members of an item result, in the order of its schema. */
    static final List<String> ITEM_MEMBERS = List.of(ITEM_ID, FORM, CHECKOUT, ANSWER, SCORE, TOTAL_TOKENS, TOKENS_IN,
            TOKENS_OUT, DURATION_MS);

    /**
     * @param entry a qualification record
     * @return the document, in its one canonical text
     */
    public static byte[] write(QualificationRecord entry) {
        Objects.requireNonNull(entry, "entry");
        return CanonicalJson.write(document(entry)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param file     the record file, named by a refusal
     * @param document the document
     * @return the record
     * @throws StoreRefusal if the document does not have the shape of a qualification record
     */
    public static QualificationRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, QualificationRecord.FORMAT_VERSION,
                parser -> readRecord(parser));
    }

    private static QualificationRecord readRecord(JsonParser parser) throws IOException {
        requireObject(parser, "qualification record");
        var seen = new HashSet<String>();
        String role = null;
        String digest = null;
        GradedConfiguration configuration = null;
        String corpusRevision = null;
        List<String> missedSources = null;
        Map<RoleForm, FormResult> forms = null;
        BigDecimal gap = null;
        int ungraded = -1;
        Instant gradedAt = null;
        List<ItemResult> items = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FORMAT_VERSION -> version(parser, field);
                case ROLE -> role = string(parser, field);
                case QUALIFICATION_DIGEST -> digest = string(parser, field);
                case CONFIGURATION -> configuration = readConfiguration(parser);
                case CORPUS_REVISION -> corpusRevision = string(parser, field);
                case MISSED_SOURCES -> missedSources = stringList(parser, field);
                case FORMS -> forms = readForms(parser);
                case WARM_FRESH_GAP -> gap = nullableDecimal(parser, field);
                case UNGRADED -> ungraded = count(parser, field);
                case GRADED_AT -> gradedAt = instant(parser, field);
                case ITEMS -> items = StrictJson.array(parser, field, QualificationRecordCodec::readItem);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "qualification record", MEMBERS.toArray(String[]::new));
        try {
            return new QualificationRecord(role, digest, configuration, corpusRevision, missedSources, forms, gap,
                    ungraded, gradedAt, items);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static GradedConfiguration readConfiguration(JsonParser parser) throws IOException {
        requireObject(parser, CONFIGURATION);
        var seen = new HashSet<String>();
        String harness = null;
        String harnessVersion = null;
        String model = null;
        String effort = null;
        List<String> tools = null;
        RoleClass roleClass = null;
        Map<String, String> skillDigests = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case HARNESS -> harness = string(parser, field);
                case HARNESS_VERSION -> harnessVersion = string(parser, field);
                case MODEL -> model = string(parser, field);
                case EFFORT -> effort = nullableString(parser, field);
                case TOOLS -> tools = stringList(parser, field);
                case CLASS -> roleClass = closed(parser, field, RoleClass::fromWireName);
                case SKILL_DIGESTS -> skillDigests = StrictJson.map(parser, field, StrictJson::string);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, CONFIGURATION, CONFIGURATION_MEMBERS.toArray(String[]::new));
        try {
            return new GradedConfiguration(harness, harnessVersion, model, effort, tools, roleClass, skillDigests);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Map<RoleForm, FormResult> readForms(JsonParser parser) throws IOException {
        requireObject(parser, FORMS);
        var forms = new EnumMap<RoleForm, FormResult>(RoleForm.class);
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            forms.put(closedName(field, RoleForm::fromWireName), readForm(parser));
        }
        if (!forms.containsKey(RoleForm.FRESH)) {
            throw unreadable("'forms' must hold 'fresh'");
        }
        return Collections.unmodifiableMap(forms);
    }

    private static FormResult readForm(JsonParser parser) throws IOException {
        requireObject(parser, "form");
        var seen = new HashSet<String>();
        Map<Topic, TopicResult> topics = null;
        int items = -1;
        TokenTotal totalTokens = null;
        Long tokensIn = null;
        Long tokensOut = null;
        long duration = -1;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case TOPICS -> topics = readTopics(parser);
                case FORM_ITEMS -> items = count(parser, field);
                case TOTAL_TOKENS -> totalTokens = readTokens(parser, field);
                case TOKENS_IN -> tokensIn = readSplit(parser, field);
                case TOKENS_OUT -> tokensOut = readSplit(parser, field);
                case DURATION_MS -> duration = integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "form", FORM_MEMBERS.toArray(String[]::new));
        try {
            return new FormResult(topics, items, withSplit(totalTokens, tokensIn, tokensOut), duration);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Map<Topic, TopicResult> readTopics(JsonParser parser) throws IOException {
        requireObject(parser, TOPICS);
        var topics = new EnumMap<Topic, TopicResult>(Topic.class);
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            topics.put(closedName(field, Topic::fromWireName), readTopic(parser));
        }
        if (topics.isEmpty()) {
            throw unreadable("'topics' must hold at least one topic");
        }
        return Collections.unmodifiableMap(topics);
    }

    private static TopicResult readTopic(JsonParser parser) throws IOException {
        requireObject(parser, "topic");
        var seen = new HashSet<String>();
        BigDecimal agreement = null;
        BigDecimal baseline = null;
        int items = -1;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case AGREEMENT -> agreement = decimal(parser, field);
                case CONSTANT_BASELINE -> baseline = decimal(parser, field);
                case FORM_ITEMS -> items = count(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "topic", TOPIC_MEMBERS.toArray(String[]::new));
        try {
            return new TopicResult(agreement, baseline, items);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static ItemResult readItem(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        String itemId = null;
        RoleForm form = null;
        Checkout checkout = null;
        JsonNode answer = null;
        Grade score = null;
        TokenTotal totalTokens = null;
        Long tokensIn = null;
        Long tokensOut = null;
        Long duration = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case ITEM_ID -> itemId = string(parser, field);
                case FORM -> form = closed(parser, field, RoleForm::fromWireName);
                case CHECKOUT -> checkout = closed(parser, field, Checkout::fromWireName);
                case ANSWER -> answer = value(parser);
                case SCORE -> score = readGrade(parser, field);
                case TOTAL_TOKENS -> totalTokens = readTokens(parser, field);
                case TOKENS_IN -> tokensIn = readSplit(parser, field);
                case TOKENS_OUT -> tokensOut = readSplit(parser, field);
                case DURATION_MS -> duration = nullableInteger(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, ITEM_MEMBERS.toArray(String[]::new));
        try {
            return new ItemResult(itemId, form, checkout, answer, score,
                    withSplit(totalTokens, tokensIn, tokensOut), duration);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    /**
     * @param declared the {@code total_tokens} member
     * @param tokensIn the {@code tokens_in} member; {@code null} when it is {@code unmeasured}
     * @param tokensOut the {@code tokens_out} member; {@code null} when it is {@code unmeasured}
     * @return the total with its split
     */
    private static TokenTotal withSplit(TokenTotal declared, Long tokensIn, Long tokensOut) {
        return new TokenTotal(declared.measured() ? declared.tokens() : null, tokensIn, tokensOut);
    }

    private static Long readSplit(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            var text = string(parser, field);
            if (TokenTotal.UNMEASURED_MARK.equals(text)) {
                return null;
            }
            throw unreadable("'%s' is a count or '%s': %s".formatted(field, TokenTotal.UNMEASURED_MARK, text));
        }
        var value = integer(parser, field);
        if (value < 0) {
            throw unreadable("'%s' must not be negative: %d".formatted(field, value));
        }
        return value;
    }

    private static Grade readGrade(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            var text = string(parser, field);
            if (UNGRADED.equals(text)) {
                return Grade.ungraded();
            }
            throw unreadable("'%s' is a score or 'ungraded': %s".formatted(field, text));
        }
        return Grade.scored(decimal(parser, field));
    }

    private static TokenTotal readTokens(JsonParser parser, String field) throws IOException {
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

    private static JsonNode value(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        return switch (token) {
            case VALUE_NULL -> JsonNode.nullValue();
            case VALUE_STRING -> JsonNode.string(parser.getText());
            case VALUE_TRUE -> JsonNode.bool(true);
            case VALUE_FALSE -> JsonNode.bool(false);
            case VALUE_NUMBER_INT -> JsonNode.integer(parser.getLongValue());
            case VALUE_NUMBER_FLOAT -> JsonNode.decimal(parser.getDecimalValue());
            case START_ARRAY -> arrayValue(parser);
            case START_OBJECT -> objectValue(parser);
            default -> throw unreadable("Not a JSON value: " + token);
        };
    }

    private static JsonNode arrayValue(JsonParser parser) throws IOException {
        var elements = new ArrayList<JsonNode>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(value(parser));
        }
        return JsonNode.array(elements);
    }

    private static JsonNode objectValue(JsonParser parser) throws IOException {
        var members = new LinkedHashMap<String, JsonNode>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            if (members.put(field, value(parser)) != null) {
                throw unreadable("Member '%s' occurs twice".formatted(field));
            }
        }
        return JsonNode.object(members);
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
        if (value != QualificationRecord.FORMAT_VERSION) {
            throw unreadable("'%s' must be %d: %d".formatted(field, QualificationRecord.FORMAT_VERSION, value));
        }
    }

    private static Long nullableInteger(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
    }

    private static BigDecimal decimal(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
            throw unreadable("'%s' must be a number".formatted(field));
        }
        return parser.getDecimalValue();
    }

    private static BigDecimal nullableDecimal(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : decimal(parser, field);
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    private static <E> E closedName(String field, Function<String, Optional<E>> lookup) {
        return lookup.apply(field)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, field)));
    }

    private static JsonNode document(QualificationRecord entry) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(FORMAT_VERSION, JsonNode.integer(QualificationRecord.FORMAT_VERSION));
        members.put(ROLE, JsonNode.string(entry.role()));
        members.put(QUALIFICATION_DIGEST, JsonNode.string(entry.qualificationDigest()));
        members.put(CONFIGURATION, configuration(entry.configuration()));
        members.put(CORPUS_REVISION, JsonNode.string(entry.corpusRevision()));
        members.put(MISSED_SOURCES, JsonNode.array(entry.missedSources().stream().map(JsonNode::string).toList()));
        var forms = new LinkedHashMap<String, JsonNode>();
        entry.forms().forEach((form, result) -> forms.put(form.wireName(), form(result)));
        members.put(FORMS, JsonNode.object(forms));
        members.put(WARM_FRESH_GAP, entry.warmFreshGap() == null
                ? JsonNode.nullValue()
                : JsonNode.decimal(entry.warmFreshGap()));
        members.put(UNGRADED, JsonNode.integer(entry.ungraded()));
        members.put(GRADED_AT, JsonNode.string(entry.gradedAt().toString()));
        members.put(ITEMS, JsonNode.array(entry.items().stream().map(QualificationRecordCodec::item).toList()));
        return JsonNode.object(members);
    }

    private static JsonNode configuration(GradedConfiguration configuration) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(HARNESS, JsonNode.string(configuration.harness()));
        members.put(HARNESS_VERSION, JsonNode.string(configuration.harnessVersion()));
        members.put(MODEL, JsonNode.string(configuration.model()));
        members.put(EFFORT, configuration.effort() == null
                ? JsonNode.nullValue()
                : JsonNode.string(configuration.effort()));
        members.put(TOOLS, JsonNode.array(configuration.tools().stream().map(JsonNode::string).toList()));
        members.put(CLASS, JsonNode.string(configuration.roleClass().wireName()));
        var digests = new LinkedHashMap<String, JsonNode>();
        configuration.skillDigests().forEach((uri, digest) -> digests.put(uri, JsonNode.string(digest)));
        members.put(SKILL_DIGESTS, JsonNode.object(digests));
        return JsonNode.object(members);
    }

    private static JsonNode form(FormResult result) {
        var members = new LinkedHashMap<String, JsonNode>();
        var topics = new LinkedHashMap<String, JsonNode>();
        result.topics().forEach((topic, value) -> topics.put(topic.wireName(), topic(value)));
        members.put(TOPICS, JsonNode.object(topics));
        members.put(FORM_ITEMS, JsonNode.integer(result.items()));
        tokens(members, result.totalTokens());
        members.put(DURATION_MS, JsonNode.integer(result.durationMs()));
        return JsonNode.object(members);
    }

    private static JsonNode topic(TopicResult result) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(AGREEMENT, JsonNode.decimal(result.agreement()));
        members.put(CONSTANT_BASELINE, JsonNode.decimal(result.constantBaseline()));
        members.put(FORM_ITEMS, JsonNode.integer(result.items()));
        return JsonNode.object(members);
    }

    private static JsonNode item(ItemResult result) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(ITEM_ID, JsonNode.string(result.itemId()));
        members.put(FORM, JsonNode.string(result.form().wireName()));
        members.put(CHECKOUT, JsonNode.string(result.checkout().wireName()));
        members.put(ANSWER, result.answer());
        members.put(SCORE, result.score().isUngraded() ? JsonNode.string(UNGRADED)
                : JsonNode.decimal(result.score().score()));
        tokens(members, result.totalTokens());
        members.put(DURATION_MS, result.durationMs() == null
                ? JsonNode.nullValue()
                : JsonNode.integer(result.durationMs()));
        return JsonNode.object(members);
    }

    private static void tokens(Map<String, JsonNode> members, TokenTotal total) {
        members.put(TOTAL_TOKENS, total.measured() ? JsonNode.integer(total.tokens())
                : JsonNode.string(TokenTotal.UNMEASURED_MARK));
        members.put(TOKENS_IN, total.tokensIn() == null ? JsonNode.string(TokenTotal.UNMEASURED_MARK)
                : JsonNode.integer(total.tokensIn()));
        members.put(TOKENS_OUT, total.tokensOut() == null ? JsonNode.string(TokenTotal.UNMEASURED_MARK)
                : JsonNode.integer(total.tokensOut()));
    }
}
