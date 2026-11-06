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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.RetryCounters.Entry;
import de.planmarshall.core.store.RetryCounters.RetryRule;

/**
 * The retry counts of the server-rule retries (PM-WF-7: a budget is never reset). Only an attempt raises a count and
 * only the end of the retry or the pop of the counting frame drops it; no operation lowers a count otherwise, so a
 * restart that reads the store again cannot hand a plan more retries than its budget. The rule keys are the wire
 * contract of {@code retry_counters}; the state names the counters are bound to are generated, the boundary values of
 * the attempts (0, 1, -1) are kept.
 */
@EnableGeneratorController
@DisplayName("Retry counters")
class RetryCountersTest {

    @Test
    @DisplayName("the closed rule set is the key list of retry_counters in the specification (wire contract)")
    void closedRuleSet() {
        assertEquals(List.of("sync_failed", "merge_barrier_indeterminate", "merge_refused_re_enter",
                        "merge_queue_re_enter", "push_failed", "open_pr", "retry_once", "condition_budget"),
                Arrays.stream(RetryRule.values()).map(RetryRule::wireName).toList(),
                "the rule keys should be the specified key list");
    }

    @Test
    @DisplayName("a key is resolved to its rule; an unknown key resolves to none (wire keys are the contract)")
    void ofWireName() {
        var rule = Generators.enumValues(RetryRule.class).next();

        assertAll(
                () -> assertEquals(Optional.of(rule), RetryRule.ofWireName(rule.wireName()),
                        "a wire key should resolve to its rule"),
                () -> assertEquals(Optional.empty(), RetryRule.ofWireName("re_enter"),
                        "an unknown key should resolve to no rule"));
    }

    @Test
    @DisplayName("attempt counts per rule and state")
    void attemptCountsPerRuleAndState() {
        var sync = state();
        var barrier = sync + "x";
        var counters = RetryCounters.empty()
                .attempt(RetryRule.SYNC_FAILED, sync)
                .attempt(RetryRule.SYNC_FAILED, sync)
                .attempt(RetryRule.MERGE_BARRIER_INDETERMINATE, barrier);

        assertAll(
                () -> assertEquals(2, counters.attemptsOf(RetryRule.SYNC_FAILED, sync),
                        "two attempts should count two for the rule"),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.MERGE_BARRIER_INDETERMINATE, barrier),
                        "one attempt should count one for the rule"));
    }

    @Test
    @DisplayName("attemptsOf reads 0 for an absent rule and for an entry bound to another state")
    void attemptsOfReadsZero() {
        var sync = state();
        var barrier = sync + "x";
        var counters = RetryCounters.empty().attempt(RetryRule.SYNC_FAILED, sync);

        assertAll(
                () -> assertEquals(0, counters.attemptsOf(RetryRule.PUSH_FAILED, sync),
                        "an absent rule should read zero attempts"),
                () -> assertEquals(0, counters.attemptsOf(RetryRule.SYNC_FAILED, barrier),
                        "an entry of another state should read zero"),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, sync),
                        "the matched control should read one attempt"));
    }

    @Test
    @DisplayName("an attempt at another state replaces the entry and starts its budget at one")
    void attemptAtAnotherStateReplaces() {
        var blocked = state();
        var archive = blocked + "x";
        var counters = RetryCounters.empty()
                .attempt(RetryRule.CONDITION_BUDGET, blocked)
                .attempt(RetryRule.CONDITION_BUDGET, blocked)
                .attempt(RetryRule.CONDITION_BUDGET, archive);

        assertEquals(Map.of(RetryRule.CONDITION_BUDGET, new Entry(archive, 1)), counters.entries(),
                "the new state should replace the entry at one");
    }

    @Test
    @DisplayName("clearedFor drops only the entries of the state that is left")
    void clearedForDropsOnlyThatState() {
        var sync = state();
        var barrier = sync + "x";
        var gate = sync + "y";
        var counters = RetryCounters.empty()
                .attempt(RetryRule.SYNC_FAILED, sync)
                .attempt(RetryRule.MERGE_BARRIER_INDETERMINATE, barrier);

        var cleared = counters.clearedFor(sync);

        assertAll(
                () -> assertEquals(0, cleared.attemptsOf(RetryRule.SYNC_FAILED, sync),
                        "the entry of the left state should be dropped"),
                () -> assertEquals(1, cleared.attemptsOf(RetryRule.MERGE_BARRIER_INDETERMINATE, barrier),
                        "the entry of another state should be kept"),
                () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, sync),
                        "the original value should be unchanged"),
                () -> assertSame(counters, counters.clearedFor(gate),
                        "a state without entries should keep the value"));
    }

    @Test
    @DisplayName("no operation lowers a count: attempts only grow until the entry is dropped")
    void noOperationDecrements() {
        var conservative = state();
        var rerun = conservative + "x";
        var openPr = conservative + "y";
        var rounds = Generators.integers(3, 8).next();
        var counters = RetryCounters.empty();
        var previous = 0L;
        for (var round = 0; round < rounds; round++) {
            counters = counters.attempt(RetryRule.RETRY_ONCE, conservative)
                    .clearedFor(rerun)
                    .attempt(RetryRule.OPEN_PR, openPr);
            var now = counters.attemptsOf(RetryRule.RETRY_ONCE, conservative);
            assertEquals(previous + 1, now, "each round should raise the count by one");
            previous = now;
        }
    }

    @Test
    @DisplayName("the empty value has no entry, and an entry refuses a blank state or negative attempts (boundaries)")
    void emptyAndEntryValidation() {
        var sync = state();

        assertAll(
                () -> assertTrue(RetryCounters.empty().isEmpty(), "the empty value should hold no entry"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Entry(" ", 0),
                        "a blank state should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Entry(sync, -1),
                        "negative attempts should be refused"),
                () -> assertEquals(0, new Entry(sync, 0).attempts(), "zero attempts should be admitted"));
    }

    private static String state() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT) + "."
                + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }
}
