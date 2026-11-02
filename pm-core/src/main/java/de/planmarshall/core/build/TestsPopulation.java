/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

/**
 * Whether the test counts of a parsed build are a measurement.
 * <p>
 * The distinction is load-bearing: {@link #UNMEASURED} means the parser could not tell summary lines from other count
 * lines, so it reports no figure at all rather than a zero. The ported {@code plan-marshall} parser keeps the summed
 * counts of an unmeasured population; this port does not, by the deliberate change recorded in ADR {@code core-build/002}
 * and specified in {@code doc/specification/job-runtime/03-parsers-and-gates.adoc}.
 *
 * @since 0.1
 */
public enum TestsPopulation {

    /** The counts were derived from summary blocks the parser recognised. */
    MEASURED,

    /** The parser could not aggregate the summaries, so it reports no test count. */
    UNMEASURED
}
