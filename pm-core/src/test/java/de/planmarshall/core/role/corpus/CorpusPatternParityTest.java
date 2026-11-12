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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.JsonValue.JsonNumber;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;

/**
 * The patterns of the corpus records agree with the schema patterns they copy (PM-WORK-6): each is the schema's
 * pattern compiled with {@link PythonRegex}, so a record never accepts a value the schema, evaluated as
 * {@code validate.py} evaluates it, refuses. The record matches the whole value and is stricter than the schema's
 * {@code re.search} only for a value ending in one {@code \n}.
 * <p>
 * The inventory of {@link #copies()} is checked against both sides: every schema property carrying a {@code pattern}
 * has a case unless it is listed in {@link #SCHEMA_ONLY}, and every pattern constant of the records has a case unless
 * it is listed in {@link #RECORD_ONLY}. A new pattern on either side without a case fails the test.
 */
@EnableGeneratorController
@DisplayName("Corpus record patterns and schema patterns")
class CorpusPatternParityTest {

    private static final String MANIFEST = "source-manifest.schema.json";
    private static final String OVERLAY = "source-overlay.schema.json";
    private static final String ITEM = "imported-item.schema.json";
    private static final String COMMIT = "%040x".formatted(Generators.longs().next());
    private static final String HASH = "%064x".formatted(Generators.longs().next());
    private static final URI REPOSITORY = URI.create("https://github.com/example/sample-set");

    /** The line separators of Java's default {@code .} that Python's {@code .} crosses. */
    private static final List<String> SEPARATORS = List.of("\r", "\u0085", " ", " ");

    /**
     * The schema patterns no record constant copies, as {@code <resource> <path>}: the record holds a repository as a
     * {@link URI} and checks it itself.
     */
    private static final Set<String> SCHEMA_ONLY = Set.of(
            MANIFEST + " repository",
            ITEM + " provenance/origin/repository");

    /** The pattern constants of the records that copy no schema pattern: a position key is the record's own form. */
    private static final Set<Pattern> RECORD_ONLY = Set.of(SourceOverlay.POSITION);

    /**
     * The schema {@code maxLength} members no bound constant of a record copies, as {@code <resource> <path>}: a date is
     * a {@link java.time.LocalDate} in the record, and the codec accepts only the one shape whose text is ten code
     * points, {@link CorpusJson#DATE_PATTERN}.
     */
    private static final Set<String> SCHEMA_ONLY_BOUNDS = Set.of(
            OVERLAY + " created",
            OVERLAY + " rows/items/review/date",
            ITEM + " provenance/created",
            ITEM + " review/date");

