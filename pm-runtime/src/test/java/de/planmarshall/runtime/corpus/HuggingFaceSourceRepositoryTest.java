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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import mockwebserver3.Dispatcher;
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
 * The HTTPS corpus source repository (PM-WORK-6, PM-EXEC-5) against a mock web server with its own dispatcher: the pin
 * confirmation and the raw read of a Hugging Face dataset, the outcomes of a pin or a file the dataset lacks and of an
 * endpoint that does not answer, and the URL rules that refuse a repository before anything is sent.
 */
@EnableMockWebServer
@EnableTestLogger
@DisplayName("HuggingFaceSourceRepository")
class HuggingFaceSourceRepositoryTest {

    private static final String COMMIT = "1".repeat(40);
    private static final String DATASET = "example/gamma";
    private static final String REPOSITORY_PATH = "/datasets/" + DATASET;
    private static final String REVISION = "/api/datasets/" + DATASET + "/revision/" + COMMIT;
    private static final String FILE = "data/test.csv";
    private static final String RAW = REPOSITORY_PATH + "/raw/" + COMMIT + "/" + FILE;
    private static final byte[] CSV = "question_id,question\nq-1,Is the sky blue?\n".getBytes(UTF_8);

    /** Canned responses by {@code METHOD target}, each answering one request; anything else is answered 404. */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    private MockWebServer server;
    private HuggingFaceSourceRepository repository;

