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
import java.util.List;
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
import de.planmarshall.provider.http.Result;

/**
 * GitLab as a CI provider: {@code ci.whoami}, {@code ci.issue-view}, {@code ci.issue-comment} and
 * {@code ci.pr-view} on the native variant, over the shared HTTP base and {@link GitLabClient}.
 * <p>
 * {@code ci.pr-view} reads the merge request, its diffs and the projects that decide the head, for every state:
 * {@code opened} is {@link PullRequest.State#OPEN}, {@code merged} {@link PullRequest.State#MERGED}, {@code closed}
 * and {@code locked} {@link PullRequest.State#CLOSED}. The head is writable for a same-project merge request when the
 * credential has Developer access or more on the project; for a fork when the author allows collaboration and the
 * credential has Developer access or more on the target project; never for a source project GitLab no longer knows.
 * The changed-path list is {@link PullRequest.PathCompleteness#PAGE_CAP} when the page cap cut it. Once the merge
 * request has been read, a {@code 404} of a later read (the diffs endpoint of GitLab before 15.7, the target
 * project the credential cannot see) is {@link de.planmarshall.provider.http.Result.Outcome#FAILED} with a fixed
 * reason, never {@code NOT_FOUND}, which says that the merge request does not exist. The one {@code 404} of a later
 * read that is a value is the source-project read of a fork: a deleted or hidden fork gives an absent head
 * repository that is not writable. Every other non-{@code OK} outcome of a later read is propagated.
 * <p>
 * A provider is bound to one web origin and one credential. The acting identity is the token identity the client
 * already reads ({@code personal_access_tokens/self}, with the {@code GET /user} fallback for a token that is no
 * personal access token); the kind is the one the provider reports through that identity and its user.
 *
 * @since 0.1
 */
public final class GitLabCiProvider implements CiProvider {

    /** The provider id, equal to the derived fact {@code ci_provider} and the credential key. */
    public static final String PROVIDER_ID = "gitlab";

