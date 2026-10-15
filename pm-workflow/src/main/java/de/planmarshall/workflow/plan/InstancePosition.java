/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Where the plan stands when a version is composed (PM-IMPL-5): its current state and the calling state of every frame
 * of its call stack. The position rule keeps a version only if it contains every one of them; state ids are stable
 * across versions, so the position maps onto the new version unchanged.
 *
 * @param currentState the state the plan is in, or the state the composing transition enters for a server event
 * @param callStack    the calling state of every frame of the call stack, outermost first
 * @since 0.1
 */
public record InstancePosition(String currentState, List<String> callStack) {

    /**
     * Refuses a missing component and copies the call stack into an unmodifiable list that keeps its order.
     *
     * @param currentState the current state
     * @param callStack    the calling states
     */
    public InstancePosition {
        Objects.requireNonNull(currentState, "currentState");
        callStack = List.copyOf(callStack);
    }

    /**
     * @param state the state the plan is in, with an empty call stack
     * @return the position
     */
    public static InstancePosition at(String state) {
        return new InstancePosition(state, List.of());
    }

    /**
     * @param stateIds the states of a version
     * @return the states of the position the version does not contain, the current state first
     */
    List<String> missingFrom(Set<String> stateIds) {
        var missing = new ArrayList<String>();
        if (!stateIds.contains(currentState)) {
            missing.add(currentState);
        }
        callStack.stream().filter(state -> !stateIds.contains(state) && !missing.contains(state))
                .forEach(missing::add);
        return missing;
    }
}
