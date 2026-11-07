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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
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
import java.util.stream.Stream;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import de.planmarshall.core.role.corpus.SourceManifest.FetchKind;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceManifestCodec;
import de.planmarshall.core.role.corpus.SourceOverlay;
import de.planmarshall.core.role.corpus.SourceOverlay.Row;
import de.planmarshall.core.role.corpus.SourceOverlayCodec;
import de.planmarshall.core.role.corpus.SourceRepository;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The Git LFS object download (PM-WORK-6, PM-EXEC-5) against a mock web server: the batch request and the download
 * of its action, the outcomes of an endpoint that lacks the object or does not answer, and delivered bytes that are
 * not the pointer's object, which the import's hash check refuses so the run fails.
 * <p>
 * Every request is answered by one response primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target, so a request for a wrong path gets no answer; each
 * nested group returns that one element from its own {@code getModuleDispatcher()}. Contract literals, kept by
 * design: the Git LFS batch API ({@code .git/info/lfs/objects/batch}, {@code application/vnd.git-lfs+json}, the
 * field names, {@code basic}, {@code download}, {@code sha256}); the {@code /objects/{oid}} download path of the
 * test; the status codes; the header-injection values and the refused URL shapes; the user information of a refused
 * URL; the {@code RemoteAuth} scheme and the {@code s3cret} marker every secret of the test carries, so no detail and
 * no log line may hold it; the wording of the details; the Persian locale; the fixed fields of the overlay row. The
 * object and its pointer, the repository and the commit are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@EnableTestLogger
@ModuleDispatcher
@DisplayName("LfsBatchClient")
class LfsBatchClientTest {

    /** The marker every secret of the test carries (contract: no detail and no log line may hold it). */
    private static final String SECRET_MARKER = "s3cret";

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private String base;
    private String repositoryPath;
    private byte[] object;
    private LfsPointer pointer;
    private String actionSecret;
    private LfsBatchClient client;

