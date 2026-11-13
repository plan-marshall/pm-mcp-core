/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

/**
 * The cost size of a plan task, the {@code cost_size} of its task record.
 * <p>
 * The sizes are ordered from the smallest to the largest, so that {@link #compareTo(Enum)} follows the score bands of
 * {@link TaskCostEstimator}: a higher score never maps to a smaller size. The size-to-token table
 * {@link SizeTokenTable} gives the predicted tokens of each size.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}, § Cost Sizing, item 2.
 */
public enum CostSize {

    /** A score below 30. */
    XS,

    /** A score from 30 up to below 60. */
    S,

    /** A score from 60 up to below 150. */
    M,

    /** A score from 150 up to below 300. */
    L,

    /** A score from 300 up to below 700. */
    XL,

    /** A score of 700 or more. */
    XXL
}
