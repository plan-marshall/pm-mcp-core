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
 * The closed state of the provider thread of a finding (member {@code resolve_state} of the findings ledger record,
 * PM-IMPL-1). It is persisted apart from the transmission, so a retry of the resolution never re-sends the reply.
 *
 * @since 0.1
 */
public enum ResolveState {
    /** The thread is not resolved. */
    UNRESOLVED("unresolved"),
    /** The thread is resolved. */
    RESOLVED("resolved"),
    /** The finding has no thread to resolve. */
    NOT_APPLICABLE("not_applicable");

    private final String wireName;

    ResolveState(String wireName) {
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
    public static Optional<ResolveState> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
