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
 * A {@code completes <step_id> as <status>} clause of a transition (PM-IMPL-5).
 *
 * @param stepId   the identifier of the completed step
 * @param status   the status the step is settled with
 * @param location where the clause was read
 * @since 0.1
 */
public record StepCompletion(String stepId, StepCompletionStatus status, SourceLocation location) {
}
