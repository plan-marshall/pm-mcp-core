/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The generator of the documentation of a workflow unit (PM-IMPL-5).
 * <p>
 * {@link de.planmarshall.workflow.diagram.WorkflowDiagramGenerator} is the only public type. It renders the tree of a
 * unit, a {@link de.planmarshall.workflow.ast.WorkflowDefinition}, in three forms, each by a package-private
 * renderer:
 * <ul>
 * <li><b>Mermaid</b>: a {@code stateDiagram-v2} with one composite state per phase value, labelled transitions and,
 * for a live state, the style {@code active}.</li>
 * <li><b>AsciiDoc</b>: per phase group a state table with the columns of the State Catalogue, a link table and an
 * outcome table.</li>
 * <li><b>Terminal</b>: a table drawn with Unicode box-drawing characters, aligned on its right border, with the
 * active state marked.</li>
 * </ul>
 * A plan instance version, a {@link de.planmarshall.workflow.plan.PlanInstance}, is rendered in the same three forms
 * (Workflow DSL Specification, Plan Instance, Display), each by its own package-private renderer that draws the
 * composed unit through the renderer of the unit form, the active state highlighted or marked, and adds what the
 * instance header records beside the unit: the plan, the version and its composition event, every pruned element with
 * its reason and source fact, every waived gate with its waiver id, and in the two table forms the lane and the
 * posture. These values are derived once from the header into one shared view, so the three instance forms name them
 * in the same words.
 * <p>
 * The three renderers share the kind of a state, derived from the sealed
 * {@link de.planmarshall.workflow.ast.StateBehavior} alone, and the edges of the unit with their label parts, so the
 * vocabulary and the order of every form are defined once.
 * <p>
 * The output is byte-stable: it follows the declaration order of the unit and sorts nothing, holds no timestamp and
 * nothing of the environment, and ends every line with a line feed; the same unit always renders to the same bytes,
 * which is what lets committed documentation be compared with the units it was rendered from.
 */
package de.planmarshall.workflow.diagram;
