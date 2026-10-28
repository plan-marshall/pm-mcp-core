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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The call-resolved graph of a unit and the sub-workflows it calls (PM-IMPL-5, PM-WF-16).
 * <p>
 * The compiled invariants are decided over the graph the engine runs, not over the storage layout: a call edge leaves the
 * call state into the callee's entry, and a terminal of the callee returns to the caller's transition of that outcome.
 * Nothing is expanded in storage; the expansion exists for the verifier only, so a plan whose phases are separate
 * sub-workflows keeps the guarantees the merged plan unit stated (Workflow DSL, Compiled Invariants).
 * <p>
 * The expansion is context-insensitive: a unit called from several call sites contributes its states once, and a
 * terminal of it returns to the targets of every call site that routes its outcome. Reachability and dominance are
 * therefore decided over the union of the call paths, which is the conservative direction for a guarantee: a refactor
 * that opens a bypass on one call path is still seen.
 * <p>
 * The phase of a state is the phase of the unit that declares it; a unit that is no phase sub-workflow itself — the land
 * lane, a remediation unit, a chat — carries the phase of the first frame that calls it, so every sub-workflow called
 * within a phase reports that phase and no unit and no state declares a phase of its own.
 *
 * @since 0.1
 */
final class CallGraphExpansion {

    private final Map<String, WorkflowDefinition> units;

    private final Map<String, StateNode> states;

    private final Map<String, String> owners;

    private final Map<String, Phase> phases;

    private final UnitGraph graph;

    private CallGraphExpansion(Map<String, WorkflowDefinition> units, Map<String, StateNode> states,
            Map<String, String> owners, Map<String, Phase> phases, UnitGraph graph) {
        this.units = units;
        this.states = states;
        this.owners = owners;
        this.phases = phases;
        this.graph = graph;
    }

    /**
     * The expansion of a unit on its own: the compiled invariants of a unit contract — a remediation, commit,
     * re-planning or adjudication unit — are decided over that unit, never over the sub-workflows it calls.
     *
     * @param unit the unit
     * @return the expansion that holds only the unit's own states
     */
    static CallGraphExpansion single(WorkflowDefinition unit) {
        return new CallGraphExpansion(Map.of(unit.id(), unit), unit.states(),
                unit.states().keySet().stream().collect(Collectors.toMap(id -> id, id -> unit.id(),
                        (left, right) -> left, LinkedHashMap::new)),
                carriedPhases(unit, Map.of(unit.id(), unit)), UnitGraph.of(unit));
    }

    /**
     * Expands the calls of a root unit over the units it reaches.
     *
     * @param root     the unit the search starts from
     * @param callable the units a call may enter, by identifier; the root is used with its own content
     * @return the expansion of the root and of every unit reachable from it
     */
    static CallGraphExpansion of(WorkflowDefinition root, Map<String, WorkflowDefinition> callable) {
        var units = reachableUnits(root, callable);
        var states = new LinkedHashMap<String, StateNode>();
        var owners = new LinkedHashMap<String, String>();
        collect(states, owners, units);
        return new CallGraphExpansion(Collections.unmodifiableMap(units), Collections.unmodifiableMap(states),
                Collections.unmodifiableMap(owners), Collections.unmodifiableMap(carriedPhases(root, units)),
                graph(units));
    }

    /**
     * @return the root and every unit a call of a reachable unit enters, the root first
     */
    private static Map<String, WorkflowDefinition> reachableUnits(WorkflowDefinition root,
            Map<String, WorkflowDefinition> callable) {
        var units = new LinkedHashMap<String, WorkflowDefinition>();
        var order = new ArrayDeque<WorkflowDefinition>();
        units.put(root.id(), root);
        order.add(root);
        while (!order.isEmpty()) {
            addCallees(order.poll(), callable, units, order);
        }
        return units;
    }

