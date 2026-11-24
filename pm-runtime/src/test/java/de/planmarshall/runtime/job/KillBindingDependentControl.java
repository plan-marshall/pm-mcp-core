/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

/**
 * The positive control of the tracker's no-group-signal guard for an implementation of the primitive: it depends on
 * {@code KillBinding} on purpose, so the guard's implementation clause must report a violation for it. It carries none
 * of the tracker's name prefix, so the package scan of the real subject never selects it.
 *
 * @see ProcessTreeTrackerTest
 * @since 0.1
 */
final class KillBindingDependentControl {

    private final KillBinding binding;

    /**
     * @param binding the signalling implementation the control depends on
     */
    KillBindingDependentControl(KillBinding binding) {
        this.binding = binding;
    }

    /** @return the signalling implementation the control depends on */
    KillBinding binding() {
        return binding;
    }
}
