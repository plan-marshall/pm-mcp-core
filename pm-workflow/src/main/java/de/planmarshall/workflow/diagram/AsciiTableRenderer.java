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
import java.util.List;
import java.util.Optional;

import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a unit as a table for terminal output, drawn with Unicode box-drawing characters and aligned on its right
 * border (PM-IMPL-5).
 * <p>
 * The columns are <em>State</em>, <em>Task link (rel)</em>, <em>Offered when</em> and <em>Outcome -&gt; Next
 * State</em>; every state is one block of lines in declaration order, the blocks separated by a rule. The state cell
 * holds the state identifier, prefixed {@code *} and followed by a line {@code (ACTIVE)} for the active state, and the
 * state's description (see {@link StateTransitions#description(StateNode)}), so a task state shows its task label and
 * never a link of the task's entity. The other cells hold one line per transition in the order of
 * {@link StateTransitions#outgoing(String)}: for a transition of a declared link the link as {@code <id> (<rel>)} and
 * its guard on the link's first line, for an engine transition {@code -} and its result guard; a state without a
 * transition shows {@code -}. The width of a column is the largest number of code points of its cells.
 *
 * @since 0.1
 */
final class AsciiTableRenderer {

    private static final String NONE = "-";
    private static final String ACTIVE_MARK = "*";
    private static final String CONTINUATION = " ";
    private static final List<String> HEADER = List.of("State", "Task link (rel)", "Offered when",
            "Outcome -> Next State");

    private AsciiTableRenderer() {
    }

    /**
     * Renders a unit.
     *
     * @param definition  the unit
     * @param activeState the state to mark; empty for none
     * @return the table, every line ended by a line feed
     */
    static String render(WorkflowDefinition definition, Optional<String> activeState) {
        var transitions = StateTransitions.of(definition);
        var blocks = definition.states().values().stream()
                .map(state -> block(state, transitions, activeState.filter(state.id()::equals).isPresent()))
                .toList();
        var widths = widths(blocks);
        var out = new StringBuilder();
        rule(out, widths, '┌', '┬', '┐');
        line(out, widths, HEADER);
        for (var block : blocks) {
            rule(out, widths, '├', '┼', '┤');
            block.forEach(cells -> line(out, widths, cells));
        }
        rule(out, widths, '└', '┴', '┘');
        return out.toString();
    }

    /**
     * Lays out the lines of one state: the state cell lines beside one line per transition.
     */
    private static List<List<String>> block(StateNode state, StateTransitions transitions, boolean active) {
        var stateCell = new ArrayList<String>();
        stateCell.add((active ? ACTIVE_MARK : "") + state.id());
        if (active) {
            stateCell.add(CONTINUATION + "(ACTIVE)");
        }
        stateCell.add(CONTINUATION + StateTransitions.description(state));
        var rows = new ArrayList<List<String>>();
        Optional<LinkSpec> previousLink = Optional.empty();
        for (var edge : transitions.outgoing(state.id())) {
            var outcome = edge.outcome() + " -> " + edge.target();
            var guard = edge.guard().orElse(NONE);
            if (edge.link().isEmpty()) {
                rows.add(List.of(NONE, guard, outcome));
            } else if (edge.link().equals(previousLink)) {
                rows.add(List.of("", "", outcome));
            } else {
                var link = edge.link().orElseThrow();
                rows.add(List.of("%s (%s)".formatted(link.idPattern(), StateTransitions.lower(link.rel())), guard,
                        outcome));
            }
            previousLink = edge.link();
        }
        if (rows.isEmpty()) {
            rows.add(List.of(NONE, NONE, NONE));
        }
        var lines = new ArrayList<List<String>>();
        for (var index = 0; index < Math.max(stateCell.size(), rows.size()); index++) {
            var cells = new ArrayList<String>();
            cells.add(index < stateCell.size() ? stateCell.get(index) : "");
            cells.addAll(index < rows.size() ? rows.get(index) : List.of("", "", ""));
            lines.add(List.copyOf(cells));
        }
        return lines;
    }

    private static int[] widths(List<List<List<String>>> blocks) {
        var widths = HEADER.stream().mapToInt(AsciiTableRenderer::length).toArray();
        for (var block : blocks) {
            for (var cells : block) {
                for (var column = 0; column < widths.length; column++) {
                    widths[column] = Math.max(widths[column], length(cells.get(column)));
                }
            }
        }
        return widths;
    }

    private static void rule(StringBuilder out, int[] widths, char left, char middle, char right) {
        out.append(left);
        for (var column = 0; column < widths.length; column++) {
            if (column > 0) {
                out.append(middle);
            }
            out.append("─".repeat(widths[column] + 2));
        }
        out.append(right).append('\n');
    }

    private static void line(StringBuilder out, int[] widths, List<String> cells) {
        out.append('│');
        for (var column = 0; column < widths.length; column++) {
            var cell = cells.get(column);
            out.append(' ').append(cell).append(" ".repeat(widths[column] - length(cell))).append(" │");
        }
        out.append('\n');
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
