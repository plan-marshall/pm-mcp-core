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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
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
import de.planmarshall.provider.http.Result;

/**
 * GitLab as a CI provider: {@code ci.whoami}, {@code ci.issue-view} and {@code ci.issue-comment} on the native
 * variant, over the shared HTTP base and {@link GitLabClient}.
 * <p>
 * A provider is bound to one web origin and one credential. The acting identity is the token identity the client
 * already reads ({@code personal_access_tokens/self}, with the {@code GET /user} fallback for a token that is no
 * personal access token); the kind is the one the provider reports through that identity and its user.
 *
 * @since 0.1
 */
// The web URL of a GitLab comment is the forge's fixed grammar, not a configurable endpoint (the API origin belongs
// to the HttpClient), so the rule that asks for a configurable URI is suppressed for this contract literal.
@SuppressWarnings("java:S1075")
public final class GitLabCiProvider implements CiProvider {

    /** The provider id, equal to the derived fact {@code ci_provider} and the credential key. */
    public static final String PROVIDER_ID = "gitlab";

    private static final String PROJECT_PATH = "projects/";
    /** The web path of the issue of a project, for the comment URL a consumer shows. */
    private static final String WEB_ISSUE_PATH = "/-/issues/";

    private final String origin;
    private final HttpClient http;

    /**
     * @param origin the forge's web origin the provider is bound to
     * @param http   the HTTP base on the {@code /api/v4/} origin, authenticated with the credential
     * @throws IllegalArgumentException if the origin is not an https origin without a path
     */
    public GitLabCiProvider(String origin, HttpClient http) {
        this.origin = RepositoryRef.normaliseOrigin(Objects.requireNonNull(origin, "origin"));
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public Result<Identity> whoami(@Nullable RepositoryRef repository) {
        String refusal = refusalFor(repository);
        if (refusal != null) {
            return Result.of(Result.Outcome.REJECTED, refusal);
        }
        GitLabClient client = client(repository);
        Result<GitLabClient.TokenIdentity> token = client.tokenIdentity();
        if (!token.isOk()) {
            return token.asFailure();
        }
        Response user = http.get("user");
        if (!user.isOk()) {
            return Result.failed(user);
        }
        Object json = Json.parseOrNull(user.body()).orElse(null);
        Optional<String> login = Json.string(json, "username").filter(value -> !value.isBlank());
        if (login.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the user response carries no username");
        }
        Result<Map<String, String>> permissions = permissions(repository);
        if (!permissions.isOk()) {
            return permissions.asFailure();
        }
        GitLabClient.TokenIdentity identity = token.value().orElseThrow();
        return Result.ok(Identity.builder().login(login.get())
                .userId(Json.string(json, "id").orElse(identity.userId()))
                .tokenKind(kindOf(identity, login.get(), Json.bool(json, "bot")))
                .permissions(permissions.value().orElseThrow())
                .scopes(identity.scopes())
                .expiresAt(identity.expiresAt().flatMap(GitLabCiProvider::instantOf).orElse(null)).build());
    }

    @Override
    public Result<Issue> issueView(IssueRef issue) {
        String refusal = refusalFor(issue);
        if (refusal != null) {
            return Result.of(Result.Outcome.REJECTED, refusal);
        }
        Result<Issue> read = client(issue.repository()).issue(issue.number());
        if (read.outcome() == Result.Outcome.NOT_FOUND) {
            return mergeRequestOutcome(issue, read);
        }
        return read;
    }

    /**
     * A missing issue is the pull-request refusal when a merge request with that number is visible; a failed lookup keeps
     * its own outcome, so the caller never reads a rate limit as "absent".
     */
    private Result<Issue> mergeRequestOutcome(IssueRef issue, Result<Issue> read) {
        Response probe = mergeRequestProbe(issue.repository(), issue.number());
        if (probe.isOk()) {
            return Result.of(Result.Outcome.REJECTED,
                    "is a pull request, or an issue the credential cannot see");
        }
        if (probe.outcome() == Response.Outcome.RATE_LIMITED || probe.outcome() == Response.Outcome.TRANSPORT_FAILED
                || (probe.outcome() == Response.Outcome.HTTP_ERROR && probe.status() >= 500)) {
            return Result.failed(probe);
        }
        return read;
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
        Result<String> id = client(repository).issueComment(issue.number(), body);
        if (!id.isOk()) {
            return id.asFailure();
        }
        String noteId = id.value().orElseThrow();
        return Result.ok(new IssueComment(noteId, repository.origin() + "/" + repository.ownerOrPath() + "/"
                + repository.name() + WEB_ISSUE_PATH + issue.number() + "#note_" + noteId));
    }

    @Nullable
    private String refusalFor(IssueRef issue) {
        return refusalFor(issue.repository());
    }

    @Nullable
    private String refusalFor(@Nullable RepositoryRef repository) {
        if (repository == null) {
            return null;
        }
        if (!PROVIDER_ID.equals(repository.forge())) {
            return "the repository is on the forge " + repository.forge();
        }
        if (!origin.equals(repository.origin())) {
            return "the repository is on another origin";
        }
        return null;
    }

    private GitLabClient client(@Nullable RepositoryRef repository) {
        String project = repository == null ? "" : repository.ownerOrPath() + "/" + repository.name();
        return new GitLabClient(http, project);
    }

    /** @return the answer of the merge-request read, so a failed lookup is not read as "no merge request" */
    private Response mergeRequestProbe(RepositoryRef repository, int iid) {
        String path = PROJECT_PATH
                + URLEncoder.encode(repository.ownerOrPath() + "/" + repository.name(), StandardCharsets.UTF_8)
                + "/merge_requests/" + iid;
        return http.get(path);
    }

    private Result<Map<String, String>> permissions(@Nullable RepositoryRef repository) {
        if (repository == null) {
            return Result.ok(Map.of());
        }
        String path = PROJECT_PATH + URLEncoder.encode(repository.ownerOrPath() + "/" + repository.name(),
                StandardCharsets.UTF_8);
        Response response = http.get(path);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object permissions = Json.at(Json.parseOrNull(response.body()).orElse(null), "permissions").orElse(null);
        if (!(permissions instanceof Map<?, ?> map)) {
            return Result.ok(Map.of());
        }
        var byName = new LinkedHashMap<String, String>();
        map.forEach((name, value) -> Json.string(value, "access_level")
                .ifPresent(level -> byName.put(String.valueOf(name), level)));
        return Result.ok(Map.copyOf(byName));
    }

    private static TokenKind kindOf(GitLabClient.TokenIdentity token, String login, boolean bot) {
        if (token.id().isEmpty()) {
            return TokenKind.GITLAB_OAUTH_TOKEN;
        }
        if (bot && login.startsWith("project_")) {
            return TokenKind.GITLAB_PROJECT_TOKEN;
        }
        if (bot && login.startsWith("group_")) {
            return TokenKind.GITLAB_GROUP_TOKEN;
        }
        return TokenKind.GITLAB_PERSONAL_TOKEN;
    }

    private static Optional<Instant> instantOf(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException _) {
            return instantFromDate(value);
        }
    }

    private static Optional<Instant> instantFromDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
