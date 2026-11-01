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
 * The fail-closed verdict of the OpenRewrite log parse.
 * <p>
 * Only {@link #OBSERVED} carries findings. The other three are distinct non-clean states, never a vacuous "clean": a build
 * that never reached {@code rewrite:run} did not observe anything, and neither did one of a project whose domain does not
 * provide the parser.
 *
 * @since 0.1
 */
public enum RewriteVerdict {

    /** The build reached {@code rewrite:run} and the java-cui domain was active, so the log was parsed. */
    OBSERVED,

    /** The build never reached {@code rewrite:run}, so no finding was produced this run. */
    NOT_OBSERVED,

    /** The build reached {@code rewrite:run}, but the java-cui domain is not active. */
    DOMAIN_INACTIVE,

    /** The log carried a finding line the parser could not read, so nothing was observed this run. */
    PARSE_ERROR
}
