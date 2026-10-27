/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.question;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The question bound (Model Work § Question Bound, Timeouts &amp; Budgets § Question Bound): the {@code ask} link is
 * offered to the tasks of one entry of a state at most {@value #MAX_ASKS} times, re-entries through
 * {@code <state>.asked} continuing the entry, and {@code ask:TASK-NNN} at most {@value #MAX_ASKS} times per plan task
 * over all its envelope tasks, whichever form each question took. After that the link is not offered.
 * <p>
 * The bound is counted per axis because the two axes answer different questions: the state-entry axis bounds a state
 * that keeps asking, and the plan-task axis bounds a plan task whose work moves between envelope tasks. A question
 * counts on both axes it belongs to. {@code ask} and {@code ask fresh} count alike: the bound is on the link, never on
 * the continuation form.
 * <p>
 * The ledger is the server's, held in memory for the lifetime of the queue that serves the scope; a restart rebuilds it
 * from the scope's question records, which is what the caller owes. The engine passes the same {@code stateEntryId} on a
 * re-entry through {@code <state>.asked} and the entry's count continues; a new entry of the state has a new identity
 * and a fresh count.
 *
 * @since 0.1
 */
public final class QuestionBound {

    /** The compiled number of times the {@code ask} link is offered per axis. */
    public static final int MAX_ASKS = 3;

    /** The axis a count belongs to. */
    public enum Axis {
        /** One entry of a state, re-entries through {@code <state>.asked} continuing the entry. */
        STATE_ENTRY("state_entry"),
        /** One plan task over all its envelope tasks. */
        PLAN_TASK("plan_task");

        private final String wireName;

        Axis(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the axis in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One counted question.
     *
     * @param axis the axis the count belongs to
     * @param id   the entry identity or the plan task id
     * @since 0.1
     */
    public record Key(Axis axis, String id) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the id is blank
         */
        public Key {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("A question-bound key names its entry or plan task");
            }
        }
    }

    private final Map<Key, Integer> asked = new HashMap<>();

    /**
     * @param key the state entry or plan task
     * @return how many questions it asked so far
     */
    public synchronized int asked(Key key) {
        return asked.getOrDefault(Objects.requireNonNull(key, "key"), 0);
    }

    /**
     * @param key the state entry or plan task
     * @return how many questions it may still ask
     */
    public synchronized int remaining(Key key) {
        return Math.max(0, MAX_ASKS - asked(key));
    }

    /**
     * @param key the state entry or plan task
     * @return whether the {@code ask} link is still offered on it
     */
    public synchronized boolean mayOffer(Key key) {
        return remaining(key) > 0;
    }

    /**
     * Counts one question on the key and answers whether the link was still offered before it, which is what the
     * server decides the offer on.
     *
     * @param key the state entry or plan task
     * @return {@code true} when the question was within the bound and is now counted; {@code false} when the bound was
     *         already spent and nothing was counted
     */
    public synchronized boolean record(Key key) {
        Objects.requireNonNull(key, "key");
        var used = asked.getOrDefault(key, 0);
        if (used >= MAX_ASKS) {
            return false;
        }
        asked.put(key, used + 1);
        return true;
    }

    /**
     * Counts one question on both axes it belongs to: the state entry the task works under, and the plan task it
     * serves.
     *
     * @param stateEntry the state entry
     * @param planTask   the plan task
     * @return whether both axes still offer the link; when either is spent, neither is counted
     */
    public synchronized boolean record(Key stateEntry, Key planTask) {
        if (!mayOffer(stateEntry) || !mayOffer(planTask)) {
            return false;
        }
        record(stateEntry);
        record(planTask);
        return true;
    }

    /**
     * @param stateEntry the state entry
     * @param planTask   the plan task
     * @return whether both axes still offer the {@code ask} link
     */
    public synchronized boolean mayOffer(Key stateEntry, Key planTask) {
        return mayOffer(stateEntry) && mayOffer(planTask);
    }
}
