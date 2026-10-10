/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.planmarshall.workflow.ast.InstanceHeader;

/**
 * Reads the one-line JSON of the {@code instance} line of a plan instance version into an {@link InstanceHeader}
 * (PM-IMPL-5), with the streaming parser of jackson-core.
 * <p>
 * The line must be exactly one JSON object with the members {@code plan_id} (string), {@code version} (integer),
 * {@code event} (string) and {@code input_digests} (object of strings), each at most once; further members are kept
 * in the header's JSON and not interpreted. Anything else is refused with an {@link IllegalArgumentException} whose
 * message names the defect; a jackson-core {@link IOException} is mapped to that refusal and never rethrown.
 */
final class InstanceHeaderReader {

    private static final String FIELD_PLAN_ID = "plan_id";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_INPUT_DIGESTS = "input_digests";

    private static final JsonFactory JSON = new JsonFactory();

    private InstanceHeaderReader() {
    }

    /**
     * Reads an instance header.
     *
     * @param json the JSON of the {@code instance} line
     * @return the header, holding {@code json} as read
     * @throws IllegalArgumentException if the line is no JSON object or lacks or mistypes a required member
     */
    static InstanceHeader read(String json) {
        try (var parser = JSON.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("the instance header is not a JSON object");
            }
            String planId = null;
            var version = 0;
            String event = null;
            Map<String, String> inputDigests = Map.of();
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_PLAN_ID -> planId = string(parser, field);
                    case FIELD_VERSION -> version = integer(parser, field);
                    case FIELD_EVENT -> event = string(parser, field);
                    case FIELD_INPUT_DIGESTS -> inputDigests = digests(parser);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("content after the instance header object");
            }
            requireFields(seen);
            return new InstanceHeader(planId, version, event, inputDigests, json);
        } catch (IOException e) {
            throw new IllegalArgumentException("the instance header is not valid JSON: " + describe(e), e);
        }
    }

    private static String nextField(JsonParser parser, Set<String> seen) throws IOException {
        var field = parser.currentName();
        if (!seen.add(field)) {
            throw new IllegalArgumentException("member '%s' of the instance header twice".formatted(field));
        }
        parser.nextToken();
        return field;
    }

    private static void requireFields(Set<String> seen) {
        for (var field : new String[]{FIELD_PLAN_ID, FIELD_VERSION, FIELD_EVENT, FIELD_INPUT_DIGESTS}) {
            if (!seen.contains(field)) {
                throw new IllegalArgumentException("member '%s' of the instance header is missing".formatted(field));
            }
        }
    }

    private static String string(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new IllegalArgumentException("member '%s' of the instance header must be a string".formatted(field));
        }
        return parser.getText();
    }

    private static int integer(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new IllegalArgumentException(
                    "member '%s' of the instance header must be an integer".formatted(field));
        }
        return parser.getIntValue();
    }

    private static Map<String, String> digests(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException(
                    "member '%s' of the instance header must be an object".formatted(FIELD_INPUT_DIGESTS));
        }
        var digests = new LinkedHashMap<String, String>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var input = nextField(parser, seen);
            digests.put(input, string(parser, FIELD_INPUT_DIGESTS + "." + input));
        }
        return digests;
    }

    /** Names where the line stops being JSON, without the source excerpt the parser may append. */
    private static String describe(IOException e) {
        if (e instanceof JsonProcessingException malformed && malformed.getLocation() != null) {
            return "%s (column %d)".formatted(malformed.getOriginalMessage(), malformed.getLocation().getColumnNr());
        }
        return String.valueOf(e.getMessage());
    }
}
