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

/**
 * The levels of the one total order in which transaction locks are acquired (PM-IMPL-7). The declaration order is
 * the acquisition order: a transaction that needs locks of several levels takes them from the first constant to the
 * last, which is what keeps two transactions from waiting on each other.
 * <p>
 * The process locks of the runtime (its singleton lock and the on-demand start lock) are held for a process and not
 * for a transaction; they have no level.
 *
 * @since 0.1
 */
public enum LockLevel {
    /** The lock of the one workspace scope of a project. */
    WORKSPACE,
    /** The lock of a plan; several plan locks are ordered by their plan id. */
    PLAN,
    /** The lock of an epic; several epic locks are ordered by their epic id. */
    EPIC,
    /** The lock of the git operations on the shared epic worktree of a project. */
    EPICS_WORKTREE,
    /** The lock of the lesson store of a project. */
    LESSON_STORE,
    /** The lock of the local configuration of a project. */
    LOCAL_CONFIGURATION,
    /** The lock of the derived facts of a project; a refresh inside a scope transaction takes it after the scope lock. */
    DERIVED_FACTS,
    /** The lock of the build timings of a project. */
    BUILD_TIMINGS,
    /** The lock of the task queue of a project; taken after a scope lock and before a machine store lock. */
    QUEUE,
    /** The lock of a machine store; several machine store locks are ordered alphabetically by their lock path. */
    MACHINE_STORE,
    /** The lock of an append-only file that is locked directly; nothing is acquired after it. */
    LEAF
}
