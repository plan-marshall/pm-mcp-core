/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The port through which the engine reads the units a version in force runs (PM-IMPL-5, Workflow DSL § Versions &amp;
 * Composition Events, item 1).
 * <p>
 * A plan runs the composed files of the version in force, never the level's copy of a template: the plan unit of
 * {@code instance/plan.v<N>.pmwf}, the composed file of every phase sub-workflow and bound axis variant of
 * {@code instance/<unit>.v<N>.pmwf}, and the stored text {@code mcp/units/<digest>.pmwf} of every sub-unit the header
 * binds by digest. An edit of a project's or the machine's unit therefore never changes a running call.
 * <p>
 * {@link PlanInstanceVersions} reads the port over a {@link PlanInstanceStore}; the engine calls it afresh for every
 * advance, so a version written by another session is read without a restart.
 *
 * @since 0.1
 */
public interface InstanceUnits {

    /**
     * @param version an instance version
     * @return the plan unit of the version, the unit {@code plan} with its {@code instance} header
     */
    Optional<WorkflowDefinition> planUnit(int version);

    /**
     * @param version an instance version
     * @param unit    the id of a composed unit
     * @return the composed file of the unit for the version, if the version composes it
     */
    Optional<WorkflowDefinition> composedUnit(int version, String unit);

    /**
     * @param version an instance version
     * @param unit    the id of a sub-unit the version's header binds by digest
     * @return the stored text {@code mcp/units/<digest>.pmwf} of the unit
     */
    Optional<WorkflowDefinition> subUnit(int version, String unit);
}