    /** Answers a GET or POST with the next response primed for its method and target; anything else gets none. */
    ModuleDispatcherElement dispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/";
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                return next(request);
            }

            @Override
            public Optional<MockResponse> handlePost(RecordedRequest request) {
                return next(request);
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET, HttpMethodMapper.POST);
            }
        };
    }

    private Optional<MockResponse> next(RecordedRequest request) {
        Deque<MockResponse> queue = routes.get(request.getMethod() + " " + request.getTarget());
        return Optional.ofNullable(queue == null ? null : queue.poll());
    }

    ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher();
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        URI root = uriBuilder.build();
        base = root.getScheme() + "://" + root.getHost() + ":" + root.getPort();
        repositoryPath = "/" + word() + "/" + word();
        object = ("{\"diff\":\"" + word() + " " + word() + "\"}\n{\"diff\":\"" + word() + " " + word() + "\"}\n")
                .getBytes(UTF_8);
        pointer = new LfsPointer(Sha256.hex(object), object.length);
        actionSecret = "RemoteAuth " + word() + "-" + SECRET_MARKER;
        client = new LfsBatchClient(true, 2, 1);
    }

    /** @return a generated lower-case word, safe in a URL path and a JSON string */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    private void on(String method, String target, MockResponse response) {
        routes.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private String batch() {
        return repositoryPath + ".git" + LfsBatchClient.BATCH_PATH;
    }

    private String objectPath() {
        return "/objects/" + pointer.oid();
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
        return String.format(Locale.ROOT, """
                {"transfer":"basic","objects":[{"oid":"%s","size":%d,"actions":{"download":{"href":"%s",\
                "header":{"Authorization":"%s"}}}}]}""", pointer.oid(), pointer.size(), base + objectPath(),
                actionSecret);
    }

    private void serves(byte[] delivered) {
        on("POST", batch(), lfsJson(200, batchAnswer()));
        on("GET", objectPath(), bytes(delivered));
    }

    private URI repository() {
        return URI.create(base + repositoryPath);
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

    private static void assertNoLogLineHolds(String value) {
        for (var level : TestLogLevel.values()) {
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(level, value).isEmpty(),
                    "no " + level + " line holds the value");
        }
    }

    @Nested
    @DisplayName("under a default locale with non-Latin digits")
    class NonLatinDigits {

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
        @DisplayName("the batch request writes the size in ASCII digits, so the body stays JSON")
        void batchBody() throws Exception {
            serves(object);

            var fetch = client.object(repository(), pointer);

            assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail);
            var request = nextRequest();
            assertNotNull(request.getBody(), "the batch request carries a body");
            assertEquals("{\"operation\":\"download\",\"transfers\":[\"basic\"],\"objects\":[{\"oid\":\"" + pointer.oid()
                    + "\",\"size\":" + Long.toString(pointer.size()) + "}],\"hash_algo\":\"sha256\"}",
                    request.getBody().utf8(), "the batch body is JSON with ASCII digits");
        }

        @Test
        @DisplayName("a status in a detail is written in ASCII digits")
        void statusDetail() {
            on("POST", batch(), lfsJson(503, "{}"));

            var fetch = client.object(repository(), pointer);

            assertAll("the ASCII status",
                    () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                    () -> assertTrue(fetch.detail().endsWith("answered 503"), fetch::detail));
        }
    }

    @Nested
    @DisplayName("a delivered object")
    class Delivered {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("is fetched through the batch request and the download of its action")
        void downloads() throws Exception {
            serves(object);

            var fetch = client.object(repository(), pointer);

            assertAll("the fetched object",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(object, fetch.content(), "the object bytes are delivered"),
                    () -> assertEquals(2, server.getRequestCount(), "a batch and a download are sent"));
            var batchRequest = nextRequest();
            var download = nextRequest();
            assertNotNull(batchRequest.getBody(), "the batch request carries a body");
            var batchBody = batchRequest.getBody().utf8();
            assertAll("the batch request and the download",
                    () -> assertEquals("POST", batchRequest.getMethod(), "the batch is a POST"),
                    () -> assertEquals(batch(), batchRequest.getTarget(), "the batch API of the repository"),
                    () -> assertEquals(LfsBatchClient.MEDIA_TYPE, batchRequest.getHeaders().get("Accept"),
                            "the batch accepts the LFS media type"),
                    () -> assertTrue(batchRequest.getHeaders().get("Content-Type")
                            .startsWith(LfsBatchClient.MEDIA_TYPE), batchRequest.getHeaders()::toString),
                    () -> assertTrue(batchBody.contains("\"operation\":\"download\""), batchBody),
                    () -> assertTrue(batchBody.contains("\"oid\":\"" + pointer.oid() + "\""), batchBody),
                    () -> assertEquals("GET", download.getMethod(), "the download is a GET"),
                    () -> assertEquals(objectPath(), download.getTarget(), "the action's href is downloaded"),
                    () -> assertEquals(actionSecret, download.getHeaders().get("Authorization"),
                            "the action's header is sent with the download"));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, LfsBatchClient.class);
        }

        @Test
        @DisplayName("hands over bytes that differ from the pointer's SHA-256 for the import's hash check")
        void mismatchingBytes() {
            var other = Tamper.CHANGED.apply(object);
            serves(other);

            var fetch = client.object(repository(), pointer);

            assertAll("the mismatching bytes",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertArrayEquals(other, fetch.content(), "the delivered bytes are handed over"),
                    () -> assertTrue(fetch.detail().contains("not the object " + pointer.oid()), fetch::detail),
                    () -> assertFalse(Sha256.matches(fetch.content(), pointer.oid()),
                            "the bytes do not match the pointer"));
        }

        @Test
        @DisplayName("stops reading one byte beyond the pointer's size")
        void boundedBySize() {
            var longer = (new String(object, UTF_8) + "x".repeat(4096)).getBytes(UTF_8);
            serves(longer);

            var fetch = client.object(repository(), pointer);

            assertAll("the bounded read",
                    () -> assertEquals(Outcome.FETCHED, fetch.outcome(), fetch::detail),
                    () -> assertEquals(pointer.size() + 1, fetch.content().length,
                            "reading stops one byte beyond the size"),
                    () -> assertTrue(fetch.detail().contains("served more bytes"), fetch::detail));
        }
    }

    /** The batch answers of an object the endpoint does not deliver. */
    enum BatchBody {
        EMPTY, BRACES, OBJECT_ERROR, OTHER_OBJECT, NO_JSON, NO_ACTION, ANSWER
    }

    private String batchBody(BatchBody kind) {
        var noAction = "{\"objects\":[{\"oid\":\"%s\",\"size\":%d}]}".formatted(pointer.oid(), pointer.size());
        return switch (kind) {
            case EMPTY -> "";
            case BRACES -> "{}";
            case OBJECT_ERROR -> "{\"objects\":[{\"oid\":\"%s\",\"size\":%d,\"error\":{\"code\":404,\"message\":\"gone\"}}]}"
                    .formatted(pointer.oid(), pointer.size());
            case OTHER_OBJECT -> noAction.replace(pointer.oid(), "f".repeat(64));
            case NO_JSON -> "<html>";
            case NO_ACTION -> noAction;
            case ANSWER -> batchAnswer();
        };
    }

    static Stream<Arguments> unavailable() {
        return Stream.of(
                Arguments.of("a repository without LFS endpoint (404)", 404, BatchBody.EMPTY, 0, Outcome.PIN_MISSING, 1),
                Arguments.of("an endpoint that answers 503", 503, BatchBody.BRACES, 0, Outcome.UNREACHABLE, 1),
                Arguments.of("an object error in the batch answer", 200, BatchBody.OBJECT_ERROR, 0, Outcome.PIN_MISSING,
                        1),
                Arguments.of("a batch answer naming another object", 200, BatchBody.OTHER_OBJECT, 0,
                        Outcome.PIN_MISSING, 1),
                Arguments.of("a batch answer that is no JSON", 200, BatchBody.NO_JSON, 0, Outcome.UNREACHABLE, 1),
                Arguments.of("a batch answer without download action", 200, BatchBody.NO_ACTION, 0,
                        Outcome.PIN_MISSING, 1),
                Arguments.of("a download that answers 404", 200, BatchBody.ANSWER, 404, Outcome.PIN_MISSING, 2),
                Arguments.of("a download that answers 500", 200, BatchBody.ANSWER, 500, Outcome.UNREACHABLE, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailable")
    @DisplayName("an object the endpoint does not deliver is not fetched, and the WARN is logged")
    void notDelivered(String name, int batchStatus, BatchBody body, int downloadStatus, Outcome expected,
            int requests) {
        on("POST", batch(), lfsJson(batchStatus, batchBody(body)));
        if (downloadStatus != 0) {
            on("GET", objectPath(), new MockResponse.Builder().code(downloadStatus).build());
        }

        var fetch = client.object(repository(), pointer);

        assertAll("the undelivered object",
                () -> assertEquals(expected, fetch.outcome(), fetch::detail),
                () -> assertEquals(0, fetch.content().length, "no content is delivered"),
                () -> assertEquals(requests, server.getRequestCount(), "the expected requests are sent"),
                () -> assertFalse(fetch.detail().contains(SECRET_MARKER), fetch::detail));
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
                .formatted(pointer.oid(), pointer.size(), base + objectPath(), headerName, headerValue);
        on("POST", batch(), lfsJson(200, answer));
        on("GET", objectPath(), bytes(object));

        var fetch = client.object(repository(), pointer);

        assertAll("the refused header",
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                () -> assertEquals(1, server.getRequestCount(), "only the batch request is sent"),
                () -> assertTrue(fetch.detail().contains(expectedDetail), fetch::detail),
                () -> assertFalse(fetch.detail().contains(SECRET_MARKER), fetch::detail),
                () -> assertFalse(fetch.detail().contains("RemoteAuth"), fetch::detail));
        assertUnavailableLogged();
        assertNoLogLineHolds(SECRET_MARKER);
    }

    /**
     * A download action the client refuses after the batch answer: nothing is downloaded, the action's
     * {@code Authorization} header is sent nowhere, and the WARN is logged without its value. The client under test
     * admits plain {@code http} to a loopback address only, so that the batch request reaches the mock web server; an
     * {@code http} download to any other host is refused by the very rule that refuses every {@code http} URL in the
     * production client, and user information is refused whatever the scheme and host.
     */
    @Nested
    @DisplayName("a refused download action")
    class RefusedDownload {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        private void answers(String href, long size) {
            on("POST", batch(), lfsJson(200, String.format(Locale.ROOT, """
                    {"objects":[{"oid":"%s","size":%d,"actions":{"download":{"href":"%s",\
                    "header":{"Authorization":"%s"}}}}]}""", pointer.oid(), size, href, actionSecret)));
            on("GET", objectPath(), bytes(object));
        }

        private void assertNothingDownloaded(SourceFetch fetch, Outcome expected, String detail)
                throws InterruptedException {
            assertAll("nothing downloaded",
                    () -> assertEquals(expected, fetch.outcome(), fetch::detail),
                    () -> assertEquals(0, fetch.content().length, "no content is delivered"),
                    () -> assertTrue(fetch.detail().contains(detail), fetch::detail),
                    () -> assertFalse(fetch.detail().contains(SECRET_MARKER), fetch::detail),
                    () -> assertEquals(1, server.getRequestCount(), "only the batch request is sent"));
            var batchRequest = nextRequest();
            assertAll("the batch request",
                    () -> assertEquals("POST", batchRequest.getMethod(), "the batch is a POST"),
                    () -> assertEquals(batch(), batchRequest.getTarget(), "the batch API of the repository"),
                    () -> assertNull(batchRequest.getHeaders().get("Authorization"),
                            "the action's header is never sent"));
            assertUnavailableLogged();
            assertNoLogLineHolds(SECRET_MARKER);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"http://example.org", "http://example.org:80", "HTTP://lfs.example.org"})
        @DisplayName("a download URL that is http to a host other than loopback is UNREACHABLE")
        void cleartextDownload(String origin) throws Exception {
            answers(origin + objectPath(), pointer.size());

            var fetch = client.object(repository(), pointer);

            assertNothingDownloaded(fetch, Outcome.UNREACHABLE, "refused: the download URL of " + pointer.oid()
                    + " is not https");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"http", "https"})
        @DisplayName("a download URL with user information is UNREACHABLE, even to the loopback test server")
        void userInfoDownload(String scheme) throws Exception {
            var href = base.replace("http://", scheme + "://user:pa55word@") + objectPath();
            answers(href, pointer.size());

            var fetch = client.object(repository(), pointer);

            assertNothingDownloaded(fetch, Outcome.UNREACHABLE, "refused: the download URL of " + pointer.oid()
                    + " is not https");
            assertFalse(fetch.detail().contains("pa55word"), fetch::detail);
            assertNoLogLineHolds("pa55word");
        }

        @Test
        @DisplayName("a pointer larger than a byte array holds is UNREACHABLE before the download")
        void tooLarge() throws Exception {
            var huge = new LfsPointer(pointer.oid(), Integer.MAX_VALUE);
            answers(base + objectPath(), huge.size());

            var fetch = client.object(repository(), huge);

            assertNothingDownloaded(fetch, Outcome.UNREACHABLE,
                    "the object of " + Integer.MAX_VALUE + " bytes is too large to hold");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"https://lfs.example.org/objects/a b", "https://lfs.example.org/objects/%zz",
                "https://[lfs.example.org/objects"})
        @DisplayName("a download href that is no URL is UNREACHABLE")
        void noUrl(String href) throws Exception {
            answers(href, pointer.size());

            var fetch = client.object(repository(), pointer);

            assertNothingDownloaded(fetch, Outcome.UNREACHABLE, "the download action names no URL");
        }
    }

    @Test
    @DisplayName("names a header by its name only when that is an HTTP token")
    void headerNameText() {
        assertAll("the header naming",
                () -> assertEquals("the header X-Auth of the download action is invalid",
                        LfsBatchClient.headerName("X-Auth"), "a token name is named"),
                () -> assertEquals("a header of the download action has an invalid name",
                        LfsBatchClient.headerName("X Auth: value"), "a name with a space is not named"),
                () -> assertEquals("a header of the download action has an invalid name",
                        LfsBatchClient.headerName(""), "an empty name is not named"));
    }

    @Test
    @DisplayName("a stalled response is UNREACHABLE after the read timeout, and the WARN is logged")
    void stalled() {
        on("POST", batch(), new MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS)
                .body(batchAnswer()).build());

        var fetch = client.object(repository(), pointer);

        assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail);
        assertUnavailableLogged();
    }

    @Test
    @DisplayName("refuses a repository that is not https without sending anything")
    void refusesCleartext() {
        var fetch = new LfsBatchClient().object(repository(), pointer);

        assertAll("the refused cleartext repository",
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
        assertUnavailableLogged();
    }

    @Test
    @DisplayName("refuses a repository URL with user information without sending anything")
    void refusesUserInfo() {
        var fetch = client.object(URI.create("https://user:pw@example.org/r"), pointer);

        assertAll("the refused user information",
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                () -> assertFalse(fetch.detail().contains("pw"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"example/beta", "mailto:user@example.org", "https:/example/beta", "file:///tmp/beta",
            "//example.org/beta"})
    @DisplayName("refuses a repository URL without scheme or host as UNREACHABLE, logs the WARN and never throws")
    void refusesWithoutSchemeOrHost(String uri) {
        var fetch = client.object(URI.create(uri), pointer);

        assertAll("the refused URL",
                () -> assertEquals(Outcome.UNREACHABLE, fetch.outcome(), fetch::detail),
                () -> assertTrue(fetch.detail().startsWith("refused"), fetch::detail),
                () -> assertEquals(0, server.getRequestCount(), "nothing is sent"));
        assertUnavailableLogged();
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "<a URL without scheme or host>");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("batchUris")
    @DisplayName("derives the batch API from the .git URL of the repository")
    void batchUri(String repository, String expected) {
        assertEquals(URI.create(expected), LfsBatchClient.batchUri(URI.create(repository)),
                "the batch API follows the .git URL");
    }

    static Stream<Arguments> batchUris() {
        return Stream.of(
                Arguments.of("https://github.com/o/r", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://github.com/o/r.git", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://github.com/o/r/", "https://github.com/o/r.git/info/lfs/objects/batch"),
                Arguments.of("https://h:8443/o/r", "https://h:8443/o/r.git/info/lfs/objects/batch"));
    }

    /** The ways delivered bytes miss the pointer's object. */
    enum Tamper {
        CHANGED, FEWER, MORE;

        byte[] apply(byte[] original) {
            return switch (this) {
                case CHANGED -> {
                    var changed = original.clone();
                    changed[2] = changed[2] == (byte) 'X' ? (byte) 'Y' : (byte) 'X';
                    yield changed;
                }
                case FEWER -> new String(original, UTF_8).substring(1).getBytes(UTF_8);
                case MORE -> (new String(original, UTF_8) + "tail").getBytes(UTF_8);
            };
        }
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
        Path importBase;

        private Path corpusRoot;

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @BeforeEach
        void corpus() throws IOException {
            String name = word();
            String file = "data/" + word() + ".jsonl";
            corpusRoot = importBase.resolve("corpus");
            var directory = corpusRoot.resolve(CorpusImport.SOURCES).resolve(name);
            Files.createDirectories(directory);
            Files.write(directory.resolve(CorpusImport.MANIFEST), SourceManifestCodec.write(new SourceManifest(name,
                    URI.create("https://github.com" + repositoryPath), Generators.strings("0123456789abcdef", 40, 40)
                            .next(), "MIT", FetchKind.GIT, List.of(new SourceFile(file, pointer.oid(), true)))));
            Files.write(directory.resolve(CorpusImport.OVERLAY), SourceOverlayCodec.write(new SourceOverlay(name,
                    file, null, LocalDate.of(2026, 10, 14), List.of(new Row("2", "d-2", "review/" + name,
                            "review", "open question 2", List.of(), null, "Review: {diff}", "", null, null,
                            new Target(null, "one defect", List.of(), List.of(new RubricItem("d1", "the null check")),
                                    false),
                            new Grading(GradingMethod.RUBRIC_ITEMS, null, null),
                            new Review(ReviewStatus.DRAFT, null, null, null), null)))));
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
                    return SourceFetch.fetched(pointer.toBytes(), path);
                }
            };
            return new CorpusImport(pointerOnly, (uri, lfsPointer) -> client.object(repository(), lfsPointer));
        }

        @Test
        @DisplayName("the delivered object yields the item")
        void complete() {
            serves(object);

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll("the complete import",
                    () -> assertEquals(Verdict.COMPLETE, result.verdict(), result::toString),
                    () -> assertEquals(1, result.items(), "the one item is generated"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Tamper.class)
        @DisplayName("delivered bytes that are not the object fail the run")
        void refused(Tamper tamper) {
            serves(tamper.apply(object));

            var result = corpusImport().run(corpusRoot, importBase.resolve("cache"));

            assertAll("the refused import",
                    () -> assertEquals(Verdict.FAILED, result.verdict(), result::toString),
                    () -> assertEquals(1, result.missed().size(), "the one file is missed"),
                    () -> assertEquals(Reason.HASH_MISMATCH, result.missed().getFirst().reason(),
                            "the miss is a hash mismatch"),
                    () -> assertEquals(0, result.items(), "no item is generated"));
        }
    }
}
