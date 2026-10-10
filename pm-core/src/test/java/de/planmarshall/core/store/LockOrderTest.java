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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import de.planmarshall.core.store.LockOrder.Permitted;
import de.planmarshall.core.store.LockOrder.Reentry;
import de.planmarshall.core.store.LockOrder.Violation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The rules of the total lock order (PM-IMPL-7): one case per rule and one per boundary between two adjacent
 * levels.
 */
@DisplayName("Lock order")
class LockOrderTest {

    private static final Path BASE = Path.of("/pm-base");
    private static final String PROJECT = "sample-project";

    /** One key of every level, in the order of the levels. */
    private static final List<LockKey> ONE_PER_LEVEL = List.of(
            LockKey.workspace(BASE, PROJECT),
            LockKey.plan(BASE, PROJECT, "plan-one"),
            LockKey.epic(BASE, PROJECT, "epic-one"),
            LockKey.epicsWorktree(BASE, PROJECT),
            LockKey.lessonStore(BASE, PROJECT),
            LockKey.localConfiguration(BASE, PROJECT),
            LockKey.derivedFacts(BASE, PROJECT),
            LockKey.buildTimings(BASE, PROJECT),
            LockKey.queue(BASE, PROJECT),
            LockKey.mergeQueue(BASE),
            LockKey.auditLog(BASE));

    static Stream<Arguments> adjacentLevels() {
        return IntStream.range(0, ONE_PER_LEVEL.size() - 1)
                .mapToObj(index -> Arguments.of(ONE_PER_LEVEL.get(index), ONE_PER_LEVEL.get(index + 1)));
    }

    private static void assertViolation(List<LockKey> held, LockKey requested, LockOrder.Decision decision) {
        var violation = assertInstanceOf(Violation.class, decision);
        assertAll(
                () -> assertEquals(held, violation.held()),
                () -> assertEquals(requested, violation.requested()));
    }

    @Nested
    @DisplayName("between levels")
    class BetweenLevels {

        @Test
        @DisplayName("the fixture holds one key of each of the eleven levels, in their order")
        void fixtureCoversEveryLevel() {
            assertEquals(Arrays.asList(LockLevel.values()), ONE_PER_LEVEL.stream().map(LockKey::level).toList());
        }

        @ParameterizedTest(name = "{0} then {1}")
        @MethodSource("de.planmarshall.core.store.LockOrderTest#adjacentLevels")
        @DisplayName("a key of the next level is permitted")
        void ascending(LockKey lower, LockKey higher) {
            var decision = LockOrder.check(Set.of(lower), higher);

            assertInstanceOf(Permitted.class, decision);
        }

        @ParameterizedTest(name = "{1} then {0}")
        @MethodSource("de.planmarshall.core.store.LockOrderTest#adjacentLevels")
        @DisplayName("a key of the level before is a violation that names the held and the requested key")
        void descending(LockKey lower, LockKey higher) {
            var decision = LockOrder.check(Set.of(higher), lower);

            assertViolation(List.of(higher), lower, decision);
        }

