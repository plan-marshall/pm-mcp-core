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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.role.corpus.CorpusImport;
import de.planmarshall.core.role.corpus.CorpusValidator;
import de.planmarshall.core.role.corpus.CorpusViolation;
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
 */
@EnableTestLogger
@DisplayName("JGitSourceRepository with the corpus import")
class JGitSourceRepositoryTest {

    private static final String CSV = "question_id,question,option_a,option_b\nq-1,Is the sky blue?,yes,no\n";
    private static final String CHANGED_CSV = "question_id,question,option_a,option_b\nq-1,Is the sky green?,no,yes\n";
    private static final byte[] JSONL = "{\"diff\":\"drop the null check\"}\n".getBytes(UTF_8);
    private static final LocalDate CREATED = LocalDate.of(2026, 10, 14);

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path corpus;
    private Path cache;
    private final Map<String, Path> fixtures = new HashMap<>();
    private final Map<String, byte[]> objects = new HashMap<>();
    private String alphaCommit;
    private String betaCommit;

    @BeforeEach
    void fixtureRepositories() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        corpus = temp.resolve("corpus");
        cache = temp.resolve("cache");
        var pointer = new String(new LfsPointer(Sha256.hex(JSONL), JSONL.length).toBytes(), UTF_8);
        fixtures.put("alpha", temp.resolve("fixtures/alpha"));
        fixtures.put("beta", temp.resolve("fixtures/beta"));
        alphaCommit = JGitCacheRepositoryTest.sourceRepository(git, fixtures.get("alpha"), CSV, pointer);
        objects.put(Sha256.hex(JSONL), JSONL);
    }

    private void betaRepository(String csv) throws IOException {
        betaCommit = JGitCacheRepositoryTest.sourceRepository(git, fixtures.get("beta"), csv, "no pointer\n");
    }

    private void writeCorpus() throws IOException {
        writeSource(new SourceManifest("alpha", URI.create("https://github.com/example/alpha"), alphaCommit, "MIT",
                List.of(new SourceFile("data/test.csv", Sha256.hex(CSV.getBytes(UTF_8)), false),
                        new SourceFile("data/big.jsonl", Sha256.hex(JSONL), true))),
                new SourceOverlay("alpha", "data/big.jsonl", null, CREATED, List.of(new Row("1", "d-1", "review/alpha",
                        "review", "missing null check", List.of(), "Review: {diff}", "", null,
                        new Target(null, "one defect", List.of(), List.of(new RubricItem("d1", "the null check")),
                                false),
                        new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                        new Review(ReviewStatus.DRAFT, null, null, null)))));
        writeSource(new SourceManifest("beta", URI.create("https://github.com/example/beta"), betaCommit, "MIT",
                List.of(new SourceFile("data/test.csv", Sha256.hex(CSV.getBytes(UTF_8)), false))),
                new SourceOverlay("beta", "data/test.csv", "question_id", CREATED, List.of(new Row("q-1", "q-1",
                        "screening/beta", "screening", "sky question", List.of("imported"), "{question}", "",
                        List.of("{option_a}", "{option_b}"), new Target("{option_a}", "the first holds", List.of(),
                                List.of(), false),
                        new Grading(GradingMethod.EXACT_CHOICE, null, null),
                        new Review(ReviewStatus.DRAFT, null, null, null)))));
        Files.writeString(corpus.resolve(CorpusValidator.ATTRIBUTION), "## alpha\n\nMIT\n\n## beta\n\nMIT\n");
        var schema = corpus.resolve(CorpusValidator.ITEM_SCHEMA);
        Files.createDirectories(schema.getParent());
        try (var in = CorpusValidator.class.getResourceAsStream("imported-item.schema.json")) {
            assertNotNull(in, "the imported item schema of pm-core");
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

    private static String report(List<CorpusViolation> violations) {
        return violations.stream().map(CorpusViolation::toString).collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("fetches the pinned sources, verifies them and generates items the validator accepts")
    void complete() throws IOException {
        betaRepository(CSV);
        writeCorpus();

        var result = run();

        var violations = new CorpusValidator(corpus).validate(cache);
        assertAll(
                () -> assertEquals(Verdict.COMPLETE, result.verdict(), result::toString),
                () -> assertEquals(List.of(new CoveredSource("alpha", 1), new CoveredSource("beta", 1)),
                        result.covered()),
                () -> assertEquals(List.of("review/alpha/d-1.json", "screening/beta/q-1.json"), itemFiles()),
                () -> assertEquals(List.of(), violations, () -> report(violations)),
                () -> assertTrue(Files.isDirectory(cache.resolve(JGitSourceRepository.REPOSITORIES)
                        .resolve("alpha.git").resolve("objects"))));
    }

    @Test
    @DisplayName("a changed file fails its hash and the import, the other source's items are still generated")
    void changedFile() throws IOException {
        betaRepository(CHANGED_CSV);
        writeCorpus();

        var result = run();

        assertAll(
                () -> assertEquals(Verdict.FAILED, result.verdict(), result::toString),
                () -> assertEquals("beta", result.missed().getFirst().name()),
                () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason()),
                () -> assertEquals("data/test.csv", result.missed().getFirst().file()),
                () -> assertEquals(List.of("review/alpha/d-1.json"), itemFiles()));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                CoreLogMessages.ERROR.CORPUS_SOURCE_HASH_MISMATCH.resolveIdentifierString());
    }

    @Test
    @DisplayName("a removed fixture repository is missed by name, the other source's items remain")
    void removedRepository() throws IOException {
        betaRepository(CSV);
        writeCorpus();
        FileUtils.delete(fixtures.get("alpha").toFile(), FileUtils.RECURSIVE);

        var result = run();

        assertAll(
                () -> assertEquals(Verdict.PARTIAL, result.verdict(), result::toString),
                () -> assertEquals(List.of("alpha"), result.missed().stream().map(MissedSource::name).toList()),
                () -> assertEquals(Reason.UNREACHABLE, result.missed().getFirst().reason()),
                () -> assertEquals(List.of("screening/beta/q-1.json"), itemFiles()));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                CoreLogMessages.WARN.CORPUS_SOURCE_MISSED.resolveIdentifierString());
    }
}
