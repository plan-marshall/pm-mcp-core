/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
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

import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.testing.generators.HttpGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableMockWebServer
@EnableGeneratorController
@DisplayName("GitLabClient")
class GitLabClientTest {

    private static final String SELF = "/api/v4/personal_access_tokens/self";
    private static final String MR = "/api/v4/projects/group%2Fproject/merge_requests/12";
    private static final MockResponse NOT_FOUND = json(404, "{\"message\":\"Not Found\"}");

    /**
     * Canned responses by {@code METHOD target}, queued per key; each answers one request, and a key with no
     * response left, like any other request, is answered 404.
     */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    /** The requests the mock web server received, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private String base;
    private HttpClient http;
    private GitLabClient client;

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
        base = "http://" + server.getHostName() + ":" + server.getPort() + "/";
        http = new HttpClient(HttpGenerators.endpointAt(URI.create(base + "api/v4")).next(),
                () -> Optional.of("glpat-secret"), Map.of());
        client = new GitLabClient(http, "group/project");
    }

    @AfterEach
    void stop() {
        http.close();
    }

    private void on(String method, String target, MockResponse response) {
        responses.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static MockResponse response(int status, String body, Map<String, String> headers) {
        var builder = new MockResponse.Builder().code(status).body(body);
        headers.forEach(builder::addHeader);
        return builder.build();
    }

    private static MockResponse json(int status, String body) {
        return response(status, body, Map.of("Content-Type", "application/json"));
    }

    private Object body(int index) {
        var body = requests.get(index).getBody();
        assertNotNull(body, "the request carries a body");
        return Json.parse(body.utf8());
    }

    @Nested
    @DisplayName("merge path")
    class MergePath {

        @Test
        @DisplayName("merges bound to the expected head")
        void merges() {
            on("PUT", MR + "/merge", json(200, "{\"state\":\"merged\",\"merge_commit_sha\":\"m1\"}"));

            Result<String> result = client.merge(12, "abc");

            assertEquals(Optional.of("m1"), result.value());
            assertEquals(Optional.of("abc"), Json.string(body(0), "sha"));
            assertEquals("Bearer glpat-secret", requests.getFirst().getHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("reports a moved head or an unmergeable request as rejected")
        void rejects() {
            on("PUT", MR + "/merge", json(409, "{\"message\":\"SHA does not match HEAD of source branch\"}"));
            on("PUT", "/api/v4/projects/group%2Fproject/merge_requests/13/merge", json(200, "{}"));

            var moved = client.merge(12, "stale");

            assertEquals(Result.Outcome.REJECTED, moved.outcome());
            assertTrue(moved.detail().contains("SHA does not match"));
            assertEquals(Result.Outcome.FAILED, client.merge(13, "x").outcome());
        }

        @Test
        @DisplayName("reports a merge train that is not enabled as rejected, not as failed")
        void mergeTrainNotEnabled() {
            on("POST", "/api/v4/projects/group%2Fproject/merge_trains/merge_requests/12",
                    json(400, "{\"message\":\"Merge trains are not enabled for this project\"}"));
            on("POST", "/api/v4/projects/group%2Fproject/merge_trains/merge_requests/13",
                    json(502, "<html>Bad Gateway</html>"));

            var refused = client.mergeTrainAdd(12, "abc");

            assertEquals(Result.Outcome.REJECTED, refused.outcome());
            assertEquals("HTTP 400: Merge trains are not enabled for this project", refused.detail());
            assertEquals(Result.Outcome.FAILED, client.mergeTrainAdd(13, "abc").outcome());
        }

        @Test
        @DisplayName("adds to the merge train bound to the expected head")
        void mergeTrain() {
            on("POST", "/api/v4/projects/group%2Fproject/merge_trains/merge_requests/12",
                    json(201, "[{\"id\":1,\"status\":\"idle\"}]"));

            var result = client.mergeTrainAdd(12, "abc");

            assertTrue(result.isOk());
            assertEquals(Optional.of("abc"), Json.string(body(0), "sha"));
            assertEquals(Result.Outcome.NOT_FOUND, client.mergeTrainAdd(99, "abc").outcome());
        }
    }

    @Nested
    @DisplayName("discussions")
    class Discussions {

        private static final String NOTE = "{\"id\":%d,\"author\":{\"username\":\"%s\"},\"body\":\"%s\","
                + "\"resolvable\":true,\"resolved\":%s}";

        private String discussion(String id, String author, boolean resolved) {
            return "{\"id\":\"" + id + "\",\"notes\":[" + NOTE.formatted(1, author, "Fix\\nthis", resolved) + "]}";
        }

        @Test
        @DisplayName("lists every page of discussions")
        void lists() {
            on("GET", MR + "/discussions?per_page=100", response(200, "[" + discussion("d1", "bot", false) + "]",
                    Map.of("Link", "<" + base + "api/v4/projects/group%2Fproject/merge_requests/12/discussions"
                            + "?per_page=100&page=2>; rel=\"next\"")));
            on("GET", MR + "/discussions?per_page=100&page=2", json(200, "[" + discussion("d2", "dev", true) + "]"));

            var result = client.discussions(12);

            assertTrue(result.complete());
            var discussions = result.value().orElseThrow();
            assertEquals(List.of("d1", "d2"), discussions.stream().map(GitLabClient.Discussion::id).toList());
            assertFalse(discussions.getFirst().resolved());
            assertTrue(discussions.get(1).resolved());
            assertEquals("Fix\nthis", discussions.getFirst().notes().getFirst().body());
            assertEquals("bot", discussions.getFirst().notes().getFirst().author());
        }

        @Test
        @DisplayName("reports a failed first page")
        void failedFirstPage() {
            assertEquals(Result.Outcome.NOT_FOUND, client.discussions(404).outcome());
        }

        @Test
        @DisplayName("replies with the body in memory and resolves")
        void repliesAndResolves() {
            on("POST", MR + "/discussions/d1/notes", json(201, "{\"id\":77}"));
            on("PUT", MR + "/discussions/d1?resolved=true", json(200, discussion("d1", "bot", true)));
            String reply = "Fixed in abc.\n\n> quoted \"text\"";

            var replied = client.reply(12, "d1", reply);
            var resolved = client.resolve(12, "d1");

            assertEquals(Optional.of("77"), replied.value());
            assertEquals(Optional.of(reply), Json.string(body(0), "body"));
            assertEquals(Optional.of(true), resolved.value());
        }

        @Test
        @DisplayName("maps failures of reply and resolve")
        void replyFailures() {
            on("POST", MR + "/discussions/d9/notes", json(201, "{}"));

            assertEquals(Result.Outcome.FAILED, client.reply(12, "d9", "x").outcome());
            assertEquals(Result.Outcome.NOT_FOUND, client.reply(12, "zz", "x").outcome());
            assertEquals(Result.Outcome.NOT_FOUND, client.resolve(12, "zz").outcome());
        }
    }

    @Nested
    @DisplayName("job trace and token identity")
    class TraceAndIdentity {

        @Test
        @DisplayName("reads a job trace")
        void trace() {
            on("GET", "/api/v4/projects/group%2Fproject/jobs/5/trace",
                    response(200, "line 1\nline 2\n", Map.of("Content-Type", "text/plain")));

            assertEquals(Optional.of("line 1\nline 2\n"), client.jobTrace(5).value());
            assertEquals(Result.Outcome.NOT_FOUND, client.jobTrace(6).outcome());
        }

        @Test
        @DisplayName("reads the token identity")
        void identity() {
            on("GET", SELF, json(200, "{\"id\":4,\"name\":\"pm-mcp\","
                    + "\"user_id\":17,\"scopes\":[\"api\",\"read_repository\"],\"active\":true,\"expires_at\":null}"));

            var identity = client.tokenIdentity().value().orElseThrow();

            assertEquals("17", identity.userId());
            assertEquals(List.of("api", "read_repository"), identity.scopes());
            assertTrue(identity.active());
            assertTrue(identity.expiresAt().isEmpty());
            assertEquals("pm-mcp", identity.name());
        }

        @Test
        @DisplayName("resolves the identity of a token that is no personal access token through GET /user")
        void identityOfOAuthToken() {
            on("GET", SELF, json(400,
                    "{\"message\":\"400 Bad request - requires token type to be a personal access token\"}"));
            on("GET", "/api/v4/user", json(200, "{\"id\":17,\"username\":\"oliver\",\"state\":\"active\"}"));

            var result = client.tokenIdentity();

            var identity = result.value().orElseThrow();
            assertEquals(Result.Outcome.OK, result.outcome());
            assertEquals("17", identity.userId());
            assertTrue(identity.active());
            assertEquals("", identity.id());
            assertEquals("", identity.name());
            assertEquals(List.of(), identity.scopes());
            assertTrue(identity.expiresAt().isEmpty());
            assertEquals(List.of("GET " + SELF, "GET /api/v4/user"), requests.stream()
                    .map(r -> r.getMethod() + " " + r.getTarget()).toList());
            assertEquals("Bearer glpat-secret", requests.getLast().getHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("reports a blocked user and the failures of GET /user for a token of another type")
        void identityFallbackFailures() {
            // each of the three identity reads asks the token first, then GET /user
            for (int read = 0; read < 3; read++) {
                on("GET", SELF, json(400,
                        "{\"message\":\"400 Bad request - requires token type to be a personal access token\"}"));
            }
            on("GET", "/api/v4/user", json(200, "{\"id\":18,\"state\":\"blocked\"}"));
            on("GET", "/api/v4/user", json(200, "{}"));
            on("GET", "/api/v4/user", json(401, "{\"message\":\"401 Unauthorized\"}"));

            var blocked = client.tokenIdentity();
            var unreadable = client.tokenIdentity();
            var refused = client.tokenIdentity();

            assertFalse(blocked.value().orElseThrow().active());
            assertEquals(Result.Outcome.FAILED, unreadable.outcome());
            assertEquals(Result.Outcome.AUTH_FAILED, refused.outcome());
            assertEquals(6, requests.size());
        }

        @Test
        @DisplayName("does not ask GET /user when the token read fails otherwise")
        void identityWithoutFallback() {
            on("GET", SELF, json(500, "{\"message\":\"500 Internal Server Error\"}"));

            assertEquals(Result.Outcome.FAILED, client.tokenIdentity().outcome());
            assertEquals(1, requests.size());
        }

        @Test
        @DisplayName("reports an invalid token as an authentication failure")
        void unauthorized() {
            on("GET", SELF, json(401, "{\"message\":\"401 Unauthorized\"}"));

            assertEquals(Result.Outcome.AUTH_FAILED, client.tokenIdentity().outcome());
        }
    }
}
