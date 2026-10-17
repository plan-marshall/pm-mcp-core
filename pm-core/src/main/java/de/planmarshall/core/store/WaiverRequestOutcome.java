/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Arrays;
import java.util.Optional;

/**
 * How a {@link WaiverRequest} ended (PM-SEC-3). The set is closed; a request that is still pending has no outcome, and
 * no outcome ends a request by time: there is no {@code expired}.
 *
 * @since 0.1
 */
public enum WaiverRequestOutcome {

    /** The operator answered with a {@code met} waiver. */
    WAIVED("waived"),
    /** The operator answered with a {@code raise} of the ceiling. */
    RAISED("raised"),
    /** The operator declined the request. */
    DECLINED("declined"),
    /** The plan ended while the request was pending. */
    CANCELLED("cancelled");

    private final String wireName;

    WaiverRequestOutcome(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in a record */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in a record
     * @return the value of that name; empty if no value has it
     */
    public static Optional<WaiverRequestOutcome> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
