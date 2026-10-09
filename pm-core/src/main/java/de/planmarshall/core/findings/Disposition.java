/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

/**
 * The closed dispositions triage gives a finding (PM-IMPL-1). The names are the values on the wire and in the
 * findings store.
 *
 * @since 0.1
 */
public enum Disposition {
    /** The finding is valid and is fixed. */
    FIX,
    /** The finding is valid and is accepted as it is. */
    ACCEPT,
    /** The finding is dismissed. */
    SUPPRESS
}
