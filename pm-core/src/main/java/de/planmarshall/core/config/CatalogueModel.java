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

import java.time.Instant;
import java.util.Objects;

/**
 * One allowed model of one harness in a model catalogue (Store Schemas § Model Catalogue): its identifier, where it came
 * from and when it was recorded.
 *
 * @param model      the model identifier, as the harness takes it
 * @param source     where the model came from
 * @param recordedAt when it was recorded
 * @since 0.1
 */
public record CatalogueModel(String model, ModelSource source, Instant recordedAt) {

    /** @throws IllegalArgumentException if the identifier is empty or longer than the schema allows */
    public CatalogueModel {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (model.isEmpty() || model.length() > 128) {
            throw new IllegalArgumentException("A catalogue model names 1 to 128 characters: " + model);
        }
    }
}
