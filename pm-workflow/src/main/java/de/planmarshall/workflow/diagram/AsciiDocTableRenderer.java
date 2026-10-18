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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * Renders a unit as AsciiDoc tables (PM-IMPL-5): per phase group, in the order in which the phase values are first
 * declared, a state table, a link table and an outcome table, each titled with its phase value.
 * <ul>
 * <li><b>States</b>: exactly the columns of the State Catalogue: <em>State id</em>, <em>Phase value</em>,
 * <em>Kind</em> ({@link StateKind}), <em>Entered from</em> ({@code start} for the start state, {@code entry <name>} for
 * each entry that binds the state, then the source of every incoming transition once), <em>Offered links / outcomes</em> (the task label of a task state, the declared
 * and implicit links, the distinct outcomes of the engine transitions, or the outcome a terminal state reports),
 * <em>Exits to</em> (every target once) and <em>Defined in</em>, which is emitted empty because it is maintained by
 * hand in the catalogue.</li>
 * <li><b>Links</b>: one row per declared link and per implicit link (see
 * {@link StateTransitions#implicitLinks(WorkflowScope, StateNode)}) with its relation, identifier, form, free
 * parameters and guard; emitted only for a group that has a link.</li>
 * <li><b>Outcomes</b>: one row per transition with its outcome (and the link whose action it routes), its result
 * guard and its next state; emitted only for a group that has a transition.</li>
 * </ul>
 * A cell that has nothing to show holds {@code -}; a {@code |} inside a cell is escaped.
 *
 * @since 0.1
 */
final class AsciiDocTableRenderer {

    private static final String NONE = "-";
    private static final String LIST_SEPARATOR = ", ";
    private static final String TABLE_DELIMITER = "|===";

    private final StringBuilder out = new StringBuilder();

    private AsciiDocTableRenderer() {
    }

    /**
     * Renders a unit.
     *
     * @param definition the unit
     * @return the tables, every line ended by a line feed
     */
    static String render(WorkflowDefinition definition) {
        var renderer = new AsciiDocTableRenderer();
        renderer.renderUnit(definition);
        return renderer.out.toString();
    }

    private void renderUnit(WorkflowDefinition definition) {
        var transitions = StateTransitions.of(definition);
        var groups = new LinkedHashMap<String, List<StateNode>>();
        definition.states().values()
                .forEach(state -> groups.computeIfAbsent(state.phase(), phase -> new ArrayList<>()).add(state));
        var first = true;
        for (Map.Entry<String, List<StateNode>> group : groups.entrySet()) {
            if (!first) {
                out.append('\n');
            }
            first = false;
            stateTable(definition, transitions, group.getKey(), group.getValue());
            linkTable(definition.scope(), group.getKey(), group.getValue());
            outcomeTable(transitions, group.getKey(), group.getValue());
        }
    }

    private void stateTable(WorkflowDefinition definition, StateTransitions transitions, String phase,
            List<StateNode> states) {
        header(phase, "states", "2,1,1,2,3,2,2", "State id", "Phase value", "Kind", "Entered from",
                "Offered links / outcomes", "Exits to", "Defined in");
        for (var state : states) {
            row(code(state.id()), code(state.phase()), code(StateKind.of(state.behavior()).label()),
                    enteredFrom(definition, transitions, state), offered(definition.scope(), state),
                    exitsTo(transitions, state), "");
        }
        out.append(TABLE_DELIMITER).append('\n');
    }

    private void linkTable(WorkflowScope scope, String phase, List<StateNode> states) {
        var rows = new ArrayList<String[]>();
        for (var state : states) {
            for (var link : StateTransitions.links(state.behavior())) {
                rows.add(new String[]{code(state.id()), code(StateTransitions.lower(link.rel())),
                        code(link.idPattern()), code(StateTransitions.lower(link.form())), freeParams(link),
                        link.guard().map(guard -> code(StateTransitions.guard(guard.expr()))).orElse(NONE), "no"});
            }
            for (var link : StateTransitions.implicitLinks(scope, state)) {
                rows.add(new String[]{code(state.id()), code(StateTransitions.lower(link.rel())), code(link.id()),
                        code(StateTransitions.lower(link.rel().defaultForm())), NONE, NONE, "yes"});
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        out.append('\n');
        header(phase, "links", "2,1,2,1,3,2,1", "State", "rel", "Identifier", "form", "Free parameters", "Guard",
                "Implicit link");
        rows.forEach(this::row);
        out.append(TABLE_DELIMITER).append('\n');
    }

    private void outcomeTable(StateTransitions transitions, String phase, List<StateNode> states) {
        var edges = states.stream().flatMap(state -> transitions.outgoing(state.id()).stream()).toList();
        if (edges.isEmpty()) {
            return;
        }
        out.append('\n');
        header(phase, "outcomes", "2,2,2,2", "State", "Outcome", "Guard", "Next state");
        for (var edge : edges) {
            var outcome = code(edge.outcome()) + edge.link().map(link -> " via " + code(link.idPattern())).orElse("");
            row(code(edge.source().id()), outcome,
                    edge.transition().resultGuard().map(guard -> code(StateTransitions.resultGuard(guard)))
                            .orElse(NONE),
                    code(edge.target()));
        }
        out.append(TABLE_DELIMITER).append('\n');
    }

    private void header(String phase, String title, String cols, String... columns) {
        out.append(".Phase ").append(code(phase)).append(": ").append(title).append('\n');
        out.append("[cols=\"").append(cols).append("\"]\n");
        out.append(TABLE_DELIMITER).append('\n');
        out.append(Arrays.stream(columns).map(column -> "|" + column).collect(Collectors.joining(" "))).append('\n');
    }

    private void row(String... cells) {
        out.append('\n');
        for (var cell : cells) {
            out.append('|').append(escape(cell)).append('\n');
        }
    }

    private static String enteredFrom(WorkflowDefinition definition, StateTransitions transitions, StateNode state) {
        var sources = new LinkedHashSet<String>();
        if (definition.startState().filter(state.id()::equals).isPresent()) {
            sources.add("start");
        }
        definition.entryBindings().forEach((entry, binding) -> {
            if (binding.stateId().equals(state.id())) {
                sources.add("entry " + code(entry));
            }
        });
        transitions.incoming(state.id()).forEach(edge -> sources.add(code(edge.source().id())));
        return joinedOrNone(sources);
    }

    private static String offered(WorkflowScope scope, StateNode state) {
        var parts = new ArrayList<String>();
        if (state.behavior() instanceof StateBehavior.ModelJob job) {
            parts.add(code(StateTransitions.taskLabel(job)));
        }
        var links = new ArrayList<String>();
        StateTransitions.links(state.behavior()).forEach(link -> links.add(code(link.idPattern())));
        StateTransitions.implicitLinks(scope, state).forEach(link -> links.add(code(link.id())));
        if (!links.isEmpty()) {
            parts.add("links " + String.join(LIST_SEPARATOR, links));
        }
        var outcomes = new LinkedHashSet<String>();
        StateTransitions.engineTransitions(state.behavior())
                .forEach(transition -> outcomes.add(code(transition.outcomeName())));
        if (!outcomes.isEmpty()) {
            parts.add("outcomes " + String.join(LIST_SEPARATOR, outcomes));
        }
        state.terminalOutcome().ifPresent(outcome -> parts.add("reports %s (%s)".formatted(code(outcome.id()),
                code(StateTransitions.lower(outcome.outcomeClass())))));
        return parts.isEmpty() ? NONE : String.join("; ", parts);
    }

    private static String exitsTo(StateTransitions transitions, StateNode state) {
        var targets = new LinkedHashSet<String>();
        transitions.outgoing(state.id()).forEach(edge -> targets.add(code(edge.target())));
        return joinedOrNone(targets);
    }

    private static String freeParams(LinkSpec link) {
        if (link.freeParams().isEmpty()) {
            return NONE;
        }
        return link.freeParams().stream().map(param -> code(param(param))).collect(Collectors.joining(
                LIST_SEPARATOR));
    }

    private static String param(ParamSpec param) {
        return param.name() + ": " + type(param.type()) + (param.optional() ? "?" : "");
    }

    private static String type(ParamType type) {
        return switch (type) {
            case ParamType.Text(var minLength, var maxLength) -> "text" + range(minLength, maxLength);
            case ParamType.Int(var min, var max) -> "int" + range(min, max);
            case ParamType.EnumOf(var values) -> "enum[" + String.join("|", values) + "]";
            case ParamType.SchemaRef(var schemaName) -> "schema[" + schemaName + "]";
            case ParamType.ListOf(var elementType) -> "list<" + type(elementType) + ">";
        };
    }

    private static String range(Optional<Integer> low, Optional<Integer> high) {
        if (low.isEmpty() || high.isEmpty()) {
            return "";
        }
        return "[" + low.orElseThrow() + ".." + high.orElseThrow() + "]";
    }

    private static String joinedOrNone(LinkedHashSet<String> values) {
        return values.isEmpty() ? NONE : String.join(LIST_SEPARATOR, values);
    }

    private static String code(String text) {
        return "`" + text + "`";
    }

    private static String escape(String cell) {
        return cell.replace("|", "\\|");
    }
}
