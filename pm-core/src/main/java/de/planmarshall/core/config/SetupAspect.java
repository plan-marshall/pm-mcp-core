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
import java.util.Objects;
import java.util.Optional;

/**
 * One aspect of a project's setup, in the order its single-aspect run offers it (State Catalogue § Project Setup,
 * {@code setup.entry}): the value is what {@code project.completed}'s {@code aspect:<name>} link names and what the run
 * records in {@code setup_aspect}. The six aspects below are the setup's; {@code aspect:operator_values} is not one of
 * them, because that link enters {@code project.operator} directly.
 * <p>
 * The list lives in this module because the unit that routes an aspect and the runtime that serves the links read the
 * same names, so the closed list and its order exist once.
 *
 * @since 0.1
 */
public enum SetupAspect {

    /** The team's settings, {@code setup.project-settings}. */
    TEAM_SETTINGS("team_settings"),
    /** The build map, {@code setup.route-setup-run}. */
    BUILD_MAP("build_map"),
    /** The team's providers, {@code setup.select-providers}. */
    PROVIDERS("providers"),
    /** The finalize steps and the landing variant, {@code setup.configure-finalize}. */
    FINALIZE("finalize"),
    /** Role qualification, {@code setup.probe-qualification}. */
    QUALIFICATION("qualification"),
    /** Module discovery and enrichment, {@code setup.discover-modules}. */
    ARCHITECTURE("architecture");

    private final String wireName;

    SetupAspect(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the aspect in an {@code aspect:<name>} link and in {@code setup_aspect} */
    public String wireName() {
        return wireName;
    }

    /**
     * @return the aspect the single-aspect run offers after this one; empty for the last one
     */
    public Optional<SetupAspect> next() {
        var following = ordinal() + 1;
        return following < values().length ? Optional.of(values()[following]) : Optional.empty();
    }

    /**
     * @param wireName the name of an {@code aspect:<name>} link
     * @return the aspect of that name; empty for a name no aspect has
     */
    public static Optional<SetupAspect> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(aspect -> aspect.wireName.equals(wireName)).findFirst();
    }
}
