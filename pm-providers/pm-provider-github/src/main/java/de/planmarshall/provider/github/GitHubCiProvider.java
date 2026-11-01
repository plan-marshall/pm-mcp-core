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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.ci.CiProvider;
import de.planmarshall.provider.ci.Identity;
import de.planmarshall.provider.ci.Issue;
import de.planmarshall.provider.ci.IssueComment;
import de.planmarshall.provider.ci.IssueRef;
import de.planmarshall.provider.ci.RepositoryRef;
import de.planmarshall.provider.ci.TokenKind;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Response;
import de.planmarshall.provider.http.ResponseWithHeaders;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.TokenSource;

/**
 * GitHub as a CI provider: {@code ci.whoami}, {@code ci.issue-view} and {@code ci.issue-comment} on the native
 * variant, over the shared HTTP base and {@link GitHubClient}.
 * <p>
 * A provider is bound to one web origin and one credential. A personal access token acts with its own user and
 * reports its kind from the token prefix and its expiry from the {@code github-authentication-token-expiration}
 * response header; a GitHub App acts with two identities, its bot identity and the operator whose user access token
 * authors the writes.
 *
 * @since 0.1
 */
public final class GitHubCiProvider implements CiProvider {

    /** The provider id, equal to the derived fact {@code ci_provider} and the credential key. */
    public static final String ID = "github";

    private static final String TOKEN_HEADER_PREFIX_FINE_GRAINED = "github_pat_";
    private static final String TOKEN_HEADER_PREFIX_CLASSIC = "ghp_";
    private static final String SCOPES_HEADER = "x-oauth-scopes";
    private static final String EXPIRY_HEADER = "github-authentication-token-expiration";
    private static final DateTimeFormatter GITHUB_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    private final String origin;
    private final HttpClient http;
    private final GitHubClient client;
    @Nullable
    private final TokenSource tokens;
    @Nullable
    private final GitHubClient appClient;
    @Nullable
    private final HttpClient userHttp;

