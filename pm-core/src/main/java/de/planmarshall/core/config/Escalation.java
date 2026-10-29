/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffortValue;

/**
 * The optional stronger configuration the last round of a fix budget runs on (Store Schemas § Role Assignments, Model Work
 * § Escalation Assignment): its harness, its model and the effort it runs with ({@code null}: the profile's default).
 *
 * @param harness the client id of an enrolled, worker-eligible harness
 * @param model   the model, allowed on that harness by the effective catalogue
 * @param effort  the effort, or empty when the schema's {@code null} applies
 * @since 0.1
 */
public record Escalation(String harness, String model, Optional<EffortValue> effort) {

    /** Rejects {@code null} components and empty names. */
    public Escalation {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        if (harness.isEmpty() || model.isEmpty()) {
            throw new IllegalArgumentException("An escalation names its harness and its model");
        }
    }
}
