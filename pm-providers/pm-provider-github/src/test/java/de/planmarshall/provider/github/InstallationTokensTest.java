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

import static org.junit.jupiter.api.Assertions.assertAll;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import de.planmarshall.provider.http.Endpoint;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The installation token cache of a GitHub App against a mock GitHub API.
 * <p>
 * Every mint is answered by one response primed per test into {@link #getModuleDispatcher()}, keyed by method and
 * request target, so a mint for a wrong installation gets no answer. Contract literals, kept by design: the mint
 * path grammar {@code /app/installations/{id}/access_tokens}; the JSON field names {@code token},
 * {@code expires_at}, {@code repositories} and {@code permissions}; the permission names and levels GitHub defines
 * ({@code contents}, {@code pull_requests}, {@code read}, {@code write}); the {@code ghs_} prefix of an installation
 * token; the {@code X-GitHub-Api-Version} header with its version; the {@code Bearer} scheme; the five-minute reuse
 * margin; the status codes; the unreadable expiry {@code tomorrow} and the unusable key {@code garbage}. The clock,
 * the App client id, the installation ids, the repository names and the token values are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("InstallationTokens")
class InstallationTokensTest {

    /** The permissions of a mint (contract: GitHub's permission names and levels). */
    private static final Map<String, String> PERMISSIONS = Map.of("pull_requests", "write", "contents", "read");
    /** The request body those permissions give, keys sorted (contract: the mint body grammar). */
    private static final String PERMISSIONS_JSON = "{\"contents\":\"read\",\"pull_requests\":\"write\"}";
    /** The seconds before expiry from which a cached token is renewed (contract: the reuse margin). */
    private static final int REUSE_MARGIN = 300;

    /** A clock the test moves. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

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

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    /** The requests the mock web server received, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private Instant now;
    private String clientId;
    private int installation;
    private String repository;
    private Endpoint endpoint;
    private MovableClock clock;
    private List<String> redacted;
    private InstallationTokens tokens;

    /** Records every request and answers it with the next response primed for its method and target. */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/app";
            }

            @Override
            public Optional<MockResponse> handlePost(RecordedRequest request) {
                requests.add(request);
                Deque<MockResponse> queue = routes.get(request.getMethod() + " " + request.getTarget());
                return Optional.ofNullable(queue == null ? null : queue.poll());
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.POST);
            }
        };
    }

    @BeforeEach
    void start(URIBuilder uriBuilder) {
        URI root = uriBuilder.build();
        endpoint = Endpoint.of(URI.create(root.getScheme() + "://" + root.getHost() + ":" + root.getPort() + "/"));
        now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_900_000_000L).next());
        clientId = "Iv23" + Generators.letterStrings(8, 16).next();
        installation = Generators.integers(1, 1_000_000).next();
        repository = Generators.letterStrings(4, 20).next();
        clock = new MovableClock(now);
        redacted = new ArrayList<>();
        tokens = new InstallationTokens(endpoint, clientId, () -> TestKeys.PKCS8_PEM, redacted::add, clock);
    }

    @AfterEach
    void stop() {
        tokens.close();
    }

    private void on(int installationId, MockResponse response) {
        routes.computeIfAbsent("POST " + path(installationId), k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static String path(int installationId) {
        return "/app/installations/" + installationId + "/access_tokens";
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse.Builder().code(status).addHeader("Content-Type", "application/json").body(body)
                .build();
    }

    private static MockResponse minted(String token, Instant expiresAt) {
        return json(201, "{\"token\":\"" + token + "\",\"expires_at\":\"" + expiresAt + "\"}");
    }

    /** @return a generated installation token with the {@code ghs_} prefix */
    private static String installationToken() {
        return "ghs_" + Generators.letterStrings(16, 36).next();
    }

    /** @return a generated installation id that differs from {@code other} */
    private static int otherInstallation(int other) {
        return other + Generators.integers(1, 1000).next();
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
        String minted = installationToken();
        on(installation, minted(minted, now.plusSeconds(3600)));

        Result<InstallationTokens.InstallationToken> result = tokens.token(installation, repository, PERMISSIONS);

        assertTrue(result.isOk(), "the mint succeeds");
        var request = requests.getFirst();
        String authorization = header(request, "Authorization");
        assertTrue(authorization.startsWith("Bearer "), "the JWT is sent as a bearer token");
        Object claims = Json.parse(TestKeys.verifiedClaims(authorization.substring(7),
                TestKeys.PAIR.getPublic())).orElseThrow();
        assertAll("the minted token and its request",
                () -> assertEquals(minted, result.value().orElseThrow().token(), "the minted token is returned"),
                () -> assertFalse(result.value().orElseThrow().toString().contains(minted),
                        "the token is not printed"),
                () -> assertEquals(Optional.of(clientId), Json.string(claims, "iss"),
                        "the JWT is issued by the App client"),
                () -> assertEquals("{\"repositories\":[\"" + repository + "\"],\"permissions\":"
                        + PERMISSIONS_JSON + "}", body(request), "the mint is narrowed to repo and permissions"),
                () -> assertEquals("2022-11-28", header(request, "X-GitHub-Api-Version"),
                        "the API version header is sent"),
                () -> assertEquals(List.of(minted), redacted, "the minted token is registered for redaction"));
    }

    @Test
    @DisplayName("mints the token of a pull-request comment with pull_requests:write, signed with a PKCS#1 key")
    void mintsForOperationClass() throws Exception {
        on(installation, minted(installationToken(), now.plusSeconds(3600)));
        try (var pkcs1 = new InstallationTokens(endpoint, clientId, () -> TestKeys.PKCS1_PEM, redacted::add,
                Clock.fixed(now, ZoneOffset.UTC))) {

            var result = pkcs1.token(installation, repository, GitHubOperation.PULL_REQUEST_COMMENT);

            assertTrue(result.isOk(), "the PKCS#1 signed mint succeeds");
        }
        var request = requests.getFirst();
        TestKeys.verifiedClaims(header(request, "Authorization").substring(7), TestKeys.PAIR.getPublic());
        assertEquals("{\"repositories\":[\"" + repository + "\"],\"permissions\":{\"pull_requests\":\"write\"}}",
                body(request), "the comment mint asks for pull_requests:write");
    }

    @Test
    @DisplayName("reuses a token until the reuse margin, then mints with a fresh JWT")
    void reusesUntilMargin() {
        String one = installationToken();
        String two = installationToken() + "x";
        on(installation, minted(one, now.plusSeconds(3600)));
        on(installation, minted(two, now.plusSeconds(7200)));

        String first = tokens.token(installation, repository, PERMISSIONS).value().orElseThrow().token();
        clock.now = now.plusSeconds(3600L - REUSE_MARGIN - 1);
        String reused = tokens.tokenSource(installation, repository, PERMISSIONS).token().orElseThrow();
        clock.now = now.plusSeconds(3600L - REUSE_MARGIN);
        String renewed = tokens.token(installation, repository, PERMISSIONS).value().orElseThrow().token();

        assertAll("the reuse window",
                () -> assertEquals(one, first, "the first token is minted"),
                () -> assertEquals(one, reused, "the token is reused before the margin"),
                () -> assertEquals(two, renewed, "a new token is minted at the margin"),
                () -> assertEquals(2, requests.size(), "two mints are sent"),
                () -> assertNotEquals(header(requests.get(0), "Authorization"),
                        header(requests.get(1), "Authorization"), "a JWT is never reused"));
    }

    @Test
    @DisplayName("keys the cache by permission set")
    void keyedByPermissions() {
        String write = installationToken() + "w";
        on(installation, minted(installationToken(), now.plusSeconds(3600)));
        on(installation, minted(write, now.plusSeconds(3600)));

        tokens.token(installation, repository, Map.of("contents", "read"));
        String minted = tokens.token(installation, repository, Map.of("contents", "write")).value().orElseThrow()
                .token();

        assertEquals(write, minted, "another permission set mints its own token");
    }

    @Test
    @DisplayName("reports refused mints, unreadable responses and unusable keys")
    void failures() {
        int noExpiry = otherInstallation(installation);
        int badExpiry = otherInstallation(noExpiry);
        // the installation is asked twice: by token and by the token source, neither caching a refused mint
        on(installation, json(401, "{\"message\":\"A JSON web token could not be decoded\"}"));
        on(installation, json(401, "{\"message\":\"A JSON web token could not be decoded\"}"));
        on(noExpiry, json(201, "{\"token\":\"" + installationToken() + "\"}"));
        on(badExpiry, json(201, "{\"token\":\"" + installationToken() + "\",\"expires_at\":\"tomorrow\"}"));

        assertAll("the failed mints",
                () -> assertEquals(Result.Outcome.AUTH_FAILED, tokens.token(installation, repository, PERMISSIONS)
                        .outcome(), "a refused JWT is an auth failure"),
                () -> assertEquals(Result.Outcome.FAILED, tokens.token(noExpiry, repository, PERMISSIONS).outcome(),
                        "a mint without expiry is a failure"),
                () -> assertEquals(Result.Outcome.FAILED, tokens.token(badExpiry, repository, PERMISSIONS)
                        .outcome(), "an unreadable expiry is a failure"),
                () -> assertTrue(tokens.tokenSource(installation, repository, PERMISSIONS).token().isEmpty(),
                        "a refused mint gives no token"));
        try (var broken = new InstallationTokens(endpoint, clientId, () -> "garbage", redacted::add, clock)) {
            assertEquals(Result.Outcome.AUTH_FAILED, broken.token(installation, repository, PERMISSIONS).outcome(),
                    "an unusable key is an auth failure");
        }
        assertTrue(redacted.isEmpty(), "no failed mint registers a token");
        assertEquals(4, requests.size(), "an unusable key sends no request");
    }
}
