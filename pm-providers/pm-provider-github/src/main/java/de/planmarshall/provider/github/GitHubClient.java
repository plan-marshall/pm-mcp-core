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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.ci.Issue;
import de.planmarshall.provider.ci.PullRequest;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.PagedResult;
import de.planmarshall.provider.http.Response;
import de.planmarshall.provider.http.Result;

/**
 * The native GitHub operations: the identity, issue and pull-request reads of the CI contract, and the review and
 * merge path (review threads, resolve, merge-queue enqueue) through fixed GraphQL documents shipped as resources, and
 * comment posting through REST. Every body travels in memory as a request payload.
 * <p>
 * The pull-request read ({@link #pullRequest(String, String, int)}) is three REST reads: the pull request itself,
 * its changed files through the {@code Link} pagination of the shared HTTP base ({@link #pullRequestFiles(String,
 * String, int)}, at most {@value #MAX_FILE_PAGES} pages of 100 files), and the {@code push} fact of a repository
 * ({@link #canPush(String, String)}).
 *
 * @since 0.1
 */
public final class GitHubClient {

    /** The GraphQL path relative to {@code https://api.github.com/}. */
    public static final String GRAPHQL_PATH = "graphql";
    private static final String PAGE_INFO = "pageInfo";
    /** Page cap of the review-thread read (100 threads per page). */
    static final int MAX_THREAD_PAGES = 20;
    /**
     * Page cap of the changed-files read: 30 pages of 100 files reach the 3000 files GitHub lists for one pull
     * request at most.
     */
    static final int MAX_FILE_PAGES = 30;
    private static final String REPOS = "repos/";
    private static final String PULLS = "/pulls/";
    private static final String STATE = "state";
    private static final String TITLE = "title";
    private static final String LOGIN = "login";

    static final String REVIEW_THREADS = document("review-threads");
    static final String RESOLVE_REVIEW_THREAD = document("resolve-review-thread");
    static final String ENQUEUE_PULL_REQUEST = document("enqueue-pull-request");

    private final HttpClient http;
    private final String graphqlPath;

    /**
     * The App's own identity, from {@code GET /app}.
     *
     * @param slug the App's slug, the name its bot user login is built from
     * @param id   the App's id
     */
    public record AppIdentity(String slug, String id) {
    }

    /**
     * One issue read: its fields and whether the number names a pull request.
     *
     * @param issue       the issue's fields
     * @param pullRequest whether the number names a pull request (GitHub answers one through the issue endpoint)
     */
    public record IssuePayload(Issue issue, boolean pullRequest) {
    }

    /**
     * The repository the head branch of a pull request lives in.
     *
     * @param owner the owner login
     * @param name  the repository name
     */
    public record HeadRepository(String owner, String name) {
    }

    /**
     * One pull-request read: the fields of {@code GET repos/{owner}/{repo}/pulls/{n}}.
     *
     * @param state               the state; {@code closed} with {@code merged: true} is {@link PullRequest.State#MERGED}
     * @param title               the title
     * @param body                the body, empty when the pull request has none
     * @param baseBranch          the base ref
     * @param headBranch          the head ref
     * @param headSha             the head commit
     * @param headRepositoryRef   the head repository; {@code null} when GitHub no longer knows it (a deleted fork)
     * @param fork                whether the head repository is another repository than the base
     * @param maintainerCanModify whether the author allows the maintainers of the base to push to the head
     * @param changedFiles        the number of files the pull request changes, as GitHub counts them
     */
    @Builder
    public record PullRequestPayload(PullRequest.State state, String title, String body, String baseBranch,
    String headBranch, String headSha, @Nullable HeadRepository headRepositoryRef,
    boolean fork, boolean maintainerCanModify, int changedFiles) {

        /**
         * @return the head repository; empty when GitHub no longer knows it (a deleted fork)
         */
        public Optional<HeadRepository> headRepository() {
            return Optional.ofNullable(headRepositoryRef);
        }
    }

    /**
     * A review comment.
     *
     * @param id         the node id
     * @param databaseId the REST id
     * @param author     the author login, {@code null} for a deleted account
     * @param body       the body
     * @param createdAt  the creation instant (ISO-8601)
     */
    @Builder
    public record ReviewComment(String id, String databaseId, @Nullable String author, String body, String createdAt) {
    }

    /**
     * A review thread of a pull request.
     *
     * @param id       the node id
     * @param resolved whether it is resolved
     * @param outdated whether its position is outdated
     * @param path     the file path
     * @param line     the line, {@code null} when outdated or file-level
     * @param comments the first comments of the thread
     * @param complete whether {@code comments} holds every comment
     */
    @Builder
    public record ReviewThread(String id, boolean resolved, boolean outdated, String path, @Nullable String line,
    List<ReviewComment> comments, boolean complete) {
    }

