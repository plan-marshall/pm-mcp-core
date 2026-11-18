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
 * The run a project setup records as {@code setup_run}, or the entry the caller named (State Catalogue § Project Setup,
 * {@code setup.entry}): the first run, a reconcile run, one aspect alone, and the {@code integrations} entry that
 * continues a first run after the project's operator values. The {@code integrations} entry is never a recorded run.
 * <p>
 * The value is recorded from server facts, never from a model, and every later fork of the run follows it, so an
 * interrupted first run keeps its first-run path. The closed list lives here because the unit, the runtime and the
 * clients name the same four entries.
 *
 * @since 0.1
 */
public enum SetupRun {

    /** The first configuration of the project; the run that creates {@code .marshall/config/}. */
    FIRST_RUN("first_run"),
    /** The first run continued after the project's operator values, entered at {@code setup.probe-integration-variants}. */
    INTEGRATIONS("integrations"),
    /** A later run of the whole setup. */
    RECONCILE("reconcile"),
    /** One aspect alone, its name recorded beside this run. */
    ASPECT("aspect");

    private final String wireName;

    SetupRun(String wireName) {
        this.wireName = wireName;
    }

    /** @return the value of {@code setup_run} or the name of an entry of {@code project.setup}; {@code integrations} is
     *          an entry only and is never recorded as a run */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName a value of {@code setup_run} or an entry of {@code project.setup}
     * @return the run; empty for a value outside the closed list
     */
    public static Optional<SetupRun> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(run -> run.wireName.equals(wireName)).findFirst();
    }
}
