/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The port through which a grading run checks an item out at its origin commit (Model Work § Grading; {@code Checkout}).
 * <p>
 * An item taken from the project's own repository runs in a job-private detached worktree of the project at that commit,
 * so its worker never sees a change made after the decision the item was taken from. A commit the repository does not
 * hold, or a checkout the runtime cannot create, answers empty, and the adapter then leaves the item ungraded: the record
 * must never claim `item_commit` for a run that happened elsewhere. Whether a commit is present at all is the grading
 * engine's `ItemCheckout` port, which records {@code CURRENT} for one the repository does not hold.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface ItemWorktrees {

    /**
     * @param repository the origin repository of the item
     * @param commit     the origin commit of the item
     * @return the job-private detached worktree at that commit; empty when it is not present or not creatable
     */
    Optional<Path> checkout(URI repository, String commit);

    /** @return a source of worktrees that holds no commit, so every origin-commit item is left ungraded */
    static ItemWorktrees none() {
        return (repository, commit) -> Optional.empty();
    }
}
