/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.corpus;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.CorpusImport;
import de.planmarshall.core.role.corpus.ImportResult.Reason;
import de.planmarshall.core.role.corpus.ImportResult.Verdict;
import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.LfsPointer;
import de.planmarshall.core.role.corpus.Sha256;
import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.core.role.corpus.SourceFetch.Outcome;
import de.planmarshall.core.role.corpus.SourceManifest;
import de.planmarshall.core.role.corpus.SourceManifest.FetchKind;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceManifestCodec;
import de.planmarshall.core.role.corpus.SourceOverlay;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;
import de.planmarshall.core.role.corpus.SourceOverlayCodec;
import de.planmarshall.core.role.corpus.SourceRepository;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The HTTPS corpus source repository (PM-WORK-6, PM-EXEC-5) against a mock web server: the pin confirmation and the
 * raw read of a Hugging Face dataset, the outcomes of a pin or a file the dataset lacks and of an endpoint that does
 * not answer, and the URL rules that refuse a repository before anything is sent.
 * <p>
 * Every request is answered by one response primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target, so a request for a wrong path gets no answer; each
 * nested group returns that one element from its own {@code getModuleDispatcher()}. Contract literals, kept by
 * design: the Hugging Face URL grammar ({@code /datasets/{owner}/{name}}, {@code /raw/{commit}/{path}},
 * {@code /api/datasets/{owner}/{name}/revision/{commit}}); the status codes; the file name whose segments need
 * percent-encoding and its encoded form; the refused URL shapes and the user information of the refused URL; the
 * {@code huggingface.co} and {@code github.com} origins; the {@code answered}, {@code refused} and maximum wording of
 * the details; the Persian locale; the fixed fields of the overlay row. The dataset, the commit, the file, the CSV
 * content, the question id and the size cap are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@EnableTestLogger
@ModuleDispatcher
@DisplayName("HuggingFaceSourceRepository")
class HuggingFaceSourceRepositoryTest {

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private String base;
    private String name;
    private String dataset;
    private String commit;
    private String file;
    private String questionId;
    private byte[] csv;
    private HuggingFaceSourceRepository repository;

    @TempDir
    Path cacheRoot;

