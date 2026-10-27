/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.util.Objects;

import de.planmarshall.core.role.RoleArtifact;

/**
 * One off-track signal the runtime computed from a task's activity, with the action the role artifact chose (Model Work
 * § A Worker Off Track).
 * <p>
 * The runtime reports it to the task protocol; the protocol records it in the queue task's {@code signals[]} and as a
 * {@code signal} entry of the activity journal, and takes the action.
 *
 * @param signal      the signal
 * @param action      the action the role artifact chose; {@code show} where it named none
 * @param measurement the measured value
 * @param threshold   the threshold in force
 * @since 0.1
 */
public record ActivitySignal(RoleArtifact.OffTrackSignal signal, RoleArtifact.OffTrackAction action,
double measurement, double threshold) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a value is not finite or negative
     */
    public ActivitySignal {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(action, "action");
        if (measurement < 0 || threshold < 0 || !Double.isFinite(measurement) || !Double.isFinite(threshold)) {
            throw new IllegalArgumentException("A signal's measurement and threshold are finite and not negative");
        }
    }
}
