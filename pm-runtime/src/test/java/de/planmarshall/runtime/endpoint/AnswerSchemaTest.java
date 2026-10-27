/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The server's own check of an answer against the result schema (PM-CLIENT-2, Client Profiles § The Request): the JSON
 * Schema subset a result schema uses is enforced, and a schema with a keyword the check does not implement is refused
 * instead of passing.
 */
@DisplayName("Answer schema")
class AnswerSchemaTest {

    private static JsonNode parse(String json) {
        return JsonDocument.parse(json.getBytes(StandardCharsets.UTF_8), "document");
    }

    @Test
    @DisplayName("an answer of the schema's shape is valid")
    void validAnswer() {
        var schema = parse("{\"type\":\"object\",\"required\":[\"verdict\"],\"additionalProperties\":false,"
                + "\"properties\":{\"verdict\":{\"type\":\"string\",\"enum\":[\"clean\",\"suspicious\"]},"
                + "\"spans\":{\"type\":\"array\",\"maxItems\":2,\"items\":{\"type\":\"object\"}}}}");

        assertAll(
                () -> assertTrue(AnswerSchema.validate(schema, parse("{\"verdict\":\"clean\"}")).valid()),
                () -> assertTrue(AnswerSchema.validate(schema,
                        parse("{\"verdict\":\"suspicious\",\"spans\":[{},{}]}")).valid()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "a required member is missing | {} | /verdict",
            "a member is not declared | {\"verdict\":\"clean\",\"extra\":1} | /extra",
            "the type differs | {\"verdict\":1} | /verdict",
            "the value is not in the enum | {\"verdict\":\"maybe\"} | /verdict",
            "the array is too long | {\"verdict\":\"clean\",\"spans\":[{},{},{}]} | /spans",
            "the number is below the minimum | {\"verdict\":\"clean\",\"score\":0} | /score"
    })
    @DisplayName("an answer outside the schema is refused at the failing member")
    void refusedAnswer(String name, String answer, String pointer) {
        var schema = parse("{\"type\":\"object\",\"required\":[\"verdict\"],\"additionalProperties\":false,"
                + "\"properties\":{\"verdict\":{\"type\":\"string\",\"enum\":[\"clean\",\"suspicious\"]},"
                + "\"spans\":{\"type\":\"array\",\"maxItems\":2,\"items\":{\"type\":\"object\"}},"
                + "\"score\":{\"type\":\"number\",\"minimum\":1}}}");

        var check = AnswerSchema.validate(schema, parse(answer));

        assertAll(
                () -> assertFalse(check.valid()),
                () -> assertTrue(check.detail().startsWith(pointer), check.detail()));
    }

    @Test
    @DisplayName("a schema with a keyword the check does not implement is refused instead of passed")
    void unsupportedKeywordRefused() {
        var check = AnswerSchema.validate(parse("{\"type\":\"object\",\"patternProperties\":{}}"), parse("{}"));

        assertAll(
                () -> assertFalse(check.valid()),
                () -> assertTrue(check.detail().contains("patternProperties")));
    }

    @Test
    @DisplayName("the string form of type is enforced, not skipped")
    void stringTypeEnforced() {
        assertAll(
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"object\"}"), parse("\"clean\"")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\"}"), parse("1")).valid()),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"object\"}"), parse("{}")).valid()),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":[\"object\",\"null\"]}"), parse("null")).valid()));
    }

    @Test
    @DisplayName("an additionalProperties given as a schema is refused, never read as open")
    void schemaAdditionalPropertiesRefused() {
        var check = AnswerSchema.validate(parse("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"),
                parse("{\"a\":1}"));

        assertAll(
                () -> assertFalse(check.valid()),
                () -> assertTrue(check.detail().contains("additionalProperties")));
    }

    @Test
    @DisplayName("a local $ref to $defs resolves and is checked")
    void localReference() {
        var schema = parse("{\"$defs\":{\"verdict\":{\"type\":\"string\",\"const\":\"clean\"}},"
                + "\"type\":\"object\",\"required\":[\"verdict\"],\"properties\":{\"verdict\":"
                + "{\"$ref\":\"#/$defs/verdict\"}}}");

        assertAll(
                () -> assertTrue(AnswerSchema.validate(schema, parse("{\"verdict\":\"clean\"}")).valid()),
                () -> assertFalse(AnswerSchema.validate(schema, parse("{\"verdict\":\"suspicious\"}")).valid()));
    }

    @Test
    @DisplayName("a reference that does not resolve is refused")
    void unresolvedReference() {
        var check = AnswerSchema.validate(parse("{\"$ref\":\"#/$defs/absent\"}"), parse("{}"));

        assertAll(
                () -> assertFalse(check.valid()),
                () -> assertTrue(check.detail().contains("does not resolve")));
    }

    @Test
    @DisplayName("the closed keywords of the subset are enforced")
    void keywords() {
        assertAll(
                () -> assertTrue(AnswerSchema.validate(parse("{\"const\":\"clean\"}"), parse("\"clean\"")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"const\":\"clean\"}"), parse("\"x\"")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\",\"minLength\":3}"),
                        parse("\"ab\"")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\",\"maxLength\":1}"),
                        parse("\"ab\"")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"number\",\"maximum\":1}"),
                        parse("2")).valid()),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"integer\"}"), parse("1.5")).valid()),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"boolean\"}"), parse("true")).valid()),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"null\"}"), parse("null")).valid()));
    }

    @Test
    @DisplayName("an array's items are checked")
    void arrayItems() {
        var schema = parse("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");

        assertAll(
                () -> assertTrue(AnswerSchema.validate(schema, parse("[\"a\"]")).valid()),
                () -> assertFalse(AnswerSchema.validate(schema, parse("[1]")).valid()));
    }

    @Test
    @DisplayName("a deeply nested schema is checked without looping")
    void depthGuard() {
        var nested = "{\"type\":\"object\"}";
        for (var index = 0; index < 5; index++) {
            nested = "{\"type\":\"object\",\"properties\":{\"child\":" + nested + "}}";
        }

        var check = AnswerSchema.validate(parse(nested), parse("{}"));

        assertTrue(check.valid());
    }
}
