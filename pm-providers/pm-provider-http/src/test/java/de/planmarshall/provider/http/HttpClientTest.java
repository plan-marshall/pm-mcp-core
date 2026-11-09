/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

import de.planmarshall.provider.http.Response.Outcome;
import de.cuioss.http.client.handler.RedirectPolicy.RedirectRefusal;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import de.cuioss.test.mockwebserver.mockresponse.MockResponseConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shared HTTP client against the mock web server.
 * <p>
 * Every request is answered by one canned response. The static ones are {@code @MockResponseConfig} on the test;
 * the ones that carry generated data are primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target (path and query), so a request with a wrong method, path
 * or query gets no answer. Each nested group returns that one element from its own {@code getModuleDispatcher()},
 * because the extension looks the method up on the nested test instance.
 * <p>
 * Contract literals, kept by design: the header names ({@code Authorization}, {@code Accept},
 * {@code Content-Type}, {@code ETag}, {@code If-None-Match}, {@code Location}, {@code Link}, {@code Host},
 * {@code Retry-After}, {@code x-ratelimit-remaining}, {@code x-ratelimit-reset}, {@code RateLimit-Reset}); the
 * {@code Bearer} scheme of RFC 6750; the GitHub media type of the default {@code Accept} header; the status codes;
 * the {@code Link} grammar of RFC 8288 with its {@code next}, {@code prev} and {@code last} relations; the
 * {@code CROSS_ORIGIN} reason cui-http names; the HTTP-date form of {@code Retry-After}; the bodies and headers of
 * the static configurations. Tokens, paths, bodies, ETags, the clock and the reset instants are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("HttpClient")
class HttpClientTest {

    /** The default header the client is built with (contract: the GitHub media type). */
    private static final String GITHUB_MEDIA_TYPE = "application/vnd.github+json";
    /** The authorization scheme of a token (contract: RFC 6750). */
    private static final String BEARER = "Bearer ";
    /** Redirects queued for the redirect loop, more hops than cui-http follows. */
    private static final int LOOP_REDIRECTS = 20;

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private String host;
    private Instant now;
    private String token;
    private HttpClient client;

