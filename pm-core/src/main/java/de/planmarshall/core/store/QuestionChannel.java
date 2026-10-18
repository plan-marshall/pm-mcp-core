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
 * The channel through which a question was answered (PM-WF-12, PM-SEC-3). The set is closed: no channel is configured
 * per project.
 *
 * @since 0.1
 */
public enum QuestionChannel {

    /** A paired device of the web app. */
    WEB("web"),
    /** The terminal client. */
    CLI("cli"),
    /** The quorum the declaration of the decision names decided the set. */
    QUORUM("quorum");

    private final String wireName;

    QuestionChannel(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the channel in a record */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a channel in a record
     * @return the channel of that name; empty if no channel has it
     */
    public static Optional<QuestionChannel> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
