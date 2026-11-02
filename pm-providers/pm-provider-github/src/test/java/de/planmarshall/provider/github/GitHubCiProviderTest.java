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
import static org.junit.jupiter.api.Assertions.assertNull;
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
@DisplayName("GitHubCiProvider")
class GitHubCiProviderTest {

    private static final String WEB_ORIGIN = "https://github.com";
    private static final RepositoryRef REPOSITORY = new RepositoryRef("github", WEB_ORIGIN, "cuioss", "pm");

    private final List<HttpClient> clients = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(HttpClient::close);
    }

    private HttpClient http(MockWebServer server, TokenSource tokens) {
        URI baseUri = URI.create("http://" + server.getHostName() + ":" + server.getPort() + "/");
        HttpClient client = new HttpClient(HttpGenerators.endpointAt(baseUri).next(), tokens,
                InstallationTokens.HEADERS);
        clients.add(client);
        return client;
    }

    private GitHubCiProvider tokenProvider(MockWebServer server, String token) {
        TokenSource tokens = () -> Optional.of(token);
        HttpClient http = http(server, tokens);
        return GitHubCiProvider.forToken(WEB_ORIGIN, http, tokens);
    }

    private IssueRef issue(String origin, String forge, int number) {
        return new IssueRef(new RepositoryRef(forge, origin, "cuioss", "pm"), number);
    }

    private GitHubCiProvider appProvider(MockWebServer server) {
        HttpClient readHttp = http(server, () -> Optional.of("ghs_installation"));
        HttpClient userHttp = http(server, () -> Optional.of("ghu_operator"));
        GitHubClient appClient = new GitHubClient(http(server, () -> Optional.of("jwt")),
                GitHubClient.GRAPHQL_PATH);
        return GitHubCiProvider.forApp(WEB_ORIGIN, readHttp, appClient, userHttp);
    }

    @Test
    @DisplayName("reads a personal access token identity with its scopes and expiry")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":583231}",
            headers = {"x-oauth-scopes=repo, read:org",
                    "github-authentication-token-expiration=2026-11-01 12:00:00 UTC"})
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true,\"pull\":true}}")
    void readsTokenIdentity(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        Result<Identity> result = provider.whoami(REPOSITORY);

        Identity identity = result.value().orElseThrow();
        assertAll("the token identity",
                () -> assertEquals("octocat", identity.login(), "the login is read from the user"),
                () -> assertEquals("583231", identity.userId(), "the provider user id is read"),
                () -> assertEquals(TokenKind.GITHUB_CLASSIC_PAT, identity.tokenKind(), "the token kind is a classic PAT"),
                () -> assertEquals(Map.of("push", "true", "pull", "true"), identity.permissions(),
                        "the repository permissions are read"),
                () -> assertEquals(List.of("repo", "read:org"), identity.scopes(), "the scopes are read from the header"),
                () -> assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                        "the expiry instant is read"));
    }

    @Test
    @DisplayName("names a fine-grained token from its prefix")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}")
    void namesFineGrainedToken(MockWebServer server) {
        var provider = tokenProvider(server, "github_pat_fine");

        var identity = provider.whoami(null).value().orElseThrow();

        assertAll("the fine-grained identity",
                () -> assertEquals(TokenKind.GITHUB_FINE_GRAINED_PAT, identity.tokenKind(), "the token kind is fine-grained"),
                () -> assertTrue(identity.permissions().isEmpty(), "no repository means no permissions"));
    }

    @Test
    @DisplayName("refuses a token whose prefix names no kind")
    void refusesUnknownPrefix(MockWebServer server) {
        var provider = tokenProvider(server, "opaque-token");

        var result = provider.whoami(REPOSITORY);

        assertAll("the unknown kind",
                () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "the kind is not guessed"),
                () -> assertTrue(result.detail().contains("prefix"), "the detail names the prefix"));
    }

    @Test
    @DisplayName("refuses whoami without a credential")
    void refusesWithoutCredential(MockWebServer server) {
        var provider = GitHubCiProvider.forToken(WEB_ORIGIN, http(server, TokenSource.NONE), TokenSource.NONE);

        assertEquals(Result.Outcome.AUTH_FAILED, provider.whoami(REPOSITORY).outcome(),
                "a missing credential is an auth failure");
    }

    @Test
    @DisplayName("reads both identities of a GitHub App")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"slug\":\"plan-marshall\",\"id\":123}")
    @MockResponseConfig(path = "/users/plan-marshall%5Bbot%5D", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":456}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"operator\"}")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"admin\":false,\"push\":true}}")
    void readsAppIdentity(MockWebServer server) {
        HttpClient readHttp = http(server, () -> Optional.of("ghs_installation"));
        HttpClient userHttp = http(server, () -> Optional.of("ghu_operator"));
        GitHubClient appClient = new GitHubClient(http(server, () -> Optional.of("jwt")),
                GitHubClient.GRAPHQL_PATH);
        var provider = GitHubCiProvider.forApp(WEB_ORIGIN, readHttp, appClient, userHttp);

        Identity identity = provider.whoami(REPOSITORY).value().orElseThrow();

        assertAll("the App identity",
                () -> assertEquals("plan-marshall[bot]", identity.login(), "the bot login is derived"),
                () -> assertEquals("456", identity.userId(), "the bot user id is read"),
                () -> assertEquals(TokenKind.GITHUB_APP, identity.tokenKind(), "the token kind is the App"),
                () -> assertEquals("operator", identity.operatorLogin(), "the operator login is read"),
                () -> assertEquals(Map.of("admin", "false", "push", "true"), identity.permissions(),
                        "the repository permissions are read"));
    }

    @Test
    @DisplayName("reads one issue with its structured fields")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"A title\",\"body\":\"A body\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/issues/5\","
                    + "\"labels\":[{\"name\":\"bug\"},{\"name\":\"docs\"}],"
                    + "\"milestone\":{\"title\":\"M1\"},\"assignees\":[{\"login\":\"octocat\"}]}")
    void readsIssue(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        Result<Issue> result = provider.issueView(issue(WEB_ORIGIN, "github", 5));

        Issue read = result.value().orElseThrow();
        assertAll("the issue fields",
                () -> assertEquals("A title", read.title(), "the issue title is read"),
                () -> assertEquals("A body", read.body(), "the issue body is read"),
                () -> assertEquals(List.of("bug", "docs"), read.labels(), "the issue labels are read"),
                () -> assertEquals("M1", read.milestone(), "the milestone is read"),
                () -> assertEquals(List.of("octocat"), read.assignees(), "the assignees are read"),
                () -> assertEquals("open", read.state(), "the issue state is read"),
                () -> assertEquals("https://github.com/cuioss/pm/issues/5", read.url(), "the issue web URL is read"));
    }

    @Test
    @DisplayName("answers a pull request number with a refusal")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/7", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"A pull request\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/pull/7\","
                    + "\"pull_request\":{\"url\":\"https://api.github.com/repos/cuioss/pm/pulls/7\"}}")
    void refusesPullRequest(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        var result = provider.issueView(issue(WEB_ORIGIN, "github", 7));

        assertAll("the pull request refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a pull request is refused"),
                () -> assertEquals("is a pull request", result.detail(), "the reason names the pull request"));
    }

    @Test
    @DisplayName("answers an anonymous read of a private issue with not found")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/9", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void refusesAnonymousPrivateIssue(MockWebServer server) {
        var provider = GitHubCiProvider.forToken(WEB_ORIGIN, http(server, TokenSource.NONE), TokenSource.NONE);

        assertEquals(Result.Outcome.NOT_FOUND, provider.issueView(issue(WEB_ORIGIN, "github", 9)).outcome(),
                "a private issue is not found without a credential");
    }

    @Test
    @DisplayName("passes a rate limit through with its reset instant")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 403,
            stringContent = "{\"message\":\"API rate limit exceeded\"}",
            headers = {"x-ratelimit-remaining=0", "x-ratelimit-reset=1791194400"})
    void reportsRateLimit(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        var result = provider.issueView(issue(WEB_ORIGIN, "github", 5));

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertEquals(1791194400L, result.resetAt().orElseThrow().getEpochSecond(),
                        "the reset instant is read"));
    }

    @Test
    @DisplayName("maps a forbidden and a missing issue")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 403,
            stringContent = "{\"message\":\"Forbidden\"}")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/6", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void mapsRefusals(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        assertAll("the refused reads",
                () -> assertEquals(Result.Outcome.PERMISSION_DENIED,
                        provider.issueView(issue(WEB_ORIGIN, "github", 5)).outcome(),
                        "a 403 is a permission refusal"),
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueView(issue(WEB_ORIGIN, "github", 6)).outcome(),
                        "a 404 is not found"));
    }

    @Test
    @DisplayName("answers a malformed issue body with failed, never an exception")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "this is not json")
    void refusesMalformedBody(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        var result = provider.issueView(issue(WEB_ORIGIN, "github", 5));

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a malformed body is a failure");
    }

    @Test
    @DisplayName("refuses an issue on another origin before sending")
    void refusesAnotherOrigin(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        var result = provider.issueView(issue("https://github.example.com", "github", 5));

        assertAll("the origin refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "another origin is refused"),
                () -> assertTrue(result.detail().contains("origin"), "the detail names the origin"));
    }

    @Test
    @DisplayName("refuses an issue of another forge before sending")
    void refusesAnotherForge(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        var result = provider.issueView(issue(WEB_ORIGIN, "forgejo", 5));

        assertAll("the forge refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "another forge is refused"),
                () -> assertTrue(result.detail().contains("forgejo"), "the detail names the forge"));
    }

    @Test
    @DisplayName("posts a comment and derives its URL")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 201,
            stringContent = "{\"id\":9001}")
    void postsComment(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        Result<IssueComment> result = provider.issueComment(issue(WEB_ORIGIN, "github", 5), "A comment");

        IssueComment comment = result.value().orElseThrow();
        assertAll("the posted comment",
                () -> assertEquals("9001", comment.id(), "the comment id is read"),
                () -> assertEquals("https://github.com/cuioss/pm/issues/5#issuecomment-9001", comment.url(),
                        "the comment URL is derived"));
    }

    @Test
    @DisplayName("maps a refused comment and refuses a blank body")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void refusesComment(MockWebServer server) {
        var provider = tokenProvider(server, "ghp_classic");

        assertAll("the comment refusals",
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueComment(issue(WEB_ORIGIN, "github", 5), "A comment").outcome(),
                        "a refused post is mapped"),
                () -> assertEquals(Result.Outcome.REJECTED,
                        provider.issueComment(issue(WEB_ORIGIN, "github", 5), " ").outcome(),
                        "a blank body is refused"));
    }

    @Test
    @DisplayName("maps a refused identity read and a body without a login")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 401,
            stringContent = "{\"message\":\"Bad credentials\"}")
    void mapsRefusedIdentity(MockWebServer server) {
        assertEquals(Result.Outcome.AUTH_FAILED, tokenProvider(server, "ghp_classic").whoami(null).outcome(),
                "a refused user read is an auth failure");
    }

    @Test
    @DisplayName("answers a user body without a login with failed")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":1}")
    void refusesUserWithoutLogin(MockWebServer server) {
        assertEquals(Result.Outcome.FAILED, tokenProvider(server, "ghp_classic").whoami(null).outcome(),
                "a user body without a login is a failure");
    }

    @Test
    @DisplayName("maps a refused repository permission read")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void mapsRefusedPermissions(MockWebServer server) {
        assertEquals(Result.Outcome.NOT_FOUND, tokenProvider(server, "ghp_classic").whoami(REPOSITORY).outcome(),
                "a refused permission read is mapped");
    }

    @Test
    @DisplayName("reads an ISO expiry header and skips an unreadable one")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"x-oauth-scopes=repo, ,read:org",
                    "github-authentication-token-expiration=2026-11-01T12:00:00Z"})
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{}")
    void readsExpiryVariants(MockWebServer server) {
        Identity identity = tokenProvider(server, "ghp_classic").whoami(REPOSITORY).value().orElseThrow();

        assertAll("the header variants",
                () -> assertEquals(List.of("repo", "read:org"), identity.scopes(),
                        "the empty scope is dropped"),
                () -> assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                        "an ISO expiry header is read"),
                () -> assertTrue(identity.permissions().isEmpty(), "a body without permissions is empty"));
    }

    @Test
    @DisplayName("drops an expiry header it cannot parse")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=not-a-date"})
    void dropsUnreadableExpiry(MockWebServer server) {
        var identity = tokenProvider(server, "ghp_classic").whoami(null).value().orElseThrow();

        assertNull(identity.expiresAt(), "an unreadable expiry is dropped");
    }

    @Test
    @DisplayName("maps a refused App read")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 401,
            stringContent = "{\"message\":\"Bad credentials\"}")
    void mapsRefusedApp(MockWebServer server) {
        var provider = appProvider(server);

        assertEquals(Result.Outcome.AUTH_FAILED, provider.whoami(null).outcome(),
                "a refused App JWT is an auth failure");
    }

    @Test
    @DisplayName("maps a refused bot user read and a body without an id")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"slug\":\"plan-marshall\",\"id\":123}")
    @MockResponseConfig(path = "/users/plan-marshall%5Bbot%5D", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void mapsRefusedBotUser(MockWebServer server) {
        assertEquals(Result.Outcome.NOT_FOUND, appProvider(server).whoami(null).outcome(),
                "a refused bot user read is mapped");
    }

    @Test
    @DisplayName("answers a bot user body without an id with failed")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"slug\":\"plan-marshall\",\"id\":123}")
    @MockResponseConfig(path = "/users/plan-marshall%5Bbot%5D", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{}")
    void refusesBotUserWithoutId(MockWebServer server) {
        assertEquals(Result.Outcome.FAILED, appProvider(server).whoami(null).outcome(),
                "a bot body without an id is a failure");
    }

    @Test
    @DisplayName("maps a refused operator read and a body without a login")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"slug\":\"plan-marshall\",\"id\":123}")
    @MockResponseConfig(path = "/users/plan-marshall%5Bbot%5D", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":456}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 401,
            stringContent = "{\"message\":\"Bad credentials\"}")
    void mapsRefusedOperator(MockWebServer server) {
        assertEquals(Result.Outcome.AUTH_FAILED, appProvider(server).whoami(null).outcome(),
                "a refused operator read is an auth failure");
    }

    @Test
    @DisplayName("answers an operator body without a login with failed")
    @MockResponseConfig(path = "/app", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"slug\":\"plan-marshall\",\"id\":123}")
    @MockResponseConfig(path = "/users/plan-marshall%5Bbot%5D", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":456}")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{}")
    void refusesOperatorWithoutLogin(MockWebServer server) {
        assertEquals(Result.Outcome.FAILED, appProvider(server).whoami(null).outcome(),
                "an operator body without a login is a failure");
    }

    @Test
    @DisplayName("normalises the origin it is bound to")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{}")
    void normalisesOrigin(MockWebServer server) {
        HttpClient http = http(server, () -> Optional.of("ghp_classic"));
        var provider = GitHubCiProvider.forToken("https://GitHub.com/", http, () -> Optional.of("ghp_classic"));

        var result = provider.whoami(REPOSITORY);

        assertEquals(Result.Outcome.OK, result.outcome(), "a normalised origin matches the reference");
    }

    @Test
    @DisplayName("refuses a whoami of another origin before sending")
    void refusesWhoamiOfAnotherOrigin(MockWebServer server) {
        var other = new RepositoryRef("github", "https://github.example.com", "cuioss", "pm");

        var result = tokenProvider(server, "ghp_classic").whoami(other);

        assertEquals(Result.Outcome.REJECTED, result.outcome(), "another origin is refused before sending");
        assertEquals(0, server.getRequestCount(), "no request is sent for another origin");
    }

    @Test
    @DisplayName("posts an App comment with the user access token")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 201,
            stringContent = "{\"id\":9001}")
    void postsAppCommentWithUserToken(MockWebServer server) {
        var provider = appProvider(server);

        var result = provider.issueComment(issue(WEB_ORIGIN, "github", 5), "A comment");

        assertAll("the App comment",
                () -> assertTrue(result.isOk(), "the comment is posted"),
                () -> assertEquals("Bearer ghu_operator",
                        server.takeRequest().getHeaders().get("Authorization"),
                        "the user access token authors the comment"));
    }

    @Test
    @DisplayName("has the CI provider id")
    void hasProviderId(MockWebServer server) {
        assertEquals("github", tokenProvider(server, "ghp_classic").id(), "the provider id is github");
    }
}