    /**
     * Answers a GET or POST with the next response primed for its method and target; a request nothing was primed
     * for is left to the static configurations, and after them to the mock web server's refusal.
     */
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

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        host = uriBuilder.build().getHost();
        now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_900_000_000L).next());
        token = Generators.letterStrings(8, 24).next();
        client = new HttpClient(new Endpoint(base(), Set.of()), () -> Optional.of(token),
                Map.of("Accept", GITHUB_MEDIA_TYPE), Clock.fixed(now, ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        client.close();
    }

    private void on(String method, String target, MockResponse response) {
        routes.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static MockResponse response(int status, String body, Map<String, String> headers) {
        var builder = new MockResponse.Builder().code(status);
        headers.forEach(builder::addHeader);
        if (!body.isEmpty()) {
            builder.body(body);
        }
        return builder.build();
    }

    private static MockResponse json(int status, String body) {
        return response(status, body, Map.of("Content-Type", "application/json"));
    }

    /** @return a generated path segment, prefixed so two segments of one test never collide */
    private static String segment(String prefix) {
        return prefix + Generators.letterStrings(6, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated response body */
    private static String body() {
        return Generators.letterStrings(4, 40).next();
    }

    /** @return a generated strong entity tag, in the quoted form of RFC 9110 */
    private static String etag() {
        return "\"" + Generators.letterStrings(4, 16).next() + "\"";
    }

    /** @return a generated reset instant, in epoch seconds */
    private static long epochSeconds() {
        return Generators.longs(1_700_000_000L, 1_900_000_000L).next();
    }

    private URI base(String hostName) {
        return URI.create("http://" + hostName + ":" + server.getPort() + "/");
    }

    /** @return the base URI the injected {@link URIBuilder} names */
    private URI base() {
        return base(host);
    }

    /**
     * The second origin on the same server: {@code localhost} when the injected base names the bound address, the
     * literal of the bound address ({@code 127.0.0.1} or {@code [::1]}) when it names {@code localhost}.
     */
    private String otherHost() {
        if (!"localhost".equalsIgnoreCase(host)) {
            return "localhost";
        }
        InetAddress address = server.getSocketAddress().getAddress();
        return address instanceof Inet6Address ? "[::1]" : "127.0.0.1";
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "a request reached the mock web server");
        return request;
    }

    private static String header(RecordedRequest request, String name) {
        return request.getHeaders().get(name);
    }

    @Nested
    @DisplayName("authentication and requests")
    class Requests {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("sends the bearer token and default headers to the origin")
        void bearer() throws Exception {
            String path = segment("r");
            String content = body();
            String tag = etag();
            on("GET", "/" + path, response(200, content, Map.of("ETag", tag)));

            Response response = client.get(path);

            var request = nextRequest();
            assertAll("the authenticated read",
                    () -> assertEquals(Outcome.OK, response.outcome(), "the read outcome is OK"),
                    () -> assertEquals(content, response.body(), "the response body is returned"),
                    () -> assertEquals(tag, response.etag(), "the entity tag is returned"),
                    () -> assertEquals(BEARER + token, header(request, "Authorization"),
                            "the token is sent as a bearer token"),
                    () -> assertEquals(GITHUB_MEDIA_TYPE, header(request, "Accept"),
                            "the default Accept header is sent"));
        }

        @Test
        @DisplayName("keeps the headers of a header-aware read")
        void keepsHeaders() {
            String path = segment("h");
            String name = "x-" + segment("n");
            String value = body();
            on("GET", "/" + path, response(200, body(), Map.of(name, value)));

            ResponseWithHeaders observed = client.getWithHeaders("/" + path);

            assertAll("the observed headers",
                    () -> assertTrue(observed.isOk(), "the observed response is ok"),
                    () -> assertEquals(Optional.of(value), observed.header(name.toUpperCase(Locale.ROOT)),
                            "a header is read case-insensitively"),
                    () -> assertTrue(observed.header(segment("m")).isEmpty(), "a missing header is empty"));
        }

        @Test
        @DisplayName("posts a JSON body from memory")
        void postsJson() throws Exception {
            String path = segment("p");
            String sent = "{\"body\":\"" + body() + "\\n" + body() + "\"}";
            on("POST", "/" + path, json(201, "{}"));

            Response response = client.send("POST", "/" + path, sent, null);

            var request = nextRequest();
            assertNotNull(request.getBody(), "the request carries a body");
            assertAll("the posted body",
                    () -> assertTrue(response.isOk(), "the created response is ok"),
                    () -> assertEquals(sent, request.getBody().utf8(), "the JSON body is sent unchanged"),
                    () -> assertEquals("application/json", header(request, "Content-Type"),
                            "the body is sent as JSON"));
        }

        @Test
        @DisplayName("sends no Authorization header without a token")
        void noToken() throws Exception {
            String path = segment("o");
            try (var anonymous = new HttpClient(Endpoint.of(base()), TokenSource.NONE, Map.of())) {
                on("GET", "/" + path, json(200, "[]"));

                assertTrue(anonymous.get("/" + path).isOk(), "the anonymous read is ok");
                assertNull(header(nextRequest(), "Authorization"), "no Authorization header is sent");
                assertEquals(base(), anonymous.endpoint().baseUri(), "the endpoint keeps its base URI");
            }
        }

        @Test
        @DisplayName("refuses a target outside the origin without sending anything")
        void refusesOtherOrigin() {
            Response response = client.get(base(otherHost()) + segment("s"));

            assertEquals(Outcome.REFUSED, response.outcome(), "a foreign origin is refused");
            assertEquals(0, server.getRequestCount(), "nothing is sent to the foreign origin");
        }

        @Test
        @DisplayName("reports an error status and a transport failure")
        @MockResponseConfig(path = "/missing", method = HttpMethodMapper.GET, status = 404,
                stringContent = "{\"message\":\"Not Found\"}")
        void failures() throws Exception {
            Response notFound = client.get("/missing");
            Response unreachable;
            try (var closed = new HttpClient(Endpoint.of(closedOrigin()), () -> Optional.of(token), Map.of())) {
                unreachable = closed.get("/" + segment("a"));
            }

            assertAll("the failures",
                    () -> assertEquals(Outcome.HTTP_ERROR, notFound.outcome(), "a 404 is an HTTP error"),
                    () -> assertEquals(404, notFound.status(), "the error status is kept"),
                    () -> assertEquals(Outcome.TRANSPORT_FAILED, unreachable.outcome(),
                            "a closed port is a transport failure"),
                    () -> assertEquals(0, unreachable.status(), "a transport failure has no status"));
        }

        /** An origin on a loopback port that was bound and released, so nothing listens there. */
        private static URI closedOrigin() throws IOException {
            int port;
            try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
            return URI.create("http://127.0.0.1:" + port + "/");
        }
    }

    @Nested
    @DisplayName("redirects")
    class Redirects {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("drops the token on a redirect to another origin")
        void dropsTokenCrossOrigin() throws Exception {
            String other = otherHost();
            String from = segment("f");
            String to = segment("t");
            String content = body();
            try (var redirecting = new HttpClient(new Endpoint(base(), Set.of(other.toLowerCase(Locale.ROOT))),
                         () -> Optional.of(token), Map.of())) {
                on("GET", "/" + to, json(200, content));
                on("GET", "/" + from, response(302, "", Map.of("Location", base(other) + to)));

                Response response = redirecting.get("/" + from);

                var origin = nextRequest();
                var redirected = nextRequest();
                assertAll("the cross-origin redirect",
                        () -> assertEquals(Outcome.OK, response.outcome(), "the redirected read is OK"),
                        () -> assertEquals(content, response.body(), "the redirect target's body is read"),
                        () -> assertTrue(header(origin, "Host").startsWith(host + ":"),
                                "first request to the endpoint origin"),
                        () -> assertEquals(BEARER + token, header(origin, "Authorization"),
                                "the origin request carries the token"),
                        () -> assertTrue(header(redirected, "Host").startsWith(other + ":"),
                                "redirect followed to the other origin"),
                        () -> assertNull(header(redirected, "Authorization"), "token dropped across origins"));
            }
        }

        @Test
        @DisplayName("refuses a redirect to a host that is not allowlisted")
        void refusesRedirect() {
            String from = segment("f");
            on("GET", "/" + from, response(302, "", Map.of("Location", base(otherHost()) + segment("t"))));

            Response response = client.get("/" + from);

            assertAll("the refused redirect",
                    () -> assertEquals(Outcome.REFUSED, response.outcome(), "the redirect is refused"),
                    () -> assertTrue(response.detail().contains("CROSS_ORIGIN"), "the detail names CROSS_ORIGIN"),
                    () -> assertEquals(1, server.getRequestCount(), "nothing sent to the refused redirect target"));
        }

        @Test
        @DisplayName("guards the rule that every HTTP call goes through cui-http: a redirect loop is refused "
                + "with the reason cui-http names")
        void sendsThroughCuiHttp() {
            String loop = segment("l");
            // the loop sends the same path once per hop: one redirect for each, more than cui-http follows
            for (int hop = 0; hop < LOOP_REDIRECTS; hop++) {
                on("GET", "/" + loop, response(302, "", Map.of("Location", base() + loop)));
            }

            Response response = client.get("/" + loop);

            assertAll("the refused loop",
                    () -> assertEquals(Outcome.REFUSED, response.outcome(), "the redirect loop is refused"),
                    () -> assertEquals("redirect refused: " + RedirectRefusal.TOO_MANY_HOPS, response.detail(),
                            "the detail names the cui-http reason"));
        }
    }

    @Nested
    @DisplayName("pagination")
    class Pagination {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        /**
         * Primes {@code count} pages of {@code path}, each linking the next and the last page.
         *
         * @return the generated page bodies in page order
         */
        private List<String> pages(String path, int count) {
            var bodies = new ArrayList<String>();
            for (int i = 1; i <= count; i++) {
                var headers = i < count
                        ? Map.of("Link", "<" + base() + path + "?page=" + (i + 1) + ">; rel=\"next\", <"
                        + base() + path + "?page=" + count + ">; rel=\"last\"")
                        : Map.<String, String>of();
                String content = body();
                bodies.add(content);
                on("GET", "/" + path + "?page=" + i, response(200, content, headers));
            }
            return bodies;
        }

        @Test
        @DisplayName("follows every next link and reports completeness")
        void complete() throws Exception {
            String path = segment("i");
            List<String> bodies = pages(path, 3);

            PagedResult result = client.getAll(path + "?page=1", 10);

            assertAll("the complete read",
                    () -> assertTrue(result.complete(), "the paged read is complete"),
                    () -> assertEquals(bodies, result.pages(), "every page is read in order"),
                    () -> assertEquals(3, server.getRequestCount(), "one request per page"));
            for (int i = 0; i < 3; i++) {
                assertEquals(BEARER + token, header(nextRequest(), "Authorization"), "every page carries the token");
            }
        }

        @Test
        @DisplayName("is incomplete at the page cap")
        void pageCap() {
            String path = segment("i");
            pages(path, 3);

            PagedResult result = client.getAll(path + "?page=1", 2);

            assertAll("the capped read",
                    () -> assertFalse(result.complete(), "a capped read is incomplete"),
                    () -> assertEquals(2, result.pages().size(), "only the capped pages are read"));
        }

        @Test
        @DisplayName("is incomplete when a page fails or the next link leaves the origin")
        void incomplete() {
            String failing = segment("a");
            String leaving = segment("b");
            String first = body();
            on("GET", "/" + failing + "?page=1",
                    response(200, body(), Map.of("Link", "<" + base() + failing + "?page=2>; rel=next")));
            on("GET", "/" + leaving + "?page=1", response(200, first,
                    Map.of("Link", "<" + base(otherHost()) + leaving + "?page=2>; rel=\"next\"")));

            PagedResult failedPage = client.getAll(failing + "?page=1", 10);
            PagedResult leaves = client.getAll(leaving + "?page=1", 10);

            assertAll("the incomplete reads",
                    () -> assertFalse(failedPage.complete(), "a failed page is incomplete"),
                    () -> assertEquals(Outcome.HTTP_ERROR, failedPage.last().outcome(),
                            "the unanswered page is an HTTP error"),
                    () -> assertFalse(leaves.complete(), "a foreign next link is incomplete"),
                    () -> assertEquals(List.of(first), leaves.pages(), "only the first page is read"));
        }

        @Test
        @DisplayName("parses only the next relation")
        void parsesLink() {
            assertEquals(Optional.of(URI.create("https://h/p2")),
                    HttpClient.nextLink("<https://h/p0>; rel=\"prev\", <https://h/p2>; rel=\"next\""),
                    "the next relation is parsed");
            assertEquals(Optional.empty(), HttpClient.nextLink("<https://h/p0>; rel=\"prev\""),
                    "a prev relation alone is no next link");
        }
    }

    @Nested
    @DisplayName("conditional requests and rate limits")
    class ConditionalAndRateLimits {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("sends If-None-Match and reports not modified")
        void notModified() throws Exception {
            String path = segment("c");
            String tag = etag();
            on("GET", "/" + path, response(304, "", Map.of("ETag", tag)));

            Response response = client.get("/" + path, tag);

            assertEquals(Outcome.NOT_MODIFIED, response.outcome(), "a 304 is reported not modified");
            assertEquals(tag, header(nextRequest(), "If-None-Match"), "the entity tag is sent");
        }

        @Test
        @DisplayName("reports 429 with Retry-After as rate limited until the stated instant")
        void retryAfter() {
            String path = segment("r");
            int seconds = Generators.integers(1, 3600).next();
            on("GET", "/" + path, response(429, body(), Map.of("Retry-After", String.valueOf(seconds))));

            Response response = client.get("/" + path);

            assertAll("the rate limit",
                    () -> assertEquals(Outcome.RATE_LIMITED, response.outcome(), "a 429 is rate limited"),
                    () -> assertEquals(now.plusSeconds(seconds), response.resetAt(),
                            "the reset is now plus Retry-After"));
        }

        @Test
        @DisplayName("reports an exhausted primary limit with its reset instant")
        // the HTTP-date form of Retry-After is a static wire shape the client does not parse
        @MockResponseConfig(path = "/rate-limit-date", method = HttpMethodMapper.GET, status = 429,
                stringContent = "{}", headers = {"Retry-After=Wed, 21 Oct 2026 07:28:00 GMT"})
        void primaryLimit() {
            String primary = segment("p");
            String secondary = segment("s");
            long primaryReset = epochSeconds();
            long secondaryReset = epochSeconds();
            on("GET", "/" + primary, response(403, "{}",
                    Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(primaryReset))));
            on("GET", "/" + secondary, response(429, "{}",
                    Map.of("RateLimit-Reset", String.valueOf(secondaryReset))));

            assertAll("the reset instants",
                    () -> assertEquals(Instant.ofEpochSecond(primaryReset),
                            client.get("/" + primary).resetAt(), "the primary limit names its reset"),
                    () -> assertEquals(Instant.ofEpochSecond(secondaryReset),
                            client.get("/" + secondary).resetAt(), "RateLimit-Reset names the reset"),
                    () -> assertEquals(now.plus(HttpClient.DEFAULT_RATE_LIMIT_WAIT),
                            client.get("/rate-limit-date").resetAt(), "an HTTP date falls back to the default"));
        }

        @Test
        @DisplayName("keeps a plain 403 an error")
        @MockResponseConfig(path = "/forbidden", method = HttpMethodMapper.GET, status = 403,
                stringContent = "{}", headers = {"x-ratelimit-remaining=12"})
        void forbidden() {
            assertEquals(Outcome.HTTP_ERROR, client.get("/forbidden").outcome(), "a plain 403 is an HTTP error");
        }
    }
}
