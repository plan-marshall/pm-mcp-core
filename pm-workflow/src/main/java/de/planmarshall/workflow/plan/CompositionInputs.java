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

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Every input a plan instance version was composed from, named by its digest (PM-IMPL-5, PM-EXT-7): the
 * {@code inputs} object of the instance header. The configuration hash binds the version to the effective
 * configuration it was composed under, so a later change of the configuration never silently changes what the plan
 * does. The unit lists are copied into unmodifiable lists that keep their order.
 *
 * @param configHash               the SHA-256 over the effective configuration
 * @param templates                the phase units the instance was composed from
 * @param subUnits                 every sub-unit a call site of the instance calls
 * @param compositionRulesRevision the revision of the composition rule file
 * @param compositionRulesDigest   the SHA-256 of the composition rule file
 * @param planFactsDigest          the SHA-256 over the plan facts read
 * @param realizedFootprintDigest  the SHA-256 over the realized footprint; {@code null} if the composition read none
 * @param changeType               the change type of the plan; {@code null} if unknown
 * @param providersDigest          the SHA-256 over the provider configuration; {@code null} if the composition read
 *                                 none
 * @since 0.1
 */
public record CompositionInputs(String configHash, List<UnitReference> templates, List<UnitReference> subUnits,
String compositionRulesRevision, String compositionRulesDigest, String planFactsDigest,
@Nullable String realizedFootprintDigest, @Nullable String changeType, @Nullable String providersDigest) {

    /**
     * Refuses a missing required component and copies the unit lists into unmodifiable lists that keep their order.
     *
     * @param configHash               the SHA-256 over the effective configuration
     * @param templates                the phase units the instance was composed from
     * @param subUnits                 every sub-unit a call site of the instance calls
     * @param compositionRulesRevision the revision of the composition rule file
     * @param compositionRulesDigest   the SHA-256 of the composition rule file
     * @param planFactsDigest          the SHA-256 over the plan facts read
     * @param realizedFootprintDigest  the SHA-256 over the realized footprint, {@code null} if none
     * @param changeType               the change type of the plan, {@code null} if unknown
     * @param providersDigest          the SHA-256 over the provider configuration, {@code null} if none
     */
    public CompositionInputs {
        Objects.requireNonNull(configHash, "configHash");
        templates = List.copyOf(templates);
        subUnits = List.copyOf(subUnits);
        Objects.requireNonNull(compositionRulesRevision, "compositionRulesRevision");
        Objects.requireNonNull(compositionRulesDigest, "compositionRulesDigest");
        Objects.requireNonNull(planFactsDigest, "planFactsDigest");
    }
}
