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
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariants of the init phase of the plan unit, {@code INV-INIT-1} and {@code INV-INIT-2} (PM-IMPL-5), as path
 * and dominator checks over the merged plan unit.
 * <ul>
 * <li>{@link InvariantId#INV_INIT_1}: {@code init.classify} is a state of the unit; the routes from the start state
 * into {@code init.ready} that pass no {@code init.classify} are those of the default unit (the {@code recipe},
 * {@code local_changes} and {@code pr} sources), so no new route skips the recipe scorer and the lane router; every
 * question state of phase {@code init} returns every resolution to {@code init.classify}; and every state that lies on
 * every path of the default unit into {@code init.ready}, the stamp of {@code main_sha} included, lies on every path
 * of the unit into it.</li>
 * <li>{@link InvariantId#INV_INIT_2}: every path into {@code outline.light} passes {@code init.light-gate}; every path
 * from {@code outline.light} into {@code outline.gate} passes {@code outline.light-regate}; and every transition of the
 * default unit that leaves {@code init.light-gate} or {@code outline.light-regate} (the {@code light -> deep}
 * ratchet) is a transition of the unit.</li>
 * </ul>
 *
 * @since 0.1
 */
final class InitInvariants {

    /** The re-entrant classification state of the init phase. */
    static final String CLASSIFY = "init.classify";

    /** The last state of the init phase. */
    static final String READY = "init.ready";

    /** The light-lane gate of the init phase. */
    static final String LIGHT_GATE = "init.light-gate";

    /** The authoring state of the light lane. */
    static final String OUTLINE_LIGHT = "outline.light";

    /** The re-gate of every new task of the light lane. */
    static final String LIGHT_REGATE = "outline.light-regate";

    /** The phase value of the init phase. */
    private static final String PHASE_INIT = "init";

    private InitInvariants() {
    }

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
            if (state.behavior() instanceof StateBehavior.QuestionWait && PHASE_INIT.equals(state.phase())) {
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
        if (context.state(OUTLINE_LIGHT).isPresent()) {
            if (context.state(LIGHT_GATE).isEmpty()) {
                context.reportUnit(id, "the unit has '%s' but no '%s'".formatted(OUTLINE_LIGHT, LIGHT_GATE));
            } else if (!context.dominates(LIGHT_GATE, OUTLINE_LIGHT)) {
                context.reportState(id, context.state(OUTLINE_LIGHT).orElseThrow(),
                        "a path reaches '%s' without passing '%s'".formatted(OUTLINE_LIGHT, LIGHT_GATE));
            }
            regate(context);
        }
        for (var gate : List.of(LIGHT_GATE, LIGHT_REGATE)) {
            context.referenceState(gate).ifPresent(ratchet -> Context.transitions(ratchet).stream()
                    .filter(transition -> !Context.hasTransition(context.unit(), gate, transition.outcomeName(),
                            transition.targetStateId()))
                    .forEach(transition -> context.report(id, gate, context.state(gate)
                                    .map(state -> state.location()).orElse(context.unit().location()),
                            "the ratchet transition '%s' -> '%s' of '%s' is missing".formatted(
                                    transition.outcomeName(), transition.targetStateId(), gate))));
        }
    }

    private static void regate(Context context) {
        var id = InvariantId.INV_INIT_2;
        var gate = context.state(OutlineInvariants.GATE);
        if (gate.isEmpty()) {
            return;
        }
        var unregated = Context.avoiding(context.graph(), OUTLINE_LIGHT, Set.of(LIGHT_REGATE));
        if (unregated.contains(OutlineInvariants.GATE)) {
            context.reportState(id, gate.get(), "a path from '%s' reaches '%s' without passing '%s'"
                    .formatted(OUTLINE_LIGHT, OutlineInvariants.GATE, LIGHT_REGATE));
        }
    }
}
