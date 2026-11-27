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
import java.util.Map;
import java.util.Set;

import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;
import lombok.experimental.UtilityClass;

/**
 * The invariants of the init phase of the plan unit, {@code INV-INIT-1} and {@code INV-INIT-2} (PM-IMPL-5), as path
 * and dominator checks over the composed plan unit's call-resolved graph.
 * <ul>
 * <li>{@link InvariantId#INV_INIT_1}: {@code init.classify} is a state of the unit; the routes from the start state
 * into {@code init.ready} that pass no {@code init.classify} are those of the default unit (the {@code recipe},
 * {@code local_changes} and {@code pr} sources), so no new route skips the recipe scorer and the lane router; every
 * question state of phase {@code init} returns every resolution to {@code init.classify}; and every state that lies on
 * every path of the default unit into {@code init.ready}, the stamp of {@code main_sha} included, lies on every path
 * of the unit into it.</li>
 * <li>{@link InvariantId#INV_INIT_2}, per lane (Workflow DSL Specification, Compiled Invariants): wherever the light
 * envelope {@link LaneEntryIds#LIGHT_ENTRY} ({@code outline.light}) is a state of the unit, every path from it into
 * {@code outline.gate} passes the light gate {@link LaneEntryIds#LIGHT_GATE} ({@code outline.light-gate}); a path that
 * escalates passes the deep entry {@link LaneEntryIds#DEEP_ENTRY} ({@code refine.analyze}) and is a path of the deep
 * lane, and a path through a fallback ({@code plan.return}, {@code finalize.hand-back}) leaves the lane; neither owes
 * the light gate. For the lane {@code light} or an unknown lane, every transition of the default unit
 * that leaves {@code outline.light} or {@code outline.light-gate}, the {@code light -> deep} ratchet among them, is a
 * transition of the unit. A version of another lane, such as a {@code deep} version that holds no state of the light
 * route, owes no ratchet.</li>
 * </ul>
 *
 * @since 0.1
 */
@UtilityClass
final class InitInvariants {

    /** The re-entrant classification state of the init phase. */
    static final String CLASSIFY = "init.classify";

    /** The last state of the init phase. */
    static final String READY = "init.ready";

    /**
     * The states that end a path of the light lane from {@code outline.light}: the light gate, the deep entry its
     * escalation enters, and the fallbacks, by which the plan leaves its lane altogether.
     */
    private static final Set<String> LIGHT_PATH_ENDS = Set.of(LaneEntryIds.LIGHT_GATE, LaneEntryIds.DEEP_ENTRY,
            CompiledInvariants.PLAN_RETURN, CompiledInvariants.HAND_BACK, CompiledInvariants.PLAN_ABORTED);

    /** The phase value of the init phase. */
    private static final String PHASE_INIT = "init";

    /** The wire name of the light lane, the one lane besides an unknown lane that needs the ratchet. */
    static final String LANE_LIGHT = "light";

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_INIT_1, InitInvariants::classification,
                InvariantId.INV_INIT_2, InitInvariants::lightLane);
    }

    private static void classification(Context context) {
        var id = InvariantId.INV_INIT_1;
        context.requirePresent(id, CLASSIFY);
        context.requireNoNewBypass(id, Set.of(CLASSIFY), READY);
        for (var state : context.states()) {
            if (state.behavior() instanceof StateBehavior.QuestionWait && context.inPhase(state, PHASE_INIT)) {
                Context.transitions(state).stream()
                        .filter(transition -> !CLASSIFY.equals(transition.targetStateId()))
                        .forEach(transition -> context.reportTransition(id, state, transition,
                                "the init question '%s' routes '%s' to '%s', not back to '%s'".formatted(state.id(),
                                        transition.outcomeName(), transition.targetStateId(), CLASSIFY)));
            }
        }
        context.requireDominatorsOf(id, state -> READY.equals(state.id()), state -> true);
    }

    private static void lightLane(Context context) {
        var id = InvariantId.INV_INIT_2;
        if (context.state(LaneEntryIds.LIGHT_ENTRY).isPresent()) {
            lightGate(context);
        }
        if (context.lane().filter(lane -> !LANE_LIGHT.equals(lane)).isPresent()) {
            return;
        }
        for (var light : List.of(LaneEntryIds.LIGHT_ENTRY, LaneEntryIds.LIGHT_GATE)) {
            context.referenceState(light).ifPresent(ratchet -> Context.transitions(ratchet).stream()
                    .filter(transition -> !context.hasTransition(light, transition.outcomeName(),
                            transition.targetStateId()))
                    .forEach(transition -> context.report(id, light, context.state(light)
                                    .map(StateNode::location).orElse(context.unit().location()),
                            "the ratchet transition '%s' -> '%s' of '%s' is missing".formatted(
                                    transition.outcomeName(), transition.targetStateId(), light))));
        }
    }

    private static void lightGate(Context context) {
        var gate = context.state(OutlineInvariants.GATE);
        if (gate.isEmpty()) {
            return;
        }
        var ungated = Context.avoiding(context.graph(), LaneEntryIds.LIGHT_ENTRY, LIGHT_PATH_ENDS);
        if (ungated.contains(OutlineInvariants.GATE)) {
            context.reportState(InvariantId.INV_INIT_2, gate.get(),
                    "a path from '%s' reaches '%s' without passing '%s'".formatted(LaneEntryIds.LIGHT_ENTRY,
                            OutlineInvariants.GATE, LaneEntryIds.LIGHT_GATE));
        }
    }
}
