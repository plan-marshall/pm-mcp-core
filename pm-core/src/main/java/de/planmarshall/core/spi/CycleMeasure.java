/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

/**
 * The finite measure that strictly decreases on every pass of a cycle the engine chains through the state of a
 * primitive (PM-IMPL-5). A cycle of chained states is admitted only when a primitive on it declares a measure other
 * than {@link #NONE}, because only then the cycle is known to end.
 *
 * @since 0.1
 */
public enum CycleMeasure {
    /** The primitive declares no measure; a chained cycle through its state is not admitted on its account. */
    NONE,
    /** The unsettled steps of the step list of the plan instance: every pass fires and settles exactly one step. */
    STEP_LIST,
    /** The remaining archive steps of an epic that is being archived. */
    ARCHIVE_STEPS,
    /**
     * A compiled cap on the attempts of the primitive. Once the cap is spent the primitive returns its
     * {@link Primitive#exhaustionOutcome() exhaustion outcome}, which leaves every cycle through its state.
     */
    ATTEMPT_CAP
}
