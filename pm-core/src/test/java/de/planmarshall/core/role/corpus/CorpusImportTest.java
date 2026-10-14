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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.log.CoreLogMessages.ERROR;
import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.role.corpus.ImportResult.CoveredSource;
import de.planmarshall.core.role.corpus.ImportResult.MissedSource;
import de.planmarshall.core.role.corpus.ImportResult.Reason;
import de.planmarshall.core.role.corpus.ImportResult.Verdict;
import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Origin;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;

/**
 * The import of the corpus sources (PM-WORK-6, PM-TEST-6), over a temporary corpus root and cache root with in-test
 * fakes of {@link SourceRepository} and {@link LfsObjectSource}: two sources, {@code alpha} with a plain CSV file and
 * {@code beta} with a JSON Lines file stored in Git LFS.
 * <p>
 * A complete run yields valid items and {@link Verdict#COMPLETE}; a file or object that fails its hash makes the
 * import {@link Verdict#FAILED} while the other source's items are still generated; an unreachable source or a missing
 * pin makes it {@link Verdict#PARTIAL} and names the missed source. Nothing is ever written below the corpus root.
 */
@EnableTestLogger
@DisplayName("Corpus import")
class CorpusImportTest {

    private static final LocalDate CREATED = LocalDate.of(2026, 10, 14);
    private static final URI ALPHA_REPOSITORY = URI.create("https://github.com/example/alpha");
    private static final URI BETA_REPOSITORY = URI.create("https://github.com/example/beta");
    private static final String ALPHA_COMMIT = "1".repeat(40);
    private static final String BETA_COMMIT = "2".repeat(40);
    private static final String ALPHA_FILE = "data/test.csv";
    private static final String BETA_FILE = "data/rows.jsonl";
    private static final byte[] CSV = """
            question_id,question,subject,option_a,option_b
            q-1,Is the sky blue?,weather,yes,no
            q-2,Is water dry?,physics,no,yes
            """.getBytes(UTF_8);
    private static final byte[] JSONL = """
            {"diff":"rename the field"}
            {"diff":"drop the null check"}
            """.getBytes(UTF_8);
    private static final String ALPHA_Q1 = "screening/alpha/q-1.json";
    private static final String ALPHA_Q2 = "screening/alpha/q-2.json";
    private static final String BETA_D2 = "review/beta/d-2.json";

    @TempDir
    Path base;

    private Path corpusRoot;
    private Path cacheRoot;
    private FakeRepository repository;
    private FakeLfs lfs;

    @BeforeEach
    void corpus() throws IOException {
        corpusRoot = base.resolve("corpus");
        cacheRoot = base.resolve("cache");
        repository = new FakeRepository();
        lfs = new FakeLfs();
        writeSource(new SourceManifest("alpha", ALPHA_REPOSITORY, ALPHA_COMMIT, "CC-BY-4.0",
                        List.of(new SourceFile(ALPHA_FILE, Sha256.hex(CSV), false))),
                new SourceOverlay("alpha", ALPHA_FILE, "question_id", CREATED,
                        List.of(closedRow("q-1", "{question}"), closedRow("q-2", "{question}"))));
        writeSource(new SourceManifest("beta", BETA_REPOSITORY, BETA_COMMIT, "MIT",
                        List.of(new SourceFile(BETA_FILE, Sha256.hex(JSONL), true))),
                new SourceOverlay("beta", BETA_FILE, null, CREATED, List.of(openRow("2", "review/beta", "d-2"))));
        repository.files.put("alpha:" + ALPHA_FILE, CSV);
        repository.files.put("beta:" + BETA_FILE, new LfsPointer(Sha256.hex(JSONL), JSONL.length).toBytes());
        lfs.objects.put(Sha256.hex(JSONL), JSONL);
    }

    private static Row closedRow(String key, String question) {
        return closedRow(key, question, "screening/alpha");
    }

    private static Row closedRow(String key, String question, String topic) {
        return new Row(key, key, topic, "screening", "closed question " + key, List.of("imported"),
                question, "Subject: {subject}", List.of("{option_a}", "{option_b}"),
                new Target("{option_a}", "the first option holds", List.of(), List.of(), false),
                new Grading(GradingMethod.EXACT_CHOICE, null, null), new Review(ReviewStatus.DRAFT, null, null, null));
    }

