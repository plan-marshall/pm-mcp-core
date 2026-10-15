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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;

/**
 * The rows of a JSON Lines source file (PM-WORK-6): one object per line, strings kept as strings and every other
 * value as its JSON text, a blank last line allowed, any other malformed line refused with its number. Every test runs
 * under a default locale with non-Latin digits, so the line a refusal names must be ASCII digits.
 */
@ExtendWith(NonLatinDigits.class)
@DisplayName("JSON Lines row reader")
class JsonlRowReaderTest {

    private static List<SourceRow> read(String text) {
        return JsonlRowReader.read(text.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("valid rows")
    class Valid {

        @Test
        @DisplayName("every line becomes a row with its line number and its fields in order")
        void rows() {
            var rows = read("{\"id\":\"q-1\",\"question\":\"Which?\"}\n{\"id\":\"q-2\",\"question\":\"Why?\"}");

            assertAll(
                    () -> assertEquals(2, rows.size()),
                    () -> assertEquals(new SourceRow(1, Map.of("id", "q-1", "question", "Which?")), rows.get(0)),
                    () -> assertEquals(2, rows.get(1).line()),
                    () -> assertEquals(List.of("id", "question"), List.copyOf(rows.get(1).fields().keySet())));
        }

        @Test
        @DisplayName("a nested value, a number, a boolean and null keep their JSON text")
        void nestedValues() {
            var row = read("{\"options\":[\"a\",\"b\"],\"meta\":{\"n\":1},\"score\":2.5,\"ok\":true,\"x\":null}")
                    .getFirst();

            assertEquals(Map.of("options", "[\"a\",\"b\"]", "meta", "{\"n\":1}", "score", "2.5", "ok", "true",
                    "x", "null"), row.fields());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"2.50", "1e3", "-0", "-0.0", "1E-7", "9223372036854775808",
                "123456789012345678901234567890", "0.1234567890123456789", "12345678901234567890.123456789"})
        @DisplayName("a number keeps the text the line writes, never a converted value")
        void numberText(String number) {
            var row = read("{\"n\":" + number + "}").getFirst();

            assertEquals(number, row.fields().get("n"));
        }

        @Test
        @DisplayName("a nested object or array keeps the text it spans, its numbers unconverted")
        void nestedText() {
            var row = read("{\"meta\": {\"score\" : 2.50, \"list\": [1e3, -0]}, \"after\":\"x\"}").getFirst();

            assertAll(
                    () -> assertEquals("{\"score\" : 2.50, \"list\": [1e3, -0]}", row.fields().get("meta")),
                    () -> assertEquals("x", row.fields().get("after")));
        }

        @Test
        @DisplayName("a blank last line and CRLF line ends are accepted")
        void blankLastLine() {
            var rows = read("{\"id\":\"q-1\"}\r\n{\"id\":\"q-2\"}\r\n");

            assertAll(
                    () -> assertEquals(2, rows.size()),
                    () -> assertEquals("q-2", rows.get(1).fields().get("id")));
        }

        @Test
        @DisplayName("an empty file has no rows")
        void empty() {
            assertEquals(List.of(), read(""));
        }
    }

    static Stream<Arguments> malformed() {
        return Stream.of(
                Arguments.of("a line that is no JSON", "{\"id\":\"q-1\"}\n{\"id\":", 2, "not JSON"),
                Arguments.of("a line that is an array", "[1,2]\n", 1, "not a JSON object"),
                Arguments.of("a field twice", "{\"id\":\"a\"}\n{\"id\":\"a\",\"id\":\"b\"}\n", 2,
                        "field 'id' twice"),
                Arguments.of("a key twice in a nested object", "{\"meta\":{\"n\":1,\"n\":2}}\n", 1,
                        "field 'n' twice in a nested object"),
                Arguments.of("a key twice in an object inside an array",
                        "{\"id\":\"a\"}\n{\"l\":[{\"k\":1,\"k\":1}]}\n", 2, "field 'k' twice in a nested object"),
                Arguments.of("content after the object", "{\"id\":\"a\"} {\"id\":\"b\"}\n", 1,
                        "content after the JSON object"),
                Arguments.of("a blank line before the last", "{\"id\":\"a\"}\n\n{\"id\":\"b\"}\n", 2,
                        "a blank line before the last line"),
                Arguments.of("two blank last lines", "{\"id\":\"a\"}\n\n\n", 2, "a blank line before the last line"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformed")
    @DisplayName("a malformed line is refused with its line number")
    void malformedLine(String name, String text, int line, String detail) {
        var refusal = assertThrows(MalformedSource.class, () -> read(text));

        assertAll(
                () -> assertEquals(line, refusal.line()),
                () -> assertTrue(refusal.getMessage().startsWith("line " + line + ": "), refusal::getMessage),
                () -> assertTrue(refusal.getMessage().contains(detail), refusal::getMessage));
    }

    @Test
    @DisplayName("a file that is not UTF-8 is refused")
    void notUtf8() {
        var refusal = assertThrows(MalformedSource.class,
                () -> JsonlRowReader.read(new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xC3, '"', '}'}));

        assertTrue(refusal.getMessage().contains("not UTF-8"), refusal::getMessage);
    }
}
