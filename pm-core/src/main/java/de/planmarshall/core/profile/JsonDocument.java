/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Reads a client profile or a profile verification record as the bounded tree of a {@link JsonNode} (PM-CLIENT-5).
 * <p>
 * A profile is untrusted input, so the reader is bounded before any value of it is used: a document of at most
 * {@value #MAX_BYTES} bytes, values nested at most {@value #MAX_DEPTH} deep, strings of at most {@value #MAX_STRING}
 * characters and no member twice. Whatever it refuses, it refuses as a {@link ProfileRefusal} carrying the document and
 * the JSON Pointer of the value that broke the bound.
 *
 * @since 0.1
 */
public final class JsonDocument {

    /** The largest profile or record document, in bytes. */
    public static final int MAX_BYTES = 64 * 1024;

    /** The largest nesting depth of a value. */
    public static final int MAX_DEPTH = 16;

    /** The longest string of a document. */
    public static final int MAX_STRING = 64 * 1024;

    private static final JsonFactory JSON = new JsonFactory();

    private JsonDocument() {
    }

    /**
     * @param document the document, UTF-8 encoded
     * @param name     the document, as a refusal names it
     * @return the tree of the document
     * @throws ProfileRefusal if the document is too large, is not JSON, or breaks a bound of the reader
     */
    public static JsonNode parse(byte[] document, String name) {
        if (document == null) {
            throw ProfileRefusal.unreadable(name, "the document is absent");
        }
        if (document.length > MAX_BYTES) {
            throw ProfileRefusal.invalid(name, "",
                    "the document has %d bytes, more than the %d of a profile".formatted(document.length,
                            MAX_BYTES));
        }
        try (var parser = JSON.createParser(document)) {
            if (parser.nextToken() == null) {
                throw ProfileRefusal.invalid(name, "", "the document is empty");
            }
            var root = readValue(parser, "", 0, name);
            if (parser.nextToken() != null) {
                throw ProfileRefusal.invalid(name, "", "content after the document");
            }
            return root;
        } catch (JsonProcessingException e) {
            var location = e.getLocation();
            var where = location == null ? ""
                    : " (line %d, column %d)".formatted(location.getLineNr(), location.getColumnNr());
            throw ProfileRefusal.unreadable(name, "not JSON: %s%s".formatted(e.getOriginalMessage(), where));
        } catch (IOException e) {
            throw ProfileRefusal.unreadable(name, "not readable: %s".formatted(e.getMessage()));
        }
    }

    private static JsonNode readValue(JsonParser parser, String pointer, int depth, String document) throws IOException {
        if (depth > MAX_DEPTH) {
            throw ProfileRefusal.invalid(document, pointer,
                    "nested deeper than the %d of a profile".formatted(MAX_DEPTH));
        }
        return switch (parser.currentToken()) {
            case START_OBJECT -> readObject(parser, pointer, depth, document);
            case START_ARRAY -> readArray(parser, pointer, depth, document);
            case VALUE_STRING -> JsonNode.string(readString(parser, pointer, document));
            case VALUE_NUMBER_INT -> JsonNode.integer(readInteger(parser, pointer, document));
            case VALUE_NUMBER_FLOAT -> JsonNode.decimal(readDecimal(parser, pointer, document));
            case VALUE_TRUE -> JsonNode.bool(true);
            case VALUE_FALSE -> JsonNode.bool(false);
            case VALUE_NULL -> JsonNode.nullValue();
            case null, default -> throw ProfileRefusal.invalid(document, pointer, "not a JSON value");
        };
    }

    private static JsonNode readObject(JsonParser parser, String pointer, int depth, String document)
            throws IOException {
        var members = new LinkedHashMap<String, JsonNode>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = parser.currentName();
            var child = JsonPointer.child(pointer, name);
            if (members.containsKey(name)) {
                throw ProfileRefusal.invalid(document, child, "member '%s' occurs twice".formatted(name));
            }
            parser.nextToken();
            members.put(name, readValue(parser, child, depth + 1, document));
        }
        return JsonNode.object(members);
    }

    private static JsonNode readArray(JsonParser parser, String pointer, int depth, String document)
            throws IOException {
        var elements = new ArrayList<JsonNode>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(readValue(parser, pointer + "/" + elements.size(), depth + 1, document));
        }
        return JsonNode.array(elements);
    }

    private static String readString(JsonParser parser, String pointer, String document) throws IOException {
        var text = parser.getText();
        if (text.length() > MAX_STRING) {
            throw ProfileRefusal.invalid(document, pointer,
                    "a string of %d characters, longer than the %d of a profile".formatted(text.length(), MAX_STRING));
        }
        return text;
    }

    private static long readInteger(JsonParser parser, String pointer, String document) throws IOException {
        try {
            return parser.getLongValue();
        } catch (JsonProcessingException _) {
            throw ProfileRefusal.invalid(document, pointer, "an integer outside the range of a long");
        }
    }

    private static BigDecimal readDecimal(JsonParser parser, String pointer, String document) throws IOException {
        try {
            var value = parser.getDecimalValue();
            if (value.precision() > 1000 || Math.abs((long) value.scale()) > 1000) {
                throw ProfileRefusal.invalid(document, pointer, "a number outside the range of a profile");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw ProfileRefusal.invalid(document, pointer, "a number that is not readable: " + e.getOriginalMessage());
        }
    }

}
