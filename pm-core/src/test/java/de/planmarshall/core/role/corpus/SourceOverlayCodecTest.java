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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The overlay of a corpus source (PM-WORK-6): one format version, every declared field exactly once, no undeclared
 * field, every value in its format, every item id once; a refusal names the file. Every test runs under a default
 * locale with non-Latin digits, so the numbers of a document and of a refusal must be ASCII digits.
 */
@ExtendWith(NonLatinDigits.class)
@DisplayName("Source overlay codec")
class SourceOverlayCodecTest {

    private static final Path FILE = Path.of("/corpus/sources/sample-set/overlay.json");

    /** The members of a valid row selected by its key, each with its JSON value. */
    private static Map<String, String> rowMembers(String key, String slug) {
        var members = new LinkedHashMap<String, String>();
        members.put("key", "\"" + key + "\"");
        members.put("id", "\"" + slug + "\"");
        members.put("topic", "\"screening/sample-set\"");
        members.put("role", "\"screening\"");
        members.put("title", "\"sample question " + key + "\"");
        members.put("tags", "[\"imported\"]");
        members.put("question", "\"{question}\"");
        members.put("facts", "\"Subject: {subject}\"");
        members.put("choices", "[\"{option_a}\",\"{option_b}\"]");
        members.put("answer_schema", "{\"type\":\"object\"}");
        members.put("target", "{\"answer\":\"{option_a}\",\"rationale\":\"the first option holds\","
                + "\"alternatives\":[],\"items\":[],\"expected_empty\":false}");
        members.put("grading", "{\"method\":\"exact_choice\",\"judge\":null,\"pass_threshold\":null}");
        members.put("review", "{\"status\":\"draft\",\"by\":null,\"date\":null,\"notes\":null}");
        return members;
    }

    private static String object(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** The members of a valid overlay with two rows, each with its JSON value. */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("source", "\"sample-set\"");
        members.put("file", "\"data/test.csv\"");
        members.put("key_field", "\"question_id\"");
        members.put("created", "\"2026-10-14\"");
        members.put("rows", "[" + object(rowMembers("q-1", "q-1")) + "," + object(rowMembers("q-2", "q-2")) + "]");
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return object(members).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] with(String field, String value) {
        var members = members();
        members.put(field, value);
        return document(members);
    }

    private static byte[] withRow(String field, String value) {
        var row = rowMembers("q-1", "q-1");
        row.put(field, value);
        return with("rows", "[" + object(row) + "]");
    }

    private static StoreRefusal refusalOf(byte[] document) {
        return assertThrows(StoreRefusal.class, () -> SourceOverlayCodec.read(FILE, document));
    }

