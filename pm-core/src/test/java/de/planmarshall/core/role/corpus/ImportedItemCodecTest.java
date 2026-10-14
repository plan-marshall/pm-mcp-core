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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Input;
import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.ImportedItem.Provenance;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * An imported item (PM-WORK-6): the corpus item format, tightened, written deterministically in the order of the
 * schema and read back in exactly that shape; a refusal names the file.
 */
@DisplayName("Imported item codec")
class ImportedItemCodecTest {

    private static final Path FILE = Path.of("/cache/items/screening/sample-set/q-1.json");
    private static final URI REPOSITORY = URI.create("https://github.com/example/sample-set");
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String CANARY = "corpus canary 0000";

    /** A closed item with only the required members. */
    static ImportedItem closedItem() {
        return new ImportedItem("screening/sample-set/q-1", "screening/sample-set", "screening", "sample question",
                List.of(), new Input("Which holds?", "Subject: logic", List.of("first", "second")),
                new Target("first", "the first holds", List.of(), List.of(), false),
                new Grading(GradingMethod.EXACT_CHOICE, null, null),
                new Provenance(LocalDate.of(2026, 10, 14), new Origin(REPOSITORY, COMMIT, "data/test.csv", 2),
                        List.of("2026-10-14: generated from row 2 of data/test.csv")),
                new Review(ReviewStatus.DRAFT, null, null, null), CANARY);
    }

    /** An open item with every optional member. */
    static ImportedItem openItem() {
        return new ImportedItem("review/sample-set/q-7", "review/sample-set", "review", "open question",
                List.of("imported", "review"), new Input("Review the change.", "diff --git a b", null),
                new Target("report the defect", "one defect is seeded", List.of("name the defect"),
                        List.of(new RubricItem("d1", "the off-by-one")), true),
                new Grading(GradingMethod.RUBRIC_ITEMS, "strict judge", new BigDecimal("0.75")),
                new Provenance(LocalDate.of(2026, 10, 14), new Origin(REPOSITORY, COMMIT, "data/big.jsonl", 7),
                        List.of("2026-10-14: generated", "2026-10-15: reviewed")),
                new Review(ReviewStatus.CONFIRMED, "operator", LocalDate.of(2026, 10, 15), "checked"), CANARY);
    }

    private static String text(ImportedItem item) {
        return new String(ImportedItemCodec.write(item), StandardCharsets.UTF_8);
    }

