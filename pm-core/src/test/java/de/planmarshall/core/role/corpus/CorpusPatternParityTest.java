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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;

/**
 * The patterns of the corpus records agree with the schema patterns they copy (PM-WORK-6): each is the schema's
 * pattern compiled with {@link PythonRegex}, so a record never accepts a value the schema, evaluated as
 * {@code validate.py} evaluates it, refuses. The record matches the whole value and is stricter than the schema's
 * {@code re.search} only for a value ending in one {@code \n}.
 */
@DisplayName("Corpus record patterns and schema patterns")
class CorpusPatternParityTest {

    private static final String MANIFEST = "source-manifest.schema.json";
    private static final String OVERLAY = "source-overlay.schema.json";
    private static final String ITEM = "imported-item.schema.json";
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String HASH = "a".repeat(64);
    private static final URI REPOSITORY = URI.create("https://github.com/example/sample-set");

    /** The line separators of Java's default {@code .} that Python's {@code .} crosses. */
    private static final List<String> SEPARATORS = List.of("\r", "\u0085", " ", " ");

    /**
     * @param resource the schema resource
     * @param path     the property names leading to the string schema, {@code items} entering an array
     * @return the string schema at the path
     */
    private static JsonValue schemaAt(String resource, String... path) {
        JsonValue schema = JsonValue.parse(CorpusSchemaResourcesTest.resource(resource).getBytes(UTF_8));
        for (var name : path) {
            var object = (JsonObject) schema;
            schema = "items".equals(name) ? object.get("items").orElseThrow()
                    : ((JsonObject) object.get("properties").orElseThrow()).get(name).orElseThrow();
        }
        return schema;
    }

    private static String patternAt(String resource, String... path) {
        return ((JsonString) ((JsonObject) schemaAt(resource, path)).get("pattern").orElseThrow()).value();
    }

    private static boolean schemaAccepts(String value, String resource, String... path) {
        return SchemaSubset.validate(new JsonString(value), schemaAt(resource, path)).isEmpty();
    }