    private static void assertUnreadable(StoreRefusal refusal, String detail) {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file(), "the refusal names the file"),
                () -> assertTrue(refusal.getMessage().startsWith(FILE.toString()), refusal::getMessage),
                () -> assertTrue(refusal.getMessage().contains(detail), refusal::getMessage));
    }

    @Nested
    @DisplayName("a valid overlay")
    class Valid {

        @Test
        @DisplayName("reads with every member of the overlay and of a row")
        void reads() {
            var overlay = SourceOverlayCodec.read(FILE, document(members()));

            var row = overlay.rows().getFirst();
            assertAll(
                    () -> assertEquals("sample-set", overlay.source()),
                    () -> assertEquals("data/test.csv", overlay.file()),
                    () -> assertEquals("question_id", overlay.keyField()),
                    () -> assertEquals(LocalDate.of(2026, 10, 14), overlay.created()),
                    () -> assertEquals(2, overlay.rows().size()),
                    () -> assertEquals("q-1", row.key()),
                    () -> assertEquals("screening/sample-set/q-1", row.itemId()),
                    () -> assertEquals("screening", row.role()),
                    () -> assertEquals(List.of("imported"), row.tags()),
                    () -> assertEquals("{question}", row.question()),
                    () -> assertEquals("Subject: {subject}", row.facts()),
                    () -> assertEquals(List.of("{option_a}", "{option_b}"), row.choices()),
                    () -> assertEquals(JsonValue.parse("{\"type\": \"object\"}".getBytes(StandardCharsets.UTF_8)),
                            row.answerSchema()),
                    () -> assertEquals("{option_a}", row.target().answer()),
                    () -> assertEquals(GradingMethod.EXACT_CHOICE, row.grading().method()),
                    () -> assertNull(row.grading().passThreshold()),
                    () -> assertEquals(ReviewStatus.DRAFT, row.review().status()));
        }

        @Test
        @DisplayName("an open row with rubric items, a threshold and a confirmed review reads")
        void openRow() {
            var row = rowMembers("q-1", "q-1");
            row.put("choices", "null");
            row.put("answer_schema", "null");
            row.put("target", "{\"answer\":null,\"rationale\":\"r\",\"alternatives\":[\"a\"],"
                    + "\"items\":[{\"id\":\"d1\",\"description\":\"names the defect\"}],\"expected_empty\":false}");
            row.put("grading", "{\"method\":\"rubric_items\",\"judge\":\"strict\",\"pass_threshold\":0.75}");
            row.put("review", "{\"status\":\"confirmed\",\"by\":\"operator\",\"date\":\"2026-10-14\","
                    + "\"notes\":\"checked\"}");

            var overlay = SourceOverlayCodec.read(FILE, with("rows", "[" + object(row) + "]"));

            var read = overlay.rows().getFirst();
            assertAll(
                    () -> assertNull(read.choices()),
                    () -> assertNull(read.answerSchema()),
                    () -> assertNull(read.target().answer()),
                    () -> assertEquals(List.of(new RubricItem("d1", "names the defect")), read.target().items()),
                    () -> assertEquals(new BigDecimal("0.75"), read.grading().passThreshold()),
                    () -> assertEquals("strict", read.grading().judge()),
                    () -> assertEquals(LocalDate.of(2026, 10, 14), read.review().date()));
        }

        @Test
        @DisplayName("rows selected by their position read without a key field")
        void byPosition() {
            var members = members();
            members.put("key_field", "null");
            members.put("rows", "[" + object(rowMembers("1", "q-1")) + "," + object(rowMembers("12", "q-12")) + "]");

            var overlay = SourceOverlayCodec.read(FILE, document(members));

            assertAll(
                    () -> assertNull(overlay.keyField()),
                    () -> assertEquals("12", overlay.rows().get(1).key()));
        }

        @Test
        @DisplayName("is written and read back equal, and written again byte for byte")
        void roundTrip() {
            var overlay = SourceOverlayCodec.read(FILE, document(members()));

            var written = SourceOverlayCodec.write(overlay);
            var reread = SourceOverlayCodec.read(FILE, written);

            assertAll(
                    () -> assertEquals(overlay, reread),
                    () -> assertArrayEquals(written, SourceOverlayCodec.write(reread)));
        }
    }

    @Nested
    @DisplayName("the format version and the declared fields")
    class DeclaredFields {

        @Test
        @DisplayName("an older format version is refused as outdated")
        void outdated() {
            var refusal = refusalOf(with("format_version", "0"));

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()));
        }

        @Test
        @DisplayName("an undeclared field of the overlay is refused")
        void undeclared() {
            assertUnreadable(refusalOf(with("licence", "\"MIT\"")), "undeclared field 'licence'");
        }

        @Test
        @DisplayName("an undeclared field of a row is refused")
        void undeclaredRowField() {
            assertUnreadable(refusalOf(withRow("group", "\"g\"")), "undeclared field 'group'");
        }

        @Test
        @DisplayName("a field twice is refused")
        void twice() {
            var text = new String(document(members()), StandardCharsets.UTF_8)
                    .replace("\"source\":", "\"source\":\"other\",\"source\":");

            assertUnreadable(refusalOf(text.getBytes(StandardCharsets.UTF_8)), "field 'source' twice");
        }

        @ParameterizedTest(name = "missing {0}")
        @MethodSource("de.planmarshall.core.role.corpus.SourceOverlayCodecTest#overlayFields")
        @DisplayName("a missing field of the overlay is refused, a nullable one too")
        void missing(String field) {
            var members = members();
            members.remove(field);

            assertUnreadable(refusalOf(document(members)), "field '%s' of the overlay is missing".formatted(field));
        }

        @ParameterizedTest(name = "missing {0}")
        @MethodSource("de.planmarshall.core.role.corpus.SourceOverlayCodecTest#rowFields")
        @DisplayName("a missing field of a row is refused, a nullable one too")
        void missingRowField(String field) {
            var row = rowMembers("q-1", "q-1");
            row.remove(field);

            assertUnreadable(refusalOf(with("rows", "[" + object(row) + "]")),
                    "field '%s' of the rows[0] is missing".formatted(field));
        }
    }

    static Stream<String> overlayFields() {
        return Stream.of("source", "file", "key_field", "created", "rows");
    }

    static Stream<String> rowFields() {
        return Stream.of("key", "id", "topic", "role", "title", "tags", "question", "facts", "choices",
                "answer_schema", "target", "grading", "review");
    }

    static Stream<Arguments> malformedOverlayValues() {
        return Stream.of(
                Arguments.of("a source name with upper case", "source", "\"Sample\"", "'source'"),
                Arguments.of("a file escaping the repository", "file", "\"../test.csv\"", "'file'"),
                Arguments.of("a file with a .. segment behind U+0085", "file", "\"data\\u0085/../test.csv\"", "'file'"),
                Arguments.of("a file with a .. segment behind U+2028", "file", "\"data\\u2028/../test.csv\"", "'file'"),
                Arguments.of("a file with a .. segment behind U+2029", "file", "\"data/x\\u2029/..\"", "'file'"),
                Arguments.of("an empty key field", "key_field", "\"\"", "'key_field'"),
                Arguments.of("a key field of line feeds only", "key_field", "\"\\n\\n\"", "'key_field'"),
                Arguments.of("a date in another shape", "created", "\"2026-10-4\"", "'created'"),
                Arguments.of("a date with a five-digit signed year", "created", "\"+12345-01-01\"",
                        "'created' is not in the one shape of a date"),
                Arguments.of("a date with a negative year", "created", "\"-0001-01-01\"",
                        "'created' is not in the one shape of a date"),
                Arguments.of("a date in the shape that names no day", "created", "\"2026-02-30\"",
                        "'created' is not a date"),
                Arguments.of("no row", "rows", "[]", "'rows'"),
                Arguments.of("an item twice", "rows",
                        "[" + object(rowMembers("q-1", "q-1")) + "," + object(rowMembers("q-2", "q-1")) + "]",
                        "generates the item 'screening/sample-set/q-1' twice"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedOverlayValues")
    @DisplayName("an overlay value outside its format is refused naming the file")
    void malformedOverlayValue(String name, String field, String value, String detail) {
        assertUnreadable(refusalOf(with(field, value)), detail);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"2026-10-14", "0001-01-01", "9999-12-31"})
    @DisplayName("a date in the schema's shape with a four-digit year is read")
    void fourDigitYear(String date) {
        var overlay = SourceOverlayCodec.read(FILE, with("created", "\"" + date + "\""));

        assertEquals(LocalDate.parse(date), overlay.created());
    }

    @Test
    @DisplayName("a key that is no position is refused without a key field")
    void keyWithoutKeyField() {
        var members = members();
        members.put("key_field", "null");

        assertUnreadable(refusalOf(document(members)), "must be a position from 1");
    }

    static Stream<Arguments> malformedRowValues() {
        return Stream.of(
                Arguments.of("an empty key", "key", "\"\"", "'key'"),
                Arguments.of("a key of a line feed only", "key", "\"\\n\"", "'key'"),
                Arguments.of("a slug with a slash", "id", "\"a/b\"", "'id'"),
                Arguments.of("a topic with upper case", "topic", "\"Screening\"", "'topic'"),
                Arguments.of("a title longer than 120 characters", "title", "\"" + "t".repeat(121) + "\"",
                        "'title' is longer than 120 characters"),
                Arguments.of("one choice", "choices", "[\"only\"]", "'choices'"),
                Arguments.of("an undeclared grading method", "grading",
                        "{\"method\":\"vote\",\"judge\":null,\"pass_threshold\":null}", "'method'"),
                Arguments.of("a threshold above 1", "grading",
                        "{\"method\":\"label\",\"judge\":null,\"pass_threshold\":1.5}", "'pass_threshold'"),
                Arguments.of("a threshold that is a string", "grading",
                        "{\"method\":\"label\",\"judge\":null,\"pass_threshold\":\"0.5\"}",
                        "'pass_threshold' must be a number"),
                Arguments.of("an undeclared review status", "review",
                        "{\"status\":\"approved\",\"by\":null,\"date\":null,\"notes\":null}", "'status'"),
                Arguments.of("a missing expected_empty", "target",
                        "{\"answer\":null,\"rationale\":\"r\",\"alternatives\":[],\"items\":[]}",
                        "field 'expected_empty' of the target is missing"),
                Arguments.of("a rubric item without description", "target",
                        "{\"answer\":null,\"rationale\":\"r\",\"alternatives\":[],\"items\":[{\"id\":\"d\"}],"
                                + "\"expected_empty\":false}",
                        "field 'description' of the items[0] is missing"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRowValues")
    @DisplayName("a row value outside its format is refused naming the file")
    void malformedRowValue(String name, String field, String value, String detail) {
        assertUnreadable(refusalOf(withRow(field, value)), detail);
    }
}
