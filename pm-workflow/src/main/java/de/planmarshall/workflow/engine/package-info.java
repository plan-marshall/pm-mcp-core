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
 * The deterministic interpreter of the state machine: the call stack, the guards, the loop-back budgets, the
 * remediation units, and the handling of questions and waivers.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/workflow-dsl/02-units-jobs-decisions.adoc}</li>
 * <li>{@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc}</li>
 * <li>{@code doc/specification/workflow-dsl/08-remediation-units.adoc}</li>
 * <li>{@code doc/specification/mcp-tools/01-core-workflow-tools.adoc}</li>
 * <li>{@code doc/specification/state-catalogue.adoc}</li>
 * </ul>
 */
package de.planmarshall.workflow.engine;
