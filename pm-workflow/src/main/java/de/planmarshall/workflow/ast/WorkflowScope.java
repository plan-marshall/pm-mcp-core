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
 * The scope a workflow unit runs in (PM-IMPL-5).
 *
 * @since 0.1
 */
public enum WorkflowScope {
    /** A plan. */
    PLAN,
    /** An epic. */
    EPIC,
    /** The workspace. */
    WORKSPACE,
    /** An entity. */
    ENTITY,
    /** A unit that is called from another unit. */
    SUBWORKFLOW
}
