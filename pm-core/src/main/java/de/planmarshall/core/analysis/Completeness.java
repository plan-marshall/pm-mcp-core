/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import java.util.Arrays;
import java.util.Optional;

/**
 * Whether the operation {@code analysis.issues} read every issue of its target (PM-IMPL-1).
 *
 * @since 0.1
 */
public enum Completeness {
    /** Every issue was read. */
    COMPLETE("complete"),
    /** Not every issue was read, for example because a page was cut off. */
    INCOMPLETE("incomplete");

    private final String wireName;

    Completeness(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value on the wire */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value on the wire
     * @return the value of that name; empty if no value has it
     */
    public static Optional<Completeness> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
