/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOperations.Identity;

/**
 * The port through which the commit primitives read the scope they commit for (PM-WF-16): the target checkout, its
 * dirty paths, the operator's stored values and the breaches of the footprint check the site observes.
 * <p>
 * The assembly implements it, resolving every answer per call from the scope the context addresses, so one stateless
 * instance serves every scope; this module implements none of it. The primitives read no store and build no git command
 * line themselves.
 *
 * @since 0.1
 */
public interface CommitSite {

    /**
     * @param context the call
     * @return the root of the target checkout: the plan's worktree, the shared epic worktree or the main checkout
     */
    Path worktreeRoot(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the type of the scope that commits
     */
    ScopeType scopeType(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return whether the epic archives (its archive flag); {@code false} for every scope but an archiving epic
     */
    boolean archiving(WorkflowExecutionContext context);

    /**
     * The status is read with untracked files listed one by one ({@code git status --porcelain --untracked-files=all}),
     * so every untracked file is listed by its own path and no entry is a directory: an untracked directory is expanded
     * to the files below it here, by the site, and never collapsed to {@code newdir/}. A directory entry is no legal
     * entry; the commit refuses it.
     *
     * @param context the call
     * @return the dirty files of the target checkout, each a repository-relative file path, no entry a directory, with
     *         {@code .marshall/local/**} left out
     */
    List<String> dirtyPaths(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the trailer values the operator stored for the project; empty when the project stores none
     */
    Optional<CommitTrailer.Stored> localTrailer(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the trailer values the machine stores as the default of every project; empty when it stores none
     */
    Optional<CommitTrailer.Stored> machineTrailer(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the effective commit identity of the operator, the author of every server commit; empty while no level
     *         holds one
     */
    Optional<Identity> commitIdentity(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the agent identity resolved from the provider credential, the committer; empty when none resolves
     */
    Optional<Identity> agentIdentity(WorkflowExecutionContext context);

    /**
     * @param context the call
     * @return the summary of the diff of the target checkout, the facts a commit message is composed from
     */
    DiffSummary diffSummary(WorkflowExecutionContext context);

    /**
     * Check 2 of the footprint check.
     *
     * @param context the call
     * @return the pinned build files of the enrolled wrappers that changed in the target checkout
     */
    List<String> pinnedFilesChanged(WorkflowExecutionContext context);

    /**
     * Check 3 of the footprint check.
     *
     * @param context the call
     * @return for a plan, the paths of the main checkout outside {@code .marshall/local/} that are absent from its entry
     *         baseline or changed since it; empty for every other scope
     */
    List<String> mainCheckoutBreaches(WorkflowExecutionContext context);

    /**
     * The summary of a diff: the facts of {@code git.diff-summary}.
     *
     * @param changedFileCount the number of changed files, at least 0
     * @param moduleScope      the module all changed files belong to, {@code null} when they span several or none
     * @param suggestedTypes   the Conventional Commit types the changes suggest, in no order; empty when none is
     *                         suggested
     * @since 0.1
     */
    record DiffSummary(int changedFileCount, @Nullable String moduleScope, Set<String> suggestedTypes) {

        /**
         * @param changedFileCount the number of changed files
         * @param moduleScope      the module scope
         * @param suggestedTypes   the suggested types, copied
         * @throws IllegalArgumentException if the count is negative
         */
        public DiffSummary {
            if (changedFileCount < 0) {
                throw new IllegalArgumentException("changedFileCount must not be negative: " + changedFileCount);
            }
            suggestedTypes = Set.copyOf(Objects.requireNonNull(suggestedTypes, "suggestedTypes"));
        }
    }
}
