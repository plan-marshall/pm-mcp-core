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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The server's own check of an answer against the result schema (PM-CLIENT-2, Client Profiles § The Request): the JSON
 * Schema subset a result schema uses is enforced, and a schema with a keyword the check does not implement is refused
 * instead of passing.
 * <p>
 * Every datum of this test is a contract: the JSON Schema keywords, the member names and the pointers into the answer.
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
                () -> assertTrue(AnswerSchema.validate(schema, parse("{\"verdict\":\"clean\"}")).valid(),
                        "an answer of the schema's shape is valid"),
                () -> assertTrue(AnswerSchema.validate(schema,
                                parse("{\"verdict\":\"suspicious\",\"spans\":[{},{}]}")).valid(),
                        "an answer with two spans is still valid"));
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
                () -> assertFalse(check.valid(), "an answer outside the schema is refused"),
                () -> assertTrue(check.detail().startsWith(pointer), check.detail()));
    }

    @Test
    @DisplayName("a $ref with a sibling keyword is refused, never resolved with its sibling ignored")
    void referenceWithSiblingRefused() {
        var check = AnswerSchema.validate(parse("{\"$defs\":{\"a\":{\"type\":\"string\"}},"
                + "\"$ref\":\"#/$defs/a\",\"minLength\":3}"), parse("\"ab\""));

        assertAll(
                () -> assertFalse(check.valid(), "a $ref with a sibling keyword is refused"),
                () -> assertTrue(check.detail().contains("combines $ref"),
                        "the detail names the refused $ref combination"));
    }

    @Test
    @DisplayName("a schema with a keyword the check does not implement is refused instead of passed")
    void unsupportedKeywordRefused() {
        var check = AnswerSchema.validate(parse("{\"type\":\"object\",\"patternProperties\":{}}"), parse("{}"));

        assertAll(
                () -> assertFalse(check.valid(), "a schema with an unimplemented keyword is refused"),
                () -> assertTrue(check.detail().contains("patternProperties"),
                        "the detail names the unimplemented keyword"));
    }

    @Test
    @DisplayName("the string form of type is enforced, not skipped")
    void stringTypeEnforced() {
        assertAll(
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"object\"}"), parse("\"clean\"")).valid(),
                        "an object-typed schema refuses a string"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\"}"), parse("1")).valid(),
                        "a string-typed schema refuses a number"),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"object\"}"), parse("{}")).valid(),
                        "an object-typed schema accepts an empty object"),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":[\"object\",\"null\"]}"),
                        parse("null")).valid(), "a nullable object accepts null"));
    }

    @Test
    @DisplayName("an additionalProperties given as a schema is refused, never read as open")
    void schemaAdditionalPropertiesRefused() {
        var check = AnswerSchema.validate(parse("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"),
                parse("{\"a\":1}"));

        assertAll(
                () -> assertFalse(check.valid(), "a schema-valued additionalProperties is refused"),
                () -> assertTrue(check.detail().contains("additionalProperties"),
                        "the detail names additionalProperties"));
    }

    @Test
    @DisplayName("a local $ref to $defs resolves and is checked")
    void localReference() {
        var schema = parse("{\"$defs\":{\"verdict\":{\"type\":\"string\",\"const\":\"clean\"}},"
                + "\"type\":\"object\",\"required\":[\"verdict\"],\"properties\":{\"verdict\":"
                + "{\"$ref\":\"#/$defs/verdict\"}}}");

        assertAll(
                () -> assertTrue(AnswerSchema.validate(schema, parse("{\"verdict\":\"clean\"}")).valid(),
                        "a local $ref to $defs resolves and accepts"),
                () -> assertFalse(AnswerSchema.validate(schema, parse("{\"verdict\":\"suspicious\"}")).valid(),
                        "a local $ref rejects a value outside const"));
    }

    @Test
    @DisplayName("a reference that does not resolve is refused")
    void unresolvedReference() {
        var check = AnswerSchema.validate(parse("{\"$ref\":\"#/$defs/absent\"}"), parse("{}"));

        assertAll(
                () -> assertFalse(check.valid(), "a reference that does not resolve is refused"),
                () -> assertTrue(check.detail().contains("does not resolve"),
                        "the detail says the reference does not resolve"));
    }

    @Test
    @DisplayName("the closed keywords of the subset are enforced")
    void keywords() {
        assertAll(
                () -> assertTrue(AnswerSchema.validate(parse("{\"const\":\"clean\"}"), parse("\"clean\"")).valid(),
                        "a const keyword accepts its own value"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"const\":\"clean\"}"), parse("\"x\"")).valid(),
                        "a const keyword refuses another value"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\",\"minLength\":3}"),
                        parse("\"ab\"")).valid(), "minLength refuses a shorter string"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"string\",\"maxLength\":1}"),
                        parse("\"ab\"")).valid(), "maxLength refuses a longer string"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"number\",\"maximum\":1}"),
                        parse("2")).valid(), "maximum refuses a larger number"),
                () -> assertFalse(AnswerSchema.validate(parse("{\"type\":\"integer\"}"), parse("1.5")).valid(),
                        "an integer type refuses a fractional number"),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"boolean\"}"), parse("true")).valid(),
                        "a boolean type accepts true"),
                () -> assertTrue(AnswerSchema.validate(parse("{\"type\":\"null\"}"), parse("null")).valid(),
                        "a null type accepts null"));
    }

    @Test
    @DisplayName("an array's items are checked")
    void arrayItems() {
        var schema = parse("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");

        assertAll(
                () -> assertTrue(AnswerSchema.validate(schema, parse("[\"a\"]")).valid(),
                        "a string item passes the array schema"),
                () -> assertFalse(AnswerSchema.validate(schema, parse("[1]")).valid(),
                        "a non-string item fails the array schema"));
    }

    @Test
    @DisplayName("a deeply nested schema is checked without looping")
    void depthGuard() {
        var nested = new StringBuilder("{\"type\":\"object\"}");
        for (var index = 0; index < 5; index++) {
            var wrapper = new StringBuilder("{\"type\":\"object\",\"properties\":{\"child\":");
            nested = wrapper.append(nested).append("}}");
        }

        var check = AnswerSchema.validate(parse(nested.toString()), parse("{}"));

        assertTrue(check.valid(), "a deeply nested schema is checked as valid");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusalsNoEarlierCaseReaches")
    @DisplayName("the refusals no case above reaches are pinned with the message they state")
    void refusalMessages(String name, String schema, String answer, String detail) {
        var check = AnswerSchema.validate(parse(schema), parse(answer));

        assertAll(
                () -> assertFalse(check.valid(), "a schema outside the subset is refused"),
                () -> assertEquals(detail, check.detail(), "the refusal names the bound it applies"));
    }

    static Stream<Arguments> refusalsNoEarlierCaseReaches() {
        return Stream.of(
                // a schema that refers to itself recurses until the depth bound refuses it; the bound is the contract
                Arguments.of("the depth bound",
                        "{\"$defs\":{\"self\":{\"$ref\":\"#/$defs/self\"}},\"$ref\":\"#/$defs/self\"}", "{}",
                        "/: the schema nests deeper than 32"),
                Arguments.of("a schema that is not an object", "\"a schema\"", "{}",
                        "/: a schema is an object"),
                Arguments.of("a type of another shape", "{\"type\":5}", "{}",
                        "/: the result schema's type is neither a string nor an array"),
                Arguments.of("a reference outside the document", "{\"$ref\":\"https://example.com/a.json\"}", "{}",
                        "/: the result schema refers to 'https://example.com/a.json'"),
                Arguments.of("an array below minItems", "{\"type\":\"array\",\"minItems\":2}", "[1]",
                        "/: the array has fewer than 2 items"));
    }

    @Test
    @DisplayName("a type refusal is returned before a const refusal on the same answer")
    void typeRefusalPrecedesConstRefusal() {
        var check = AnswerSchema.validate(parse("{\"type\":\"string\",\"const\":\"ok\"}"), parse("1"));

        assertAll(
                () -> assertFalse(check.valid(), "an answer of another type is refused"),
                () -> assertEquals("/: the answer is not of type [string]", check.detail(),
                        "the type refusal wins over the const refusal"));
    }
}
