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
 * The entry point a submission of the intent record arrives through (Phase Workflows § The Intent Record; Store
 * Schemas § Intent Record): {@code submit:refine}, the light lane's {@code submit:outline}, and the re-planning action
 * that changes intent. All three accept the record as {@code intent:schema[intent-record]}; the entry point changes
 * which transaction accepts it, never the record or its checks.
 *
 * @since 0.1
 */
public enum IntentSubmission {

    /** The refine task's {@code submit:refine}. */
    REFINE("submit:refine"),

    /** The light lane's {@code submit:outline}. */
    OUTLINE_LIGHT("submit:outline"),

    /** The re-planning action that changes intent. */
    REPLAN("replan");

    private final String wireName;

    IntentSubmission(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the entry point in the link grammar */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of an entry point in the link grammar
     * @return the entry point of that name; empty if none has it
     */
    public static Optional<IntentSubmission> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
