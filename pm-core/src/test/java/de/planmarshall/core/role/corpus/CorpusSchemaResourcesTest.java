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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.SourceManifest.FetchKind;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;

/**
 * The three schema resources of the corpus formats (PM-WORK-6): every document a codec writes is valid against its
 * schema, and every schema is closed at every level and uses only the keyword subset the corpus validator evaluates.
 */
@DisplayName("Corpus schema resources")
class CorpusSchemaResourcesTest {

    private static final String MANIFEST_SCHEMA = "source-manifest.schema.json";
    private static final String OVERLAY_SCHEMA = "source-overlay.schema.json";
    private static final String ITEM_SCHEMA = "imported-item.schema.json";

    /** The validation keywords the corpus validator evaluates. */
    private static final Set<String> SUBSET = Set.of("type", "required", "additionalProperties", "properties",
            "items", "enum", "const", "pattern", "minItems", "maxLength", "minimum", "maximum");

    /** The annotation keywords that carry no validation. */
    private static final Set<String> ANNOTATIONS = Set.of("$schema", "title", "description");

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    /** The member whose value is a literal JSON Schema, not a record of the corpus formats. */
    private static final String ANSWER_SCHEMA_MEMBER = "answer_schema";

    private static final JsonValue ANSWER_SCHEMA = JsonValue.parse("{\"type\": \"object\"}".getBytes(UTF_8));

