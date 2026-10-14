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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;

/**
 * The rows of a CSV source file as RFC 4180 defines it (PM-WORK-6): a header naming the fields, quoted fields with
 * doubled quotes and line breaks, CRLF and LF line ends, an optional byte order mark; a malformed record refused with
 * the line it starts on.
 */
@DisplayName("CSV row reader")
class CsvRowReaderTest {

    private static List<SourceRow> read(String text) {
        return CsvRowReader.read(text.getBytes(StandardCharsets.UTF_8));
    }

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of("plain fields with LF", "id,question\nq-1,Which?\nq-2,Why?\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "Which?")),
                                new SourceRow(3, Map.of("id", "q-2", "question", "Why?")))),
                Arguments.of("CRLF line ends", "id,question\r\nq-1,Which?\r\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "Which?")))),
                Arguments.of("no line end after the last record", "id,question\nq-1,Which?",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "Which?")))),
                Arguments.of("a quoted field with a comma", "id,question\nq-1,\"a, b\"\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "a, b")))),
                Arguments.of("doubled quotes in a quoted field", "id,question\nq-1,\"say \"\"yes\"\"\"\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "say \"yes\"")))),
                Arguments.of("an embedded CRLF counts as a line", "id,question\nq-1,\"first\r\nsecond\"\nq-2,x\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "first\r\nsecond")),
                                new SourceRow(4, Map.of("id", "q-2", "question", "x")))),
                Arguments.of("empty fields, quoted and unquoted", "a,b,c\n,\"\",\n",
                        List.of(new SourceRow(2, Map.of("a", "", "b", "", "c", "")))),
                Arguments.of("a byte order mark", "﻿id,question\nq-1,Which?\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "Which?")))),
                Arguments.of("a header only", "id,question\n", List.of()),
                Arguments.of("UTF-8 text", "id,question\nq-1,Grüße ✓\n",
                        List.of(new SourceRow(2, Map.of("id", "q-1", "question", "Grüße ✓")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validCases")
    @DisplayName("an RFC 4180 case is read into its rows")
    void valid(String name, String text, List<SourceRow> expected) {
        assertEquals(expected, read(text));
    }

    @Test
    @DisplayName("the fields of a row keep the order of the header")
    void headerOrder() {
        var row = read("z,a,m\n1,2,3\n").getFirst();

        assertEquals(List.of("z", "a", "m"), List.copyOf(row.fields().keySet()));
    }

    static Stream<Arguments> malformedCases() {
        return Stream.of(
                Arguments.of("an unterminated quoted field", "id,question\nq-1,\"open\nq-2,x\n", 2,
                        "an unterminated quoted field"),
                Arguments.of("a record with too few fields", "id,question\nq-1,a\nq-2\n", 3,
                        "the record has 1 fields, the header 2"),
                Arguments.of("a record with too many fields", "id,question\nq-1,a,b\n", 2,
                        "the record has 3 fields, the header 2"),
                Arguments.of("a quote inside an unquoted field", "id,question\nq-1,a\"b\n", 2,
                        "a quote inside an unquoted field"),
                Arguments.of("a character after a closing quote", "id,question\nq-1,\"a\"b\n", 2,
                        "a character after a closing quote"),
                Arguments.of("a carriage return without a line feed", "id,question\rq-1,a\n", 1,
                        "a carriage return without a line feed"),
                Arguments.of("a blank line among the records", "id,question\nq-1,a\n\nq-2,b\n", 3,
                        "the record has 1 fields, the header 2"),
                Arguments.of("a header field twice", "id,id\n1,2\n", 1, "names the field 'id' twice"),
                Arguments.of("a header field without a name", "id,\n1,2\n", 1, "has no name"),
                Arguments.of("an empty file", "", 1, "the header is missing"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedCases")
    @DisplayName("a malformed record is refused with the line it starts on")
    void malformed(String name, String text, int line, String detail) {
        var refusal = assertThrows(MalformedSource.class, () -> read(text));

        assertAll(
                () -> assertEquals(line, refusal.line()),
                () -> assertTrue(refusal.getMessage().contains(detail), refusal::getMessage));
    }

    @Test
    @DisplayName("a file that is not UTF-8 is refused")
    void notUtf8() {
        var refusal = assertThrows(MalformedSource.class,
                () -> CsvRowReader.read(new byte[]{'a', '\n', (byte) 0xFF, '\n'}));

        assertTrue(refusal.getMessage().contains("not UTF-8"), refusal::getMessage);
    }
}
