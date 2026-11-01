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
import static de.planmarshall.core.store.StrictJson.nextField;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.corpus.JsonValue.JsonBoolean;
import de.planmarshall.core.role.corpus.JsonValue.JsonList;
import de.planmarshall.core.role.corpus.JsonValue.JsonNull;
import de.planmarshall.core.role.corpus.JsonValue.JsonNumber;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;

/**
 * Reads and writes {@code derived-facts.json} (Store Schemas § Derived Facts Store; PM-EXT-6, PM-IMPL-2):
 * {@code {"format_version": 1, "facts": {"<kind>[:<qualifier>]": <entry>}}}, where an entry carries the kind, the
 * kind-specific value, the source, the input digest, the instant of the derivation, the state and the failure record.
 * <p>
 * The reader accepts exactly one shape: the current format version, only the declared members, each at most once,
 * every required member, the closed values of {@code kind}, {@code source.class} and {@code state}, the key grammar,
 * the digest grammar, an instant in the one shape {@link Instant#toString()} writes, and the value and failure shapes
 * of {@link DerivedFact}. Anything else is a {@link StoreRefusal} naming the file, never an empty store.
 * <p>
 * The value is the existing {@link JsonValue} tree: it is read with {@link JsonValue#read(JsonParser)} and written by
 * the private generator below, which keeps the member order. An integer is written as its plain digits; a fraction is
 * written in the canonical form the writer produces, so a spelling the parser normalizes (an exponent, a negative
 * zero) is written in that form once, and {@code DerivedFactsCodecTest} states it.
 * {@code failure} is written as a JSON {@code null} for a current entry; the reader accepts the member absent or
 * {@code null} there, because the schema does not require it (ADR {@code core-store/055}).
 * <p>
 * The member lists are package-private constants, so {@code DerivedFactsSchemaParityTest} pins them to the schema
 * block of the specification (the pattern of {@link JobRecordCodec#MEMBERS}).
 *
 * @since 0.1
 */
@UtilityClass
public class DerivedFactsCodec {

    private static final String FIELD_FACTS = "facts";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_INPUT_DIGEST = "input_digest";
    private static final String FIELD_DERIVED_AT = "derived_at";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_FAILURE = "failure";
    private static final String FIELD_DERIVATION = "derivation";
    private static final String FIELD_CLASS = "class";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_AT = "at";

    /** The members of the document, in the order of the schema. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, FIELD_FACTS);

    /** The required members of the document, in the order of the schema. */
    static final List<String> MEMBERS_REQUIRED = List.of(FIELD_FORMAT_VERSION, FIELD_FACTS);

    /** The members of an entry, in the order of the schema; {@code failure} is not required. */
    static final List<String> ENTRY_MEMBERS = List.of(FIELD_KIND, FIELD_VALUE, FIELD_SOURCE, FIELD_INPUT_DIGEST,
            FIELD_DERIVED_AT, FIELD_STATE, FIELD_FAILURE);

    /** The required members of an entry, in the order of the schema. */
    static final List<String> ENTRY_REQUIRED = List.of(FIELD_KIND, FIELD_VALUE, FIELD_SOURCE, FIELD_INPUT_DIGEST,
            FIELD_DERIVED_AT, FIELD_STATE);

    /** The members of {@code source}, in the order of the schema. */
    static final List<String> SOURCE_MEMBERS = List.of(FIELD_DERIVATION, FIELD_CLASS);

    /** The required members of {@code source}, in the order of the schema. */
    static final List<String> SOURCE_REQUIRED = List.of(FIELD_DERIVATION, FIELD_CLASS);

    /** The members of {@code failure}, in the order of the schema. */
    static final List<String> FAILURE_MEMBERS = List.of(FIELD_REASON, FIELD_AT);

    /** The required members of {@code failure}, in the order of the schema. */
    static final List<String> FAILURE_REQUIRED = List.of(FIELD_REASON, FIELD_AT);

