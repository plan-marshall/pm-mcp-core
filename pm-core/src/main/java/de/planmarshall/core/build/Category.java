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
 * The category of a parsed build diagnostic.
 * <p>
 * The names follow the category names of the ported {@code plan-marshall} parsers in lower case. {@link #UNRECOGNIZED}
 * does not exist in the originals: it is the deliberate change of this port, so that an {@code [ERROR]} line no pattern
 * recognised is reported as unrecognised rather than as a real category (see ADR {@code core-build/002}).
 * {@link #OTHER} is the originals' {@code other}: a line whose severity the tool printed but whose text matched no
 * pattern. The Maven port keeps it for warnings; for errors it reports {@link #UNRECOGNIZED}.
 *
 * @since 0.1
 */
public enum Category {

    /** A Java compilation error such as {@code cannot find symbol}. */
    COMPILATION_ERROR,

    /** A failing test or a failing test phase. */
    TEST_FAILURE,

    /** A dependency resolution failure. */
    DEPENDENCY_ERROR,

    /** A Javadoc warning. */
    JAVADOC_WARNING,

    /** A deprecation warning. */
    DEPRECATION_WARNING,

    /** An unchecked-operation warning. */
    UNCHECKED_WARNING,

    /** A line of the OpenRewrite tooling rather than a diagnostic of the source. */
    OPENREWRITE_INFO,

    /** An error line no pattern of the compiled table recognised. */
    UNRECOGNIZED,

    /** A line the tool printed at error or warning level with no matching pattern: the originals' {@code other}. */
    OTHER
}