    private static Row openRow(String key, String topic, String slug) {
        return new Row(key, slug, topic, "review", "open question " + key, List.of(), "Review: {diff}", "", null,
                new Target(null, "one defect", List.of(), List.of(new RubricItem("d1", "the null check")), false),
                new Grading(GradingMethod.RUBRIC_ITEMS, null, null), new Review(ReviewStatus.DRAFT, null, null, null));
    }

    private void writeSource(SourceManifest manifest, SourceOverlay overlay) throws IOException {
        writeSource(manifest.name(), SourceManifestCodec.write(manifest), SourceOverlayCodec.write(overlay));
    }

    /** Writes the documents of a source as given; a {@code null} document is left out. */
    private void writeSource(String directoryName, byte[] manifest, byte[] overlay) throws IOException {
        var directory = corpusRoot.resolve(CorpusImport.SOURCES).resolve(directoryName);
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve(CorpusImport.MANIFEST));
        Files.deleteIfExists(directory.resolve(CorpusImport.OVERLAY));
        if (manifest != null) {
            Files.write(directory.resolve(CorpusImport.MANIFEST), manifest);
        }
        if (overlay != null) {
            Files.write(directory.resolve(CorpusImport.OVERLAY), overlay);
        }
    }

    private static SourceManifest alphaManifest(String file, byte[] content) {
        return new SourceManifest("alpha", ALPHA_REPOSITORY, ALPHA_COMMIT, "CC-BY-4.0",
                List.of(new SourceFile(file, Sha256.hex(content), false)));
    }

    private ImportResult run() {
        return new CorpusImport(repository, lfs).run(corpusRoot, cacheRoot);
    }

    private Path items() {
        return cacheRoot.resolve(CorpusImport.ITEMS);
    }

    /** @return the item files below the cache root, relative to its items directory */
    private Set<String> itemFiles() throws IOException {
        if (!Files.isDirectory(items())) {
            return Set.of();
        }
        try (Stream<Path> files = Files.walk(items())) {
            return files.filter(Files::isRegularFile).map(file -> items().relativize(file).toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private ImportedItem item(String relative) throws IOException {
        var file = items().resolve(relative);
        var document = Files.readAllBytes(file);
        assertEquals(Set.of(), ItemGeneratorTest.violations(document), () -> new String(document, UTF_8));
        return ImportedItemCodec.read(file, document);
    }

    /** @return every entry below the root with the SHA-256 of a file, so a write below it changes the snapshot */
    private static Map<String, String> snapshot(Path root) throws IOException {
        var entries = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (var entry : walk.toList()) {
                entries.put(root.relativize(entry).toString(),
                        Files.isRegularFile(entry) ? Sha256.hex(Files.readAllBytes(entry)) : "directory");
            }
        }
        return entries;
    }

    @Nested
    @DisplayName("a complete run")
    class Complete {

        @Test
        @DisplayName("covers both sources, writes their items and is COMPLETE")
        void complete() throws Exception {
            var result = run();

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict()),
                    () -> assertEquals(List.of(new CoveredSource("alpha", 2), new CoveredSource("beta", 1)),
                            result.covered()),
                    () -> assertEquals(List.of(), result.missed()),
                    () -> assertEquals(3, result.items()),
                    () -> assertEquals(Set.of(ALPHA_Q1, ALPHA_Q2, BETA_D2), itemFiles()));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, CorpusImport.class);
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, CorpusImport.class);
        }

        @Test
        @DisplayName("generates a valid item from the plain file, naming its row")
        void plainFileItem() throws Exception {
            run();

            var item = item(ALPHA_Q2);

            assertAll(
                    () -> assertEquals("screening/alpha/q-2", item.id()),
                    () -> assertEquals("Is water dry?", item.input().question()),
                    () -> assertEquals("Subject: physics", item.input().facts()),
                    () -> assertEquals(List.of("no", "yes"), item.input().choices()),
                    () -> assertEquals("no", item.target().answer()),
                    () -> assertEquals(new Origin(ALPHA_REPOSITORY, ALPHA_COMMIT, ALPHA_FILE, 3),
                            item.provenance().origin()),
                    () -> assertEquals(ItemGenerator.CANARY, item.canary()));
        }

        @Test
        @DisplayName("generates a valid item from the LFS object, not from its pointer")
        void lfsObjectItem() throws Exception {
            run();

            var item = item(BETA_D2);

            assertAll(
                    () -> assertEquals("review/beta/d-2", item.id()),
                    () -> assertEquals("Review: drop the null check", item.input().question()),
                    () -> assertEquals(new Origin(BETA_REPOSITORY, BETA_COMMIT, BETA_FILE, 2),
                            item.provenance().origin()),
                    () -> assertEquals(List.of(BETA_REPOSITORY), lfs.asked));
        }

        @Test
        @DisplayName("writes nothing below the corpus root, and hands the repository only the cache root")
        void corpusRootUntouched() throws Exception {
            var before = snapshot(corpusRoot);

            run();

            assertAll(
                    () -> assertEquals(before, snapshot(corpusRoot)),
                    () -> assertEquals(Set.of(cacheRoot.toAbsolutePath().normalize()), repository.cacheRoots));
        }

        @Test
        @DisplayName("replaces the items of an earlier import")
        void replacesEarlierItems() throws Exception {
            var stale = items().resolve("old/topic/gone.json");
            Files.createDirectories(stale.getParent());
            Files.writeString(stale, "{}");

            run();

            assertAll(
                    () -> assertFalse(Files.exists(stale)),
                    () -> assertEquals(Set.of(ALPHA_Q1, ALPHA_Q2, BETA_D2), itemFiles()));
        }

        @Test
        @DisplayName("a corpus root without sources imports nothing and is COMPLETE")
        void noSources() {
            var result = new CorpusImport(repository, lfs).run(base.resolve("empty"), cacheRoot);

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict()),
                    () -> assertEquals(List.of(), result.covered()),
                    () -> assertEquals(0, result.items()));
        }
    }

    static Stream<Arguments> hashMismatches() {
        return Stream.of(
                Arguments.of("a changed plain file", "alpha", ALPHA_FILE, "beta",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.files
                                .put("alpha:" + ALPHA_FILE, (new String(CSV, UTF_8) + "q-3,a,b,c,d\n")
                                        .getBytes(UTF_8))),
                Arguments.of("a changed LFS object", "beta", BETA_FILE, "alpha",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> lfs.objects
                                .put(Sha256.hex(JSONL), "{\"diff\":\"changed\"}\n".getBytes(UTF_8))),
                Arguments.of("an LFS pointer naming another object", "beta", BETA_FILE, "alpha",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.files
                                .put("beta:" + BETA_FILE, new LfsPointer("c".repeat(64), 1).toBytes())),
                Arguments.of("a plain file where the manifest expects an LFS pointer", "beta", BETA_FILE, "alpha",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.files
                                .put("beta:" + BETA_FILE, JSONL)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hashMismatches")
    @DisplayName("a file that fails its hash fails the import, the other source's items are still generated")
    void hashMismatch(String name, String source, String file, String other,
            BiConsumer<FakeRepository, FakeLfs> change) throws Exception {
        change.accept(repository, lfs);
        var before = snapshot(corpusRoot);

        var result = run();

        var missed = result.missed();
        assertAll(
                () -> assertEquals(Verdict.FAILED, result.verdict()),
                () -> assertEquals(1, missed.size(), missed::toString),
                () -> assertEquals(source, missed.getFirst().name()),
                () -> assertEquals(Reason.HASH_MISMATCH, missed.getFirst().reason()),
                () -> assertEquals(file, missed.getFirst().file()),
                () -> assertEquals(List.of(other), result.covered().stream().map(CoveredSource::name).toList()),
                () -> assertTrue(itemFiles().stream().noneMatch(item -> item.contains("/" + source + "/")),
                        () -> "no item of " + source),
                () -> assertTrue(itemFiles().stream().anyMatch(item -> item.contains("/" + other + "/")),
                        () -> "the items of " + other),
                () -> assertEquals(before, snapshot(corpusRoot)));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                ERROR.CORPUS_SOURCE_HASH_MISMATCH.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "Corpus source '%s' fails its hash: file '%s'".formatted(source, file));
    }

    static Stream<Arguments> missedSources() {
        return Stream.of(
                Arguments.of("alpha's repository does not answer", "alpha", Reason.UNREACHABLE, null, "beta",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.failures.put(
                                "alpha:fetch",
                                SourceFetch.unreachable("https://secret@github.com/example/alpha did not answer"))),
                Arguments.of("alpha's pinned commit is absent", "alpha", Reason.PIN_MISSING, null, "beta",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.failures.put(
                                "alpha:fetch",
                                SourceFetch.pinMissing("https://secret@github.com/example/alpha has no commit"))),
                Arguments.of("beta's file is absent at the pin", "beta", Reason.PIN_MISSING, BETA_FILE, "alpha",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> repository.failures.put(
                                "beta:read:" + BETA_FILE,
                                SourceFetch.pinMissing("https://secret@github.com/example/beta has no file"))),
                Arguments.of("beta's LFS endpoint does not answer", "beta", Reason.UNREACHABLE, BETA_FILE, "alpha",
                        (BiConsumer<FakeRepository, FakeLfs>) (repository, lfs) -> lfs.failures.put(BETA_REPOSITORY,
                                SourceFetch.unreachable("https://secret@github.com/example/beta did not answer"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missedSources")
    @DisplayName("a source that cannot be fetched is missed, the import is PARTIAL and keeps the other items")
    void missedSource(String name, String source, Reason reason, String file, String other,
            BiConsumer<FakeRepository, FakeLfs> failure) throws Exception {
        failure.accept(repository, lfs);
        var before = snapshot(corpusRoot);

        var result = run();

        var missed = result.missed();
        assertAll(
                () -> assertEquals(Verdict.PARTIAL, result.verdict()),
                () -> assertEquals(1, missed.size(), missed::toString),
                () -> assertEquals(source, missed.getFirst().name()),
                () -> assertEquals(reason, missed.getFirst().reason()),
                () -> assertEquals(file, missed.getFirst().file()),
                () -> assertTrue(missed.getFirst().detail().startsWith("https://github.com/example/" + source),
                        missed.getFirst()::detail),
                () -> assertFalse(missed.getFirst().detail().contains("secret"), "the detail carries no credential"),
                () -> assertEquals(List.of(other), result.covered().stream().map(CoveredSource::name).toList()),
                () -> assertTrue(itemFiles().stream().noneMatch(item -> item.contains("/" + source + "/")),
                        () -> "no item of " + source),
                () -> assertTrue(itemFiles().stream().anyMatch(item -> item.contains("/" + other + "/")),
                        () -> "the items of " + other),
                () -> assertEquals(before, snapshot(corpusRoot)));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.CORPUS_SOURCE_MISSED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "Corpus source '%s' missed, its items are left out: %s".formatted(source, reason.wireName()));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, CorpusImport.class);
    }

    static Stream<Arguments> unreadableDocuments() {
        var manifest = SourceManifestCodec.write(alphaManifest(ALPHA_FILE, CSV));
        var overlay = SourceOverlayCodec.write(new SourceOverlay("alpha", ALPHA_FILE, "question_id", CREATED,
                List.of(closedRow("q-1", "{question}"))));
        var refused = "{}".getBytes(UTF_8);
        return Stream.of(
                Arguments.of("an absent manifest", null, overlay, "manifest.json cannot be read"),
                Arguments.of("an absent overlay", manifest, null, "overlay.json cannot be read"),
                Arguments.of("a manifest its codec refuses", refused, overlay, "manifest.json"),
                Arguments.of("an overlay its codec refuses", manifest, refused, "overlay.json"));
    }

    static Stream<Arguments> refusedOrigins() {
        return Stream.of(
                Arguments.of("http", "http://example.org/o/r"),
                Arguments.of("user information", "https://user@example.org/o/r"),
                Arguments.of("a query", "https://example.org/o/r?x=1"),
                Arguments.of("a fragment", "https://example.org/o/r#x"),
                Arguments.of("no host", "https:/o/r"),
                Arguments.of("no scheme", "example.org/o/r"));
    }

    @Nested
    @DisplayName("a source that does not yield its items")
    class Malformed {

        @Test
        @DisplayName("a template naming a field the row lacks misses the source and fails the import")
        void unknownTemplateField() throws Exception {
            writeSource(new SourceManifest("alpha", ALPHA_REPOSITORY, ALPHA_COMMIT, "CC-BY-4.0",
                            List.of(new SourceFile(ALPHA_FILE, Sha256.hex(CSV), false))),
                    new SourceOverlay("alpha", ALPHA_FILE, "question_id", CREATED,
                            List.of(closedRow("q-1", "{nothing}"))));

            var result = run();

            assertAll(
                    () -> assertEquals(Verdict.FAILED, result.verdict()),
                    () -> assertEquals(Reason.MALFORMED, result.missed().getFirst().reason()),
                    () -> assertEquals("alpha", result.missed().getFirst().name()),
                    () -> assertTrue(result.missed().getFirst().detail().contains("'nothing'"),
                            result.missed().getFirst()::detail),
                    () -> assertEquals(Set.of(BETA_D2), itemFiles()));
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.CORPUS_SOURCE_MISSED.resolveIdentifierString());
        }

        @Test
        @DisplayName("an item id another source generates too misses the later source and fails the import")
        void duplicateItemId() throws Exception {
            writeSource(new SourceManifest("beta", BETA_REPOSITORY, BETA_COMMIT, "MIT",
                            List.of(new SourceFile(BETA_FILE, Sha256.hex(JSONL), true))),
                    new SourceOverlay("beta", BETA_FILE, null, CREATED,
                            List.of(openRow("2", "screening/alpha", "q-1"))));

            var result = run();

            assertAll(
                    () -> assertEquals(Verdict.FAILED, result.verdict()),
                    () -> assertEquals(List.of(new MissedSource("beta", Reason.MALFORMED, null,
                            "the item 'screening/alpha/q-1' is generated by another source too")), result.missed()),
                    () -> assertEquals(Set.of(ALPHA_Q1, ALPHA_Q2), itemFiles()));
        }

        @Test
        @DisplayName("a manifest that names another source than its directory misses the source")
        void manifestNamesAnotherSource() throws Exception {
            var gamma = new SourceManifest("gamma", ALPHA_REPOSITORY, ALPHA_COMMIT, "CC-BY-4.0",
                    List.of(new SourceFile(ALPHA_FILE, Sha256.hex(CSV), false)));
            var overlay = new SourceOverlay("alpha", ALPHA_FILE, "question_id", CREATED,
                    List.of(closedRow("q-1", "{question}")));
            writeSource("alpha", SourceManifestCodec.write(gamma), SourceOverlayCodec.write(overlay));

            var result = run();

            assertMalformed(result, "alpha", null, "the manifest names the source 'gamma' in the directory 'alpha'");
        }

        @Test
        @DisplayName("an overlay that names another source misses the source")
        void overlayNamesAnotherSource() throws Exception {
            writeSource(alphaManifest(ALPHA_FILE, CSV), new SourceOverlay("beta", ALPHA_FILE, "question_id", CREATED,
                    List.of(closedRow("q-1", "{question}"))));

            var result = run();

            assertMalformed(result, "alpha", ALPHA_FILE, "the overlay does not name a file of the source's manifest");
        }

        @Test
        @DisplayName("an overlay that names a file outside the manifest misses the source")
        void overlayNamesAnotherFile() throws Exception {
            writeSource(alphaManifest(ALPHA_FILE, CSV), new SourceOverlay("alpha", "data/other.csv", "question_id",
                    CREATED, List.of(closedRow("q-1", "{question}"))));

            var result = run();

            assertMalformed(result, "alpha", "data/other.csv",
                    "the overlay does not name a file of the source's manifest");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.role.corpus.CorpusImportTest#unreadableDocuments")
        @DisplayName("a manifest or overlay that cannot be read misses the source")
        void unreadableDocument(String name, byte[] manifest, byte[] overlay, String detail) throws Exception {
            writeSource("alpha", manifest, overlay);

            var result = run();

            assertMalformed(result, "alpha", null, detail);
        }

        @Test
        @DisplayName("a source file that is neither JSON Lines nor CSV misses the source")
        void unknownFileKind() throws Exception {
            var file = "data/test.txt";
            writeSource(alphaManifest(file, CSV), new SourceOverlay("alpha", file, "question_id", CREATED,
                    List.of(closedRow("q-1", "{question}"))));
            repository.files.put("alpha:" + file, CSV);

            var result = run();

            assertMalformed(result, "alpha", file, "the file is neither JSON Lines (.jsonl) nor CSV (.csv)");
        }

        @Test
        @DisplayName("a source file its reader refuses misses the source, naming the line")
        void readerRefusal() throws Exception {
            var broken = "question_id,question\n\"q-1,unterminated\n".getBytes(UTF_8);
            writeSource(alphaManifest(ALPHA_FILE, broken), new SourceOverlay("alpha", ALPHA_FILE, "question_id",
                    CREATED, List.of(closedRow("q-1", "{question}"))));
            repository.files.put("alpha:" + ALPHA_FILE, broken);

            var result = run();

            assertMalformed(result, "alpha", ALPHA_FILE, "line 2");
        }

        @ParameterizedTest(name = "position {0}")
        @ValueSource(strings = {"3", "10", "99999999999"})
        @DisplayName("a position beyond the rows of the file misses the source")
        void positionBeyondRows(String position) throws Exception {
            writeSource(new SourceManifest("beta", BETA_REPOSITORY, BETA_COMMIT, "MIT",
                            List.of(new SourceFile(BETA_FILE, Sha256.hex(JSONL), true))),
                    new SourceOverlay("beta", BETA_FILE, null, CREATED,
                            List.of(openRow(position, "review/beta", "d-x"))));

            var result = run();

            assertMalformed(result, "beta", BETA_FILE, "the file has no row at position " + position);
        }

        @Test
        @DisplayName("the last position of the file still selects its row")
        void lastPosition() {
            var result = run();

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict()),
                    () -> assertEquals("Review: drop the null check", item(BETA_D2).input().question()));
        }

        @Test
        @DisplayName("a key no row of the file has misses the source")
        void keyMatchesNoRow() throws Exception {
            writeSource(alphaManifest(ALPHA_FILE, CSV), new SourceOverlay("alpha", ALPHA_FILE, "question_id",
                    CREATED, List.of(closedRow("q-9", "{question}"))));

            var result = run();

            assertMalformed(result, "alpha", ALPHA_FILE, "the file has 0 rows whose field 'question_id' is 'q-9'");
        }

        @Test
        @DisplayName("a key several rows of the file have misses the source")
        void keyMatchesSeveralRows() throws Exception {
            var twice = (new String(CSV, UTF_8) + "q-1,Again?,weather,yes,no\n").getBytes(UTF_8);
            writeSource(alphaManifest(ALPHA_FILE, twice), new SourceOverlay("alpha", ALPHA_FILE, "question_id",
                    CREATED, List.of(closedRow("q-1", "{question}"))));
            repository.files.put("alpha:" + ALPHA_FILE, twice);

            var result = run();

            assertMalformed(result, "alpha", ALPHA_FILE, "the file has 2 rows whose field 'question_id' is 'q-1'");
        }

        private void assertMalformed(ImportResult result, String source, String file, String detail) {
            var missed = result.missed();
            assertAll(
                    () -> assertEquals(Verdict.FAILED, result.verdict()),
                    () -> assertEquals(1, missed.size(), missed::toString),
                    () -> assertEquals(source, missed.getFirst().name()),
                    () -> assertEquals(Reason.MALFORMED, missed.getFirst().reason()),
                    () -> assertEquals(file, missed.getFirst().file()),
                    () -> assertTrue(missed.getFirst().detail().contains(detail), missed.getFirst()::detail),
                    () -> assertTrue(itemFiles().stream().noneMatch(item -> item.contains("/" + source + "/")),
                            () -> "no item of " + source));
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.CORPUS_SOURCE_MISSED.resolveIdentifierString());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    "Corpus source '%s' missed, its items are left out: malformed".formatted(source));
        }
    }

    @Nested
    @DisplayName("a source outside GitHub")
    class OtherRepository {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"https://huggingface.co/datasets/example/gamma",
                "https://git.example.org:8443/group/sub/gamma.git", "https://github.com/example/gamma/"})
        @DisplayName("any https repository the manifest accepts imports COMPLETE, its items naming it")
        void importsComplete(String uri) throws Exception {
            var gamma = URI.create(uri);
            writeSource(new SourceManifest("gamma", gamma, ALPHA_COMMIT, "CC-BY-4.0",
                            List.of(new SourceFile(ALPHA_FILE, Sha256.hex(CSV), false))),
                    new SourceOverlay("gamma", ALPHA_FILE, "question_id", CREATED,
                            List.of(closedRow("q-1", "{question}", "screening/gamma"))));
            repository.files.put("gamma:" + ALPHA_FILE, CSV);

            var result = run();

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict(), result.missed()::toString),
                    () -> assertTrue(result.covered().contains(new CoveredSource("gamma", 1)),
                            result.covered()::toString),
                    () -> assertEquals(new Origin(gamma, ALPHA_COMMIT, ALPHA_FILE, 2),
                            item("screening/gamma/q-1.json").provenance().origin()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.role.corpus.CorpusImportTest#refusedOrigins")
        @DisplayName("an origin outside the manifest's repository rule is refused, as the manifest refuses it")
        void refusedOrigin(String name, String uri) {
            var origin = URI.create(uri);
            var files = List.of(new SourceFile(ALPHA_FILE, Sha256.hex(CSV), false));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Origin(origin, ALPHA_COMMIT, ALPHA_FILE, 1)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new SourceManifest("gamma", origin, ALPHA_COMMIT, "MIT", files)));
        }
    }

    @Test
    @DisplayName("a cache root below the corpus root is refused before anything is fetched or written")
    void cacheRootBelowCorpusRoot() throws Exception {
        var before = snapshot(corpusRoot);
        var inside = corpusRoot.resolve("cache");
        var corpusImport = new CorpusImport(repository, lfs);

        assertThrows(IllegalArgumentException.class, () -> corpusImport.run(corpusRoot, inside));

        assertAll(
                () -> assertEquals(before, snapshot(corpusRoot)),
                () -> assertEquals(Set.of(), repository.cacheRoots));
    }

    /** The git part of the fetch, serving the files of each source from memory. */
    private static final class FakeRepository implements SourceRepository {

        /** The stored files, keyed {@code <source>:<path>}. */
        private final Map<String, byte[]> files = new HashMap<>();

        /** The failing steps, keyed {@code <source>:prepare}, {@code <source>:fetch} or {@code <source>:read:<path>}. */
        private final Map<String, SourceFetch> failures = new HashMap<>();

        /** The cache roots the import handed over. */
        private final Set<Path> cacheRoots = new HashSet<>();

        @Override
        public SourceFetch prepare(Path cacheRoot, SourceManifest manifest) {
            cacheRoots.add(cacheRoot);
            return failures.getOrDefault(manifest.name() + ":prepare", SourceFetch.done("prepared"));
        }

        @Override
        public SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest) {
            cacheRoots.add(cacheRoot);
            return failures.getOrDefault(manifest.name() + ":fetch", SourceFetch.done("fetched"));
        }

        @Override
        public SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path) {
            cacheRoots.add(cacheRoot);
            var failure = failures.get(manifest.name() + ":read:" + path);
            if (failure != null) {
                return failure;
            }
            var content = files.get(manifest.name() + ":" + path);
            return content == null ? SourceFetch.pinMissing(path + " is absent") : SourceFetch.fetched(content, path);
        }
    }

    /** The LFS part of the fetch, serving the objects from memory. */
    private static final class FakeLfs implements LfsObjectSource {

        /** The objects, keyed by their oid. */
        private final Map<String, byte[]> objects = new HashMap<>();

        /** The failing endpoints, keyed by the repository. */
        private final Map<URI, SourceFetch> failures = new HashMap<>();

        /** The repositories asked for an object, in order. */
        private final List<URI> asked = new ArrayList<>();

        @Override
        public SourceFetch object(URI repository, LfsPointer pointer) {
            asked.add(repository);
            var failure = failures.get(repository);
            if (failure != null) {
                return failure;
            }
            var object = objects.get(pointer.oid());
            return object == null ? SourceFetch.pinMissing("object " + pointer.oid() + " is absent")
                    : SourceFetch.fetched(object, pointer.oid());
        }
    }
}
