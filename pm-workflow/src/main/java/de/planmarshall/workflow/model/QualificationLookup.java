/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.Optional;

import de.planmarshall.core.role.RoleForm;

/**
 * The port through which the model caller looks up a valid qualification record (PM-WORK-6, PM-WORK-1 Qualify). The record
 * is looked up by the role's <em>qualification digest</em>, so a changed harness, model, effort, tool set, class, skill
 * digest, harness major.minor or weights identity makes the record stale and the role unqualified.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface QualificationLookup {

    /**
     * @param roleName            the role name
     * @param qualificationDigest the role's qualification digest
     * @param artifactForm        the form the artifact declares
     * @return the form the record qualifies: the artifact's form, or {@code fresh} when a warm artifact qualifies only
     *         fresh; empty when no valid record covers the digest
     */
    Optional<RoleForm> qualifiedForm(String roleName, String qualificationDigest, RoleForm artifactForm);
}
