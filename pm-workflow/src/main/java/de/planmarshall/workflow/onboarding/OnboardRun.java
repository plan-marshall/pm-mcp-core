/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.onboarding;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * How an aspect is called: the machine's first run, the automatic run, or a later run of one aspect from the settings or
 * from the automatic run's result ({@code onboard_run} of the aspect call states, State Catalogue § Machine Onboarding).
 *
 * @since 0.1
 */
public enum OnboardRun {

    /** The guided first run, {@code first_run}: the aspects in their fixed order, every choice an operator wait. */
    FIRST_RUN("first_run"),
    /** The automatic run, {@code auto}: each aspect's proposal accepted, the waits the proposal could not answer. */
    AUTO("auto"),
    /** A later run of one aspect, {@code reconfigure}: its current values as the proposal. */
    RECONFIGURE("reconfigure");

    private final String wireName;

    OnboardRun(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the value of {@code onboard_run}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the value of {@code onboard_run}
     * @return the run of that value; empty for a value the specification does not admit
     */
    public static Optional<OnboardRun> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(run -> run.wireName.equals(wireName)).findFirst();
    }
}
