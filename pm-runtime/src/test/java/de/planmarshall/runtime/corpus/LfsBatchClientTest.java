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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.role.corpus.CorpusImport;
import de.planmarshall.core.role.corpus.ImportResult.Reason;
import de.planmarshall.core.role.corpus.ImportResult.Verdict;
import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.GradingMethod;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.ReviewStatus;
import de.planmarshall.core.role.corpus.ImportedItem.RubricItem;
import de.planmarshall.core.role.corpus.ImportedItem.Target;
import de.planmarshall.core.role.corpus.LfsPointer;
import de.planmarshall.core.role.corpus.Sha256;
import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.core.role.corpus.SourceFetch.Outcome;
import de.planmarshall.core.role.corpus.SourceManifest;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceManifestCodec;
import de.planmarshall.core.role.corpus.SourceOverlay;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;
import de.planmarshall.core.role.corpus.SourceOverlayCodec;
import de.planmarshall.core.role.corpus.SourceRepository;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The Git LFS object download (PM-WORK-6, PM-EXEC-5) against a mock web server with its own dispatcher: the batch
 * request and the download of its action, the outcomes of an endpoint that lacks the object or does not answer, and
 * delivered bytes that are not the pointer's object, which the import's hash check refuses so the run fails.
 */
@EnableMockWebServer
@EnableTestLogger
@DisplayName("LfsBatchClient")
class LfsBatchClientTest {

    private static final byte[] OBJECT = """
            {"diff":"rename the field"}
            {"diff":"drop the null check"}
            """.getBytes(UTF_8);
    private static final LfsPointer POINTER = new LfsPointer(Sha256.hex(OBJECT), OBJECT.length);
    private static final String REPOSITORY_PATH = "/example/beta";
    private static final String BATCH = REPOSITORY_PATH + ".git" + LfsBatchClient.BATCH_PATH;
    private static final String OBJECT_PATH = "/objects/" + POINTER.oid();
    private static final String ACTION_SECRET = "RemoteAuth action-s3cret";
    private static final MockResponse NOT_FOUND = new MockResponse.Builder().code(404).build();
    /** Stands for the batch answer naming the download, which carries the port of the mock web server. */
    private static final String ANSWER = "ANSWER";