    private static final String PROJECT_PATH = "projects/";
    /** The reason of a {@code 404} of the diffs read after the merge request was read: the endpoint is missing. */
    private static final String DIFFS_UNAVAILABLE =
            "the merge-request diffs endpoint is unavailable; GitLab 15.7 or later is required";
    /** The reason of a {@code 404} of a project read after the merge request was read. */
    private static final String PROJECT_UNAVAILABLE =
            "a project of the merge request is not visible to the credential";
    /** The web path of the issue of a project, for the comment URL a consumer shows. */
    @SuppressWarnings("java:S1075") // the forge's fixed web grammar, not a configurable endpoint
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
        Optional<String> refusal = refusalFor(repository);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
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
        GitLabClient.TokenIdentity identity = Objects.requireNonNull(token.value());
        Optional<String> userId = Json.string(json, "id").filter(value -> !value.isBlank())
                .or(() -> Optional.of(identity.userId()).filter(value -> !value.isBlank()));
        if (userId.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the user response carries no id");
        }
        return Result.ok(Identity.builder().login(login.get())
                .userId(userId.get())
                .tokenKind(kindOf(identity, login.get(), Json.bool(json, "bot")))
                .permissions(Objects.requireNonNull(permissions.value()))
                .scopes(identity.scopes())
                .expiresAt(Optional.ofNullable(identity.expiresAt()).flatMap(GitLabCiProvider::instantOf).orElse(null))
                .build());
    }

    @Override
    public Result<Issue> issueView(IssueRef issue) {
        Optional<String> refusal = refusalFor(issue.repository());
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        return client(issue.repository()).issue(issue.number());
    }

    @Override
    public Result<IssueComment> issueComment(IssueRef issue, String body) {
        Optional<String> refusal = refusalFor(issue.repository());
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        if (body.isBlank()) {
            return Result.of(Result.Outcome.REJECTED, "a comment body is never blank");
        }
        RepositoryRef repository = issue.repository();
        Result<String> id = client(repository).issueComment(issue.number(), body);
        if (!id.isOk()) {
            return id.asFailure();
        }
        String noteId = Objects.requireNonNull(id.value());
        return Result.ok(new IssueComment(noteId, repository.origin() + "/" + repository.ownerOrPath() + "/"
                + repository.name() + WEB_ISSUE_PATH + issue.number() + "#note_" + noteId));
    }

    @Override
    public Result<PullRequest> prView(PullRequestRef pullRequest) {
        RepositoryRef repository = pullRequest.repository();
        Optional<String> refusal = refusalFor(repository);
        if (refusal.isPresent()) {
            return Result.of(Result.Outcome.REJECTED, refusal.get());
        }
        GitLabClient client = client(repository);
        Result<GitLabClient.MergeRequestPayload> read = client.mergeRequest(pullRequest.number());
        if (!read.isOk()) {
            return read.asFailure();
        }
        GitLabClient.MergeRequestPayload payload = Objects.requireNonNull(read.value());
        Result<List<String>> diffs = client.mergeRequestDiffs(pullRequest.number());
        if (!diffs.isOk()) {
            return afterMergeRequestRead(diffs, DIFFS_UNAVAILABLE);
        }
        Result<Head> head = head(repository, client, payload);
        if (!head.isOk()) {
            return afterMergeRequestRead(head, PROJECT_UNAVAILABLE);
        }
        Head resolved = Objects.requireNonNull(head.value());
        return Result.ok(PullRequest.builder().state(payload.state()).title(payload.title()).body(payload.body())
                .baseBranch(payload.baseBranch()).headBranch(payload.headBranch()).headSha(payload.headSha())
                .headRepositoryRef(resolved.repositoryRef()).headWritable(resolved.writable())
                .changedPaths(Objects.requireNonNull(diffs.value()))
                .pathCompleteness(completeness(diffs.complete(), payload.providerResultCap()))
                .build());
    }

    /**
     * The completeness of the changed paths: {@code PAGE_CAP} when the local page cap stopped the read, which comes
     * first because the read then ended before GitLab's diff limit could be seen; {@code PROVIDER_RESULT_CAP} when the
     * merge request states that GitLab's diff limit capped the list ({@code changes_count} {@code 1000+});
     * {@code COMPLETE} otherwise.
     */
    private static PullRequest.PathCompleteness completeness(boolean allPagesRead, boolean providerResultCap) {
        if (!allPagesRead) {
            return PullRequest.PathCompleteness.PAGE_CAP;
        }
        return providerResultCap ? PullRequest.PathCompleteness.PROVIDER_RESULT_CAP
                : PullRequest.PathCompleteness.COMPLETE;
    }

    /**
     * The failure of a read that follows the merge-request read. The merge request exists, so a {@code 404} of the
     * secondary read (the diffs endpoint does not exist before GitLab 15.7, a project may be hidden from the
     * credential) is no {@link Result.Outcome#NOT_FOUND}, which means that the merge request does not exist; it is
     * {@link Result.Outcome#FAILED} with a fixed reason that carries no remote text. Every other outcome is kept.
     */
    private static <T, R> Result<R> afterMergeRequestRead(Result<T> secondary, String notFoundReason) {
        return secondary.outcome() == Result.Outcome.NOT_FOUND ? Result.of(Result.Outcome.FAILED, notFoundReason)
                : secondary.asFailure();
    }

    /**
     * The head of a merge request and whether the credential can push to it.
     *
     * @param repositoryRef the repository the source branch lives in; {@code null} when GitLab no longer knows it
     * @param writable      whether the credential can push to the source branch
     */
    private record Head(@Nullable RepositoryRef repositoryRef, boolean writable) {
    }

    /**
     * The head of a merge request: the addressed repository for a same-project merge request, writable with
     * Developer access on it; the source project for a fork, writable when the author allows collaboration and the
     * credential has Developer access on the target project; none and not writable for a source project GitLab no
     * longer knows.
     */
    private Result<Head> head(RepositoryRef base, GitLabClient client, GitLabClient.MergeRequestPayload payload) {
        if (!payload.fork()) {
            Result<GitLabClient.Project> project = client.project(payload.targetProjectId());
            return project.isOk()
                    ? Result.ok(new Head(base, Objects.requireNonNull(project.value()).developerAccess()))
                    : project.asFailure();
        }
        Optional<String> sourceId = payload.sourceProject();
        if (sourceId.isEmpty()) {
            return Result.ok(new Head(null, false));
        }
        Result<GitLabClient.Project> source = client.project(sourceId.get());
        if (source.outcome() == Result.Outcome.NOT_FOUND) {
            return Result.ok(new Head(null, false));
        }
        if (!source.isOk()) {
            return source.asFailure();
        }
        Optional<RepositoryRef> sourceRepository = repositoryOf(Objects.requireNonNull(source.value()).path());
        if (sourceRepository.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the source project path is no repository path");
        }
        if (!payload.allowCollaboration()) {
            return Result.ok(new Head(sourceRepository.get(), false));
        }
        Result<GitLabClient.Project> target = client.project(payload.targetProjectId());
        if (!target.isOk()) {
            return target.asFailure();
        }
        return Result.ok(new Head(sourceRepository.get(),
                Objects.requireNonNull(target.value()).developerAccess()));
    }

    private Optional<String> refusalFor(@Nullable RepositoryRef repository) {
        if (repository == null
                || (PROVIDER_ID.equals(repository.forge()) && origin.equals(repository.origin()))) {
            return Optional.empty();
        }
        String reason = PROVIDER_ID.equals(repository.forge())
                ? "the repository is on another origin"
                : "the repository is on the forge " + repository.forge();
        return Optional.of(reason);
    }

    private GitLabClient client(@Nullable RepositoryRef repository) {
        String project = repository == null ? "" : projectPath(repository);
        return new GitLabClient(http, project);
    }

    /** @return the GitLab project path {@code group/sub/project} the API addresses */
    @SuppressWarnings("java:S1075") // the GitLab project-path grammar, not a configurable endpoint
    private static String projectPath(RepositoryRef repository) {
        return repository.ownerOrPath() + "/" + repository.name();
    }

    /** @return the repository of a GitLab project path {@code group/sub/project}; empty when it is no such path */
    private Optional<RepositoryRef> repositoryOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RepositoryRef(PROVIDER_ID, origin, path.substring(0, slash),
                    path.substring(slash + 1)));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private Result<Map<String, String>> permissions(@Nullable RepositoryRef repository) {
        if (repository == null) {
            return Result.ok(Map.of());
        }
        String path = PROJECT_PATH + URLEncoder.encode(projectPath(repository), StandardCharsets.UTF_8);
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
