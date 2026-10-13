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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The strict reading every store codec shares (PM-IMPL-2): a document is accepted in exactly one shape and refused
 * otherwise, never tolerated.
 * <p>
 * The format version is checked before any other field, so a document in another version is refused as outdated or
 * newer and not as unreadable for a field that version changed. Then the codec reads the document with the field
 * helpers of this type: every field at most once, only the declared fields, every required field, each with its one
 * JSON type. Whatever does not parse is refused as unreadable with the file named; nothing is ever an empty store.
 * <p>
 * An instant has one shape in a store: an RFC 3339 string in UTC with the suffix {@code Z}, as
 * {@link Instant#toString()} writes it. Another spelling of the same moment (an offset, a fraction padded
 * differently) is refused, so what is read is written again byte for byte.
 * <p>
 * The reading entry {@link #read(Path, byte[], int, DocumentReader)}, the field helpers and the refusals they raise are
 * public API of {@code core.store}: the codec of a store of another package of {@code pm-core} (the session generation
 * record of {@code core.queue}) reads its document with them, so every store of the runtime is read with the one
 * strictness and refused with a {@link StoreRefusal} in the same shape. A {@link Malformed} thrown by a field helper is
 * caught by {@code read} and becomes the refusal naming the file; it never leaves {@code read} on its own.
 *
 * @since 0.1
 */
@UtilityClass
public class StrictJson {

    /** The field every store document declares first in its check. */
    public static final String FIELD_FORMAT_VERSION = "format_version";

    private static final JsonFactory JSON = new JsonFactory();

    /**
     * Reads the content of a document whose format version was checked.
     *
     * @param <T> what the document holds
     * @since 0.1
     */
    @FunctionalInterface
    public interface DocumentReader<T> {

        /**
         * @param parser the parser, positioned on the start of the document object
         * @return what the document holds
         * @throws IOException if the document does not parse as JSON
         * @throws Malformed   if the document parses as JSON but not in the shape of its kind
         */
        T read(JsonParser parser) throws IOException;
    }

    /**
     * Reads one value of an array or of an object with free keys.
     *
     * @param <V> the value
     * @since 0.1
     */
    @FunctionalInterface
    public interface ValueReader<V> {

        /**
         * @param parser the parser, positioned on the value
         * @param name   the name of the value in a refusal: the array element or the key of the object
         * @return the value
         * @throws IOException if the document does not parse as JSON
         * @throws Malformed   if the value does not have its shape
         */
        V read(JsonParser parser, String name) throws IOException;
    }

    /**
     * The content of a document that does not have the one shape of its kind. It is turned into a
     * {@link StoreRefusal} naming the file by {@link #read(Path, byte[], int, DocumentReader)}.
     *
     * @since 0.1
     */
    public static final class Malformed extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private Malformed(String detail, Throwable cause) {
            super(detail, cause);
        }
    }

    /**
     * Reads a document of the current format version: the version is checked first, then the reader reads the
     * object, and nothing may follow it.
     *
     * @param file     the file the document was read from, named by a refusal
     * @param document the document, UTF-8 encoded
     * @param current  the current format version of the file kind
     * @param reader   reads the document object
     * @param <T>      what the document holds
     * @return what the reader read
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static <T> T read(Path file, byte[] document, int current, DocumentReader<T> reader) {
        Objects.requireNonNull(reader, "reader");
        requireCurrentVersion(file, document, current);
        try (var parser = JSON.createParser(document)) {
            parser.nextToken();
            var result = reader.read(parser);
            if (parser.nextToken() != null) {
                throw unreadable("content after the document");
            }
            return result;
        } catch (Malformed e) {
            throw StoreRefusal.unreadable(file, current, e.getMessage(), e.getCause());
        } catch (IOException e) {
            throw StoreRefusal.unreadable(file, current, describe(e), e);
        }
    }

    /**
     * Finds the format version before anything else is read: a JSON object with exactly one integer
     * {@code format_version}, equal to the current one.
     *
     * @param file     the file the document was read from, named by a refusal
     * @param document the document, UTF-8 encoded
     * @param current  the current format version of the file kind
     * @throws StoreRefusal if the document declares another format version, none, or is unreadable
     */
    static void requireCurrentVersion(Path file, byte[] document, int current) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Integer found = null;
        try (var parser = JSON.createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable("not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                var token = parser.nextToken();
                if (!FIELD_FORMAT_VERSION.equals(field)) {
                    parser.skipChildren();
                } else if (found != null || token != JsonToken.VALUE_NUMBER_INT) {
                    throw unreadable("'format_version' must be one integer");
                } else {
                    found = parser.getIntValue();
                }
            }
        } catch (Malformed e) {
            throw StoreRefusal.unreadable(file, current, e.getMessage(), e.getCause());
        } catch (IOException e) {
            throw StoreRefusal.unreadable(file, current, describe(e), e);
        }
        if (found == null) {
            throw StoreRefusal.unreadable(file, current, "'format_version' is missing", null);
        }
        if (found != current) {
            throw StoreRefusal.ofVersion(file, found, current);
        }
    }

    /**
     * @param detail what does not have the shape of its kind
     * @return the refusal of the content, to be thrown
     */
    public static Malformed unreadable(String detail) {
        return new Malformed(detail, null);
    }

    /**
     * @param detail what does not have the shape of its kind
     * @param cause  the failure that found it
     * @return the refusal of the content, to be thrown
     */
    public static Malformed unreadable(String detail, Throwable cause) {
        return new Malformed(detail, cause);
    }

    /**
     * @param field a field the object does not declare
     * @return the refusal of the field, to be thrown
     */
    public static Malformed undeclared(String field) {
        return unreadable("undeclared field '%s'".formatted(field));
    }

    /**
     * Moves to the value of the current field and refuses a field that the object already had.
     *
     * @param parser the parser, positioned on a field name
     * @param seen   the fields of the object read so far; the field is added
     * @return the name of the field
     * @throws IOException if the document does not parse as JSON
     */
    public static String nextField(JsonParser parser, Set<String> seen) throws IOException {
        var field = parser.currentName();
        if (!seen.add(field)) {
            throw unreadable("field '%s' twice".formatted(field));
        }
        parser.nextToken();
        return field;
    }

    /**
     * @param parser the parser, positioned on a value
     * @param name   the name of the value in a refusal
     */
    public static void requireObject(JsonParser parser, String name) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw unreadable("'%s' must be an object".formatted(name));
        }
    }

    /**
     * @param parser the parser, positioned on a value
     * @param name   the name of the value in a refusal
     */
    public static void requireArray(JsonParser parser, String name) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw unreadable("'%s' must be an array".formatted(name));
        }
    }

    /**
     * @param seen     the fields the object had
     * @param object   the name of the object in a refusal
     * @param required the fields the object must have
     */
    public static void requireFields(Set<String> seen, String object, String... required) {
        for (var field : required) {
            if (!seen.contains(field)) {
                throw unreadable("field '%s' of the %s is missing".formatted(field, object));
            }
        }
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the string value
     * @throws IOException if the document does not parse as JSON
     */
    public static String string(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw unreadable("'%s' must be a string".formatted(field));
        }
        return parser.getText();
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the string value; {@code null} for a JSON {@code null}
     * @throws IOException if the document does not parse as JSON
     */
    public static String nullableString(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : string(parser, field);
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the integer value
     * @throws IOException if the document does not parse as JSON or the integer exceeds a {@code long}
     */
    public static long integer(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw unreadable("'%s' must be an integer".formatted(field));
        }
        return parser.getLongValue();
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the boolean value
     */
    public static boolean bool(JsonParser parser, String field) {
        return switch (parser.currentToken()) {
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case null, default -> throw unreadable("'%s' must be a boolean".formatted(field));
        };
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the strings of the array, in order; unmodifiable
     * @throws IOException if the document does not parse as JSON
     */
    public static List<String> stringList(JsonParser parser, String field) throws IOException {
        return array(parser, field, StrictJson::string);
    }

    /**
     * Reads an array whose every element has the same shape.
     *
     * @param parser  the parser, positioned on the value of the field
     * @param field   the name of the field
     * @param element reads one element, named {@code field[index]}
     * @param <V>     an element
     * @return the elements, in order; unmodifiable
     * @throws IOException if the document does not parse as JSON
     */
    public static <V> List<V> array(JsonParser parser, String field, ValueReader<V> element) throws IOException {
        requireArray(parser, field);
        var values = new ArrayList<V>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(element.read(parser, "%s[%d]".formatted(field, values.size())));
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Reads an object with free keys whose every value has the same shape; a key twice is refused.
     *
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @param value  reads the value of one key, named by the key
     * @param <V>    a value
     * @return the values by their keys, in the order of the document; unmodifiable
     * @throws IOException if the document does not parse as JSON
     */
    public static <V> Map<String, V> map(JsonParser parser, String field, ValueReader<V> value) throws IOException {
        requireObject(parser, field);
        var values = new LinkedHashMap<String, V>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var key = nextField(parser, seen);
            values.put(key, value.read(parser, key));
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Accepts the one spelling the writer produces, so reading and writing an instant never changes its bytes.
     *
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the instant
     * @throws IOException if the document does not parse as JSON
     */
    public static Instant instant(JsonParser parser, String field) throws IOException {
        var text = string(parser, field);
        try {
            var instant = Instant.parse(text);
            if (instant.toString().equals(text)) {
                return instant;
            }
        } catch (DateTimeException e) {
            throw unreadable("'%s' is not an instant: %s".formatted(field, text), e);
        }
        throw unreadable(
                "'%s' is not in the one shape of an instant (RFC 3339, UTC, suffix Z): %s".formatted(field, text));
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the instant; {@code null} for a JSON {@code null}
     * @throws IOException if the document does not parse as JSON
     */
    public static Instant nullableInstant(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : instant(parser, field);
    }

    /**
     * Writes an instant in its one shape, or a JSON {@code null}.
     *
     * @param generator the generator, inside an object
     * @param field     the name of the field
     * @param instant   the instant; {@code null} writes a JSON {@code null}
     * @throws IOException if the generator cannot write
     */
    public static void writeInstant(JsonGenerator generator, String field, Instant instant) throws IOException {
        if (instant == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, instant.toString());
        }
    }

    /** @return the shared factory of parsers and generators */
    public static JsonFactory factory() {
        return JSON;
    }

    /** Names where the document stops being JSON, without the source excerpt the parser may append. */
    private static String describe(IOException e) {
        if (e instanceof JsonProcessingException malformed && malformed.getLocation() != null) {
            return "%s (line %d, column %d)".formatted(malformed.getOriginalMessage(),
                    malformed.getLocation().getLineNr(), malformed.getLocation().getColumnNr());
        }
        return String.valueOf(e.getMessage());
    }
}