    /**
     * A merge-queue entry.
     *
     * @param id       the node id
     * @param position the queue position
     * @param state    the entry state, e.g. {@code QUEUED}
     */
    public record MergeQueueEntry(String id, String position, String state) {
    }

    /**
     * @param http        the HTTP base on the API endpoint, authenticated with an installation token
     * @param graphqlPath the GraphQL path relative to the API base ({@link #GRAPHQL_PATH}, or
     *                    {@code ../graphql} for an Enterprise base {@code /api/v3/})
     */
    public GitHubClient(HttpClient http, String graphqlPath) {
        this.http = Objects.requireNonNull(http, "http");
        this.graphqlPath = Objects.requireNonNull(graphqlPath, "graphqlPath");
    }

    /**
     * Lists every review thread of a pull request.
     *
     * @param owner  the repository owner
     * @param name   the repository name
     * @param number the pull request number
     * @return the threads; {@code complete} is {@code false} when the page cap was reached
     */
    public Result<List<ReviewThread>> reviewThreads(String owner, String name, int number) {
        var threads = new ArrayList<ReviewThread>();
        @Nullable Object cursor = null;
        for (int page = 0; page < MAX_THREAD_PAGES; page++) {
            var variables = new LinkedHashMap<String, Object>();
            variables.put("owner", owner);
            variables.put("name", name);
            variables.put("number", number);
            variables.put("cursor", cursor);
            Result<Object> data = graphql(REVIEW_THREADS, variables);
            if (!data.isOk()) {
                return data.asFailure();
            }
            Object connection = Json.at(Objects.requireNonNull(data.value()), "repository", "pullRequest",
                    "reviewThreads").orElse(null);
            if (connection == null) {
                return Result.of(Result.Outcome.NOT_FOUND, owner + "/" + name + "#" + number);
            }
            Json.list(connection, "nodes").forEach(node -> threads.add(thread(node)));
            if (!Json.bool(connection, PAGE_INFO, "hasNextPage")) {
                return Result.ok(List.copyOf(threads), true);
            }
            cursor = Json.string(connection, PAGE_INFO, "endCursor").orElse(null);
        }
        return Result.ok(List.copyOf(threads), false);
    }

    /**
     * Resolves a review thread.
     *
     * @param threadId the thread's node id
     * @return whether the thread is resolved afterwards
     */
    public Result<Boolean> resolveReviewThread(String threadId) {
        var variables = new LinkedHashMap<String, Object>();
        variables.put("threadId", threadId);
        Result<Object> data = graphql(RESOLVE_REVIEW_THREAD, variables);
        if (!data.isOk()) {
            return data.asFailure();
        }
        return Result.ok(Json.bool(Objects.requireNonNull(data.value()), "resolveReviewThread", "thread",
                "isResolved"));
    }

    /**
     * Adds a pull request to the merge queue, bound to the expected head commit.
     *
     * @param pullRequestId   the pull request's node id
     * @param expectedHeadOid the head commit the barrier evaluated; a moved head is refused
     * @return the queue entry; {@code REJECTED} when GitHub refuses (e.g. head moved)
     */
    public Result<MergeQueueEntry> enqueue(String pullRequestId, String expectedHeadOid) {
        var variables = new LinkedHashMap<String, Object>();
        variables.put("pullRequestId", pullRequestId);
        variables.put("expectedHeadOid", expectedHeadOid);
        Result<Object> data = graphql(ENQUEUE_PULL_REQUEST, variables);
        if (!data.isOk()) {
            return data.asFailure();
        }
        Object entry = Json.at(Objects.requireNonNull(data.value()), "enqueuePullRequest", "mergeQueueEntry")
                .orElse(null);
        if (entry == null) {
            return Result.of(Result.Outcome.FAILED, "enqueue returned no merge queue entry");
        }
        return Result.ok(new MergeQueueEntry(Json.string(entry, "id").orElse(""),
                Json.string(entry, "position").orElse(""), Json.string(entry, STATE).orElse("")));
    }

