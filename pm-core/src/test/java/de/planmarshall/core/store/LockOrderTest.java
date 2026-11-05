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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.LockOrder.Permitted;
import de.planmarshall.core.store.LockOrder.Reentry;
import de.planmarshall.core.store.LockOrder.Violation;

/**
 * The rules of the total lock order (PM-IMPL-7): one case per rule and one per boundary between two adjacent
 * levels.
 */
@EnableGeneratorController
@DisplayName("Lock order")
class LockOrderTest {

    private static final Path BASE = Path.of("/pm-base");

    private String project;
    private String plan;
    private String epic;
    private List<LockKey> onePerLevel;

    @BeforeEach
    void createKeys() {
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
        epic = "epic-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
        onePerLevel = List.of(
                LockKey.workspace(BASE, project),
                LockKey.plan(BASE, project, plan),
                LockKey.epic(BASE, project, epic),
                LockKey.epicsWorktree(BASE, project),
                LockKey.lessonStore(BASE, project),
                LockKey.localConfiguration(BASE, project),
                LockKey.derivedFacts(BASE, project),
                LockKey.buildTimings(BASE, project),
                LockKey.queue(BASE, project),
                LockKey.mergeQueue(BASE),
                LockKey.auditLog(BASE));
    }

    static Stream<Arguments> adjacentLevels() {
        return IntStream.range(0, LockLevel.values().length - 1)
                .mapToObj(index -> Arguments.of(LockLevel.values()[index], LockLevel.values()[index + 1]));
    }

    private LockKey keyOf(LockLevel level) {
        return switch (level) {
            case WORKSPACE -> LockKey.workspace(BASE, project);
            case PLAN -> LockKey.plan(BASE, project, plan);
            case EPIC -> LockKey.epic(BASE, project, epic);
            case EPICS_WORKTREE -> LockKey.epicsWorktree(BASE, project);
            case LESSON_STORE -> LockKey.lessonStore(BASE, project);
            case LOCAL_CONFIGURATION -> LockKey.localConfiguration(BASE, project);
            case DERIVED_FACTS -> LockKey.derivedFacts(BASE, project);
            case BUILD_TIMINGS -> LockKey.buildTimings(BASE, project);
            case QUEUE -> LockKey.queue(BASE, project);
            case MACHINE_STORE -> LockKey.buildSlots(BASE);
            case LEAF -> LockKey.auditLog(BASE);
        };
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
            assertEquals(Arrays.asList(LockLevel.values()), onePerLevel.stream().map(LockKey::level).toList(),
                    "the fixture holds one key of every level");
        }

        @ParameterizedTest(name = "{0} then {1}")
        @MethodSource("de.planmarshall.core.store.LockOrderTest#adjacentLevels")
        @DisplayName("a key of the next level is permitted")
        void ascending(LockLevel lowerLevel, LockLevel higherLevel) {
            var lower = keyOf(lowerLevel);
            var higher = keyOf(higherLevel);

            var decision = LockOrder.check(Set.of(lower), higher);

            assertInstanceOf(Permitted.class, decision, "the key of the next level is permitted");
        }

        @ParameterizedTest(name = "{1} then {0}")
        @MethodSource("de.planmarshall.core.store.LockOrderTest#adjacentLevels")
        @DisplayName("a key of the level before is a violation that names the held and the requested key")
        void descending(LockLevel lowerLevel, LockLevel higherLevel) {
            var lower = keyOf(lowerLevel);
            var higher = keyOf(higherLevel);

            var decision = LockOrder.check(Set.of(higher), lower);

            assertViolation(List.of(higher), lower, decision);
        }

        @Test
        @DisplayName("the first key of a transaction is permitted at every level")
        void nothingHeld() {
            assertAll("the first key of a transaction is permitted",
                    onePerLevel.stream().<Executable>map(key -> () -> assertInstanceOf(Permitted.class,
                            LockOrder.check(Set.of(), key), "the first key is permitted at every level")));
        }

        @Test
        @DisplayName("a key between two held keys is a violation, and the held keys are named in the lock order")
        void betweenHeldKeys() {
            var workspace = LockKey.workspace(BASE, project);
            var queue = LockKey.queue(BASE, project);
            var planKey = LockKey.plan(BASE, project, plan);

            var decision = LockOrder.check(List.of(queue, workspace), planKey);

            assertViolation(List.of(workspace, queue), planKey, decision);
        }

