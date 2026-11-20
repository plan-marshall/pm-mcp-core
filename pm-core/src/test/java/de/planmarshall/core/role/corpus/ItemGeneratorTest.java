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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.SchemaValidation;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;

/**
 * The generation of a corpus item from one selected row of a fetched source (PM-WORK-6): the overlay frames the item,
 * the row fills its templates, the provenance names the row, and the item is valid against
 * {@code imported-item.schema.json}; a template naming a field the row lacks is refused.
 */
@EnableGeneratorController
@DisplayName("Item generator")
class ItemGeneratorTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final int LINE = 3;
    private static final String QUESTION = Generators.letterStrings(5, 20).next();
    private static final String SUBJECT = Generators.letterStrings(3, 12).next();
    private static final String OPTION_A = "a" + Generators.letterStrings(1, 8).next();
    private static final String OPTION_B = "b" + Generators.letterStrings(1, 8).next();
    private static final String DIFF = Generators.letterStrings(5, 20).next();

    /**
     * @param document an imported item as written
     * @return the violations of {@code imported-item.schema.json}; empty for a valid item
     */
    static List<?> violations(byte[] document) {
        var schema = SchemaValidation.compile(CorpusSchemaResourcesTest.resource("imported-item.schema.json"));
        return SchemaValidation.validate(schema, new String(document, UTF_8));
    }

    private static Map<String, String> fields() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("question_id", "q-1");
        fields.put("question", QUESTION);
        fields.put("subject", SUBJECT);
        fields.put("option_a", OPTION_A);
        fields.put("option_b", OPTION_B);
        fields.put("diff", DIFF);
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
                    () -> assertEquals("screening/sample-set/q-1", item.id(),
                            "the item id comes from the overlay and the key"),
                    () -> assertEquals("screening/sample-set", item.topic(), "the topic comes from the overlay"),
                    () -> assertEquals("screening", item.role(), "the role is the screening role"),
                    () -> assertEquals("closed question", item.title(), "the title is the overlay's closed question"),
                    () -> assertEquals(List.of("imported"), item.tags(), "the tags hold the overlay's imported tag"),
                    () -> assertEquals(QUESTION, item.input().question(), "the question is filled from the row"),
                    () -> assertEquals("Subject: " + SUBJECT, item.input().facts(),
                            "the facts carry the subject prefix"),
                    () -> assertEquals(List.of(OPTION_A, OPTION_B), item.input().choices(),
                            "the choices are the two options of the row"),
                    () -> assertEquals(OPTION_A, item.target().answer(), "the target answer is option A"),
                    () -> assertEquals("the first holds", item.target().rationale(),
                            "the rationale is the literal row text"),
                    () -> assertEquals(GradingMethod.LABEL, item.grading().method(), "the grading method is a label"),
                    () -> assertEquals(ReviewStatus.DRAFT, item.review().status(), "the review status is draft"),
                    () -> assertEquals(ItemGenerator.CANARY, item.canary(), "the canary is the generator's constant"));
        }

        @Test
        @DisplayName("carries the row answer schema verbatim into the input")
        void answerSchema() {
            var row = overlay().rows().getFirst();

            var item = generate(row, fields());

            assertAll(
                    () -> assertNotNull(row.answerSchema(), "the row declares an answer schema"),
                    () -> assertEquals(row.answerSchema(), item.input().answerSchema(),
                            "the answer schema is carried verbatim"));
        }

        @Test
        @ExtendWith(NonLatinDigits.class)
        @DisplayName("names the row it was generated from in a fetched provenance, its line in ASCII digits under any "
                + "locale")
        void provenance() {
            var provenance = generate(overlay().rows().getFirst(), fields()).provenance();

            assertAll(
                    () -> assertEquals(LocalDate.of(2026, 10, 14), provenance.created(),
                            "the provenance date is the overlay's date"),
                    () -> assertEquals(new Origin(URI.create("https://github.com/example/sample-set"), COMMIT,
                                    "data/test.csv", LINE), provenance.origin(),
                            "the origin names the file and its line"),
                    () -> assertEquals(List.of("2026-10-14: generated from line 3 of data/test.csv of "
                                    + "https://github.com/example/sample-set at commit " + COMMIT
                                    + ", selected and framed by the overlay of the source sample-set."),
                            provenance.history(), "the history names the source and its commit"));
        }

        @Test
        @DisplayName("is valid against imported-item.schema.json")
        void valid() {
            var item = generate(overlay().rows().getFirst(), fields());

            assertEquals(List.of(), violations(ImportedItemCodec.write(item)),
                    "the generated item is valid against its schema");
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
                    () -> assertEquals("review/sample-set/q-2", item.id(), "the open id comes from the row key"),
                    () -> assertEquals("Review: " + DIFF, item.input().question(),
                            "the question is the review template filled"),
                    () -> assertEquals("", item.input().facts(), "an open row has empty facts"),
                    () -> assertNull(item.input().choices(), "an open row has no choices"),
                    () -> assertNull(item.target().answer(), "an open row has no target answer"),
                    () -> assertEquals(List.of("alternative"), item.target().alternatives(),
                            "the alternatives hold the overlay's text"),
                    () -> assertEquals(List.of(new RubricItem("d1", "the defect")), item.target().items(),
                            "the rubric items are read from the row"),
                    () -> assertEquals(GradingMethod.RUBRIC_PROPERTIES, item.grading().method(),
                            "the grading method is rubric properties"));
        }

        @Test
        @DisplayName("is valid against imported-item.schema.json")
        void valid() {
            var item = generate(overlay().rows().get(1), fields());

            assertEquals(List.of(), violations(ImportedItemCodec.write(item)),
                    "the open item is valid against its schema");
        }
    }

    @Nested
    @DisplayName("a template naming a field the row lacks")
    class MissingField {

        @Test
        @ExtendWith(NonLatinDigits.class)
        @DisplayName("in the question is refused, naming the field and the line in ASCII digits under any locale")
        void question() {
            var fields = fields();
            fields.remove("question");
            var row = overlay().rows().getFirst();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> generate(row, fields), "a template naming a missing field is refused");

            assertEquals("the row at line 3 has no field 'question' the template names", refusal.getMessage(),
                    "the refusal names the field and the line");
        }

        @Test
        @DisplayName("in a choice is refused")
        void choice() {
            var fields = fields();
            fields.remove("option_b");
            var row = overlay().rows().getFirst();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> generate(row, fields), "a choice naming a missing field is refused");

            assertTrue(refusal.getMessage().contains("'option_b'"), "the refusal names the missing choice field");
        }
    }

    /**
     * The answer and the alternatives are templates as the question is, as the overlay's documents say: a literal brace
     * there is written doubled, a single one is read as a placeholder. The rationale and the rubric items are literal.
     */
    @Nested
    @DisplayName("a literal brace in the reference answer or an alternative")
    class LiteralBrace {

        private static final String CODE = "if (x == null) { return; }";
        private static final String ESCAPED = "if (x == null) {{ return; }}";

        private static SourceOverlay.Row withTarget(Target target) {
            var open = overlay().rows().get(1);
            return new SourceOverlay.Row(open.key(), open.slug(), open.topic(), open.role(), open.title(), open.tags(),
                    open.group(), open.question(), open.facts(), open.choices(), open.answerSchema(), target,
                    open.grading(), open.review(), open.contamination());
        }

        @Test
        @DisplayName("written single in an alternative is refused as a field the row lacks")
        void singleInAlternative() {
            var row = withTarget(new Target(null, "r", List.of(CODE), List.of(new RubricItem("d1", "d")), false));
            var fields = fields();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> generate(row, fields), "a single brace in an alternative is a field");

            assertTrue(refusal.getMessage().contains("has no field ' return; '"),
                    "the refusal names the brace text as a field");
        }

        @Test
        @DisplayName("written single in the answer is refused as a field the row lacks")
        void singleInAnswer() {
            var row = withTarget(new Target(CODE, "r", List.of(), List.of(new RubricItem("d1", "d")), false));
            var fields = fields();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> generate(row, fields), "a single brace in the answer is a field");

            assertTrue(refusal.getMessage().contains("has no field ' return; '"),
                    "the refusal names the brace text as a field");
        }

        @Test
        @DisplayName("written doubled in the answer and an alternative is one literal brace; the rationale is literal")
        void doubled() {
            var row = withTarget(new Target(ESCAPED, CODE, List.of(ESCAPED),
                    List.of(new RubricItem("d1", CODE)), false));

            var item = generate(row, fields());

            assertAll(
                    () -> assertEquals(CODE, item.target().answer(), "a doubled brace is one literal brace"),
                    () -> assertEquals(List.of(CODE), item.target().alternatives(),
                            "the alternative keeps its one literal brace"),
                    () -> assertEquals(CODE, item.target().rationale(), "the rationale is literal, braces included"),
                    () -> assertEquals(List.of(new RubricItem("d1", CODE)), item.target().items(),
                            "the rubric item stays literal too"));
        }
    }

    /**
     * The group and the contamination note are literal, as the rationale is: they are copied as the overlay row writes
     * them, and a single brace in them is no placeholder.
     */
    @Nested
    @DisplayName("the group and the contamination note")
    class GroupAndContamination {

        private static final String GROUP = "sample-set {pair}";
        private static final String CONTAMINATION = "names screening/sample-set/q-1 { and } of the other item";

        private static SourceOverlay.Row withMembers(String group, String contamination) {
            var closed = overlay().rows().getFirst();
            return new SourceOverlay.Row(closed.key(), closed.slug(), closed.topic(), closed.role(), closed.title(),
                    closed.tags(), group, closed.question(), closed.facts(), closed.choices(), closed.answerSchema(),
                    closed.target(), closed.grading(), closed.review(), contamination);
        }

        @Test
        @DisplayName("are carried unchanged, a single brace included, and the item is valid against the schema")
        void carried() {
            var item = generate(withMembers(GROUP, CONTAMINATION), fields());

            assertAll(
                    () -> assertEquals(GROUP, item.group(), "the group is carried unchanged"),
                    () -> assertEquals(CONTAMINATION, item.contamination(), "the contamination note keeps its braces"),
                    () -> assertEquals(List.of(), violations(ImportedItemCodec.write(item)),
                            "the item with a brace is still valid"));
        }

        @Test
        @DisplayName("are absent from the item when the row has none")
        void absent() {
            var item = generate(withMembers(null, null), fields());

            var text = new String(ImportedItemCodec.write(item), UTF_8);
            assertAll(
                    () -> assertNull(item.group(), "a row without a group gives no group"),
                    () -> assertNull(item.contamination(), "a row without a note gives no note"),
                    () -> assertFalse(text.contains("\"group\""), "the item is written without a group"),
                    () -> assertFalse(text.contains("\"contamination\""), "the item is written without a note"));
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
        assertEquals(expected, ItemGenerator.fill(template, Map.of("a", "x", "b", "y"), LINE),
                "the template fills its fields and keeps braces");
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

        var refusal = assertThrows(IllegalArgumentException.class,
                () -> ItemGenerator.fill(template, fields, LINE), "a template that names no field is refused");

        assertTrue(refusal.getMessage().contains(detail), "the refusal message names what is wrong");
    }
}