    private GitHubCiProvider(String origin, HttpClient http, GitHubClient client, @Nullable TokenSource tokens,
            @Nullable GitHubClient appClient, @Nullable HttpClient userHttp) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.http = Objects.requireNonNull(http, "http");
        this.client = Objects.requireNonNull(client, "client");
        this.tokens = tokens;
        this.appClient = appClient;
        this.userHttp = userHttp;
    }

    /**
     * A provider acting with a personal access token or another token credential.
     *
     * @param origin the forge's web origin the provider is bound to
     * @param http   the HTTP base on the API origin, authenticated with the token
     * @param tokens the token source, read to name the token kind
     * @return the provider
     */
    public static GitHubCiProvider forToken(String origin, HttpClient http, TokenSource tokens) {
        return new GitHubCiProvider(origin, http, new GitHubClient(http, GitHubClient.GRAPHQL_PATH),
                Objects.requireNonNull(tokens, "tokens"), null, null);
    }

    /**
     * A provider acting as a GitHub App: the App JWT for its own identity and the user access token for the
     * operator's login and for every write.
     *
     * @param origin    the forge's web origin the provider is bound to
     * @param http      the HTTP base on the API origin, authenticated with an installation token for the reads
     * @param appClient the GitHub client on the App-JWT-authenticated API
     * @param userHttp  the HTTP base authenticated with the user access token
     * @return the provider
     */
    public static GitHubCiProvider forApp(String origin, HttpClient http, GitHubClient appClient, HttpClient userHttp) {
        return new GitHubCiProvider(origin, http, new GitHubClient(http, GitHubClient.GRAPHQL_PATH), null,
                appClient, userHttp);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Result<Identity> whoami(@Nullable RepositoryRef repository) {
        return appClient == null ? tokenIdentity(repository) : appIdentity(repository);
    }

    @Override
    public Result<Issue> issueView(IssueRef issue) {
        String refusal = refusalFor(issue);
        if (refusal != null) {
            return Result.of(Result.Outcome.REJECTED, refusal);
        }
        Result<GitHubClient.IssuePayload> payload = client.issue(issue.repository().ownerOrPath(),
                issue.repository().name(), issue.number());
        if (!payload.isOk()) {
            return payload.asFailure();
        }
        var read = payload.value().orElseThrow();
        return read.pullRequest() ? Result.of(Result.Outcome.REJECTED, "is a pull request")
                : Result.ok(read.issue());
    }

    @Override
    public Result<IssueComment> issueComment(IssueRef issue, String body) {
        String refusal = refusalFor(issue);
        if (refusal != null) {
            return Result.of(Result.Outcome.REJECTED, refusal);
        }
        if (body.isBlank()) {
            return Result.of(Result.Outcome.REJECTED, "a comment body is never blank");
        }
        RepositoryRef repository = issue.repository();
        Result<String> id = client.postComment(repository.ownerOrPath(), repository.name(), issue.number(), body);
        if (!id.isOk()) {
            return id.asFailure();
        }
        String commentId = id.value().orElseThrow();
        return Result.ok(new IssueComment(commentId, repository.origin() + "/" + repository.ownerOrPath() + "/"
                + repository.name() + "/issues/" + issue.number() + "#issuecomment-" + commentId));
    }

    @Nullable
    private String refusalFor(IssueRef issue) {
        RepositoryRef repository = issue.repository();
        if (!ID.equals(repository.forge())) {
            return "the issue is on the forge " + repository.forge();
        }
        if (!origin.equals(repository.origin())) {
            return "the issue is on another origin";
        }
        return null;
    }

    private Result<Identity> tokenIdentity(@Nullable RepositoryRef repository) {
        Optional<String> token = Objects.requireNonNull(tokens, "tokens").token();
        if (token.isEmpty()) {
            return Result.of(Result.Outcome.AUTH_FAILED, "no GitHub credential is available");
        }
        TokenKind kind = kindOf(token.get());
        if (kind == null) {
            return Result.of(Result.Outcome.FAILED, "the token prefix names no GitHub token kind");
        }
        ResponseWithHeaders user = http.getWithHeaders("user");
        if (!user.isOk()) {
            return Result.failed(user.response());
        }
        Object json = Json.parseOrNull(user.response().body()).orElse(null);
        Optional<String> login = Json.string(json, "login").filter(value -> !value.isBlank());
        Optional<String> userId = Json.string(json, "id").filter(value -> !value.isBlank());
        if (login.isEmpty() || userId.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the user response carries no login or id");
        }
        Result<Map<String, String>> permissions = permissions(repository);
        if (!permissions.isOk()) {
            return permissions.asFailure();
        }
        return Result.ok(Identity.builder().login(login.get()).userId(userId.get()).tokenKind(kind)
                .permissions(permissions.value().orElseThrow())
                .scopes(user.header(SCOPES_HEADER).map(GitHubCiProvider::scopes).orElse(List.of()))
                .expiresAt(user.header(EXPIRY_HEADER).flatMap(GitHubCiProvider::instantOf).orElse(null)).build());
    }

    private Result<Identity> appIdentity(@Nullable RepositoryRef repository) {
        Result<GitHubClient.AppIdentity> app = Objects.requireNonNull(appClient, "appClient").app();
        if (!app.isOk()) {
            return app.asFailure();
        }
        String slug = app.value().orElseThrow().slug();
        String botLogin = slug + "[bot]";
        Response bot = http.get("users/" + URLEncoder.encode(botLogin, StandardCharsets.UTF_8));
        if (!bot.isOk()) {
            return Result.failed(bot);
        }
        Optional<String> botId = Json.string(Json.parseOrNull(bot.body()).orElse(null), "id")
                .filter(value -> !value.isBlank());
        if (botId.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the bot user response carries no id");
        }
        ResponseWithHeaders user = Objects.requireNonNull(userHttp, "userHttp").getWithHeaders("user");
        if (!user.isOk()) {
            return Result.failed(user.response());
        }
        Optional<String> operatorLogin = Json.string(Json.parseOrNull(user.response().body()).orElse(null), "login")
                .filter(value -> !value.isBlank());
        if (operatorLogin.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the user response carries no login");
        }
        Result<Map<String, String>> permissions = permissions(repository);
        if (!permissions.isOk()) {
            return permissions.asFailure();
        }
        return Result.ok(Identity.builder().login(botLogin).userId(botId.get()).tokenKind(TokenKind.GITHUB_APP)
                .operatorLogin(operatorLogin.get()).permissions(permissions.value().orElseThrow())
                .scopes(List.of()).build());
    }

    private Result<Map<String, String>> permissions(@Nullable RepositoryRef repository) {
        if (repository == null) {
            return Result.ok(Map.of());
        }
        Response response = http.get("repos/" + repository.ownerOrPath() + "/" + repository.name());
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object permissions = Json.at(Json.parseOrNull(response.body()).orElse(null), "permissions").orElse(null);
        if (!(permissions instanceof Map<?, ?> map)) {
            return Result.ok(Map.of());
        }
        var byName = new LinkedHashMap<String, String>();
        map.forEach((name, level) -> byName.put(String.valueOf(name), String.valueOf(level)));
        return Result.ok(Map.copyOf(byName));
    }

    @Nullable
    private static TokenKind kindOf(String token) {
        if (token.startsWith(TOKEN_HEADER_PREFIX_FINE_GRAINED)) {
            return TokenKind.GITHUB_FINE_GRAINED_PAT;
        }
        if (token.startsWith(TOKEN_HEADER_PREFIX_CLASSIC)) {
            return TokenKind.GITHUB_CLASSIC_PAT;
        }
        return null;
    }

    private static List<String> scopes(String header) {
        return Arrays.stream(header.split(",")).map(String::trim).filter(scope -> !scope.isEmpty()).toList();
    }

    private static Optional<Instant> instantOf(String value) {
        try {
            return Optional.of(ZonedDateTime.parse(value, GITHUB_EXPIRY_FORMAT).toInstant());
        } catch (DateTimeParseException first) {
            return instantOrEmpty(value);
        }
    }

    private static Optional<Instant> instantOrEmpty(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
