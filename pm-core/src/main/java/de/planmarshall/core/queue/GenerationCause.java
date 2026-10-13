/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.util.Arrays;
import java.util.Optional;

/**
 * What issued a session generation (PM-WORK-5), as {@code issued_by} of the session generation record names it. The
 * set is closed.
 *
 * @since 0.1
 */
public enum GenerationCause {

    /** The first binding of a session relay that found no current generation of its session identity. */
    RELAY_START("relay_start"),
    /** The relay observed a new host session: a changed host session identifier or a host marker. */
    NEW_SESSION("new_session"),
    /** {@code pm-mcp enter} of the session identity. */
    ENTER("enter");

    private final String wireName;

    GenerationCause(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in the record */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in the record
     * @return the value of that name; empty if no value has it
     */
    public static Optional<GenerationCause> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
