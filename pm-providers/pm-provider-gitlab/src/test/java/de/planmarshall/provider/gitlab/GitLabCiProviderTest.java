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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.ci.Identity;
import de.planmarshall.provider.ci.Issue;
import de.planmarshall.provider.ci.IssueComment;
import de.planmarshall.provider.ci.IssueRef;
import de.planmarshall.provider.ci.PullRequest;
import de.planmarshall.provider.ci.PullRequestRef;
import de.planmarshall.provider.ci.RepositoryRef;
import de.planmarshall.provider.ci.TokenKind;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.TokenSource;
import de.planmarshall.provider.http.testing.generators.HttpGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import de.cuioss.test.mockwebserver.mockresponse.MockResponseConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("GitLabCiProvider")
class GitLabCiProviderTest {

    private static final String WEB_ORIGIN = "https://gitlab.com";
    private static final RepositoryRef REPOSITORY = new RepositoryRef("gitlab", WEB_ORIGIN, "cuioss", "pm");
    private static final String TOKEN = "glpat-token";
    private static final PullRequestRef MERGE_REQUEST = new PullRequestRef(REPOSITORY, 7);
    /** The project reads a test primes, answered by {@link #getModuleDispatcher()}. */
    private static final String PROJECTS_PATH = "/projects";
    /** The merge-request read of {@link #MERGE_REQUEST}; the path is GitLab's wire format. */
    private static final String MERGE_REQUEST_PATH = "/projects/cuioss%2Fpm/merge_requests/7";
    private static final String DIFFS_PATH = MERGE_REQUEST_PATH + "/diffs";

    private final List<HttpClient> clients = new CopyOnWriteArrayList<>();
    private final Map<String, MockResponse> primed = new ConcurrentHashMap<>();

