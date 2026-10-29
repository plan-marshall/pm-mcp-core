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

import java.util.Arrays;
import java.util.Optional;

/**
 * Where a model of a catalogue came from (Store Schemas § Model Catalogue): the harness's own model probe
 * ({@code probe}), run on the consent {@code probe_harness_models}, or the operator's entry where the harness cannot list
 * its models ({@code operator}).
 *
 * @since 0.1
 */
public enum ModelSource {

    /** The model the harness listed through its {@code headless.models_probe}. */
    PROBE("probe"),
    /** The model id the operator entered. */
    OPERATOR("operator");

    private final String wireName;

    ModelSource(String wireName) {
        this.wireName = wireName;
    }

    /** @return the value of {@code source} */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName a value of {@code source}
     * @return the source, or empty for a value outside the closed list
     */
    public static Optional<ModelSource> of(String wireName) {
        return Arrays.stream(values()).filter(source -> source.wireName.equals(wireName)).findFirst();
    }
}