    /** Canned responses by {@code METHOD target}, each answering one request; anything else is answered 404. */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    private MockWebServer server;
    private LfsBatchClient client;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Deque<MockResponse> queue = responses.get(request.getMethod() + " " + request.getTarget());
                MockResponse next = queue == null ? null : queue.poll();
                return next == null ? NOT_FOUND : next;
            }
        });
        client = new LfsBatchClient(true, 2, 1);
    }

    private void on(String method, String target, MockResponse response) {
        responses.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static MockResponse lfsJson(int status, String body) {
        return new MockResponse.Builder().code(status).addHeader("Content-Type", LfsBatchClient.MEDIA_TYPE)
                .body(body).build();
    }

    private static MockResponse bytes(byte[] body) {
        return new MockResponse.Builder().code(200).body(new String(body, UTF_8)).build();
    }

    /** The batch answer naming the download of the object at the mock web server, with an action header. */
    private String batchAnswer() {
        return """
                {"transfer":"basic","objects":[{"oid":"%s","size":%d,"actions":{"download":{"href":"%s",\
                "header":{"Authorization":"%s"}}}}]}"""
                .formatted(POINTER.oid(), POINTER.size(), base() + OBJECT_PATH, ACTION_SECRET);
    }

    private void serves(byte[] object) {
        on("POST", BATCH, lfsJson(200, batchAnswer()));
        on("GET", OBJECT_PATH, bytes(object));
    }

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    private URI repository() {
        return URI.create(base() + REPOSITORY_PATH);
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "a request reached the mock web server");
        return request;
    }

    private static void assertUnavailableLogged() {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.CORPUS_LFS_OBJECT_UNAVAILABLE.resolveIdentifierString());
    }

    @Nested
    @DisplayName("a delivered object")
    class Delivered {

        @Test
        @DisplayName("is fetched through the batch request and the download of its action")
        void downloads() throws Exception {
            serves(OBJECT);

            var fetch = client.object(repository(), POINTER);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(OBJECT, fetch.content()),
                    () -> assertEquals(2, server.getRequestCount()));
            var batch = nextRequest();
            var download = nextRequest();
            assertNotNull(batch.getBody(), "the batch request carries a body");
            var batchBody = batch.getBody().utf8();
            assertAll(
                    () -> assertEquals("POST", batch.getMethod()),
                    () -> assertEquals(BATCH, batch.getTarget()),
                    () -> assertEquals(LfsBatchClient.MEDIA_TYPE, batch.getHeaders().get("Accept")),
                    () -> assertTrue(batch.getHeaders().get("Content-Type").startsWith(LfsBatchClient.MEDIA_TYPE),
                            batch.getHeaders()::toString),
                    () -> assertTrue(batchBody.contains("\"operation\":\"download\""), batchBody),
                    () -> assertTrue(batchBody.contains("\"oid\":\"" + POINTER.oid() + "\""), batchBody),
                    () -> assertEquals("GET", download.getMethod()),
                    () -> assertEquals(OBJECT_PATH, download.getTarget()),
                    () -> assertEquals(ACTION_SECRET, download.getHeaders().get("Authorization")));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, LfsBatchClient.class);
        }

        @Test
        @DisplayName("hands over bytes that differ from the pointer's SHA-256 for the import's hash check")
        void mismatchingBytes() {
            var other = OBJECT.clone();
            other[2] = (byte) 'X';
            serves(other);

            var fetch = client.object(repository(), POINTER);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome()),
                    () -> assertArrayEquals(other, fetch.content()),
                    () -> assertTrue(fetch.detail().contains("not the object " + POINTER.oid()), fetch::detail),
                    () -> assertFalse(Sha256.matches(fetch.content(), POINTER.oid())));
        }

        @Test
        @DisplayName("stops reading one byte beyond the pointer's size")
        void boundedBySize() {
            var longer = (new String(OBJECT, UTF_8) + "x".repeat(4096)).getBytes(UTF_8);
            serves(longer);

            var fetch = client.object(repository(), POINTER);

            assertAll(
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome()),
                    () -> assertEquals(POINTER.size() + 1, fetch.content().length),
                    () -> assertTrue(fetch.detail().contains("served more bytes"), fetch::detail));
        }
    }

    static Stream<Arguments> unavailable() {
        var objectError = "{\"objects\":[{\"oid\":\"%s\",\"size\":%d,\"error\":{\"code\":404,\"message\":\"gone\"}}]}"
                .formatted(POINTER.oid(), POINTER.size());
        var noAction = "{\"objects\":[{\"oid\":\"%s\",\"size\":%d}]}".formatted(POINTER.oid(), POINTER.size());
        return Stream.of(
                Arguments.of("a repository without LFS endpoint (404)", 404, "", 0, Outcome.PIN_MISSING, 1),
                Arguments.of("an endpoint that answers 503", 503, "{}", 0, Outcome.UNREACHABLE, 1),
                Arguments.of("an object error in the batch answer", 200, objectError, 0, Outcome.PIN_MISSING, 1),
                Arguments.of("a batch answer naming another object", 200, noAction.replace(POINTER.oid(),
                        "f".repeat(64)), 0, Outcome.PIN_MISSING, 1),
                Arguments.of("a batch answer that is no JSON", 200, "<html>", 0, Outcome.UNREACHABLE, 1),
                Arguments.of("a batch answer without download action", 200, noAction, 0, Outcome.PIN_MISSING, 1),
                Arguments.of("a download that answers 404", 200, ANSWER, 404, Outcome.PIN_MISSING, 2),
                Arguments.of("a download that answers 500", 200, ANSWER, 500, Outcome.UNREACHABLE, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailable")
    @DisplayName("an object the endpoint does not deliver is not fetched, and the WARN is logged")
    void notDelivered(String name, int batchStatus, String batchBody, int downloadStatus, Outcome expected,
            int requests) {
        on("POST", BATCH, lfsJson(batchStatus, ANSWER.equals(batchBody) ? batchAnswer() : batchBody));
        if (downloadStatus != 0) {
            on("GET", OBJECT_PATH, new MockResponse.Builder().code(downloadStatus).build());
        }

        var fetch = client.object(repository(), POINTER);

        assertAll(
                () -> assertEquals(expected, fetch.outcome(), fetch::detail),
                () -> assertEquals(0, fetch.content().length),
                () -> assertEquals(requests, server.getRequestCount()),
                () -> assertFalse(fetch.detail().contains("action-s3cret"), fetch::detail));
        assertUnavailableLogged();
    }

    static Stream<Arguments> refusedHeaders() {
        return Stream.of(
                Arguments.of("a value with a line feed", "Authorization", "RemoteAuth tok\\nen-s3cret",
                        "the header Authorization of the download action is invalid"),
                Arguments.of("a value with a carriage return and line feed", "Authorization",
                        "RemoteAuth tok\\r\\nX-Injected: en-s3cret",
                        "the header Authorization of the download action is invalid"),
                Arguments.of("a name with a line feed", "X-Auth\\nX-Other", "RemoteAuth en-s3cret",
                        "a header of the download action has an invalid name"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedHeaders")
    @DisplayName("a refused download action header is UNREACHABLE, and no detail or log line holds its value")
    void refusedHeader(String name, String headerName, String headerValue, String expectedDetail) {
        var answer = """
                {"objects":[{"oid":"%s","size":%d,"actions":{"download":{"href":"%s","header":{"%s":"%s"}}}}]}"""
                .formatted(POINTER.oid(), POINTER.size(), base() + OBJECT_PATH, headerName, headerValue);
        on("POST", BATCH, lfsJson(200, answer));
        on("GET", OBJECT_PATH, bytes(OBJECT));

        var fetch = client.object(repository(), POINTER);

        assertAll(
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                () -> assertEquals(1, server.getRequestCount(), "only the batch request is sent"),
                () -> assertTrue(fetch.detail().contains(expectedDetail), fetch::detail),
                () -> assertFalse(fetch.detail().contains("s3cret"), fetch::detail),
                () -> assertFalse(fetch.detail().contains("RemoteAuth"), fetch::detail));
        assertUnavailableLogged();
        for (var level : TestLogLevel.values()) {
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(level, "s3cret").isEmpty(),
                    "no " + level + " line holds the header value");
        }
    }

    @Test
    @DisplayName("names a header by its name only when that is an HTTP token")
    void headerNameText() {
        assertAll(
                () -> assertEquals("the header X-Auth of the download action is invalid",
                        LfsBatchClient.headerName("X-Auth")),
                () -> assertEquals("a header of the download action has an invalid name",
                        LfsBatchClient.headerName("X Auth: value")),
                () -> assertEquals("a header of the download action has an invalid name",
                        LfsBatchClient.headerName("")));
    }

    @Test
    @DisplayName("a stalled response is UNREACHABLE after the read timeout, and the WARN is logged")
    void stalled() {
        on("POST", BATCH, new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS)
                .body(batchAnswer()).build());

        var fetch = client.object(repository(), POINTER);

        assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
        assertUnavailableLogged();
    }

    @Test
    @DisplayName("refuses a repository that is not https without sending anything")
    void refusesCleartext() {
        var fetch = new LfsBatchClient().object(repository(), POINTER);

        assertAll(
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount()));
        assertUnavailableLogged();
    }

    @Test
    @DisplayName("refuses a repository URL with user information without sending anything")
    void refusesUserInfo() {
        var fetch = client.object(URI.create("https://user:pw@example.org/r"), POINTER);

        assertAll(
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                () -> assertFalse(fetch.detail().contains("pw"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"example/beta", "mailto:user@example.org", "https:/example/beta", "file:///tmp/beta",
            "//example.org/beta"})
    @DisplayName("refuses a repository URL without scheme or host as UNREACHABLE, logs the WARN and never throws")
    void refusesWithoutSchemeOrHost(String uri) {
        var fetch = client.object(URI.create(uri), POINTER);

        assertAll(
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome()),
                () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount()));
        assertUnavailableLogged();
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "<a URL without scheme or host>");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("batchUris")
    @DisplayName("derives the batch API from the .git URL of the repository")
    void batchUri(String repository, String expected) {
        assertEquals(URI.create(expected), LfsBatchClient.batchUri(URI.create(repository)));
    }

    static Stream<Arguments> batchUris() {
        return Stream.of(
                Arguments.of("https://github.com/o/r", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://github.com/o/r.git", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://github.com/o/r/", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://h:8443/o/r", "https://h:8443/o/r.git/info/lfs/objects/batch"));
    }

    /**
     * The client within the import: bytes the endpoint delivers that are not the pointer's object fail the run with
     * the reason {@link Reason#HASH_MISMATCH}. The manifest names an {@code https} repository; the source the import
     * hands it to is redirected to the mock web server.
     */
    @Nested
    @DisplayName("within the corpus import")
    class WithinImport {

        @TempDir
        Path base;

        private Path corpusRoot;

        @BeforeEach
        void corpus() throws IOException {
            corpusRoot = base.resolve("corpus");
            var directory = corpusRoot.resolve(CorpusImport.SOURCES).resolve("beta");
            Files.createDirectories(directory);
            Files.write(directory.resolve(CorpusImport.MANIFEST), SourceManifestCodec.write(new SourceManifest("beta",
                    URI.create("https://github.com/example/beta"), "2".repeat(40), "MIT",
                    List.of(new SourceFile("data/rows.jsonl", POINTER.oid(), true)))));
            Files.write(directory.resolve(CorpusImport.OVERLAY), SourceOverlayCodec.write(new SourceOverlay("beta",
                    "data/rows.jsonl", null, LocalDate.of(2026, 10, 14), List.of(new Row("2", "d-2", "review/beta",
                            "review", "open question 2", List.of(), "Review: {diff}", "", null,
                            new Target(null, "one defect", List.of(), List.of(new RubricItem("d1", "the null check")),
                                    false),
                            new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                            new Review(ReviewStatus.DRAFT, null, null, null))))));
        }

        private CorpusImport corpusImport() {
            SourceRepository pointerOnly = new SourceRepository() {
                @Override
                public SourceFetch prepare(Path cacheRoot, SourceManifest manifest) {
                    return SourceFetch.done("prepared");
                }

                @Override
                public SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest) {
                    return SourceFetch.done("fetched");
                }

                @Override
                public SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path) {
                    return SourceFetch.fetched(POINTER.toBytes(), path);
                }
            };
            return new CorpusImport(pointerOnly, (repository, pointer) -> client.object(repository(), pointer));
        }

        @Test
        @DisplayName("the delivered object yields the item")
        void complete() {
            serves(OBJECT);

            var result = corpusImport().run(corpusRoot, base.resolve("cache"));

            assertAll(
                    () -> assertEquals(Verdict.COMPLETE, result.verdict(), result::toString),
                    () -> assertEquals(1, result.items()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.runtime.corpus.LfsBatchClientTest#refusedObjects")
        @DisplayName("delivered bytes that are not the object fail the run")
        void refused(String name, byte[] delivered) {
            serves(delivered);

            var result = corpusImport().run(corpusRoot, base.resolve("cache"));

            assertAll(
                    () -> assertEquals(Verdict.FAILED, result.verdict(), result::toString),
                    () -> assertEquals(1, result.missed().size()),
                    () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason()),
                    () -> assertEquals(0, result.items()));
        }
    }

    static Stream<Arguments> refusedObjects() {
        var changed = OBJECT.clone();
        changed[2] = (byte) 'X';
        return Stream.of(
                Arguments.of("bytes with another SHA-256", changed),
                Arguments.of("fewer bytes", new String(OBJECT, UTF_8).substring(1).getBytes(UTF_8)),
                Arguments.of("more bytes", (new String(OBJECT, UTF_8) + "tail").getBytes(UTF_8)));
    }
}
