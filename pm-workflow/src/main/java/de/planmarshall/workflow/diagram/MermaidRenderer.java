/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a unit as a Mermaid {@code stateDiagram-v2} (PM-IMPL-5).
 * <p>
 * The states are grouped by their phase value into one composite state {@code phase_<phase>} each, in the order in
 * which the phase values are first declared; the states of phase {@code inherit} (of a sub-workflow or an entity unit)
 * stay at the top level. Inside a composite come the state declarations ({@code state "<id>" as <alias>}, the alias
 * with {@code .} and {@code -} mapped to {@code _}), the description lines ({@code <alias> : <kind> <target>}, see
 * {@link StateTransitions#description(StateNode)}) and the transitions between two states of that phase; every other
 * transition follows at the top level, after the arrow {@code [*]} into the start state, because Mermaid admits no
 * transition between inner states of different composites inside a composite block.
 * <p>
 * A transition is labelled {@code <rel> <link_id> [<form>] [<guard>] / <outcome>} when it routes the action of a
 * declared link, and {@code <trigger> [<guard>] / <outcome>} when the engine takes it, the trigger being the kind of
 * the state, {@code question} or the task label (see {@link StateTransitions#engineTrigger}); the guard is the link's
 * guard or the result guard and is omitted when there is none. With an active state, the style {@code active} is
 * defined and assigned to that state; without one, neither line is emitted.
 *
 * @since 0.1
 */
final class MermaidRenderer {

    private static final String INDENT = "    ";
    private static final String INHERIT = "inherit";
    private static final String PHASE_PREFIX = "phase_";
    private static final String ACTIVE_STYLE = "classDef active fill:#90ee90";

    private final StringBuilder out = new StringBuilder();

    private MermaidRenderer() {
    }

    /**
     * Renders a unit.
     *
     * @param definition  the unit
     * @param activeState the state to highlight; empty for none
     * @return the diagram, every line ended by a line feed
     */
    static String render(WorkflowDefinition definition, Optional<String> activeState) {
        var renderer = new MermaidRenderer();
        renderer.renderUnit(definition, activeState);
        return renderer.out.toString();
    }

    private void renderUnit(WorkflowDefinition definition, Optional<String> activeState) {
        var transitions = StateTransitions.of(definition);
        var groups = phaseGroups(definition);
        line(0, "stateDiagram-v2");
        groups.forEach((phase, states) -> {
            if (INHERIT.equals(phase)) {
                declare(1, states);
            } else {
                line(1, "state " + PHASE_PREFIX + alias(phase) + " {");
                declare(2, states);
                states.stream().flatMap(state -> transitions.outgoing(state.id()).stream())
                        .filter(edge -> isInsideComposite(definition, edge))
                        .forEach(edge -> line(2, transition(edge)));
                line(1, "}");
            }
        });
        line(1, "[*] --> " + alias(definition.startState()));
        definition.states().values().stream().flatMap(state -> transitions.outgoing(state.id()).stream())
                .filter(edge -> !isInsideComposite(definition, edge))
                .forEach(edge -> line(1, transition(edge)));
        activeState.ifPresent(state -> {
            line(1, ACTIVE_STYLE);
            line(1, "class " + alias(state) + " active");
        });
    }

    private void declare(int depth, List<StateNode> states) {
        states.forEach(state -> line(depth, "state \"%s\" as %s".formatted(state.id(), alias(state.id()))));
        states.forEach(state -> line(depth, alias(state.id()) + " : " + StateTransitions.description(state)));
    }

    private void line(int depth, String text) {
        out.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    /**
     * Groups the states of a unit by phase value, the groups and the states of a group in declaration order.
     */
    private static Map<String, List<StateNode>> phaseGroups(WorkflowDefinition definition) {
        var groups = new LinkedHashMap<String, List<StateNode>>();
        definition.states().values()
                .forEach(state -> groups.computeIfAbsent(state.phase(), phase -> new ArrayList<>()).add(state));
        return groups;
    }

    /**
     * Tells whether a transition is emitted inside the composite of its source state's phase: its source and its
     * target are states of the same phase, which is not {@code inherit}.
     */
    private static boolean isInsideComposite(WorkflowDefinition definition, StateTransitions.Edge edge) {
        var phase = edge.source().phase();
        var target = definition.states().get(edge.target());
        return !INHERIT.equals(phase) && target != null && phase.equals(target.phase());
    }

    private static String transition(StateTransitions.Edge edge) {
        var label = new StringBuilder();
        edge.link().ifPresentOrElse(
                link -> label.append(StateTransitions.lower(link.rel())).append(' ').append(link.idPattern())
                        .append(" [").append(StateTransitions.lower(link.form())).append(']'),
                () -> label.append(StateTransitions.engineTrigger(edge.source().behavior())));
        edge.guard().ifPresent(guard -> label.append(" [").append(guard).append(']'));
        label.append(" / ").append(edge.outcome());
        return "%s --> %s : %s".formatted(alias(edge.source().id()), alias(edge.target()), label);
    }

    /**
     * Maps a state identifier or a phase value to a Mermaid identifier: {@code .} and {@code -} become {@code _}.
     */
    private static String alias(String id) {
        return id.replace('.', '_').replace('-', '_');
    }
}
