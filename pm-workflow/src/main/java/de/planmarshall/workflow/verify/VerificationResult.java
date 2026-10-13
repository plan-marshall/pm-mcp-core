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
 * Every violation and every advisory {@link WorkflowValidator} found in one unit (PM-IMPL-5). A violation refuses the
 * unit; an advisory reports a finding the specification asks to be reported without failing validation, so far the
 * partial configuration reachability of Rule 3 (a state or transition that some {@code depends_on_config}
 * combinations do not reach). Both lists are ordered alike: by the declaration order of their state, released names
 * after every state, and within a state by rule.
 *
 * @param violations the violations in that order; empty when the unit passes
 * @param advisories the advisories in that order; they never decide whether the unit passes
 * @since 0.1
 */
public record VerificationResult(List<Violation> violations, List<Violation> advisories) {

    /**
     * Copies the violations and the advisories into immutable lists.
     *
     * @param violations the violations
     * @param advisories the advisories
     * @throws NullPointerException if a list or one of its elements is {@code null}
     */
    public VerificationResult {
        violations = List.copyOf(violations);
        advisories = List.copyOf(advisories);
    }

    /**
     * @return {@code true} when the unit violates no rule, whatever its advisories
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
