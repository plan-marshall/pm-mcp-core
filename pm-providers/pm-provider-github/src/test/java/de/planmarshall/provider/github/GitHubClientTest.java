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

import de.planmarshall.provider.github.GitHubClient.ReviewThread;
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
@DisplayName("GitHubClient")
class GitHubClientTest {

    private static final MockResponse NOT_FOUND = json(404, "{\"message\":\"Not Found\"}");

    /**
     * Canned responses by {@code METHOD target}, queued per key; each answers one request, and a key with no
     * response left, like any other request, is answered 404.
     */
    private final Map<String, Deque<MockResponse>> responses = new ConcurrentHashMap<>();
    /** The requests the mock web server received, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private HttpClient http;
    private GitHubClient client;

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
        var base = URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/");
        http = new HttpClient(HttpGenerators.endpointAt(base).next(), () -> Optional.of("ghs_installation"),
                InstallationTokens.HEADERS);
        client = new GitHubClient(http, GitHubClient.GRAPHQL_PATH);
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

    private static String page(String threads, boolean hasNext, String cursor) {
        return "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{\"pageInfo\":{\"hasNextPage\":" + hasNext
                + ",\"endCursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"") + "},\"nodes\":[" + threads
                + "]}}}}}";
    }

    private static String thread(String id, boolean resolved) {
        return "{\"id\":\"" + id + "\",\"isResolved\":" + resolved + ",\"isOutdated\":false,\"path\":\"src/A.java\","
                + "\"line\":12,\"comments\":{\"pageInfo\":{\"hasNextPage\":false},\"nodes\":[{\"id\":\"C1\","
                + "\"databaseId\":101,\"author\":{\"login\":\"coderabbitai[bot]\"},\"body\":\"Consider\\nthis\","
                + "\"createdAt\":\"2026-10-05T10:00:00Z\"}]}}";
    }

    private Object requestJson(int index) {
        var body = requests.get(index).getBody();
        assertNotNull(body, "the request carries a body");
        return Json.parse(body.utf8()).orElseThrow();
    }

    @Nested
    @DisplayName("review threads")
    class ReviewThreads {

        @Test
        @DisplayName("lists every page with the fixed document and the cursor")
        void listsPages() {
            on("POST", "/graphql", json(200, page(thread("T1", false), true, "CUR1")));
            on("POST", "/graphql", json(200, page(thread("T2", true), false, null)));

            Result<List<ReviewThread>> result = client.reviewThreads("cuioss", "plan-marshall-mcp", 7);

            assertTrue(result.isOk());
            assertTrue(result.complete());
            List<ReviewThread> threads = result.value().orElseThrow();
            assertEquals(List.of("T1", "T2"), threads.stream().map(ReviewThread::id).toList());
            ReviewThread first = threads.getFirst();
            assertFalse(first.resolved());
            assertEquals(Optional.of("12"), first.line());
            assertEquals("Consider\nthis", first.comments().getFirst().body());
            assertEquals(Optional.of("coderabbitai[bot]"), first.comments().getFirst().author());
            assertTrue(first.complete());
            assertEquals(Optional.of(GitHubClient.REVIEW_THREADS), Json.string(requestJson(0), "query"));
            assertTrue(Json.at(requestJson(0), "variables", "cursor").isEmpty());
            assertEquals(Optional.of("CUR1"), Json.string(requestJson(1), "variables", "cursor"));
            assertEquals(Optional.of("7"), Json.string(requestJson(1), "variables", "number"));
            assertEquals("Bearer ghs_installation", requests.getFirst().getHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("reports a missing pull request and GraphQL errors")
        void missing() {
            on("POST", "/graphql", json(200, "{\"data\":{\"repository\":{\"pullRequest\":null}}}"));
            on("POST", "/graphql",
                    json(200, "{\"data\":null,\"errors\":[{\"type\":\"NOT_FOUND\",\"message\":\"no repo\"}]}"));

            assertEquals(Result.Outcome.NOT_FOUND, client.reviewThreads("o", "r", 1).outcome());
            var error = client.reviewThreads("o", "missing", 1);
            assertEquals(Result.Outcome.NOT_FOUND, error.outcome());
            assertEquals("no repo", error.detail());
        }

        @Test
        @DisplayName("resolves a thread")
        void resolves() {
            on("POST", "/graphql",
                    json(200, "{\"data\":{\"resolveReviewThread\":{\"thread\":{\"id\":\"T1\",\"isResolved\":true}}}}"));

            Result<Boolean> result = client.resolveReviewThread("T1");

            assertEquals(Optional.of(true), result.value());
            assertEquals(Optional.of(GitHubClient.RESOLVE_REVIEW_THREAD), Json.string(requestJson(0), "query"));
            assertEquals(Optional.of("T1"), Json.string(requestJson(0), "variables", "threadId"));
        }

        @Test
        @DisplayName("passes a rate limit through with its reset instant")
        void rateLimited() {
            on("POST", "/graphql", response(403, "{\"message\":\"API rate limit exceeded\"}",
                    Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1791194400")));

            var result = client.resolveReviewThread("T1");

            assertEquals(Result.Outcome.RATE_LIMITED, result.outcome());
            assertEquals(1791194400L, result.resetAt().orElseThrow().getEpochSecond());
        }
    }

    @Nested
    @DisplayName("CI reads")
    class CiReads {

        @Test
        @DisplayName("reads the App's own identity")
        void readsApp() {
            on("GET", "/app", json(200, "{\"slug\":\"plan-marshall\",\"id\":123}"));

            try (var jwtHttp = new HttpClient(http.endpoint(), () -> Optional.of("jwt"),
                    InstallationTokens.HEADERS)) {
                var appClient = new GitHubClient(jwtHttp, GitHubClient.GRAPHQL_PATH);
                var result = appClient.app();

                assertEquals(Optional.of(new GitHubClient.AppIdentity("plan-marshall", "123")), result.value(),
                        "the App slug and id are read");
            }
            assertEquals("Bearer jwt", requests.getFirst().getHeaders().get("Authorization"),
                    "the App JWT is the bearer token");
        }

        @Test
        @DisplayName("reads one issue and flags a pull request")
        void readsIssue() {
            on("GET", "/repos/cuioss/pm/issues/5", json(200, "{\"title\":\"A title\",\"body\":\"A body\","
                    + "\"state\":\"open\",\"html_url\":\"https://github.com/cuioss/pm/issues/5\","
                    + "\"labels\":[{\"name\":\"bug\"}],\"milestone\":{\"title\":\"M1\"},"
                    + "\"assignees\":[{\"login\":\"octocat\"}]}"));
            on("GET", "/repos/cuioss/pm/issues/6", json(200, "{\"title\":\"A pull request\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/pull/6\",\"pull_request\":{}}"));

            var payload = client.issue("cuioss", "pm", 5).value().orElseThrow();

            assertEquals("A title", payload.issue().title(), "the issue title is mapped");
            assertEquals(List.of("bug"), payload.issue().labels(), "the labels are mapped");
            assertEquals("M1", payload.issue().milestone(), "the milestone is mapped");
            assertEquals(List.of("octocat"), payload.issue().assignees(), "the assignees are mapped");
            assertFalse(payload.pullRequest(), "an issue is no pull request");
            assertTrue(client.issue("cuioss", "pm", 6).value().orElseThrow().pullRequest(),
                    "a pull request is flagged");
        }

        @Test
        @DisplayName("maps a malformed issue body and a refused app read")
        void mapsRefusals() {
            on("GET", "/repos/cuioss/pm/issues/7", json(200, "not json"));
            on("GET", "/repos/cuioss/pm/issues/8", json(200, "{\"title\":\"A title\"}"));
            on("GET", "/app", json(401, "{\"message\":\"Bad credentials\"}"));

            assertEquals(Result.Outcome.FAILED, client.issue("cuioss", "pm", 7).outcome(),
                    "a malformed body is a failure");
            assertEquals(Result.Outcome.FAILED, client.issue("cuioss", "pm", 8).outcome(),
                    "an incomplete body is a failure");
            assertEquals(Result.Outcome.AUTH_FAILED, client.app().outcome(), "a refused JWT is auth failure");
        }
    }

    @Nested
    @DisplayName("merge queue and comments")
    class MergeQueueAndComments {

        @Test
        @DisplayName("enqueues bound to the expected head")
        void enqueues() {
            on("POST", "/graphql", json(200, "{\"data\":{\"enqueuePullRequest\":{\"mergeQueueEntry\":"
                    + "{\"id\":\"MQE1\",\"position\":3,\"state\":\"QUEUED\"}}}}"));

            var entry = client.enqueue("PR_node", "abc123").value().orElseThrow();

            assertEquals(new GitHubClient.MergeQueueEntry("MQE1", "3", "QUEUED"), entry);
            assertEquals(Optional.of(GitHubClient.ENQUEUE_PULL_REQUEST), Json.string(requestJson(0), "query"));
            assertEquals(Optional.of("abc123"), Json.string(requestJson(0), "variables", "expectedHeadOid"));
            assertEquals(Optional.of("PR_node"), Json.string(requestJson(0), "variables", "pullRequestId"));
        }

        @Test
        @DisplayName("reports a refused enqueue and an empty answer")
        void refusedEnqueue() {
            on("POST", "/graphql", json(200,
                    "{\"errors\":[{\"type\":\"UNPROCESSABLE\",\"message\":\"Expected head oid does not match\"}]}"));
            on("POST", "/graphql", json(200, "{\"data\":{\"enqueuePullRequest\":null}}"));
            on("POST", "/graphql", json(200, "{}"));

            var refused = client.enqueue("PR_node", "stale");

            assertEquals(Result.Outcome.REJECTED, refused.outcome());
            assertEquals("Expected head oid does not match", refused.detail());
            assertEquals(Result.Outcome.FAILED, client.enqueue("PR_node", "x").outcome());
            assertEquals(Result.Outcome.FAILED, client.enqueue("PR_node", "x").outcome());
        }

        @Test
        @DisplayName("posts a multi-line comment through REST")
        void postsComment() {
            on("POST", "/repos/cuioss/pm/issues/5/comments", json(201, "{\"id\":9001}"));
            String body = "## Gaps\n\n- one \"quoted\"\n";

            var result = client.postComment("cuioss", "pm", 5, body);

            assertEquals(Optional.of("9001"), result.value());
            assertEquals(Optional.of(body), Json.string(requestJson(0), "body"));
        }

        @Test
        @DisplayName("maps refused comment posts")
        void refusedComment() {
            on("POST", "/repos/cuioss/pm/issues/6/comments", json(201, "{}"));

            assertEquals(Result.Outcome.NOT_FOUND, client.postComment("cuioss", "pm", 404, "x").outcome());
            assertEquals(Result.Outcome.FAILED, client.postComment("cuioss", "pm", 6, "x").outcome());
        }
    }
}
