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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which models the plans of a project may use, per enrolled harness (PM-WORK-1, and
 * {@code store-schemas/12-machine-stores.adoc#model-catalogue}). The effective catalogue of a project is its own, else the
 * machine's; a model that no entry of its harness allows is not allowed, which {@link RoleValidation} refuses with
 * {@code model_not_allowed}.
 * <p>
 * The store is written by the model step of the onboarding; this package only reads it, through {@link RoleEnvironment}.
 *
 * @param allowed the allowed models per client id of an enrolled harness
 * @since 0.1
 */
public record ModelCatalogue(Map<String, Set<String>> allowed) {

    /** Copies the sets. */
    public ModelCatalogue {
        var copy = new LinkedHashMap<String, Set<String>>();
        allowed.forEach((harness, models) -> copy.put(harness, Collections.unmodifiableSet(
                new LinkedHashSet<>(models))));
        allowed = Collections.unmodifiableMap(copy);
    }

    /**
     * @param harness the client id of a harness
     * @param model   a model identifier
     * @return whether the catalogue allows the model on the harness
     */
    public boolean allows(String harness, String model) {
        return allowed.getOrDefault(harness, Set.of()).contains(model);
    }
}
