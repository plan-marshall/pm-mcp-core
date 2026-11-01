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

import de.planmarshall.provider.http.Result;
import org.jspecify.annotations.Nullable;

/**
 * A CI provider of one forge, implemented by that forge's adapter and registered under its {@link #id()} in the
 * {@link CiProviders} registry.
 * <p>
 * Every operation returns the closed {@link Result} of the shared HTTP base. A refusal of the operation itself is
 * {@link Result.Outcome#REJECTED} with the provider's reason, kept apart from {@link Result.Outcome#AUTH_FAILED} and
 * {@link Result.Outcome#PERMISSION_DENIED} (a credential refusal), {@link Result.Outcome#NOT_FOUND},
 * {@link Result.Outcome#RATE_LIMITED} with its reset instant, and {@link Result.Outcome#FAILED} (no usable answer).
 * A provider is bound to one origin and one credential; an issue on another origin is refused as
 * {@link Result.Outcome#REJECTED} before anything is sent.
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
     * @return the identity; no value for a credential or transport outcome
     */
    Result<Identity> whoami(@Nullable RepositoryRef repository);

    /**
     * The operation {@code ci.issue-view}: one issue by its reference.
     *
     * @param issue the issue
     * @return the issue; {@code REJECTED} "is a pull request" when the number names a pull request, and
     *         {@code REJECTED} when the issue is on another origin
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
}
