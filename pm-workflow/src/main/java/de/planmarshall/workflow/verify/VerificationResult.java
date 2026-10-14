/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.List;

/**
 * Every violation {@link WorkflowValidator} found in one unit (PM-IMPL-5). A violation refuses the unit. The list is
 * ordered by the declaration order of the violation's state, released names after every state, and within a state by
 * rule.
 *
 * @param violations the violations in that order; empty when the unit passes
 * @since 0.1
 */
public record VerificationResult(List<Violation> violations) {

    /**
     * Copies the violations into an immutable list.
     *
     * @param violations the violations
     * @throws NullPointerException if the list or one of its elements is {@code null}
     */
    public VerificationResult {
        violations = List.copyOf(violations);
    }

    /**
     * @return {@code true} when the unit violates no rule
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
