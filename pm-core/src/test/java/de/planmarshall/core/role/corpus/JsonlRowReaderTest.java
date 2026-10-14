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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;

/**
 * The rows of a JSON Lines source file (PM-WORK-6): one object per line, strings kept as strings and every other
 * value as its JSON text, a blank last line allowed, any other malformed line refused with its number.
 */
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