    /**
     * Answers the project, merge-request and diffs reads a test primed by their path; those reads carry generated
     * titles, branches, paths and project ids, which a static {@code @MockResponseConfig} cannot. A path no test
     * primed falls through to the {@code @MockResponseConfig} of the test.
     */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return PROJECTS_PATH;
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                return Optional.ofNullable(primed.get(request.getUrl().encodedPath()));
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET);
            }
        };
    }

    @AfterEach
    void closeClients() {
        clients.forEach(HttpClient::close);
    }

    private HttpClient http(MockWebServer server) {
        URI baseUri = URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/");
        HttpClient client = new HttpClient(HttpGenerators.endpointAt(baseUri).next(), () -> Optional.of(TOKEN),
                Map.of());
        clients.add(client);
        return client;
    }

    private GitLabCiProvider provider(MockWebServer server) {
        return new GitLabCiProvider(WEB_ORIGIN, http(server));
    }

    private IssueRef issue(String origin, String forge, String ownerOrPath, String name, int number) {
        return new IssueRef(new RepositoryRef(forge, origin, ownerOrPath, name), number);
    }

    private static String authorization(RecordedRequest request) {
        return request == null ? null : request.getHeaders().get("Authorization");
    }

    @Test
    @DisplayName("reads a personal access token identity with its scopes and expiry")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":7,\"scopes\":[\"api\",\"read_user\"],"
                    + "\"active\":true,\"expires_at\":\"2026-12-01\"}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":7,\"username\":\"alice\",\"state\":\"active\",\"bot\":false}")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"project_access\":{\"access_level\":40},"
                    + "\"group_access\":{\"access_level\":50}}}")
    void readsTokenIdentity(MockWebServer server) {
        Identity identity = provider(server).whoami(REPOSITORY).value().orElseThrow();

        assertAll("the token identity",
                () -> assertEquals("alice", identity.login(), "the username is read"),
                () -> assertEquals("7", identity.userId(), "the provider user id is read"),
                () -> assertEquals(TokenKind.GITLAB_PERSONAL_TOKEN, identity.tokenKind(), "the kind is personal"),
                () -> assertEquals(List.of("api", "read_user"), identity.scopes(), "the scopes are read from the record"),
                () -> assertEquals(Instant.parse("2026-12-01T00:00:00Z"), identity.expiresAt(),
                        "the expiry instant is read"),
                () -> assertEquals(Map.of("project_access", "40", "group_access", "50"), identity.permissions(),
                        "the repository permissions are read"));
    }

    @Test
    @DisplayName("names a project and a group access token from its bot user")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":12,\"name\":\"project\",\"user_id\":9,\"scopes\":[\"api\"],"
                    + "\"active\":true,\"expires_at\":\"2026-12-01\"}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":9,\"username\":\"project_42_bot_abc\",\"state\":\"active\",\"bot\":true}")
    void namesProjectToken(MockWebServer server) {
        var identity = provider(server).whoami(null).value().orElseThrow();

        assertAll("the project token",
                () -> assertEquals(TokenKind.GITLAB_PROJECT_TOKEN, identity.tokenKind(), "the kind is project"),
                () -> assertTrue(identity.permissions().isEmpty(), "no repository means no permissions"));
    }

    @Test
    @DisplayName("names a group access token from its bot user")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":13,\"name\":\"group\",\"user_id\":9,\"scopes\":[\"api\"],"
                    + "\"active\":true,\"expires_at\":\"2026-12-01\"}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":9,\"username\":\"group_5_bot_xyz\",\"state\":\"active\",\"bot\":true}")
    void namesGroupToken(MockWebServer server) {
        var identity = provider(server).whoami(null).value().orElseThrow();

        assertEquals(TokenKind.GITLAB_GROUP_TOKEN, identity.tokenKind(), "the token kind is a group token");
    }

    @Test
    @DisplayName("names an OAuth token from the refused token record")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 400,
            stringContent = "{\"message\":\"400 Unauthorized\"}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":7,\"username\":\"alice\",\"state\":\"active\"}")
    void namesOAuthToken(MockWebServer server) {
        var identity = provider(server).whoami(null).value().orElseThrow();

        assertEquals(TokenKind.GITLAB_OAUTH_TOKEN, identity.tokenKind(), "the token kind is an OAuth token");
    }

    @Test
    @DisplayName("reads an issue of a nested group project")
    @MockResponseConfig(path = "/projects/group%2Fsub%2Fproject/issues/9", method = HttpMethodMapper.GET,
            status = 200, stringContent = "{\"title\":\"A title\",\"description\":\"A body\",\"state\":\"opened\","
            + "\"web_url\":\"https://gitlab.com/group/sub/project/-/issues/9\","
            + "\"labels\":[\"bug\",\"docs\"],\"milestone\":{\"title\":\"M1\"},"
            + "\"assignees\":[{\"username\":\"alice\"}]}")
    void readsNestedProjectIssue(MockWebServer server) {
        var provider = provider(server);

        Result<Issue> result = provider.issueView(issue(WEB_ORIGIN, "gitlab", "group/sub", "project", 9));

        Issue read = result.value().orElseThrow();
        assertAll("the issue fields",
                () -> assertEquals("A title", read.title(), "the issue title is read"),
                () -> assertEquals("A body", read.body(), "the issue body is read"),
                () -> assertEquals(List.of("bug", "docs"), read.labels(), "the issue labels are read"),
                () -> assertEquals("M1", read.milestone(), "the milestone is read"),
                () -> assertEquals(List.of("alice"), read.assignees(), "the assignees are read"),
                () -> assertEquals("opened", read.state(), "the issue state is read"),
                () -> assertEquals("https://gitlab.com/group/sub/project/-/issues/9", read.url(),
                        "the issue web URL is read"));
    }

    @Test
    @DisplayName("passes a rate limit through with a reset instant")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/5", method = HttpMethodMapper.GET, status = 429,
            stringContent = "{\"message\":\"Too many requests\"}", headers = {"Retry-After=60"})
    void reportsRateLimit(MockWebServer server) {
        Instant before = Instant.now();
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5));
        Instant after = Instant.now();
        Instant reset = result.resetAt().orElseThrow();

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertFalse(reset.isBefore(before.plusSeconds(60)),
                        "the reset instant is at least 60 seconds away"),
                () -> assertFalse(reset.isAfter(after.plusSeconds(60)),
                        "the reset instant is at most 60 seconds away"));
    }

    @Test
    @DisplayName("maps a forbidden and a missing issue")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/5", method = HttpMethodMapper.GET, status = 403,
            stringContent = "{\"message\":\"403 Forbidden\"}")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/6", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"404 Not found\"}")
    void mapsRefusals(MockWebServer server) {
        var provider = provider(server);

        assertAll("the refused reads",
                () -> assertEquals(Result.Outcome.PERMISSION_DENIED,
                        provider.issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5)).outcome(),
                        "a 403 is a permission refusal"),
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 6)).outcome(),
                        "a 404 is not found"));
    }

    @Test
    @DisplayName("answers a malformed issue body with failed, never an exception")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "this is not json")
    void refusesMalformedBody(MockWebServer server) {
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5));

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a malformed body is a failure");
    }

    @Test
    @DisplayName("refuses an issue on another origin or of another forge")
    void refusesForeignIssues(MockWebServer server) {
        var provider = provider(server);

        assertAll("the refusals",
                () -> assertEquals(Result.Outcome.REJECTED,
                        provider.issueView(issue("https://gitlab.example.com", "gitlab", "cuioss", "pm", 5))
                                .outcome(),
                        "another origin is refused"),
                () -> assertEquals(Result.Outcome.REJECTED,
                        provider.issueView(issue(WEB_ORIGIN, "github", "cuioss", "pm", 5)).outcome(),
                        "another forge is refused"));
    }

    @Test
    @DisplayName("reads the identity of a subgroup project")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":7,\"scopes\":[\"api\"],\"active\":true}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":7,\"username\":\"alice\",\"state\":\"active\"}")
    @MockResponseConfig(path = "/projects/group%2Fsub%2Fproject", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"project_access\":{\"access_level\":40}}}")
    void readsSubgroupIdEntity(MockWebServer server) {
        var identity = provider(server)
                .whoami(new RepositoryRef("gitlab", WEB_ORIGIN, "group/sub", "project")).value().orElseThrow();

        assertAll("the subgroup identity",
                () -> assertEquals("alice", identity.login(), "the username is read"),
                () -> assertEquals(Map.of("project_access", "40"), identity.permissions(),
                        "the subgroup permissions are read"));
    }

    @Test
    @DisplayName("posts a comment on issue 9 of a subgroup project")
    @MockResponseConfig(path = "/projects/group%2Fsub%2Fproject/issues/9/notes", method = HttpMethodMapper.POST,
            status = 201, stringContent = "{\"id\":9001}")
    void postsSubgroupComment(MockWebServer server) {
        var comment = provider(server)
                .issueComment(issue(WEB_ORIGIN, "gitlab", "group/sub", "project", 9), "A comment")
                .value().orElseThrow();

        assertEquals("https://gitlab.com/group/sub/project/-/issues/9#note_9001", comment.url(),
                "the subgroup comment URL is derived");
    }

    @Test
    @DisplayName("refuses a comment on issue 5 of another forge before sending")
    void refusesCommentOfAnotherForge(MockWebServer server) {
        var result = provider(server)
                .issueComment(issue(WEB_ORIGIN, "github", "cuioss", "pm", 5), "A comment");

        assertAll("the comment of another forge",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(),
                        "a comment of another forge is refused"),
                () -> assertEquals(0, server.getRequestCount(),
                        "no request is sent for another forge"));
    }

    @Test
    @DisplayName("posts a comment and derives its URL")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/5/notes", method = HttpMethodMapper.POST, status = 201,
            stringContent = "{\"id\":9001}")
    void postsComment(MockWebServer server) {
        Result<IssueComment> result = provider(server)
                .issueComment(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5), "A comment");

        IssueComment comment = result.value().orElseThrow();
        assertAll("the posted comment",
                () -> assertEquals("9001", comment.id(), "the comment note id is read"),
                () -> assertEquals("https://gitlab.com/cuioss/pm/-/issues/5#note_9001", comment.url(),
                        "the comment URL is derived"));
    }

    @Test
    @DisplayName("maps a refused comment and refuses a blank body")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/5/notes", method = HttpMethodMapper.POST, status = 404,
            stringContent = "{\"message\":\"404 Not found\"}")
    void refusesComment(MockWebServer server) {
        var provider = provider(server);

        assertAll("the comment refusals",
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueComment(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5), "A comment").outcome(),
                        "a refused post is mapped"),
                () -> assertEquals(Result.Outcome.REJECTED,
                        provider.issueComment(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5), " ").outcome(),
                        "a blank body is refused"));
    }

    @Test
    @DisplayName("answers an anonymous read of a private issue with not found")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/9", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"404 Not found\"}")
    void refusesAnonymousPrivateIssue(MockWebServer server) throws Exception {
        URI baseUri = URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/");
        HttpClient anonymous = new HttpClient(HttpGenerators.endpointAt(baseUri).next(), TokenSource.NONE, Map.of());
        clients.add(anonymous);

        var result = new GitLabCiProvider(WEB_ORIGIN, anonymous)
                .issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 9));
        var request = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the anonymous issue 9 read",
                () -> assertNotNull(request, "an anonymous read sends one request"),
                () -> assertEquals(Result.Outcome.NOT_FOUND, result.outcome(),
                        "a private issue is not found without a credential"),
                () -> assertNull(authorization(request),
                        "an anonymous read sends no Authorization header"));
    }

    @Test
    @DisplayName("reads an issue by the issue endpoint, never a merge request")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/7", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"An issue\",\"description\":\"\",\"state\":\"opened\","
                    + "\"web_url\":\"https://gitlab.com/cuioss/pm/-/issues/7\"}")
    void readsIssueByTheIssueEndpoint(MockWebServer server) {
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 7));

        assertAll("the issue endpoint",
                () -> assertTrue(result.isOk(), "the issue is read and is no merge request"),
                () -> assertEquals("/projects/cuioss%2Fpm/issues/7", server.takeRequest().getTarget(),
                        "the issue endpoint is read, not the merge-request one"));
    }

    @Test
    @DisplayName("refuses a whoami of another origin before sending")
    void refusesWhoamiOfAnotherOrigin(MockWebServer server) {
        var other = new RepositoryRef("gitlab", "https://gitlab.example.com", "cuioss", "pm");

        var result = provider(server).whoami(other);

        assertEquals(Result.Outcome.REJECTED, result.outcome(), "another origin is refused before sending");
        assertEquals(0, server.getRequestCount(), "no request is sent for another origin");
    }

    @Test
    @DisplayName("normalises the origin it is bound to")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":7,\"scopes\":[\"api\"],"
                    + "\"active\":true,\"expires_at\":\"2026-12-01\"}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":7,\"username\":\"alice\",\"state\":\"active\"}")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{}")
    void normalisesOrigin(MockWebServer server) {
        var provider = new GitLabCiProvider("https://GitLab.com/", http(server));

        var result = provider.whoami(REPOSITORY);

        assertEquals(Result.Outcome.OK, result.outcome(), "a normalised origin matches the reference");
    }

    @Test
    @DisplayName("falls back to the token user id when the user body carries a blank id")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":7,\"scopes\":[\"api\"],\"active\":true}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":\"\",\"username\":\"alice\",\"state\":\"active\"}")
    void blankUserIdFallsBackToTokenUserId(MockWebServer server) {
        var identity = provider(server).whoami(null).value().orElseThrow();

        assertAll("the blank user id",
                () -> assertEquals("alice", identity.login(), "the login is read from the user"),
                () -> assertEquals("7", identity.userId(), "a blank user id falls back to the token id"));
    }

    @Test
    @DisplayName("answers a blank token user id with failed, never an exception")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":\"\",\"scopes\":[\"api\"],\"active\":true}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"username\":\"alice\",\"state\":\"active\"}")
    void answersBlankTokenUserIdWithFailed(MockWebServer server) {
        var result = provider(server).whoami(null);

        assertEquals(Result.Outcome.FAILED, result.outcome(),
                "a blank token user id is a returned failure");
    }

    @Test
    @DisplayName("prefers the user body id 8 over the token user id 7")
    @MockResponseConfig(path = "/personal_access_tokens/self", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":11,\"name\":\"cli\",\"user_id\":7,\"scopes\":[\"api\"],\"active\":true}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":8,\"username\":\"alice\",\"state\":\"active\"}")
    void userIdPrecedesTokenUserId(MockWebServer server) {
        var identity = provider(server).whoami(null).value().orElseThrow();

        assertEquals("8", identity.userId(), "the user body id wins over the token id");
    }

    @Test
    @DisplayName("treats a blank milestone title of issue 11 as no milestone")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/11", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"An issue\",\"description\":\"\",\"state\":\"opened\","
                    + "\"web_url\":\"https://gitlab.com/cuioss/pm/-/issues/11\",\"milestone\":{\"title\":\" \"}}")
    void blankMilestoneIsNoMilestone(MockWebServer server) {
        Issue read = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 11)).value().orElseThrow();

        assertNull(read.milestone(), "a blank milestone title is no milestone");
    }

    @Test
    @DisplayName("answers a missing issue 8 with not found although merge request 8 exists")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/8", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"404 Not found\"}")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/merge_requests/8", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"iid\":8}")
    void missingIssueIsNotFoundBesideMergeRequest(MockWebServer server) {
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 8));

        assertAll("the missing issue 8",
                () -> assertEquals(Result.Outcome.NOT_FOUND, result.outcome(),
                        "a missing issue 8 is not found"),
                () -> assertEquals(1, server.getRequestCount(),
                        "no merge-request path is requested for issue 8"),
                () -> assertEquals("/projects/cuioss%2Fpm/issues/8", server.takeRequest().getTarget(),
                        "the issue endpoint is the only request made"));
    }

    @Test
    @DisplayName("has the CI provider id")
    void hasProviderId(MockWebServer server) {
        assertEquals("gitlab", provider(server).id(), "the provider id is gitlab");
    }

    /** The generated fields of one merge request. */
    @Builder
    private record Fixture(String title, String body, String baseBranch, String headBranch, String headSha,
    List<String> paths, int targetProjectId) {
    }

    private static Fixture fixture() {
        return Fixture.builder().title(Generators.nonBlankStrings().next()).body(Generators.nonBlankStrings().next())
                .baseBranch(branch()).headBranch(branch()).headSha(Generators.letterStrings(40, 40).next())
                .paths(List.of(path(), path(), path())).targetProjectId(Generators.integers(1, 1_000_000).next())
                .build();
    }

    private static String branch() {
        return Generators.letterStrings(3, 20).next();
    }

    private static String path() {
        return Generators.letterStrings(2, 10).next() + "/" + Generators.letterStrings(2, 10).next() + ".java";
    }

    private static int forkId(Fixture fixture) {
        return fixture.targetProjectId() + Generators.integers(1, 1000).next();
    }

    private static String forkGroup() {
        return "fork" + Generators.letterStrings(3, 10).next();
    }

    /**
     * The body of {@code GET projects/{id}/merge_requests/{iid}}; the member names are GitLab's wire format.
     *
     * @param sourceProjectId the id of the source project, {@code null} for a source project GitLab no longer knows
     */
    private static Map<String, Object> mergeRequestBody(Fixture fixture, String state,
            @Nullable Integer sourceProjectId, boolean allowCollaboration) {
        var body = new LinkedHashMap<String, Object>();
        body.put("state", state);
        body.put("title", fixture.title());
        body.put("description", fixture.body());
        body.put("target_branch", fixture.baseBranch());
        body.put("source_branch", fixture.headBranch());
        body.put("sha", fixture.headSha());
        body.put("source_project_id", sourceProjectId);
        body.put("target_project_id", fixture.targetProjectId());
        body.put("allow_collaboration", allowCollaboration);
        return body;
    }

    private static List<Map<String, Object>> diffsBody(List<String> paths) {
        return paths.stream().<Map<String, Object>>map(path -> Map.of("old_path", path, "new_path", path)).toList();
    }

    /**
     * The body of {@code GET projects/{id}}; the member names and access levels are GitLab's wire format.
     *
     * @param projectLevel the project access level, {@code null} when the credential is no project member
     * @param groupLevel   the group access level, {@code null} when the credential is no group member
     */
    private static Map<String, Object> projectBody(String path, @Nullable Integer projectLevel,
            @Nullable Integer groupLevel) {
        var permissions = new LinkedHashMap<String, Object>();
        permissions.put("project_access", projectLevel == null ? null : Map.of("access_level", projectLevel));
        permissions.put("group_access", groupLevel == null ? null : Map.of("access_level", groupLevel));
        return Map.of("path_with_namespace", path, "permissions", permissions);
    }

    private static MockResponse json(int status, Object body) {
        return new MockResponse.Builder().code(status).addHeader("Content-Type", "application/json")
                .body(Json.write(body)).build();
    }

    private void prime(String path, MockResponse response) {
        primed.put(path, response);
    }

    private void primeMergeRequest(Fixture fixture, Map<String, Object> mergeRequest) {
        prime(MERGE_REQUEST_PATH, json(200, mergeRequest));
        prime(DIFFS_PATH, json(200, diffsBody(fixture.paths())));
    }

    private void primeProject(int id, Map<String, Object> project) {
        prime(PROJECTS_PATH + "/" + id, json(200, project));
    }

    /** Primes an open same-project merge request and its project with the given project access level. */
    private void primeSameProject(Fixture fixture, @Nullable Integer projectLevel) {
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", fixture.targetProjectId(), false));
        primeProject(fixture.targetProjectId(), projectBody("cuioss/pm", projectLevel, null));
    }

    @Test
    @DisplayName("prView reads an open same-project merge request with Developer access as writable")
    void readsOpenSameProjectMergeRequest(MockWebServer server) {
        var fixture = fixture();
        primeSameProject(fixture, 30);

        Result<PullRequest> result = provider(server).prView(MERGE_REQUEST);

        PullRequest read = result.value().orElseThrow();
        assertAll("the open merge request",
                () -> assertEquals(PullRequest.State.OPEN, read.state(), "an opened merge request is OPEN"),
                () -> assertEquals(fixture.title(), read.title(), "the title is read unchanged"),
                () -> assertEquals(fixture.body(), read.body(), "the description is read as the body"),
                () -> assertEquals(fixture.baseBranch(), read.baseBranch(), "the target branch is the base"),
                () -> assertEquals(fixture.headBranch(), read.headBranch(), "the source branch is the head"),
                () -> assertEquals(fixture.headSha(), read.headSha(), "the head commit is read"),
                () -> assertEquals(Optional.of(REPOSITORY), read.headRepository(),
                        "the head repository is the project"),
                () -> assertTrue(read.headWritable(), "Developer access on the project is writable"),
                () -> assertEquals(fixture.paths(), read.changedPaths(), "the changed paths keep their order"),
                () -> assertEquals(PullRequest.PathCompleteness.COMPLETE, read.pathCompleteness(),
                        "a list read to its end is COMPLETE"));
    }

    @ParameterizedTest(name = "state {0} is {1}")
    @DisplayName("prView returns every GitLab state as a value, a locked one as CLOSED")
    @CsvSource({"opened, OPEN", "closed, CLOSED", "merged, MERGED", "locked, CLOSED"})
    // the state names are GitLab's wire format and the contract's enum constants
    void mapsStates(String state, PullRequest.State expected, MockWebServer server) {
        var fixture = fixture();
        primeMergeRequest(fixture, mergeRequestBody(fixture, state, fixture.targetProjectId(), false));
        primeProject(fixture.targetProjectId(), projectBody("cuioss/pm", 40, null));

        Result<PullRequest> result = provider(server).prView(MERGE_REQUEST);

        assertAll("the state " + state,
                () -> assertEquals(Result.Outcome.OK, result.outcome(), "a non-open merge request is OK"),
                () -> assertEquals(expected, result.value().orElseThrow().state(),
                        "the GitLab state is mapped to the contract"));
    }

    @Test
    @DisplayName("prView reads a fork without collaboration as not writable, without a target read")
    void forkWithoutCollaborationIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        int forkId = forkId(fixture);
        String group = forkGroup();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", forkId, false));
        primeProject(forkId, projectBody(group + "/pm", 40, null));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertAll("the fork without collaboration",
                () -> assertFalse(read.headWritable(), "a fork without collaboration is not writable"),
                () -> assertEquals(Optional.of(new RepositoryRef("gitlab", WEB_ORIGIN, group, "pm")),
                        read.headRepository(), "the head repository is the fork"),
                () -> assertEquals(3, server.getRequestCount(), "no target project read is sent"));
    }

    @Test
    @DisplayName("prView reads a fork with collaboration and Developer group access on the target as writable")
    void forkWithCollaborationAndDeveloperAccessIsWritable(MockWebServer server) {
        var fixture = fixture();
        int forkId = forkId(fixture);
        String group = forkGroup();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", forkId, true));
        primeProject(forkId, projectBody("sub/" + group + "/pm", null, null));
        primeProject(fixture.targetProjectId(), projectBody("cuioss/pm", null, 30));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertAll("the fork with collaboration",
                () -> assertTrue(read.headWritable(), "collaboration and Developer access: writable"),
                () -> assertEquals(Optional.of(new RepositoryRef("gitlab", WEB_ORIGIN, "sub/" + group, "pm")),
                        read.headRepository(), "the head repository is the nested fork"));
    }

    @Test
    @DisplayName("prView reads a fork with collaboration but Reporter access on the target as not writable")
    void forkWithCollaborationBelowDeveloperIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        int forkId = forkId(fixture);
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", forkId, true));
        primeProject(forkId, projectBody(forkGroup() + "/pm", 40, null));
        primeProject(fixture.targetProjectId(), projectBody("cuioss/pm", 20, null));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertFalse(read.headWritable(), "Reporter access on the target is not writable");
    }

    @ParameterizedTest(name = "access level {0}")
    @DisplayName("prView reads a same-project head below Developer access as not writable")
    @ValueSource(ints = {5, 10, 20})
    // the access levels are GitLab's wire format: Minimal, Guest, Reporter
    void accessBelowDeveloperIsNotWritable(int level, MockWebServer server) {
        var fixture = fixture();
        primeSameProject(fixture, level);

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertFalse(read.headWritable(), "an access level below 30 is not writable");
    }

    @Test
    @DisplayName("prView reads a head as not writable when neither access level is present")
    void absentAccessLevelIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", fixture.targetProjectId(), false));
        primeProject(fixture.targetProjectId(), Map.of("path_with_namespace", "cuioss/pm"));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertFalse(read.headWritable(), "an absent access level reads as not writable");
    }

    @Test
    @DisplayName("prView reads a source project GitLab no longer names as absent and not writable")
    void unnamedSourceProjectIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", null, true));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertAll("the unnamed source project",
                () -> assertTrue(read.headRepository().isEmpty(), "an unnamed source project is absent"),
                () -> assertFalse(read.headWritable(), "an unnamed source project is not writable"),
                () -> assertEquals(2, server.getRequestCount(), "no project read is sent without a source"));
    }

    @Test
    @DisplayName("prView reads a deleted source project as absent and not writable")
    void deletedSourceProjectIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        int forkId = forkId(fixture);
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", forkId, true));
        prime(PROJECTS_PATH + "/" + forkId, json(404, Map.of("message", "404 Project Not Found")));

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertAll("the deleted source project",
                () -> assertTrue(read.headRepository().isEmpty(), "a deleted source project is absent"),
                () -> assertFalse(read.headWritable(), "a deleted source project is not writable"));
    }

    @Test
    @DisplayName("prView answers a source project path of one segment with FAILED")
    void refusesSourceProjectWithoutGroup(MockWebServer server) {
        var fixture = fixture();
        int forkId = forkId(fixture);
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", forkId, false));
        primeProject(forkId, projectBody(Generators.letterStrings(3, 10).next(), 40, null));

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a path without a group is a failure");
    }

    @Test
    @DisplayName("prView reports a diff list cut by the page cap as PAGE_CAP")
    void reportsPageCap(MockWebServer server) {
        var fixture = fixture();
        primeSameProject(fixture, 30);
        String next = "http://" + server.getHostName() + ":" + server.getPort() + DIFFS_PATH
                + "?per_page=100&page=2";
        prime(DIFFS_PATH, new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .addHeader("Link", "<" + next + ">; rel=\"next\"").body(Json.write(diffsBody(fixture.paths())))
                .build());

        PullRequest read = provider(server).prView(MERGE_REQUEST).value().orElseThrow();

        assertAll("the capped list",
                () -> assertEquals(PullRequest.PathCompleteness.PAGE_CAP, read.pathCompleteness(),
                        "a list cut by the page cap is PAGE_CAP"),
                () -> assertEquals(GitLabClient.MAX_PAGES * fixture.paths().size(), read.changedPaths().size(),
                        "every page up to the cap is kept"));
    }

    @ParameterizedTest(name = "HTTP {0} is {1}")
    @DisplayName("prView maps a refused merge-request read by its status")
    @CsvSource({"404, NOT_FOUND", "403, PERMISSION_DENIED", "500, FAILED"})
    // the statuses and outcome names are the HTTP and contract vocabulary
    void mapsRefusedRead(int status, Result.Outcome expected, MockWebServer server) {
        prime(MERGE_REQUEST_PATH, json(status, Map.of("message", Generators.letterStrings(5, 20).next())));

        var result = provider(server).prView(MERGE_REQUEST);

        assertAll("the refused read",
                () -> assertEquals(expected, result.outcome(), "the status is mapped to its outcome"),
                () -> assertEquals(1, server.getRequestCount(), "nothing is read after a refused read"));
    }

    @Test
    @DisplayName("prView passes a rate limit through with its reset instant")
    void reportsMergeRequestRateLimit(MockWebServer server) {
        int seconds = Generators.integers(30, 3600).next();
        // the Retry-After header is the HTTP wire format GitLab answers a rate limit with
        prime(MERGE_REQUEST_PATH, new MockResponse.Builder().code(429)
                .addHeader("Retry-After", String.valueOf(seconds)).body("{\"message\":\"Too many requests\"}").build());

        Instant before = Instant.now();
        var result = provider(server).prView(MERGE_REQUEST);
        Instant after = Instant.now();
        Instant reset = result.resetAt().orElseThrow();

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertFalse(reset.isBefore(before.plusSeconds(seconds)),
                        "the reset is no earlier than Retry-After"),
                () -> assertFalse(reset.isAfter(after.plusSeconds(seconds)),
                        "the reset is no later than Retry-After"));
    }

    @ParameterizedTest
    @DisplayName("prView answers a malformed merge-request body with FAILED, never an exception")
    @ValueSource(strings = {"this is not json", "{}", "{\"state\":\"opened\",\"title\":\"A title\"}",
            "{\"state\":\"draft\",\"title\":\"A title\",\"target_branch\":\"main\",\"source_branch\":\"topic\","
                    + "\"sha\":\"abc\",\"target_project_id\":1}"})
    // recorded-shape fixtures of an unusable merge-request body
    void refusesMalformedMergeRequest(String body, MockWebServer server) {
        prime(MERGE_REQUEST_PATH, new MockResponse.Builder().code(200).body(body).build());

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a malformed body is a failure");
    }

    @Test
    @DisplayName("prView answers a diffs page that is no JSON array with FAILED")
    void refusesMalformedDiffs(MockWebServer server) {
        var fixture = fixture();
        primeSameProject(fixture, 30);
        prime(DIFFS_PATH, json(200, Map.of("new_path", fixture.paths().getFirst())));

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a diffs page without an array fails");
    }

    @Test
    @DisplayName("prView maps a refused diffs read")
    void mapsRefusedDiffs(MockWebServer server) {
        var fixture = fixture();
        primeSameProject(fixture, 30);
        prime(DIFFS_PATH, json(403, Map.of("message", "403 Forbidden")));

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.PERMISSION_DENIED, result.outcome(), "a refused diffs read is mapped");
    }

    @Test
    @DisplayName("prView maps a refused project read")
    void mapsRefusedProjectRead(MockWebServer server) {
        var fixture = fixture();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", fixture.targetProjectId(), false));
        prime(PROJECTS_PATH + "/" + fixture.targetProjectId(), json(401, Map.of("message", "401 Unauthorized")));

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.AUTH_FAILED, result.outcome(), "a refused project read is mapped");
    }

    @Test
    @DisplayName("prView answers a project body without a path with FAILED")
    void refusesProjectWithoutPath(MockWebServer server) {
        var fixture = fixture();
        primeMergeRequest(fixture, mergeRequestBody(fixture, "opened", fixture.targetProjectId(), false));
        primeProject(fixture.targetProjectId(), Map.of("permissions", Map.of()));

        var result = provider(server).prView(MERGE_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a project without a path is a failure");
    }

    @ParameterizedTest(name = "{0} on {1}")
    @DisplayName("prView refuses a merge request of another origin or forge before sending")
    @CsvSource({"gitlab, https://gitlab.example.com", "github, https://gitlab.com", "forgejo, https://gitlab.com"})
    // the forge ids and origins are the contract's provider ids and web origins
    void refusesForeignMergeRequest(String forge, String origin, MockWebServer server) {
        var foreign = new PullRequestRef(new RepositoryRef(forge, origin, "cuioss", "pm"), 7);

        var result = provider(server).prView(foreign);

        assertAll("the foreign merge request",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a foreign merge request is refused"),
                () -> assertEquals(0, server.getRequestCount(), "no request is sent for it"));
    }
}