    static String resource(String name) {
        try (var in = CorpusSchemaResourcesTest.class.getResourceAsStream(name)) {
            assertNotNull(in, () -> "the schema resource " + name + " exists beside the package");
            return new String(in.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<?> validate(String schemaName, byte[] document) {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(resource(schemaName));
        return schema.validate(new String(document, UTF_8), InputFormat.JSON);
    }

    static SourceManifest manifest() {
        return new SourceManifest("sample-set", URI.create("https://github.com/example/sample-set"), COMMIT,
                "CC-BY-4.0", FetchKind.GIT, List.of(new SourceFile("data/test.csv", "a".repeat(64), false),
                        new SourceFile("data/big.jsonl", "b".repeat(64), true)));
    }

    static SourceOverlay overlay(String keyField, String firstKey, String secondKey) {
        var closed = new Row(firstKey, "q-1", "screening/sample-set", "screening", "closed question",
                List.of("imported"), "{question}", "Subject: {subject}", List.of("{option_a}", "{option_b}"),
                ANSWER_SCHEMA,
                new Target("{option_a}", "the first holds", List.of(), List.of(), false),
                new Grading(GradingMethod.LABEL, null, null), new Review(ReviewStatus.DRAFT, null, null, null));
        var open = new Row(secondKey, "q-2", "review/sample-set", "review", "open question", List.of(),
                "Review: {diff}", "", null, ANSWER_SCHEMA,
                new Target(null, "one defect", List.of("alternative"), List.of(new RubricItem("d1", "the defect")),
                        false),
                new Grading(GradingMethod.RUBRIC_PROPERTIES, "judge", new BigDecimal("0.5")),
                new Review(ReviewStatus.CONFIRMED, "operator", LocalDate.of(2026, 10, 14), "checked"));
        return new SourceOverlay("sample-set", "data/test.csv", keyField, LocalDate.of(2026, 10, 14),
                List.of(closed, open));
    }

    static Stream<Arguments> writtenDocuments() {
        return Stream.of(
                Arguments.of("manifest", MANIFEST_SCHEMA, SourceManifestCodec.write(manifest())),
                Arguments.of("overlay with a key field", OVERLAY_SCHEMA,
                        SourceOverlayCodec.write(overlay("question_id", "q-1", "q-2"))),
                Arguments.of("overlay by position", OVERLAY_SCHEMA, SourceOverlayCodec.write(overlay(null, "1", "2"))),
                Arguments.of("closed imported item", ITEM_SCHEMA,
                        ImportedItemCodec.write(ImportedItemCodecTest.closedItem())),
                Arguments.of("open imported item", ITEM_SCHEMA,
                        ImportedItemCodec.write(ImportedItemCodecTest.openItem())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writtenDocuments")
    @DisplayName("a document a codec writes is valid against its schema resource")
    void writtenDocumentIsValid(String name, String schemaName, byte[] document) {
        assertEquals(Set.of(), validate(schemaName, document), () -> "violations of " + new String(document, UTF_8));
    }

    @Nested
    @DisplayName("the validator is a real verdict")
    class Controls {

        @Test
        @DisplayName("a manifest with an undeclared member is rejected")
        void manifestUndeclaredMember() {
            var text = new String(SourceManifestCodec.write(manifest()), UTF_8)
                    .replace("\"licence\"", "\"homepage\" : \"x\", \"licence\"");

            assertFalse(validate(MANIFEST_SCHEMA, text.getBytes(UTF_8)).isEmpty());
        }

        @Test
        @DisplayName("an imported item of another provenance kind is rejected")
        void itemOtherKind() {
            var text = new String(ImportedItemCodec.write(ImportedItemCodecTest.closedItem()), UTF_8)
                    .replace("\"fetched\"", "\"extracted\"");

            assertFalse(validate(ITEM_SCHEMA, text.getBytes(UTF_8)).isEmpty());
        }

        @Test
        @DisplayName("an overlay row with an undeclared member deep inside is rejected")
        void overlayDeepUndeclared() {
            var text = new String(SourceOverlayCodec.write(overlay(null, "1", "2")), UTF_8)
                    .replace("\"status\" : \"draft\"", "\"status\" : \"draft\", \"extra\" : 1");

            assertFalse(validate(OVERLAY_SCHEMA, text.getBytes(UTF_8)).isEmpty());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {MANIFEST_SCHEMA, OVERLAY_SCHEMA, ITEM_SCHEMA})
    @DisplayName("a schema uses only the keyword subset, and is closed at every level apart from a literal answer schema")
    void keywordSubsetAndClosed(String schemaName) throws Exception {
        var problems = new ArrayList<String>();
        try (var parser = new JsonFactory().createParser(resource(schemaName))) {
            parser.nextToken();
            walk(parser, "$", problems);
        }

        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("an object schema that names no property is rejected when it is not closed")
    void objectSchemaWithoutPropertiesIsRejected() throws Exception {
        assertFalse(problemsOf("{\"type\": \"object\"}").isEmpty());
    }

    @Test
    @DisplayName("a literal answer schema is left open")
    void literalAnswerSchemaIsLeftOpen() throws Exception {
        var closedRecord = "{\"type\": \"object\", \"additionalProperties\": false, \"properties\": "
                + "{ \"answer_schema\": { \"type\": \"object\" } }}";

        assertEquals(List.of(), problemsOf(closedRecord));
    }

    private static List<String> problemsOf(String schema) throws Exception {
        var problems = new ArrayList<String>();
        try (var parser = new JsonFactory().createParser(schema.getBytes(UTF_8))) {
            parser.nextToken();
            walk(parser, "$", problems);
        }
        return problems;
    }

    /**
     * Walks one schema object, positioned on its start; reports a foreign keyword and an object schema that is not
     * closed — whether it enumerates properties or only admits an object. The value of an {@code answer_schema} member
     * is a literal JSON Schema the corpus holds as an opaque slot, not a record of the format, so it is left open by
     * design: the walk does not descend into it, and its keywords are not held to the subset.
     */
    private static void walk(JsonParser parser, String path, List<String> problems) throws IOException {
        var keywords = new HashSet<String>();
        var closed = false;
        var objectType = false;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var keyword = parser.currentName();
            keywords.add(keyword);
            var token = parser.nextToken();
            if (!SUBSET.contains(keyword) && !ANNOTATIONS.contains(keyword)) {
                problems.add(path + ": keyword '" + keyword + "' is outside the subset");
            }
            switch (keyword) {
                case "properties" -> {
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        var property = parser.currentName();
                        parser.nextToken();
                        if (ANSWER_SCHEMA_MEMBER.equals(property)) {
                            parser.skipChildren();
                        } else {
                            walk(parser, path + "." + property, problems);
                        }
                    }
                }
                case "items" -> walk(parser, path + "[]", problems);
                case "additionalProperties" -> closed = token == JsonToken.VALUE_FALSE;
                case "type" -> {
                    if (token == JsonToken.START_ARRAY) {
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            objectType |= "object".equals(parser.getText());
                        }
                    } else {
                        objectType = "object".equals(parser.getText());
                    }
                }
                default -> parser.skipChildren();
            }
        }
        if ((objectType || keywords.contains("properties")) && !closed) {
            problems.add(path + ": an object schema without additionalProperties false");
        }
    }
}
