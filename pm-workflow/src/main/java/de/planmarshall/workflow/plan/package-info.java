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
 * The plan instance: the workflow unit a plan executes, composed from the templates, the composition rules and the
 * facts of the plan, immutable and versioned (PM-IMPL-5, PM-WF-1, PM-EXT-7).
 * <p>
 * The package holds:
 * <ul>
 * <li>the instance header model: {@link de.planmarshall.workflow.plan.PlanInstanceHeader} with its inputs, named by
 * digest ({@link de.planmarshall.workflow.plan.CompositionInputs},
 * {@link de.planmarshall.workflow.plan.UnitReference}), the effect of the facts with provenance
 * ({@link de.planmarshall.workflow.plan.PrunedElement} with its {@link de.planmarshall.workflow.plan.PruneReason},
 * {@link de.planmarshall.workflow.plan.PolicyCall} with its {@link de.planmarshall.workflow.plan.BoundArgument}s,
 * {@link de.planmarshall.workflow.plan.WaivedGate}) and the {@link de.planmarshall.workflow.plan.CompositionEvent}
 * that composed the version;</li>
 * <li>the check of a header against every constraint of its schema,
 * {@link de.planmarshall.workflow.plan.HeaderSchemaCheck}, which reports each
 * {@link de.planmarshall.workflow.plan.HeaderViolation} as a value and never throws, and the canonical JSON form of the
 * header (keys sorted, no insignificant whitespace, UTF-8), so the digest of an unchanged header is reproducible;</li>
 * <li>the composition rules and their evaluator, the composer that verifies every version before it is kept, and the
 * store of the versions and the composition log.</li>
 * </ul>
 * The facts, the configuration and the waivers a composition reads arrive as values: nothing in this package reads a
 * store of the plan to compose an instance.
 */
package de.planmarshall.workflow.plan;
