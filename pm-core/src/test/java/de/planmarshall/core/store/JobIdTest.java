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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JobId.Kind;

/**
 * The {@code job_id} of a job record (PM-EXEC-4): the grammar {@code ^[bcglpv]-[0-9a-f]{16}$} per kind, the random
 * draw, and the names of the record and the log it gives.
 */
// Every datum in this class is a contract: the grammar, the kind prefixes and wire names, the file names.
@DisplayName("Job id")
class JobIdTest {

    private static final Pattern FORM = Pattern.compile("^[bcglpv]-[0-9a-f]{16}$");

    /** A source of digits that answers one fixed value, so the drawn digits are known. */
    private static final RandomGenerator FIXED = () -> 0x0123456789abcdefL;

    @Nested
    @DisplayName("per kind")
    class PerKind {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Kind.class)
        @DisplayName("a drawn id has the prefix of its kind, 16 hex digits, and names its kind")
        void drawn(Kind kind) {
            var id = JobId.random(kind);

            assertAll("A drawn id matches the grammar of its kind",
                    () -> assertTrue(FORM.matcher(id.value()).matches(),
                            "The drawn id matches the job-id grammar"),
                    () -> assertEquals(kind.prefix(), id.value().charAt(0),
                            "The first character is the prefix of the kind"),
                    () -> assertEquals(kind, id.kind(),
                            "The id reports the kind it was drawn for"),
                    () -> assertEquals(Optional.of(id), JobId.parse(id.value()),
                            "A drawn id parses back to itself"));
        }

        @ParameterizedTest(name = "{0} -> {1}{2}")
        @CsvSource({
                "BUILD, b, build",
                "CI, c, ci",
                "GIT, g, git",
                "PROJECT_SCRIPT, p, project_script",
                "LLM, l, llm",
                "PROBE, v, probe"})
        @DisplayName("each kind has its prefix and its store wire name")
        void vocabulary(Kind kind, char prefix, String wireName) {
            assertAll("Each kind carries its prefix and wire name",
                    () -> assertEquals(prefix, kind.prefix(),
                            "The kind carries its prefix"),
                    () -> assertEquals(wireName, kind.wireName(),
                            "The wire name is the store name of the kind"),
                    () -> assertEquals(Optional.of(kind), Kind.fromWireName(wireName),
                            "The wire name parses back to its kind"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Kind.class)
        @DisplayName("the digits are drawn from the given source")
        void drawnFromSource(Kind kind) {
            var id = JobId.random(kind, FIXED);

            assertEquals(kind.prefix() + "-efcdab8967452301", id.value(),
                    "The digits are drawn from the given source");
        }
    }

    @Nested
    @DisplayName("file names")
    class FileNames {

        @Test
        @DisplayName("the record is <job_id>.json and the log <job_id>.log")
        void names() {
            var id = new JobId("b-0123456789abcdef");

            assertAll("The id names its record and its log file",
                    () -> assertEquals("b-0123456789abcdef.json", id.recordFileName(),
                            "The record file is the id with .json"),
                    () -> assertEquals("b-0123456789abcdef.log", id.logFileName(),
                            "The log file is the id with .log"),
                    () -> assertEquals("b-0123456789abcdef", id.toString(),
                            "The string form is the id value"));
        }

        @Test
        @DisplayName("only the name of a record parses back to its id")
        void ofRecordFileName() {
            assertAll("Only a record file name parses to an id",
                    () -> assertEquals(Optional.of(new JobId("v-0123456789abcdef")),
                            JobId.ofRecordFileName("v-0123456789abcdef.json"),
                            "The record file name yields its id"),
                    () -> assertEquals(Optional.empty(), JobId.ofRecordFileName("v-0123456789abcdef.log"),
                            "A log file name yields no id"),
                    () -> assertEquals(Optional.empty(), JobId.ofRecordFileName("v-0123456789abcdef.json.tmp.x"),
                            "A temporary suffix yields no id"),
                    () -> assertEquals(Optional.empty(), JobId.ofRecordFileName("notes.json"),
                            "An unrelated file name yields no id"),
                    () -> assertThrows(NullPointerException.class, () -> JobId.ofRecordFileName(null),
                            "A null file name is refused"));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"a-0123456789abcdef", "x-0123456789abcdef", "B-0123456789abcdef",
                "b-0123456789ABCDEF", "b-0123456789abcde", "b-0123456789abcdef0", "b0123456789abcdef",
                "ent-0123456789abcdef", "", "b-0123456789abcdeg"})
        @DisplayName("a value outside the grammar is no job id")
        void foreign(String value) {
            assertAll("A foreign value is refused as a job id",
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobId(value),
                            "The constructor refuses the foreign value"),
                    () -> assertEquals(Optional.empty(), JobId.parse(value),
                            "Parsing the foreign value yields empty"));
        }

        @Test
        @DisplayName("a missing value or kind is refused")
        void missing() {
            assertAll("A missing value or kind is refused",
                    () -> assertThrows(NullPointerException.class, () -> new JobId(null),
                            "A null value is refused by the constructor"),
                    () -> assertEquals(Optional.empty(), JobId.parse(null),
                            "Parsing a null value yields empty"),
                    () -> assertThrows(NullPointerException.class, () -> JobId.random(null),
                            "A null kind is refused by the draw"),
                    () -> assertEquals(Optional.empty(), Kind.fromWireName("worker"),
                            "An unknown wire name yields no kind"));
        }

        @Test
        @DisplayName("two draws of the secure source differ")
        void drawsDiffer() {
            assertNotEquals(JobId.random(Kind.BUILD), JobId.random(Kind.BUILD),
                    "Two draws of the secure source differ");
        }
    }
}
