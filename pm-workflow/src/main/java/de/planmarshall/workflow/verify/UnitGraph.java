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
import java.util.stream.Stream;

import de.planmarshall.workflow.ast.GuardExpr;
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
 * {@link WorkflowValidator} searches it for reachability and exit paths, and {@link TransitionFuzzer} reads the exits
 * of a state from the same behaviour switches and the atoms of a guard from {@link #atomsOf(GuardExpr)}.
 */
final class UnitGraph {

    /** The reserved outcome of a task state that the engine routes by default when no transition declares it. */
    static final String LOST_OUTCOME = "lost";

    /** The atom of the built-in guard {@code in_worktree}. */
    static final String IN_WORKTREE = "in_worktree";

    /** The atom of the built-in guard {@code has_open_findings}. */
    static final String HAS_OPEN_FINDINGS = "has_open_findings";

    /** The atom of the built-in guard {@code all_tasks_completed}. */
    static final String ALL_TASKS_COMPLETED = "all_tasks_completed";

    /** The prefix of the atom of a {@code predicate:} guard, followed by the primitive's identifier. */
    static final String PREDICATE_PREFIX = "predicate:";

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

    /**
     * @param expr a guard expression
     * @return the primitive of every {@code predicate:} atom of the expression, in source order
     */
    static Stream<String> predicatesOf(GuardExpr expr) {
        return switch (expr) {
            case GuardExpr.CustomPredicate predicate -> Stream.of(predicate.primitiveId());
            case GuardExpr.And and -> Stream.concat(predicatesOf(and.left()), predicatesOf(and.right()));
            case GuardExpr.Or or -> Stream.concat(predicatesOf(or.left()), predicatesOf(or.right()));
            case GuardExpr.Not not -> predicatesOf(not.operand());
            case GuardExpr.InWorktree _, GuardExpr.HasOpenFindings _, GuardExpr.AllTasksCompleted _ -> Stream.empty();
        };
    }

    /**
     * Names the atoms of a guard expression: {@link #IN_WORKTREE}, {@link #HAS_OPEN_FINDINGS} and
     * {@link #ALL_TASKS_COMPLETED} for the built-in guards, {@link #PREDICATE_PREFIX} followed by the primitive's
     * identifier for a {@code predicate:} guard, and the atoms of the operands of {@code and}, {@code or} and
     * {@code not}. The switch is exhaustive over the variants of {@link GuardExpr}, so a new variant fails to compile
     * here; an atom expression yields exactly its own name.
     *
     * @param expr a guard expression
     * @return the name of every atom of the expression, in source order, a repeated atom once per occurrence
     */
    static Stream<String> atomsOf(GuardExpr expr) {
        return switch (expr) {
            case GuardExpr.InWorktree _ -> Stream.of(IN_WORKTREE);
            case GuardExpr.HasOpenFindings _ -> Stream.of(HAS_OPEN_FINDINGS);
            case GuardExpr.AllTasksCompleted _ -> Stream.of(ALL_TASKS_COMPLETED);
            case GuardExpr.CustomPredicate(var primitiveId) -> Stream.of(PREDICATE_PREFIX + primitiveId);
            case GuardExpr.And(var left, var right) -> Stream.concat(atomsOf(left), atomsOf(right));
            case GuardExpr.Or(var left, var right) -> Stream.concat(atomsOf(left), atomsOf(right));
            case GuardExpr.Not(var operand) -> atomsOf(operand);
        };
    }

    /**
     * @param behavior what a state does
     * @return all transitions of the behavior in declaration order
     */
    static List<OutcomeTransition> allTransitions(StateBehavior behavior) {
        var transitions = new ArrayList<>(engineTransitions(behavior));
        for (var link : declaredLinks(behavior)) {
            transitions.addAll(link.transitions());
        }
        return transitions;
    }

    /**
     * Tells whether a link identifier pattern is an implicit link identifier for the state:
     * {@code ack}, {@code consult:<role>}, {@code answer:<question_id>}, or {@code step_out} when
     * the state has an implicit step out exit.
     *
     * @param scope the scope of the unit
     * @param state a state of the unit
     * @param idPattern the identifier pattern of the link
     * @return {@code true} when the pattern matches an implicit link
     */
    static boolean isImplicitLink(WorkflowScope scope, StateNode state, String idPattern) {
        if ("ack".equals(idPattern) || idPattern.startsWith("consult:") || idPattern.startsWith("answer:")) {
            return true;
        }
        return hasImplicitExit(scope, state) && "step_out".equals(idPattern);
    }

    /**
     * @param definition the unit
     * @return all transition targets across all states of the unit
     */
    static Set<String> allTargets(WorkflowDefinition definition) {
        var targets = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            for (var transition : allTransitions(state.behavior())) {
                targets.add(transition.targetStateId());
            }
        }
        return Collections.unmodifiableSet(targets);
    }

    /**
     * @param definition the unit
     * @return all distinct configuration keys declared in {@code depends_on_config} across the unit
     */
    static List<String> configKeysOf(WorkflowDefinition definition) {
        var keys = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            for (var link : declaredLinks(state.behavior())) {
                link.guard().ifPresent(guard -> keys.addAll(guard.dependsOnConfig()));
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Generates all 2^N boolean assignments for the given configuration keys.
     *
     * @param keys the configuration keys
     * @return the list of assignments
     */
    static List<Map<String, Boolean>> configCombinations(List<String> keys) {
        if (keys.isEmpty()) {
            return List.of(Map.of());
        }
        int count = 1 << keys.size();
        var combinations = new ArrayList<Map<String, Boolean>>(count);
        for (int i = 0; i < count; i++) {
            var assignment = new LinkedHashMap<String, Boolean>();
            for (int j = 0; j < keys.size(); j++) {
                assignment.put(keys.get(j), ((i >> j) & 1) == 1);
            }
            combinations.add(Collections.unmodifiableMap(assignment));
        }
        return Collections.unmodifiableList(combinations);
    }

    /**
     * Constructs a graph under a specific configuration assignment.
     *
     * @param definition the unit
     * @param configAssignment the boolean assignment for configuration keys
     * @return the transition graph under the assignment
     */
    static UnitGraph of(WorkflowDefinition definition, Map<String, Boolean> configAssignment) {
        var successors = new LinkedHashMap<String, Set<String>>();
        var predecessors = new LinkedHashMap<String, Set<String>>();
        for (var state : definition.states().values()) {
            predecessors.computeIfAbsent(state.id(), id -> new LinkedHashSet<>());
            var targets = new LinkedHashSet<String>();
            for (var transition : activeTransitions(state.behavior(), configAssignment)) {
                targets.add(transition.targetStateId());
                predecessors.computeIfAbsent(transition.targetStateId(), id -> new LinkedHashSet<>()).add(state.id());
            }
            successors.put(state.id(), Collections.unmodifiableSet(targets));
        }
        return new UnitGraph(Collections.unmodifiableMap(successors), Collections.unmodifiableMap(predecessors));
    }

    /**
     * Tells which transitions of a behavior are active under a configuration assignment.
     */
    static List<OutcomeTransition> activeTransitions(StateBehavior behavior, Map<String, Boolean> configAssignment) {
        var transitions = new ArrayList<>(engineTransitions(behavior));
        for (var link : declaredLinks(behavior)) {
            if (isLinkActive(link, configAssignment)) {
                transitions.addAll(link.transitions());
            }
        }
        return transitions;
    }

    /**
     * A declared link is active if it has no depends_on_config or all its declared config keys evaluate to true.
     */
    static boolean isLinkActive(LinkSpec link, Map<String, Boolean> configAssignment) {
        if (link.guard().isEmpty() || link.guard().get().dependsOnConfig().isEmpty()) {
            return true;
        }
        return link.guard().get().dependsOnConfig().stream()
                .allMatch(key -> configAssignment.getOrDefault(key, Boolean.TRUE));
    }

    /**
     * Identifies all outcome transitions reached from the start state under a configuration assignment.
     */
    static Set<OutcomeTransition> reachableTransitions(WorkflowDefinition definition,
            Map<String, Boolean> configAssignment) {
        var graph = of(definition, configAssignment);
        var reachableStates = graph.forwardClosure(Set.of(definition.startState()));
        var reached = new LinkedHashSet<OutcomeTransition>();
        for (var stateId : reachableStates) {
            var state = definition.states().get(stateId);
            if (state != null) {
                reached.addAll(activeTransitions(state.behavior(), configAssignment));
            }
        }
        return reached;
    }
}
