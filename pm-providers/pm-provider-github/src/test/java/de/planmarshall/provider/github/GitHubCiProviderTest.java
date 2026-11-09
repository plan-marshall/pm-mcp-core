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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

/**
 * The GitHub CI provider against a mock GitHub API.
 * <p>
 * Contract literals, kept by design: the provider id {@code github} and the forge origin {@code https://github.com};
 * the repository {@code cuioss/pm} with the issue and pull-request numbers, because the paths of every static
 * {@code @MockResponseConfig} name them; the token prefixes {@code ghp_}, {@code github_pat_}, {@code ghs_} and
 * {@code ghu_}, from which the provider derives the token kind (PLAN-V2-62); the response bodies and headers of the
 * static configurations, which are recorded GitHub wire shapes with the expiry formats GitHub sends; the URL grammar
 * of the derived web URLs; the state, outcome and status names of the contract and of HTTP. The titles, bodies,
 * branches, commits, paths, comment texts and fork owners of the dynamic reads are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("GitHubCiProvider")
class GitHubCiProviderTest {

    /** The forge origin the provider is bound to (contract: the github.com web origin). */
    private static final String WEB_ORIGIN = "https://github.com";
    /** The provider id (contract: the CI provider id of GitHub). */
    private static final String FORGE = "github";
    /** The owner of the repository the static mock paths name (contract: the PLAN-V2-62 fixture repository). */
    private static final String OWNER = "cuioss";
    /** The repository the static mock paths name (contract: the PLAN-V2-62 fixture repository). */
    private static final String NAME = "pm";
    /** A classic personal access token (contract: the {@code ghp_} prefix names the kind, PLAN-V2-62). */
    private static final String CLASSIC_TOKEN = "ghp_classic";
    /** A fine-grained personal access token (contract: the {@code github_pat_} prefix names the kind). */
    private static final String FINE_GRAINED_TOKEN = "github_pat_fine";
    /** A token whose prefix names no kind (contract: no known prefix). */
    private static final String OPAQUE_TOKEN = "opaque-token";
    /** An App installation token (contract: the {@code ghs_} prefix). */
    private static final String INSTALLATION_TOKEN = "ghs_installation";
    /** An App user access token (contract: the {@code ghu_} prefix). */
    private static final String USER_TOKEN = "ghu_operator";
    /** The App JWT the installation lookup carries; its value is opaque to the provider. */
    private static final String APP_JWT = "jwt";
    /** The prefix of an Authorization header carrying a token (contract: RFC 6750). */
    private static final String BEARER = "Bearer ";

    private static final RepositoryRef REPOSITORY = new RepositoryRef(FORGE, WEB_ORIGIN, OWNER, NAME);
    private static final PullRequestRef PULL_REQUEST = new PullRequestRef(REPOSITORY, 7);
    /** The repository reads of the GitHub REST API (contract: GitHub's URL grammar). */
    private static final String REPOSITORIES_PATH = "/repos";
    /** The repository read of {@link #REPOSITORY}, the permission read a test may prime. */
    private static final String REPOSITORY_PATH = REPOSITORIES_PATH + "/" + OWNER + "/" + NAME;
    /** The pull-request reads of {@link #PULL_REQUEST}, answered by {@link #getModuleDispatcher()}. */
    private static final String PULLS_PATH = REPOSITORY_PATH + "/pulls";
    /** The issue reads of {@link #REPOSITORY}, answered by {@link #getModuleDispatcher()} when a test primed one. */
    private static final String ISSUES_PATH = REPOSITORY_PATH + "/issues/";

    private final List<HttpClient> clients = new CopyOnWriteArrayList<>();
    private final AtomicReference<MockResponse> pullResponse = new AtomicReference<>();
    private final AtomicReference<MockResponse> filesResponse = new AtomicReference<>();
    private final AtomicReference<MockResponse> repositoryResponse = new AtomicReference<>();
    private final AtomicReference<MockResponse> issueResponse = new AtomicReference<>();

    /**
     * Answers the pull-request read and the changed-files read with the responses a test primed; the two reads
     * carry generated titles, bodies, branches and paths, which a static {@code @MockResponseConfig} cannot. The
     * repository's permission read is answered only when a test primed it, so a test can vary the permission between
     * two reads; every other path is left to the static configurations. The base URL is the parent of every path it
     * answers rather than one of them, because a dispatcher whose base URL equals the path of a static configuration
     * is refused as a conflict.
     */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return REPOSITORIES_PATH;
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                String path = request.getUrl().encodedPath();
                if (REPOSITORY_PATH.equals(path)) {
                    return Optional.ofNullable(repositoryResponse.get());
                }
                if (path.startsWith(ISSUES_PATH)) {
                    return Optional.ofNullable(issueResponse.get());
                }
                if (!path.startsWith(PULLS_PATH)) {
                    return Optional.empty();
                }
                boolean files = path.endsWith("/files");
                return Optional.ofNullable(files ? filesResponse.get() : pullResponse.get());
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
        return new IssueRef(new RepositoryRef(forge, origin, OWNER, NAME), number);
    }

    /** @return a generated comment text */
    private static String comment() {
        return Generators.nonBlankStrings().next();
    }

    private GitHubCiProvider appProvider(MockWebServer server) {
        HttpClient readHttp = http(server, () -> Optional.of(INSTALLATION_TOKEN));
        HttpClient userHttp = http(server, () -> Optional.of(USER_TOKEN));
        GitHubClient appClient = new GitHubClient(http(server, () -> Optional.of(APP_JWT)),
                GitHubClient.GRAPHQL_PATH);
        return GitHubCiProvider.forApp(WEB_ORIGIN, readHttp, appClient, userHttp);
    }

    private static String authorization(RecordedRequest request) {
        return request == null ? null : request.getHeaders().get("Authorization");
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
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        Result<Identity> result = provider.whoami(REPOSITORY);

        Identity identity = result.value();
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
        var provider = tokenProvider(server, FINE_GRAINED_TOKEN);

        var identity = provider.whoami(null).value();

        assertAll("the fine-grained identity",
                () -> assertEquals(TokenKind.GITHUB_FINE_GRAINED_PAT, identity.tokenKind(), "the token kind is fine-grained"),
                () -> assertTrue(identity.permissions().isEmpty(), "no repository means no permissions"));
    }

    @Test
    @DisplayName("refuses a token whose prefix names no kind")
    void refusesUnknownPrefix(MockWebServer server) {
        var provider = tokenProvider(server, OPAQUE_TOKEN);

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
    void readsAppIdentity(MockWebServer server) throws Exception {
        HttpClient readHttp = http(server, () -> Optional.of(INSTALLATION_TOKEN));
        HttpClient userHttp = http(server, () -> Optional.of(USER_TOKEN));
        GitHubClient appClient = new GitHubClient(http(server, () -> Optional.of(APP_JWT)),
                GitHubClient.GRAPHQL_PATH);
        var provider = GitHubCiProvider.forApp(WEB_ORIGIN, readHttp, appClient, userHttp);

        Identity identity = provider.whoami(REPOSITORY).value();
        var appRequest = server.takeRequest(5, TimeUnit.SECONDS);
        var botRequest = server.takeRequest(5, TimeUnit.SECONDS);
        var operatorRequest = server.takeRequest(5, TimeUnit.SECONDS);
        var permissionsRequest = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the App identity",
                () -> assertEquals("plan-marshall[bot]", identity.login(), "the bot login is derived"),
                () -> assertEquals("456", identity.userId(), "the bot user id is read"),
                () -> assertEquals(TokenKind.GITHUB_APP, identity.tokenKind(), "the token kind is the App"),
                () -> assertEquals("operator", identity.operatorLogin(), "the operator login is read"),
                () -> assertEquals(Map.of("admin", "false", "push", "true"), identity.permissions(),
                        "the repository permissions are read"),
                () -> assertEquals(BEARER + APP_JWT, authorization(appRequest),
                        "the installation lookup carries the JWT"),
                () -> assertEquals(BEARER + INSTALLATION_TOKEN, authorization(botRequest),
                        "the bot read carries the installation token"),
                () -> assertEquals(BEARER + USER_TOKEN, authorization(operatorRequest),
                        "the operator read carries the user token"),
                () -> assertEquals(BEARER + INSTALLATION_TOKEN, authorization(permissionsRequest),
                        "the permission read carries the installation token"));
    }

    @Test
    @DisplayName("sends the installation token to read issue 5")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"A title\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/issues/5\"}")
    void readsIssueWithInstallationToken(MockWebServer server) throws Exception {
        var provider = appProvider(server);

        provider.issueView(issue(WEB_ORIGIN, FORGE, 5));
        var request = server.takeRequest(5, TimeUnit.SECONDS);

        assertEquals(BEARER + INSTALLATION_TOKEN, authorization(request),
                "the issue read carries the installation token");
    }

    @Test
    @DisplayName("reads one issue with its structured fields")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"A title\",\"body\":\"A body\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/issues/5\","
                    + "\"labels\":[{\"name\":\"bug\"},{\"name\":\"docs\"}],"
                    + "\"milestone\":{\"title\":\"M1\"},\"assignees\":[{\"login\":\"octocat\"}]}")
    void readsIssue(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        Result<Issue> result = provider.issueView(issue(WEB_ORIGIN, FORGE, 5));

        Issue read = result.value();
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
    @DisplayName("treats a blank milestone title of issue 11 as no milestone")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/11", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"An issue\",\"body\":\"\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/issues/11\",\"milestone\":{\"title\":\" \"}}")
    void blankMilestoneIsNoMilestone(MockWebServer server) {
        Issue read = tokenProvider(server, CLASSIC_TOKEN)
                .issueView(issue(WEB_ORIGIN, FORGE, 11)).value();

        assertNull(read.milestone(), "a blank milestone title is no milestone");
    }

    @Test
    @DisplayName("reads an expiry header with a numeric offset +0000")
    @SuppressWarnings("java:S5976")
    // three wire forms; a static @MockResponseConfig cannot vary a parameter
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=2026-11-01 12:00:00 +0000"})
    void readsOffsetExpiryNumeric(MockWebServer server) {
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                "a numeric offset expiry is the same instant");
    }

    @Test
    @DisplayName("reads an expiry header with a colon offset +00:00")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=2026-11-01 12:00:00 +00:00"})
    void readsOffsetExpiryColon(MockWebServer server) {
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                "a colon offset expiry is the same instant");
    }

    @Test
    @DisplayName("reads an ISO expiry header with an offset")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=2026-11-01T14:00:00+02:00"})
    void readsIsoOffsetExpiry(MockWebServer server) {
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                "an ISO offset expiry is the same instant");
    }

    @Test
    @DisplayName("reads a T-separated expiry header with a numeric offset +0200")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=2026-11-01T14:00:00+0200"})
    void readsIsoNumericOffsetExpiry(MockWebServer server) {
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), identity.expiresAt(),
                "a T-separated numeric offset expiry is the same instant");
    }

    @Test
    @DisplayName("drops an offset expiry with an invalid day of month")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}",
            headers = {"github-authentication-token-expiration=2026-02-30 12:00:00 +0000"})
    void dropsInvalidOffsetExpiry(MockWebServer server) {
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

        assertNull(identity.expiresAt(), "an invalid day of month is no expiry");
    }

    @Test
    @DisplayName("answers a pull request number with a refusal")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/7", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"title\":\"A pull request\",\"state\":\"open\","
                    + "\"html_url\":\"https://github.com/cuioss/pm/pull/7\","
                    + "\"pull_request\":{\"url\":\"https://api.github.com/repos/cuioss/pm/pulls/7\"}}")
    void refusesPullRequest(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        var result = provider.issueView(issue(WEB_ORIGIN, FORGE, 7));

        assertAll("the pull request refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a pull request is refused"),
                () -> assertEquals("is a pull request", result.detail(), "the reason names the pull request"));
    }

    @Test
    @DisplayName("answers an anonymous read of a private issue with not found")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/9", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void refusesAnonymousPrivateIssue(MockWebServer server) throws Exception {
        var provider = GitHubCiProvider.forToken(WEB_ORIGIN, http(server, TokenSource.NONE), TokenSource.NONE);

        var result = provider.issueView(issue(WEB_ORIGIN, FORGE, 9));
        var request = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the anonymous issue 9 read",
                () -> assertNotNull(request, "an anonymous read sends one request"),
                () -> assertEquals(Result.Outcome.NOT_FOUND, result.outcome(),
                        "a private issue is not found without a credential"),
                () -> assertNull(authorization(request),
                        "an anonymous read sends no Authorization header"));
    }

    @Test
    @DisplayName("passes a rate limit through with its reset instant")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 403,
            stringContent = "{\"message\":\"API rate limit exceeded\"}",
            headers = {"x-ratelimit-remaining=0", "x-ratelimit-reset=1791194400"})
    void reportsRateLimit(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        var result = provider.issueView(issue(WEB_ORIGIN, FORGE, 5));

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertEquals(1791194400L, result.resetAt().getEpochSecond(),
                        "the reset instant is read"));
    }

    @Test
    @DisplayName("maps a forbidden and a missing issue")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 403,
            stringContent = "{\"message\":\"Forbidden\"}")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/6", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void mapsRefusals(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        assertAll("the refused reads",
                () -> assertEquals(Result.Outcome.PERMISSION_DENIED,
                        provider.issueView(issue(WEB_ORIGIN, FORGE, 5)).outcome(),
                        "a 403 is a permission refusal"),
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueView(issue(WEB_ORIGIN, FORGE, 6)).outcome(),
                        "a 404 reads as not found"));
    }

    @Test
    @DisplayName("answers a malformed issue body with failed, never an exception")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5", method = HttpMethodMapper.GET, status = 200,
            stringContent = "this is not json")
    void refusesMalformedBody(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        var result = provider.issueView(issue(WEB_ORIGIN, FORGE, 5));

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a malformed body is a failure");
    }

    @Test
    @DisplayName("refuses an issue on another origin before sending")
    void refusesAnotherOrigin(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        var result = provider.issueView(issue("https://github.example.com", FORGE, 5));

        assertAll("the origin refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "another origin is refused"),
                () -> assertTrue(result.detail().contains("origin"), "the detail names the origin"));
    }

    @Test
    @DisplayName("refuses an issue of another forge before sending")
    void refusesAnotherForge(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);

        var result = provider.issueView(issue(WEB_ORIGIN, "forgejo", 5));

        assertAll("the forge refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "another forge is refused"),
                () -> assertTrue(result.detail().contains("forgejo"), "the detail names the forge"));
    }

    @Test
    @DisplayName("refuses a comment on issue 5 of another origin before sending")
    void refusesCommentOfAnotherOrigin(MockWebServer server) {
        var result = tokenProvider(server, CLASSIC_TOKEN)
                .issueComment(issue("https://github.example.com", FORGE, 5), comment());

        assertAll("the comment of another origin",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(),
                        "a comment of another origin is refused"),
                () -> assertEquals(0, server.getRequestCount(),
                        "no request is sent for another origin"));
    }

    @Test
    @DisplayName("posts a comment and derives its URL")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 201,
            stringContent = "{\"id\":9001}")
    void postsComment(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);
        issueResponse.set(issueBody(5, false));

        Result<IssueComment> result = provider.issueComment(issue(WEB_ORIGIN, FORGE, 5), comment());

        IssueComment comment = result.value();
        assertAll("the posted comment",
                () -> assertEquals("9001", comment.id(), "the comment id is read"),
                () -> assertEquals("https://github.com/cuioss/pm/issues/5#issuecomment-9001", comment.url(),
                        "the comment URL is derived"),
                () -> assertEquals(2, server.getRequestCount(), "the issue read precedes the post"));
    }

    @Test
    @DisplayName("refuses a comment on a pull-request number without posting (the pull_request member is the flag)")
    void refusesCommentOnPullRequest(MockWebServer server) throws Exception {
        var provider = tokenProvider(server, CLASSIC_TOKEN);
        issueResponse.set(issueBody(7, true));

        var result = provider.issueComment(issue(WEB_ORIGIN, FORGE, 7), comment());
        var request = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the pull-request comment refusal",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a pull-request number is refused"),
                () -> assertEquals("is a pull request", result.detail(), "the reason names the pull request"),
                () -> assertEquals("/repos/cuioss/pm/issues/7", request == null ? null : request.getTarget(),
                        "the issue is read before the refusal"),
                () -> assertEquals(1, server.getRequestCount(), "no comment is posted for a pull request"));
    }

    @ParameterizedTest(name = "a {0} of the guard read answers the issueView outcome")
    @CsvSource({"404, NOT_FOUND", "403, PERMISSION_DENIED", "429, RATE_LIMITED", "500, FAILED"})
    @DisplayName("propagates a failed guard read and sends no post (the status mapping is the contract)")
    void propagatesFailedGuardRead(int status, Result.Outcome expected, MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);
        issueResponse.set(new MockResponse.Builder().code(status).addHeader("Content-Type", "application/json")
                .body("{\"message\":\"refused\"}").build());

        var result = provider.issueComment(issue(WEB_ORIGIN, FORGE, 7), comment());

        assertAll("the failed guard read",
                () -> assertEquals(expected, result.outcome(), "the read's outcome is propagated"),
                () -> assertEquals(1, server.getRequestCount(), "no post is sent after a failed read"));
    }

    @Test
    @DisplayName("maps a refused comment and refuses a blank body")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void refusesComment(MockWebServer server) {
        var provider = tokenProvider(server, CLASSIC_TOKEN);
        issueResponse.set(issueBody(5, false));

        assertAll("the comment refusals",
                () -> assertEquals(Result.Outcome.NOT_FOUND,
                        provider.issueComment(issue(WEB_ORIGIN, FORGE, 5), comment()).outcome(),
                        "a refused post is mapped"),
                () -> assertEquals(Result.Outcome.REJECTED,
                        provider.issueComment(issue(WEB_ORIGIN, FORGE, 5), " ").outcome(),
                        "a blank body is refused"));
    }

    @Test
    @DisplayName("maps a refused identity read and a body without a login")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 401,
            stringContent = "{\"message\":\"Bad credentials\"}")
    void mapsRefusedIdentity(MockWebServer server) {
        assertEquals(Result.Outcome.AUTH_FAILED, tokenProvider(server, CLASSIC_TOKEN).whoami(null).outcome(),
                "a refused user read is an auth failure");
    }

    @Test
    @DisplayName("answers a user body without a login with failed")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"id\":1}")
    void refusesUserWithoutLogin(MockWebServer server) {
        assertEquals(Result.Outcome.FAILED, tokenProvider(server, CLASSIC_TOKEN).whoami(null).outcome(),
                "a user body without a login is a failure");
    }

    @Test
    @DisplayName("maps a refused repository permission read")
    @MockResponseConfig(path = "/user", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"login\":\"octocat\",\"id\":1}")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void mapsRefusedPermissions(MockWebServer server) {
        assertEquals(Result.Outcome.NOT_FOUND, tokenProvider(server, CLASSIC_TOKEN).whoami(REPOSITORY).outcome(),
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
        Identity identity = tokenProvider(server, CLASSIC_TOKEN).whoami(REPOSITORY).value();

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
        var identity = tokenProvider(server, CLASSIC_TOKEN).whoami(null).value();

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
        HttpClient http = http(server, () -> Optional.of(CLASSIC_TOKEN));
        // the same origin in another spelling: upper-case host and a trailing slash
        var provider = GitHubCiProvider.forToken("https://GitHub.com/", http, () -> Optional.of(CLASSIC_TOKEN));

        var result = provider.whoami(REPOSITORY);

        assertEquals(Result.Outcome.OK, result.outcome(), "a normalised origin matches the reference");
    }

    @Test
    @DisplayName("refuses a whoami of another origin before sending")
    void refusesWhoamiOfAnotherOrigin(MockWebServer server) {
        // an origin other than the one the provider is bound to
        var other = new RepositoryRef(FORGE, "https://github.example.com", OWNER, NAME);

        var result = tokenProvider(server, CLASSIC_TOKEN).whoami(other);

        assertAll("the whoami of another origin",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(),
                        "another origin is refused before sending"),
                () -> assertEquals(0, server.getRequestCount(), "no request is sent for another origin"));
    }

    @Test
    @DisplayName("posts an App comment with the user access token")
    @MockResponseConfig(path = "/repos/cuioss/pm/issues/5/comments", method = HttpMethodMapper.POST, status = 201,
            stringContent = "{\"id\":9001}")
    void postsAppCommentWithUserToken(MockWebServer server) throws Exception {
        var provider = appProvider(server);
        issueResponse.set(issueBody(5, false));

        var result = provider.issueComment(issue(WEB_ORIGIN, FORGE, 5), comment());
        var readRequest = server.takeRequest(5, TimeUnit.SECONDS);
        var commentRequest = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the App comment",
                () -> assertNotNull(readRequest, "the guard read reaches the server"),
                () -> assertNotNull(commentRequest, "the comment request reaches the server"),
                () -> assertTrue(result.isOk(), "the comment is posted"),
                () -> assertEquals(BEARER + USER_TOKEN, authorization(readRequest),
                        "the user access token reads the issue"),
                () -> assertEquals(BEARER + USER_TOKEN, authorization(commentRequest),
                        "the user access token authors the comment"));
    }

    /** The generated fields of one pull request. */
    @Builder
    private record Fixture(String title, String body, String baseBranch, String headBranch, String headSha,
    List<String> paths) {
    }

    private static Fixture fixture() {
        return Fixture.builder().title(Generators.nonBlankStrings().next()).body(Generators.nonBlankStrings().next())
                .baseBranch(branch()).headBranch(branch()).headSha(Generators.letterStrings(40, 40).next())
                .paths(List.of(path(), path(), path())).build();
    }

    private static String branch() {
        return Generators.letterStrings(3, 20).next();
    }

    private static String path() {
        return Generators.letterStrings(2, 10).next() + "/" + Generators.letterStrings(2, 10).next() + ".java";
    }

    /** @return a generated fork owner, from letters {@link #OWNER} does not contain, so it never names the base */
    private static String forkOwner() {
        return Generators.strings("abdefghjklmnpqrtvwxyz", 3, 12).next();
    }

    /** @return the body of a repository read with that push permission; the member names are GitHub's wire format */
    private static MockResponse permissions(boolean push) {
        return json(200, Map.of("permissions", Map.of("push", push, "pull", true)));
    }

    /**
     * The body of {@code GET repos/{owner}/{repo}/pulls/{n}}; the member names are GitHub's wire format.
     *
     * @param headOwner the owner of the head repository, {@code null} for a deleted head repository
     */
    private static Map<String, Object> pullBody(Fixture fixture, String state, boolean merged,
            @Nullable String headOwner, boolean maintainerCanModify, int changedFiles) {
        return pullBody(fixture, state, merged, headOwner, NAME, maintainerCanModify, changedFiles);
    }

    /**
     * The same body with the head repository named by owner and name, and the changed-file count as the JSON value a
     * test puts on the wire, which is a number for GitHub and anything for a malformed response.
     */
    private static Map<String, Object> pullBody(Fixture fixture, String state, boolean merged,
            @Nullable String headOwner, String headName, boolean maintainerCanModify, Object changedFiles) {
        var head = new LinkedHashMap<String, Object>();
        head.put("ref", fixture.headBranch());
        head.put("sha", fixture.headSha());
        head.put("repo", headOwner == null ? null
                : Map.of("name", headName, "owner", Map.of("login", headOwner)));
        var body = new LinkedHashMap<String, Object>();
        body.put("state", state);
        body.put("merged", merged);
        body.put("title", fixture.title());
        body.put("body", fixture.body());
        body.put("base", Map.of("ref", fixture.baseBranch(), "repo", Map.of("full_name", OWNER + "/" + NAME)));
        body.put("head", head);
        body.put("maintainer_can_modify", maintainerCanModify);
        body.put("changed_files", changedFiles);
        return body;
    }

    private static List<Map<String, Object>> filesBody(List<String> paths) {
        return paths.stream().<Map<String, Object>>map(path -> Map.of("filename", path)).toList();
    }

    private static MockResponse json(int status, Object body) {
        return new MockResponse.Builder().code(status).addHeader("Content-Type", "application/json")
                .body(Json.write(body)).build();
    }

    /**
     * The body of {@code GET repos/{owner}/{repo}/issues/{n}}; the member names are GitHub's wire format and the
     * {@code pull_request} member is the flag the guard reads.
     */
    private static MockResponse issueBody(int number, boolean pullRequest) {
        var body = new LinkedHashMap<String, Object>();
        body.put("title", "An issue");
        body.put("state", "open");
        body.put("html_url", "https://github.com/" + OWNER + "/" + NAME + "/issues/" + number);
        if (pullRequest) {
            body.put("pull_request", Map.of("url", "https://api.github.com/repos/" + OWNER + "/" + NAME
                    + "/pulls/" + number));
        }
        return json(200, body);
    }

    private void prime(Fixture fixture, Map<String, Object> pull) {
        pullResponse.set(json(200, pull));
        filesResponse.set(json(200, filesBody(fixture.paths())));
    }

    private void primeOpen(Fixture fixture, @Nullable String headOwner, boolean maintainerCanModify) {
        prime(fixture, pullBody(fixture, "open", false, headOwner, maintainerCanModify, fixture.paths().size()));
    }

    @Test
    @DisplayName("prView reads an open pull request of the base repository as writable")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true,\"pull\":true}}")
    void readsOpenPullRequest(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);

        Result<PullRequest> result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        PullRequest read = result.value();
        assertAll("the open pull request",
                () -> assertEquals(PullRequest.State.OPEN, read.state(), "an open pull request is OPEN"),
                () -> assertEquals(fixture.title(), read.title(), "the title is read unchanged"),
                () -> assertEquals(fixture.body(), read.body(), "the body is read unchanged"),
                () -> assertEquals(fixture.baseBranch(), read.baseBranch(), "the base branch is read"),
                () -> assertEquals(fixture.headBranch(), read.headBranch(), "the head branch is read"),
                () -> assertEquals(fixture.headSha(), read.headSha(), "the head commit is read"),
                () -> assertEquals(Optional.of(REPOSITORY), read.headRepository(),
                        "the head repository is the base repository"),
                () -> assertTrue(read.headWritable(), "a pushable base-repository head is writable"),
                () -> assertEquals(fixture.paths(), read.changedPaths(), "the changed paths keep their order"),
                () -> assertEquals(PullRequest.PathCompleteness.COMPLETE, read.pathCompleteness(),
                        "a full list of every changed file is COMPLETE"));
    }

    @ParameterizedTest(name = "state {0} with merged {1} is {2}")
    @DisplayName("prView returns every state as a value, never as a failure")
    @CsvSource({"open, false, OPEN", "closed, false, CLOSED", "closed, true, MERGED"})
    // the state names are GitHub's wire format and the contract's enum constants
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void mapsStates(String state, boolean merged, PullRequest.State expected, MockWebServer server) {
        var fixture = fixture();
        prime(fixture, pullBody(fixture, state, merged, "cuioss", false, fixture.paths().size()));

        Result<PullRequest> result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the state " + state,
                () -> assertEquals(Result.Outcome.OK, result.outcome(), "a non-open pull request is OK"),
                () -> assertEquals(expected, result.value().state(),
                        "the state is mapped from state and merged"));
    }

    @Test
    @DisplayName("prView reads a fork head with maintainer edits but no push on the base as not writable, "
            + "and the same fork with push as writable")
    void forkWithoutPushIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        String owner = forkOwner();
        primeOpen(fixture, owner, true);
        repositoryResponse.set(permissions(false));

        PullRequest withoutPush = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();
        repositoryResponse.set(permissions(true));
        PullRequest withPush = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertAll("the fork without and with push",
                () -> assertFalse(withoutPush.headWritable(), "a fork without push on the base is not writable"),
                () -> assertTrue(withPush.headWritable(), "the same fork with push on the base is writable"),
                () -> assertEquals(Optional.of(new RepositoryRef(FORGE, WEB_ORIGIN, owner, NAME)),
                        withoutPush.headRepository(), "the head repository is the fork"));
    }

    @Test
    @DisplayName("prView reads a fork head with maintainer edits and push on the base as writable")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void forkWithMaintainerEditsAndPushIsWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, forkOwner(), true);

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertTrue(read.headWritable(), "maintainer edits and push on the base: writable");
    }

    @Test
    @DisplayName("prView reads a fork head without maintainer edits as not writable, without a permission read")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void forkWithoutMaintainerEditsIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, forkOwner(), false);

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertAll("the fork without maintainer edits",
                () -> assertFalse(read.headWritable(), "a fork without maintainer edits is not writable"),
                () -> assertEquals(2, server.getRequestCount(), "no permission read is sent for the fork"));
    }

    @Test
    @DisplayName("prView reads a deleted head repository as absent and not writable")
    void deletedHeadRepositoryIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, null, true);

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertAll("the deleted head repository",
                () -> assertTrue(read.headRepository().isEmpty(), "a deleted head repository is absent"),
                () -> assertFalse(read.headWritable(), "a deleted head repository is not writable"),
                () -> assertEquals(2, server.getRequestCount(), "no permission read is sent without a head"));
    }

    @Test
    @DisplayName("prView reads a head as not writable when the permission member is absent")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"pull\":true}}")
    void absentPushMemberIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertFalse(read.headWritable(), "an absent push member reads as not writable");
    }

    @Test
    @DisplayName("prView reports a changed-file list cut by the page cap as PAGE_CAP")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void reportsPageCap(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);
        String next = "http://" + server.getHostName() + ":" + server.getPort() + PULLS_PATH
                + "/7/files?per_page=100&page=2";
        filesResponse.set(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .addHeader("Link", "<" + next + ">; rel=\"next\"").body(Json.write(filesBody(fixture.paths())))
                .build());

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertAll("the capped list",
                () -> assertEquals(PullRequest.PathCompleteness.PAGE_CAP, read.pathCompleteness(),
                        "a list cut by the page cap is PAGE_CAP"),
                () -> assertEquals(GitHubClient.MAX_FILE_PAGES * fixture.paths().size(),
                        read.changedPaths().size(), "every page up to the cap is kept"));
    }

    @Test
    @DisplayName("prView reports a complete list shorter than changed_files as PROVIDER_RESULT_CAP")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void reportsProviderResultCap(MockWebServer server) {
        var fixture = fixture();
        int changedFiles = fixture.paths().size() + Generators.integers(1, 5000).next();
        prime(fixture, pullBody(fixture, "open", false, OWNER, false, changedFiles));

        PullRequest read = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST).value();

        assertAll("the provider-capped list",
                () -> assertEquals(PullRequest.PathCompleteness.PROVIDER_RESULT_CAP, read.pathCompleteness(),
                        "a short complete list is PROVIDER_RESULT_CAP"),
                () -> assertEquals(fixture.paths(), read.changedPaths(), "the listed paths are kept"));
    }

    @ParameterizedTest(name = "HTTP {0} is {1}")
    @DisplayName("prView maps a refused pull-request read by its status")
    @CsvSource({"404, NOT_FOUND", "403, PERMISSION_DENIED", "500, FAILED"})
    // the statuses and outcome names are the HTTP and contract vocabulary
    void mapsRefusedRead(int status, Result.Outcome expected, MockWebServer server) {
        pullResponse.set(json(status, Map.of("message", Generators.letterStrings(5, 20).next())));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the refused read",
                () -> assertEquals(expected, result.outcome(), "the status is mapped to its outcome"),
                () -> assertEquals(1, server.getRequestCount(), "nothing is read after a refused read"));
    }

    @Test
    @DisplayName("prView passes a rate limit through with its reset instant")
    void reportsPullRequestRateLimit(MockWebServer server) {
        long reset = Generators.longs(1_800_000_000L, 1_900_000_000L).next();
        // the rate-limit header names are GitHub's wire format
        pullResponse.set(new MockResponse.Builder().code(403).addHeader("x-ratelimit-remaining", "0")
                .addHeader("x-ratelimit-reset", String.valueOf(reset))
                .body("{\"message\":\"API rate limit exceeded\"}").build());

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the rate limit",
                () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "the limit is an outcome"),
                () -> assertEquals(reset, result.resetAt().getEpochSecond(),
                        "the reset instant is read from the header"));
    }

    @ParameterizedTest
    @DisplayName("prView answers a malformed pull-request body with FAILED, never an exception")
    @ValueSource(strings = {"this is not json", "{}", "{\"state\":\"open\",\"title\":\"A title\"}",
            "{\"state\":\"draft\",\"title\":\"A title\",\"base\":{\"ref\":\"main\"},"
                    + "\"head\":{\"ref\":\"topic\",\"sha\":\"abc\"},\"changed_files\":1}"})
    // recorded-shape fixtures of an unusable pull-request body
    void refusesMalformedPullRequest(String body, MockWebServer server) {
        pullResponse.set(new MockResponse.Builder().code(200).body(body).build());

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a malformed body is a failure");
    }

    @Test
    @DisplayName("prView answers a changed-files page that is no JSON array with FAILED")
    void refusesMalformedFiles(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);
        filesResponse.set(json(200, Map.of("filename", fixture.paths().getFirst())));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertEquals(Result.Outcome.FAILED, result.outcome(), "a files page without an array fails");
    }

    @Test
    @DisplayName("prView answers a changed-files entry without a filename with FAILED, not PROVIDER_RESULT_CAP")
    void refusesFilesEntryWithoutFilename(MockWebServer server) {
        var fixture = fixture();
        prime(fixture, pullBody(fixture, "open", false, OWNER, false, fixture.paths().size() + 1));
        var entries = new ArrayList<Object>(filesBody(fixture.paths()));
        entries.add(Generators.integers(1, fixture.paths().size()).next(),
                Map.of("status", Generators.letterStrings(3, 10).next()));
        filesResponse.set(json(200, entries));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the entry without a filename",
                () -> assertEquals(Result.Outcome.FAILED, result.outcome(),
                        "an entry without a filename is a failure"),
                // the reason text is the contract of the failure
                () -> assertTrue(result.detail().contains("a changed-files entry carries no usable filename"),
                        "the reason names the entry without a filename"),
                () -> assertEquals(2, server.getRequestCount(), "no permission read follows the failed read"));
    }

    @Test
    @DisplayName("prView maps a refused changed-files read")
    void mapsRefusedFiles(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);
        filesResponse.set(json(403, Map.of("message", Generators.letterStrings(5, 20).next())));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertEquals(Result.Outcome.PERMISSION_DENIED, result.outcome(), "a refused files read is mapped");
    }

    @Test
    @DisplayName("prView maps a refused permission read")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 401,
            stringContent = "{\"message\":\"Bad credentials\"}")
    void mapsRefusedPermissionRead(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertEquals(Result.Outcome.AUTH_FAILED, result.outcome(), "a refused permission read is mapped");
    }

    @Test
    @DisplayName("prView reads a head as not writable when the App's user token gets 404 on the permission read")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void notFoundPermissionReadIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);

        var result = appProvider(server).prView(PULL_REQUEST);

        PullRequest read = result.value();
        assertAll("the pull request with a hidden repository",
                () -> assertEquals(Result.Outcome.OK, result.outcome(), "a 404 permission read is no failure"),
                () -> assertFalse(read.headWritable(), "a hidden repository is not writable"),
                () -> assertEquals(PullRequest.State.OPEN, read.state(), "the state of the pull request is kept"),
                () -> assertEquals(fixture.paths(), read.changedPaths(), "the changed paths are kept"),
                () -> assertEquals(Optional.of(REPOSITORY), read.headRepository(), "the head repository is kept"));
    }

    @Test
    @DisplayName("prView reads a fork head as not writable when the permission read of the base is 404")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 404,
            stringContent = "{\"message\":\"Not Found\"}")
    void notFoundBasePermissionReadIsNotWritable(MockWebServer server) {
        var fixture = fixture();
        primeOpen(fixture, forkOwner(), true);

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the fork with a hidden base",
                () -> assertEquals(Result.Outcome.OK, result.outcome(), "a 404 permission read is no failure"),
                () -> assertFalse(result.value().headWritable(),
                        "a hidden base repository is not writable"));
    }

    @Test
    @DisplayName("prView answers a 404 of the changed files of a read pull request with FAILED, not NOT_FOUND")
    void notFoundFilesAreFailed(MockWebServer server) {
        var fixture = fixture();
        String remote = Generators.letterStrings(12, 30).next();
        primeOpen(fixture, OWNER, false);
        filesResponse.set(json(404, Map.of("message", remote)));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the missing files",
                () -> assertEquals(Result.Outcome.FAILED, result.outcome(),
                        "an existing pull request is never not found"),
                () -> assertFalse(result.detail().contains(remote), "the reason carries no remote text"),
                () -> assertTrue(result.detail().contains("changed-files"), "the reason names the files read"),
                () -> assertEquals(2, server.getRequestCount(), "no permission read follows the failed files read"));
    }

    @ParameterizedTest(name = "head repository {0}/{1}")
    @DisplayName("prView refuses a head repository that is no plain path segment with FAILED")
    @CsvSource({"'..', pm", "'fork/evil', pm", "cuioss, '..'", "cuioss, 'pm/../x'", "'ow ner', pm"})
    // path-traversal, separator and blank values are the adversarial contract of a plain path segment
    void refusesHeadRepositoryThatIsNoSegment(String owner, String name, MockWebServer server) {
        var fixture = fixture();
        prime(fixture, pullBody(fixture, "open", false, owner, name, true, fixture.paths().size()));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the refused head repository",
                () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "a non-segment head is a failure"),
                () -> assertTrue(result.detail().contains("head repository"), "the reason names the head"),
                () -> assertEquals(2, server.getRequestCount(),
                        "no request is built from the remote head names"));
    }

    @ParameterizedTest(name = "changed_files {0}")
    @DisplayName("prView answers a negative or non-numeric changed_files with FAILED")
    @ValueSource(strings = {"-1", "many", "1.5", "99999999999"})
    // values GitHub never sends: below zero, no number, a fraction and a count beyond the int range
    void refusesUnusableChangedFileCount(String count, MockWebServer server) {
        var fixture = fixture();
        prime(fixture, pullBody(fixture, "open", false, "cuioss", "pm", false, count));

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(PULL_REQUEST);

        assertAll("the unusable count",
                () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "an unusable count is a failure"),
                () -> assertEquals(1, server.getRequestCount(), "nothing is read after the failed pull request"));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @DisplayName("prView refuses a pull request of another origin or forge before sending")
    @CsvSource({"github, https://github.example.com", "forgejo, https://github.com",
            "gitlab, https://gitlab.com"})
    // the forge ids and origins are the contract's provider ids and web origins
    void refusesForeignPullRequest(String forge, String origin, MockWebServer server) {
        var foreign = new PullRequestRef(new RepositoryRef(forge, origin, OWNER, NAME), PULL_REQUEST.number());

        var result = tokenProvider(server, CLASSIC_TOKEN).prView(foreign);

        assertAll("the foreign pull request",
                () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a foreign pull request is refused"),
                () -> assertEquals(0, server.getRequestCount(), "no request is sent for it"));
    }

    @Test
    @DisplayName("prView of an App reads the push fact with the user access token")
    @MockResponseConfig(path = "/repos/cuioss/pm", method = HttpMethodMapper.GET, status = 200,
            stringContent = "{\"permissions\":{\"push\":true}}")
    void readsAppPermissionWithUserToken(MockWebServer server) throws Exception {
        var fixture = fixture();
        primeOpen(fixture, OWNER, false);

        var result = appProvider(server).prView(PULL_REQUEST);
        var pullRequest = server.takeRequest(5, TimeUnit.SECONDS);
        var files = server.takeRequest(5, TimeUnit.SECONDS);
        var permission = server.takeRequest(5, TimeUnit.SECONDS);

        assertAll("the App pull-request read",
                () -> assertTrue(result.value().headWritable(), "the user may push to the head"),
                () -> assertEquals(BEARER + INSTALLATION_TOKEN, authorization(pullRequest),
                        "the pull request is read with the installation"),
                () -> assertEquals(BEARER + INSTALLATION_TOKEN, authorization(files),
                        "the files are read with the installation token"),
                () -> assertEquals(BEARER + USER_TOKEN, authorization(permission),
                        "the push fact is read with the user token"));
    }

    @Test
    @DisplayName("has the CI provider id")
    void hasProviderId(MockWebServer server) {
        assertEquals(FORGE, tokenProvider(server, CLASSIC_TOKEN).id(), "the provider id is github");
    }
}
