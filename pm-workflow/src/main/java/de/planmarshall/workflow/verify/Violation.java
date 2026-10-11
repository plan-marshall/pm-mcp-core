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

import java.util.Objects;

import de.planmarshall.workflow.ast.SourceLocation;

/**
 * One violation of a {@link VerificationRule} (PM-IMPL-5). A violation is a value, never an exception.
 *
 * @param rule     the check the unit violates
 * @param stateId  the state the violation belongs to; for {@link VerificationRule#RELEASED_STATE} the released name
 * @param location where the offending node was read
 * @param message  what is wrong, naming the primitive, outcome, parameter, link or released name concerned
 * @since 0.1
 */
public record Violation(VerificationRule rule, String stateId, SourceLocation location, String message) {

    /**
     * Refuses a missing component.
     *
     * @param rule     the check the unit violates
     * @param stateId  the state the violation belongs to
     * @param location where the offending node was read
     * @param message  what is wrong
     * @throws NullPointerException if a component is {@code null}
     */
    public Violation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }
}
