/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.role.corpus.CorpusImport;
import de.planmarshall.core.role.corpus.CorpusValidator;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportResult.CoveredSource;
import de.planmarshall.core.role.corpus.ImportResult.MissedSource;
import de.planmarshall.core.role.corpus.ImportResult.Reason;
import de.planmarshall.core.role.corpus.ImportResult.Verdict;
import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.LfsObjectSource;
import de.planmarshall.core.role.corpus.LfsPointer;
import de.planmarshall.core.role.corpus.Sha256;
import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.core.role.corpus.SourceManifest;
import de.planmarshall.core.role.corpus.SourceManifest.FetchKind;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceManifestCodec;
import de.planmarshall.core.role.corpus.SourceOverlay;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;
import de.planmarshall.core.role.corpus.SourceOverlayCodec;
import de.planmarshall.provider.git.JGitSourceRepository.Remote;

/**
 * The git side of the fetch of the corpus sources end to end, without network (PM-WORK-6, PM-EXEC-5): two local
 * fixture repositories, one with a plain file and a file kept in Git LFS, a corpus whose manifests and overlays the
 * test writes, {@link CorpusImport} with this adapter and an in-test {@link LfsObjectSource} serving the object. The
 * pinned sources are fetched, verified and generated into items the {@link CorpusValidator} accepts; a changed file
 * fails its hash; a removed fixture repository is missed by name while the other source's items remain.
 * <p>
 * The source names, the row keys, the categories and the item paths are fixture identifiers the expected item files
 * are named by; the CSV header, the overlay templates, the licence and the repository URLs are the contract of the
 * source manifest and overlay; the cell texts, the LFS object and the dates are generated.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("JGitSourceRepository with the corpus import")
class JGitSourceRepositoryTest {

    /** The CSV header whose columns the overlay templates name. */
    private static final String CSV_HEADER = "question_id,question,option_a,option_b\n";
    /** The licence of both fixture sources, an SPDX identifier. */
    private static final String LICENCE = "MIT";
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path corpus;
    private Path cache;
    private final Map<String, Path> fixtures = new HashMap<>();
    private final Map<String, byte[]> objects = new HashMap<>();
    private String csv;
    private String changedCsv;
    private byte[] jsonl;
    private LocalDate created;
    private String alphaCommit;
    private String betaCommit;

    private static String text() {
        return Generators.letterStrings(3, 30).next();
    }

    private static String csvRow(String question, String optionA, String optionB) {
        return CSV_HEADER + "q-1," + question + "," + optionA + "," + optionB + "\n";
    }

    @BeforeEach
    void fixtureRepositories() throws IOException {
        var question = text();
        csv = csvRow(question, text(), text());
        changedCsv = csvRow(question + text(), text(), text());
        jsonl = ("{\"diff\":\"" + text() + "\"}\n").getBytes(UTF_8);
        created = LocalDate.of(Generators.integers(2000, 2100).next(), Generators.integers(1, 12).next(),
                Generators.integers(1, 28).next());
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture paths of the corpus, the cache and the two source repositories
        corpus = temp.resolve("corpus");
        cache = temp.resolve("cache");
        var pointer = new String(new LfsPointer(Sha256.hex(jsonl), jsonl.length).toBytes(), UTF_8);
        fixtures.put(ALPHA, temp.resolve("fixtures/alpha"));
        fixtures.put(BETA, temp.resolve("fixtures/beta"));
        alphaCommit = JGitCacheRepositoryTest.sourceRepository(git, fixtures.get(ALPHA), csv, pointer);
        objects.put(Sha256.hex(jsonl), jsonl);
    }

    private void betaRepository(String content) throws IOException {
        betaCommit = JGitCacheRepositoryTest.sourceRepository(git, fixtures.get(BETA), content,
                JGitCacheRepositoryTest.line());
    }

    private SourceFile csvFile() {
        return new SourceFile(JGitCacheRepositoryTest.CSV_PATH, Sha256.hex(csv.getBytes(UTF_8)), false);
    }

    private void writeCorpus() throws IOException {
        writeSource(new SourceManifest(ALPHA, URI.create("https://github.com/example/alpha"), alphaCommit, LICENCE,
                        FetchKind.GIT, List.of(csvFile(),
                                new SourceFile(JGitCacheRepositoryTest.POINTER_PATH, Sha256.hex(jsonl), true))),
                new SourceOverlay(ALPHA, JGitCacheRepositoryTest.POINTER_PATH, null, created, List.of(new Row("1",
                        "d-1", "review/alpha", "review", text(), List.of(), null, "Review: {diff}", "", null, null,
                        new Target(null, text(), List.of(), List.of(new RubricItem("d1", text())), false),
                        new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                        new Review(ReviewStatus.DRAFT, null, null, null), null))));
        writeSource(new SourceManifest(BETA, URI.create("https://github.com/example/beta"), betaCommit, LICENCE,
                        FetchKind.GIT, List.of(csvFile())),
                new SourceOverlay(BETA, JGitCacheRepositoryTest.CSV_PATH, "question_id", created, List.of(new Row("q-1",
                        "q-1", "screening/beta", "screening", text(), List.of("imported"), null, "{question}", "",
                        List.of("{option_a}", "{option_b}"), null, new Target("{option_a}", text(),
                                List.of(), List.of(), false),
                        new Grading(GradingMethod.EXACT_CHOICE, null, null),
                        new Review(ReviewStatus.DRAFT, null, null, null), null))));
        // the attribution format: one heading per source, followed by its licence
        Files.writeString(corpus.resolve(CorpusValidator.ATTRIBUTION),
                "## " + ALPHA + "\n\n" + LICENCE + "\n\n## " + BETA + "\n\n" + LICENCE + "\n");
        var schema = corpus.resolve(CorpusValidator.ITEM_SCHEMA);
        Files.createDirectories(schema.getParent());
        try (var in = CorpusValidator.class.getResourceAsStream("imported-item.schema.json")) {
            assertNotNull(in, "the imported item schema of pm-core is found");
            Files.write(schema, in.readAllBytes());
        }
    }

    private void writeSource(SourceManifest manifest, SourceOverlay overlay) throws IOException {
        var directory = corpus.resolve(CorpusImport.SOURCES).resolve(manifest.name());
        Files.createDirectories(directory);
        Files.write(directory.resolve(CorpusImport.MANIFEST), SourceManifestCodec.write(manifest));
        Files.write(directory.resolve(CorpusImport.OVERLAY), SourceOverlayCodec.write(overlay));
    }

    private ImportResult run() {
        var repository = new JGitSourceRepository(git,
                manifest -> new Remote(fixtures.get(manifest.name()).toUri().toString(), RemotePolicy.localOnly()));
        LfsObjectSource lfs = (repositoryUri, pointer) -> {
            var object = objects.get(pointer.oid());
            return object == null ? SourceFetch.pinMissing("no object " + pointer.oid())
                    : SourceFetch.fetched(object, pointer.oid());
        };
        return new CorpusImport(repository, lfs).run(corpus, cache);
    }

    private List<String> itemFiles() throws IOException {
        var items = cache.resolve(CorpusImport.ITEMS);
        if (!Files.isDirectory(items)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(items)) {
            return files.filter(Files::isRegularFile).map(file -> items.relativize(file).toString()).sorted()
                    .toList();
        }
    }

    private static SourceManifest gitManifest(String name, String url, SourceFile file) {
        return new SourceManifest(name, URI.create(url), JGitCacheRepositoryTest.commitId(), LICENCE, FetchKind.GIT,
                List.of(file));
    }

    @Test
    @DisplayName("fetches the pinned sources, verifies them and generates items the validator accepts")
    void complete() throws Exception {
        betaRepository(csv);
        writeCorpus();

        var result = run();

        var violations = new CorpusValidator(corpus).validate(cache);
        assertAll(
                () -> assertEquals(Verdict.COMPLETE, result.verdict(), "the import of both sources is complete"),
                () -> assertEquals(List.of(new CoveredSource(ALPHA, 1), new CoveredSource(BETA, 1)),
                        result.covered(), "both sources are covered with one item"),
                () -> assertEquals(List.of("review/alpha/d-1.json", "screening/beta/q-1.json"), itemFiles(),
                        "one item file is generated per source"),
                () -> assertEquals(List.of(), violations, "the validator accepts the generated items"),
                () -> assertTrue(Files.isDirectory(cache.resolve(JGitSourceRepository.REPOSITORIES)
                        .resolve("alpha.git").resolve("objects")), "the source's cache repository is created"));
    }

    @Test
    @DisplayName("a changed file fails its hash and the import, the other source's items are still generated")
    void changedFile() throws Exception {
        betaRepository(changedCsv);
        writeCorpus();

        var result = run();

        assertAll(
                () -> assertEquals(Verdict.FAILED, result.verdict(), "a hash mismatch fails the import"),
                () -> assertEquals(BETA, result.missed().getFirst().name(), "the changed source is the one missed"),
                () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason(),
                        "the changed source is missed for its hash"),
                () -> assertEquals(JGitCacheRepositoryTest.CSV_PATH, result.missed().getFirst().file(),
                        "the miss names the changed file"),
                () -> assertEquals(List.of("review/alpha/d-1.json"), itemFiles(),
                        "the other source's item is still generated"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                CoreLogMessages.ERROR.CORPUS_SOURCE_HASH_MISMATCH.resolveIdentifierString());
    }

    @Test
    @DisplayName("a removed fixture repository is missed by name, the other source's items remain")
    void removedRepository() throws Exception {
        betaRepository(csv);
        writeCorpus();
        FileUtils.delete(fixtures.get(ALPHA).toFile(), FileUtils.RECURSIVE);

        var result = run();

        assertAll(
                () -> assertEquals(Verdict.PARTIAL, result.verdict(), "a missed source makes the import partial"),
                () -> assertEquals(List.of(ALPHA), result.missed().stream().map(MissedSource::name).toList(),
                        "the removed source is missed by name"),
                () -> assertEquals(Reason.UNREACHABLE, result.missed().getFirst().reason(),
                        "the removed source is unreachable"),
                () -> assertEquals(List.of("screening/beta/q-1.json"), itemFiles(),
                        "the other source's item remains"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                CoreLogMessages.WARN.CORPUS_SOURCE_MISSED.resolveIdentifierString());
    }

    @Test
    @DisplayName("a pinned commit the repository does not serve is missed as pin_missing, not unreachable")
    void unservedCommit() throws Exception {
        betaRepository(csv);
        alphaCommit = JGitCacheRepositoryTest.commitId();
        writeCorpus();

        var result = run();

        assertAll(
                () -> assertEquals(Verdict.PARTIAL, result.verdict(), "an unserved pin makes the import partial"),
                () -> assertEquals(List.of(ALPHA), result.missed().stream().map(MissedSource::name).toList(),
                        "the source of the unserved pin is missed"),
                () -> assertEquals(Reason.PIN_MISSING, result.missed().getFirst().reason(),
                        "an unserved pin is missing, not unreachable"),
                // the detail prefix is the outcome of the git contract
                () -> assertTrue(result.missed().getFirst().detail().startsWith("unknown_revision of "),
                        "the detail names the unknown revision"),
                () -> assertEquals(List.of("screening/beta/q-1.json"), itemFiles(),
                        "the other source's item remains"));
    }

    @Test
    @DisplayName("a path that is no file at the pinned commit is pin_missing")
    void absentPath() throws Exception {
        betaRepository(csv);
        var manifest = new SourceManifest(BETA, URI.create("https://github.com/example/beta"), betaCommit, LICENCE,
                FetchKind.GIT, List.of(csvFile()));
        var repository = new JGitSourceRepository(git,
                source -> new Remote(fixtures.get(source.name()).toUri().toString(), RemotePolicy.localOnly()));
        assertTrue(repository.prepare(cache, manifest).isFetched(), "the source's cache is prepared");
        assertTrue(repository.fetchCommit(cache, manifest).isFetched(), "the pinned commit is fetched");

        // the extension of no fixture file, so a generated name never matches data/test.csv
        var fetch = repository.readFile(cache, manifest, "data/" + text() + ".absent");

        assertAll(
                () -> assertEquals(SourceFetch.Outcome.PIN_MISSING, fetch.outcome(),
                        "a path absent at the pin is a missing pin"),
                // the detail is the git outcome and the manifest's repository URL
                () -> assertTrue(fetch.detail().startsWith("path_not_found of https://github.com/example/beta"),
                        "the detail names the outcome and repository"));
    }

    @Test
    @DisplayName("the default remote fetches from the manifest's repository, admitted by its https origin alone")
    void defaultRemote() {
        // port 1 of the loopback address: an https origin that never answers
        var manifest = gitManifest("gamma", "https://127.0.0.1:1/example/gamma", csvFile());
        var repository = new JGitSourceRepository(git);
        assertTrue(repository.prepare(cache, manifest).isFetched(), "the source's cache is prepared");

        var fetch = repository.fetchCommit(cache, manifest);

        assertAll(
                () -> assertEquals(SourceFetch.Outcome.UNREACHABLE, fetch.outcome(),
                        "an origin that never answers is unreachable"),
                () -> assertTrue(fetch.detail().startsWith("failed of https://127.0.0.1:1/example/gamma"),
                        "the detail names the failed repository URL"));
    }

    @Test
    @DisplayName("Remote.of names the manifest's repository and admits its origin with its port, nothing else")
    void remoteOf() {
        // the URLs, schemes and ports are the contract of the origin admission
        var manifest = gitManifest("gamma", "https://git.example.org:8443/group/gamma", csvFile());
        var standard = gitManifest("delta", "https://github.com/example/delta/", csvFile());

        var remote = Remote.of(manifest);
        var standardRemote = Remote.of(standard);

        var policy = remote.policy();
        assertAll(
                () -> assertEquals("https://git.example.org:8443/group/gamma", remote.url(),
                        "the remote is the manifest's repository"),
                () -> assertTrue(policy.permits(new URIish("https://git.example.org:8443/group/gamma")),
                        "the origin with its port is admitted"),
                () -> assertFalse(policy.permits(new URIish("https://git.example.org/group/gamma")),
                        "the origin without its port is refused"),
                () -> assertFalse(policy.permits(new URIish("https://other.example.org:8443/group/gamma")),
                        "another host on the same port is refused"),
                () -> assertFalse(policy.permits(new URIish("http://git.example.org:8443/group/gamma")),
                        "plain http on the same origin is refused"),
                () -> assertFalse(policy.permits(new URIish("file:///tmp/gamma")),
                        "a local repository is refused"),
                () -> assertEquals("https://github.com/example/delta/", standardRemote.url(),
                        "the standard remote keeps the manifest URL"),
                () -> assertTrue(standardRemote.policy().permits(new URIish("https://github.com:443/example/delta")),
                        "the default https port is the same origin"),
                () -> assertFalse(standardRemote.policy().permits(new URIish("https://gitlab.com/example/delta")),
                        "a foreign host is refused by the standard"));
    }
}
