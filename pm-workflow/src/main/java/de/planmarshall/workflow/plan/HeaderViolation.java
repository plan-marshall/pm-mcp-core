/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

/**
 * One constraint of the plan instance header schema that a header breaks (PM-IMPL-5): where, as a JSON pointer, and
 * what is wrong.
 *
 * @param pointer the JSON pointer (RFC 6901) of the member or element that breaks the constraint, the empty string
 *                for the header as a whole; a missing member is named by the pointer it would have
 * @param reason  what is wrong
 * @since 0.1
 */
public record HeaderViolation(String pointer, String reason) {

    /**
     * Refuses a missing component.
     *
     * @param pointer the JSON pointer of the defect
     * @param reason  what is wrong
     */
    public HeaderViolation {
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(reason, "reason");
    }
}
