/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard.fixtures;

/**
 * Control of the provider-neutrality guard: an enumeration outside {@code FindingSourceKind} with a constant of an
 * exempt name, on which the rule must fail, because the exemption covers the two store constants and nothing else.
 */
public enum ExemptionScopeFixture {
    /** The name of an exempt constant of {@code FindingSourceKind}, declared in another type. */
    GITHUB_PR
}
