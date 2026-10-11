/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

/**
 * The expression of a {@code guard} clause: a built-in guard, a {@code predicate:} primitive, or a combination of
 * expressions (PM-IMPL-5).
 *
 * @since 0.1
 */
public sealed interface GuardExpr
        permits GuardExpr.InWorktree,
        GuardExpr.HasOpenFindings,
        GuardExpr.AllTasksCompleted,
        GuardExpr.CustomPredicate,
        GuardExpr.And,
        GuardExpr.Or,
        GuardExpr.Not {

    /**
     * {@code in_worktree}: the scope works in its worktree.
     */
    record InWorktree() implements GuardExpr {
    }

    /**
     * {@code has_open_findings}: the scope has open findings.
     */
    record HasOpenFindings() implements GuardExpr {
    }

    /**
     * {@code all_tasks_completed}: every task of the scope is completed.
     */
    record AllTasksCompleted() implements GuardExpr {
    }

    /**
     * {@code predicate:<primitive_id>}: a read-only primitive decides.
     *
     * @param primitiveId the primitive that decides
     */
    record CustomPredicate(String primitiveId) implements GuardExpr {
    }

    /**
     * {@code <left> and <right>}.
     *
     * @param left  the left operand
     * @param right the right operand
     */
    record And(GuardExpr left, GuardExpr right) implements GuardExpr {
    }

    /**
     * {@code <left> or <right>}.
     *
     * @param left  the left operand
     * @param right the right operand
     */
    record Or(GuardExpr left, GuardExpr right) implements GuardExpr {
    }

    /**
     * {@code not <operand>}.
     *
     * @param operand the negated operand
     */
    record Not(GuardExpr operand) implements GuardExpr {
    }
}
