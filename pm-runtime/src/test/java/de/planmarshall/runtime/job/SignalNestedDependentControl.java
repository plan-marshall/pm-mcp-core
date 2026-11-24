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
 * The positive control of the tracker's no-group-signal guard for the primitive's nested type: it depends on
 * {@code ProcessGroupSignals.Signal} on purpose, so the guard's nested-type clause must report a violation for it. It
 * carries none of the tracker's name prefix, so the package scan of the real subject never selects it.
 *
 * @see ProcessTreeTrackerTest
 * @since 0.1
 */
final class SignalNestedDependentControl {

    private final ProcessGroupSignals.Signal signal;

    /**
     * @param signal the nested signal type the control depends on
     */
    SignalNestedDependentControl(ProcessGroupSignals.Signal signal) {
        this.signal = signal;
    }

    /** @return the nested signal type the control depends on */
    ProcessGroupSignals.Signal signal() {
        return signal;
    }
}
