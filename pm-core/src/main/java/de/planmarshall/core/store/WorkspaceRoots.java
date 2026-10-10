/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * The roots a project path may lie in (PM-SEC-2): the enrolled repository root and the linked git worktrees
 * that are live at the moment of the check.
 * <p>
 * The record carries what its caller has verified and reads no enrolment file itself. A worktree named here is
 * a candidate only: {@link PathConfinement} accepts it when its git common directory resolves to the enrolled
 * root, and ignores it otherwise.
 *
 * @param enrolledRoot  the enrolled repository root, never {@code null}
 * @param liveWorktrees the roots of the live linked worktrees, never {@code null}; may be empty
 * @since 0.1
 */
public record WorkspaceRoots(Path enrolledRoot, Set<Path> liveWorktrees) {

    /**
     * @param enrolledRoot  the enrolled repository root
     * @param liveWorktrees the roots of the live linked worktrees, copied
     */
    public WorkspaceRoots {
        Objects.requireNonNull(enrolledRoot, "enrolledRoot");
        liveWorktrees = Set.copyOf(liveWorktrees);
    }
}
