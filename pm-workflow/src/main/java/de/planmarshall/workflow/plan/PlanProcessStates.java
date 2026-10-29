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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The state of every node of a plan's process, derived from the plan's append-only history and the instance version in
 * force (Store Schemas § The Plan's History; Workflow DSL Specification, Plan Instance, Display).
 * <p>
 * Nothing is stored beside the history: a runtime that restarts reads {@code mcp/history.jsonl} and the version's
 * composed files and derives the same states. A state entered and not left is {@code running}; the plan's current state
 * of that kind is {@code waiting_for_operator} when it rests in a question wait, {@code running} otherwise. A state
 * entered and left is {@code done}, or {@code failed} with the outcome it settled when its terminal outcome is of the
 * class {@link OutcomeClass#FAILURE}. A state never entered is {@code planned} while the plan runs, and
 * {@code not_taken} once the plan reached a terminal state. A phase call the composition pruned is
 * {@code not_in_this_plan} with its prune reason.
 *
 * @since 0.1
 */
public final class PlanProcessStates {

    private PlanProcessStates() {
    }

    /**
     * @param instance the instance version in force
     * @param history  the plan's history, in the order of the log
     * @return one node state per state of the plan unit and of every composed unit, in the declaration order, and one
     *         per pruned call site and pruned state of the version
     * @throws NullPointerException if an argument is {@code null}
     */
    public static List<NodeState> derive(PlanInstance instance, List<PlanHistoryLine> history) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(history, "history");
        var last = new LinkedHashMap<String, PlanHistoryLine>();
        for (var line : history) {
            if (line.event() == PlanHistoryLine.Event.ENTERED || line.event() == PlanHistoryLine.Event.LEFT) {
                last.put(line.node(), line);
            }
        }
        var ended = instance.unit().states().values().stream()
                .anyMatch(state -> state.terminalOutcome().isPresent() && last.containsKey(state.id())
                        && last.get(state.id()).event() == PlanHistoryLine.Event.ENTERED);
        var current = currentState(history);
        var nodes = new ArrayList<NodeState>();
        nodes.addAll(prunedNodeStates(instance));
        append(nodes, instance.unit(), last, ended, current);
        instance.units().forEach(unit -> append(nodes, unit.definition(), last, ended, current));
        return List.copyOf(nodes);
    }

    private static void append(List<NodeState> nodes, WorkflowDefinition unit,
            Map<String, PlanHistoryLine> last, boolean ended, Optional<String> current) {
        for (var state : unit.states().values()) {
            if (nodes.stream().anyMatch(node -> node.node().equals(state.id()))) {
                continue;
            }
            nodes.add(nodeState(state, last.get(state.id()), ended, current));
        }
    }

    private static NodeState nodeState(StateNode state, PlanHistoryLine line, boolean ended,
            Optional<String> current) {
        if (line == null) {
            return new NodeState(state.id(), ended ? State.NOT_TAKEN : State.PLANNED, Optional.empty(),
                    Optional.empty());
        }
        if (line.event() == PlanHistoryLine.Event.LEFT) {
            var failed = state.terminalOutcome().filter(outcome -> outcome.outcomeClass() == OutcomeClass.FAILURE);
            return failed.isPresent()
                    ? new NodeState(state.id(), State.FAILED, Optional.of(line.outcome() != null
                    ? line.outcome() : failed.get().id()), Optional.empty())
                    : new NodeState(state.id(), State.DONE, Optional.ofNullable(line.outcome()), Optional.empty());
        }
        if (state.terminalOutcome().isPresent()) {
            var failure = state.terminalOutcome().filter(outcome -> outcome.outcomeClass() == OutcomeClass.FAILURE);
            return failure.isPresent()
                    ? new NodeState(state.id(), State.FAILED, Optional.of(failure.get().id()), Optional.empty())
                    : new NodeState(state.id(), State.DONE, state.terminalOutcome().map(outcome -> outcome.id()),
                    Optional.empty());
        }
        var waiting = state.behavior() instanceof StateBehavior.QuestionWait
                && current.filter(state.id()::equals).isPresent();
        return new NodeState(state.id(), waiting ? State.WAITING_FOR_OPERATOR : State.RUNNING, Optional.empty(),
                Optional.empty());
    }

    /**
     * @return the state the plan is in, read from the history alone: the node of the last {@code entered} line that no
     *         later {@code left} line of that node follows
     */
    private static Optional<String> currentState(List<PlanHistoryLine> history) {
        String current = null;
        for (var line : history) {
            if (line.event() == PlanHistoryLine.Event.ENTERED) {
                current = line.node();
            } else if (line.event() == PlanHistoryLine.Event.LEFT && line.node().equals(current)) {
                current = null;
            }
        }
        return Optional.ofNullable(current);
    }

    /**
     * @return one {@code not_in_this_plan} node per pruned state and per pruned call site, with the prune reason as the
     *         reason of the node
     */
    private static List<NodeState> prunedNodeStates(PlanInstance instance) {
        var nodes = new ArrayList<NodeState>();
        instance.header().pruned().forEach(pruned -> {
            switch (pruned.element()) {
                case STATE -> nodes.add(new NodeState(pruned.id(), State.NOT_IN_THIS_PLAN, Optional.empty(),
                        Optional.of(pruned.reason().wireName())));
                case CALL -> {
                    var slash = pruned.id().indexOf('/');
                    var callSite = slash > 0 ? pruned.id().substring(0, slash) : pruned.id();
                    nodes.add(new NodeState(callSite, State.NOT_IN_THIS_PLAN, Optional.empty(),
                            Optional.of(pruned.reason().wireName())));
                }
                default -> {
                    // a pruned step, link or policy action is no node of the process
                }
            }
        });
        return nodes;
    }

    /**
     * The derived state of one node of the plan's process.
     *
     * @param node    the state id
     * @param state   the derived state
     * @param outcome the outcome the node settled or failed with, when it has one
     * @param reason  the prune reason of a {@link State#NOT_IN_THIS_PLAN} node
     * @since 0.1
     */
    public record NodeState(String node, State state, Optional<String> outcome, Optional<String> reason) {

        /** @throws NullPointerException if a component is {@code null} */
        public NodeState {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The derived state of a node of the plan's process.
     *
     * @since 0.1
     */
    public enum State {

        /** The node was entered and left. */
        DONE("done"),

        /** The node was entered and not left; the plan runs on. */
        RUNNING("running"),

        /** The plan rests in the node's operator wait. */
        WAITING_FOR_OPERATOR("waiting_for_operator"),

        /** The node was entered and failed with its outcome. */
        FAILED("failed"),

        /** The node was never entered while the plan runs. */
        PLANNED("planned"),

        /** The node was never entered and the plan reached a terminal state. */
        NOT_TAKEN("not_taken"),

        /** The composition pruned the node's call. */
        NOT_IN_THIS_PLAN("not_in_this_plan");

        private final String wireName;

        State(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value a client shows for the state
         */
        public String wireName() {
            return wireName;
        }
    }
}
