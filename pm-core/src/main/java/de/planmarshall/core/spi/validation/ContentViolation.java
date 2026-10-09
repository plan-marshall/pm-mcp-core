/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import java.util.Objects;

/**
 * One rule a submitted text breaks (PM-WF-6).
 *
 * @param ruleKey the stable key of the rule, for example {@code commit.co-authored-by}; a caller tells violations
 *        apart by it, never by the message
 * @param line the line the violation was found on, counted from 1; {@code 0} when it concerns the whole text
 * @param message what is wrong, naming the offending content
 * @since 0.1
 */
public record ContentViolation(String ruleKey, int line, String message) {

    /**
     * @throws NullPointerException if the rule key or the message is {@code null}
     * @throws IllegalArgumentException if the line is negative
     */
    public ContentViolation {
        Objects.requireNonNull(ruleKey, "ruleKey");
        Objects.requireNonNull(message, "message");
        if (line < 0) {
            throw new IllegalArgumentException("line must not be negative: " + line);
        }
    }
}