    /** Answers a GET with the next response primed for its target; anything else gets no answer. */
    ModuleDispatcherElement dispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/";
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                Deque<MockResponse> queue = routes.get(request.getMethod() + " " + request.getTarget());
                return Optional.ofNullable(queue == null ? null : queue.poll());
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET);
            }
        };
    }

    ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher();
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        URI root = uriBuilder.build();
        base = root.getScheme() + "://" + root.getHost() + ":" + root.getPort();
        name = word();
        dataset = word() + "/" + name;
        commit = Generators.strings("0123456789abcdef", 40, 40).next();
        file = "data/" + word() + ".csv";
        questionId = "q-" + word();
        csv = ("question_id,question\n" + questionId + "," + word() + " " + word() + "?\n").getBytes(UTF_8);
        repository = new HuggingFaceSourceRepository(true, 2, 1);
    }

    private void on(String target, MockResponse response) {
        routes.computeIfAbsent("GET " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    /** @return a generated lower-case word, safe in a URL path and a CSV cell */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    private static MockResponse notFound() {
        return new MockResponse.Builder().code(404).build();
    }

    private static MockResponse status(int status) {
        return new MockResponse.Builder().code(status).body("{}").build();
    }

    private static MockResponse bytes(byte[] body) {
        return new MockResponse.Builder().code(200).body(new String(body, UTF_8)).build();
    }

    private String repositoryPath() {
        return "/datasets/" + dataset;
    }

    private String revision() {
        return "/api/datasets/" + dataset + "/revision/" + commit;
    }

    private String raw() {
        return repositoryPath() + "/raw/" + commit + "/" + file;
    }

    private URI datasetUri() {
        return URI.create(base + repositoryPath());
    }

    private SourceManifest anyManifest() {
        return new SourceManifest(name, URI.create("https://huggingface.co" + repositoryPath()), commit, "MIT",
                FetchKind.HUGGINGFACE, List.of(new SourceFile(file, Sha256.hex(csv), false)));
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "a request reached the mock web server");
        return request;
    }

    private static void assertUnavailableLogged() {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.CORPUS_HTTPS_SOURCE_UNAVAILABLE.resolveIdentifierString());
    }

    @Test
    @DisplayName("prepare creates nothing and makes no request")
    void prepare() {
        var fetch = repository.prepare(cacheRoot, anyManifest());

        assertAll("the prepared source",
                () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                () -> assertEquals(0, fetch.content().length, "prepare delivers no content"),
                () -> assertEquals(0, server.getRequestCount(), "prepare sends no request"),
                () -> assertEquals(0, entries(cacheRoot), "nothing is created"));
    }

    private static long entries(Path root) throws IOException {
        try (var walk = Files.list(root)) {
            return walk.count();
        }
    }

    @Nested
    @DisplayName("the pin confirmation")
    class PinConfirmation {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("a 200 is FETCHED, with the revision of the dataset requested")
        void confirmed() throws Exception {
            on(revision(), status(200));

            var fetch = repository.fetchCommit(datasetUri(), commit);

            assertAll("the confirmed pin",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertEquals(0, fetch.content().length, "a pin confirmation has no content"),
                    () -> assertEquals(1, server.getRequestCount(), "one revision request is sent"));
            var request = nextRequest();
            assertAll("the revision request",
                    () -> assertEquals("GET", request.getMethod(), "the revision is read with GET"),
                    () -> assertEquals(revision(), request.getTarget(), "the revision of the pin is asked"));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, HuggingFaceSourceRepository.class);
        }

        @Test
        @DisplayName("a 404 is PIN_MISSING, naming the commit")
        void pinMissing() {
            on(revision(), notFound());

            var fetch = repository.fetchCommit(datasetUri(), commit);

            assertAll("the missing pin",
                    () -> assertEquals(Outcome.PIN_MISSING, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().contains(commit), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a server error is UNREACHABLE")
        void serverError() {
            on(revision(), status(503));

            var fetch = repository.fetchCommit(datasetUri(), commit);

            assertAll("the server error",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 503"), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a stalled response is UNREACHABLE after the read timeout")
        void stalled() {
            on(revision(), new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS).body("{}").build());

            var fetch = repository.fetchCommit(datasetUri(), commit);

            assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }
    }

    @Nested
    @DisplayName("the raw read")
    class RawRead {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("delivers the file at the pin, from the raw URL of the repository")
        void reads() throws Exception {
            on(raw(), bytes(csv));

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertAll("the delivered file",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(csv, fetch.content(), "the raw bytes are delivered"),
                    () -> assertEquals(1, server.getRequestCount(), "one raw request is sent"));
            var request = nextRequest();
            assertAll("the raw request",
                    () -> assertEquals("GET", request.getMethod(), "the raw file is read with GET"),
                    () -> assertEquals(raw(), request.getTarget(), "the raw URL at the pin is asked"));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, HuggingFaceSourceRepository.class);
        }

        @Test
        @DisplayName("delivers a file whose path needs encoding at the raw URL that encodes each segment")
        void readsPathNeedingEncoding() throws Exception {
            // the reserved characters of a path segment and their percent-encoding are the contract
            var reserved = "data/a b#c?d%e.csv";
            var encoded = repositoryPath() + "/raw/" + commit + "/data/a%20b%23c%3Fd%25e.csv";
            on(encoded, bytes(csv));

            var fetch = repository.readFile(datasetUri(), commit, reserved);

            assertAll("the encoded read",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(csv, fetch.content(), "the raw bytes are delivered"),
                    () -> assertEquals(1, server.getRequestCount(), "one raw request is sent"));
            var request = nextRequest();
            assertAll("the encoded request",
                    () -> assertEquals("GET", request.getMethod(), "the raw file is read with GET"),
                    () -> assertEquals(encoded, request.getTarget(), "each path segment is encoded"));
        }

        @Test
        @DisplayName("delivers a file in Git LFS as its pointer, for the import to download the object")
        void lfsPointer() {
            var pointer = new LfsPointer(Sha256.hex(csv), csv.length).toBytes();
            on(raw(), bytes(pointer));

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertAll("the delivered pointer",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(pointer, fetch.content(), "the pointer bytes are delivered"));
        }

        @Test
        @DisplayName("delivers a body of exactly the compiled maximum")
        void atCap() {
            int cap = Generators.integers(8, 64).next();
            var body = "x".repeat(cap).getBytes(UTF_8);
            var capped = new HuggingFaceSourceRepository(true, 2, 1, cap);
            on(raw(), bytes(body));

            var fetch = capped.readFile(datasetUri(), commit, file);

            assertAll("the body at the cap",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(body, fetch.content(), "a body at the cap is delivered"));
        }

        @Test
        @DisplayName("a body over the compiled maximum is UNREACHABLE and is never held")
        void overCap() {
            int cap = Generators.integers(8, 64).next();
            var body = "x".repeat(cap + 1).getBytes(UTF_8);
            var capped = new HuggingFaceSourceRepository(true, 2, 1, cap);
            on(raw(), bytes(body));

            var fetch = capped.readFile(datasetUri(), commit, file);

            assertAll("the body over the cap",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertEquals(0, fetch.content().length, "no content is held"),
                    () -> assertTrue(fetch.detail().contains("larger than the maximum of " + cap + " bytes"),
                            fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a 404 is PIN_MISSING")
        void fileMissing() {
            on(raw(), notFound());

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertEquals(Outcome.PIN_MISSING, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a server error is UNREACHABLE")
        void serverError() {
            on(raw(), status(500));

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertAll("the server error",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 500"), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a stalled response is UNREACHABLE after the read timeout")
        void stalled() {
            on(raw(), new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS)
                    .body(new String(csv, UTF_8)).build());

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }
    }

    @Nested
    @DisplayName("a repository the client refuses")
    class Refused {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("a repository that is not https is refused with nothing sent")
        void cleartext() {
            var fetch = new HuggingFaceSourceRepository().readFile(datasetUri(), commit, file);

            assertAll("the refused cleartext repository",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a repository with user information is refused with nothing sent")
        void userInfo() {
            var uri = URI.create(base.replace("http://", "http://user:pa55word@") + repositoryPath());

            var fetch = repository.readFile(uri, commit, file);

            assertAll("the refused user information",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertFalse(fetch.detail().contains("pa55word"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
            assertUnavailableLogged();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"example/gamma", "mailto:user@example.org", "https:/example/gamma", "file:///tmp/gamma",
                "//example.org/gamma"})
        @DisplayName("a repository URL without scheme or host is UNREACHABLE, logs the WARN and never throws")
        void withoutSchemeOrHost(String uri) {
            var fetch = repository.readFile(URI.create(uri), commit, file);

            assertAll("the refused URL",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
            assertUnavailableLogged();
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "<a URL without scheme or host>");
        }

        @Test
        @DisplayName("a repository that is no Hugging Face dataset URL is refused with nothing sent")
        void notADataset() {
            var fetch = repository.readFile(URI.create("https://github.com/" + dataset), commit, file);

            assertAll("the refused foreign repository",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
            assertUnavailableLogged();
        }
    }

    /** Runs under a default locale with non-Latin digits, so a status a detail names must be ASCII digits. */
    @Nested
    @DisplayName("under a default locale with non-Latin digits")
    class NonLatin {

        private static final Locale PERSIAN = Locale.forLanguageTag("fa-IR");

        private Locale previous;

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @BeforeEach
        void persianDigits() {
            previous = Locale.getDefault(Locale.Category.FORMAT);
            Locale.setDefault(Locale.Category.FORMAT, PERSIAN);
            assertNotEquals("1", "%d".formatted(1), "the locale formats digits of its own, so the test can fail");
        }

        @AfterEach
        void restore() {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }

        @Test
        @DisplayName("a status in a detail is written in ASCII digits")
        void statusDetail() {
            on(raw(), status(503));

            var fetch = repository.readFile(datasetUri(), commit, file);

            assertAll("the ASCII status",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 503"), fetch::detail));
        }
    }

    /**
     * The client within the import: the manifest names an {@code https} Hugging Face repository, and the source the
     * import hands it to redirects that repository to the mock web server, so the item is generated from the bytes the
     * raw endpoint delivers.
     */
    @Nested
    @DisplayName("within the corpus import")
    class WithinImport {

        @TempDir
        Path importBase;

        private Path corpusRoot;

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @BeforeEach
        void corpus() throws IOException {
            corpusRoot = importBase.resolve("corpus");
            var directory = corpusRoot.resolve(CorpusImport.SOURCES).resolve(name);
            Files.createDirectories(directory);
            Files.write(directory.resolve(CorpusImport.MANIFEST), SourceManifestCodec.write(anyManifest()));
            Files.write(directory.resolve(CorpusImport.OVERLAY), SourceOverlayCodec.write(new SourceOverlay(name,
                    file, "question_id", LocalDate.of(2026, 10, 14), List.of(new Row(questionId, questionId,
                            "screening/" + name, "screening", "closed question", List.of("imported"), null,
                            "{question}", "", null, null, new Target(null, "the first holds", List.of(), List.of(), false),
                            new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                            new Review(ReviewStatus.DRAFT, null, null, null), null)))));
        }

        private CorpusImport corpusImport() {
            SourceRepository redirected = new SourceRepository() {
                @Override
                public SourceFetch prepare(Path root, SourceManifest manifest) {
                    return repository.prepare(root, manifest);
                }

                @Override
                public SourceFetch fetchCommit(Path root, SourceManifest manifest) {
                    return repository.fetchCommit(datasetUri(), manifest.commit());
                }

                @Override
                public SourceFetch readFile(Path root, SourceManifest manifest, String path) {
                    return repository.readFile(datasetUri(), manifest.commit(), path);
                }
            };
            return new CorpusImport(redirected, (uri, pointer) -> SourceFetch.pinMissing("no LFS object here"));
        }

        @Test
        @DisplayName("the delivered file yields the item, COMPLETE")
        void complete() {
            on(revision(), status(200));
            on(raw(), bytes(csv));

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll("the complete import",
                    () -> assertEquals(Verdict.COMPLETE, result.verdict(), result::toString),
                    () -> assertEquals(1, result.items(), "the one item is generated"));
        }

        @Test
        @DisplayName("delivered bytes that are not the pinned SHA-256 fail the run")
        void mismatched() {
            var other = ("question_id,question\n" + questionId + "," + word() + "!\n").getBytes(UTF_8);
            on(revision(), status(200));
            on(raw(), bytes(other));

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll("the mismatched import",
                    () -> assertEquals(Verdict.FAILED, result.verdict(), result::toString),
                    () -> assertEquals(1, result.missed().size(), "the one file is missed"),
                    () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason(),
                            "the miss is a hash mismatch"),
                    () -> assertEquals(0, result.items(), "no item is generated"));
        }
    }
}
