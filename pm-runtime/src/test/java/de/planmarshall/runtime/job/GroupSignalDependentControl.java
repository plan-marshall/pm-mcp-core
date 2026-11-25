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
 * The positive control of the tracker's no-group-signal guard: it depends on the numeric signalling primitive on
 * purpose, so the guard's rule must report a violation for it. It is no production code and no test of its own, and it
 * carries none of the tracker's name prefix, so the package scan of the real subject never selects it.
 *
 * @see ProcessTreeTrackerTest
 * @since 0.1
 */
final class GroupSignalDependentControl {

    private final ProcessGroupSignals signals;

    /**
     * @param signals the numeric signalling primitive the control depends on
     */
    GroupSignalDependentControl(ProcessGroupSignals signals) {
        this.signals = signals;
    }

    /** @return the primitive the control depends on */
    ProcessGroupSignals signals() {
        return signals;
    }
}
