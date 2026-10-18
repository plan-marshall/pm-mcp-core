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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a unit as a Mermaid {@code stateDiagram-v2} (PM-IMPL-5).
 * <p>
 * The states are grouped by their phase value into one composite state {@code phase_<phase>} each, in the order in
 * which the phase values are first declared; the states of phase {@code inherit} (of a sub-workflow or an entity unit)
 * stay at the top level. Every state and every composite gets a Mermaid identifier, its alias, that is unique within
 * the diagram: the alias of a state is its identifier with {@code .} and {@code -} mapped to {@code _}, that of a
 * composite {@code phase_} followed by the phase value mapped the same way; when that alias is already taken, because
 * the grammar admits {@code build.judge}, {@code build-judge} and {@code build_judge} as distinct states, the next free
 * numeric suffix {@code _2}, {@code _3}, … is appended. The aliases are allocated once per rendering, states first in
 * declaration order and then the composites, and every line of the diagram names a state through that table. Inside a
 * composite come the state declarations ({@code state "<id>" as <alias>}), the description lines
 * ({@code <alias> : <kind> <target>}, see
 * {@link StateTransitions#description(StateNode)}) and the transitions between two states of that phase; every other
 * transition follows at the top level, after the arrow {@code [*]} into the start state, or one such arrow into each
 * entry-bound state labelled {@code entry <name>} for a unit with entries, because Mermaid admits no transition
 * between inner states of different composites inside a composite block.
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
        var aliases = Aliases.of(definition, groups.keySet(), transitions);
        line(0, "stateDiagram-v2");
        groups.forEach((phase, states) -> {
            if (INHERIT.equals(phase)) {
                declare(1, states, aliases);
            } else {
                line(1, "state " + aliases.phase(phase) + " {");
                declare(2, states, aliases);
                states.stream().flatMap(state -> transitions.outgoing(state.id()).stream())
                        .filter(edge -> isInsideComposite(definition, edge))
                        .forEach(edge -> line(2, transition(edge, aliases)));
                line(1, "}");
            }
        });
        definition.startState().ifPresent(start -> line(1, "[*] --> " + aliases.state(start)));
        definition.entryBindings().forEach((entry, binding) -> line(1, "[*] --> %s : entry %s"
                .formatted(aliases.state(binding.stateId()), entry)));
        definition.states().values().stream().flatMap(state -> transitions.outgoing(state.id()).stream())
                .filter(edge -> !isInsideComposite(definition, edge))
                .forEach(edge -> line(1, transition(edge, aliases)));
        activeState.ifPresent(state -> {
            line(1, ACTIVE_STYLE);
            line(1, "class " + aliases.state(state) + " active");
        });
    }

    private void declare(int depth, List<StateNode> states, Aliases aliases) {
        states.forEach(state -> line(depth, "state \"%s\" as %s".formatted(state.id(), aliases.state(state.id()))));
        states.forEach(state -> line(depth, aliases.state(state.id()) + " : " + StateTransitions.description(state)));
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

    private static String transition(StateTransitions.Edge edge, Aliases aliases) {
        var label = new StringBuilder();
        edge.link().ifPresentOrElse(
                link -> label.append(StateTransitions.lower(link.rel())).append(' ').append(link.idPattern())
                        .append(" [").append(StateTransitions.lower(link.form())).append(']'),
                () -> label.append(StateTransitions.engineTrigger(edge.source().behavior())));
        edge.guard().ifPresent(guard -> label.append(" [").append(guard).append(']'));
        label.append(" / ").append(edge.outcome());
        return "%s --> %s : %s".formatted(aliases.state(edge.source().id()), aliases.state(edge.target()), label);
    }

    /**
     * The Mermaid identifiers of one rendering, each unique within the diagram.
     * <p>
     * The first choice for a name is its preferred form: {@code .} and {@code -} become {@code _}, and a phase value
     * is prefixed with {@code phase_}. When that identifier is already taken, the name gets the first free identifier
     * of the form {@code <preferred>_2}, {@code <preferred>_3}, … The names are allocated in a fixed order: the state
     * identifiers in the declaration order of the unit, then the phase values other than {@code inherit} in the order
     * they are first declared, then any starting state or transition target that names no declared state, in the order
     * they are referenced. An allocated identifier is never handed out again, also not to a later name whose preferred
     * form it is.
     *
     * @param states the identifier of every state and of every referenced, undeclared target
     * @param phases the identifier of every phase composite
     */
    private record Aliases(Map<String, String> states, Map<String, String> phases) {

        static Aliases of(WorkflowDefinition definition, Set<String> phaseValues, StateTransitions transitions) {
            var taken = new HashSet<String>();
            var states = new LinkedHashMap<String, String>();
            var phases = new LinkedHashMap<String, String>();
            definition.states().keySet().forEach(id -> states.put(id, allocate(preferred(id), taken)));
            phaseValues.stream().filter(phase -> !INHERIT.equals(phase))
                    .forEach(phase -> phases.put(phase, allocate(PHASE_PREFIX + preferred(phase), taken)));
            Stream.concat(definition.startingStates().stream(), definition.states().keySet().stream()
                    .flatMap(id -> transitions.outgoing(id).stream()).map(StateTransitions.Edge::target))
                    .forEach(id -> states.computeIfAbsent(id, unknown -> allocate(preferred(unknown), taken)));
            return new Aliases(Map.copyOf(states), Map.copyOf(phases));
        }

        String state(String id) {
            return Objects.requireNonNull(states.get(id), id);
        }

        String phase(String phase) {
            return Objects.requireNonNull(phases.get(phase), phase);
        }

        private static String preferred(String name) {
            return name.replace('.', '_').replace('-', '_');
        }

        private static String allocate(String preferred, Set<String> taken) {
            var candidate = preferred;
            for (var suffix = 2; !taken.add(candidate); suffix++) {
                candidate = preferred + "_" + suffix;
            }
            return candidate;
        }
    }
}
