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
 * The closed outcome of a finding (member {@code resolution} of the findings ledger record, PM-IMPL-1). The server
 * writes it from recorded evidence; the model submits only a {@link Disposition}. {@link #PENDING} is the only
 * value that is not terminal.
 *
 * @since 0.1
 */
public enum Resolution {
    /** The finding is filed, or triaged with its effect not yet recorded. */
    PENDING("pending"),
    /** The fix of the finding landed. */
    FIXED("fixed"),
    /** The finding is valid and was dismissed. */
    SUPPRESSED("suppressed"),
    /** The finding is a known gap that was accepted. */
    ACCEPTED("accepted"),
    /** The finding is not the concern of this plan. */
    TAKEN_INTO_ACCOUNT("taken_into_account"),
    /** The claim of the finding was refuted by evidence. */
    REJECTED("rejected");

    private final String wireName;

    Resolution(String wireName) {
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
    public static Optional<Resolution> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
