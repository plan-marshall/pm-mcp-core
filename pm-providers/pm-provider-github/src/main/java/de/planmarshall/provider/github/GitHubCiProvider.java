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
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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
import de.planmarshall.provider.ci.PullRequest;
import de.planmarshall.provider.ci.PullRequestRef;
import de.planmarshall.provider.ci.RepositoryRef;
import de.planmarshall.provider.ci.TokenKind;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Response;
import de.planmarshall.provider.http.ResponseWithHeaders;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.TokenSource;

/**
 * GitHub as a CI provider: {@code ci.whoami}, {@code ci.issue-view}, {@code ci.issue-comment} and
 * {@code ci.pr-view} on the native variant, over the shared HTTP base and {@link GitHubClient}.
 * <p>
 * {@code ci.pr-view} reads the pull request, its changed files and, where it decides the answer, the {@code push}
 * fact of the repository, for every state: {@code closed} with {@code merged: true} is
 * {@link PullRequest.State#MERGED}. The head is writable for a head in the base repository when the credential can
 * push there; for a fork head when the author allows maintainer edits and the credential can push to the base; never
 * for a head repository GitHub no longer knows, nor for a repository whose {@code push} fact the credential cannot
 * read ({@code 404}). An App provider reads the {@code push} fact with the user access token, the credential that
 * authors the writes; a token provider with its own token. Once the pull request has been read, a {@code 404} of its
 * changed files is {@link de.planmarshall.provider.http.Result.Outcome#FAILED}, never {@code NOT_FOUND}, which says
 * that the pull request does not exist. The changed-path list is
 * {@link PullRequest.PathCompleteness#PAGE_CAP} when the page cap cut it, and
 * {@link PullRequest.PathCompleteness#PROVIDER_RESULT_CAP} when every page was read but GitHub listed fewer files
 * than the pull request changes.
 * <p>
 * A provider is bound to one web origin and one credential. A personal access token acts with its own user and
 * reports its kind from the token prefix and its expiry from the {@code github-authentication-token-expiration}
 * response header, read in the {@code yyyy-MM-dd HH:mm:ss z} form and in the ISO and offset forms of the same
 * instant; a header the adapter cannot read is reported as no expiry, never as an error. A GitHub App acts with two
 * identities, its bot identity and the operator whose user access token authors the writes.
 *
 * @since 0.1
 */
public final class GitHubCiProvider implements CiProvider {

    /** The provider id, equal to the derived fact {@code ci_provider} and the credential key. */
    public static final String PROVIDER_ID = "github";

