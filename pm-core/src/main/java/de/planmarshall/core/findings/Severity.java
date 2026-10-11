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
 * The closed severity of a finding (member {@code severity} of the findings ledger record, PM-IMPL-1). It is set
 * when the finding is created and never changed.
 *
 * @since 0.1
 */
public enum Severity {
    /** The finding blocks any further work on the change. */
    BLOCKER("blocker"),
    /** A critical finding. */
    CRITICAL("critical"),
    /** A major finding. */
    MAJOR("major"),
    /** A minor finding. */
    MINOR("minor"),
    /** An informational finding. */
    INFO("info");

    private final String wireName;

    Severity(String wireName) {
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
    public static Optional<Severity> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