    @TempDir
    Path cacheRoot;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Deque<MockResponse> queue = responses.get(request.getMethod() + " " + request.getTarget());
                MockResponse next = queue == null ? null : queue.poll();
                return next == null ? notFound() : next;
            }
        });
        repository = new HuggingFaceSourceRepository(true, 2, 1);
    }

    private void on(String method, String target, MockResponse response) {
        responses.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
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

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    private URI datasetUri() {
        return URI.create(base() + REPOSITORY_PATH);
    }

    private static SourceManifest anyManifest() {
        return new SourceManifest("gamma", URI.create("https://huggingface.co" + REPOSITORY_PATH), COMMIT, "MIT",
                FetchKind.HUGGINGFACE, List.of(new SourceFile(FILE, Sha256.hex(CSV), false)));
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
    void prepare() throws Exception {
        var fetch = repository.prepare(cacheRoot, anyManifest());

        assertAll(
                () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                () -> assertEquals(0, fetch.content().length),
                () -> assertEquals(0, server.getRequestCount()),
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

        @Test
        @DisplayName("a 200 is FETCHED, with the revision of the dataset requested")
        void confirmed() throws Exception {
            on("GET", REVISION, status(200));

            var fetch = repository.fetchCommit(datasetUri(), COMMIT);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertEquals(0, fetch.content().length),
                    () -> assertEquals(1, server.getRequestCount()));
            var revision = nextRequest();
            assertAll(
                    () -> assertEquals("GET", revision.getMethod()),
                    () -> assertEquals(REVISION, revision.getTarget()));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, HuggingFaceSourceRepository.class);
        }

        @Test
        @DisplayName("a 404 is PIN_MISSING, naming the commit")
        void pinMissing() {
            on("GET", REVISION, notFound());

            var fetch = repository.fetchCommit(datasetUri(), COMMIT);

            assertAll(
                    () -> assertEquals(Outcome.PIN_MISSING, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().contains(COMMIT), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a server error is UNREACHABLE")
        void serverError() {
            on("GET", REVISION, status(503));

            var fetch = repository.fetchCommit(datasetUri(), COMMIT);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 503"), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a stalled response is UNREACHABLE after the read timeout")
        void stalled() {
            on("GET", REVISION, new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS)
                    .body("{}").build());

            var fetch = repository.fetchCommit(datasetUri(), COMMIT);

            assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }
    }

    @Nested
    @DisplayName("the raw read")
    class RawRead {

        @Test
        @DisplayName("delivers the file at the pin, from the raw URL of the repository")
        void reads() throws Exception {
            on("GET", RAW, bytes(CSV));

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(CSV, fetch.content()),
                    () -> assertEquals(1, server.getRequestCount()));
            var raw = nextRequest();
            assertAll(
                    () -> assertEquals("GET", raw.getMethod()),
                    () -> assertEquals(RAW, raw.getTarget()));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, HuggingFaceSourceRepository.class);
        }

        @Test
        @DisplayName("delivers a file in Git LFS as its pointer, for the import to download the object")
        void lfsPointer() {
            var pointer = new LfsPointer(Sha256.hex(CSV), CSV.length).toBytes();
            on("GET", RAW, bytes(pointer));

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(pointer, fetch.content()));
        }

        @Test
        @DisplayName("a 404 is PIN_MISSING")
        void fileMissing() {
            on("GET", RAW, notFound());

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertEquals(Outcome.PIN_MISSING, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a server error is UNREACHABLE")
        void serverError() {
            on("GET", RAW, status(500));

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 500"), fetch::detail));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a stalled response is UNREACHABLE after the read timeout")
        void stalled() {
            on("GET", RAW, new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS)
                    .body(new String(CSV, UTF_8)).build());

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
            assertUnavailableLogged();
        }
    }

    @Nested
    @DisplayName("a repository the client refuses")
    class Refused {

        @Test
        @DisplayName("a repository that is not https is refused with nothing sent")
        void cleartext() {
            var fetch = new HuggingFaceSourceRepository().readFile(datasetUri(), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount()));
            assertUnavailableLogged();
        }

        @Test
        @DisplayName("a repository with user information is refused with nothing sent")
        void userInfo() {
            var uri = URI.create(base().replace("http://", "http://user:pa55word@") + REPOSITORY_PATH);

            var fetch = repository.readFile(uri, COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                    () -> assertFalse(fetch.detail().contains("pa55word"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount()));
            assertUnavailableLogged();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"example/gamma", "mailto:user@example.org", "https:/example/gamma", "file:///tmp/gamma",
                "//example.org/gamma"})
        @DisplayName("a repository URL without scheme or host is UNREACHABLE, logs the WARN and never throws")
        void withoutSchemeOrHost(String uri) {
            var fetch = repository.readFile(URI.create(uri), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount()));
            assertUnavailableLogged();
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "<a URL without scheme or host>");
        }

        @Test
        @DisplayName("a repository that is no Hugging Face dataset URL is refused with nothing sent")
        void notADataset() {
            var fetch = repository.readFile(URI.create("https://github.com/example/gamma"), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                    () -> assertEquals(0, server.getRequestCount()));
            assertUnavailableLogged();
        }
    }

    /** Runs under a default locale with non-Latin digits, so a status a detail names must be ASCII digits. */
    @Nested
    @DisplayName("under a default locale with non-Latin digits")
    class NonLatin {

        private static final Locale PERSIAN = Locale.forLanguageTag("fa-IR");

        private Locale previous;

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
            on("GET", RAW, status(503));

            var fetch = repository.readFile(datasetUri(), COMMIT, FILE);

            assertAll(
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
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

        @BeforeEach
        void corpus() throws IOException {
            corpusRoot = importBase.resolve("corpus");
            var directory = corpusRoot.resolve(CorpusImport.SOURCES).resolve("gamma");
            Files.createDirectories(directory);
            Files.write(directory.resolve(CorpusImport.MANIFEST), SourceManifestCodec.write(new SourceManifest("gamma",
                    URI.create("https://huggingface.co" + REPOSITORY_PATH), COMMIT, "MIT", FetchKind.HUGGINGFACE,
                    List.of(new SourceFile(FILE, Sha256.hex(CSV), false)))));
            Files.write(directory.resolve(CorpusImport.OVERLAY), SourceOverlayCodec.write(new SourceOverlay("gamma",
                    FILE, "question_id", LocalDate.of(2026, 10, 14), List.of(new Row("q-1", "q-1", "screening/gamma",
                            "screening", "closed question", List.of("imported"), "{question}", "", null, null,
                            new Target(null, "the first holds", List.of(), List.of(), false),
                            new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                            new Review(ReviewStatus.DRAFT, null, null, null))))));
        }

        private CorpusImport corpusImport() {
            SourceRepository redirected = new SourceRepository() {
                @Override
                public SourceFetch prepare(Path cacheRoot, SourceManifest manifest) {
                    return repository.prepare(cacheRoot, manifest);
                }

                @Override
                public SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest) {
                    return repository.fetchCommit(datasetUri(), manifest.commit());
                }

                @Override
                public SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path) {
                    return repository.readFile(datasetUri(), manifest.commit(), path);
                }
            };
            return new CorpusImport(redirected, (uri, pointer) -> SourceFetch.pinMissing("no LFS object here"));
        }

        @Test
        @DisplayName("the delivered file yields the item, COMPLETE")
        void complete() {
            on("GET", REVISION, status(200));
            on("GET", RAW, bytes(CSV));

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict(), result::toString),
                    () -> assertEquals(1, result.items()));
        }

        @Test
        @DisplayName("delivered bytes that are not the pinned SHA-256 fail the run")
        void mismatched() {
            var other = "question_id,question\nq-1,Is water dry?\n".getBytes(UTF_8);
            on("GET", REVISION, status(200));
            on("GET", RAW, bytes(other));

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll(
                    () -> assertEquals(Verdict.FAILED, result.verdict(), result::toString),
                    () -> assertEquals(1, result.missed().size()),
                    () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason()),
                    () -> assertEquals(0, result.items()));
        }
    }
}
