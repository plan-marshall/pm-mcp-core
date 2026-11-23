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
     * so every untracked file is listed by its own path and no untracked directory is collapsed to {@code newdir/}: an
     * untracked directory is expanded to the files below it here, by the site. Two entries are the exceptions: an
     * embedded repository is reported as {@code newdir/} because git does not descend into another repository, and a
     * modified submodule by its name alone ({@code sub}), without a trailing slash, while the worktree holds it as a
     * directory. A rename is listed by both sides, the old path and the new one, as git's rename detection reports it.
     * A listed path is unquoted — the site reads the status NUL-separated ({@code -z}) — so a name outside ASCII, a name
     * with a double quote, a backslash or a control character arrives verbatim and not as an escape;
     * {@code core.quotePath=false} alone is not enough, because git keeps escaping those characters. The staging set of
     * the scope ({@code StagingSets}, over {@code CommitPathRefusals}) puts a name that no commit can stage in the
     * {@code outside} set — the {@code newdir/} entry of an embedded repository, the name of a modified submodule, a
     * tracked directory that a file replaced — so such an entry is a footprint violation naming it, never a commit that
     * fails on it. The tracked-tree rule stays silent when it cannot read the repository: a checkout whose repository or
     * index cannot be read stages by the textual and file-system rules alone, and the commit reports the condition. This
     * is the contract the
     * assembly's implementation follows; nothing in this module observes the
     * site's list, so a site that lists a submodule with a trailing slash is caught by no test here
     * ({@code PM-WATCH-DSL-48}).
     *
     * @param context the call
     * @return the dirty files of the target checkout, each a repository-relative path, with {@code .marshall/local/**}
     *         left out
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