    /** The reason of a {@code 404} of the files read after the pull request was read. */
    private static final String FILES_UNAVAILABLE =
            "the changed-files read of the pull request answered not found";
    private static final String TOKEN_HEADER_PREFIX_FINE_GRAINED = "github_pat_";
    private static final String TOKEN_HEADER_PREFIX_CLASSIC = "ghp_";
    private static final String SCOPES_HEADER = "x-oauth-scopes";
    private static final String EXPIRY_HEADER = "github-authentication-token-expiration";
    private static final DateTimeFormatter GITHUB_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final List<DateTimeFormatter> GITHUB_OFFSET_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xx", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxx", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT));

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
        this.origin = RepositoryRef.normaliseOrigin(Objects.requireNonNull(origin, "origin"));
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
     * @throws IllegalArgumentException if the origin is not an https origin without a path
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
     * @throws IllegalArgumentException if the origin is not an https origin without a path
     */
    public static GitHubCiProvider forApp(String origin, HttpClient http, GitHubClient appClient, HttpClient userHttp) {
        return new GitHubCiProvider(origin, http, new GitHubClient(http, GitHubClient.GRAPHQL_PATH), null,
                appClient, userHttp);
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public Result<Identity> whoami(@Nullable RepositoryRef repository) {
        Optional<String> refusal = refusalFor(repository);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        return appClient == null ? tokenIdentity(repository) : appIdentity(repository);
    }

    @Override
    public Result<Issue> issueView(IssueRef issue) {
        Optional<String> refusal = refusalFor(issue);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        Result<GitHubClient.IssuePayload> payload = client.issue(issue.repository().ownerOrPath(),
                issue.repository().name(), issue.number());
        if (!payload.isOk()) {
            return payload.asFailure();
        }
        var read = Objects.requireNonNull(payload.value());
        return read.pullRequest() ? Result.of(Result.Outcome.REJECTED, "is a pull request")
                : Result.ok(read.issue());
    }

    @Override
    public Result<IssueComment> issueComment(IssueRef issue, String body) {
        Optional<String> refusal = refusalFor(issue);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        if (body.isBlank()) {
            return Result.of(Result.Outcome.REJECTED, "a comment body is never blank");
        }
        RepositoryRef repository = issue.repository();
        GitHubClient reader = userClient();
        Result<GitHubClient.IssuePayload> read = reader.issue(repository.ownerOrPath(), repository.name(),
                issue.number());
        if (!read.isOk()) {
            return read.asFailure();
        }
        if (Objects.requireNonNull(read.value()).pullRequest()) {
            return Result.of(Result.Outcome.REJECTED, "is a pull request");
        }
        Result<String> id = reader.postComment(repository.ownerOrPath(), repository.name(), issue.number(), body);
        if (!id.isOk()) {
            return id.asFailure();
        }
        String commentId = Objects.requireNonNull(id.value());
        return Result.ok(new IssueComment(commentId, repository.origin() + "/" + repository.ownerOrPath() + "/"
                + repository.name() + "/issues/" + issue.number() + "#issuecomment-" + commentId));
    }

    @Override
    public Result<PullRequest> prView(PullRequestRef pullRequest) {
        // a pull-request reference always carries its repository; refusalFor also accepts the absent one of whoami
        RepositoryRef repository = Objects.requireNonNull(pullRequest.repository(), "repository");
        Optional<String> refusal = refusalFor(repository);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        String owner = repository.ownerOrPath();
        String name = repository.name();
        Result<GitHubClient.PullRequestPayload> read = client.pullRequest(owner, name, pullRequest.number());
        if (!read.isOk()) {
            return read.asFailure();
        }
        GitHubClient.PullRequestPayload payload = Objects.requireNonNull(read.value());
        Result<List<String>> files = client.pullRequestFiles(owner, name, pullRequest.number());
        if (!files.isOk()) {
            // the pull request exists, so a 404 of its files is no NOT_FOUND: it names a missing pull request
            return files.outcome() == Result.Outcome.NOT_FOUND
                    ? Result.of(Result.Outcome.FAILED, FILES_UNAVAILABLE)
                    : files.asFailure();
        }
        // The head repository names come from the response; they are validated as plain path segments before they
        // address a request, and the refusal names no remote text.
        Optional<RepositoryRef> head;
        try {
            head = payload.headRepository().map(headRepository -> new RepositoryRef(PROVIDER_ID, origin,
                    headRepository.owner(), headRepository.name()));
        } catch (IllegalArgumentException _) {
            return Result.of(Result.Outcome.FAILED, "the head repository is no repository name");
        }
        Result<Boolean> writable = headWritable(repository, payload);
        if (!writable.isOk()) {
            return writable.asFailure();
        }
        List<String> paths = Objects.requireNonNull(files.value());
        return Result.ok(PullRequest.builder().state(payload.state()).title(payload.title()).body(payload.body())
                .baseBranch(payload.baseBranch()).headBranch(payload.headBranch()).headSha(payload.headSha())
                .headRepositoryRef(head.orElse(null)).headWritable(Objects.requireNonNull(writable.value()))
                .changedPaths(paths).pathCompleteness(completeness(files.complete(), paths.size(),
                payload.changedFiles()))
                .build());
    }

    /**
     * The {@code push} fact the head is writable by: none for a head repository GitHub no longer knows or for a fork
     * whose author allows no maintainer edits, the base repository's for a fork, the head repository's otherwise.
     */
    private Result<Boolean> headWritable(RepositoryRef base, GitHubClient.PullRequestPayload payload) {
        Optional<GitHubClient.HeadRepository> head = payload.headRepository();
        if (head.isEmpty() || payload.fork() && !payload.maintainerCanModify()) {
            return Result.ok(false);
        }
        GitHubClient reader = userClient();
        Result<Boolean> push = payload.fork() ? reader.canPush(base.ownerOrPath(), base.name())
                : reader.canPush(head.get().owner(), head.get().name());
        // a repository the credential cannot see states no push fact for it: not writable, fail closed (ADR 007)
        return push.outcome() == Result.Outcome.NOT_FOUND ? Result.ok(false) : push;
    }

    private static PullRequest.PathCompleteness completeness(boolean allPagesRead, int listed, int changedFiles) {
        if (!allPagesRead) {
            return PullRequest.PathCompleteness.PAGE_CAP;
        }
        return listed < changedFiles ? PullRequest.PathCompleteness.PROVIDER_RESULT_CAP
                : PullRequest.PathCompleteness.COMPLETE;
    }

    /**
     * @return the client acting as the credential that authors the writes: the user access token of an App
     *         provider, the provider's own token otherwise
     */
    private GitHubClient userClient() {
        return userHttp == null ? client
                : new GitHubClient(Objects.requireNonNull(userHttp, "userHttp"), GitHubClient.GRAPHQL_PATH);
    }

    private Optional<String> refusalFor(IssueRef issue) {
        return refusalFor(issue.repository());
    }

    private Optional<String> refusalFor(@Nullable RepositoryRef repository) {
        if (repository == null) {
            return Optional.empty();
        }
        if (!PROVIDER_ID.equals(repository.forge())) {
            return Optional.of("the repository is on the forge " + repository.forge());
        }
        if (!origin.equals(repository.origin())) {
            return Optional.of("the repository is on another origin");
        }
        return Optional.empty();
    }

    private Result<Identity> tokenIdentity(@Nullable RepositoryRef repository) {
        Optional<String> token = Objects.requireNonNull(tokens, "tokens").token();
        if (token.isEmpty()) {
            return Result.of(Result.Outcome.AUTH_FAILED, "no GitHub credential is available");
        }
        Optional<TokenKind> kind = kindOf(token.get());
        if (kind.isEmpty()) {
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
        return Result.ok(Identity.builder().login(login.get()).userId(userId.get()).tokenKind(kind.get())
                .permissions(Objects.requireNonNull(permissions.value()))
                .scopes(user.header(SCOPES_HEADER).map(GitHubCiProvider::scopes).orElse(List.of()))
                .expiresAt(user.header(EXPIRY_HEADER).flatMap(GitHubCiProvider::instantOf).orElse(null)).build());
    }

    private Result<Identity> appIdentity(@Nullable RepositoryRef repository) {
        Result<GitHubClient.AppIdentity> app = Objects.requireNonNull(appClient, "appClient").app();
        if (!app.isOk()) {
            return app.asFailure();
        }
        String slug = Objects.requireNonNull(app.value()).slug();
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
                .operatorLogin(operatorLogin.get()).permissions(Objects.requireNonNull(permissions.value()))
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

    private static Optional<TokenKind> kindOf(String token) {
        if (token.startsWith(TOKEN_HEADER_PREFIX_FINE_GRAINED)) {
            return Optional.of(TokenKind.GITHUB_FINE_GRAINED_PAT);
        }
        if (token.startsWith(TOKEN_HEADER_PREFIX_CLASSIC)) {
            return Optional.of(TokenKind.GITHUB_CLASSIC_PAT);
        }
        return Optional.empty();
    }

    private static List<String> scopes(String header) {
        return Arrays.stream(header.split(",")).map(String::trim).filter(scope -> !scope.isEmpty()).toList();
    }

    private static Optional<Instant> instantOf(String value) {
        try {
            return Optional.of(ZonedDateTime.parse(value, GITHUB_EXPIRY_FORMAT).toInstant());
        } catch (DateTimeParseException _) {
            return instantFromOffset(value);
        }
    }

    private static Optional<Instant> instantFromOffset(String value) {
        for (DateTimeFormatter format : GITHUB_OFFSET_FORMATS) {
            try {
                return Optional.of(OffsetDateTime.parse(value, format).toInstant());
            } catch (DateTimeParseException _) {
                // the value is not in this offset form; try the next one
            }
        }
        return instantOrEmpty(value);
    }

    private static Optional<Instant> instantOrEmpty(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