    /** The records whose pattern constants, with those of their nested types, copy schema patterns. */
    private static final List<Class<?>> RECORDS = List.of(SourceManifest.class, ImportedItem.class,
            SourceOverlay.class, CorpusJson.class);

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
                Arguments.of("SourceManifest.NAME_PATTERN / manifest name", SourceManifest.NAME_PATTERN, MANIFEST,
                        new String[]{"name"}),
                Arguments.of("SourceManifest.NAME_PATTERN / overlay source", SourceManifest.NAME_PATTERN, OVERLAY,
                        new String[]{"source"}),
                Arguments.of("SourceManifest.COMMIT_PATTERN / manifest commit", SourceManifest.COMMIT_PATTERN,
                        MANIFEST, new String[]{"commit"}),
                Arguments.of("SourceManifest.COMMIT_PATTERN / item origin commit", SourceManifest.COMMIT_PATTERN,
                        ITEM, new String[]{"provenance", "origin", "commit"}),
                Arguments.of("SourceManifest.SHA256 / manifest file sha256", SourceManifest.SHA256, MANIFEST,
                        new String[]{"files", "items", "sha256"}),
                Arguments.of("SourceManifest.LICENCE_PATTERN / manifest licence", SourceManifest.LICENCE_PATTERN,
                        MANIFEST, new String[]{"licence"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / manifest file path", SourceManifest.RELATIVE_PATH,
                        MANIFEST, new String[]{"files", "items", "path"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / overlay file", SourceManifest.RELATIVE_PATH, OVERLAY,
                        new String[]{"file"}),
                Arguments.of("SourceManifest.RELATIVE_PATH / item origin path", SourceManifest.RELATIVE_PATH, ITEM,
                        new String[]{"provenance", "origin", "path"}),
                Arguments.of("ImportedItem.ID_PATTERN / item id", ImportedItem.ID_PATTERN, ITEM,
                        new String[]{"id"}),
                Arguments.of("ImportedItem.TOPIC_PATTERN / item topic", ImportedItem.TOPIC_PATTERN, ITEM,
                        new String[]{"topic"}),
                Arguments.of("ImportedItem.TOPIC_PATTERN / overlay row topic", ImportedItem.TOPIC_PATTERN, OVERLAY,
                        new String[]{"rows", "items", "topic"}),
                Arguments.of("ImportedItem.SLUG_PATTERN / overlay row id", ImportedItem.SLUG_PATTERN, OVERLAY,
                        new String[]{"rows", "items", "id"}),
                Arguments.of("SourceOverlay.KEY / overlay key_field", SourceOverlay.KEY, OVERLAY,
                        new String[]{"key_field"}),
                Arguments.of("SourceOverlay.KEY / overlay row key", SourceOverlay.KEY, OVERLAY,
                        new String[]{"rows", "items", "key"}),
                Arguments.of("ImportedItem.TEXT_PATTERN / overlay row group", ImportedItem.TEXT_PATTERN, OVERLAY,
                        new String[]{"rows", "items", "group"}),
                Arguments.of("ImportedItem.TEXT_PATTERN / overlay row contamination", ImportedItem.TEXT_PATTERN,
                        OVERLAY, new String[]{"rows", "items", "contamination"}),
                Arguments.of("ImportedItem.TEXT_PATTERN / item group", ImportedItem.TEXT_PATTERN, ITEM,
                        new String[]{"group"}),
                Arguments.of("ImportedItem.TEXT_PATTERN / item contamination", ImportedItem.TEXT_PATTERN, ITEM,
                        new String[]{"contamination"}),
                Arguments.of("CorpusJson.DATE_PATTERN / overlay created", CorpusJson.DATE_PATTERN, OVERLAY,
                        new String[]{"created"}),
                Arguments.of("CorpusJson.DATE_PATTERN / overlay row review date", CorpusJson.DATE_PATTERN, OVERLAY,
                        new String[]{"rows", "items", "review", "date"}),
                Arguments.of("CorpusJson.DATE_PATTERN / item provenance created", CorpusJson.DATE_PATTERN, ITEM,
                        new String[]{"provenance", "created"}),
                Arguments.of("CorpusJson.DATE_PATTERN / item review date", CorpusJson.DATE_PATTERN, ITEM,
                        new String[]{"review", "date"}));
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

    private static String key(String resource, String... path) {
        return resource + " " + String.join("/", path);
    }

    /**
     * @param schema   a schema
     * @param resource the name the keys carry
     * @return the keys of every schema below the given one that carries a {@code pattern}, entering {@code properties}
     *         and {@code items}
     */
    private static Set<String> schemaPatterns(JsonValue schema, String resource) {
        var found = new TreeSet<String>();
        collectPatterns(schema, resource, List.of(), found);
        return found;
    }

    private static void collectPatterns(JsonValue schema, String resource, List<String> path, Set<String> found) {
        if (!(schema instanceof JsonObject object)) {
            return;
        }
        if (object.get("pattern").isPresent()) {
            found.add(key(resource, path.toArray(String[]::new)));
        }
        if (object.get("properties").orElse(null) instanceof JsonObject properties) {
            properties.members().forEach((name, child) -> collectPatterns(child, resource, append(path, name), found));
        }
        object.get("items").ifPresent(items -> collectPatterns(items, resource, append(path, "items"), found));
    }

    private static List<String> append(List<String> path, String name) {
        var longer = new ArrayList<>(path);
        longer.add(name);
        return longer;
    }

    private static Stream<Field> patternConstants(Class<?> type) {
        return Stream.concat(
                Arrays.stream(type.getDeclaredFields()).filter(field -> field.getType() == Pattern.class
                        && Modifier.isStatic(field.getModifiers()) && !Modifier.isPrivate(field.getModifiers())),
                Arrays.stream(type.getDeclaredClasses()).flatMap(CorpusPatternParityTest::patternConstants));
    }

    private static Pattern valueOf(Field constant) {
        try {
            return (Pattern) constant.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(constant + " cannot be read", e);
        }
    }

    @Test
    @DisplayName("every schema property with a pattern has one case, or is a listed schema-only pattern")
    void everySchemaPatternHasACase() {
        var found = new TreeSet<String>();
        for (var resource : List.of(MANIFEST, OVERLAY, ITEM)) {
            found.addAll(schemaPatterns(JsonValue.parse(CorpusSchemaResourcesTest.resource(resource).getBytes(UTF_8)),
                    resource));
        }
        var cases = copies().map(Arguments::get).map(arguments -> key((String) arguments[2], (String[]) arguments[3]))
                .toList();
        var covered = new TreeSet<>(cases);
        covered.addAll(SCHEMA_ONLY);

        assertAll(
                () -> assertEquals(found, covered, "schema patterns without a case, or cases without a pattern"),
                () -> assertEquals(cases.size(), Set.copyOf(cases).size(), "a schema pattern has more than one case"),
                () -> assertEquals(found.size(), cases.size() + SCHEMA_ONLY.size(),
                        "a schema-only pattern also has a case"));
    }

    @Test
    @DisplayName("every pattern constant of the records has a case, or is a listed record-only constraint")
    void everyRecordPatternHasACase() {
        var copied = copies().map(arguments -> (Pattern) arguments.get()[1]).collect(Collectors.toSet());
        var constants = RECORDS.stream().flatMap(CorpusPatternParityTest::patternConstants).toList();
        var uncovered = constants.stream()
                .filter(constant -> !copied.contains(valueOf(constant)) && !RECORD_ONLY.contains(valueOf(constant)))
                .map(Field::toString).toList();
        var both = RECORD_ONLY.stream().filter(copied::contains).map(Pattern::pattern).toList();

        assertAll(
                () -> assertFalse(constants.isEmpty(), "the records declare pattern constants"),
                () -> assertEquals(List.of(), uncovered, "record pattern constants without a case"),
                () -> assertEquals(List.of(), both, "a record-only constraint also has a case"));
    }

    /** The bound of one record constant and the schema member it copies. */
    private static int maxLengthAt(String resource, String... path) {
        return ((JsonNumber) ((JsonObject) schemaAt(resource, path)).get("maxLength").orElseThrow()).value()
                .intValueExact();
    }

    static Stream<Arguments> bounds() {
        return Stream.of(
                Arguments.of("SourceManifest.NAME_MAX_LENGTH / manifest name", SourceManifest.NAME_MAX_LENGTH,
                        MANIFEST, new String[]{"name"}),
                Arguments.of("SourceManifest.NAME_MAX_LENGTH / overlay source", SourceManifest.NAME_MAX_LENGTH,
                        OVERLAY, new String[]{"source"}),
                Arguments.of("SourceManifest.COMMIT_MAX_LENGTH / manifest commit", SourceManifest.COMMIT_MAX_LENGTH,
                        MANIFEST, new String[]{"commit"}),
                Arguments.of("SourceManifest.COMMIT_MAX_LENGTH / item origin commit",
                        SourceManifest.COMMIT_MAX_LENGTH, ITEM, new String[]{"provenance", "origin", "commit"}),
                Arguments.of("SourceManifest.SHA256_MAX_LENGTH / manifest file sha256",
                        SourceManifest.SHA256_MAX_LENGTH, MANIFEST, new String[]{"files", "items", "sha256"}),
                Arguments.of("SourceManifest.LICENCE_MAX_LENGTH / manifest licence",
                        SourceManifest.LICENCE_MAX_LENGTH, MANIFEST, new String[]{"licence"}),
                Arguments.of("SourceManifest.RELATIVE_PATH_MAX_LENGTH / manifest file path",
                        SourceManifest.RELATIVE_PATH_MAX_LENGTH, MANIFEST, new String[]{"files", "items", "path"}),
                Arguments.of("SourceManifest.RELATIVE_PATH_MAX_LENGTH / overlay file",
                        SourceManifest.RELATIVE_PATH_MAX_LENGTH, OVERLAY, new String[]{"file"}),
                Arguments.of("SourceManifest.RELATIVE_PATH_MAX_LENGTH / item origin path",
                        SourceManifest.RELATIVE_PATH_MAX_LENGTH, ITEM, new String[]{"provenance", "origin", "path"}),
                Arguments.of("SourceManifest.REPOSITORY_MAX_LENGTH / manifest repository",
                        SourceManifest.REPOSITORY_MAX_LENGTH, MANIFEST, new String[]{"repository"}),
                Arguments.of("SourceManifest.REPOSITORY_MAX_LENGTH / item origin repository",
                        SourceManifest.REPOSITORY_MAX_LENGTH, ITEM,
                        new String[]{"provenance", "origin", "repository"}),
                Arguments.of("ImportedItem.ID_MAX_LENGTH / item id", ImportedItem.ID_MAX_LENGTH, ITEM,
                        new String[]{"id"}),
                Arguments.of("ImportedItem.TOPIC_MAX_LENGTH / item topic", ImportedItem.TOPIC_MAX_LENGTH, ITEM,
                        new String[]{"topic"}),
                Arguments.of("ImportedItem.TOPIC_MAX_LENGTH / overlay row topic", ImportedItem.TOPIC_MAX_LENGTH,
                        OVERLAY, new String[]{"rows", "items", "topic"}),
                Arguments.of("ImportedItem.SLUG_MAX_LENGTH / overlay row id", ImportedItem.SLUG_MAX_LENGTH, OVERLAY,
                        new String[]{"rows", "items", "id"}),
                Arguments.of("ImportedItem.TITLE_MAX_LENGTH / item title", ImportedItem.TITLE_MAX_LENGTH, ITEM,
                        new String[]{"title"}),
                Arguments.of("ImportedItem.TITLE_MAX_LENGTH / overlay row title", ImportedItem.TITLE_MAX_LENGTH,
                        OVERLAY, new String[]{"rows", "items", "title"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bounds")
    @DisplayName("every bound of a record is the maxLength of the schema member it copies")
    void boundEqualsSchema(String name, int bound, String resource, String[] path) {
        assertEquals(bound, maxLengthAt(resource, path), name);
    }

    /**
     * @param schema   a schema
     * @param resource the name the keys carry
     * @return the keys of every schema below the given one that carries a {@code maxLength}
     */
    private static Set<String> schemaBounds(JsonValue schema, String resource) {
        var found = new TreeSet<String>();
        collectBounds(schema, resource, List.of(), found);
        return found;
    }

    private static void collectBounds(JsonValue schema, String resource, List<String> path, Set<String> found) {
        if (!(schema instanceof JsonObject object)) {
            return;
        }
        if (object.get("maxLength").isPresent()) {
            found.add(key(resource, path.toArray(String[]::new)));
        }
        if (object.get("properties").orElse(null) instanceof JsonObject properties) {
            properties.members().forEach((name, child) -> collectBounds(child, resource, append(path, name), found));
        }
        object.get("items").ifPresent(items -> collectBounds(items, resource, append(path, "items"), found));
    }

    @Test
    @DisplayName("every schema maxLength has one bound case, or is a listed schema-only bound")
    void everySchemaBoundHasACase() {
        var found = new TreeSet<String>();
        for (var resource : List.of(MANIFEST, OVERLAY, ITEM)) {
            found.addAll(schemaBounds(JsonValue.parse(CorpusSchemaResourcesTest.resource(resource).getBytes(UTF_8)),
                    resource));
        }
        var cases = bounds().map(Arguments::get).map(arguments -> key((String) arguments[2], (String[]) arguments[3]))
                .toList();
        var covered = new TreeSet<>(cases);
        covered.addAll(SCHEMA_ONLY_BOUNDS);

        assertAll(
                () -> assertEquals(found, covered, "schema bounds without a case, or cases without a bound"),
                () -> assertEquals(cases.size(), Set.copyOf(cases).size(), "a schema bound has more than one case"),
                () -> assertEquals(found.size(), cases.size() + SCHEMA_ONLY_BOUNDS.size(),
                        "a schema-only bound also has a case"));
    }

    private static Stream<Field> intConstants(Class<?> type) {
        return Stream.concat(
                Arrays.stream(type.getDeclaredFields()).filter(field -> field.getType() == int.class
                        && Modifier.isStatic(field.getModifiers()) && !Modifier.isPrivate(field.getModifiers())),
                Arrays.stream(type.getDeclaredClasses()).flatMap(CorpusPatternParityTest::intConstants));
    }

    @Test
    @DisplayName("every bound constant of the records has a case")
    void everyRecordBoundHasACase() {
        var declared = RECORDS.stream().flatMap(CorpusPatternParityTest::intConstants)
                .map(CorpusPatternParityTest::constantName).toList();
        var cases = bounds().map(Arguments::get).map(arguments -> constantName((String) arguments[0])).toList();
        var uncovered = declared.stream().filter(constant -> !cases.contains(constant)).toList();
        var both = cases.stream().filter(constant -> !declared.contains(constant)).toList();

        assertAll(
                () -> assertFalse(declared.isEmpty(), "the records declare bound constants"),
                () -> assertEquals(List.of(), uncovered, "record bound constants without a case"),
                () -> assertEquals(List.of(), both, "a bound case for a constant no record declares"));
    }

    private static String constantName(Field constant) {
        return constant.getDeclaringClass().getSimpleName() + "." + constant.getName();
    }

    private static String constantName(String caseName) {
        return caseName.substring(0, caseName.indexOf(" / "));
    }

    @Test
    @DisplayName("the inventory walk finds a pattern in a property, in the items of an array and below both")
    void walkFindsNestedPatterns() {
        var schema = JsonValue.parse("""
                {"type": "object", "properties": {
                  "a": {"type": "string", "pattern": "x"},
                  "b": {"type": "array", "items": {"type": "string", "pattern": "y"}},
                  "c": {"type": "object", "properties": {"d": {"type": "array", "items": {"type": "object",
                    "properties": {"e": {"type": ["string", "null"], "pattern": "z"}}}}}},
                  "f": {"type": "string", "description": "no pattern"}}}
                """.getBytes(UTF_8));

        assertEquals(Set.of("t a", "t b/items", "t c/d/items/e"), schemaPatterns(schema, "t"));
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
        var name = "sample-set\n";

        assertAll(
                () -> assertTrue(schemaAccepts(name, MANIFEST, "name"), "Python's $ matches before a final \\n"),
                () -> assertFalse(SourceManifest.NAME_PATTERN.matcher(name).matches(),
                        "the record matches it whole"));
    }
}
