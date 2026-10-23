/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Optional;

import de.planmarshall.core.profile.EnrolledHarness;

/**
 * The port through which the role package reads everything that lies outside a role artifact (PM-WORK-1): the client
 * profiles and the enrolment, the effective model catalogue, the assignment stores, the plan's override, the project's
 * skills, the weights identity, the machine configuration and the default assignment.
 * <p>
 * The assignment stores, the plan's {@code overrides.json} and the model catalogues are written by the onboarding
 * ({@code machine-onboarding-unit}, {@code project-onboarding-unit}) and by {@code plan-and-epic-control}; this package
 * only reads them, through this port, and its tests read them through fixture stores. Every method is called afresh at
 * every composition, task creation and offer ({@link RoleCatalog}), so a changed store applies to the next read.
 * <p>
 * The implementation belongs to the assembly or the runtime that wires the package; no class of this package reads a
 * file or a classpath of its own for these facts.
 *
 * @since 0.1
 */
public interface RoleEnvironment {

    /**
     * @param clientId the client id of a harness
     * @return the facts of the effective client profile of the harness
     */
    Optional<HarnessProfile> profile(String clientId);

    /**
     * @param clientId the client id of a harness
     * @return the enrolled build of the harness
     */
    Optional<EnrolledHarness> enrolledHarness(String clientId);

    /** @return the machine's model catalogue, {@code <PM_MCP_BASE>/model-catalogue.json} */
    Optional<ModelCatalogue> machineCatalogue();

    /** @return the project's model catalogue, which replaces the machine's for the project */
    Optional<ModelCatalogue> projectCatalogue();

    /** @return the machine's role assignment, {@code <PM_MCP_BASE>/role-assignments.json} */
    Optional<RoleAssignments> machineAssignments();

    /** @return the project's role assignment, {@code <PM_MCP_BASE>/projects/<project_id>/role-assignments.json} */
    Optional<RoleAssignments> projectAssignments();

    /**
     * @param uri a skill URI
     * @return the skill as the project resolves it; empty if it does not resolve
     */
    Optional<RoleSkill> resolveSkill(String uri);

    /**
     * @param clientId the client id of a harness
     * @param model    a model identifier
     * @return the weights identity of the model as the enrolment records it; empty when the harness reports none
     */
    Optional<String> weightsIdentity(String clientId, String model);

    /** @return the machine configuration, {@code <PM_MCP_BASE>/machine-config.json} */
    Optional<MachineConfiguration> machineConfiguration();

    /**
     * @param role a role name
     * @return the effort of the role's default assignment, {@code classpath:/roles/default-assignment.json}; empty when
     *         the release does not name the role
     */
    Optional<EffortValue> defaultEffort(String role);
}
