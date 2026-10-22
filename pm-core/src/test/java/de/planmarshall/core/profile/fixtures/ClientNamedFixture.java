/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile.fixtures;

/**
 * Control of the harness-neutrality guard (test fixture): a class that names a client, on which the rule must fail.
 */
public final class ClientNamedFixture {

    /** A member that names a client. */
    public static final String OPENCODE = "opencode";

    private ClientNamedFixture() {
    }
}
