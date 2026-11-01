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
 * How serious a parsed build diagnostic is.
 * <p>
 * The two values are the parsed severity of a diagnostic, never the outcome of a gate: an {@link #ERROR} is a line the
 * build tool printed at error level.
 *
 * @since 0.1
 */
public enum Severity {

    /** A diagnostic the build tool reported at error level. */
    ERROR,

    /** A diagnostic the build tool reported at warning level. */
    WARNING
}
