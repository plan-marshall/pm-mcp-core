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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.JsonValue.JsonString;

/**
 * The JSON Schema keyword subset of the corpus (PM-WORK-6), evaluated as {@code validate.py} evaluates it: per keyword
 * a passing and a failing value, a boolean never a number, an unknown keyword not evaluated.
 */
@DisplayName("Schema subset")
class SchemaSubsetTest {

    private static JsonValue json(String text) {
        return JsonValue.parse(text.getBytes(UTF_8));
    }

    private static List<String> validate(String value, String schema) {
        return SchemaSubset.validate(json(value), json(schema));
    }

    static Stream<Arguments> keywords() {
        return Stream.of(
                Arguments.of("type string", "{\"type\":\"string\"}", "\"a\"", "1", "$: expected [string]"),
                Arguments.of("type object", "{\"type\":\"object\"}", "{}", "[]", "$: expected [object]"),
                Arguments.of("type array", "{\"type\":\"array\"}", "[]", "{}", "$: expected [array]"),
                Arguments.of("type null", "{\"type\":\"null\"}", "null", "false", "$: expected [null]"),
                Arguments.of("type boolean", "{\"type\":\"boolean\"}", "true", "1", "$: expected [boolean]"),
                Arguments.of("type number", "{\"type\":\"number\"}", "0.5", "true", "$: expected [number]"),
                Arguments.of("type integer", "{\"type\":\"integer\"}", "3", "3.5", "$: expected [integer]"),
                Arguments.of("type integer refuses a boolean", "{\"type\":\"integer\"}", "-1", "false",
                        "$: expected [integer]"),
                Arguments.of("type list", "{\"type\":[\"array\",\"null\"]}", "null", "\"a\"",
                        "$: expected [array, null]"),
                Arguments.of("const", "{\"const\":1}", "1.0", "2", "$: must be 1"),
                Arguments.of("const refuses a boolean for a number", "{\"const\":1}", "1", "true", "$: must be 1"),
                Arguments.of("enum", "{\"enum\":[\"a\",\"b\"]}", "\"b\"", "\"c\"", "$: \"c\" not in [\"a\", \"b\"]"),
                Arguments.of("pattern finds anywhere", "{\"pattern\":\"b+\"}", "\"abba\"", "\"aca\"",
                        "$: does not match b+"),
                Arguments.of("pattern anchored", "{\"pattern\":\"^context/\"}", "\"context/a.md\"", "\"x/context/a\"",
                        "$: does not match ^context/"),
                Arguments.of("maxLength counts code points", "{\"maxLength\":2}", "\"😀😀\"",
                        "\"abc\"", "$: longer than 2"),
                Arguments.of("minimum", "{\"minimum\":0}", "0", "-0.1", "$: out of range"),
                Arguments.of("maximum", "{\"maximum\":1}", "1", "1.01", "$: out of range"),
                Arguments.of("minItems", "{\"minItems\":2}", "[1,2]", "[1]", "$: fewer than 2 items"),
                Arguments.of("items", "{\"items\":{\"type\":\"string\"}}", "[\"a\"]", "[\"a\",2]",
                        "$[1]: expected [string]"),
                Arguments.of("required", "{\"required\":[\"a\"]}", "{\"a\":1}", "{\"b\":1}", "$: missing a"),
                Arguments.of("properties", "{\"properties\":{\"a\":{\"type\":\"string\"}}}", "{\"a\":\"x\",\"b\":1}",
                        "{\"a\":1}", "$.a: expected [string]"),
                Arguments.of("additionalProperties false", "{\"properties\":{\"a\":{}},\"additionalProperties\":false}",
                        "{\"a\":1}", "{\"a\":1,\"b\":2}", "$: unknown key b"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keywords")
    @DisplayName("a keyword accepts its passing value and reports its failing value")
    void keyword(String name, String schema, String passing, String failing, String violation) {
        assertAll(
                () -> assertEquals(List.of(), validate(passing, schema), "passing " + passing),
                () -> assertEquals(List.of(violation), validate(failing, schema), "failing " + failing));
    }

    /**
     * {@code pattern} as {@code re.search} of {@code validate.py} evaluates it, pinned against Python 3: {@code $}
     * matches at the end or before one final {@code \n}, never before another line terminator.
     */
    @Nested
    @DisplayName("pattern, as Python's re.search")
    class PythonPattern {

        private static final String COMMIT = "{\"pattern\":\"^[0-9a-f]{7,40}$\"}";

        @ParameterizedTest(name = "terminator {index}")
        @ValueSource(strings = {"\r", "\u0085", " ", " ", "\r\n", "\n\n", "\u000b", "\f"})
        @DisplayName("an anchored pattern refuses a value ending in a terminator other than one final line feed")
        void otherTerminator(String terminator) {
            var errors = SchemaSubset.validate(new JsonString("abc1234" + terminator), json(COMMIT));

            assertEquals(List.of("$: does not match ^[0-9a-f]{7,40}$"), errors);
        }

        @Test
        @DisplayName("an anchored pattern accepts a value ending in one line feed, as re.search does")
        void finalLineFeed() {
            assertEquals(List.of(), SchemaSubset.validate(new JsonString("abc1234\n"), json(COMMIT)));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "https://example.org/a|true",
                "https://exa\u001cmple.org/a|false",
                "https://exa mple.org/a|false",
                "https://example.org/a b|false"})
        @DisplayName("\\s of the repository pattern is Python's whitespace, \\x1c to \\x1f and Unicode spaces included")
        void unicodeSpace(String value, boolean valid) {
            var schema = json("{\"pattern\":\"^https://[^/?#@\\\\s]+(/[^?#\\\\s]*)?$\"}");

            assertEquals(valid, SchemaSubset.validate(new JsonString(value), schema).isEmpty());
        }
    }

    @Nested
    @ExtendWith(NonLatinDigits.class)
    @DisplayName("under a default locale with non-Latin digits")
    class NonLatin {

        @Test
        @DisplayName("the path of an array element is written in ASCII digits")
        void elementPath() {
            assertEquals(List.of("$[1]: expected [string]"),
                    validate("[\"a\",2]", "{\"items\":{\"type\":\"string\"}}"));
        }

        @Test
        @DisplayName("an escaped character is written in ASCII hexadecimal digits")
        void escape() {
            assertEquals("\"\\u00e9\\u0661\"", json("\"\\u00e9\\u0661\"").toJson());
        }
    }

    @Nested
    @DisplayName("evaluation")
    class Evaluation {

        @Test
        @DisplayName("a value of a type the schema does not admit is not looked into further")
        void wrongTypeStops() {
            var errors = validate("1", "{\"type\":\"string\",\"const\":\"a\",\"minimum\":5}");

            assertEquals(List.of("$: expected [string]"), errors);
        }

        @Test
        @DisplayName("violations deep in a document carry their path")
        void nestedPaths() {
            var schema = """
                    {"type":"object","properties":{"rows":{"type":"array","items":{"type":"object",
                    "required":["id"],"properties":{"id":{"type":"string"}},"additionalProperties":false}}}}""";

            var errors = validate("{\"rows\":[{\"id\":\"a\"},{\"id\":2,\"x\":1},{}]}", schema);

            assertEquals(List.of("$.rows[1].id: expected [string]", "$.rows[1]: unknown key x", "$.rows[2]: missing id"),
                    errors);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{\"format\":\"date\"}", "{\"description\":\"d\"}", "{\"$schema\":\"x\"}", "{}"})
        @DisplayName("a keyword outside the subset is not evaluated")
        void otherKeywords(String schema) {
            assertEquals(List.of(), validate("\"not a date\"", schema));
        }

        @Test
        @DisplayName("a schema pattern that does not compile is reported, not thrown")
        void brokenPattern() {
            var errors = validate("\"a\"", "{\"pattern\":\"(\"}");

            assertEquals(1, errors.size(), errors::toString);
        }
    }

    @Nested
    @DisplayName("the JSON tree")
    class Tree {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"", "{", "{\"a\":1,\"a\":2}", "[1,]", "1 2", "{\"a\":[1,2}"})
        @DisplayName("a document that is not exactly one JSON value, or names a member twice, is refused")
        void malformed(String document) {
            var bytes = document.getBytes(UTF_8);

            assertThrows(MalformedJson.class, () -> JsonValue.parse(bytes));
        }

        @Test
        @DisplayName("is written as Python's json.dumps writes it")
        void toJson() {
            var value = json("{\"a\":[1,2.5,true,null],\"b\":\"x\\\"\\u00e9\\n\",\"c\":{}}");

            assertEquals("{\"a\": [1, 2.5, true, null], \"b\": \"x\\\"\\u00e9\\n\", \"c\": {}}", value.toJson());
        }

        @Test
        @DisplayName("escapes DEL as Python's json.dumps does, keeping the printable range up to '~'")
        void toJsonEscapesDel() {
            var value = json("{\"a\":\" ~" + (char) 0x7f + "E1\"}");

            assertEquals("{\"a\": \" ~\\u007fE1\"}", value.toJson());
        }
    }
}