        /**
         * A refresh of the derived facts inside a scope transaction deadlocks against a transaction that holds the
         * locks the other way round unless the derived facts lock is taken after every scope lock and never before
         * one (PM-IMPL-7).
         */
        @Test
        @DisplayName("the derived facts lock is taken after every scope lock and never before one")
        void derivedFactsAfterScopeLocks() {
            var derivedFacts = LockKey.derivedFacts(BASE, project);
            var scopeLocks = List.of(LockKey.workspace(BASE, project), LockKey.plan(BASE, project, plan),
                    LockKey.epic(BASE, project, epic));

            assertAll("the derived facts lock follows every scope lock",
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(scopeLocks, derivedFacts),
                            "the derived facts lock is permitted after the scopes"),
                    () -> assertAll("the derived facts lock is a violation before a scope",
                            scopeLocks.stream().<Executable>map(scopeLock -> () -> assertViolation(
                                    List.of(derivedFacts), scopeLock,
                                    LockOrder.check(Set.of(derivedFacts), scopeLock)))));
        }
    }

    @Nested
    @DisplayName("within a level")
    class WithinLevel {

        @Test
        @DisplayName("plan locks are acquired in the order of their plan ids")
        void planLocks() {
            var first = LockKey.plan(BASE, project, "alpha");
            var second = LockKey.plan(BASE, project, "beta");

            assertAll("plan locks follow the order of their ids",
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second),
                            "a later plan id is permitted"),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }

        @Test
        @DisplayName("epic locks are acquired in the order of their epic ids")
        void epicLocks() {
            var first = LockKey.epic(BASE, project, "alpha");
            var second = LockKey.epic(BASE, project, "beta");

            assertAll("epic locks follow the order of their ids",
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second),
                            "a later epic id is permitted"),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }

        @Test
        @DisplayName("machine store locks are acquired alphabetically by lock path")
        void machineStoreLocks() {
            var buildSlots = LockKey.buildSlots(BASE);
            var modelSlots = LockKey.modelSlots(BASE);

            assertAll("machine store locks follow their lock paths",
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(buildSlots), modelSlots),
                            "a later lock path is permitted"),
                    () -> assertViolation(List.of(modelSlots), buildSlots,
                            LockOrder.check(Set.of(modelSlots), buildSlots)));
        }

        @Test
        @DisplayName("enrolment locks are acquired in the order of their project ids")
        void enrolmentLocks() {
            var first = LockKey.enrolment(BASE, "abc");
            var second = LockKey.enrolment(BASE, "abc-d");

            assertAll("enrolment locks follow their project ids",
                    () -> assertInstanceOf(Permitted.class, LockOrder.check(Set.of(first), second),
                            "a later project id is permitted"),
                    () -> assertViolation(List.of(second), first, LockOrder.check(Set.of(second), first)));
        }
    }

    @Nested
    @DisplayName("re-entry")
    class ReentryOfHeldKey {

        @Test
        @DisplayName("a held key may be requested again")
        void heldKey() {
            var planKey = LockKey.plan(BASE, project, plan);

            var decision = LockOrder.check(Set.of(planKey), LockKey.plan(BASE, project, plan));

            assertInstanceOf(Reentry.class, decision, "a held key may be requested again");
        }

        @Test
        @DisplayName("a held key may be requested again while later keys and a leaf key are held")
        void heldKeyBelowLaterKeys() {
            var workspace = LockKey.workspace(BASE, project);
            var held = List.of(workspace, LockKey.queue(BASE, project), LockKey.auditLog(BASE));

            var decision = LockOrder.check(held, workspace);

            assertInstanceOf(Reentry.class, decision, "a held key stays re-entrant below later keys");
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
            var epicKey = LockKey.epic(BASE, project, epic);

            var decision = LockOrder.check(Set.of(epicKey), LockKey.mailbox(BASE, project, plan));

            assertInstanceOf(Permitted.class, decision, "the mailbox leaf is permitted under the epic");
        }

        @Test
        @DisplayName("nothing is acquired after a leaf lock, neither a key of an earlier level nor another leaf key")
        void nothingAfterLeaf() {
            var audit = LockKey.auditLog(BASE);
            var mailbox = LockKey.mailbox(BASE, project, plan);
            var others = Stream.concat(onePerLevel.stream().filter(key -> !key.equals(audit)), Stream.of(mailbox))
                    .toList();

            assertAll("nothing is acquired after a leaf lock",
                    () -> assertEquals(11, others.size(), "the fixture names eleven later requests"),
                    () -> assertAll("every later request is a violation",
                            others.stream().<Executable>map(requested -> () -> assertViolation(
                                    List.of(audit), requested,
                                    LockOrder.check(Set.of(audit), requested)))),
                    () -> assertViolation(List.of(mailbox), audit, LockOrder.check(Set.of(mailbox), audit)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a missing argument is refused")
        void missingArguments() {
            var planKey = LockKey.plan(BASE, project, plan);
            var heldKeys = Set.of(planKey);
            var heldInOrder = List.of(planKey);

            assertAll("a missing argument is refused",
                    () -> assertThrows(NullPointerException.class, () -> LockOrder.check(null, planKey),
                            "a missing held set is refused"),
                    () -> assertThrows(NullPointerException.class, () -> LockOrder.check(heldKeys, null),
                            "a missing requested key is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Violation(null, planKey),
                            "a violation without held keys is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Violation(heldInOrder, null),
                            "a violation without a requested key is refused"));
        }
    }
}
