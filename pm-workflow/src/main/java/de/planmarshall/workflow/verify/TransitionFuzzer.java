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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The seeded transition fuzzer (PM-TEST-3): it walks a unit that passed {@link WorkflowValidator} under random
 * assignments of its guard atoms and reports every reached non-terminal state none of whose exits holds.
 * <p>
 * Each run draws one two-valued assignment over the atoms the guards of the unit name: a built-in guard
 * ({@code in_worktree}, {@code has_open_findings}, {@code all_tasks_completed}) or a {@code predicate:} primitive,
 * each once, in the order of their first occurrence in the guards of the declared links, the states and their links
 * taken in declaration order and each guard read from left to right; an atom no guard names is not drawn. The atoms of
 * a guard are named by one exhaustive switch over {@link GuardExpr}, which the evaluation of a guard reads as well, so
 * what is drawn and what is looked up cannot diverge. The run then walks from the start state, or under the same
 * assignment from each entry-bound state in turn for a unit with entries, each walk for at most
 * {@link FuzzConfiguration#maxSteps()} steps. A declared link holds when it has no guard or its guard is true under
 * the assignment; the transitions the engine takes on its own (of {@code auto}, {@code job},
 * {@code composite_job}, {@code job llm}, {@code call} and the resolution of a question) always hold, and so do the
 * implicit {@code step_out} of a link state of a sub-workflow and the default {@code lost} route of a task state,
 * which leave the unit. At a state without a holding exit the run records a {@link DeadState} and stops; otherwise it
 * takes one holding exit and one of its targets at random. A terminal state ends the run, and so does a target that is
 * not a state of the unit.
 * <p>
 * The guards are evaluated two-valued over the drawn atoms; the engine's three-valued evaluation, the persisted
 * sub-states, the condition overlays and the bound jobs are not modelled. The generator is created from the seed with
 * the fixed algorithm {@code L64X128MixRandom}, so the same configuration gives the same report.
 *
 * @since 0.1
 */
public final class TransitionFuzzer {

    /** The algorithm of the generator; fixed, so a seed gives the same sequence on every JDK. */
    static final String ALGORITHM = "L64X128MixRandom";

    /**
     * Walks the unit with the configuration.
     *
     * @param definition    a unit that passed {@link WorkflowValidator}
     * @param configuration the seed, the number of runs and the greatest number of steps of a run
     * @return every dead state reached, each once with its first witness
     * @throws NullPointerException if an argument is {@code null}
     */
    public FuzzReport fuzz(WorkflowDefinition definition, FuzzConfiguration configuration) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(configuration, "configuration");
        var random = RandomGeneratorFactory.of(ALGORITHM).create(configuration.seed());
        var atoms = atomsOf(definition);
        var deadStates = new LinkedHashMap<String, DeadState>();
        for (var index = 0; index < configuration.runs(); index++) {
            var assignment = new LinkedHashMap<String, Boolean>();
            atoms.forEach(atom -> assignment.put(atom, random.nextBoolean()));
            var witnessRun = index + 1;
            for (var root : definition.startingStates()) {
                walk(definition, root, configuration.maxSteps(), assignment, random).ifPresent(state -> deadStates
                        .computeIfAbsent(state.id(), id -> new DeadState(id, state.location(), assignment,
                                witnessRun)));
            }
        }
        return new FuzzReport(configuration, configuration.runs(), List.copyOf(deadStates.values()));
    }

    /**
     * Walks one run from a starting state and returns the dead state it stopped at, if any.
     */
    private static Optional<StateNode> walk(WorkflowDefinition definition, String root, int maxSteps,
            Map<String, Boolean> assignment, RandomGenerator random) {
        var current = definition.states().get(root);
        for (var step = 0; step < maxSteps && current != null; step++) {
            if (current.behavior() instanceof StateBehavior.TerminalState) {
                return Optional.empty();
            }
            var exits = holdingExits(definition, current, assignment);
            if (exits.isEmpty()) {
                return Optional.of(current);
            }
            var exit = exits.get(random.nextInt(exits.size()));
            if (exit.isEmpty()) {
                return Optional.empty();
            }
            current = definition.states().get(exit.get(random.nextInt(exit.size())).targetStateId());
        }
        return Optional.empty();
    }

    /**
     * Lists the exits of a state that hold under the assignment, each as the transitions it may take; an implicit
     * exit, which leaves the unit, is an empty list.
     */
    private static List<List<OutcomeTransition>> holdingExits(WorkflowDefinition definition, StateNode state,
            Map<String, Boolean> assignment) {
        var exits = new ArrayList<List<OutcomeTransition>>();
        var engineTransitions = UnitGraph.engineTransitions(state.behavior());
        if (!engineTransitions.isEmpty()) {
            exits.add(engineTransitions);
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            var guard = link.guard();
            var holds = guard.isEmpty() || holds(guard.get().expr(), assignment);
            if (holds && !link.transitions().isEmpty()) {
                exits.add(link.transitions());
            }
        }
        if (UnitGraph.hasImplicitExit(definition.scope(), state)) {
            exits.add(List.of());
        }
        return exits;
    }

    /**
     * Evaluates a guard two-valued under the assignment; an atom is looked up by the name
     * {@link UnitGraph#atomsOf(GuardExpr)} gives it, the same name {@link #atomsOf(WorkflowDefinition)} drew it under.
     */
    private static boolean holds(GuardExpr expr, Map<String, Boolean> assignment) {
        return switch (expr) {
            case GuardExpr.And(var left, var right) -> holds(left, assignment) && holds(right, assignment);
            case GuardExpr.Or(var left, var right) -> holds(left, assignment) || holds(right, assignment);
            case GuardExpr.Not(var operand) -> !holds(operand, assignment);
            case GuardExpr.InWorktree _, GuardExpr.HasOpenFindings _, GuardExpr.AllTasksCompleted _,
                GuardExpr.CustomPredicate _ -> assignment.get(UnitGraph.atomsOf(expr).toList().getFirst());
        };
    }

    /**
     * Collects the atoms the guards of the unit name, each once, in the order of their first occurrence: the states
     * and their declared links in declaration order, each guard read from left to right.
     *
     * @param definition the unit
     * @return the atoms to draw a value for in every run
     */
    static List<String> atomsOf(WorkflowDefinition definition) {
        var atoms = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            for (var link : UnitGraph.declaredLinks(state.behavior())) {
                link.guard().ifPresent(guard -> UnitGraph.atomsOf(guard.expr()).forEach(atoms::add));
            }
        }
        return List.copyOf(atoms);
    }
}