    /**
     * Posts a comment on a pull request or issue. The token needs the permission set of
     * {@link GitHubOperation#PULL_REQUEST_COMMENT} for a pull request ({@code pull_requests: write}) and of
     * {@link GitHubOperation#ISSUE_COMMENT} for an issue.
     *
     * @param owner  the repository owner
     * @param name   the repository name
     * @param number the pull request or issue number
     * @param body   the comment body
     * @return the comment's REST id
     */
    public Result<String> postComment(String owner, String name, int number, String body) {
        Response response = http.send("POST", REPOS + owner + "/" + name + "/issues/" + number + "/comments",
                Json.write(Map.of("body", body)), null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Json.string(Json.parseOrNull(response.body()).orElse(null), "id").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "comment response without id"));
    }

    /**
     * Reads the App's own identity; the client must be authenticated with the App JWT.
     *
     * @return the App's slug and id; {@code AUTH_FAILED} for a refused App JWT
     */
    public Result<AppIdentity> app() {
        Response response = http.get("app");
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        Optional<String> slug = Json.string(json, "slug");
        Optional<String> id = Json.string(json, "id");
        if (slug.isEmpty() || id.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the app response carries no slug or id");
        }
        return Result.ok(new AppIdentity(slug.get(), id.get()));
    }

    /**
     * Reads one issue by its number. The token needs the permission set of {@link GitHubOperation#ISSUE_READ}; a
     * public issue of another repository is read without a credential.
     *
     * @param owner  the repository owner
     * @param name   the repository name
     * @param number the issue number; a pull request number is answered too, and flagged
     * @return the issue's fields and whether it is a pull request
     */
    public Result<IssuePayload> issue(String owner, String name, int number) {
        Response response = http.get(REPOS + owner + "/" + name + "/issues/" + number);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        if (json == null) {
            return Result.of(Result.Outcome.FAILED, "the issue response is not JSON");
        }
        Optional<String> title = Json.string(json, TITLE).filter(t -> !t.isBlank());
        Optional<String> state = Json.string(json, STATE).filter(s -> !s.isBlank());
        Optional<String> url = Json.string(json, "html_url").filter(u -> !u.isBlank());
        if (title.isEmpty() || state.isEmpty() || url.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the issue response carries no title, state or URL");
        }
        var labels = Json.list(json, "labels").stream().map(label -> Json.string(label, "name"))
                .flatMap(Optional::stream).toList();
        var assignees = Json.list(json, "assignees").stream().map(assignee -> Json.string(assignee, LOGIN))
                .flatMap(Optional::stream).toList();
        var issue = Issue.builder().title(title.get()).body(Json.string(json, "body").orElse(""))
                .labels(labels)
                .milestone(Json.string(json, "milestone", TITLE).filter(value -> !value.isBlank()).orElse(null))
                .assignees(assignees).state(state.get()).url(url.get()).build();
        return Result.ok(new IssuePayload(issue, Json.at(json, "pull_request").isPresent()));
    }

    /**
     * Reads one pull request by its number. The token needs the permission set of
     * {@link GitHubOperation#PULL_REQUEST_READ}.
     *
     * @param owner  the repository owner
     * @param name   the repository name
     * @param number the pull-request number; an issue number is answered {@code NOT_FOUND} by GitHub
     * @return the pull request's fields; {@code FAILED} when the body is not JSON or lacks a required member
     */
    public Result<PullRequestPayload> pullRequest(String owner, String name, int number) {
        Response response = http.get(REPOS + owner + "/" + name + PULLS + number);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        Optional<PullRequest.State> state = Json.string(json, STATE)
                .flatMap(value -> stateOf(value, Json.bool(json, "merged")));
        Optional<String> title = text(json, TITLE);
        Optional<String> baseBranch = text(json, "base", "ref");
        Optional<String> headBranch = text(json, "head", "ref");
        Optional<String> headSha = text(json, "head", "sha");
        Optional<Integer> changedFiles = Json.string(json, "changed_files").flatMap(GitHubClient::count);
        if (state.isEmpty() || title.isEmpty() || baseBranch.isEmpty() || headBranch.isEmpty() || headSha.isEmpty()
                || changedFiles.isEmpty()) {
            return Result.of(Result.Outcome.FAILED,
                    "the pull request response carries no state, title, base, head or changed-file count");
        }
        @Nullable HeadRepository head = headRepositoryOf(json).orElse(null);
        String baseFullName = text(json, "base", "repo", "full_name").orElse(owner + "/" + name);
        boolean fork = head != null && !(head.owner() + "/" + head.name()).equalsIgnoreCase(baseFullName);
        return Result.ok(PullRequestPayload.builder().state(state.get()).title(title.get())
                .body(Json.string(json, "body").orElse("")).baseBranch(baseBranch.get())
                .headBranch(headBranch.get()).headSha(headSha.get()).headRepositoryRef(head).fork(fork)
                .maintainerCanModify(Json.bool(json, "maintainer_can_modify")).changedFiles(changedFiles.get())
                .build());
    }

    /**
     * Reads the changed paths of a pull request, 100 files per page, through the {@code Link} pagination. The token
     * needs the permission set of {@link GitHubOperation#PULL_REQUEST_READ}.
     *
     * @param owner  the repository owner
     * @param name   the repository name
     * @param number the pull-request number
     * @return the changed paths in GitHub's order; {@code complete} is {@code false} when the page cap (or a next
     *         link outside the origin) cut the list; {@code FAILED} when a page is not a JSON array or an entry
     *         carries no usable {@code filename}
     */
    public Result<List<String>> pullRequestFiles(String owner, String name, int number) {
        PagedResult pages = http.getAll(REPOS + owner + "/" + name + PULLS + number + "/files?per_page=100",
                MAX_FILE_PAGES);
        if (!pages.last().isOk()) {
            return Result.failed(pages.last());
        }
        var paths = new ArrayList<String>();
        for (String page : pages.pages()) {
            if (!(Json.parseOrNull(page).orElse(null) instanceof List<?> files)) {
                return Result.of(Result.Outcome.FAILED, "a changed-files page is not a JSON array");
            }
            for (Object file : files) {
                Optional<String> filename = text(file, "filename");
                if (filename.isEmpty()) {
                    return Result.of(Result.Outcome.FAILED, "a changed-files entry carries no usable filename");
                }
                paths.add(filename.get());
            }
        }
        return Result.ok(List.copyOf(paths), pages.complete());
    }

    /**
     * Reads whether the credential can push to a repository: the {@code permissions.push} member of
     * {@code GET repos/{owner}/{repo}}.
     *
     * @param owner the repository owner
     * @param name  the repository name
     * @return whether the credential can push; {@code false} when the response carries no such member;
     *         {@code NOT_FOUND} when the credential cannot see the repository, which the caller reads as not
     *         writable
     */
    public Result<Boolean> canPush(String owner, String name) {
        Response response = http.get(REPOS + owner + "/" + name);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Result.ok(Json.bool(Json.parseOrNull(response.body()).orElse(null), "permissions", "push"));
    }

    private static Optional<HeadRepository> headRepositoryOf(@Nullable Object json) {
        Optional<String> owner = text(json, "head", "repo", "owner", LOGIN);
        Optional<String> name = text(json, "head", "repo", "name");
        return owner.isPresent() && name.isPresent() ? Optional.of(new HeadRepository(owner.get(), name.get()))
                : Optional.empty();
    }

    private static Optional<PullRequest.State> stateOf(String state, boolean merged) {
        return switch (state) {
            case "open" -> Optional.of(PullRequest.State.OPEN);
            case "closed" -> Optional.of(merged ? PullRequest.State.MERGED : PullRequest.State.CLOSED);
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> count(String value) {
        try {
            int count = Integer.parseInt(value);
            return count < 0 ? Optional.empty() : Optional.of(count);
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<String> text(@Nullable Object json, String... keys) {
        return Json.string(json, keys).filter(value -> !value.isBlank());
    }

    private Result<Object> graphql(String query, Map<String, Object> variables) {
        var request = new LinkedHashMap<String, Object>();
        request.put("query", query);
        request.put("variables", variables);
        Response response = http.send("POST", graphqlPath, Json.write(request), null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        List<Object> errors = Json.list(json, "errors");
        if (!errors.isEmpty()) {
            Object first = errors.getFirst();
            Result.Outcome outcome = Json.string(first, "type").filter("NOT_FOUND"::equals).isPresent()
                    ? Result.Outcome.NOT_FOUND
                    : Result.Outcome.REJECTED;
            return Result.of(outcome, Json.string(first, "message").orElse("GraphQL error"));
        }
        return Json.at(json, "data").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "GraphQL response without data"));
    }

    private static ReviewThread thread(Object node) {
        var comments = Json.list(node, "comments", "nodes").stream()
                .map(comment -> ReviewComment.builder()
                        .id(Json.string(comment, "id").orElse(""))
                        .databaseId(Json.string(comment, "databaseId").orElse(""))
                        .author(Json.string(comment, "author", LOGIN).orElse(null))
                        .body(Json.string(comment, "body").orElse(""))
                        .createdAt(Json.string(comment, "createdAt").orElse(""))
                        .build())
                .toList();
        return ReviewThread.builder()
                .id(Json.string(node, "id").orElse(""))
                .resolved(Json.bool(node, "isResolved"))
                .outdated(Json.bool(node, "isOutdated"))
                .path(Json.string(node, "path").orElse(""))
                .line(Json.string(node, "line").orElse(null))
                .comments(comments)
                .complete(!Json.bool(node, "comments", PAGE_INFO, "hasNextPage"))
                .build();
    }

    private static String document(String name) {
        String resource = "graphql/" + name + ".graphql";
        try (InputStream in = GitHubClient.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing GraphQL document " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
