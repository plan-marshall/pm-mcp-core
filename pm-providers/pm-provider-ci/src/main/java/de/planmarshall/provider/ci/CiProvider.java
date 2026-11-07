/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.http.Result;

/**
 * A CI provider of one forge, implemented by that forge's adapter and registered under its {@link #id()} in the
 * {@link CiProviders} registry.
 * <p>
 * Every operation returns the closed {@link Result} of the shared HTTP base. A refusal of the operation itself is
 * {@link Result.Outcome#REJECTED} with the provider's reason, kept apart from {@link Result.Outcome#AUTH_FAILED} and
 * {@link Result.Outcome#PERMISSION_DENIED} (a credential refusal), {@link Result.Outcome#NOT_FOUND},
 * {@link Result.Outcome#RATE_LIMITED} with its reset instant, and {@link Result.Outcome#FAILED} (no usable answer).
 * A provider is bound to one origin and one credential; an issue, a pull request, or a repository named to
 * {@code whoami}, that is on another origin (or of another forge) is refused as {@link Result.Outcome#REJECTED} before
 * anything is sent.
 * <p>
 * The operations are:
 * <ul>
 * <li>{@code ci.whoami} ({@link #whoami(RepositoryRef)}): the identity of the credential;</li>
 * <li>{@code ci.issue-view} ({@link #issueView(IssueRef)}): one issue; it keeps its guard that a number naming a pull
 * request is not read as an issue, and {@link #prView(PullRequestRef)} is the read for a pull request;</li>
 * <li>{@code ci.issue-comment} ({@link #issueComment(IssueRef, String)}): one comment on an issue;</li>
 * <li>{@code ci.pr-view} ({@link #prView(PullRequestRef)}): one pull request (a merge request on GitLab).</li>
 * </ul>
 *
 * @since 0.1
 */
public interface CiProvider {

    /**
     * @return the provider id, matching the provider id pattern; it equals the value of the derived fact
     *         {@code ci_provider} and the credential key ({@code github}, {@code gitlab} or {@code forgejo})
     */
    String id();

    /**
     * The operation {@code ci.whoami}: the identity of the credential.
     *
     * @param repository the repository whose permissions the identity reports; {@code null} when no repository is
     *                   named, in which case the permissions are empty
     * @return the identity; no value for a credential or transport outcome, and {@code REJECTED} when the repository
     *         names another origin or another forge
     */
    Result<Identity> whoami(@Nullable RepositoryRef repository);

    /**
     * The operation {@code ci.issue-view}: one issue by its reference.
     *
     * @param issue the issue
     * @return the issue; GitHub answers {@code REJECTED} "is a pull request" when the number names a pull request,
     *         and GitLab answers {@code NOT_FOUND} when no issue exists, including when a merge request has that
     *         number; {@code REJECTED} when the issue is on another origin
     */
    Result<Issue> issueView(IssueRef issue);

    /**
     * The operation {@code ci.issue-comment}: one comment on an issue.
     *
     * @param issue the issue
     * @param body  the comment body
     * @return the posted comment; {@code REJECTED} when the issue is on another origin
     */
    Result<IssueComment> issueComment(IssueRef issue, String body);

    /**
     * The operation {@code ci.pr-view}: one pull request (a merge request on GitLab) by its reference.
     * <p>
     * A pull request that is not open is a value, not a refusal: a closed or merged pull request is
     * {@link Result.Outcome#OK} with its {@link PullRequest#state() state}. A changed-path list the forge cut short is
     * reported by the {@link PullRequest#pathCompleteness() completeness value}, never presented as the whole list.
     *
     * @param pullRequest the pull request
     * @return the pull request; {@code NOT_FOUND} when no pull request has the number; on GitHub that includes a
     *         number that names an issue, and on GitLab a number that names an issue and no merge request, because
     *         GitLab numbers the two apart and an issue iid equal to a merge-request iid reads that merge request;
     *         {@code FAILED} when a read that follows the pull-request read (the changed files, the diffs or the
     *         target project) answers {@code 404}, never {@code NOT_FOUND}, which says that the pull request does
     *         not exist; two {@code 404} answers of a later read are no failure and read as an {@code OK} value: the
     *         push-fact read of GitHub (the credential cannot see the repository, so the head is not writable) and
     *         the source-project read of a GitLab fork (a deleted or hidden fork, so the head repository is absent
     *         and the head is not writable); every other non-{@code OK} outcome of a later read is propagated;
     *         {@code REJECTED} when the pull request is on another origin or of another forge, before anything is
     *         sent
     */
    Result<PullRequest> prView(PullRequestRef pullRequest);
}
