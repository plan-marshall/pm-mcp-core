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
 * The class of the outcome of a primitive (PM-IMPL-5). It decides which step record the engine writes for the
 * outcome.
 *
 * @since 0.1
 */
public enum OutcomeClass {
    /** The operation did what it was called for. */
    SUCCESS,
    /** The operation had nothing to do. */
    SKIP,
    /** The operation failed. */
    FAILURE,
    /** The result of the operation cannot be established. */
    INDETERMINATE,
    /** The outcome depends on an event that has not arrived yet. */
    UNSETTLED,
    /** A session-driven sub-workflow was left through its implicit step out. */
    STEPPED_OUT
}