    private static StoreRefusal refusalOf(String document) {
        return assertThrows(StoreRefusal.class,
                () -> ImportedItemCodec.read(FILE, document.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertUnreadable(StoreRefusal refusal, String detail) {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file(), "the refusal names the file"),
                () -> assertTrue(refusal.getMessage().startsWith(FILE.toString()), refusal::getMessage),
                () -> assertTrue(refusal.getMessage().contains(detail), refusal::getMessage));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        static Stream<Arguments> items() {
            return Stream.of(Arguments.of("closed item, required members only", closedItem()),
                    Arguments.of("open item, every optional member", openItem()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("items")
        @DisplayName("a written item reads back equal and writes again byte for byte")
        void byteForByte(String name, ImportedItem item) {
            var written = ImportedItemCodec.write(item);

            var reread = ImportedItemCodec.read(FILE, written);

            assertAll(
                    () -> assertEquals(item, reread),
                    () -> assertArrayEquals(written, ImportedItemCodec.write(reread)));
        }

        @Test
        @DisplayName("the members are written in the order of the schema")
        void schemaOrder() {
            var text = text(openItem());

            var order = List.of("\"schema_version\" :", "\"id\" :", "\"topic\" :", "\"role\" :", "\"title\" :",
                    "\"tags\" :", "\"input\" :", "\"target\" :", "\"grading\" :", "\"provenance\" :", "\"review\" :",
                    "\"canary\" :");
            for (var index = 1; index < order.size(); index++) {
                var before = order.get(index - 1);
                var after = order.get(index);
                assertTrue(text.indexOf(before) < text.indexOf(after), () -> before + " before " + after);
            }
        }

        @Test
        @DisplayName("an optional member without a value is not written")
        void optionalMembersAbsent() {
            var text = text(closedItem());

            assertAll(
                    () -> assertFalse(text.contains("\"tags\""), text),
                    () -> assertFalse(text.contains("\"alternatives\""), text),
                    () -> assertFalse(text.contains("\"items\""), text),
                    () -> assertFalse(text.contains("\"expected_empty\""), text),
                    () -> assertFalse(text.contains("\"judge\""), text),
                    () -> assertFalse(text.contains("\"pass_threshold\""), text),
                    () -> assertFalse(text.contains("\"by\""), text),
                    () -> assertFalse(text.contains("null"), text),
                    () -> assertTrue(text.contains("\"kind\" : \"fetched\""), text));
        }
    }

    @Nested
    @DisplayName("the schema version and the declared members")
    class DeclaredMembers {

        @Test
        @DisplayName("another schema version is refused")
        void otherSchemaVersion() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"schema_version\" : 1", "\"schema_version\" : 2")),
                    "'schema_version' 2 is not 1");
        }

        @Test
        @DisplayName("a missing schema version is refused")
        void missingSchemaVersion() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"schema_version\" : 1,", "")),
                    "'schema_version' is missing");
        }

        @Test
        @DisplayName("a document that is no object is refused")
        void notAnObject() {
            assertUnreadable(refusalOf("[]"), "not a JSON object");
        }

        @Test
        @DisplayName("an undeclared member is refused")
        void undeclared() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"canary\"", "\"group\" : \"g\",\n  \"canary\"")),
                    "undeclared field 'group'");
        }

        @Test
        @DisplayName("a member twice is refused")
        void twice() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"role\"", "\"role\" : \"x\",\n  \"role\"")),
                    "field 'role' twice");
        }

        @Test
        @DisplayName("a missing required member is refused")
        void missing() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"title\" : \"sample question\",", "")),
                    "field 'title' of the item is missing");
        }

        @Test
        @DisplayName("content after the document is refused")
        void contentAfterDocument() {
            assertUnreadable(refusalOf(text(closedItem()) + "{}"), "content after the document");
        }

        @Test
        @DisplayName("a document that is no JSON is refused")
        void notJson() {
            assertUnreadable(refusalOf(text(closedItem()).replace("\"topic\" :", "\"topic\" ::")), "line");
        }
    }

    static Stream<Arguments> tightenedValues() {
        return Stream.of(
                Arguments.of("another provenance kind", "\"kind\" : \"fetched\"", "\"kind\" : \"extracted\"",
                        "'kind' must be 'fetched'"),
                Arguments.of("present but empty tags", "\"role\" : \"screening\",",
                        "\"role\" : \"screening\", \"tags\" : [],", "'tags' is present but empty"),
                Arguments.of("a null for the choices", "\"choices\" : [ \"first\", \"second\" ]",
                        "\"choices\" : null", "'choices' must be an array"),
                Arguments.of("expected_empty false", "\"rationale\" : \"the first holds\"",
                        "\"rationale\" : \"the first holds\", \"expected_empty\" : false",
                        "'expected_empty' is present but not true"),
                Arguments.of("present but empty rubric items", "\"rationale\" : \"the first holds\"",
                        "\"rationale\" : \"the first holds\", \"items\" : []", "'items' is present but empty"),
                Arguments.of("a repository outside GitHub", "https://github.com/example/sample-set",
                        "https://gitlab.com/example/sample-set", "'repository'"),
                Arguments.of("a short commit", COMMIT, "0123456", "'commit'"),
                Arguments.of("a line 0", "\"line\" : 2", "\"line\" : 0", "'line' must be at least 1"),
                Arguments.of("a missing line", ",\n      \"line\" : 2", "", "field 'line' of the origin is missing"),
                Arguments.of("an id outside its topic", "\"id\" : \"screening/sample-set/q-1\"",
                        "\"id\" : \"screening/other/q-1\"", "is not an item of the topic"),
                Arguments.of("an empty history", "\"history\" : [ \"2026-10-14: generated from row 2 of data/test.csv\" ]",
                        "\"history\" : [ ]", "'history' must have at least one entry"),
                Arguments.of("a date in another shape", "\"created\" : \"2026-10-14\"", "\"created\" : \"20261014\"",
                        "'created'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tightenedValues")
    @DisplayName("a value outside the tightened format is refused naming the file")
    void tightenedValue(String name, String original, String replacement, String detail) {
        var text = text(closedItem());
        assertTrue(text.contains(original), () -> "the fixture carries " + original + ": " + text);

        assertUnreadable(refusalOf(text.replace(original, replacement)), detail);
    }
}
