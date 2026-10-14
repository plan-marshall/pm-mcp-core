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

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a plan instance version as tables for terminal output, drawn with Unicode box-drawing characters and
 * aligned on their right border (PM-IMPL-5, Workflow DSL Specification, Plan Instance, Display).
 * <p>
 * The output is, separated by one empty line: a table of the instance with the rows <em>Instance</em> (the title,
 * {@link InstanceAnnotations#title()}), <em>Lane</em>, <em>Posture</em> and <em>Active state</em> ({@code -} without
 * one); the table of the composed unit exactly as {@link AsciiTableRenderer} draws it, the active state marked; and,
 * each only when the header lists one, a table of the pruned elements (<em>Element</em>, <em>Id</em>,
 * <em>Reason</em>, <em>Source fact</em>) and a table of the waived gates (<em>Gate</em>, <em>Waiver id</em>), in header
 * order. The width of a column is the largest number of code points of its cells; the text is meant to be written as
 * UTF-8. The text of a cell comes from the header: a line break or control character in it becomes a space
 * ({@link InstanceAnnotations#singleLine(String)}), so that every cell stays on its line and the right border aligned.
 *
 * @since 0.1
 */
final class InstanceAsciiTableRenderer {

    private InstanceAsciiTableRenderer() {
    }

    /**
     * Renders an instance version.
     *
     * @param unit        the composed unit of the version
     * @param annotations the annotations of the version, the active state included
     * @return the tables, every line ended by a line feed
     */
    static String render(WorkflowDefinition unit, InstanceAnnotations annotations) {
        var out = new StringBuilder();
        table(out, List.of("Property", "Value"), List.of(
                List.of("Instance", annotations.title()),
                List.of("Lane", annotations.lane()),
                List.of("Posture", annotations.posture()),
                List.of("Active state", annotations.activeState().orElse(InstanceAnnotations.NONE))));
        out.append('\n').append(AsciiTableRenderer.render(unit, annotations.activeState()));
        if (!annotations.pruned().isEmpty()) {
            out.append('\n');
            table(out, List.of("Element", "Id", "Reason", "Source fact"), annotations.pruned().stream()
                    .map(pruned -> List.of(pruned.element(), pruned.id(), pruned.reason(), pruned.sourceFact()))
                    .toList());
        }
        if (!annotations.waivedGates().isEmpty()) {
            out.append('\n');
            table(out, List.of("Gate", "Waiver id"), annotations.waivedGates().stream()
                    .map(waived -> List.of(waived.gate(), waived.waiverId())).toList());
        }
        return out.toString();
    }

    private static void table(StringBuilder out, List<String> header, List<List<String>> unsafeRows) {
        var rows = unsafeRows.stream().map(cells -> cells.stream().map(InstanceAnnotations::singleLine).toList())
                .toList();
        var all = new ArrayList<List<String>>();
        all.add(header);
        all.addAll(rows);
        var widths = new int[header.size()];
        for (var cells : all) {
            for (var column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], length(cells.get(column)));
            }
        }
        rule(out, widths, '┌', '┬', '┐');
        line(out, widths, header);
        rule(out, widths, '├', '┼', '┤');
        rows.forEach(cells -> line(out, widths, cells));
        rule(out, widths, '└', '┴', '┘');
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
