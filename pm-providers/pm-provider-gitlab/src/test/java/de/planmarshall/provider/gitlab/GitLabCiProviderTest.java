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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import mockwebserver3.MockWebServer;

import de.planmarshall.provider.ci.Identity;
import de.planmarshall.provider.ci.Issue;
import de.planmarshall.provider.ci.IssueComment;
import de.planmarshall.provider.ci.IssueRef;
import de.planmarshall.provider.ci.RepositoryRef;
import de.planmarshall.provider.ci.TokenKind;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.TokenSource;
import de.planmarshall.provider.http.testing.generators.HttpGenerators;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.mockresponse.MockResponseConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableMockWebServer
@DisplayName("GitLabCiProvider")
class GitLabCiProviderTest {

    private static final String WEB_ORIGIN = "https://gitlab.com";
    private static final RepositoryRef REPOSITORY = new RepositoryRef("gitlab", WEB_ORIGIN, "cuioss", "pm");
    private static final String TOKEN = "glpat-token";

    private final List<HttpClient> clients = new CopyOnWriteArrayList<>();

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
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 5));

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertTrue(result.resetAt().isPresent(), "the reset instant is read"));
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
    void refusesAnonymousPrivateIssue(MockWebServer server) {
        URI baseUri = URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/");
        HttpClient anonymous = new HttpClient(HttpGenerators.endpointAt(baseUri).next(), TokenSource.NONE, Map.of());
        clients.add(anonymous);

        var result = new GitLabCiProvider(WEB_ORIGIN, anonymous)
                .issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 9));

        assertEquals(Result.Outcome.NOT_FOUND, result.outcome(),
                "a private issue is not found without a credential");
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
    @DisplayName("refuses a number that names a merge request")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/issues/8", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"404 Not found\"}")
    @MockResponseConfig(path = "/projects/cuioss%2Fpm/merge_requests/8", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"iid\":8}")
    void refusesMergeRequest(MockWebServer server) {
        var result = provider(server).issueView(issue(WEB_ORIGIN, "gitlab", "cuioss", "pm", 8));

        assertAll("the merge-request refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a merge request is refused"),
                () -> assertEquals("is a pull request", result.detail(), "the reason names the pull request"));
    }

    @Test
    @DisplayName("has the CI provider id")
    void hasProviderId(MockWebServer server) {
        assertEquals("gitlab", provider(server).id(), "the provider id is gitlab");
    }
}