        @Test
        @DisplayName("the first key of a transaction is permitted at every level")
        void nothingHeld() {
            assertAll(ONE_PER_LEVEL.stream()
                    .<Executable>map(key -> () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(), key))));
        }

        @Test
        @DisplayName("a key between two held keys is a violation, and the held keys are named in the lock order")
        void betweenHeldKeys() {
            var workspace = LockKey.workspace(BASE, PROJECT);
            var queue = LockKey.queue(BASE, PROJECT);
            var plan = LockKey.plan(BASE, PROJECT, "plan-one");

            var decision = LockOrder.check(List.of(queue, workspace), plan);

            assertViolation(List.of(workspace, queue), plan, decision);
        }

        /**
         * A refresh of the derived facts inside a scope transaction deadlocks against a transaction that holds the
         * locks the other way round unless the derived facts lock is taken after every scope lock and never before
         * one (PM-IMPL-7).
         */
        @Test
        @DisplayName("the derived facts lock is taken after every scope lock and never before one")
        void derivedFactsAfterScopeLocks() {
            var derivedFacts = LockKey.derivedFacts(BASE, PROJECT);
            var scopeLocks = List.of(LockKey.workspace(BASE, PROJECT), LockKey.plan(BASE, PROJECT, "plan-one"),
                    LockKey.epic(BASE, PROJECT, "epic-one"));

            assertAll(
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(scopeLocks, derivedFacts)),
                    () -> assertAll(scopeLocks.stream().<Executable>map(scopeLock -> () -> assertViolation(
                            List.of(derivedFacts), scopeLock, LockOrder.check(Set.of(derivedFacts), scopeLock)))));
        }
    }

    @Nested
    @DisplayName("within a level")
    class WithinLevel {

        @Test
        @DisplayName("plan locks are acquired in the order of their plan ids")
        void planLocks() {
            var first = LockKey.plan(BASE, PROJECT, "alpha");
            var second = LockKey.plan(BASE, PROJECT, "beta");

            assertAll(
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second)),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }

        @Test
        @DisplayName("epic locks are acquired in the order of their epic ids")
        void epicLocks() {
            var first = LockKey.epic(BASE, PROJECT, "alpha");
            var second = LockKey.epic(BASE, PROJECT, "beta");

            assertAll(
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second)),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }

        @Test
        @DisplayName("machine store locks are acquired alphabetically by lock path")
        void machineStoreLocks() {
            var buildSlots = LockKey.buildSlots(BASE);
            var modelSlots = LockKey.modelSlots(BASE);

            assertAll(
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(buildSlots), modelSlots)),
                    () -> assertViolation(List.of(modelSlots), buildSlots,
                            LockOrder.check(Set.of(modelSlots), buildSlots)));
        }

        @Test
        @DisplayName("enrolment locks are acquired in the order of their project ids")
        void enrolmentLocks() {
            var first = LockKey.enrolment(BASE, "abc");
            var second = LockKey.enrolment(BASE, "abc-d");

            assertAll(
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second)),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }
    }

    @Nested
    @DisplayName("re-entry")
    class ReentryOfHeldKey {

        @Test
        @DisplayName("a held key may be requested again")
        void heldKey() {
            var plan = LockKey.plan(BASE, PROJECT, "plan-one");

            var decision = LockOrder.check(Set.of(plan), LockKey.plan(BASE, PROJECT, "plan-one"));

            assertInstanceOf(Reentry.class, decision);
        }

        @Test
        @DisplayName("a held key may be requested again while later keys and a leaf key are held")
        void heldKeyBelowLaterKeys() {
            var workspace = LockKey.workspace(BASE, PROJECT);
            var held = List.of(workspace, LockKey.queue(BASE, PROJECT), LockKey.auditLog(BASE));

            var decision = LockOrder.check(held, workspace);

            assertInstanceOf(Reentry.class, decision);
        }
    }

    @Nested
    @DisplayName("leaf locks")
    class LeafLocks {

        /**
         * A mailbox appended under the plan lock would invert the order when an epic transaction writes it; the
         * mailbox therefore has a leaf lock of its own, which an epic transaction takes under its epic lock
         * (PM-IMPL-7).
         */
        @Test
        @DisplayName("an epic transaction takes the mailbox leaf lock of a plan under its epic lock")
        void mailboxUnderEpicLock() {
            var epic = LockKey.epic(BASE, PROJECT, "epic-one");

            var decision = LockOrder.check(Set.of(epic), LockKey.mailbox(BASE, PROJECT, "plan-one"));

            assertInstanceOf(Permitted.class, decision);
        }

        @Test
        @DisplayName("nothing is acquired after a leaf lock, neither a key of an earlier level nor another leaf key")
        void nothingAfterLeaf() {
            var audit = LockKey.auditLog(BASE);
            var mailbox = LockKey.mailbox(BASE, PROJECT, "plan-one");
            var others = Stream.concat(ONE_PER_LEVEL.stream().filter(key -> !key.equals(audit)), Stream.of(mailbox))
                    .toList();

            assertAll(
                    () -> assertEquals(11, others.size()),
                    () -> assertAll(others.stream().<Executable>map(requested -> () -> assertViolation(
                            List.of(audit), requested, LockOrder.check(Set.of(audit), requested)))),
                    () -> assertViolation(List.of(mailbox), audit, LockOrder.check(Set.of(mailbox), audit)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a missing argument is refused")
        void missingArguments() {
            var plan = LockKey.plan(BASE, PROJECT, "plan-one");
            var heldKeys = Set.of(plan);
            var heldInOrder = List.of(plan);

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> LockOrder.check(null, plan)),
                    () -> assertThrows(NullPointerException.class, () -> LockOrder.check(heldKeys, null)),
                    () -> assertThrows(NullPointerException.class, () -> new Violation(null, plan)),
                    () -> assertThrows(NullPointerException.class, () -> new Violation(heldInOrder, null)));
        }
    }
}
