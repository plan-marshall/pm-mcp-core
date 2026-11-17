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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Where the plan stands when a version is composed (PM-IMPL-5): the unit and state the plan is in, and the calling
 * unit and state of every frame of its call stack. The position rule keeps a version only if, per unit, it contains
 * every one of them; state ids are stable per unit across versions, so the position maps onto the new version
 * unchanged.
 *
 * @param current   the unit and state the plan is in, or the state the composing transition enters for a server event
 * @param callStack the calling unit and state of every frame of the call stack, outermost first
 * @since 0.1
 */
public record InstancePosition(UnitState current, List<UnitState> callStack) {

    /**
     * Refuses a missing component and copies the call stack into an unmodifiable list that keeps its order.
     *
     * @param current   the unit and state the plan is in
     * @param callStack the calling unit and state of every frame
     */
    public InstancePosition {
        Objects.requireNonNull(current, "current");
        callStack = List.copyOf(callStack);
    }

    /**
     * @param state the state the plan is in in the plan unit, with an empty call stack
     * @return the position
     */
    public static InstancePosition at(String state) {
        return new InstancePosition(new UnitState(PlanInstanceComposer.PLAN_UNIT, state), List.of());
    }

    /**
     * @param unit  the unit that declares the state
     * @param state the state the plan is in
     * @return the position in the unit, with an empty call stack
     */
    public static InstancePosition of(String unit, String state) {
        return new InstancePosition(new UnitState(unit, state), List.of());
    }

    /**
     * @param unit      the unit that declares the current state
     * @param state     the state the plan is in
     * @param callStack the calling unit and state of every frame of the call stack, outermost first
     * @return the position
     */
    public static InstancePosition of(String unit, String state, List<UnitState> callStack) {
        return new InstancePosition(new UnitState(unit, state), callStack);
    }

    /**
     * @param statesByUnit the states of a version by the unit that declares them
     * @return the positions the version does not contain, the current state first, each once
     */
    List<UnitState> missingFrom(Map<String, Set<String>> statesByUnit) {
        var missing = new ArrayList<UnitState>();
        if (!contains(statesByUnit, current)) {
            missing.add(current);
        }
        callStack.stream().filter(position -> !contains(statesByUnit, position) && !missing.contains(position))
                .forEach(missing::add);
        return missing;
    }

    private static boolean contains(Map<String, Set<String>> statesByUnit, UnitState position) {
        return statesByUnit.getOrDefault(position.unit(), Set.of()).contains(position.state());
    }

    /**
     * One unit and one state of it: a component of a position. State ids are unique only within a unit, so the unit is
     * part of the pair.
     *
     * @param unit  the id of the unit that declares the state
     * @param state the state id
     * @since 0.1
     */
    public record UnitState(String unit, String state) {

        /**
         * Refuses a missing component.
         *
         * @param unit  the unit id
         * @param state the state id
         */
        public UnitState {
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(state, "state");
        }
    }
}
