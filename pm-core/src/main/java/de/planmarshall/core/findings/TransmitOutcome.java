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
 * The closed outcome of the outbound reply or dismissal for a finding's disposition (member
 * {@code transmit_outcome} of the findings ledger record, PM-IMPL-1).
 *
 * @since 0.1
 */
public enum TransmitOutcome {
    /** The transmission has not happened yet, or its outcome is not yet reconciled. */
    PENDING("pending"),
    /** The provider accepted the reply and, where it applies, the thread resolution. */
    SENT("sent"),
    /** Nothing was sent; the {@link TransmitReason} says why. */
    NOT_SENT("not_sent"),
    /** The reply was sent but the thread could not be resolved. */
    SENT_UNRESOLVED("sent_unresolved"),
    /** The finding has no address on the provider to answer on. */
    SKIPPED_NO_ADDRESS("skipped_no_address"),
    /** Only a part of the transmission succeeded. */
    PARTIAL("partial");

    private final String wireName;

    TransmitOutcome(String wireName) {
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
    public static Optional<TransmitOutcome> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
