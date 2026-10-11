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
 * The closed classification of a finding's location against the plan's effective footprint (member
 * {@code scope_class} of the findings ledger record, PM-IMPL-1), computed by the server at triage.
 *
 * @since 0.1
 */
public enum ScopeClass {
    /** The finding's path lies in the plan's effective footprint. */
    IN_FOOTPRINT("in_footprint"),
    /** The finding's path lies outside the plan's effective footprint. */
    OUT_OF_FOOTPRINT("out_of_footprint"),
    /** The finding names no path. */
    UNLOCATED("unlocated");

    private final String wireName;

    ScopeClass(String wireName) {
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
    public static Optional<ScopeClass> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
