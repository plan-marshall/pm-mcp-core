/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Objects;
import java.util.Optional;

/**
 * The facts of the machine configuration a role reads (PM-WORK-1, Steward Role): today the default harness,
 * {@code harnesses.default} of {@code machine-config.json}, on which the steward runs.
 *
 * @param defaultHarness the client id of the machine's default harness, or empty before one is chosen
 * @since 0.1
 */
public record MachineConfiguration(Optional<String> defaultHarness) {

    /** Rejects a {@code null} component. */
    public MachineConfiguration {
        Objects.requireNonNull(defaultHarness, "defaultHarness");
    }
}
