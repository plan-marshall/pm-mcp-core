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
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Response;
import de.planmarshall.provider.http.Result;

/**
 * The native GitHub operations of the review and merge path: review threads (list, resolve) and the
 * merge-queue enqueue through fixed GraphQL documents shipped as resources, and comment posting
 * through REST. Every body travels in memory as a request payload.
 *
 * @since 0.1
 */
public final class GitHubClient {

    /** The GraphQL path relative to {@code https://api.github.com/}. */
    public static final String GRAPHQL_PATH = "graphql";
    /** Page cap of the review-thread read (100 threads per page). */
    private static final String PAGE_INFO = "pageInfo";
    static final int MAX_THREAD_PAGES = 20;

    static final String REVIEW_THREADS = document("review-threads");
    static final String RESOLVE_REVIEW_THREAD = document("resolve-review-thread");
    static final String ENQUEUE_PULL_REQUEST = document("enqueue-pull-request");

    private final HttpClient http;
    private final String graphqlPath;

    /**
     * A review comment.
     *
     * @param id         the node id
     * @param databaseId the REST id
     * @param author     the author login, empty for a deleted account
     * @param body       the body
     * @param createdAt  the creation instant (ISO-8601)
     */
    public record ReviewComment(String id, String databaseId, Optional<String> author, String body, String createdAt) {
    }

    /**
     * A review thread of a pull request.
     *
     * @param id       the node id
     * @param resolved whether it is resolved
     * @param outdated whether its position is outdated
     * @param path     the file path
     * @param line     the line, empty when outdated or file-level
     * @param comments the first comments of the thread
     * @param complete whether {@code comments} holds every comment
     */
    public record ReviewThread(String id, boolean resolved, boolean outdated, String path, Optional<String> line,
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
            Object connection = Json.at(data.value().orElseThrow(), "repository", "pullRequest", "reviewThreads")
                    .orElse(null);
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
        return Result.ok(Json.bool(data.value().orElseThrow(), "resolveReviewThread", "thread", "isResolved"));
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
        Object entry = Json.at(data.value().orElseThrow(), "enqueuePullRequest", "mergeQueueEntry").orElse(null);
        if (entry == null) {
            return Result.of(Result.Outcome.FAILED, "enqueue returned no merge queue entry");
        }
        return Result.ok(new MergeQueueEntry(Json.string(entry, "id").orElse(""),
                Json.string(entry, "position").orElse(""), Json.string(entry, "state").orElse("")));
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
        Response response = http.send("POST", "repos/" + owner + "/" + name + "/issues/" + number + "/comments",
                Optional.of(Json.write(Map.of("body", body))), Optional.empty());
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Json.string(Json.parseOrNull(response.body()).orElse(null), "id").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "comment response without id"));
    }

    private Result<Object> graphql(String query, Map<String, Object> variables) {
        var request = new LinkedHashMap<String, Object>();
        request.put("query", query);
        request.put("variables", variables);
        Response response = http.send("POST", graphqlPath, Optional.of(Json.write(request)), Optional.empty());
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
                .map(comment -> new ReviewComment(Json.string(comment, "id").orElse(""),
                        Json.string(comment, "databaseId").orElse(""), Json.string(comment, "author", "login"),
                        Json.string(comment, "body").orElse(""), Json.string(comment, "createdAt").orElse("")))
                .toList();
        return new ReviewThread(Json.string(node, "id").orElse(""), Json.bool(node, "isResolved"),
                Json.bool(node, "isOutdated"), Json.string(node, "path").orElse(""), Json.string(node, "line"),
                comments, !Json.bool(node, "comments", PAGE_INFO, "hasNextPage"));
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
