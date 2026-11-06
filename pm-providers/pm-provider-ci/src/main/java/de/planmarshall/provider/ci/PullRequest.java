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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One pull request (a merge request on GitLab) as {@code ci.pr-view} answers it: the state, the title and body as
 * text, the base and head branches, the head commit, the head repository, whether the credential can push to the
 * head, and the changed paths with the completeness of their list.
 * <p>
 * The title and body are remote text. This contract returns them unvalidated and says so: the consumer
 * ({@code source-pr.store}) runs the level-1 validator and the credential-shaped-text check on them before a model
 * sees them, never the provider.
 * <p>
 * The head repository is absent when the forge no longer knows it (a deleted fork); it is read through
 * {@link #headRepository()}.
 *
 * @param state             the state of the pull request
 * @param title             the title text
 * @param body              the body text, empty when the pull request has none
 * @param baseBranch        the branch the pull request merges into
 * @param headBranch        the branch the pull request merges from
 * @param headSha           the commit at the head of the pull request
 * @param headRepositoryRef the repository the head branch lives in; {@code null} when the forge no longer knows it
 * @param headWritable      whether the credential can push to the head branch
 * @param changedPaths      the changed paths, in the provider's order
 * @param pathCompleteness  whether the changed-path list is the whole list, or why it is not
 * @since 0.1
 */
@Builder
public record PullRequest(State state, String title, String body, String baseBranch, String headBranch,
                          String headSha, @Nullable RepositoryRef headRepositoryRef, boolean headWritable,
                          List<String> changedPaths, PathCompleteness pathCompleteness) {

    /** The state of a pull request. */
    public enum State {
        /** The pull request is open. */
        OPEN,
        /** The pull request was closed without being merged. */
        CLOSED,
        /** The pull request was merged. */
        MERGED
    }

    /** Whether the changed-path list is the whole list, or why it is not. */
    public enum PathCompleteness {
        /** The list holds every changed path. */
        COMPLETE,
        /**
         * The list was cut by the page cap of the read, or the {@code Link} {@code next} header of a page pointed
         * outside the origin the provider is bound to, so the read stopped there.
         */
        PAGE_CAP,
        /** Every page was read, but the forge returned fewer paths than the pull request changes. */
        PROVIDER_RESULT_CAP
    }

    /**
     * @param state             the state
     * @param title             the title
     * @param body              the body
     * @param baseBranch        the base branch
     * @param headBranch        the head branch
     * @param headSha           the head commit
     * @param headRepositoryRef the head repository, or {@code null}
     * @param headWritable      whether the head is writable
     * @param changedPaths      the changed paths
     * @param pathCompleteness  the completeness of the changed paths
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the title, a branch or the head commit is blank
     */
    public PullRequest {
        Objects.requireNonNull(state, "state");
        requireText(title, "title");
        Objects.requireNonNull(body, "body");
        requireText(baseBranch, "baseBranch");
        requireText(headBranch, "headBranch");
        requireText(headSha, "headSha");
        changedPaths = List.copyOf(changedPaths);
        Objects.requireNonNull(pathCompleteness, "pathCompleteness");
    }

    /**
     * @return the repository the head branch lives in; empty when the forge no longer knows it (a deleted fork)
     */
    public Optional<RepositoryRef> headRepository() {
        return Optional.ofNullable(headRepositoryRef);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is never blank");
        }
    }
}
