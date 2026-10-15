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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.CorpusViolation.Rule;
import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;

/**
 * The corpus validator (PM-WORK-6) over a corpus the test writes into a temporary directory, never copied from the
 * product's content: a valid corpus with two items, a context file and one source reports nothing, and each rule has
 * a fixture that breaks it alone and reports exactly its violation.
 */
@DisplayName("Corpus validator")
class CorpusValidatorTest {

    private static final String ITEM_A = "screening/topic/item-a";
    private static final String ITEM_B = "review/topic/item-b";
    private static final String NESTED_SOURCES_ITEM = "screening/sources/q-1";
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SOURCE_FILE = "data/test.csv";
    private static final byte[] CSV = "question_id,question\nq-1,Is the sky blue?\n".getBytes(UTF_8);
    private static final String MANIFEST = "sources/alpha/manifest.json";
    private static final String OVERLAY = "sources/alpha/overlay.json";

    /** The item schema of the fixture corpus: a closed schema of the keyword subset, written for this test. */
    private static final String ITEM_SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["schema_version","id","topic","role","title","input","target","grading","provenance",
                         "review","canary"],
             "properties":{
              "schema_version":{"const":1},
              "id":{"type":"string","pattern":"^[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)+$"},
              "topic":{"type":"string","pattern":"^[a-z0-9-]+(/[a-z0-9-]+)*$"},
              "role":{"type":"string"},
              "title":{"type":"string","maxLength":120},
              "tags":{"type":"array","items":{"type":"string"}},
              "input":{"type":"object","additionalProperties":false,"required":["question","facts"],"properties":{
               "question":{"type":"string"},"facts":{"type":"string"},
               "choices":{"type":["array","null"],"items":{"type":"string"},"minItems":2},
               "context":{"type":"array","items":{"type":"string","pattern":"^context/"}}}},
              "target":{"type":"object","additionalProperties":false,"required":["rationale"],"properties":{
               "answer":{},"rationale":{"type":"string"},"alternatives":{"type":"array","items":{"type":"string"}},
               "items":{"type":"array","items":{"type":"object","additionalProperties":false,
                "required":["id","description"],"properties":{"id":{"type":"string"},"description":{"type":"string"}}}},
               "expected_empty":{"type":"boolean"}}},
              "grading":{"type":"object","additionalProperties":false,"required":["method"],"properties":{
               "method":{"enum":["exact_choice","label","rubric_items","rubric_properties"]},
               "judge":{"type":"string"},"pass_threshold":{"type":"number","minimum":0,"maximum":1}}},
              "provenance":{"type":"object","additionalProperties":false,"required":["kind","created","history"],
               "properties":{"kind":{"enum":["hand-made","fetched","written"]},"created":{"type":"string"},
                "origin":{"type":"object","additionalProperties":false,"required":["repository"],"properties":{
                 "repository":{"type":"string"},"commit":{"type":"string"},"path":{"type":"string"},
                 "line":{"type":"integer"}}},
                "history":{"type":"array","items":{"type":"string"},"minItems":1}}},
              "review":{"type":"object","additionalProperties":false,"required":["status"],"properties":{
               "status":{"enum":["draft","confirmed"]},"by":{"type":"string"},"date":{"type":"string"},
               "notes":{"type":"string"}}},
              "canary":{"type":"string"}}}
            """;

    @TempDir
    Path base;

    private Fixture fixture;

    /** The corpus root and the cache root of one test, with the writers of their files. */
    record Fixture(Path corpus, Path cache) {

        void write(String relative, byte[] content) throws IOException {
            var file = corpus.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        }

        void write(String relative, String content) throws IOException {
            write(relative, content.getBytes(UTF_8));
        }

        void item(Map<String, Object> item) throws IOException {
            write(item.get("id") + ".json", json(item));
        }

        void generated(Map<String, Object> item) throws IOException {
            var file = cache.resolve(CorpusImport.ITEMS).resolve(item.get("id") + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, json(item));
        }

        void delete(String relative) throws IOException {
            Files.delete(corpus.resolve(relative));
        }

        void manifest(SourceManifest manifest) throws IOException {
            write(MANIFEST, SourceManifestCodec.write(manifest));
        }

        void overlay(SourceOverlay overlay) throws IOException {
            write(OVERLAY, SourceOverlayCodec.write(overlay));
        }
    }

    /** Changes the valid fixture so that it breaks one rule. */
    @FunctionalInterface
    interface Mutation {
        void apply(Fixture fixture) throws IOException;
    }

    @BeforeEach
    void validCorpus() throws IOException {
        fixture = new Fixture(base.resolve("corpus"), base.resolve("cache"));
        fixture.write(CorpusValidator.ITEM_SCHEMA, ITEM_SCHEMA);
        fixture.write("context/rules.md", "# Rules\n\nAnswer with yes or no.\n");
        fixture.item(closedItem());
        fixture.item(rubricItem());
        fixture.manifest(manifest("alpha", SOURCE_FILE));
        fixture.overlay(overlay("alpha", SOURCE_FILE));
        fixture.write(CorpusValidator.ATTRIBUTION, "# Attribution\n\n## alpha\n\nMIT, the example authors.\n");
    }

    private List<CorpusViolation> validate() {
        return new CorpusValidator(fixture.corpus()).validate(fixture.cache());
    }

    private static String report(List<CorpusViolation> violations) {
        return violations.stream().map(CorpusViolation::toString).collect(Collectors.joining("\n"));
    }

    static Map<String, Object> closedItem() {
        var item = itemFrame(ITEM_A, "screening");
        item.put("tags", List.of("closed"));
        item.put("input", map("question", "Is the sky blue?", "facts", "The sky at noon.", "choices",
                List.of("yes", "no"), "context", List.of("context/rules.md")));
        item.put("target", map("answer", "yes", "rationale", "the sky is blue at noon"));
        item.put("grading", map("method", "exact_choice"));
        return reorder(item);
    }

    /** A valid item in a topic with a {@code sources} segment below the corpus root, which holds items. */
    static Map<String, Object> nestedSourcesItem() {
        var item = closedItem();
        item.put("id", NESTED_SOURCES_ITEM);
        item.put("topic", "screening/sources");
        return item;
    }

    static Map<String, Object> rubricItem() {
        var item = itemFrame(ITEM_B, "review");
        item.put("input", map("question", "Review the change.", "facts", "A diff."));
        item.put("target", map("rationale", "one defect", "items",
                List.of(map("id", "d1", "description", "the missing null check"))));
        item.put("grading", map("method", "rubric_items", "pass_threshold", 0.5));
        return reorder(item);
    }

    /** An item in the imported format, as an import writes it below a cache root. */
    static Map<String, Object> importedItem(String id) {
        var item = itemFrame(id, "screening");
        item.put("tags", List.of("imported"));
        item.put("input", map("question", "Is water dry?", "facts", "", "choices", List.of("no", "yes")));
        item.put("target", map("answer", "no", "rationale", "water is wet"));
        item.put("grading", map("method", "label"));
        item.put("provenance", map("kind", "fetched", "created", "2026-10-14", "origin",
                map("repository", "https://github.com/example/alpha", "commit", COMMIT, "path", SOURCE_FILE, "line", 2),
                "history", List.of("2026-10-14: generated from line 2 of the source alpha.")));
        return reorder(item);
    }

    private static Map<String, Object> itemFrame(String id, String role) {
        var item = new LinkedHashMap<String, Object>();
        item.put("schema_version", 1);
        item.put("id", id);
        item.put("topic", id.substring(0, id.lastIndexOf('/')));
        item.put("role", role);
        item.put("title", "item " + id);
        item.put("provenance", map("kind", "written", "created", "2026-10-14", "history",
                List.of("2026-10-14: written for the corpus.")));
        item.put("review", map("status", "confirmed", "by", "operator", "date", "2026-10-14"));
        item.put("canary", ItemGenerator.CANARY);
        return item;
    }

    /** Puts the members in the order of the item format. */
    private static Map<String, Object> reorder(Map<String, Object> item) {
        var ordered = new LinkedHashMap<String, Object>();
        for (var key : List.of("schema_version", "id", "topic", "role", "title", "tags", "input", "target",
                "grading", "provenance", "review", "canary")) {
            if (item.containsKey(key)) {
                ordered.put(key, item.get(key));
            }
        }
        return ordered;
    }

    private static Map<String, Object> map(Object... members) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < members.length; index += 2) {
            map.put((String) members[index], members[index + 1]);
        }
        return map;
    }

    /** @return the item with the member at the dotted path set; {@code null} removes it */
    @SuppressWarnings("unchecked")
    static Map<String, Object> with(Map<String, Object> item, String path, Object value) {
        var names = path.split("\\.");
        var parent = item;
        for (var index = 0; index < names.length - 1; index++) {
            parent = (Map<String, Object>) parent.get(names[index]);
        }
        if (value == null) {
            parent.remove(names[names.length - 1]);
        } else {
            parent.put(names[names.length - 1], value);
        }
        return item;
    }

    static String json(Object value) {
        return switch (value) {
            case null -> "null";
            case Map<?, ?> map -> map.entrySet().stream()
                    .map(member -> json(member.getKey()) + ":" + json(member.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
            case List<?> list -> list.stream().map(CorpusValidatorTest::json).collect(Collectors.joining(",", "[", "]"));
            case String string -> "\"" + string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
            default -> value.toString();
        };
    }

    private static SourceManifest manifest(String name, String file) {
        return new SourceManifest(name, URI.create("https://github.com/example/alpha"), COMMIT, "MIT",
                List.of(new SourceFile(file, Sha256.hex(CSV), false)));
    }

    private static SourceOverlay overlay(String source, String file) {
        var row = new Row("q-1", "q-1", "screening/alpha", "screening", "sky question", List.of("imported"),
                "{question}", "", List.of("yes", "no"), new Target("yes", "the sky is blue", List.of(), List.of(), false),
                new Grading(GradingMethod.EXACT_CHOICE, null, null), new Review(ReviewStatus.DRAFT, null, null, null));
        return new SourceOverlay(source, file, "question_id", LocalDate.of(2026, 10, 14), List.of(row));
    }

    /**
     * @return a manifest of {@code alpha} that names {@link #SOURCE_FILE} twice: its schema admits it (the keyword
     *         subset has no {@code uniqueItems}), its codec refuses it
     */
    private static String pathTwice() {
        var twoFiles = new SourceManifest("alpha", URI.create("https://github.com/example/alpha"), COMMIT, "MIT",
                List.of(new SourceFile(SOURCE_FILE, Sha256.hex(CSV), false),
                        new SourceFile("data/other.csv", Sha256.hex(CSV), false)));
        return new String(SourceManifestCodec.write(twoFiles), UTF_8).replace("data/other.csv", SOURCE_FILE);
    }

    /**
     * @return an overlay of {@code alpha} whose two rows select the key {@code q-1}: its schema admits it, its codec
     *         refuses it
     */
    private static String rowTwice() {
        var row = overlay("alpha", SOURCE_FILE).rows().getFirst();
        var second = new Row("q-2", "q-2", row.topic(), row.role(), row.title(), row.tags(), row.question(),
                row.facts(), row.choices(), row.target(), row.grading(), row.review());
        var twoRows = new SourceOverlay("alpha", SOURCE_FILE, "question_id", LocalDate.of(2026, 10, 14),
                List.of(row, second));
        return new String(SourceOverlayCodec.write(twoRows), UTF_8).replace("\"q-2\"", "\"q-1\"");
    }

    @Test
    @DisplayName("the codec-refusal fixtures pass their schemas, so the codec refusal is what reports them")
    void codecRefusalFixturesPassTheirSchemas() throws Exception {
        var manifestErrors = SchemaSubset.validate(JsonValue.parse(pathTwice().getBytes(UTF_8)),
                JsonValue.parse(resource("source-manifest.schema.json")));
        var overlayErrors = SchemaSubset.validate(JsonValue.parse(rowTwice().getBytes(UTF_8)),
                JsonValue.parse(resource("source-overlay.schema.json")));

        assertAll(
                () -> assertEquals(List.of(), manifestErrors),
                () -> assertEquals(List.of(), overlayErrors));
    }

    private static byte[] resource(String name) throws IOException {
        try (var in = CorpusValidator.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }

    @Nested
    @DisplayName("a valid corpus")
    class Valid {

        @Test
        @DisplayName("reports no violation")
        void valid() {
            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("reports no violation without the cache root either")
        void validWithoutCache() {
            var violations = new CorpusValidator(fixture.corpus()).validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("an item an import generated below the cache root is accepted")
        void generatedItemAccepted() throws Exception {
            var overlay = CorpusSchemaResourcesTest.overlay("question_id", "q-1", "q-2");
            var generated = ItemGenerator.generate(CorpusSchemaResourcesTest.manifest(), overlay,
                    overlay.rows().getFirst(), new SourceRow(3, Map.of("question", "Is the sky blue?", "subject",
                            "weather", "option_a", "yes", "option_b", "no")));
            var file = fixture.cache().resolve(CorpusImport.ITEMS).resolve(generated.id() + ".json");
            Files.createDirectories(file.getParent());
            Files.write(file, ImportedItemCodec.write(generated));

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("the files below schema/ and context/ at any depth and below the root sources/ are no items")
        void noItemDirectories() throws Exception {
            fixture.write("context/notes.json", "not JSON");
            fixture.write("schema/other.json", "{\"id\":\"elsewhere\"}");
            fixture.write("screening/context/notes.json", "not JSON");
            fixture.write("review/schema/other.json", "{\"id\":\"elsewhere\"}");
            fixture.write("sources/alpha/extra.json", "[]");
            fixture.write("sources/alpha/nested/q-1.json", "not JSON");

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("a valid item below a sources directory deeper than the root is an item and accepted")
        void nestedSourcesItemAccepted() throws Exception {
            fixture.item(nestedSourcesItem());

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("an item of the corpus's own part fetched from a review comment, naming no row, is accepted")
        void fetchedOwnItem() throws Exception {
            fixture.item(with(closedItem(), "provenance", map("kind", "fetched", "created", "2026-10-14", "origin",
                    map("repository", "https://github.com/example/alpha", "path", "src/Main.java"), "history",
                    List.of("2026-10-14: fetched from a review comment of the repository."))));

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("a DEL before a stage-like name is written as \\u007f, as validate.py matches it, so no name")
        void delBeforeStageName() throws Exception {
            fixture.item(with(closedItem(), "tags", List.of((char) 0x7f + "E1")));

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("a control without rubric items is accepted")
        void control() throws Exception {
            var item = with(rubricItem(), "target.items", null);
            fixture.item(with(item, "target.expected_empty", true));

            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }
    }

    static Stream<Arguments> brokenRules() {
        return Stream.of(
                Arguments.of("the item schema is missing", CorpusValidator.ITEM_SCHEMA, Rule.ITEM_SCHEMA,
                        (Mutation) f -> f.delete(CorpusValidator.ITEM_SCHEMA)),
                Arguments.of("an item is no JSON", "screening/topic/broken", Rule.NOT_JSON,
                        (Mutation) f -> f.write("screening/topic/broken.json", "{")),
                Arguments.of("an item has an undeclared member", ITEM_A, Rule.SCHEMA,
                        (Mutation) f -> f.item(with(closedItem(), "extra", 1))),
                Arguments.of("an id differs from the path", "screening/topic/item-c", Rule.ID_PATH,
                        (Mutation) f -> f.write("screening/topic/item-c.json",
                                json(with(closedItem(), "id", "screening/topic/other")))),
                Arguments.of("an item below a nested sources directory has a wrong canary", NESTED_SOURCES_ITEM,
                        Rule.CANARY, (Mutation) f -> f.item(with(nestedSourcesItem(), "canary", "another canary"))),
                Arguments.of("an item below a nested sources directory has an id that differs from its path",
                        NESTED_SOURCES_ITEM, Rule.ID_PATH, (Mutation) f -> f.write(NESTED_SOURCES_ITEM + ".json",
                                json(with(nestedSourcesItem(), "id", "screening/sources/other")))),
                Arguments.of("a topic differs from the directory", ITEM_A, Rule.TOPIC_DIRECTORY,
                        (Mutation) f -> f.item(with(closedItem(), "topic", "screening/else"))),
                Arguments.of("a generated item has the id of a corpus item", ITEM_A, Rule.DUPLICATE_ID,
                        (Mutation) f -> f.generated(with(importedItem(ITEM_A), "topic", "screening/topic"))),
                Arguments.of("the canary is wrong", ITEM_A, Rule.CANARY,
                        (Mutation) f -> f.item(with(closedItem(), "canary", "another canary"))),
                Arguments.of("a closed answer is no choice", ITEM_A, Rule.CLOSED_ANSWER,
                        (Mutation) f -> f.item(with(closedItem(), "target.answer", "maybe"))),
                Arguments.of("a closed answer without choices", ITEM_A, Rule.CLOSED_ANSWER,
                        (Mutation) f -> f.item(with(closedItem(), "input.choices", null))),
                Arguments.of("a rubric has no items and is no control", ITEM_B, Rule.RUBRIC_ITEMS,
                        (Mutation) f -> f.item(with(rubricItem(), "target.items", List.of()))),
                Arguments.of("a context file is missing", ITEM_A, Rule.CONTEXT_FILE,
                        (Mutation) f -> f.item(with(closedItem(), "input.context", List.of("context/missing.md")))),
                Arguments.of("the provenance names a stage", ITEM_A, Rule.TRANSIENT_NAME,
                        (Mutation) f -> f.item(with(closedItem(), "provenance.history",
                                List.of("2026-10-14: written in Stage 2.")))),
                Arguments.of("the tags name a local plan path", ITEM_B, Rule.TRANSIENT_NAME,
                        (Mutation) f -> f.item(with(rubricItem(), "tags", List.of(".plan/local")))),
                Arguments.of("an imported item is stored below the corpus", ITEM_A, Rule.THIRD_PARTY_TEXT,
                        (Mutation) f -> f.item(with(importedItem(ITEM_A), "topic", "screening/topic"))),
                Arguments.of("a source file is stored below the corpus", "context/rows.csv", Rule.THIRD_PARTY_TEXT,
                        (Mutation) f -> f.write("context/rows.csv", CSV)),
                Arguments.of("a generated item is outside the imported format", "screening/imported/q-1",
                        Rule.IMPORTED_ITEM,
                        (Mutation) f -> f.generated(with(importedItem("screening/imported/q-1"), "tags", List.of()))),
                Arguments.of("the manifest is missing", MANIFEST, Rule.MANIFEST, (Mutation) f -> f.delete(MANIFEST)),
                Arguments.of("the manifest has an undeclared member", MANIFEST, Rule.MANIFEST,
                        (Mutation) f -> f.write(MANIFEST, new String(SourceManifestCodec.write(manifest("alpha",
                                SOURCE_FILE)), UTF_8).replace("\"licence\"", "\"homepage\" : \"x\", \"licence\""))),
                Arguments.of("the manifest names another source", MANIFEST, Rule.MANIFEST,
                        (Mutation) f -> f.manifest(manifest("beta", SOURCE_FILE))),
                Arguments.of("the manifest is no JSON", MANIFEST, Rule.MANIFEST, (Mutation) f -> f.write(MANIFEST, "[")),
                Arguments.of("the manifest passes its schema but its codec refuses a path twice", MANIFEST,
                        Rule.MANIFEST, (Mutation) f -> f.write(MANIFEST, pathTwice())),
                Arguments.of("the overlay passes its schema but its codec refuses a row twice", OVERLAY, Rule.OVERLAY,
                        (Mutation) f -> f.write(OVERLAY, rowTwice())),
                Arguments.of("the overlay is missing", OVERLAY, Rule.OVERLAY, (Mutation) f -> f.delete(OVERLAY)),
                Arguments.of("the overlay names another source", OVERLAY, Rule.OVERLAY,
                        (Mutation) f -> f.overlay(overlay("beta", SOURCE_FILE))),
                Arguments.of("the overlay reads a file the manifest does not name", OVERLAY, Rule.OVERLAY,
                        (Mutation) f -> f.overlay(overlay("alpha", "data/other.csv"))),
                Arguments.of("ATTRIBUTION.md is missing", CorpusValidator.ATTRIBUTION, Rule.ATTRIBUTION,
                        (Mutation) f -> f.delete(CorpusValidator.ATTRIBUTION)),
                Arguments.of("ATTRIBUTION.md lacks the section of the source", CorpusValidator.ATTRIBUTION,
                        Rule.ATTRIBUTION, (Mutation) f -> f.write(CorpusValidator.ATTRIBUTION, "# Attribution\n")),
                Arguments.of("ATTRIBUTION.md has the section twice", CorpusValidator.ATTRIBUTION, Rule.ATTRIBUTION,
                        (Mutation) f -> f.write(CorpusValidator.ATTRIBUTION, "## alpha\n\none\n\n## alpha\n\ntwo\n")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenRules")
    @DisplayName("a fixture that breaks one rule reports exactly its violation")
    void brokenRule(String name, String subject, Rule rule, Mutation mutation) throws Exception {
        mutation.apply(fixture);

        var violations = validate();

        assertAll(
                () -> assertEquals(1, violations.size(), () -> report(violations)),
                () -> assertEquals(subject, violations.getFirst().subject(), () -> report(violations)),
                () -> assertEquals(rule, violations.getFirst().rule(), () -> report(violations)));
    }

    @Test
    @DisplayName("a corpus root that is no directory is reported, not thrown")
    void rootIsNoDirectory() throws Exception {
        var file = Files.writeString(base.resolve("file"), "no corpus");

        var violations = new CorpusValidator(file).validate();

        assertAll(
                () -> assertEquals(1, violations.size(), () -> report(violations)),
                () -> assertEquals(Rule.UNREADABLE, violations.getFirst().rule()));
    }

    /**
     * The {@code sources} entry and the generated {@code items} are read by the rules of {@link CorpusImport}: an
     * entry of the wrong type, or a directory that cannot be listed, is reported, never read as a corpus without
     * sources or without generated items.
     */
    @Nested
    @DisplayName("a sources or items entry that cannot be read as a directory")
    class WrongType {

        private Path sources;

        @BeforeEach
        void removeSources() throws IOException {
            sources = fixture.corpus().resolve(CorpusImport.SOURCES);
            try (Stream<Path> walk = Files.walk(sources)) {
                for (var entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(entry);
                }
            }
        }

        @Test
        @DisplayName("an absent sources entry is a corpus without sources, no violation")
        void absentSources() {
            var violations = validate();

            assertEquals(List.of(), violations, () -> report(violations));
        }

        @Test
        @DisplayName("a sources entry that is a file is reported, as the import refuses it")
        void sourcesIsFile() throws Exception {
            Files.writeString(sources, "no directory");

            var violations = validate();

            assertSingle(violations, CorpusImport.SOURCES, Rule.UNREADABLE);
        }

        @Test
        @DisplayName("a sources entry that is a dangling link is reported, as the import refuses it")
        void sourcesIsDanglingLink() throws Exception {
            Files.createSymbolicLink(sources, base.resolve("nowhere"));

            var violations = validate();

            assertSingle(violations, CorpusImport.SOURCES, Rule.UNREADABLE);
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a sources directory that cannot be listed is reported, as the import refuses it")
        void unlistableSources() throws Exception {
            Files.createDirectories(sources.resolve("alpha"));
            var permissions = Files.getPosixFilePermissions(sources);
            Files.setPosixFilePermissions(sources, PosixFilePermissions.fromString("-wx------"));
            try {
                var violations = validate();

                assertTrue(violations.stream().anyMatch(violation -> violation.rule() == Rule.UNREADABLE
                        && CorpusImport.SOURCES.equals(violation.subject())), () -> report(violations));
            } finally {
                Files.setPosixFilePermissions(sources, permissions);
            }
        }

        @Test
        @DisplayName("generated items that are a file are reported, not read as no generated items")
        void itemsIsFile() throws Exception {
            Files.createDirectories(fixture.cache());
            Files.writeString(fixture.cache().resolve(CorpusImport.ITEMS), "no directory");

            var violations = validate();

            assertSingle(violations, CorpusImport.ITEMS, Rule.UNREADABLE);
        }

        private static void assertSingle(List<CorpusViolation> violations, String subject, Rule rule) {
            assertAll(
                    () -> assertEquals(1, violations.size(), () -> report(violations)),
                    () -> assertEquals(subject, violations.getFirst().subject(), () -> report(violations)),
                    () -> assertEquals(rule, violations.getFirst().rule(), () -> report(violations)));
        }
    }

    @Test
    @DisplayName("the transient pattern finds what validate.py's finds, with Python's \\b and \\d")
    void transientPatternAsPython() {
        var found = CorpusValidator.TRANSIENT.matcher("Stage ١, éE12 xE1 -V7").results()
                .map(MatchResult::group).toList();

        assertEquals(List.of("Stage ١", "V7"), found);
    }

    @Nested
    @ExtendWith(NonLatinDigits.class)
    @DisplayName("under a default locale with non-Latin digits")
    class NonLatin {

        @Test
        @DisplayName("the count of an attribution section is written in ASCII digits")
        void attributionCount() throws Exception {
            fixture.write(CorpusValidator.ATTRIBUTION, "## alpha\n\none\n\n## alpha\n\ntwo\n");

            var violations = validate();

            assertEquals(List.of("the section '## alpha' appears 2 times, not once"),
                    violations.stream().map(CorpusViolation::detail).toList(), () -> report(violations));
        }
    }

    @Test
    @DisplayName("a transient name is named in the detail, once per occurrence")
    void transientDetail() throws Exception {
        fixture.item(with(closedItem(), "provenance.history", List.of("2026-10-14: pulled by pull.py, see E12.")));

        var violations = validate();

        assertEquals(List.of("names a transient tool or stage: 'pull.py'",
                        "names a transient tool or stage: 'E12'"),
                violations.stream().map(CorpusViolation::detail).toList(), () -> report(violations));
    }

    @Test
    @DisplayName("an item breaking the schema reports each violation with its path")
    void schemaDetail() throws Exception {
        fixture.item(with(with(closedItem(), "title", "t".repeat(121)), "grading.method", "vote"));

        var violations = validate();

        assertAll(
                () -> assertTrue(violations.stream().allMatch(violation -> violation.rule() == Rule.SCHEMA),
                        () -> report(violations)),
                () -> assertEquals(List.of("$.title: longer than 120",
                                "$.grading.method: \"vote\" not in [\"exact_choice\", \"label\", \"rubric_items\", "
                                        + "\"rubric_properties\"]"),
                        violations.stream().map(CorpusViolation::detail).toList()));
    }
}
