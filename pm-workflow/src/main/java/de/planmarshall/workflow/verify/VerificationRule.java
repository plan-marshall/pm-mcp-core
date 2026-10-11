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

/**
 * The closed set of checks {@link WorkflowValidator} runs (PM-IMPL-5). Every {@link Violation} names exactly one of
 * them, and each carries the number of the static verification rule of the specification it implements a part of.
 * This enum is the only source of the rule vocabulary.
 *
 * @since 0.1
 */
public enum VerificationRule {
    /** Every primitive a state or a link names, a {@code predicate:} guard included, is registered. */
    PRIMITIVE_BINDING(1),
    /** The free parameters of a link equal the {@code @FreeParam} components of its primitive's parameter record. */
    PARAMETER_EQUIVALENCE(1),
    /** The transitions of an {@code auto}, {@code job} or link action route every outcome of its primitive once. */
    OUTCOME_COVERAGE(2),
    /** Every state is reachable from the start state. */
    REACHABILITY(3),
    /** Every non-terminal state has a path to a terminal state, in a workspace unit back to the start state. */
    EXIT_PATH(3),
    /** The link identifiers of a state are unique, the implicit {@code step_out} included. */
    LINK_ID_UNIQUE(3),
    /** Every released state name is a state, or is migrated directly to a state. */
    RELEASED_STATE(5);

    private final int specificationRule;

    VerificationRule(int specificationRule) {
        this.specificationRule = specificationRule;
    }

    /**
     * @return the number of the static verification rule of the specification this check implements a part of
     */
    public int specificationRule() {
        return specificationRule;
    }
}
