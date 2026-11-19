/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.input;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A refusal of a value by {@link InputValidator}: the rule that rejected it and the value as it was offered, echoed
 * for no more than {@link #MAX_ECHO_LENGTH} characters so a large or sensitive value never leaves the refusal.
 * <p>
 * The refusal is a value, never an exception (ADR {@code workflow-verify/013}).
 *
 * @param ruleId     the identifier of the rule that rejected the value
 * @param inputValue the value as it was offered, truncated to at most {@link #MAX_ECHO_LENGTH} characters
 * @since 0.1
 */
public record InputRefusal(String ruleId, String inputValue) {

    /** The most characters of a refused value that a refusal echoes. */
    public static final int MAX_ECHO_LENGTH = 80;

    /**
     * @throws NullPointerException if either argument is {@code null}
     */
    public InputRefusal {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(inputValue, "inputValue");
        if (inputValue.length() > MAX_ECHO_LENGTH) {
            inputValue = inputValue.substring(0, MAX_ECHO_LENGTH);
        }
    }

    /**
     * Builds a refusal from a value that may be {@code null}, echoing {@code "<null>"} for an absent value.
     *
     * @param ruleId the identifier of the rule that rejected the value
     * @param value  the value as it was offered, {@code null} for an absent value
     * @return the refusal
     */
    public static InputRefusal of(@Nullable String value, String ruleId) {
        return new InputRefusal(ruleId, value == null ? "<null>" : value);
    }
}