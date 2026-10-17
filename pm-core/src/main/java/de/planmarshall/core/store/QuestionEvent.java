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
 * What one line of a question log records about its question (PM-WF-12). The set is closed.
 * <p>
 * Three events close a question: {@link #ANSWERED}, {@link #WITHDRAWN} and {@link #CANCELLED}. A question has at most
 * one closing line; no event closes it by time.
 *
 * @since 0.1
 */
public enum QuestionEvent {

    /** The question was created as a part of its decision. */
    ASKED("asked"),
    /** The open decision was changed; the line carries the new revision. */
    REVISED("revised"),
    /** A revision of the decision was adjudicated by its quorum. */
    ADJUDICATED("adjudicated"),
    /** A revision of the decision was delivered to the operator's clients. */
    DELIVERED("delivered"),
    /** The question was answered; a closing event. */
    ANSWERED("answered"),
    /** The server withdrew the open question; a closing event. */
    WITHDRAWN("withdrawn"),
    /** The end of the question's scope cancelled it; a closing event. */
    CANCELLED("cancelled");

    private final String wireName;

    QuestionEvent(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the event in a record */
    public String wireName() {
        return wireName;
    }

    /** @return whether a line of this event closes its question */
    public boolean closes() {
        return this == ANSWERED || this == WITHDRAWN || this == CANCELLED;
    }

    /**
     * @param wireName the name of an event in a record
     * @return the event of that name; empty if no event has it
     */
    public static Optional<QuestionEvent> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
