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
 * {@link WorkflowValidator} searches it for reachability and exit paths from its roots, the start state or every
 * entry-bound state ({@link WorkflowDefinition#startingStates()}), and for the exits reached from the entries
 * (interface rule 4, {@link #outcomesReachedFromEntries(WorkflowDefinition)}); {@link TransitionFuzzer} reads the
 * exits of a state from the same behaviour switches and the atoms of a guard from {@link #atomsOf(GuardExpr)}.
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

    /**
     * Interface rule 4, the exits of a unit with entries: which outcomes the terminals reachable from at least one of
     * its entries report. That every state is reachable from the starting point or from an entry is the reachability
     * over {@link WorkflowDefinition#startingStates()}.
     *
     * @param definition the unit
     * @return the outcome of every terminal reachable from an entry-bound state, in declaration order of the
     *         terminals; empty for a unit that binds no entry
     */
    Set<String> outcomesReachedFromEntries(WorkflowDefinition definition) {
        var entryStates = new LinkedHashSet<String>();
        definition.entryBindings().values().forEach(binding -> entryStates.add(binding.stateId()));
        var reached = forwardClosure(entryStates);
        var outcomes = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            if (reached.contains(state.id())) {
                state.terminalOutcome().ifPresent(outcome -> outcomes.add(outcome.id()));
            }
        }
        return Collections.unmodifiableSet(outcomes);
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
     * Tells whether a state of a unit that passed {@link WorkflowValidator} leaves the unit through an exit no
     * transition declares: the verifier admits an undeclared {@code lost} only where the engine routes it by default,
     * so every task state without a {@code lost} transition takes that route.
     *
     * @param scope the scope of the unit
     * @param state a state of the unit
     * @return {@code true} when the state has such an implicit exit
     * @see #hasImplicitExit(WorkflowScope, StateNode, boolean)
     */
    static boolean hasImplicitExit(WorkflowScope scope, StateNode state) {
        return hasImplicitExit(scope, state, true);
    }

    /**
     * Tells whether a state leaves the unit through an exit no transition declares: the implicit {@code step_out} of
     * a link state of a {@link WorkflowScope#SUBWORKFLOW} unit, and, where the engine routes the reserved outcome
     * {@code lost} by default, the default route of a task state that declares no {@code lost} transition (Rule 2).
     *
     * @param scope            the scope of the unit
     * @param state            a state of the unit
     * @param defaultLostRoute whether the engine routes an undeclared {@code lost} of the unit to the phase's fallback:
     *                         in a plan unit and in a sub-workflow every caller chain of which ends in plan units
     * @return {@code true} when the state has such an implicit exit
     */
    static boolean hasImplicitExit(WorkflowScope scope, StateNode state, boolean defaultLostRoute) {
        return switch (state.behavior()) {
            case StateBehavior.InteractiveLinks _ -> scope == WorkflowScope.SUBWORKFLOW;
            case StateBehavior.ModelJob modelJob -> defaultLostRoute && modelJob.transitions().stream()
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
     * Finds all states reachable from the roots without passing through any state in {@code producers} before
     * reaching the state.
     *
     * @param roots     the states control enters the unit at: its start state or every entry-bound state
     *                  ({@link WorkflowDefinition#startingStates()})
     * @param producers the set of states that produce the artifact
     * @return the set of states reached without a preceding producer, the roots included
     */
    Set<String> reachableWithoutProducers(List<String> roots, Set<String> producers) {
        var seen = new LinkedHashSet<>(roots);
        var queue = new ArrayDeque<String>();
        roots.stream().filter(root -> !producers.contains(root)).forEach(queue::add);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var next : successors(current)) {
                if (seen.add(next) && !producers.contains(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * Finds all elementary simple cycles in the unit graph.
     *
     * @param definition the workflow definition
     * @return the list of simple cycles, each represented as a sequence of state IDs ending in the start state
     */
    static List<List<String>> findSimpleCycles(WorkflowDefinition definition) {
        var stateIds = new ArrayList<>(definition.states().keySet());
        var graph = of(definition);
        var cycles = new ArrayList<List<String>>();
        var stateIndex = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < stateIds.size(); i++) {
            stateIndex.put(stateIds.get(i), i);
        }

        for (int i = 0; i < stateIds.size(); i++) {
            var startId = stateIds.get(i);
            var path = new ArrayList<String>();
            path.add(startId);
            var inPath = new LinkedHashSet<String>();
            inPath.add(startId);
            dfsCycles(graph, startId, startId, i, stateIndex, path, inPath, cycles);
        }
        return cycles;
    }

    private static void dfsCycles(UnitGraph graph, String current, String start, int startIndex,
            Map<String, Integer> stateIndex, List<String> path, Set<String> inPath, List<List<String>> cycles) {
        for (var next : graph.successors(current)) {
            if (next.equals(start)) {
                var cycle = new ArrayList<>(path);
                cycle.add(start);
                cycles.add(Collections.unmodifiableList(cycle));
            } else {
                var nextIndex = stateIndex.get(next);
                if (nextIndex != null && nextIndex > startIndex && inPath.add(next)) {
                    path.add(next);
                    dfsCycles(graph, next, start, startIndex, stateIndex, path, inPath, cycles);
                    path.removeLast();
                    inPath.remove(next);
                }
            }
        }
    }
}
