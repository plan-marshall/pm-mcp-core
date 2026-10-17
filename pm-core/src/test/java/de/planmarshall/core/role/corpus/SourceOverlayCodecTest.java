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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.planmarshall.core.role.corpus.JsonValue.JsonList;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;
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
        members.put("group", "null");
        members.put("question", "\"{question}\"");
        members.put("facts", "\"Subject: {subject}\"");
        members.put("choices", "[\"{option_a}\",\"{option_b}\"]");
        members.put("answer_schema", "{\"type\":\"object\"}");
        members.put("target", "{\"answer\":\"{option_a}\",\"rationale\":\"the first option holds\","
                + "\"alternatives\":[],\"items\":[],\"expected_empty\":false}");
        members.put("grading", "{\"method\":\"exact_choice\",\"judge\":null,\"pass_threshold\":null}");
        members.put("review", "{\"status\":\"draft\",\"by\":null,\"date\":null,\"notes\":null}");
        members.put("contamination", "null");
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
            assertUnreadable(refusalOf(withRow("context", "[]")), "undeclared field 'context'");
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

    /**
     * The names of the row fields the overlay schema declares as required, read from
     * {@code source-overlay.schema.json} at {@code properties.rows.items.required}, so a required field added to the
     * schema is covered here without editing a list that mirrors it.
     */
    static Stream<String> rowFields() {
        var schema = (JsonObject) JsonValue.parse(
                CorpusSchemaResourcesTest.resource("source-overlay.schema.json").getBytes(StandardCharsets.UTF_8));
        var rows = (JsonObject) ((JsonObject) schema.get("properties").orElseThrow()).get("rows").orElseThrow();
        var items = (JsonObject) rows.get("items").orElseThrow();
        return ((JsonList) items.get("required").orElseThrow()).elements().stream()
                .map(field -> ((JsonString) field).value());
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
                        "field 'description' of the items[0] is missing"),
                Arguments.of("an answer_schema object that names a member twice", "answer_schema",
                        "{\"type\":\"object\",\"type\":\"object\"}",
                        "'answer_schema' is not one JSON value: the member 'type' is named twice"),
                Arguments.of("an answer_schema that is a string", "answer_schema", "\"not-an-object\"",
                        "'answer_schema' must be an object or null"),
                Arguments.of("an answer_schema that is an array", "answer_schema", "[\"a\"]",
                        "'answer_schema' must be an object or null"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRowValues")
    @DisplayName("a row value outside its format is refused naming the file")
    void malformedRowValue(String name, String field, String value, String detail) {
        assertUnreadable(refusalOf(withRow(field, value)), detail);
    }

    @Nested
    @DisplayName("the answer schema of a row")
    class AnswerSchema {

        /** A valid row with the given answer schema. */
        private static SourceOverlay.Row row(JsonValue answerSchema) {
            return new SourceOverlay.Row("q-1", "q-1", "screening/sample-set", "screening", "sample question",
                    List.of("imported"), null, "{question}", "Subject: {subject}",
                    List.of("{option_a}", "{option_b}"), answerSchema,
                    new ImportedItem.Target("{option_a}", "the first option holds", List.of(), List.of(), false),
                    new ImportedItem.Grading(GradingMethod.EXACT_CHOICE, null, null),
                    new ImportedItem.Review(ReviewStatus.DRAFT, null, null, null), null);
        }

        static Stream<Arguments> nonObjectValues() {
            return Stream.of(
                    Arguments.of("a string", new JsonValue.JsonString("not-an-object")),
                    Arguments.of("a number", new JsonValue.JsonNumber(new BigDecimal("7"), true)),
                    Arguments.of("a boolean", new JsonValue.JsonBoolean(true)),
                    Arguments.of("an array", new JsonValue.JsonList(List.of(new JsonValue.JsonString("a")))),
                    Arguments.of("a JSON null", new JsonValue.JsonNull()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nonObjectValues")
        @DisplayName("an answer schema that is no object and no null is refused at construction")
        void nonObjectRefused(String name, JsonValue answerSchema) {
            var refused = assertThrows(IllegalArgumentException.class, () -> row(answerSchema));

            assertEquals("'answer_schema' must be an object or null: " + answerSchema.toJson(), refused.getMessage());
        }

        @Test
        @DisplayName("a JSON object and a null are accepted")
        void objectAndNullAccepted() {
            var schema = JsonValue.parse("{\"type\": \"object\"}".getBytes(StandardCharsets.UTF_8));

            assertAll(
                    () -> assertEquals(schema, row(schema).answerSchema()),
                    () -> assertNull(row(null).answerSchema()));
        }
    }

    @Nested
    @DisplayName("the group and the contamination note of a row")
    class GroupAndContamination {

        private static final String GROUP = "sample-set/q {shared}";
        private static final String CONTAMINATION = "shares its subject with screening/sample-set/q-2";

        private static List<String> overlaySchemaErrors(byte[] document) {
            var schema = JsonValue.parse(
                    CorpusSchemaResourcesTest.resource("source-overlay.schema.json").getBytes(StandardCharsets.UTF_8));
            return SchemaSubset.validate(JsonValue.parse(document), schema);
        }

        private static byte[] withMembers(String group, String contamination) {
            var row = rowMembers("q-1", "q-1");
            row.put("group", group);
            row.put("contamination", contamination);
            return with("rows", "[" + object(row) + "]");
        }

        @Test
        @DisplayName("both present are read as written, round trip and are accepted by the schema")
        void present() {
            var document = withMembers("\"" + GROUP + "\"", "\"" + CONTAMINATION + "\"");

            var overlay = SourceOverlayCodec.read(FILE, document);
            var written = SourceOverlayCodec.write(overlay);
            var reread = SourceOverlayCodec.read(FILE, written);

            var row = overlay.rows().getFirst();
            assertAll(
                    () -> assertEquals(GROUP, row.group()),
                    () -> assertEquals(CONTAMINATION, row.contamination()),
                    () -> assertEquals(overlay, reread),
                    () -> assertArrayEquals(written, SourceOverlayCodec.write(reread)),
                    () -> assertEquals(List.of(), overlaySchemaErrors(document)),
                    () -> assertEquals(List.of(), overlaySchemaErrors(written)));
        }

        @Test
        @DisplayName("both null are read as null, written as null and are accepted by the schema")
        void bothNull() {
            var document = withMembers("null", "null");

            var overlay = SourceOverlayCodec.read(FILE, document);
            var written = SourceOverlayCodec.write(overlay);

            var row = overlay.rows().getFirst();
            var text = new String(written, StandardCharsets.UTF_8);
            assertAll(
                    () -> assertNull(row.group()),
                    () -> assertNull(row.contamination()),
                    () -> assertTrue(text.contains("\"group\" : null"), text),
                    () -> assertTrue(text.contains("\"contamination\" : null"), text),
                    () -> assertEquals(overlay, SourceOverlayCodec.read(FILE, written)),
                    () -> assertEquals(List.of(), overlaySchemaErrors(document)));
        }

        @Test
        @DisplayName("the group is written after the tags and the contamination note after the review")
        void schemaOrder() {
            var text = new String(SourceOverlayCodec.write(SourceOverlayCodec.read(FILE,
                    withMembers("\"" + GROUP + "\"", "\"" + CONTAMINATION + "\""))), StandardCharsets.UTF_8);

            assertAll(
                    () -> assertTrue(text.indexOf("\"tags\" :") < text.indexOf("\"group\" :"), text),
                    () -> assertTrue(text.indexOf("\"group\" :") < text.indexOf("\"question\" :"), text),
                    () -> assertTrue(text.indexOf("\"review\" :") < text.indexOf("\"contamination\" :"), text));
        }

        @ParameterizedTest(name = "missing {0}")
        @ValueSource(strings = {"group", "contamination"})
        @DisplayName("an absent member is refused by the codec and by the schema")
        void absent(String field) {
            var row = rowMembers("q-1", "q-1");
            row.remove(field);
            var document = with("rows", "[" + object(row) + "]");

            assertAll(
                    () -> assertUnreadable(refusalOf(document),
                            "field '%s' of the rows[0] is missing".formatted(field)),
                    () -> assertFalse(overlaySchemaErrors(document).isEmpty(), "the schema refuses it"));
        }

        static Stream<Arguments> malformedValues() {
            return Stream.of("group", "contamination").flatMap(field -> Stream.of(
                    Arguments.of(field + " that is a number", field, "7", "'%s' must be a string".formatted(field)),
                    Arguments.of(field + " that is a list", field, "[\"a\"]",
                            "'%s' must be a string".formatted(field)),
                    Arguments.of(field + " that is empty", field, "\"\"",
                            "'%s' must hold a character other than a line feed".formatted(field)),
                    Arguments.of(field + " of a line feed only", field, "\"\\n\"",
                            "'%s' must hold a character other than a line feed".formatted(field))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedValues")
        @DisplayName("a value outside its format is refused by the codec and by the schema")
        void malformed(String name, String field, String value, String detail) {
            var document = withRow(field, value);

            assertAll(
                    () -> assertUnreadable(refusalOf(document), detail),
                    () -> assertFalse(overlaySchemaErrors(document).isEmpty(), "the schema refuses it"));
        }

        @Test
        @DisplayName("a row refuses a group or a contamination note of line feeds only at construction")
        void constructionRefused() {
            var target = new ImportedItem.Target("{option_a}", "r", List.of(), List.of(), false);
            var grading = new ImportedItem.Grading(GradingMethod.EXACT_CHOICE, null, null);
            var review = new ImportedItem.Review(ReviewStatus.DRAFT, null, null, null);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new SourceOverlay.Row("q-1", "q-1",
                            "screening/sample-set", "screening", "t", List.of(), "\n\n", "q", "f", null, null,
                            target, grading, review, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new SourceOverlay.Row("q-1", "q-1",
                            "screening/sample-set", "screening", "t", List.of(), null, "q", "f", null, null,
                            target, grading, review, "")));
        }
    }
}
