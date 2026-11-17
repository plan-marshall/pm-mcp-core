/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.onboarding;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Why the automatic run decided a value, the closed list of the specification (CLI &amp; Security § Automatic
 * Onboarding): {@code system}, {@code only_supported}, {@code all_supported}, {@code all_found}, {@code default},
 * {@code available}, {@code tool_logged_in} and {@code asked}. Each reason is a message key of the catalogue,
 * {@code onboard.auto.reason.<reason>}, which the result's items carry.
 *
 * @since 0.1
 */
public enum ProposalReason {

    /** From the operator's system, for example the system language. */
    SYSTEM("system"),
    /** The only supported harness or version found. */
    ONLY_SUPPORTED("only_supported"),
    /** Every supported one was found; the first in the order of its id is the default. */
    ALL_SUPPORTED("all_supported"),
    /** Every one the machine found was chosen. */
    ALL_FOUND("all_found"),
    /** The default the specification names, with no detection needed. */
    DEFAULT("default"),
    /** Available on this machine, for example an OS credential store. */
    AVAILABLE("available"),
    /** The tool logged in, naming the tool. */
    TOOL_LOGGED_IN("tool_logged_in"),
    /** The proposal was incomplete, so the operator was asked in the run. */
    ASKED("asked");

    /** The prefix of the message key of every reason. */
    public static final String MESSAGE_KEY_PREFIX = "onboard.auto.reason.";

    private final String wireName;

    ProposalReason(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the value of the reason
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @return the message key the result's item carries, {@code onboard.auto.reason.<reason>}
     */
    public String messageKey() {
        return MESSAGE_KEY_PREFIX + wireName;
    }

    /**
     * @param wireName the value of a reason
     * @return the reason of that value; empty for a value the closed list does not admit
     */
    public static Optional<ProposalReason> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(reason -> reason.wireName.equals(wireName)).findFirst();
    }
}
