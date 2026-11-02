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
 * The status a build tool reported, derived from its own success and failure markers.
 * <p>
 * The status is what the tool printed, not what the parsed result proves: a build that printed its success marker while
 * reporting failing tests is {@link #SUCCESS} with a non-zero failure count, and resolving that contradiction is the
 * consumer's job. {@link ParsedBuild#successMarker()} is the positive marker the status was derived from.
 *
 * @since 0.1
 */
public enum BuildStatus {

    /** The tool printed its success marker. */
    SUCCESS,

    /** The tool printed a failure marker, or printed no marker at all. */
    FAILURE
}
