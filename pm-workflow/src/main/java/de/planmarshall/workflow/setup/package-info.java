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
 * The state machine of the project setup, and the setup gate that the machine scope's onboarding decides.
 * <p>
 * {@link de.planmarshall.workflow.setup.MachineOnboarding} reads the admission from the machine scope's status, and
 * {@link de.planmarshall.workflow.setup.SetupGate} is the one guard the starts of a plan, an epic and a project's
 * onboarding consult; the onboarding itself and the operator's verbs stay open in every state.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/domain-extensions/04-setup-state-machine.adoc}</li>
 * <li>{@code doc/specification/workflow-dsl/01-grammar.adoc}, Machine Scope</li>
 * <li>{@code doc/specification/workflow-dsl/02-units-jobs-decisions.adoc}, Waiting for the Operator</li>
 * </ul>
 */
package de.planmarshall.workflow.setup;