    /**
     * @param facts the document
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(DerivedFacts facts) {
        Objects.requireNonNull(facts, FIELD_FACTS);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, DerivedFacts.FORMAT_VERSION);
            generator.writeObjectFieldStart(FIELD_FACTS);
            for (var entry : facts.facts().entrySet()) {
                generator.writeFieldName(entry.getKey().wireName());
                writeEntry(generator, entry.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Derived-facts store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document a derived facts store document, UTF-8 encoded
     * @return the document
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static DerivedFacts read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, DerivedFacts.FORMAT_VERSION, parser -> {
            try {
                return readDocument(parser);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    private static void writeEntry(JsonGenerator generator, DerivedFact fact) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KIND, fact.kind().wireName());
        generator.writeFieldName(FIELD_VALUE);
        if (fact.value() == null) {
            generator.writeNull();
        } else {
            writeValue(generator, fact.value());
        }
        generator.writeObjectFieldStart(FIELD_SOURCE);
        generator.writeStringField(FIELD_DERIVATION, fact.source().derivation());
        generator.writeStringField(FIELD_CLASS, fact.source().derivationClass().wireName());
        generator.writeEndObject();
        generator.writeStringField(FIELD_INPUT_DIGEST, fact.inputDigest());
        writeInstant(generator, FIELD_DERIVED_AT, fact.derivedAt());
        generator.writeStringField(FIELD_STATE, fact.state().wireName());
        if (fact.failure() == null) {
            generator.writeNullField(FIELD_FAILURE);
        } else {
            generator.writeObjectFieldStart(FIELD_FAILURE);
            generator.writeStringField(FIELD_REASON, fact.failure().reason());
            writeInstant(generator, FIELD_AT, fact.failure().at());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeValue(JsonGenerator generator, JsonValue value) throws IOException {
        switch (value) {
            case JsonObject(var members) -> {
                generator.writeStartObject();
                for (var member : members.entrySet()) {
                    generator.writeFieldName(member.getKey());
                    writeValue(generator, member.getValue());
                }
                generator.writeEndObject();
            }
            case JsonList(var elements) -> {
                generator.writeStartArray();
                for (var element : elements) {
                    writeValue(generator, element);
                }
                generator.writeEndArray();
            }
            case JsonString(var text) -> generator.writeString(text);
            case JsonNumber(var number, var integral) ->
                generator.writeNumber(integral ? number.toPlainString() : number.toString().replace('E', 'e'));
            case JsonBoolean(var flag) -> generator.writeBoolean(flag);
            case JsonNull _ -> generator.writeNull();
        }
    }

    private static DerivedFacts readDocument(JsonParser parser) throws IOException {
        @Nullable Map<DerivedFactKey, DerivedFact> facts = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_FACTS -> facts = readFacts(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "document", MEMBERS_REQUIRED.toArray(String[]::new));
        return new DerivedFacts(Objects.requireNonNull(facts, FIELD_FACTS));
    }

    private static Map<DerivedFactKey, DerivedFact> readFacts(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_FACTS);
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var wireName = nextField(parser, seen);
            var key = DerivedFactKey.fromWireName(wireName)
                    .orElseThrow(() -> unreadable("'%s' is not a fact key".formatted(wireName)));
            facts.put(key, readEntry(parser, key));
        }
        return facts;
    }

    private static DerivedFact readEntry(JsonParser parser, DerivedFactKey key) throws IOException {
        requireObject(parser, key.wireName());
        @Nullable DerivedFactKind kind = null;
        @Nullable JsonValue value = null;
        DerivedFact.@Nullable Source source = null;
        @Nullable String inputDigest = null;
        @Nullable Instant derivedAt = null;
        DerivedFact.@Nullable State state = null;
        DerivedFact.@Nullable Failure failure = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = closed(parser, field, DerivedFactKind::fromWireName);
                case FIELD_VALUE -> value = parser.currentToken() == JsonToken.VALUE_NULL ? null : JsonValue.read(parser);
                case FIELD_SOURCE -> source = readSource(parser);
                case FIELD_INPUT_DIGEST -> inputDigest = string(parser, field);
                case FIELD_DERIVED_AT -> derivedAt = instant(parser, field);
                case FIELD_STATE -> state = closed(parser, field, DerivedFact.State::fromWireName);
                case FIELD_FAILURE ->
                    failure = parser.currentToken() == JsonToken.VALUE_NULL ? null : readFailure(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, key.wireName(), ENTRY_REQUIRED.toArray(String[]::new));
        return DerivedFact.builder().kind(kind).value(value).source(source).inputDigest(inputDigest)
                .derivedAt(derivedAt).state(state).failure(failure).build();
    }

    private static DerivedFact.Source readSource(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_SOURCE);
        @Nullable String derivation = null;
        DerivedFact.@Nullable DerivationClass derivationClass = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_DERIVATION -> derivation = string(parser, field);
                case FIELD_CLASS -> derivationClass = closed(parser, FIELD_SOURCE + "." + field,
                        DerivedFact.DerivationClass::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_SOURCE, SOURCE_REQUIRED.toArray(String[]::new));
        return new DerivedFact.Source(derivation, derivationClass);
    }

    private static DerivedFact.Failure readFailure(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_FAILURE);
        @Nullable String reason = null;
        @Nullable Instant at = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_REASON -> reason = string(parser, field);
                case FIELD_AT -> at = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_FAILURE, FAILURE_REQUIRED.toArray(String[]::new));
        return new DerivedFact.Failure(reason, at);
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }
}
