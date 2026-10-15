/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

/**
 * A gate a {@code met} waiver marks as waived in a plan instance (PM-IMPL-5): one entry of {@code waived_gates[]} in
 * the instance header.
 *
 * @param gate     the waivable gate identifier
 * @param waiverId the id of the waiver, {@code W-<n>}
 * @since 0.1
 */
public record WaivedGate(String gate, String waiverId) {

    /**
     * Refuses a missing component.
     *
     * @param gate     the waivable gate identifier
     * @param waiverId the id of the waiver
     */
    public WaivedGate {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(waiverId, "waiverId");
    }
}
