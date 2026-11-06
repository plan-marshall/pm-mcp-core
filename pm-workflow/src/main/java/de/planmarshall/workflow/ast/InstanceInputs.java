/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The {@code inputs} object of a plan instance header (PM-IMPL-5, PM-EXT-7): every input the version was composed
 * from, named by its digest. The unit lists are copied into unmodifiable lists that keep their order. A member the
 * header schema allows to be absent or {@code null} is {@code null} here in both cases.
 *
 * @param configHash              the digest over the effective configuration
 * @param templates               the phase units the instance was composed from
 * @param subUnits                every sub-unit a call site of the instance calls
 * @param compositionRules        the revision and digest of the composition rule file
 * @param planFactsDigest         the digest over the plan facts read
 * @param realizedFootprintDigest the digest over the realized footprint, {@code null} when the header names none
 * @param changeType              the change type of the plan, {@code null} when the header names none
 * @param providersDigest         the digest over the provider configuration, {@code null} when the header names none
 * @since 0.1
 */
public record InstanceInputs(String configHash, List<UnitRef> templates, List<UnitRef> subUnits,
CompositionRules compositionRules, String planFactsDigest, @Nullable String realizedFootprintDigest,
@Nullable String changeType, @Nullable String providersDigest) {

    /**
     * Copies the unit lists into unmodifiable lists that keep their order.
     *
     * @param configHash              the digest over the effective configuration
     * @param templates               the phase units the instance was composed from
     * @param subUnits                every sub-unit a call site of the instance calls
     * @param compositionRules        the revision and digest of the composition rule file
     * @param planFactsDigest         the digest over the plan facts read
     * @param realizedFootprintDigest the digest over the realized footprint, {@code null} when the header names none
     * @param changeType              the change type of the plan, {@code null} when the header names none
     * @param providersDigest         the digest over the provider configuration, {@code null} when the header names none
     */
    public InstanceInputs {
        templates = List.copyOf(templates);
        subUnits = List.copyOf(subUnits);
    }

    /**
     * One unit an instance names by digest, a template or a sub-unit ({@code unit_ref} of the header schema).
     *
     * @param unit   the unit id
     * @param file   the unit file, {@code null} when the header names none
     * @param digest the SHA-256 of the unit text
     * @param origin where the unit comes from, {@code default} or {@code project}, as written in the header
     * @since 0.1
     */
    public record UnitRef(String unit, @Nullable String file, String digest, String origin) {
    }

    /**
     * The composition rule file an instance was composed with.
     *
     * @param revision the revision of the rule file
     * @param digest   the digest of the rule file
     * @since 0.1
     */
    public record CompositionRules(String revision, String digest) {
    }
}
