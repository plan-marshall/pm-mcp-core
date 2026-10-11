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
 * The status a {@code completes <step_id> as <status>} clause settles a step with (PM-IMPL-5).
 *
 * @since 0.1
 */
public enum StepCompletionStatus {
    /** The step did its work. */
    DONE,
    /** The step had nothing to do. */
    SKIPPED,
    /** The step failed. */
    FAILED,
    /** The step was settled by a consumed waiver. */
    WAIVED
}
