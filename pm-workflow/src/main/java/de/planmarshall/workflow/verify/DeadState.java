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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.workflow.ast.SourceLocation;

/**
 * A non-terminal state that {@link TransitionFuzzer} reached and that has no exit under the drawn assignment of the
 * guard atoms (PM-TEST-3): the first witness of a dead state.
 *
 * @param stateId    the identifier of the state
 * @param location   where the state was read
 * @param assignment the value of every guard atom the guards of the unit name in the run that reached it, in the
 *                   order the fuzzer draws them: the order of their first occurrence in the guards of the declared
 *                   links, in declaration order
 * @param run        the number of the run that reached it, counted from {@code 1}
 * @since 0.1
 */
public record DeadState(String stateId, SourceLocation location, Map<String, Boolean> assignment, int run) {

    /**
     * Refuses a missing component and copies the assignment into an unmodifiable map that keeps its order.
     *
     * @param stateId    the identifier of the state
     * @param location   where the state was read
     * @param assignment the value of every guard atom
     * @param run        the number of the run
     * @throws NullPointerException if a component is {@code null}
     */
    public DeadState {
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(location, "location");
        assignment = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(assignment, "assignment")));
    }
}
