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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The attempts of the server-rule retries and of the compiled condition budgets counted outside a condition, the
 * member {@code retry_counters} of the workflow state store (PM-WF-7: a budget is persisted and never reset;
 * PM-ARCH-4: no state is lost by a restart; PM-ARCH-6: the status document is the persisted state).
 * <p>
 * Per retry rule there is at most one entry, bound to the state whose primitive counts the retry. The primitive
 * counts an attempt with {@link #attempt(RetryRule, String)} in the same status write as the retry it records, and
 * drops its entry with {@link #clearedFor(String)} in the transaction in which its state settles an outcome that ends
 * the retry; the engine drops the entries of every state of a sub-workflow with {@link #clearedFor(String)} when it
 * pops or unwinds the frame of that sub-workflow. Nothing else lowers a count: a runtime restart or a drain reads the
 * same value from the store again.
 * <p>
 * The value is immutable; every operation returns a new one.
 *
 * @param entries the entries by rule; an unmodifiable copy in the order of the rules is kept
 * @since 0.1
 */
public record RetryCounters(Map<RetryRule, Entry> entries) {

    private static final RetryCounters EMPTY = new RetryCounters(Map.of());

    private static final String STATE_ID = "stateId";

    /**
     * The retry rules whose attempts are counted, as the keys of {@code retry_counters} name them. The set is closed.
     *
     * @since 0.1
     */
    public enum RetryRule {
        /** {@code sync_failed} of {@code finalize.merge-sync}: a failed fetch or push of the merge sync. */
        SYNC_FAILED("sync_failed"),
        /** {@code indeterminate} of {@code finalize.merge-barrier}: the findings store could not be evaluated. */
        MERGE_BARRIER_INDETERMINATE("merge_barrier_indeterminate"),
        /** {@code re_enter} of {@code finalize.merge-refused}: a refusal the merge gate is entered again for. */
        MERGE_REFUSED_RE_ENTER("merge_refused_re_enter"),
        /** {@code re_enter} of {@code finalize.merge-queue-exit}: an ejected pull request. */
        MERGE_QUEUE_RE_ENTER("merge_queue_re_enter"),
        /** {@code push_failed} counted at {@code git.push-retry-wait}: a push failure other than a rejection. */
        PUSH_FAILED("push_failed"),
        /** The provider call of {@code finalize.open-pr}. */
        OPEN_PR("open_pr"),
        /** The conservative retry once per unit instance of a remediation unit's {@code conservative} state. */
        RETRY_ONCE("retry_once"),
        /**
         * A compiled condition budget counted outside a condition: {@code finalize.adopt-blocked},
         * {@code finalize.merge-lease-interrupted}, {@code finalize.archive-plan}.
         */
        CONDITION_BUDGET("condition_budget");

        private final String wireName;

        RetryRule(String wireName) {
            this.wireName = wireName;
        }

        /** @return the key of the rule in {@code retry_counters} */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a key of {@code retry_counters}
         * @return the rule of that key; empty if no rule has it
         */
        public static Optional<RetryRule> ofWireName(String wireName) {
            return Arrays.stream(values()).filter(rule -> rule.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The attempts of one rule at the state that counts them.
     *
     * @param stateId  the state whose primitive counts the retry
     * @param attempts the retries taken by the rule at that state, at least 0
     * @since 0.1
     */
    public record Entry(String stateId, long attempts) {

        /**
         * @throws NullPointerException     if the state is {@code null}
         * @throws IllegalArgumentException if the state is blank or the attempts are negative
         */
        public Entry {
            Objects.requireNonNull(stateId, STATE_ID);
            if (stateId.isBlank()) {
                throw new IllegalArgumentException("'state_id' of a retry counter must not be blank");
            }
            if (attempts < 0) {
                throw new IllegalArgumentException("'attempts' of a retry counter must be at least 0: " + attempts);
            }
        }
    }

    /**
     * @throws NullPointerException if the map, a rule or an entry is {@code null}
     */
    public RetryCounters {
        Objects.requireNonNull(entries, "entries");
        var copy = new EnumMap<RetryRule, Entry>(RetryRule.class);
        entries.forEach((rule, entry) -> copy.put(Objects.requireNonNull(rule, "rule"),
                Objects.requireNonNull(entry, "entry")));
        entries = Collections.unmodifiableMap(copy);
    }

    /** @return the counters of a scope that has not retried yet, as an absent member reads */
    public static RetryCounters empty() {
        return EMPTY;
    }

    /**
     * Counts one attempt of a rule at a state. An entry of the rule bound to another state is replaced: the new
     * state's budget starts at one attempt.
     *
     * @param rule    the rule that retries
     * @param stateId the state whose primitive counts the retry
     * @return the counters with the attempt counted
     */
    public RetryCounters attempt(RetryRule rule, String stateId) {
        Objects.requireNonNull(rule, "rule");
        var counted = new EnumMap<RetryRule, Entry>(RetryRule.class);
        counted.putAll(entries);
        counted.put(rule, new Entry(stateId, attemptsOf(rule, stateId) + 1));
        return new RetryCounters(counted);
    }

    /**
     * @param rule    a rule
     * @param stateId a state
     * @return the attempts of the rule at the state; 0 when the rule has no entry or its entry is bound to another
     *         state
     */
    public long attemptsOf(RetryRule rule, String stateId) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(stateId, STATE_ID);
        var entry = entries.get(rule);
        return entry != null && entry.stateId().equals(stateId) ? entry.attempts() : 0;
    }

    /**
     * Drops every entry bound to a state: its retry ended, or the frame of its sub-workflow was popped.
     *
     * @param stateId a state
     * @return the counters without the entries of the state; this value if it had none
     */
    public RetryCounters clearedFor(String stateId) {
        Objects.requireNonNull(stateId, STATE_ID);
        if (entries.values().stream().noneMatch(entry -> entry.stateId().equals(stateId))) {
            return this;
        }
        var kept = new EnumMap<RetryRule, Entry>(RetryRule.class);
        entries.forEach((rule, entry) -> {
            if (!entry.stateId().equals(stateId)) {
                kept.put(rule, entry);
            }
        });
        return new RetryCounters(kept);
    }

    /** @return whether no rule has an entry */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
