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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

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
 * The GitLab REST client against a mock GitLab API.
 * <p>
 * Every request is answered by one canned response. The static ones are {@code @MockResponseConfig} on the test;
 * the ones that carry generated data are primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target (path and query), so a request with a wrong method,
 * path or query gets no answer. Each nested group returns that one element from its own
 * {@code getModuleDispatcher()}.
 * <p>
 * Contract literals, kept by design: GitLab's URL grammar below {@code /api/v4} with the URL-encoded project path
 * ({@code %2F}), the {@code per_page}, {@code page} and {@code resolved} query names; the JSON field names; the
 * {@code glpat-} prefix of a personal access token; the {@code Bearer} scheme; the states {@code merged},
 * {@code opened}, {@code active}, {@code blocked} and {@code idle}; the scopes {@code api} and
 * {@code read_repository}; the status codes; GitLab's wire messages for a moved head, a disabled merge train and a
 * token that is no personal access token; the bodies of the static configurations. Group and project names,
 * request numbers, commit SHAs, discussion and note ids, user names, titles, bodies, labels, job ids and trace lines
 * are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("GitLabClient")
class GitLabClientTest {

    /** The token-identity read (contract: GitLab's URL grammar). */
    private static final String SELF = "/api/v4/personal_access_tokens/self";
    /** The user read of the identity fallback (contract: GitLab's URL grammar). */
    private static final String USER = "/api/v4/user";
    /** The answer to a token read with a token of another type (contract: GitLab's wire message). */
    private static final String NOT_A_PAT =
            "{\"message\":\"400 Bad request - requires token type to be a personal access token\"}";
    /** The answer to a missing resource (contract: GitLab's wire shape). */
    private static final String NOT_FOUND = "{\"message\":\"404 Not Found\"}";

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    /** The requests the dispatcher saw, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private String base;
    private String token;
    private String projectPath;
    private HttpClient http;
    private GitLabClient client;

    /** Records every request and answers it with the next response primed for its method and target. */
    ModuleDispatcherElement dispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/api/v4";
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
            public Optional<MockResponse> handlePut(RecordedRequest request) {
                return next(request);
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET, HttpMethodMapper.POST, HttpMethodMapper.PUT);
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
        base = root.getScheme() + "://" + root.getHost() + ":" + root.getPort() + "/";
        token = "glpat-" + word();
        projectPath = word() + "/" + word();
        http = new HttpClient(HttpGenerators.endpointAt(URI.create(base + "api/v4")).next(),
                () -> Optional.of(token), Map.of());
        client = new GitLabClient(http, projectPath);
    }

    @AfterEach
    void stop() {
        http.close();
    }

    private void on(String method, String target, MockResponse response) {
        routes.computeIfAbsent(method + " " + target, k -> new ConcurrentLinkedDeque<>()).add(response);
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
        return Generators.letterStrings(4, 16).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated request, issue or job number */
    private static int number() {
        return Generators.integers(1, 100_000).next();
    }

    /** @return the project path of the API, URL-encoded as GitLab expects it */
    private String project() {
        return "/api/v4/projects/" + projectPath.replace("/", "%2F");
    }

    private String mergeRequest(int iid) {
        return project() + "/merge_requests/" + iid;
    }

    private Object body(int index) {
        var body = requests.get(index).getBody();
        assertNotNull(body, "the request carries a body");
        return Json.parse(body.utf8()).orElseThrow();
    }

    @Nested
    @DisplayName("merge path")
    class MergePath {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("merges bound to the expected head")
        void merges() {
            int iid = number();
            String head = word();
            String mergeCommit = word();
            on("PUT", mergeRequest(iid) + "/merge",
                    json(200, "{\"state\":\"merged\",\"merge_commit_sha\":\"" + mergeCommit + "\"}"));

            Result<String> result = client.merge(iid, head);

            assertAll("the merge",
                    () -> assertEquals(mergeCommit, result.value(), "the merge commit is returned"),
                    () -> assertEquals(Optional.of(head), Json.string(body(0), "sha"), "the expected head is sent"),
                    () -> assertEquals("Bearer " + token, requests.getFirst().getHeaders().get("Authorization"),
                            "the token is sent as a bearer token"));
        }

        @Test
        @DisplayName("reports a moved head or an unmergeable request as rejected")
        void rejects() {
            int moved = number();
            int unmergeable = moved + 1;
            on("PUT", mergeRequest(moved) + "/merge",
                    json(409, "{\"message\":\"SHA does not match HEAD of source branch\"}"));
            on("PUT", mergeRequest(unmergeable) + "/merge", json(200, "{}"));

            var refused = client.merge(moved, word());
            var empty = client.merge(unmergeable, word());

            assertAll("the refused merges",
                    () -> assertEquals(Result.Outcome.REJECTED, refused.outcome(), "a moved head is rejected"),
                    () -> assertTrue(refused.detail().contains("SHA does not match"),
                            "the detail names the moved head"),
                    () -> assertEquals(Result.Outcome.FAILED, empty.outcome(), "an empty answer is a failure"));
        }

        @Test
        @DisplayName("reports a merge train that is not enabled as rejected, not as failed")
        void mergeTrainNotEnabled() {
            int disabled = number();
            int gateway = disabled + 1;
            on("POST", project() + "/merge_trains/merge_requests/" + disabled,
                    json(400, "{\"message\":\"Merge trains are not enabled for this project\"}"));
            on("POST", project() + "/merge_trains/merge_requests/" + gateway, json(502, "<html>Bad Gateway</html>"));

            var refused = client.mergeTrainAdd(disabled, word());
            var failed = client.mergeTrainAdd(gateway, word());

            assertAll("the merge train refusals",
                    () -> assertEquals(Result.Outcome.REJECTED, refused.outcome(), "a disabled train is rejected"),
                    () -> assertEquals("HTTP 400: Merge trains are not enabled for this project", refused.detail(),
                            "the detail carries GitLab's message"),
                    () -> assertEquals(Result.Outcome.FAILED, failed.outcome(), "a bad gateway is a failure"));
        }

        @Test
        @DisplayName("adds to the merge train bound to the expected head")
        void mergeTrain() {
            int iid = number();
            int missing = iid + 1;
            String head = word();
            on("POST", project() + "/merge_trains/merge_requests/" + iid,
                    json(201, "[{\"id\":" + number() + ",\"status\":\"idle\"}]"));
            on("POST", project() + "/merge_trains/merge_requests/" + missing, json(404, NOT_FOUND));

            var result = client.mergeTrainAdd(iid, head);
            var notFound = client.mergeTrainAdd(missing, head);

            assertAll("the merge train",
                    () -> assertTrue(result.isOk(), "the request is added to the train"),
                    () -> assertEquals(Optional.of(head), Json.string(body(0), "sha"), "the expected head is sent"),
                    () -> assertEquals(Result.Outcome.NOT_FOUND, notFound.outcome(),
                            "a missing request is not found"));
        }
    }

    @Nested
    @DisplayName("discussions")
    class Discussions {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        private static final String NOTE = "{\"id\":%d,\"author\":{\"username\":\"%s\"},\"body\":\"%s\","
                + "\"resolvable\":true,\"resolved\":%s}";

        private static String discussion(String id, String author, String body, boolean resolved) {
            return "{\"id\":\"" + id + "\",\"notes\":[" + NOTE.formatted(number(), author, body, resolved) + "]}";
        }

        @Test
        @DisplayName("lists every page of discussions")
        void lists() {
            int iid = number();
            String first = word();
            String second = word() + "x";
            String author = word();
            String line1 = word();
            String line2 = word();
            on("GET", mergeRequest(iid) + "/discussions?per_page=100",
                    response(200, "[" + discussion(first, author, line1 + "\\n" + line2, false) + "]",
                            Map.of("Link", "<" + base + mergeRequest(iid).substring(1) + "/discussions"
                                    + "?per_page=100&page=2>; rel=\"next\"")));
            on("GET", mergeRequest(iid) + "/discussions?per_page=100&page=2",
                    json(200, "[" + discussion(second, word(), word(), true) + "]"));

            var result = client.discussions(iid);

            var discussions = result.value();
            assertAll("the discussions",
                    () -> assertTrue(result.complete(), "every page is read"),
                    () -> assertEquals(List.of(first, second),
                            discussions.stream().map(GitLabClient.Discussion::id).toList(),
                            "both pages' discussions are read"),
                    () -> assertFalse(discussions.getFirst().resolved(), "an open discussion is unresolved"),
                    () -> assertTrue(discussions.get(1).resolved(), "a resolved discussion is resolved"),
                    () -> assertEquals(line1 + "\n" + line2, discussions.getFirst().notes().getFirst().body(),
                            "the multi-line note is read"),
                    () -> assertEquals(author, discussions.getFirst().notes().getFirst().author(),
                            "the note author is read"));
        }

        @Test
        @DisplayName("reports a failed first page")
        void failedFirstPage() {
            int missing = number();
            on("GET", mergeRequest(missing) + "/discussions?per_page=100", json(404, NOT_FOUND));

            assertEquals(Result.Outcome.NOT_FOUND, client.discussions(missing).outcome(),
                    "a missing request is not found");
        }

        @Test
        @DisplayName("replies with the body in memory and resolves")
        void repliesAndResolves() {
            int iid = number();
            String id = word();
            int noteId = number();
            on("POST", mergeRequest(iid) + "/discussions/" + id + "/notes", json(201, "{\"id\":" + noteId + "}"));
            on("PUT", mergeRequest(iid) + "/discussions/" + id + "?resolved=true",
                    json(200, discussion(id, word(), word(), true)));
            String reply = word() + ".\n\n> " + word() + " \"" + word() + "\"";

            var replied = client.reply(iid, id, reply);
            var resolved = client.resolve(iid, id);

            assertAll("the reply and the resolve",
                    () -> assertEquals(String.valueOf(noteId), replied.value(),
                            "the note id is returned"),
                    () -> assertEquals(Optional.of(reply), Json.string(body(0), "body"),
                            "the reply body is sent unchanged"),
                    () -> assertEquals(true, resolved.value(), "the discussion is resolved"));
        }

        @Test
        @DisplayName("maps failures of reply and resolve")
        void replyFailures() {
            int iid = number();
            String withoutId = word();
            String missing = word() + "z";
            on("POST", mergeRequest(iid) + "/discussions/" + withoutId + "/notes", json(201, "{}"));
            on("POST", mergeRequest(iid) + "/discussions/" + missing + "/notes", json(404, NOT_FOUND));
            on("PUT", mergeRequest(iid) + "/discussions/" + missing + "?resolved=true", json(404, NOT_FOUND));

            assertAll("the failed reply and resolve",
                    () -> assertEquals(Result.Outcome.FAILED, client.reply(iid, withoutId, word()).outcome(),
                            "a reply without id is a failure"),
                    () -> assertEquals(Result.Outcome.NOT_FOUND, client.reply(iid, missing, word()).outcome(),
                            "a reply to a missing discussion is not found"),
                    () -> assertEquals(Result.Outcome.NOT_FOUND, client.resolve(iid, missing).outcome(),
                            "resolving a missing discussion is not found"));
        }
    }

    @Nested
    @DisplayName("issues")
    class Issues {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("reads an issue and treats a blank milestone title as absent")
        void readsIssue() {
            int iid = number();
            String title = word();
            String description = word();
            String label = word();
            String assignee = word();
            String url = "https://gitlab.com/" + projectPath + "/-/issues/" + iid;
            on("GET", project() + "/issues/" + iid, json(200, "{\"title\":\"" + title + "\","
                    + "\"description\":\"" + description + "\",\"state\":\"opened\",\"web_url\":\"" + url + "\","
                    + "\"labels\":[\"" + label + "\"],\"milestone\":{\"title\":\" \"},"
                    + "\"assignees\":[{\"username\":\"" + assignee + "\"}]}"));

            var issue = client.issue(iid).value();

            assertAll("the issue fields",
                    () -> assertEquals(title, issue.title(), "the issue title is read"),
                    () -> assertEquals(description, issue.body(), "the issue body is read"),
                    () -> assertEquals(List.of(label), issue.labels(), "the issue labels are read"),
                    () -> assertNull(issue.milestone(), "a blank milestone title is no milestone"),
                    () -> assertEquals(List.of(assignee), issue.assignees(), "the assignees are read"),
                    () -> assertEquals("opened", issue.state(), "the issue state is read"),
                    () -> assertEquals(url, issue.url(), "the issue web URL is read"));
        }

        @Test
        @DisplayName("posts a comment on an issue through the notes endpoint")
        void postsIssueComment() {
            int iid = number();
            int noteId = number();
            String comment = word() + " " + word();
            on("POST", project() + "/issues/" + iid + "/notes", json(201, "{\"id\":" + noteId + "}"));

            var result = client.issueComment(iid, comment);

            assertAll("the posted note",
                    () -> assertEquals(String.valueOf(noteId), result.value(),
                            "the posted note id is read"),
                    () -> assertEquals(Optional.of(comment), Json.string(body(0), "body"),
                            "the comment body travels in memory"));
        }
    }

    @Nested
    @DisplayName("job trace and token identity")
    class TraceAndIdentity {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("reads a job trace")
        void trace() {
            int job = number();
            int missing = job + 1;
            String trace = word() + "\n" + word() + "\n";
            on("GET", project() + "/jobs/" + job + "/trace",
                    response(200, trace, Map.of("Content-Type", "text/plain")));
            on("GET", project() + "/jobs/" + missing + "/trace", json(404, NOT_FOUND));

            assertAll("the job traces",
                    () -> assertEquals(trace, client.jobTrace(job).value(), "the trace is read verbatim"),
                    () -> assertEquals(Result.Outcome.NOT_FOUND, client.jobTrace(missing).outcome(),
                            "a missing job is not found"));
        }

        @Test
        @DisplayName("reads the token identity")
        void identity() {
            int tokenId = number();
            int userId = number();
            String name = word();
            on("GET", SELF, json(200, "{\"id\":" + tokenId + ",\"name\":\"" + name + "\",\"user_id\":" + userId
                    + ",\"scopes\":[\"api\",\"read_repository\"],\"active\":true,\"expires_at\":null}"));

            var identity = client.tokenIdentity().value();

            assertAll("the token identity",
                    () -> assertEquals(String.valueOf(userId), identity.userId(), "the user id is read"),
                    () -> assertEquals(List.of("api", "read_repository"), identity.scopes(), "the scopes are read"),
                    () -> assertTrue(identity.active(), "the token is active"),
                    () -> assertNull(identity.expiresAt(), "a null expiry is no expiry"),
                    () -> assertEquals(name, identity.name(), "the token name is read"));
        }

        @Test
        @DisplayName("resolves the identity of a token that is no personal access token through GET /user")
        void identityOfOAuthToken() {
            int userId = number();
            on("GET", SELF, json(400, NOT_A_PAT));
            on("GET", USER, json(200, "{\"id\":" + userId + ",\"username\":\"" + word()
                    + "\",\"state\":\"active\"}"));

            var result = client.tokenIdentity();

            var identity = result.value();
            assertAll("the fallback identity",
                    () -> assertEquals(Result.Outcome.OK, result.outcome(), "the fallback read succeeds"),
                    () -> assertEquals(String.valueOf(userId), identity.userId(), "the user id is read"),
                    () -> assertTrue(identity.active(), "an active user is active"),
                    () -> assertEquals("", identity.id(), "no token id is known"),
                    () -> assertEquals("", identity.name(), "no token name is known"),
                    () -> assertEquals(List.of(), identity.scopes(), "no scopes are known"),
                    () -> assertNull(identity.expiresAt(), "no expiry is known"),
                    () -> assertEquals(List.of("GET " + SELF, "GET " + USER),
                            requests.stream().map(r -> r.getMethod() + " " + r.getTarget()).toList(),
                            "the token is read first, then the user"),
                    () -> assertEquals("Bearer " + token, requests.getLast().getHeaders().get("Authorization"),
                            "the user read carries the token"));
        }

        @Test
        @DisplayName("reports a blocked user and the failures of GET /user for a token of another type")
        void identityFallbackFailures() {
            // each of the three identity reads asks the token first, then GET /user
            for (int read = 0; read < 3; read++) {
                on("GET", SELF, json(400, NOT_A_PAT));
            }
            on("GET", USER, json(200, "{\"id\":" + number() + ",\"state\":\"blocked\"}"));
            on("GET", USER, json(200, "{}"));
            on("GET", USER, json(401, "{\"message\":\"401 Unauthorized\"}"));

            var blocked = client.tokenIdentity();
            var unreadable = client.tokenIdentity();
            var refused = client.tokenIdentity();

            assertAll("the fallback failures",
                    () -> assertFalse(blocked.value().active(), "a blocked user is inactive"),
                    () -> assertEquals(Result.Outcome.FAILED, unreadable.outcome(), "an empty user is a failure"),
                    () -> assertEquals(Result.Outcome.AUTH_FAILED, refused.outcome(),
                            "a refused user read is an auth failure"),
                    () -> assertEquals(6, requests.size(), "each read asks the token, then the user"));
        }

        @Test
        @DisplayName("does not ask GET /user when the token read fails otherwise")
        @MockResponseConfig(path = SELF, method = HttpMethodMapper.GET, status = 500,
                stringContent = "{\"message\":\"500 Internal Server Error\"}")
        void identityWithoutFallback(MockWebServer server) {
            assertAll("the failed token read",
                    () -> assertEquals(Result.Outcome.FAILED, client.tokenIdentity().outcome(),
                            "a server error is a failure"),
                    () -> assertEquals(1, server.getRequestCount(), "GET /user is not asked"));
        }

        @Test
        @DisplayName("reports an invalid token as an authentication failure")
        @MockResponseConfig(path = SELF, method = HttpMethodMapper.GET, status = 401,
                stringContent = "{\"message\":\"401 Unauthorized\"}")
        void unauthorized() {
            assertEquals(Result.Outcome.AUTH_FAILED, client.tokenIdentity().outcome(),
                    "a 401 is an authentication failure");
        }
    }
}
