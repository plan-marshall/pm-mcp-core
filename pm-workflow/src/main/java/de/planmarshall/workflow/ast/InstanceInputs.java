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

import java.util.List;
import java.util.Optional;

/**
 * The {@code inputs} object of a plan instance header (PM-IMPL-5, PM-EXT-7): every input the version was composed
 * from, named by its digest. The unit lists are copied into unmodifiable lists that keep their order. A member the
 * header schema allows to be absent or {@code null} is empty here in both cases.
 *
 * @param configHash              the digest over the effective configuration
 * @param templates               the phase units the instance was composed from
 * @param subUnits                every sub-unit a call site of the instance calls
 * @param compositionRules        the revision and digest of the composition rule file
 * @param planFactsDigest         the digest over the plan facts read
 * @param realizedFootprintDigest the digest over the realized footprint, if the header names one
 * @param changeType              the change type of the plan, if the header names one
 * @param providersDigest         the digest over the provider configuration, if the header names one
 * @since 0.1
 */
public record InstanceInputs(String configHash, List<UnitRef> templates, List<UnitRef> subUnits,
CompositionRules compositionRules, String planFactsDigest, Optional<String> realizedFootprintDigest,
Optional<String> changeType, Optional<String> providersDigest) {

    /**
     * Copies the unit lists into unmodifiable lists that keep their order.
     *
     * @param configHash              the digest over the effective configuration
     * @param templates               the phase units the instance was composed from
     * @param subUnits                every sub-unit a call site of the instance calls
     * @param compositionRules        the revision and digest of the composition rule file
     * @param planFactsDigest         the digest over the plan facts read
     * @param realizedFootprintDigest the digest over the realized footprint, if the header names one
     * @param changeType              the change type of the plan, if the header names one
     * @param providersDigest         the digest over the provider configuration, if the header names one
     */
    public InstanceInputs {
        templates = List.copyOf(templates);
        subUnits = List.copyOf(subUnits);
    }

    /**
     * One unit an instance names by digest, a template or a sub-unit ({@code unit_ref} of the header schema).
     *
     * @param unit   the unit id
     * @param file   the unit file, if the header names one
     * @param digest the SHA-256 of the unit text
     * @param origin where the unit comes from, {@code default} or {@code project}, as written in the header
     * @since 0.1
     */
    public record UnitRef(String unit, Optional<String> file, String digest, String origin) {
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