    private static void addCallees(WorkflowDefinition unit, Map<String, WorkflowDefinition> callable,
            Map<String, WorkflowDefinition> units, ArrayDeque<WorkflowDefinition> order) {
        for (var state : unit.states().values()) {
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                var callee = callable.get(call.subWorkflowId());
                if (callee != null && units.putIfAbsent(callee.id(), callee) == null) {
                    order.add(callee);
                }
            }
        }
    }

    private static void collect(Map<String, StateNode> states, Map<String, String> owners,
            Map<String, WorkflowDefinition> units) {
        for (var definition : units.values()) {
            for (var state : definition.states().values()) {
                if (states.putIfAbsent(state.id(), state) == null) {
                    owners.put(state.id(), definition.id());
                }
            }
        }
    }

    /**
     * @return the units of the expansion by identifier, the root first
     */
    Map<String, WorkflowDefinition> units() {
        return units;
    }

    /**
     * @return every state of the expansion by identifier, the root's states first
     */
    Map<String, StateNode> states() {
        return states;
    }

    /**
     * @return the identifier of the unit that declares each state
     */
    Map<String, String> owners() {
        return owners;
    }

    /**
     * @return the phase of every unit of the expansion that carries one
     */
    Map<String, Phase> phases() {
        return phases;
    }

    /**
     * @return the graph of the expansion: the call edges lead into the callee's entry, and a terminal of the callee
     *         returns to the caller's transitions of its outcome
     */
    UnitGraph graph() {
        return graph;
    }

    /**
     * Carries the phase of the calling frame into every sub-workflow that is no phase unit itself: the phase sub-workflow
     * on the call stack decides the phase, so the land lane, a remediation unit and a chat report the phase they run in.
     */
    private static Map<String, Phase> carriedPhases(WorkflowDefinition root,
            Map<String, WorkflowDefinition> units) {
        var phases = new LinkedHashMap<String, Phase>();
        var seen = new LinkedHashSet<String>();
        var queue = new ArrayDeque<Map.Entry<String, Optional<Phase>>>();
        var rootPhase = Phase.ofUnit(root.id());
        rootPhase.ifPresent(phase -> phases.put(root.id(), phase));
        queue.add(Map.entry(root.id(), rootPhase));
        while (!queue.isEmpty()) {
            carry(queue, units, phases, seen);
        }
        return phases;
    }

    /**
     * Carries the phase of one queued frame into the sub-workflows it calls, and queues them.
     */
    private static void carry(ArrayDeque<Map.Entry<String, Optional<Phase>>> queue,
            Map<String, WorkflowDefinition> units, Map<String, Phase> phases, Set<String> seen) {
        var current = queue.poll();
        if (!seen.add(current.getKey())) {
            return;
        }
        var definition = units.get(current.getKey());
        if (definition == null) {
            return;
        }
        for (var state : definition.states().values()) {
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call
                    && units.containsKey(call.subWorkflowId())) {
                var phase = Phase.ofUnit(call.subWorkflowId()).or(current::getValue);
                phase.ifPresent(found -> phases.putIfAbsent(call.subWorkflowId(), found));
                queue.add(Map.entry(call.subWorkflowId(), phase));
            }
        }
    }

    private static UnitGraph graph(Map<String, WorkflowDefinition> units) {
        var successors = new LinkedHashMap<String, Set<String>>();
        var predecessors = new LinkedHashMap<String, Set<String>>();
        seedEdges(successors, predecessors, units);
        linkCallEdges(units, successors);
        indexPredecessors(predecessors, successors);
        return freeze(successors, predecessors);
    }

    /**
     * Seeds every state of the expansion with the targets of its own transitions.
     */
    private static void seedEdges(Map<String, Set<String>> successors, Map<String, Set<String>> predecessors,
            Map<String, WorkflowDefinition> units) {
        for (var definition : units.values()) {
            for (var state : definition.states().values()) {
                var targets = new LinkedHashSet<String>();
                predecessors.computeIfAbsent(state.id(), id -> new LinkedHashSet<>());
                for (var transition : UnitGraph.allTransitions(state.behavior())) {
                    targets.add(transition.targetStateId());
                }
                successors.put(state.id(), targets);
            }
        }
    }

    /**
     * Replaces the transitions of every call state by the edge into its callee's entry and routes the callee's
     * terminals back to the calling states.
     */
    private static void linkCallEdges(Map<String, WorkflowDefinition> units, Map<String, Set<String>> successors) {
        for (var definition : units.values()) {
            for (var state : definition.states().values()) {
                if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                    var callee = units.get(call.subWorkflowId());
                    if (callee != null) {
                        entered(callee, call).ifPresent(entry -> {
                            successors.get(state.id()).clear();
                            successors.get(state.id()).add(entry);
                        });
                        returnEdges(units, state, callee, successors);
                    }
                }
            }
        }
    }

    private static void indexPredecessors(Map<String, Set<String>> predecessors,
            Map<String, Set<String>> successors) {
        for (var entry : successors.entrySet()) {
            for (var target : entry.getValue()) {
                predecessors.computeIfAbsent(target, id -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
    }

    private static UnitGraph freeze(Map<String, Set<String>> successors, Map<String, Set<String>> predecessors) {
        var successorsCopy = new LinkedHashMap<String, Set<String>>();
        successors.forEach((id, targets) -> successorsCopy.put(id, Collections.unmodifiableSet(targets)));
        var predecessorsCopy = new LinkedHashMap<String, Set<String>>();
        predecessors.forEach((id, sources) -> predecessorsCopy.put(id, Collections.unmodifiableSet(sources)));
        return UnitGraph.of(Collections.unmodifiableMap(successorsCopy), Collections.unmodifiableMap(predecessorsCopy));
    }

    /**
     * Routes every terminal of the callee back to the targets the calling states route its outcome to.
     */
    private static void returnEdges(Map<String, WorkflowDefinition> units, StateNode caller,
            WorkflowDefinition callee, Map<String, Set<String>> successors) {
        var callSites = callSites(units, callee.id());
        if (callSites.isEmpty()) {
            return;
        }
        for (var terminal : callee.states().values()) {
            routeTerminal(terminal, callSites, successors);
        }
        for (var transition : UnitGraph.allTransitions(caller.behavior())) {
            successors.computeIfAbsent(transition.targetStateId(), id -> new LinkedHashSet<>());
        }
    }

    /**
     * @return every state of the expansion that calls the given unit, in declaration order
     */
    private static List<StateNode> callSites(Map<String, WorkflowDefinition> units, String calleeId) {
        return units.values().stream()
                .flatMap(definition -> definition.states().values().stream())
                .filter(state -> state.behavior() instanceof StateBehavior.SubWorkflowCall site
                        && calleeId.equals(site.subWorkflowId()))
                .toList();
    }

    /**
     * Adds to a terminal of the callee an edge into every target the call sites route its outcome to.
     */
    private static void routeTerminal(StateNode terminal, List<StateNode> callSites,
            Map<String, Set<String>> successors) {
        var outcome = terminal.terminalOutcome();
        if (outcome.isEmpty()) {
            return;
        }
        for (var site : callSites) {
            for (var transition : UnitGraph.allTransitions(site.behavior())) {
                if (outcome.get().id().equals(transition.outcomeName())) {
                    successors.get(terminal.id()).add(transition.targetStateId());
                }
            }
        }
    }

    /**
     * @return the state a call enters: its named entry, the callee's sole entry, or its start state
     */
    private static Optional<String> entered(WorkflowDefinition callee, StateBehavior.SubWorkflowCall call) {
        if (call.entry().isPresent()) {
            var binding = callee.entryBindings().get(call.entry().get());
            return binding == null ? Optional.empty() : Optional.of(binding.stateId());
        }
        if (callee.entryBindings().size() == 1) {
            return Optional.of(callee.entryBindings().values().iterator().next().stateId());
        }
        return callee.startState();
    }

    /**
     * @return every state of the expansion in declaration order, the root's states first
     */
    Collection<StateNode> orderedStates() {
        return List.copyOf(states.values());
    }

    /**
     * @return the transitions of the expansion, each with its source state
     */
    List<CompiledInvariants.Edge> edges() {
        var edges = new ArrayList<CompiledInvariants.Edge>();
        for (var state : states.values()) {
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                edges.add(new CompiledInvariants.Edge(state, transition));
            }
        }
        return edges;
    }

    /**
     * @return {@code true} when the expansion has a transition from the source on the outcome into the target
     */
    boolean hasTransition(String source, String outcome, String target) {
        var state = states.get(source);
        return state != null && UnitGraph.allTransitions(state.behavior()).stream()
                .anyMatch(transition -> outcome.equals(transition.outcomeName())
                        && target.equals(transition.targetStateId()));
    }

    /**
     * @return the transitions the expansion has from the source, in declaration order
     */
    static List<OutcomeTransition> transitionsOf(Map<String, StateNode> states, String source) {
        var state = states.get(source);
        return state == null ? List.of() : UnitGraph.allTransitions(state.behavior());
    }
}
