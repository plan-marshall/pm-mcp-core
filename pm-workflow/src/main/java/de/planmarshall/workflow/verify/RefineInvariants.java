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

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;
import de.planmarshall.workflow.verify.CompiledInvariants.Edge;

/**
 * The invariant of the refine phase of the plan unit, {@code INV-REFINE-1} (PM-IMPL-5).
 * <p>
 * A deep path is a path through the states of phase {@code refine}. Every such path, from a transition that enters
 * the phase to a transition that leaves it for a state that is not a fallback, passes {@code refine.baseline-reconcile}
 * and {@code refine.analyze}, except where the default unit itself leaves the phase without passing them (a re-entry
 * at {@code refine.analyze} after a remediation); a transition that leaves the phase without passing one of them and
 * that the default unit does not have is a violation. {@code refine.qgate}, the Q-Gate of a lesson-derived deep plan,
 * is a state of the unit with every transition of the default unit into it, and its {@code blocking} verdict calls
 * {@code remediation.qgate-findings}. The confidence aggregation and the single re-run ceiling are compiled into the
 * primitive of {@code refine.analyze}, which Rule 1 binds.
 *
 * @since 0.1
 */
final class RefineInvariants {

    /** The analysis of the refine phase. */
    static final String ANALYZE = "refine.analyze";

    /** The Q-Gate of a lesson-derived deep plan. */
    static final String QGATE = "refine.qgate";

    /** The phase value of the refine phase. */
    static final String PHASE_REFINE = "refine";

    /** The states a path may leave the phase for without passing the deep-path states. */
    private static final Set<String> FALLBACKS = Set.of(CompiledInvariants.PLAN_RETURN, CompiledInvariants.HAND_BACK,
            CompiledInvariants.PLAN_ABORTED);

    private RefineInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(InvariantId.INV_REFINE_1, RefineInvariants::deepPath);
    }

    private static void deepPath(Context context) {
        var id = InvariantId.INV_REFINE_1;
        for (var required : List.of(LaneEntryIds.DEEP_ENTRY, ANALYZE)) {
            context.requirePresent(id, required);
            var known = new LinkedHashSet<String>();
            exitsAvoiding(context, context.referenceStateMap(), context.referenceGraph(), required)
                    .forEach(edge -> known.add(edge.key()));
            for (var edge : exitsAvoiding(context, context.stateMap(), context.graph(), required)) {
                if (!known.contains(edge.key())) {
                    context.reportTransition(id, edge.source(), edge.transition(),
                            "the transition '%s' -> '%s' leaves the refine phase on a path that does not pass '%s'"
                                    .formatted(edge.transition().outcomeName(), edge.transition().targetStateId(),
                                            required));
                }
            }
        }
        if (context.requirePresent(id, QGATE)) {
            for (var edge : context.referenceEdges()) {
                var transition = edge.transition();
                if (QGATE.equals(transition.targetStateId()) && !context.hasTransition(edge.source().id(),
                        transition.outcomeName(), QGATE)) {
                    context.reportUnit(id, "the transition '%s' of '%s' into '%s' of the default unit is missing"
                            .formatted(transition.outcomeName(), edge.source().id(), QGATE));
                }
            }
        }
        requireRemediationOnBlocking(context, id, QGATE);
    }

    /**
     * Requires the {@code blocking} verdict of a Q-Gate state to call {@code remediation.qgate-findings}.
     *
     * @param context the unit under check
     * @param id      the invariant the check belongs to
     * @param qgate   the Q-Gate state
     */
    static void requireRemediationOnBlocking(Context context, InvariantId id, String qgate) {
        context.state(qgate).ifPresent(state -> {
            for (var transition : Context.transitionsOn(state, CompiledInvariants.BLOCKING)) {
                var callsRemediation = context.state(transition.targetStateId()).flatMap(Context::callee)
                        .filter(CompiledInvariants.QGATE_REMEDIATION::equals).isPresent();
                if (!callsRemediation) {
                    context.reportTransition(id, state, transition,
                            "the verdict 'blocking' of '%s' enters '%s', which does not call '%s'".formatted(qgate,
                                    transition.targetStateId(), CompiledInvariants.QGATE_REMEDIATION));
                }
            }
        });
    }

    /**
     * @return the transitions that leave the refine phase for a terminal that is not a fallback, from a refine state
     *         reached from an entry of the phase without passing the required state
     */
    private static List<Edge> exitsAvoiding(Context context, Map<String, StateNode> states, UnitGraph graph,
            String required) {
        var reached = reachedRefineStates(context, states, refineEntries(context, states), graph, required);
        return reached.values().stream()
                .flatMap(source -> Context.transitions(source).stream().map(transition -> new Edge(source, transition)))
                .filter(edge -> {
                    var target = states.get(edge.transition().targetStateId());
                    return target != null && Context.isTerminal(target) && !FALLBACKS.contains(target.id());
                })
                .toList();
    }

    /**
     * @return the refine states a path enters the phase at: each starting state of the unit (its start state or an
     *         entry-bound state) that is a refine state, then every refine state a transition from outside the phase
     *         targets, in declaration order
     */
    private static Set<String> refineEntries(Context context, Map<String, StateNode> states) {
        var entries = new LinkedHashSet<String>();
        context.unit().startingStates().stream()
                .filter(start -> isRefine(context, states.get(start)))
                .forEach(entries::add);
        for (var edge : context.allEdges()) {
            var target = states.get(edge.transition().targetStateId());
            if (!isRefine(context, edge.source()) && isRefine(context, target)) {
                entries.add(target.id());
            }
        }
        return entries;
    }

    /**
     * Walks the refine phase breadth-first from its entries without passing the required state.
     *
     * @return the refine states reached, in the order they were reached
     */
    private static Map<String, StateNode> reachedRefineStates(Context context, Map<String, StateNode> states,
            Set<String> entries, UnitGraph graph, String required) {
        var reached = new LinkedHashMap<String, StateNode>();
        var queue = new ArrayDeque<String>();
        entries.stream()
                .filter(entry -> !entry.equals(required))
                .forEach(entry -> visit(reached, queue, entry, states.get(entry)));
        while (!queue.isEmpty()) {
            for (var next : graph.successors(queue.poll())) {
                var nextState = states.get(next);
                if (isRefine(context, nextState) && !next.equals(required)) {
                    visit(reached, queue, next, nextState);
                }
            }
        }
        return reached;
    }

    /**
     * Records a reached state and queues it for the walk unless it was reached before.
     */
    private static void visit(Map<String, StateNode> reached, Queue<String> queue, String id, StateNode state) {
        if (reached.putIfAbsent(id, state) == null) {
            queue.add(id);
        }
    }

    private static boolean isRefine(Context context, StateNode state) {
        return state != null && context.inPhase(state, PHASE_REFINE);
    }
}
