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

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.plan.PlanInstance;

/**
 * Renders the tree of a unit in the three forms of its documentation (PM-IMPL-5): a Mermaid state diagram, the
 * AsciiDoc state, link and outcome tables, and a table for terminal output.
 * <p>
 * A plan instance version is rendered in the same three forms (Workflow DSL Specification, Plan Instance, Display):
 * the composed unit as above, the active state highlighted or marked, together with what the instance header records
 * beside the unit, the plan, the version and its composition event, every pruned element with its reason and source
 * fact, every waived gate with its waiver id, and in the two table forms the lane and the posture. The pruned elements
 * are absent from the composed unit, so they are listed beside it, never drawn as states of the unit.
 * <p>
 * Every form is a pure function of the unit: it follows the declaration order of the states, their links and their
 * transitions, sorts nothing, reads no clock and no environment, and ends every line with a line feed, so the same
 * unit always gives the same bytes. The forms are meant for a unit that passed the static verifier; they check
 * nothing of the unit beyond the active state they are asked to mark.
 * <p>
 * Example:
 * {@snippet :
 * String diagram = WorkflowDiagramGenerator.renderMermaid(definition, Optional.empty());
 * String tables = WorkflowDiagramGenerator.renderAsciiDocTables(definition);
 * String terminal = WorkflowDiagramGenerator.renderAscii(definition, Optional.of("finalize.remediate-ci"));
 * String instanceDiagram = WorkflowDiagramGenerator.renderInstanceMermaid(instance, Optional.empty());
 * }
 *
 * @since 0.1
 */
public final class WorkflowDiagramGenerator {

    private WorkflowDiagramGenerator() {
    }

    /**
     * Renders a unit as a Mermaid {@code stateDiagram-v2}, its states grouped into one composite state per phase
     * value, the active state styled {@code active}.
     *
     * @param definition  the unit
     * @param activeState the state to highlight; empty to render the unit without a highlight
     * @return the diagram, every line ended by a line feed
     * @throws IllegalArgumentException if the active state is no state of the unit
     */
    public static String renderMermaid(WorkflowDefinition definition, Optional<String> activeState) {
        requireKnownState(definition, activeState);
        return MermaidRenderer.render(definition, activeState);
    }

    /**
     * Renders a unit as AsciiDoc tables: per phase group a state table with the columns of the State Catalogue, a
     * link table and an outcome table.
     *
     * @param definition the unit
     * @return the tables, every line ended by a line feed
     */
    public static String renderAsciiDocTables(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return AsciiDocTableRenderer.render(definition);
    }

    /**
     * Renders a unit as a right-border-aligned table drawn with Unicode box-drawing characters, for terminal output;
     * the text is meant to be written as UTF-8.
     *
     * @param definition  the unit
     * @param activeState the state to mark; empty to render the unit without a mark
     * @return the table, every line ended by a line feed
     * @throws IllegalArgumentException if the active state is no state of the unit
     */
    public static String renderAscii(WorkflowDefinition definition, Optional<String> activeState) {
        requireKnownState(definition, activeState);
        return AsciiTableRenderer.render(definition, activeState);
    }

    /**
     * Renders a plan instance version as a Mermaid {@code stateDiagram-v2}: a front matter titled with the plan, the
     * version and its composition event, the diagram of the composed unit with the active state styled
     * {@code active}, and one composite state each for the pruned elements with their reason and source fact and for
     * the waived gates with their waiver id.
     *
     * @param instance    the instance version
     * @param activeState the state to highlight; empty to render the instance without a highlight
     * @return the diagram, every line ended by a line feed
     * @throws IllegalArgumentException if the active state is no state of the instance
     */
    public static String renderInstanceMermaid(PlanInstance instance, Optional<String> activeState) {
        return InstanceMermaidRenderer.render(instance.unit(), annotations(instance, activeState));
    }

    /**
     * Renders a plan instance version as AsciiDoc tables: a table of the instance (plan, version, composition event,
     * lane, posture, active state), the tables of the composed unit, and the tables of the pruned elements and of the
     * waived gates.
     *
     * @param instance    the instance version
     * @param activeState the state to name as active; empty for none
     * @return the tables, every line ended by a line feed
     * @throws IllegalArgumentException if the active state is no state of the instance
     */
    public static String renderInstanceAsciiDocTables(PlanInstance instance, Optional<String> activeState) {
        return InstanceAsciiDocTableRenderer.render(instance.unit(), annotations(instance, activeState));
    }

    /**
     * Renders a plan instance version as tables for terminal output, drawn with Unicode box-drawing characters: a
     * table of the instance, the table of the composed unit with the active state marked, and the tables of the
     * pruned elements and of the waived gates; the text is meant to be written as UTF-8.
     *
     * @param instance    the instance version
     * @param activeState the state to mark; empty to render the instance without a mark
     * @return the tables, every line ended by a line feed
     * @throws IllegalArgumentException if the active state is no state of the instance
     */
    public static String renderInstanceAscii(PlanInstance instance, Optional<String> activeState) {
        return InstanceAsciiTableRenderer.render(instance.unit(), annotations(instance, activeState));
    }

    private static InstanceAnnotations annotations(PlanInstance instance, Optional<String> activeState) {
        Objects.requireNonNull(instance, "instance");
        requireKnownState(instance.unit(), activeState);
        return InstanceAnnotations.of(instance, activeState);
    }

    private static void requireKnownState(WorkflowDefinition definition, Optional<String> activeState) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(activeState, "activeState");
        activeState.filter(state -> !definition.states().containsKey(state)).ifPresent(state -> {
            throw new IllegalArgumentException("the active state '%s' is no state of the unit '%s'"
                    .formatted(state, definition.id()));
        });
    }
}
