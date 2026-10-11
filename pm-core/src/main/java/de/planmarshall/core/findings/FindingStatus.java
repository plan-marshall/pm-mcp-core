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

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed status of a finding in its lifecycle (member {@code status} of the findings ledger record, PM-IMPL-1).
 *
 * @since 0.1
 */
public enum FindingStatus {
    /** The finding is filed and not yet triaged. */
    OPEN("open"),
    /** Triage decided to fix the finding. */
    TRIAGED_FIX("triaged_fix"),
    /** Triage dismissed the finding. */
    TRIAGED_DISMISS("triaged_dismiss"),
    /** Triage accepted the finding as it is. */
    TRIAGED_ACCEPT("triaged_accept"),
    /** The finding is resolved. */
    RESOLVED("resolved"),
    /** The finding was still pending after the merge and is carried out of the plan. */
    CARRIED("carried");

    private final String wireName;

    FindingStatus(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in the findings store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in the findings store
     * @return the value of that name; empty if no value has it
     */
    public static Optional<FindingStatus> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
