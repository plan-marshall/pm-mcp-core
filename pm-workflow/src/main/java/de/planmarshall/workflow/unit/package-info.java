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
 * The loaders of the workflow units (PM-IMPL-5): the default units compiled into the release and the units a project
 * owns.
 * <p>
 * {@link de.planmarshall.workflow.unit.DefaultUnitLoader} reads the default units from a classpath root the assembly
 * passes ({@code workflows/}); this module carries no unit. {@link de.planmarshall.workflow.unit.ProjectUnitLoader}
 * reads the project-owned units from {@code .marshall/config/}, confined to the workspace, and verifies them: the
 * DSL format, Rules 1-12 without Rule 5, the declaration bounds, the finalize pipeline over the project's roster, the
 * compiled invariants against the default units, and the agreement of the project steps with their declarations and
 * bindings. No file is merged: each unit, the plan unit and its six phase sub-workflows among them, is loaded and
 * verified on its own, so the plan unit calls its phases instead of holding their states.
 * <p>
 * Every check reports a {@link de.planmarshall.workflow.unit.UnitFinding} with file and line, never an exception. A
 * {@link de.planmarshall.workflow.unit.UnitLoadResult} carries either every verified unit or every finding; a result
 * with findings returns the {@code config_invalid} refusal that the assembly ({@code pm-mcp-server} in
 * plan-marshall-mcp) applies at plan start and at resume, and no default unit ever stands in for a refused project
 * unit. Nothing in this module calls the loader on a lifecycle path.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/02-units-jobs-decisions.adoc} (Workflow Definition Units) and
 * {@code doc/specification/workflow-dsl/04-ast-node-model.adoc} (Project-owned units).
 */
package de.planmarshall.workflow.unit;
