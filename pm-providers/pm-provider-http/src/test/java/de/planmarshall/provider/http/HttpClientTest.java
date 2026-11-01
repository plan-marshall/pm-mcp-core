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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.http.client.handler.RedirectPolicy.RedirectRefusal;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import de.planmarshall.provider.http.Response.Outcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableMockWebServer
@DisplayName("HttpClient")
class HttpClientTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final TokenSource TOKEN = () -> Optional.of("s3cret");
    private static final MockResponse NOT_FOUND = json(404, "{\"message\":\"Not Found\"}");
    /** Redirects queued for the redirect loop, more hops than cui-http follows. */
    private static final int LOOP_REDIRECTS = 20;

    /**
     * Canned responses by {@code METHOD target}, queued per key; each answers one request, and a key with no
     * response left, like any other request, is answered 404.
     */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    private MockWebServer server;
    private HttpClient client;

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
        client = new HttpClient(new Endpoint(base(), Set.of()), TOKEN,
                Map.of("Accept", "application/vnd.github+json"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        client.close();
    }

    private void on(String method, String target, MockResponse response) {
        responses.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
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

    /**
     * Base URI with the given host name; the literal of the bound address and {@code localhost} give two origins
     * on one server.
     */
    private URI base(String host) {
        return URI.create("http://" + host + ":" + server.getPort() + "/");
    }

    private URI base() {
        return base(boundHost());
    }

    /**
     * The literal of the address the mock web server bound: {@code 127.0.0.1} for an IPv4 address, {@code [::1]}
     * for an IPv6 address, in the bracketed form a URI needs.
     */
    private String boundHost() {
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

        @Test
        @DisplayName("sends the bearer token and default headers to the origin")
        void bearer() throws Exception {
            on("GET", "/repos/o/r", response(200, "{\"id\":1}", Map.of("ETag", "\"v1\"")));

            Response response = client.get("repos/o/r");

            assertEquals(Outcome.OK, response.outcome());
            assertEquals("{\"id\":1}", response.body());
            assertEquals(Optional.of("\"v1\""), response.etag());
            var request = nextRequest();
            assertEquals("Bearer s3cret", header(request, "Authorization"));
            assertEquals("application/vnd.github+json", header(request, "Accept"));
        }

        @Test
        @DisplayName("keeps the headers of a header-aware read")
        void keepsHeaders() {
            on("GET", "/user", response(200, "{\"login\":\"octocat\"}",
                    Map.of("x-oauth-scopes", "repo, read:org")));

            ResponseWithHeaders observed = client.getWithHeaders("/user");

            assertAll("the observed headers",
                    () -> assertTrue(observed.isOk(), "the response is ok"),
                    () -> assertEquals(Optional.of("repo, read:org"), observed.header("X-OAuth-Scopes"),
                            "a header is read case-insensitively"),
                    () -> assertTrue(observed.header("missing").isEmpty(), "a missing header is empty"));
        }

        @Test
        @DisplayName("posts a JSON body from memory")
        void postsJson() throws Exception {
            on("POST", "/x", json(201, "{}"));

            Response response = client.send("POST", "/x", Optional.of("{\"body\":\"line1\\nline2\"}"), Optional.empty());

            assertTrue(response.isOk());
            var request = nextRequest();
            assertNotNull(request.getBody(), "the request carries a body");
            assertEquals("{\"body\":\"line1\\nline2\"}", request.getBody().utf8());
            assertEquals("application/json", header(request, "Content-Type"));
        }

        @Test
        @DisplayName("sends no Authorization header without a token")
        void noToken() throws Exception {
            try (var anonymous = new HttpClient(Endpoint.of(base()), TokenSource.NONE, Map.of())) {
                on("GET", "/open", json(200, "[]"));

                assertTrue(anonymous.get("/open").isOk());
                assertNull(header(nextRequest(), "Authorization"));
                assertEquals(base(), anonymous.endpoint().baseUri());
            }
        }

        @Test
        @DisplayName("refuses a target outside the origin without sending anything")
        void refusesOtherOrigin() {
            Response response = client.get(base("localhost") + "steal");

            assertEquals(Outcome.REFUSED, response.outcome());
            assertEquals(0, server.getRequestCount());
        }

        @Test
        @DisplayName("reports an error status and a transport failure")
        void failures() throws Exception {
            Response notFound = client.get("/missing");
            Response unreachable;
            try (var closed = new HttpClient(Endpoint.of(closedOrigin()), TOKEN, Map.of())) {
                unreachable = closed.get("/any");
            }

            assertEquals(Outcome.HTTP_ERROR, notFound.outcome());
            assertEquals(404, notFound.status());
            assertEquals(Outcome.TRANSPORT_FAILED, unreachable.outcome());
            assertEquals(0, unreachable.status());
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

        @Test
        @DisplayName("drops the token on a redirect to another origin")
        void dropsTokenCrossOrigin() throws Exception {
            try (var redirecting = new HttpClient(new Endpoint(base(), Set.of("localhost")), TOKEN, Map.of())) {
                on("GET", "/log", json(200, "log text"));
                on("GET", "/jobs/1/logs", response(302, "", Map.of("Location", base("localhost") + "log")));

                Response response = redirecting.get("/jobs/1/logs");

                assertEquals(Outcome.OK, response.outcome());
                assertEquals("log text", response.body());
                var origin = nextRequest();
                var redirected = nextRequest();
                assertTrue(header(origin, "Host").startsWith(boundHost() + ":"),
                        "first request to the endpoint origin");
                assertEquals("Bearer s3cret", header(origin, "Authorization"));
                assertTrue(header(redirected, "Host").startsWith("localhost:"), "redirect followed to the other origin");
                assertNull(header(redirected, "Authorization"), "token dropped across origins");
            }
        }

        @Test
        @DisplayName("refuses a redirect to a host that is not allowlisted")
        void refusesRedirect() {
            on("GET", "/jobs/2/logs", response(302, "", Map.of("Location", base("localhost") + "log")));

            Response response = client.get("/jobs/2/logs");

            assertEquals(Outcome.REFUSED, response.outcome());
            assertTrue(response.detail().contains("CROSS_ORIGIN"));
            assertEquals(1, server.getRequestCount(), "nothing sent to the refused redirect target");
        }

        @Test
        @DisplayName("guards the rule that every HTTP call goes through cui-http: a redirect loop is refused "
                + "with the reason cui-http names")
        void sendsThroughCuiHttp() {
            // the loop sends /loop once per hop: one redirect for each, more than cui-http follows
            for (int hop = 0; hop < LOOP_REDIRECTS; hop++) {
                on("GET", "/loop", response(302, "", Map.of("Location", base() + "loop")));
            }

            Response response = client.get("/loop");

            assertEquals(Outcome.REFUSED, response.outcome());
            assertEquals("redirect refused: " + RedirectRefusal.TOO_MANY_HOPS, response.detail());
        }
    }

    @Nested
    @DisplayName("pagination")
    class Pagination {

        private void pages(int count) {
            for (int i = 1; i <= count; i++) {
                var headers = i < count
                        ? Map.of("Link", "<" + base() + "items?page=" + (i + 1) + ">; rel=\"next\", <"
                        + base() + "items?page=" + count + ">; rel=\"last\"")
                        : Map.<String, String>of();
                on("GET", "/items?page=" + i, response(200, "[" + i + "]", headers));
            }
        }

        @Test
        @DisplayName("follows every next link and reports completeness")
        void complete() throws Exception {
            pages(3);

            PagedResult result = client.getAll("items?page=1", 10);

            assertTrue(result.complete());
            assertEquals(List.of("[1]", "[2]", "[3]"), result.pages());
            assertEquals(3, server.getRequestCount());
            for (int i = 0; i < 3; i++) {
                assertEquals("Bearer s3cret", header(nextRequest(), "Authorization"));
            }
        }

        @Test
        @DisplayName("is incomplete at the page cap")
        void pageCap() {
            pages(3);

            PagedResult result = client.getAll("items?page=1", 2);

            assertFalse(result.complete());
            assertEquals(2, result.pages().size());
        }

        @Test
        @DisplayName("is incomplete when a page fails or the next link leaves the origin")
        void incomplete() {
            on("GET", "/a?page=1", response(200, "[1]", Map.of("Link", "<" + base() + "a?page=2>; rel=next")));
            on("GET", "/b?page=1",
                    response(200, "[1]", Map.of("Link", "<" + base("localhost") + "b?page=2>; rel=\"next\"")));

            PagedResult failedPage = client.getAll("a?page=1", 10);
            PagedResult leaves = client.getAll("b?page=1", 10);

            assertFalse(failedPage.complete());
            assertEquals(Outcome.HTTP_ERROR, failedPage.last().outcome());
            assertFalse(leaves.complete());
            assertEquals(List.of("[1]"), leaves.pages());
        }

        @Test
        @DisplayName("parses only the next relation")
        void parsesLink() {
            assertEquals(Optional.of(URI.create("https://h/p2")),
                    HttpClient.nextLink("<https://h/p0>; rel=\"prev\", <https://h/p2>; rel=\"next\""));
            assertEquals(Optional.empty(), HttpClient.nextLink("<https://h/p0>; rel=\"prev\""));
        }
    }

    @Nested
    @DisplayName("conditional requests and rate limits")
    class ConditionalAndRateLimits {

        @Test
        @DisplayName("sends If-None-Match and reports not modified")
        void notModified() throws Exception {
            on("GET", "/c", response(304, "", Map.of("ETag", "\"v1\"")));

            Response response = client.get("/c", "\"v1\"");

            assertEquals(Outcome.NOT_MODIFIED, response.outcome());
            assertEquals("\"v1\"", header(nextRequest(), "If-None-Match"));
        }

        @Test
        @DisplayName("reports 429 with Retry-After as rate limited until the stated instant")
        void retryAfter() {
            on("GET", "/r1", response(429, "slow down", Map.of("Retry-After", "30")));

            Response response = client.get("/r1");

            assertEquals(Outcome.RATE_LIMITED, response.outcome());
            assertEquals(Optional.of(NOW.plusSeconds(30)), response.resetAt());
        }

        @Test
        @DisplayName("reports an exhausted primary limit with its reset instant")
        void primaryLimit() {
            on("GET", "/r2", response(403, "{}",
                    Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1791194400")));
            on("GET", "/r3", response(429, "{}", Map.of("RateLimit-Reset", "1791194500")));
            on("GET", "/r4", response(429, "{}", Map.of("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")));

            assertEquals(Optional.of(Instant.ofEpochSecond(1791194400L)), client.get("/r2").resetAt());
            assertEquals(Optional.of(Instant.ofEpochSecond(1791194500L)), client.get("/r3").resetAt());
            assertEquals(Optional.of(NOW.plus(HttpClient.DEFAULT_RATE_LIMIT_WAIT)), client.get("/r4").resetAt());
        }

        @Test
        @DisplayName("keeps a plain 403 an error")
        void forbidden() {
            on("GET", "/f", response(403, "{}", Map.of("x-ratelimit-remaining", "12")));

            assertEquals(Outcome.HTTP_ERROR, client.get("/f").outcome());
        }
    }
}
