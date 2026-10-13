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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.RetryCounters.Entry;
import de.planmarshall.core.store.RetryCounters.RetryRule;

/**
 * The retry counts of the server-rule retries (PM-WF-7: a budget is never reset). Only an attempt raises a count and
 * only the end of the retry or the pop of the counting frame drops it; no operation lowers a count otherwise, so a
 * restart that reads the store again cannot hand a plan more retries than its budget.
 */
@DisplayName("Retry counters")
class RetryCountersTest {

    private static final String MERGE_SYNC = "finalize.merge-sync";
    private static final String MERGE_BARRIER = "finalize.merge-barrier";

    @Test
    @DisplayName("the closed rule set is the key list of retry_counters in the specification")
    void closedRuleSet() {
        assertEquals(List.of("sync_failed", "merge_barrier_indeterminate", "merge_refused_re_enter",
                        "merge_queue_re_enter", "push_failed", "open_pr", "retry_once", "condition_budget"),
                Arrays.stream(RetryRule.values()).map(RetryRule::wireName).toList());
    }

    @Test
    @DisplayName("a key is resolved to its rule; an unknown key resolves to none")
    void ofWireName() {
        assertAll(
                () -> assertEquals(Optional.of(RetryRule.PUSH_FAILED), RetryRule.ofWireName("push_failed")),
                () -> assertEquals(Optional.empty(), RetryRule.ofWireName("re_enter")));
    }

    @Test
    @DisplayName("attempt counts per rule and state")
    void attemptCountsPerRuleAndState() {
        var counters = RetryCounters.empty()
                .attempt(RetryRule.SYNC_FAILED, MERGE_SYNC)
                .attempt(RetryRule.SYNC_FAILED, MERGE_SYNC)
                .attempt(RetryRule.MERGE_BARRIER_INDETERMINATE, MERGE_BARRIER);

        assertAll(
                () -> assertEquals(2, counters.attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC)),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.MERGE_BARRIER_INDETERMINATE, MERGE_BARRIER)));
    }

    @Test
    @DisplayName("attemptsOf reads 0 for an absent rule and for an entry bound to another state")
    void attemptsOfReadsZero() {
        var counters = RetryCounters.empty().attempt(RetryRule.SYNC_FAILED, MERGE_SYNC);

        assertAll(
                () -> assertEquals(0, counters.attemptsOf(RetryRule.PUSH_FAILED, MERGE_SYNC), "absent rule"),
                () -> assertEquals(0, counters.attemptsOf(RetryRule.SYNC_FAILED, MERGE_BARRIER), "other state"),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC), "matched control"));
    }

    @Test
    @DisplayName("an attempt at another state replaces the entry and starts its budget at one")
    void attemptAtAnotherStateReplaces() {
        var counters = RetryCounters.empty()
                .attempt(RetryRule.CONDITION_BUDGET, "finalize.adopt-blocked")
                .attempt(RetryRule.CONDITION_BUDGET, "finalize.adopt-blocked")
                .attempt(RetryRule.CONDITION_BUDGET, "finalize.archive-plan");

        assertEquals(Map.of(RetryRule.CONDITION_BUDGET, new Entry("finalize.archive-plan", 1)), counters.entries());
    }

    @Test
    @DisplayName("clearedFor drops only the entries of the state that is left")
    void clearedForDropsOnlyThatState() {
        var counters = RetryCounters.empty()
                .attempt(RetryRule.SYNC_FAILED, MERGE_SYNC)
                .attempt(RetryRule.MERGE_BARRIER_INDETERMINATE, MERGE_BARRIER);

        var cleared = counters.clearedFor(MERGE_SYNC);

        assertAll(
                () -> assertEquals(0, cleared.attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC)),
                () -> assertEquals(1, cleared.attemptsOf(RetryRule.MERGE_BARRIER_INDETERMINATE, MERGE_BARRIER)),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC),
                        "the original value is unchanged"),
                () -> assertSame(counters, counters.clearedFor("finalize.merge-gate"),
                        "a state without entries leaves the value as it is"));
    }

    @Test
    @DisplayName("no operation lowers a count: attempts only grow until the entry is dropped")
    void noOperationDecrements() {
        var counters = RetryCounters.empty();
        var previous = 0L;
        for (var round = 0; round < 5; round++) {
            counters = counters.attempt(RetryRule.RETRY_ONCE, "remediation-ci.conservative")
                    .clearedFor("remediation-ci.rerun")
                    .attempt(RetryRule.OPEN_PR, "finalize.open-pr");
            var now = counters.attemptsOf(RetryRule.RETRY_ONCE, "remediation-ci.conservative");
            assertEquals(previous + 1, now);
            previous = now;
        }
    }

    @Test
    @DisplayName("the empty value has no entry, and an entry refuses a blank state or negative attempts")
    void emptyAndEntryValidation() {
        assertAll(
                () -> assertTrue(RetryCounters.empty().isEmpty()),
                () -> assertThrows(IllegalArgumentException.class, () -> new Entry(" ", 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Entry(MERGE_SYNC, -1)),
                () -> assertEquals(0, new Entry(MERGE_SYNC, 0).attempts(), "0 is admitted"));
    }
}
