/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import de.planmarshall.provider.http.Endpoint;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.cuioss.test.mockwebserver.EnableMockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableMockWebServer
@DisplayName("InstallationTokens")
class InstallationTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String PATH = "/app/installations/42/access_tokens";
    private static final Map<String, String> PERMISSIONS = Map.of("pull_requests", "write", "contents", "read");
    private static final MockResponse NOT_FOUND = json(404, "{\"message\":\"Not Found\"}");

    /** A clock the test moves. */
    static final class MovableClock extends Clock {
        Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * Canned responses by {@code METHOD target}, queued per key; each answers one request, and a key with no
     * response left, like any other request, is answered 404.
     */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    /** The requests the mock web server received, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private Endpoint endpoint;
    private MovableClock clock;
    private List<String> redacted;
    private InstallationTokens tokens;

    @BeforeEach
    void start(MockWebServer server) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                Deque<MockResponse> queue = responses.get(request.getMethod() + " " + request.getTarget());
                MockResponse next = queue == null ? null : queue.poll();
                return next == null ? NOT_FOUND : next;
            }
        });
        endpoint = Endpoint.of(URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/"));
        clock = new MovableClock();
        redacted = new ArrayList<>();
        tokens = new InstallationTokens(endpoint, "Iv23liClient", () -> TestKeys.PKCS8_PEM, redacted::add, clock);
    }

    @AfterEach
    void stop() {
        tokens.close();
    }

    private void on(String method, String target, MockResponse response) {
        responses.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse.Builder().code(status).addHeader("Content-Type", "application/json").body(body)
                .build();
    }

    private static MockResponse minted(String token, Instant expiresAt) {
        return json(201, "{\"token\":\"" + token + "\",\"expires_at\":\"" + expiresAt + "\"}");
    }

    private static String header(RecordedRequest request, String name) {
        return request.getHeaders().get(name);
    }

    private static String body(RecordedRequest request) {
        assertNotNull(request.getBody(), "the request carries a body");
        return request.getBody().utf8();
    }

    @Test
    @DisplayName("mints with a verifiable JWT, narrowed to the repository and permissions")
    void mints() throws Exception {
        on("POST", PATH, minted("ghs_one", NOW.plusSeconds(3600)));

        Result<InstallationTokens.InstallationToken> result = tokens.token(42, "plan-marshall-mcp", PERMISSIONS);

        assertTrue(result.isOk());
        assertEquals("ghs_one", result.value().orElseThrow().token());
        assertFalse(result.value().orElseThrow().toString().contains("ghs_one"));
        var request = requests.getFirst();
        String authorization = header(request, "Authorization");
        assertTrue(authorization.startsWith("Bearer "));
        Object claims = Json.parse(TestKeys.verifiedClaims(authorization.substring(7),
                TestKeys.PAIR.getPublic())).orElseThrow();
        assertEquals(Optional.of("Iv23liClient"), Json.string(claims, "iss"));
        assertEquals("{\"repositories\":[\"plan-marshall-mcp\"],\"permissions\":{\"contents\":\"read\","
                + "\"pull_requests\":\"write\"}}", body(request));
        assertEquals("2022-11-28", header(request, "X-GitHub-Api-Version"));
        assertEquals(List.of("ghs_one"), redacted);
    }

    @Test
    @DisplayName("mints the token of a pull-request comment with pull_requests:write, signed with a PKCS#1 key")
    void mintsForOperationClass() throws Exception {
        on("POST", PATH, minted("ghs_comment", NOW.plusSeconds(3600)));
        try (var pkcs1 = new InstallationTokens(endpoint, "Iv23liClient", () -> TestKeys.PKCS1_PEM, redacted::add,
                     Clock.fixed(NOW, ZoneOffset.UTC))) {

            var result = pkcs1.token(42, "plan-marshall-mcp", GitHubOperation.PULL_REQUEST_COMMENT);

            assertTrue(result.isOk());
        }
        var request = requests.getFirst();
        TestKeys.verifiedClaims(header(request, "Authorization").substring(7), TestKeys.PAIR.getPublic());
        assertEquals("{\"repositories\":[\"plan-marshall-mcp\"],\"permissions\":{\"pull_requests\":\"write\"}}",
                body(request));
    }

    @Test
    @DisplayName("reuses a token until the reuse margin, then mints with a fresh JWT")
    void reusesUntilMargin() {
        on("POST", PATH, minted("ghs_one", NOW.plusSeconds(3600)));
        on("POST", PATH, minted("ghs_two", NOW.plusSeconds(7200)));

        String first = tokens.token(42, "repo", PERMISSIONS).value().orElseThrow().token();
        clock.now = NOW.plusSeconds(3600 - 301);
        String reused = tokens.tokenSource(42, "repo", PERMISSIONS).token().orElseThrow();
        clock.now = NOW.plusSeconds(3600 - 300);
        String renewed = tokens.token(42, "repo", PERMISSIONS).value().orElseThrow().token();

        assertEquals("ghs_one", first);
        assertEquals("ghs_one", reused);
        assertEquals("ghs_two", renewed);
        assertEquals(2, requests.size());
        assertNotEquals(header(requests.get(0), "Authorization"), header(requests.get(1), "Authorization"),
                "a JWT is never reused");
    }

    @Test
    @DisplayName("keys the cache by permission set")
    void keyedByPermissions() {
        on("POST", PATH, minted("ghs_read", NOW.plusSeconds(3600)));
        on("POST", PATH, minted("ghs_write", NOW.plusSeconds(3600)));

        tokens.token(42, "repo", Map.of("contents", "read"));
        String write = tokens.token(42, "repo", Map.of("contents", "write")).value().orElseThrow().token();

        assertEquals("ghs_write", write);
    }

    @Test
    @DisplayName("reports refused mints, unreadable responses and unusable keys")
    void failures() {
        // installation 42 is asked twice: by token and by the token source, neither caching a refused mint
        on("POST", PATH, json(401, "{\"message\":\"A JSON web token could not be decoded\"}"));
        on("POST", PATH, json(401, "{\"message\":\"A JSON web token could not be decoded\"}"));
        on("POST", "/app/installations/7/access_tokens", json(201, "{\"token\":\"t\"}"));
        on("POST", "/app/installations/8/access_tokens", json(201, "{\"token\":\"t\",\"expires_at\":\"tomorrow\"}"));

        assertEquals(Result.Outcome.AUTH_FAILED, tokens.token(42, "repo", PERMISSIONS).outcome());
        assertEquals(Result.Outcome.FAILED, tokens.token(7, "repo", PERMISSIONS).outcome());
        assertEquals(Result.Outcome.FAILED, tokens.token(8, "repo", PERMISSIONS).outcome());
        assertTrue(tokens.tokenSource(42, "repo", PERMISSIONS).token().isEmpty());
        try (var broken = new InstallationTokens(endpoint, "id", () -> "garbage", redacted::add, clock)) {
            assertEquals(Result.Outcome.AUTH_FAILED, broken.token(42, "repo", PERMISSIONS).outcome());
        }
        assertTrue(redacted.isEmpty());
        assertEquals(4, requests.size(), "an unusable key sends no request");
    }
}
