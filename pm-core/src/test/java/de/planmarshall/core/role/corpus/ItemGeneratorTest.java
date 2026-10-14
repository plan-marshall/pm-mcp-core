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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;

/**
 * The generation of a corpus item from one selected row of a fetched source (PM-WORK-6): the overlay frames the item,
 * the row fills its templates, the provenance names the row, and the item is valid against
 * {@code imported-item.schema.json}; a template naming a field the row lacks is refused.
 */
@DisplayName("Item generator")
class ItemGeneratorTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final int LINE = 3;

    /**
     * @param document an imported item as written
     * @return the violations of {@code imported-item.schema.json}; empty for a valid item
     */
    static Set<?> violations(byte[] document) {
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(CorpusSchemaResourcesTest.resource("imported-item.schema.json"));
        return schema.validate(new String(document, UTF_8), InputFormat.JSON);
    }

    private static Map<String, String> fields() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("question_id", "q-1");
        fields.put("question", "Is the sky blue?");
        fields.put("subject", "weather");
        fields.put("option_a", "yes");
        fields.put("option_b", "no");
        fields.put("diff", "drop the null check");
        return fields;
    }

    private static SourceOverlay overlay() {
        return CorpusSchemaResourcesTest.overlay("question_id", "q-1", "q-2");
    }

    private static ImportedItem generate(SourceOverlay.Row row, Map<String, String> fields) {
        return ItemGenerator.generate(CorpusSchemaResourcesTest.manifest(), overlay(), row,
                new SourceRow(LINE, fields));
    }

    @Nested
    @DisplayName("a closed row")
    class ClosedRow {

        @Test
        @DisplayName("gives the item the overlay frames, filled from the row")
        void generates() {
            var item = generate(overlay().rows().getFirst(), fields());

            assertAll(
                    () -> assertEquals("screening/sample-set/q-1", item.id()),
                    () -> assertEquals("screening/sample-set", item.topic()),
                    () -> assertEquals("screening", item.role()),
                    () -> assertEquals("closed question", item.title()),
                    () -> assertEquals(List.of("imported"), item.tags()),
                    () -> assertEquals("Is the sky blue?", item.input().question()),
                    () -> assertEquals("Subject: weather", item.input().facts()),
                    () -> assertEquals(List.of("yes", "no"), item.input().choices()),
                    () -> assertEquals("yes", item.target().answer()),
                    () -> assertEquals("the first holds", item.target().rationale()),
                    () -> assertEquals(GradingMethod.LABEL, item.grading().method()),
                    () -> assertEquals(ReviewStatus.DRAFT, item.review().status()),
                    () -> assertEquals(ItemGenerator.CANARY, item.canary()));
        }

        @Test
        @DisplayName("names the row it was generated from in a fetched provenance")
        void provenance() {
            var provenance = generate(overlay().rows().getFirst(), fields()).provenance();

            assertAll(
                    () -> assertEquals(LocalDate.of(2026, 10, 14), provenance.created()),
                    () -> assertEquals(new Origin(URI.create("https://github.com/example/sample-set"), COMMIT,
                            "data/test.csv", LINE), provenance.origin()),
                    () -> assertEquals(List.of("2026-10-14: generated from line 3 of data/test.csv of "
                                    + "https://github.com/example/sample-set at commit " + COMMIT
                                    + ", selected and framed by the overlay of the source sample-set."),
                            provenance.history()));
        }

        @Test
        @DisplayName("is valid against imported-item.schema.json")
        void valid() {
            var item = generate(overlay().rows().getFirst(), fields());

            assertEquals(Set.of(), violations(ImportedItemCodec.write(item)));
        }
    }

    @Nested
    @DisplayName("an open row")
    class OpenRow {

        @Test
        @DisplayName("keeps the open answer, the alternatives and the rubric items the overlay writes")
        void generates() {
            var item = generate(overlay().rows().get(1), fields());

            assertAll(
                    () -> assertEquals("review/sample-set/q-2", item.id()),
                    () -> assertEquals("Review: drop the null check", item.input().question()),
                    () -> assertEquals("", item.input().facts()),
                    () -> assertNull(item.input().choices()),
                    () -> assertNull(item.target().answer()),
                    () -> assertEquals(List.of("alternative"), item.target().alternatives()),
                    () -> assertEquals(List.of(new RubricItem("d1", "the defect")), item.target().items()),
                    () -> assertEquals(GradingMethod.RUBRIC_PROPERTIES, item.grading().method()));
        }

        @Test
        @DisplayName("is valid against imported-item.schema.json")
        void valid() {
            var item = generate(overlay().rows().get(1), fields());

            assertEquals(Set.of(), violations(ImportedItemCodec.write(item)));
        }
    }

    @Nested
    @DisplayName("a template naming a field the row lacks")
    class MissingField {

        @Test
        @DisplayName("in the question is refused, naming the field and the line")
        void question() {
            var fields = fields();
            fields.remove("question");
            var row = overlay().rows().getFirst();

            var refusal = assertThrows(IllegalArgumentException.class, () -> generate(row, fields));

            assertEquals("the row at line 3 has no field 'question' the template names", refusal.getMessage());
        }

        @Test
        @DisplayName("in a choice is refused")
        void choice() {
            var fields = fields();
            fields.remove("option_b");
            var row = overlay().rows().getFirst();

            var refusal = assertThrows(IllegalArgumentException.class, () -> generate(row, fields));

            assertTrue(refusal.getMessage().contains("'option_b'"), refusal::getMessage);
        }
    }

    @ParameterizedTest(name = "''{0}'' gives ''{1}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "plain text|plain text",
            "{a} and {b}|x and y",
            "{{a}}|{a}",
            "{{{a}}}|{x}",
            "}}{a}{{|}x{"})
    @DisplayName("a template fills its fields and keeps doubled braces as one literal brace")
    void fills(String template, String expected) {
        assertEquals(expected, ItemGenerator.fill(template, Map.of("a", "x", "b", "y"), LINE));
    }

    @ParameterizedTest(name = "''{0}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{missing}|has no field 'missing'",
            "{}|has no field ''",
            "{a|opens a field it does not close",
            "a}|closes a field it did not open",
            "{{a}|closes a field it did not open"})
    @DisplayName("a template naming no field of the row, or with a brace opening or closing nothing, is refused")
    void refuses(String template, String detail) {
        var fields = Map.of("a", "x");

        var refusal = assertThrows(IllegalArgumentException.class, () -> ItemGenerator.fill(template, fields, LINE));

        assertTrue(refusal.getMessage().contains(detail), refusal::getMessage);
    }
}
