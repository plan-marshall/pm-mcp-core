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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.StrictJson;

/**
 * A minimal immutable JSON tree (PM-WORK-6), read on the streaming API of {@code jackson-core}, that
 * {@link SchemaSubset} and {@link CorpusValidator} walk.
 * <p>
 * A number keeps its value and whether it was written as an integer (no fraction, no exponent), which the schema
 * type {@code integer} asks for. An object keeps its members in the order of the document; a member named twice is
 * refused, since it leaves the value of the member open.
 *
 * @since 0.1
 */
public sealed interface JsonValue {

    /**
     * A JSON object.
     *
     * @param members the members in the order of the document; unmodifiable
     * @since 0.1
     */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {

        /**
         * Copies the members, keeping their order.
         */
        public JsonObject {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(members, "members")));
        }

        /**
         * @param name the name of a member
         * @return the value of the member; empty if the object has no member of that name
         */
        public Optional<JsonValue> get(String name) {
            return Optional.ofNullable(members.get(name));
        }
    }

    /**
     * A JSON array.
     *
     * @param elements the elements in the order of the document; unmodifiable
     * @since 0.1
     */
    record JsonList(List<JsonValue> elements) implements JsonValue {

        /**
         * Copies the elements.
         */
        public JsonList {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    /**
     * A JSON string.
     *
     * @param value the string
     * @since 0.1
     */
    record JsonString(String value) implements JsonValue {

        /**
         * Requires the string.
         */
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number.
     *
     * @param value    the number
     * @param integral {@code true} if the document wrote it as an integer, without fraction or exponent
     * @since 0.1
     */
    record JsonNumber(BigDecimal value, boolean integral) implements JsonValue {

        /**
         * Requires the number.
         */
        public JsonNumber {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON {@code true} or {@code false}.
     *
     * @param value the value
     * @since 0.1
     */
    record JsonBoolean(boolean value) implements JsonValue {
    }

    /**
     * The JSON {@code null}.
     *
     * @since 0.1
     */
    record JsonNull() implements JsonValue {
    }

    /**
     * A document that is not one JSON value, or that names a member of an object twice.
     *
     * @since 0.1
     */
    final class MalformedJson extends IllegalArgumentException {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * @param detail what is malformed
         * @param cause  the failure that found it; {@code null} if there is none
         */
        public MalformedJson(String detail, Throwable cause) {
            super(Objects.requireNonNull(detail, "detail"), cause);
        }
    }

    /**
     * @param document one JSON value, UTF-8 encoded
     * @return the value
     * @throws MalformedJson if the document is not exactly one JSON value, or an object names a member twice
     */
    static JsonValue parse(byte[] document) {
        Objects.requireNonNull(document, "document");
        try (var parser = StrictJson.factory().createParser(document)) {
            if (parser.nextToken() == null) {
                throw new MalformedJson("the document is empty", null);
            }
            var value = read(parser);
            if (parser.nextToken() != null) {
                throw new MalformedJson("content after the document", null);
            }
            return value;
        } catch (JsonParseException e) {
            throw new MalformedJson(e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new MalformedJson("the document cannot be read: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the value as Python's {@code json.dumps} writes it by default: members separated by {@code ", "}, names
     * by {@code ": "}, every character outside ASCII escaped. The corpus rules match text against this form, as
     * {@code validate.py} does. A number keeps the digits of the document, a fraction or exponent with a lower-case
     * {@code e}.
     *
     * @return the JSON text
     */
    default String toJson() {
        var text = new StringBuilder();
        write(this, text);
        return text.toString();
    }

    private static JsonValue read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == null) {
            throw new MalformedJson("the document ends inside a value", null);
        }
        return switch (token) {
            case START_OBJECT -> readObject(parser);
            case START_ARRAY -> readArray(parser);
            case VALUE_STRING -> new JsonString(parser.getText());
            case VALUE_NUMBER_INT -> new JsonNumber(parser.getDecimalValue(), true);
            case VALUE_NUMBER_FLOAT -> new JsonNumber(parser.getDecimalValue(), false);
            case VALUE_TRUE -> new JsonBoolean(true);
            case VALUE_FALSE -> new JsonBoolean(false);
            case VALUE_NULL -> new JsonNull();
            default -> throw new MalformedJson("unexpected token " + token, null);
        };
    }

    private static JsonObject readObject(JsonParser parser) throws IOException {
        var members = new LinkedHashMap<String, JsonValue>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = parser.currentName();
            parser.nextToken();
            if (members.put(name, read(parser)) != null) {
                throw new MalformedJson("the member '%s' is named twice".formatted(name), null);
            }
        }
        return new JsonObject(members);
    }

    private static JsonList readArray(JsonParser parser) throws IOException {
        var elements = new ArrayList<JsonValue>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(read(parser));
        }
        return new JsonList(elements);
    }

    private static void write(JsonValue value, StringBuilder text) {
        switch (value) {
            case JsonObject object -> {
                text.append('{');
                var first = true;
                for (var member : object.members().entrySet()) {
                    if (!first) {
                        text.append(", ");
                    }
                    first = false;
                    writeString(member.getKey(), text);
                    text.append(": ");
                    write(member.getValue(), text);
                }
                text.append('}');
            }
            case JsonList array -> {
                text.append('[');
                for (var index = 0; index < array.elements().size(); index++) {
                    if (index > 0) {
                        text.append(", ");
                    }
                    write(array.elements().get(index), text);
                }
                text.append(']');
            }
            case JsonString string -> writeString(string.value(), text);
            case JsonNumber number -> text.append(number.integral() ? number.value().toPlainString()
                    : number.value().toString().replace('E', 'e'));
            case JsonBoolean bool -> text.append(bool.value());
            case JsonNull _ -> text.append("null");
        }
    }

    private static void writeString(String value, StringBuilder text) {
        text.append('"');
        for (var index = 0; index < value.length(); index++) {
            var c = value.charAt(index);
            switch (c) {
                case '"' -> text.append("\\\"");
                case '\\' -> text.append("\\\\");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\t' -> text.append("\\t");
                case '\b' -> text.append("\\b");
                case '\f' -> text.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        text.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        text.append('"');
    }
}
