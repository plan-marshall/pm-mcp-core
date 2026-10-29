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

import java.util.Arrays;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a plan instance version as AsciiDoc tables (PM-IMPL-5, Workflow DSL Specification, Plan Instance, Display).
 * <ul>
 * <li><b>Instance</b>: a table titled with the title of the instance ({@link InstanceAnnotations#title()}) that names
 * the plan, the version, the composition event, the lane, the posture and the active state ({@code -} without
 * one).</li>
 * <li><b>Unit</b>: the state, link and outcome tables of the composed unit exactly as {@link AsciiDocTableRenderer}
 * renders them.</li>
 * <li><b>Pruned elements</b>: one row per pruned element with its kind, id, reason and source fact, in header order;
 * emitted only when the header lists one.</li>
 * <li><b>Waived gates</b>: one row per waived gate with its waiver id, in header order; emitted only when the header
 * lists one.</li>
 * </ul>
 * The tables are separated by one empty line; every value is written as code, a {@code |} inside a cell is escaped
 * and a line break becomes a space ({@link InstanceAnnotations#singleLine(String)}).
 *
 * @since 0.1
 */
final class InstanceAsciiDocTableRenderer {

    private static final String TABLE_DELIMITER = "|===";

    private final StringBuilder out = new StringBuilder();

    private InstanceAsciiDocTableRenderer() {
    }

    /**
     * Renders an instance version.
     *
     * @param unit        the composed unit of the version
     * @param annotations the annotations of the version, the active state included
     * @return the tables, every line ended by a line feed
     */
    static String render(WorkflowDefinition unit, InstanceAnnotations annotations) {
        var renderer = new InstanceAsciiDocTableRenderer();
        renderer.renderInstance(unit, annotations);
        return renderer.out.toString();
    }

    private void renderInstance(WorkflowDefinition unit, InstanceAnnotations annotations) {
        header("Instance " + annotations.title(), "1,3", "Property", "Value");
        row("Plan", code(annotations.planId()));
        row("Version", code(Integer.toString(annotations.version())));
        row("Composition event", code(annotations.event()));
        row("Lane", codeOrNone(annotations.lane()));
        row("Posture", codeOrNone(annotations.posture()));
        row("Active state", annotations.activeState().map(InstanceAsciiDocTableRenderer::code)
                .orElse(InstanceAnnotations.NONE));
        out.append(TABLE_DELIMITER).append('\n');
        out.append('\n').append(AsciiDocTableRenderer.render(unit));
        if (!annotations.composed().isEmpty()) {
            out.append('\n');
            header("Instance: composed files", "1,2,2", "Unit", "File", "Digest");
            annotations.composed().forEach(composed -> row(code(composed.unit()), code(composed.file()),
                    code(composed.digest())));
            out.append(TABLE_DELIMITER).append('\n');
        }
        if (!annotations.pruned().isEmpty()) {
            out.append('\n');
            header("Instance: pruned elements", "1,2,1,2", "Element", "Id", "Reason", "Source fact");
            annotations.pruned().forEach(pruned -> row(code(pruned.element()), code(pruned.id()),
                    code(pruned.reason()), code(pruned.sourceFact())));
            out.append(TABLE_DELIMITER).append('\n');
        }
        if (!annotations.waivedGates().isEmpty()) {
            out.append('\n');
            header("Instance: waived gates", "2,1", "Gate", "Waiver id");
            annotations.waivedGates().forEach(waived -> row(code(waived.gate()), code(waived.waiverId())));
            out.append(TABLE_DELIMITER).append('\n');
        }
    }

    private void header(String title, String cols, String... columns) {
        out.append('.').append(title).append('\n');
        out.append("[cols=\"").append(cols).append("\"]\n");
        out.append(TABLE_DELIMITER).append('\n');
        out.append(Arrays.stream(columns).map(column -> "|" + column).collect(Collectors.joining(" "))).append('\n');
    }

    private void row(String... cells) {
        out.append('\n');
        for (var cell : cells) {
            out.append('|').append(cell).append('\n');
        }
    }

    private static String codeOrNone(String value) {
        return InstanceAnnotations.NONE.equals(value) ? value : code(value);
    }

    /**
     * Writes a value as code. The text comes from the header, so it is made safe for the cell that holds it: a
     * {@code |}, which would start the next cell, is escaped, and a line break, which could end the table, becomes a
     * space.
     */
    private static String code(String text) {
        return "`" + InstanceAnnotations.singleLine(text).replace("|", "\\|") + "`";
    }
}
