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
 * One violation of a compiled invariant by a unit (PM-IMPL-5). A violation is a value with the file and the line of
 * the offending node, never an exception.
 *
 * @param id       the invariant the unit violates
 * @param stateId  the state the violation belongs to; the unit's identifier for a violation of the unit as a whole
 * @param location where the offending node was read: the state, the transition or, for the unit as a whole, the
 *                 unit header
 * @param message  what is wrong, naming the states, transitions, outcomes or values concerned
 * @since 0.1
 */
public record InvariantViolation(InvariantId id, String stateId, SourceLocation location, String message) {

    /**
     * Refuses a missing component.
     *
     * @param id       the invariant the unit violates
     * @param stateId  the state the violation belongs to
     * @param location where the offending node was read
     * @param message  what is wrong
     * @throws NullPointerException if a component is {@code null}
     */
    public InvariantViolation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }
}
