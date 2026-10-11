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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The transition graph of one unit (PM-IMPL-5): the successor and the predecessor edges of every state, built from
 * the transitions of every {@link StateBehavior} variant and of every declared link. A target that is not a state of
 * the unit is kept as a successor; whether a target exists is not this graph's concern.
 * <p>
 * {@link WorkflowValidator} searches it for reachability and exit paths.
 */
final class UnitGraph {

    /** The reserved outcome of a task state that the engine routes by default when no transition declares it. */
    static final String LOST_OUTCOME = "lost";

    private final Map<String, Set<String>> successors;

    private final Map<String, Set<String>> predecessors;

    private UnitGraph(Map<String, Set<String>> successors, Map<String, Set<String>> predecessors) {
        this.successors = successors;
        this.predecessors = predecessors;
    }

    /**
     * @param definition the unit
     * @return the graph of the unit's states, in declaration order
     */
    static UnitGraph of(WorkflowDefinition definition) {
        var successors = new LinkedHashMap<String, Set<String>>();
        var predecessors = new LinkedHashMap<String, Set<String>>();
        for (var state : definition.states().values()) {
            predecessors.computeIfAbsent(state.id(), id -> new LinkedHashSet<>());
            var targets = new LinkedHashSet<String>();
            for (var transition : allTransitions(state.behavior())) {
                targets.add(transition.targetStateId());
                predecessors.computeIfAbsent(transition.targetStateId(), id -> new LinkedHashSet<>()).add(state.id());
            }
            successors.put(state.id(), Collections.unmodifiableSet(targets));
        }
        return new UnitGraph(Collections.unmodifiableMap(successors), Collections.unmodifiableMap(predecessors));
    }

    /**
     * @param stateId a state of the unit
     * @return the targets of its transitions in declaration order; empty for a terminal or an unknown state
     */
    Set<String> successors(String stateId) {
        return successors.getOrDefault(stateId, Set.of());
    }

    /**
     * @param stateId a state of the unit, or a target that is none
     * @return the states with a transition into it, in declaration order
     */
    Set<String> predecessors(String stateId) {
        return predecessors.getOrDefault(stateId, Set.of());
    }

    /**
     * @param starts the identifiers to start from
     * @return every identifier reachable from them along the successor edges, the starts included
     */
    Set<String> forwardClosure(Set<String> starts) {
        return closure(starts, true);
    }

    /**
     * @param ends the identifiers to end in
     * @return every identifier from which one of them is reachable, the ends included
     */
    Set<String> backwardClosure(Set<String> ends) {
        return closure(ends, false);
    }

    private Set<String> closure(Set<String> seeds, boolean forward) {
        var seen = new LinkedHashSet<>(seeds);
        var queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var next : forward ? successors(current) : predecessors(current)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * @param behavior what a state does
     * @return the links the state declares; empty for every behaviour but links and question states
     */
    static List<LinkSpec> declaredLinks(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks links -> links.links();
            case StateBehavior.QuestionWait question -> question.links();
            case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> List.of();
        };
    }

    /**
     * @param behavior what a state does
     * @return the transitions the engine takes on its own, outside of any declared link: those of {@code auto},
     *         {@code job}, {@code composite_job}, {@code job llm}, {@code call} and the resolution outcomes of a
     *         question; empty for links and terminal states
     */
    static List<OutcomeTransition> engineTransitions(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.AutoStep auto -> auto.transitions();
            case StateBehavior.JobStep job -> job.transitions();
            case StateBehavior.ModelJob modelJob -> modelJob.transitions();
            case StateBehavior.SubWorkflowCall call -> call.transitions();
            case StateBehavior.QuestionWait question -> question.transitions();
            case StateBehavior.InteractiveLinks _, StateBehavior.TerminalState _ -> List.of();
        };
    }

    /**
     * Tells whether a state leaves the unit through an exit no transition declares: the implicit {@code step_out} of
     * a link state of a {@link WorkflowScope#SUBWORKFLOW} unit, and the default route of the reserved outcome
     * {@code lost} of a task state that declares no {@code lost} transition.
     *
     * @param scope the scope of the unit
     * @param state a state of the unit
     * @return {@code true} when the state has such an implicit exit
     */
    static boolean hasImplicitExit(WorkflowScope scope, StateNode state) {
        return switch (state.behavior()) {
            case StateBehavior.InteractiveLinks _ -> scope == WorkflowScope.SUBWORKFLOW;
            case StateBehavior.ModelJob modelJob -> modelJob.transitions().stream()
                    .noneMatch(transition -> LOST_OUTCOME.equals(transition.outcomeName()));
            case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.SubWorkflowCall _,
                StateBehavior.QuestionWait _, StateBehavior.TerminalState _ -> false;
        };
    }

    private static List<OutcomeTransition> allTransitions(StateBehavior behavior) {
        var transitions = new ArrayList<>(engineTransitions(behavior));
        for (var link : declaredLinks(behavior)) {
            transitions.addAll(link.transitions());
        }
        return transitions;
    }
}
