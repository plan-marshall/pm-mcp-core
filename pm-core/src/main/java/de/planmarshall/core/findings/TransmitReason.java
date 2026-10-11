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
 * The closed reason why a transmission did not fully succeed (member {@code transmit_reason} of the findings ledger
 * record, PM-IMPL-1). It is one vocabulary for every provider; provider error text is never packed into it.
 *
 * @since 0.1
 */
public enum TransmitReason {
    /** There is no thread, comment id or issue key to answer on. */
    NO_PROVIDER_ADDRESS("no_provider_address"),
    /** The finding cannot be shown to belong to this pull request, because its number is not recorded. */
    PR_NUMBER_UNRECORDED("pr_number_unrecorded"),
    /** The finding belongs to another pull request. */
    BELONGS_TO_OTHER_PR("belongs_to_other_pr"),
    /** The disposition carries no reply. */
    NOTHING_TO_TRANSMIT("nothing_to_transmit"),
    /** The provider call for the reply failed. */
    REPLY_FAILED("reply_failed"),
    /** The provider call for the thread resolution failed. */
    RESOLVE_FAILED("resolve_failed"),
    /** The provider could not be reached. */
    PROVIDER_UNREACHABLE("provider_unreachable"),
    /** The provider rejected the call. */
    PROVIDER_REJECTED("provider_rejected");

    private final String wireName;

    TransmitReason(String wireName) {
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
    public static Optional<TransmitReason> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
