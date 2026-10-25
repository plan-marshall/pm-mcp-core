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
 * How the attempt of a queue task ended, as the queue task record holds it (Store Schemas § Metrics &amp; Quality
 * Report, {@code attempt_end}; Model Work § Post-Completion Integrity Invariants, item 5).
 * <p>
 * Every metrics row of a task copies the end of its attempt, so that the share of attempts that ended with a clean exit
 * and no submission ({@link #EXITED_WITHOUT_SUBMIT}) is read per role, harness version, model and effort. {@code null}
 * is not a member of the set: the column is {@code null} only for a task closed without an attempt, whose cause class
 * is {@code refused}, and that absence is what {@link MetricsRow} requires.
 * <p>
 * The values mirror the queue task record's own enum ({@code QueueTaskRecord.AttemptEnd}). The vocabulary is declared
 * here because {@code core.queue} depends on {@code core.store} and not the other way round; the writer of a row copies
 * the value from the queue task record it reads.
 *
 * @since 0.1
 */
public enum AttemptEnd {

    /** The task closed with its submission. */
    CLOSED("closed"),
    /** The worker was lost without an observed exit. */
    WORKER_LOST("worker_lost"),
    /** The worker exited cleanly without submitting anything. */
    EXITED_WITHOUT_SUBMIT("exited_without_submit"),
    /** The attempt ended because its task is offered again later. */
    WAITED_AGAIN("waited_again"),
    /** The worker's budget expired. */
    BUDGET("budget"),
    /** The worker failed to start. */
    START_FAILURE("start_failure"),
    /** A restart ended the attempt. */
    RESTART("restart"),
    /** A drain ended the attempt. */
    DRAIN("drain"),
    /** An operator pause ended the attempt. */
    PAUSED("paused"),
    /** The worker was ended for an off-track signal. */
    OFF_TRACK("off_track"),
    /** The task was withdrawn. */
    WITHDRAWN("withdrawn"),
    /** The task's deadline expired. */
    DEADLINE_EXPIRED("deadline_expired"),
    /** The task was suspended after a question. */
    SUSPENDED("suspended");

    private final String wireName;

    AttemptEnd(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in a store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in a store
     * @return the value of that name; empty if no value has it
     */
    public static Optional<AttemptEnd> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