    static Stream<Arguments> copies() {
        return Stream.of(
                Arguments.of("SourceManifest.NAME / manifest name", SourceManifest.NAME, MANIFEST,
                        new String[]{"name"}),
                Arguments.of("SourceManifest.NAME / overlay source", SourceManifest.NAME, OVERLAY,
                        new String[]{"source"}),
                Arguments.of("SourceManifest.COMMIT / manifest commit", SourceManifest.COMMIT, MANIFEST,
                        new String[]{"commit"}),
                Arguments.of("SourceManifest.COMMIT / item origin commit", SourceManifest.COMMIT, ITEM,
                        new String[]{"provenance", "origin", "commit"}),
                Arguments.of("SourceManifest.SHA256 / manifest file sha256", SourceManifest.SHA256, MANIFEST,
                        new String[]{"files", "items", "sha256"}),
                Arguments.of("SourceManifest.LICENCE / manifest licence", SourceManifest.LICENCE, MANIFEST,
                        new String[]{"licence"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / manifest file path", SourceManifest.RELATIVE_PATH,
                        MANIFEST, new String[]{"files", "items", "path"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / overlay file", SourceManifest.RELATIVE_PATH, OVERLAY,
                        new String[]{"file"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / item origin path", SourceManifest.RELATIVE_PATH, ITEM,
                        new String[]{"provenance", "origin", "path"}),
                Arguments.of("ImportedItem.ID / item id", ImportedItem.ID, ITEM, new String[]{"id"}),
                Arguments.of("ImportedItem.TOPIC / item topic", ImportedItem.TOPIC, ITEM, new String[]{"topic"}),
                Arguments.of("ImportedItem.TOPIC / overlay row topic", ImportedItem.TOPIC, OVERLAY,
                        new String[]{"rows", "items", "topic"}),
                Arguments.of("ImportedItem.SLUG / overlay row id", ImportedItem.SLUG, OVERLAY,
                        new String[]{"rows", "items", "id"}),
                Arguments.of("SourceOverlay.KEY / overlay key_field", SourceOverlay.KEY, OVERLAY,
                        new String[]{"key_field"}),
                Arguments.of("SourceOverlay.KEY / overlay row key", SourceOverlay.KEY, OVERLAY,
                        new String[]{"rows", "items", "key"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("copies")
    @DisplayName("every record pattern is its schema pattern compiled with Python's meaning")
    void compiledWithPythonRegex(String name, Pattern pattern, String resource, String[] path) {
        var expected = PythonRegex.compile(patternAt(resource, path));

        assertAll(
                () -> assertEquals(expected.pattern(), pattern.pattern(), name),
                () -> assertEquals(expected.flags(), pattern.flags(), name));
    }

    static Stream<Arguments> hiddenParentSegments() {
        return SEPARATORS.stream().flatMap(separator -> Stream.of(
                Arguments.of(name(separator) + " before /../", "docs" + separator + "/../x"),
                Arguments.of(name(separator) + " before a final /..", "docs/x" + separator + "/..")));
    }

    private static String name(String separator) {
        return "U+%04X".formatted((int) separator.charAt(0));
    }

    @Nested
    @DisplayName("a repository-relative path")
    class RelativePath {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.role.corpus.CorpusPatternParityTest#hiddenParentSegments")
        @DisplayName("a .. segment behind a line separator is refused by the schema and by every record")
        void hiddenParentSegment(String name, String path) {
            assertAll(
                    () -> assertFalse(schemaAccepts(path, MANIFEST, "files", "items", "path"), "manifest schema"),
                    () -> assertFalse(schemaAccepts(path, OVERLAY, "file"), "overlay schema"),
                    () -> assertFalse(schemaAccepts(path, ITEM, "provenance", "origin", "path"), "item schema"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new SourceFile(path, HASH, false)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Origin(REPOSITORY, COMMIT, path, 1)));
        }

        @ParameterizedTest(name = "separator {index}")
        @ValueSource(strings = {"\u0085", " ", " "})
        @DisplayName("a line separator inside a segment is still a character of the segment")
        void separatorInSegment(String separator) {
            var path = "docs" + separator + "/x.csv";

            assertAll(
                    () -> assertTrue(schemaAccepts(path, MANIFEST, "files", "items", "path")),
                    () -> assertDoesNotThrow(() -> new SourceFile(path, HASH, false)));
        }
    }

    @Nested
    @DisplayName("a key and a key field")
    class Key {

        @ParameterizedTest(name = "{index} line feeds")
        @ValueSource(strings = {"\n", "\n\n"})
        @DisplayName("line feeds only are refused by the schema and by the record")
        void lineFeedsOnly(String key) {
            assertAll(
                    () -> assertFalse(schemaAccepts(key, OVERLAY, "rows", "items", "key")),
                    () -> assertFalse(schemaAccepts(key, OVERLAY, "key_field")),
                    () -> assertFalse(SourceOverlay.KEY.matcher(key).find()));
        }

        @ParameterizedTest(name = "value {index}")
        @ValueSource(strings = {"q-1", "a\n", " "})
        @DisplayName("a value with a character other than a line feed is accepted by both")
        void anyOtherCharacter(String key) {
            assertAll(
                    () -> assertTrue(schemaAccepts(key, OVERLAY, "rows", "items", "key")),
                    () -> assertTrue(SourceOverlay.KEY.matcher(key).find()));
        }
    }

    @Test
    @DisplayName("a value ending in one line feed is admitted by the schema's $ and refused by the record")
    void finalLineFeedStricter() {
        var commit = COMMIT + "\n";

        assertAll(
                () -> assertTrue(schemaAccepts(commit, MANIFEST, "commit"), "Python's $ matches before a final \\n"),
                () -> assertFalse(SourceManifest.COMMIT.matcher(commit).matches(), "the record matches it whole"));
    }
}
