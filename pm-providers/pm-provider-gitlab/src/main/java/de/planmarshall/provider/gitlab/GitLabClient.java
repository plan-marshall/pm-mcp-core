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
import java.util.ArrayList;
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
 * A thin GitLab REST client ({@code /api/v4}) for the merge path, the review operations of the native variant and
 * the CI operations: merge and merge-train add bound to the expected head {@code sha}, merge-request discussions
 * (list, reply, resolve), job traces, the token identity read behind
 * {@link de.planmarshall.provider.ci.CiProvider#whoami}, and the issue read, issue comment and merge-request read
 * of the CI contract.
 * <p>
 * The merge-request read behind {@link de.planmarshall.provider.ci.CiProvider#prView} is three REST reads: the merge
 * request itself ({@link #mergeRequest(long)}), its changed paths through the paginated diffs endpoint of GitLab
 * 15.7 and later ({@link #mergeRequestDiffs(long)}, at most {@value #MAX_PAGES} pages of 100 diffs), and a project
 * by its id with the credential's access level on it ({@link #project(String)}).
 *
 * @since 0.1
 */
public final class GitLabClient {

    /** Page cap of paginated reads (100 entries per page). */
    static final int MAX_PAGES = 50;
    /** The access level from which a member can push: Developer. */
    static final int DEVELOPER_ACCESS = 30;
    private static final String PROJECTS = "projects/";
    private static final String MERGE_REQUESTS = "/merge_requests/";
    private static final String ACCESS_LEVEL = "access_level";
    private static final String STATE = "state";
    private static final String TITLE = "title";
    /**
     * The {@code changes_count} with which GitLab states that its diff limit (1000 files by default) capped the
     * changed files of a merge request.
     */
    static final String CHANGES_COUNT_CAPPED = "1000+";
    /** The status with which {@code personal_access_tokens/self} refuses a token of another type. */
    private static final int NOT_A_PERSONAL_ACCESS_TOKEN = 400;

    private final HttpClient http;
    private final String project;

    /**
     * A note of a discussion.
     *
     * @param id         the note id
     * @param author     the author's username
     * @param body       the body
     * @param resolvable whether the note can be resolved
     * @param resolved   whether it is resolved
     */
    @Builder
    public record Note(String id, String author, String body, boolean resolvable, boolean resolved) {
    }

    /**
     * A merge-request discussion.
     *
     * @param id    the discussion id
     * @param notes the notes in order
     */
    public record Discussion(String id, List<Note> notes) {

        /**
         * @return whether every resolvable note is resolved
         */
        public boolean resolved() {
            return notes.stream().filter(Note::resolvable).allMatch(Note::resolved);
        }
    }

    /**
     * The identity behind the token. A token that is no personal access token has no token record: its
     * {@code id}, {@code name} and {@code scopes} are empty, and {@code active} is the state of its user.
     *
     * @param id        the token id, empty when the token is no personal access token
     * @param name      the token name, empty when the token is no personal access token
     * @param userId    the user the token acts as
     * @param scopes    the scopes, empty when GitLab does not report them
     * @param active    whether the token (or, without a token record, its user) is active
     * @param expiresAt the expiry date, {@code null} when none or unknown
     */
    @Builder
    public record TokenIdentity(String id, String name, String userId, List<String> scopes, boolean active,
    @Nullable String expiresAt) {
    }

    /**
     * One merge-request read: the fields of {@code GET projects/{id}/merge_requests/{iid}}.
     *
     * @param state              the state; {@code locked} is {@link PullRequest.State#CLOSED}
     * @param title              the title
     * @param body               the description, empty when the merge request has none
     * @param baseBranch         the target branch
     * @param headBranch         the source branch
     * @param headSha            the head commit
     * @param sourceProjectRef   the id of the source project; {@code null} when GitLab no longer knows it
     * @param targetProjectId    the id of the target project
     * @param allowCollaboration whether the author allows the members of the target project to push to the source
     *                           branch
     * @param providerResultCap  whether GitLab states that its diff limit capped the changed files: its
     *                           {@code changes_count} is the string {@value GitLabClient#CHANGES_COUNT_CAPPED}, and
     *                           its diff APIs list no file past the limit
     */
    @Builder
    public record MergeRequestPayload(PullRequest.State state, String title, String body, String baseBranch,
    String headBranch, String headSha, @Nullable String sourceProjectRef,
    String targetProjectId, boolean allowCollaboration, boolean providerResultCap) {

        /**
         * @return the id of the source project; empty when GitLab no longer knows it (a deleted fork)
         */
        public Optional<String> sourceProject() {
            return Optional.ofNullable(sourceProjectRef);
        }

        /**
         * @return whether the source project is another project than the target, or is no longer known
         */
        public boolean fork() {
            return sourceProject().map(id -> !id.equals(targetProjectId)).orElse(true);
        }
    }

    /**
     * A project as the credential sees it.
     *
     * @param path            the full path ({@code group/sub/project})
     * @param developerAccess whether the credential's access level on the project, as a project or a group member,
     *                        is {@value GitLabClient#DEVELOPER_ACCESS} or above
     */
    public record Project(String path, boolean developerAccess) {
    }

    /**
     * @param http    the HTTP base on the {@code /api/v4/} endpoint
     * @param project the project id or full path ({@code group/project})
     */
    public GitLabClient(HttpClient http, String project) {
        this.http = Objects.requireNonNull(http, "http");
        this.project = PROJECTS + URLEncoder.encode(project, StandardCharsets.UTF_8);
    }

    /**
     * Merges a merge request if its head is still {@code sha}.
     *
     * @param iid the merge request iid
     * @param sha the expected head
     * @return the merge commit sha; {@code REJECTED} when not mergeable or the head moved
     */
    public Result<String> merge(long iid, String sha) {
        Response response = http.send("PUT", project + MERGE_REQUESTS + iid + "/merge",
                Json.write(Map.of("sha", sha)), null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Json.string(Json.parseOrNull(response.body()).orElse(null), "merge_commit_sha").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "merge response without merge_commit_sha"));
    }

    /**
     * Adds a merge request to the merge train if its head is still {@code sha}.
     *
     * @param iid the merge request iid
     * @param sha the expected head
     * @return the response body (the merge train cars)
     */
    public Result<String> mergeTrainAdd(long iid, String sha) {
        Response response = http.send("POST", project + "/merge_trains/merge_requests/" + iid,
                Json.write(Map.of("sha", sha)), null);
        return response.isOk() ? Result.ok(response.body()) : Result.failed(response);
    }

    /**
     * Lists the discussions of a merge request.
     *
     * @param iid the merge request iid
     * @return the discussions; {@code complete} is {@code false} at the page cap
     */
    public Result<List<Discussion>> discussions(long iid) {
        PagedResult pages = http.getAll(project + MERGE_REQUESTS + iid + "/discussions?per_page=100", MAX_PAGES);
        if (pages.pages().isEmpty()) {
            return Result.failed(pages.last());
        }
        var discussions = new ArrayList<Discussion>();
        for (String page : pages.pages()) {
            for (Object discussion : Json.list(Json.parseOrNull(page).orElse(null))) {
                discussions.add(discussion(discussion));
            }
        }
        return Result.ok(List.copyOf(discussions), pages.complete());
    }

    /**
     * Replies to a discussion.
     *
     * @param iid          the merge request iid
     * @param discussionId the discussion id
     * @param body         the reply body
     * @return the note id
     */
    public Result<String> reply(long iid, String discussionId, String body) {
        Response response = http.send("POST", discussionPath(iid, discussionId) + "/notes",
                Json.write(Map.of("body", body)), null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Json.string(Json.parseOrNull(response.body()).orElse(null), "id").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "note response without id"));
    }

    /**
     * Resolves a discussion.
     *
     * @param iid          the merge request iid
     * @param discussionId the discussion id
     * @return whether the discussion is resolved afterwards
     */
    public Result<Boolean> resolve(long iid, String discussionId) {
        Response response = http.send("PUT", discussionPath(iid, discussionId) + "?resolved=true", null,
                null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Result.ok(discussion(Json.parseOrNull(response.body()).orElse(null)).resolved());
    }

    /**
     * Reads the log of a job.
     *
     * @param jobId the job id
     * @return the trace text
     */
    public Result<String> jobTrace(long jobId) {
        Response response = http.get(project + "/jobs/" + jobId + "/trace");
        return response.isOk() ? Result.ok(response.body()) : Result.failed(response);
    }

    /**
     * Reads the identity of the token: {@code GET /personal_access_tokens/self}, and for a token that is no
     * personal access token (the OAuth token of a {@code glab} login, which GitLab refuses there with
     * {@code 400} "requires token type to be a personal access token") {@code GET /user}.
     *
     * @return the identity; {@code AUTH_FAILED} for an invalid token
     */
    public Result<TokenIdentity> tokenIdentity() {
        Response response = http.get("personal_access_tokens/self");
        if (response.outcome() == Response.Outcome.HTTP_ERROR && response.status() == NOT_A_PERSONAL_ACCESS_TOKEN) {
            return userIdentity();
        }
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        var scopes = Json.list(json, "scopes").stream().map(String::valueOf).toList();
        return Result.ok(TokenIdentity.builder()
                .id(Json.string(json, "id").orElse(""))
                .name(Json.string(json, "name").orElse(""))
                .userId(Json.string(json, "user_id").orElse(""))
                .scopes(scopes)
                .active(Json.bool(json, "active"))
                .expiresAt(Json.string(json, "expires_at").orElse(null))
                .build());
    }

    /** The identity of a token without a token record: the user it acts as ({@code GET /user}). */
    private Result<TokenIdentity> userIdentity() {
        Response response = http.get("user");
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        return Json.string(json, "id")
                .map(userId -> Result.ok(TokenIdentity.builder()
                        .id("")
                        .name("")
                        .userId(userId)
                        .scopes(List.of())
                        .active("active".equals(Json.string(json, STATE).orElse("")))
                        .expiresAt(null)
                        .build()))
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "user response without id"));
    }

    /**
     * Reads one issue by its iid.
     *
     * @param iid the issue iid; a merge-request iid is no issue iid, because GitLab numbers the two apart
     * @return the issue's fields
     */
    public Result<Issue> issue(long iid) {
        Response response = http.get(project + "/issues/" + iid);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        if (json == null) {
            return Result.of(Result.Outcome.FAILED, "the issue response is not JSON");
        }
        Optional<String> title = Json.string(json, TITLE).filter(value -> !value.isBlank());
        Optional<String> state = Json.string(json, STATE).filter(value -> !value.isBlank());
        Optional<String> url = Json.string(json, "web_url").filter(value -> !value.isBlank());
        if (title.isEmpty() || state.isEmpty() || url.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the issue response carries no title, state or URL");
        }
        var labels = Json.list(json, "labels").stream().map(String::valueOf).toList();
        var assignees = Json.list(json, "assignees").stream().map(assignee -> Json.string(assignee, "username"))
                .flatMap(Optional::stream).toList();
        return Result.ok(Issue.builder().title(title.get()).body(Json.string(json, "description").orElse(""))
                .labels(labels)
                .milestone(Json.string(json, "milestone", TITLE).filter(value -> !value.isBlank()).orElse(null))
                .assignees(assignees).state(state.get()).url(url.get()).build());
    }

    /**
     * Posts a comment on an issue.
     *
     * @param iid  the issue iid
     * @param body the comment body
     * @return the note id
     */
    public Result<String> issueComment(long iid, String body) {
        Response response = http.send("POST", project + "/issues/" + iid + "/notes",
                Json.write(Map.of("body", body)), null);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        return Json.string(Json.parseOrNull(response.body()).orElse(null), "id").map(Result::ok)
                .orElseGet(() -> Result.of(Result.Outcome.FAILED, "note response without id"));
    }

    /**
     * Reads one merge request by its iid.
     *
     * @param iid the merge-request iid; an issue iid is answered {@code NOT_FOUND} by GitLab unless a merge request
     *            has the same iid, because GitLab numbers the two apart
     * @return the merge request's fields; {@code FAILED} when the body is not JSON, lacks a required member or names
     *         a state outside {@code opened}, {@code closed}, {@code merged} and {@code locked}
     */
    public Result<MergeRequestPayload> mergeRequest(long iid) {
        Response response = http.get(project + MERGE_REQUESTS + iid);
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        Optional<PullRequest.State> state = Json.string(json, STATE).flatMap(GitLabClient::stateOf);
        Optional<String> title = text(json, TITLE);
        Optional<String> baseBranch = text(json, "target_branch");
        Optional<String> headBranch = text(json, "source_branch");
        Optional<String> headSha = text(json, "sha");
        Optional<String> targetProjectId = text(json, "target_project_id");
        if (state.isEmpty() || title.isEmpty() || baseBranch.isEmpty() || headBranch.isEmpty() || headSha.isEmpty()
                || targetProjectId.isEmpty()) {
            return Result.of(Result.Outcome.FAILED,
                    "the merge request response carries no state, title, branches, sha or target project");
        }
        return Result.ok(MergeRequestPayload.builder().state(state.get()).title(title.get())
                .body(Json.string(json, "description").orElse("")).baseBranch(baseBranch.get())
                .headBranch(headBranch.get()).headSha(headSha.get())
                .sourceProjectRef(text(json, "source_project_id").orElse(null)).targetProjectId(targetProjectId.get())
                .allowCollaboration(Json.bool(json, "allow_collaboration"))
                .providerResultCap(Json.string(json, "changes_count").filter(CHANGES_COUNT_CAPPED::equals).isPresent())
                .build());
    }

    /**
     * Reads the changed paths of a merge request, 100 diffs per page, through the paginated
     * {@code GET projects/{id}/merge_requests/{iid}/diffs} of GitLab 15.7 and later.
     *
     * @param iid the merge-request iid
     * @return the new paths of the diffs in GitLab's order; {@code complete} is {@code false} when the page cap (or a
     *         next link outside the origin) cut the list; {@code FAILED} when a page is not a JSON array or a diff
     *         carries no usable {@code new_path}, because the read compares no count against the list and a dropped
     *         diff would otherwise read as {@code complete}
     */
    public Result<List<String>> mergeRequestDiffs(long iid) {
        PagedResult pages = http.getAll(project + MERGE_REQUESTS + iid + "/diffs?per_page=100", MAX_PAGES);
        if (!pages.last().isOk()) {
            return Result.failed(pages.last());
        }
        var paths = new ArrayList<String>();
        for (String page : pages.pages()) {
            if (!(Json.parseOrNull(page).orElse(null) instanceof List<?> diffs)) {
                return Result.of(Result.Outcome.FAILED, "a merge-request diffs page is not a JSON array");
            }
            for (Object diff : diffs) {
                Optional<String> path = text(diff, "new_path");
                if (path.isEmpty()) {
                    return Result.of(Result.Outcome.FAILED, "a merge-request diff carries no new path");
                }
                paths.add(path.get());
            }
        }
        return Result.ok(List.copyOf(paths), pages.complete());
    }

    /**
     * Reads a project by its id: {@code GET projects/{id}}, with the credential's access level from its
     * {@code permissions} member.
     *
     * @param id the project id
     * @return the project's path and whether the credential has Developer access or more on it, as a project or a
     *         group member ({@code false} when the response carries neither level); {@code FAILED} when the body
     *         carries no path
     */
    public Result<Project> project(String id) {
        Response response = http.get(PROJECTS + URLEncoder.encode(id, StandardCharsets.UTF_8));
        if (!response.isOk()) {
            return Result.failed(response);
        }
        Object json = Json.parseOrNull(response.body()).orElse(null);
        Optional<String> path = text(json, "path_with_namespace");
        if (path.isEmpty()) {
            return Result.of(Result.Outcome.FAILED, "the project response carries no path");
        }
        int level = Math.max(accessLevel(json, "project_access"), accessLevel(json, "group_access"));
        return Result.ok(new Project(path.get(), level >= DEVELOPER_ACCESS));
    }

    private static int accessLevel(@Nullable Object json, String kind) {
        return Json.string(json, "permissions", kind, ACCESS_LEVEL).flatMap(GitLabClient::level).orElse(0);
    }

    private static Optional<Integer> level(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<PullRequest.State> stateOf(String state) {
        return switch (state) {
            case "opened" -> Optional.of(PullRequest.State.OPEN);
            case "merged" -> Optional.of(PullRequest.State.MERGED);
            case "closed", "locked" -> Optional.of(PullRequest.State.CLOSED);
            default -> Optional.empty();
        };
    }

    private static Optional<String> text(@Nullable Object json, String... keys) {
        return Json.string(json, keys).filter(value -> !value.isBlank());
    }

    private String discussionPath(long iid, String discussionId) {
        return project + MERGE_REQUESTS + iid + "/discussions/" + URLEncoder.encode(discussionId, StandardCharsets.UTF_8);
    }

    private static Discussion discussion(Object json) {
        var notes = Json.list(json, "notes").stream()
                .map(note -> Note.builder()
                        .id(Json.string(note, "id").orElse(""))
                        .author(Json.string(note, "author", "username").orElse(""))
                        .body(Json.string(note, "body").orElse(""))
                        .resolvable(Json.bool(note, "resolvable"))
                        .resolved(Json.bool(note, "resolved"))
                        .build())
                .toList();
        return new Discussion(Json.string(json, "id").orElse(""), notes);
    }
}
