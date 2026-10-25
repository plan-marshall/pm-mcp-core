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
 * The closed reason a {@link Disposition#SUPPRESS} carries (PM-IMPL-2, Closed Suppression Reason). A suppression
 * without a reason, or one whose reason contradicts the record, is refused; there is no reason "other".
 *
 * @since 0.1
 */
public enum SuppressReason {

    /** The finding's claim is wrong; the only reason that resolves the finding {@code rejected}. */
    CLAIM_REFUTED("claim_refuted"),
    /** Another finding of the plan reports the same defect. */
    DUPLICATE("duplicate"),
    /** A rule of a skill the plan resolves permits the case. */
    RULE_EXCEPTION("rule_exception"),
    /** The finding lies in a path git does not track or marks generated. */
    GENERATED_CODE("generated_code"),
    /** The producing tool cannot see what makes the code correct. */
    TOOL_LIMITATION("tool_limitation");

    private final String wireName;

    SuppressReason(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the reason in the findings store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a reason in the findings store
     * @return the reason of that name; empty if no reason has it
     */
    public static Optional<SuppressReason> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
