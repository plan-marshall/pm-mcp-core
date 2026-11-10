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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Builder;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;

import de.planmarshall.provider.github.GitHubClient.ReviewThread;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.testing.generators.HttpGenerators;
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
 * The GitHub REST and GraphQL client against a mock GitHub API.
 * <p>
 * Every request is answered by one canned response. The static ones are {@code @MockResponseConfig} on the test;
 * the ones that carry generated data are primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target, so a request with a wrong method, path or query gets no
 * answer. Each nested group returns that one element from its own {@code getModuleDispatcher()}.
 * <p>
 * Contract literals, kept by design: GitHub's URL grammar ({@code /graphql}, {@code /app},
 * {@code /repos/{owner}/{repo}/issues/{n}} and its {@code /comments}); the GraphQL and REST field names; the
 * GraphQL error types {@code NOT_FOUND} and {@code UNPROCESSABLE}; the merge-queue state {@code QUEUED}; the
 * {@code ghs_} prefix of an installation token; the {@code Bearer} scheme; the rate-limit header names; the status
 * codes; the bodies of the static configurations. Owners, repositories, numbers, node ids, cursors, logins, titles,
 * bodies, labels, comment texts, tokens and reset instants are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("GitHubClient")
class GitHubClientTest {

    /** The authorization scheme of a token (contract: RFC 6750). */
    private static final String BEARER = "Bearer ";

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    /** The requests the dispatcher saw, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private String token;
    private String owner;
    private String repository;
    private HttpClient http;
    private GitHubClient client;

    /** Records every GET and POST and answers it with the next response primed for its method and target. */
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
        requests.add(request);
        Deque<MockResponse> queue = routes.get(request.getMethod() + " " + request.getTarget());
        return Optional.ofNullable(queue == null ? null : queue.poll());
    }

    @BeforeEach
    void start(URIBuilder uriBuilder) {
        URI root = uriBuilder.build();
        var base = URI.create(root.getScheme() + "://" + root.getHost() + ":" + root.getPort() + "/");
        token = "ghs_" + word();
        owner = word();
        repository = word();
        http = new HttpClient(HttpGenerators.endpointAt(base).next(), () -> Optional.of(token),
                InstallationTokens.HEADERS);
        client = new GitHubClient(http, GitHubClient.GRAPHQL_PATH);
    }

    @AfterEach
    void stop() {
        http.close();
    }

    private void on(String method, String target, MockResponse response) {
        routes.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private void graphql(MockResponse response) {
        on("POST", "/graphql", response);
    }

    private static MockResponse response(int status, String body, Map<String, String> headers) {
        var builder = new MockResponse.Builder().code(status).body(body);
        headers.forEach(builder::addHeader);
        return builder.build();
    }

    private static MockResponse json(int status, String body) {
        return response(status, body, Map.of("Content-Type", "application/json"));
    }

    /** @return a generated word, safe in a path, a JSON string and a header */
    private static String word() {
        return Generators.letterStrings(4, 16).next();
    }

    /** @return a generated issue or pull-request number */
    private static int number() {
        return Generators.integers(1, 100_000).next();
    }

    private String issuePath(int number) {
        return "/repos/" + owner + "/" + repository + "/issues/" + number;
    }

    private static String page(String threads, boolean hasNext, String cursor) {
        return "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{\"pageInfo\":{\"hasNextPage\":" + hasNext
                + ",\"endCursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"") + "},\"nodes\":[" + threads
                + "]}}}}}";
    }

    private static String thread(ThreadData data) {
        return "{\"id\":\"" + data.id + "\",\"isResolved\":" + data.resolved + ",\"isOutdated\":false,"
                + "\"path\":\"" + data.path + "\",\"line\":" + data.line + ",\"comments\":{\"pageInfo\":"
                + "{\"hasNextPage\":false},\"nodes\":[{\"id\":\"" + word() + "\",\"databaseId\":" + number() + ","
                + "\"author\":{\"login\":\"" + data.author + "\"},\"body\":\"" + data.firstLine + "\\n"
                + data.secondLine + "\",\"createdAt\":\"2026-10-05T10:00:00Z\"}]}}";
    }

    /** The generated values of one review thread. */
    @Builder
    private record ThreadData(String id, boolean resolved, String path, int line, String author, String firstLine,
    String secondLine) {

        static ThreadData generated(boolean resolved) {
            return new ThreadData(word(), resolved, word() + "/" + word() + ".java", number(), word() + "[bot]",
                    word(), word());
        }
    }

    private Object requestJson(int index) {
        var body = requests.get(index).getBody();
        assertNotNull(body, "the request carries a body");
        return Json.parse(body.utf8()).orElseThrow();
    }

    @Nested
    @DisplayName("review threads")
    class ReviewThreads {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("lists every page with the fixed document and the cursor")
        void listsPages() {
            var first = ThreadData.generated(false);
            var second = ThreadData.generated(true);
            String cursor = word();
            int pullRequest = number();
            graphql(json(200, page(thread(first), true, cursor)));
            graphql(json(200, page(thread(second), false, null)));

            Result<List<ReviewThread>> result = client.reviewThreads(owner, repository, pullRequest);

            assertTrue(result.isOk(), "the thread read succeeds");
            List<ReviewThread> threads = result.value();
            ReviewThread read = threads.getFirst();
            assertAll("the threads and the requests",
                    () -> assertTrue(result.complete(), "every page is read"),
                    () -> assertEquals(List.of(first.id(), second.id()),
                            threads.stream().map(ReviewThread::id).toList(), "both pages' threads are read"),
                    () -> assertFalse(read.resolved(), "the resolved flag is read"),
                    () -> assertEquals(String.valueOf(first.line()), read.line(), "the line is read"),
                    () -> assertEquals(first.firstLine() + "\n" + first.secondLine(),
                            read.comments().getFirst().body(), "the multi-line comment is read"),
                    () -> assertEquals(first.author(), read.comments().getFirst().author(),
                            "the comment author is read"),
                    () -> assertTrue(read.complete(), "the thread's comments are complete"),
                    () -> assertEquals(Optional.of(GitHubClient.REVIEW_THREADS), Json.string(requestJson(0), "query"),
                            "the fixed document is sent"),
                    () -> assertTrue(Json.at(requestJson(0), "variables", "cursor").isEmpty(),
                            "the first page has no cursor"),
                    () -> assertEquals(Optional.of(cursor), Json.string(requestJson(1), "variables", "cursor"),
                            "the second page sends the cursor"),
                    () -> assertEquals(Optional.of(String.valueOf(pullRequest)),
                            Json.string(requestJson(1), "variables", "number"), "the pull request number is sent"),
                    () -> assertEquals(BEARER + token, requests.getFirst().getHeaders().get("Authorization"),
                            "the installation token is sent"));
        }

        @Test
        @DisplayName("reports a missing pull request and GraphQL errors")
        void missing() {
            String message = word();
            graphql(json(200, "{\"data\":{\"repository\":{\"pullRequest\":null}}}"));
            graphql(json(200, "{\"data\":null,\"errors\":[{\"type\":\"NOT_FOUND\",\"message\":\"" + message
                    + "\"}]}"));

            var missingPullRequest = client.reviewThreads(owner, repository, number());
            var error = client.reviewThreads(owner, word(), number());

            assertAll("the missing reads",
                    () -> assertEquals(Result.Outcome.NOT_FOUND, missingPullRequest.outcome(),
                            "a null pull request is not found"),
                    () -> assertEquals(Result.Outcome.NOT_FOUND, error.outcome(), "a NOT_FOUND error is not found"),
                    () -> assertEquals(message, error.detail(), "the GraphQL message is the detail"));
        }

        @Test
        @DisplayName("resolves a thread")
        void resolves() {
            String threadId = word();
            graphql(json(200, "{\"data\":{\"resolveReviewThread\":{\"thread\":{\"id\":\"" + threadId
                    + "\",\"isResolved\":true}}}}"));

            Result<Boolean> result = client.resolveReviewThread(threadId);

            assertAll("the resolved thread",
                    () -> assertEquals(true, result.value(), "the thread is resolved"),
                    () -> assertEquals(Optional.of(GitHubClient.RESOLVE_REVIEW_THREAD),
                            Json.string(requestJson(0), "query"), "the resolve document is sent"),
                    () -> assertEquals(Optional.of(threadId), Json.string(requestJson(0), "variables", "threadId"),
                            "the thread id is sent"));
        }

        @Test
        @DisplayName("passes a rate limit through with its reset instant")
        void rateLimited() {
            long reset = Generators.longs(1_700_000_000L, 1_900_000_000L).next();
            graphql(response(403, "{\"message\":\"API rate limit exceeded\"}",
                    Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(reset))));

            var result = client.resolveReviewThread(word());

            assertAll("the rate limit",
                    () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is passed through"),
                    () -> assertEquals(reset, result.resetAt().getEpochSecond(),
                            "the reset instant is passed through"));
        }
    }

    @Nested
    @DisplayName("CI reads")
    class CiReads {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("reads the App's own identity")
        void readsApp() {
            String slug = word();
            int appId = number();
            String jwt = word();
            on("GET", "/app", json(200, "{\"slug\":\"" + slug + "\",\"id\":" + appId + "}"));

            try (var jwtHttp = new HttpClient(http.endpoint(), () -> Optional.of(jwt), InstallationTokens.HEADERS)) {
                var appClient = new GitHubClient(jwtHttp, GitHubClient.GRAPHQL_PATH);
                var result = appClient.app();

                assertEquals(new GitHubClient.AppIdentity(slug, String.valueOf(appId)), result.value(),
                        "the App slug and id are read");
            }
            assertEquals(BEARER + jwt, requests.getFirst().getHeaders().get("Authorization"),
                    "the App JWT is the bearer token");
        }

        @Test
        @DisplayName("reads one issue and flags a pull request")
        void readsIssue() {
            int issue = number();
            int pullRequest = issue + 1;
            String title = word();
            String label = word();
            String milestone = word();
            String assignee = word();
            on("GET", issuePath(issue), json(200, "{\"title\":\"" + title + "\",\"body\":\"" + word() + "\","
                    + "\"state\":\"open\",\"html_url\":\"https://github.com/" + owner + "/" + repository + "/issues/"
                    + issue + "\",\"labels\":[{\"name\":\"" + label + "\"}],\"milestone\":{\"title\":\"" + milestone
                    + "\"},\"assignees\":[{\"login\":\"" + assignee + "\"}]}"));
            on("GET", issuePath(pullRequest), json(200, "{\"title\":\"" + word() + "\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/" + owner + "/" + repository + "/pull/" + pullRequest
                    + "\",\"pull_request\":{}}"));

            var payload = client.issue(owner, repository, issue).value();
            var pullPayload = client.issue(owner, repository, pullRequest).value();

            assertAll("the issue and the pull request",
                    () -> assertEquals(title, payload.issue().title(), "the issue title is mapped"),
                    () -> assertEquals(List.of(label), payload.issue().labels(), "the labels are mapped"),
                    () -> assertEquals(milestone, payload.issue().milestone(), "the milestone is mapped"),
                    () -> assertEquals(List.of(assignee), payload.issue().assignees(), "the assignees are mapped"),
                    () -> assertFalse(payload.pullRequest(), "an issue is no pull request"),
                    () -> assertTrue(pullPayload.pullRequest(), "a pull request is flagged"));
        }

        @Test
        @DisplayName("maps a missing body as empty")
        void missingBody() {
            int issue = number();
            on("GET", issuePath(issue), json(200, "{\"title\":\"" + word() + "\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/" + owner + "/" + repository + "/issues/" + issue + "\"}"));

            var payload = client.issue(owner, repository, issue).value();

            assertEquals("", payload.issue().body(), "a missing body is mapped as empty");
        }

        @Test
        @DisplayName("maps a blank milestone title as absent")
        void blankMilestone() {
            int issue = number();
            on("GET", issuePath(issue), json(200, "{\"title\":\"" + word() + "\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/" + owner + "/" + repository + "/issues/" + issue + "\","
                    + "\"milestone\":{\"title\":\" \"}}"));

            var payload = client.issue(owner, repository, issue).value();

            assertNull(payload.issue().milestone(), "a blank milestone title is no milestone");
        }

        @Test
        @DisplayName("maps a malformed issue body and a refused app read")
        // the refused App read is a static wire shape: GitHub's answer to a bad JWT
        @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 401,
                stringContent = "{\"message\":\"Bad credentials\"}")
        void mapsRefusals() {
            int malformed = number();
            int incomplete = malformed + 1;
            on("GET", issuePath(malformed), json(200, "not json"));
            on("GET", issuePath(incomplete), json(200, "{\"title\":\"" + word() + "\"}"));

            assertAll("the refusals",
                    () -> assertEquals(Result.Outcome.FAILED, client.issue(owner, repository, malformed).outcome(),
                            "a malformed body is a failure"),
                    () -> assertEquals(Result.Outcome.FAILED, client.issue(owner, repository, incomplete).outcome(),
                            "an incomplete body is a failure"),
                    () -> assertEquals(Result.Outcome.AUTH_FAILED, client.app().outcome(),
                            "a refused JWT is an auth failure"));
        }
    }

    @Nested
    @DisplayName("merge queue and comments")
    class MergeQueueAndComments {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("enqueues bound to the expected head")
        void enqueues() {
            String entryId = word();
            int position = number();
            String node = word();
            String head = word();
            graphql(json(200, "{\"data\":{\"enqueuePullRequest\":{\"mergeQueueEntry\":{\"id\":\"" + entryId
                    + "\",\"position\":" + position + ",\"state\":\"QUEUED\"}}}}"));

            var entry = client.enqueue(node, head).value();

            assertAll("the queue entry and the request",
                    () -> assertEquals(new GitHubClient.MergeQueueEntry(entryId, String.valueOf(position), "QUEUED"),
                            entry, "the merge queue entry is read"),
                    () -> assertEquals(Optional.of(GitHubClient.ENQUEUE_PULL_REQUEST),
                            Json.string(requestJson(0), "query"), "the enqueue document is sent"),
                    () -> assertEquals(Optional.of(head), Json.string(requestJson(0), "variables", "expectedHeadOid"),
                            "the expected head is sent"),
                    () -> assertEquals(Optional.of(node), Json.string(requestJson(0), "variables", "pullRequestId"),
                            "the pull request node is sent"));
        }

        @Test
        @DisplayName("reports a refused enqueue and an empty answer")
        void refusedEnqueue() {
            String message = word();
            String node = word();
            graphql(json(200, "{\"errors\":[{\"type\":\"UNPROCESSABLE\",\"message\":\"" + message + "\"}]}"));
            graphql(json(200, "{\"data\":{\"enqueuePullRequest\":null}}"));
            graphql(json(200, "{}"));

            var refused = client.enqueue(node, word());
            var nullEntry = client.enqueue(node, word());
            var empty = client.enqueue(node, word());

            assertAll("the refused enqueues",
                    () -> assertEquals(Result.Outcome.REJECTED, refused.outcome(), "an UNPROCESSABLE is rejected"),
                    () -> assertEquals(message, refused.detail(), "the GraphQL message is the detail"),
                    () -> assertEquals(Result.Outcome.FAILED, nullEntry.outcome(), "a null entry is a failure"),
                    () -> assertEquals(Result.Outcome.FAILED, empty.outcome(), "an empty answer is a failure"));
        }

        @Test
        @DisplayName("posts a multi-line comment through REST")
        void postsComment() {
            int issue = number();
            int commentId = number();
            on("POST", issuePath(issue) + "/comments", json(201, "{\"id\":" + commentId + "}"));
            String body = "## " + word() + "\n\n- " + word() + " \"" + word() + "\"\n";

            var result = client.postComment(owner, repository, issue, body);

            assertAll("the posted comment",
                    () -> assertEquals(String.valueOf(commentId), result.value(),
                            "the comment id is returned"),
                    () -> assertEquals(Optional.of(body), Json.string(requestJson(0), "body"),
                            "the comment body is sent unchanged"));
        }

        @Test
        @DisplayName("maps refused comment posts")
        void refusedComment() {
            int missing = number();
            int withoutId = missing + 1;
            on("POST", issuePath(missing) + "/comments", json(404, "{\"message\":\"Not Found\"}"));
            on("POST", issuePath(withoutId) + "/comments", json(201, "{}"));

            assertAll("the refused posts",
                    () -> assertEquals(Result.Outcome.NOT_FOUND,
                            client.postComment(owner, repository, missing, word()).outcome(),
                            "a post to a missing issue is not found"),
                    () -> assertEquals(Result.Outcome.FAILED,
                            client.postComment(owner, repository, withoutId, word()).outcome(),
                            "a post without id is a failure"));
        }
    }
}
