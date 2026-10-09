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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The lock paths and the order of the lock keys (PM-IMPL-7).
 */
@DisplayName("Lock key")
class LockKeyTest {

    private static final Path BASE = Path.of("/pm-base");
    private static final String PROJECT = "sample-project";
    private static final Path PROJECT_STORE = BASE.resolve("projects").resolve(PROJECT);
    private static final Path PROJECT_LOCKS = PROJECT_STORE.resolve("locks");
    private static final Path MACHINE_LOCKS = BASE.resolve("locks");

    static Stream<Arguments> projectKeys() {
        return Stream.of(
                Arguments.of(LockKey.workspace(BASE, PROJECT), LockLevel.WORKSPACE, "workspace.lock"),
                Arguments.of(LockKey.plan(BASE, PROJECT, "plan-one"), LockLevel.PLAN, "plan-plan-one.lock"),
                Arguments.of(LockKey.epic(BASE, PROJECT, "epic-one"), LockLevel.EPIC, "epic-epic-one.lock"),
                Arguments.of(LockKey.epicsWorktree(BASE, PROJECT), LockLevel.EPICS_WORKTREE, "epics-worktree.lock"),
                Arguments.of(LockKey.lessonStore(BASE, PROJECT), LockLevel.LESSON_STORE, "lessons.lock"),
                Arguments.of(LockKey.localConfiguration(BASE, PROJECT), LockLevel.LOCAL_CONFIGURATION,
                        "local-config.lock"),
                Arguments.of(LockKey.derivedFacts(BASE, PROJECT), LockLevel.DERIVED_FACTS, "derived-facts.lock"),
                Arguments.of(LockKey.buildTimings(BASE, PROJECT), LockLevel.BUILD_TIMINGS, "build-timings.lock"),
                Arguments.of(LockKey.queue(BASE, PROJECT), LockLevel.QUEUE, "queue.lock"));
    }

    /** Each machine store lock with the store it guards, relative to the base directory. */
    static Stream<Arguments> machineStoreKeys() {
        return Stream.of(
                Arguments.of(LockKey.buildSlots(BASE), "locks/build-slots.lock", "state/build-slots.json"),
                Arguments.of(LockKey.credentials(BASE), "locks/credentials.lock", "credentials"),
                Arguments.of(LockKey.enrolment(BASE, PROJECT), "locks/enrolments/sample-project.lock",
                        "enrolments/sample-project.json"),
                Arguments.of(LockKey.harnesses(BASE), "locks/harnesses.lock", "harnesses.json"),
                Arguments.of(LockKey.installRecords(BASE), "locks/install-records.lock",
                        "state/install-records.json"),
                Arguments.of(LockKey.machineConfiguration(BASE), "locks/machine-config.lock", "machine-config.json"),
                Arguments.of(LockKey.mergeQueue(BASE), "locks/merge.lock", "state/merge-queue.json"),
                Arguments.of(LockKey.modelSlots(BASE), "locks/model-slots.lock", "state/model-slots.json"),
                Arguments.of(LockKey.reviewBotWindows(BASE), "locks/review-bot.lock",
                        "state/review-bot-windows.json"),
                Arguments.of(LockKey.webDevices(BASE), "locks/web-devices.lock", "state/web-devices.json"));
    }

    private static List<LockKey> sortedAfterShuffle(List<LockKey> keys) {
        var shuffled = new ArrayList<>(keys);
        Collections.reverse(shuffled);
        Collections.sort(shuffled);
        return shuffled;
    }

    @Nested
    @DisplayName("lock paths")
    class LockPaths {

        @ParameterizedTest(name = "{2}")
        @MethodSource("de.planmarshall.core.store.LockKeyTest#projectKeys")
        @DisplayName("a lock of a project lies in the project's lock directory, outside every directory it protects")
        void projectLock(LockKey key, LockLevel level, String fileName) {
            assertAll(
                    () -> assertEquals(level, key.level()),
                    () -> assertEquals(PROJECT_LOCKS.resolve(fileName), key.lockFile()),
                    () -> assertFalse(key.lockFile().startsWith(PROJECT_STORE.resolve("plans")),
                            "below the plan directories"),
                    () -> assertFalse(key.lockFile().startsWith(PROJECT_STORE.resolve("epics")),
                            "below the epic directories"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("de.planmarshall.core.store.LockKeyTest#machineStoreKeys")
        @DisplayName("a machine store lock is a sibling lock file and never the store it guards")
        void machineStoreLock(LockKey key, String lockPath, String guarded) {
            var store = BASE.resolve(guarded);

            assertAll(
                    () -> assertEquals(LockLevel.MACHINE_STORE, key.level()),
                    () -> assertEquals(BASE.resolve(lockPath), key.lockFile()),
                    () -> assertTrue(key.lockFile().startsWith(MACHINE_LOCKS), "below locks/"),
                    () -> assertNotEquals(store, key.lockFile()),
                    () -> assertFalse(key.lockFile().startsWith(store), "below the guarded store"));
        }

        @Test
        @DisplayName("the leaf keys are taken on the append-only file itself")
        void leafKeys() {
            var audit = LockKey.auditLog(BASE);
            var mailbox = LockKey.mailbox(BASE, PROJECT, "plan-one");

            assertAll(
                    () -> assertEquals(LockLevel.LEAF, audit.level()),
                    () -> assertEquals(BASE.resolve("logs/audit.jsonl"), audit.lockFile()),
                    () -> assertEquals(LockLevel.LEAF, mailbox.level()),
                    () -> assertEquals(PROJECT_STORE.resolve("plans/plan-one/mcp/mailbox.jsonl"),
                            mailbox.lockFile()));
        }

        @Test
        @DisplayName("a base directory is made absolute and normalized, so one lock has one key")
        void canonicalBase() {
            var direct = LockKey.queue(BASE, PROJECT);

            var indirect = LockKey.queue(BASE.resolve("state").resolve(".."), PROJECT);

            assertEquals(direct, indirect);
        }
    }

    @Nested
    @DisplayName("order")
    class Order {

        @Test
        @DisplayName("the ten machine store locks sort alphabetically by lock path")
        void machineStoreLocks() {
            var keys = machineStoreKeys().map(arguments -> (LockKey) arguments.get()[0]).toList();
            var alphabetical = keys.stream().map(key -> BASE.relativize(key.lockFile()).toString()).sorted().toList();

            var sorted = sortedAfterShuffle(keys);

            assertAll(
                    () -> assertEquals(10, sorted.size()),
                    () -> assertEquals(alphabetical,
                            sorted.stream().map(key -> BASE.relativize(key.lockFile()).toString()).toList()));
        }

        @Test
        @DisplayName("enrolment locks sort by project id, also where the lock paths sort the other way")
        void enrolmentLocks() {
            var shorter = LockKey.enrolment(BASE, "abc");
            var longer = LockKey.enrolment(BASE, "abc-d");
            var last = LockKey.enrolment(BASE, "abd");

            var sorted = sortedAfterShuffle(List.of(shorter, longer, last));

            assertAll(
                    () -> assertEquals(List.of(shorter, longer, last), sorted),
                    () -> assertTrue(shorter.lockFile().toString().compareTo(longer.lockFile().toString()) > 0,
                            "the fixture must be a pair whose paths sort against the ids"));
        }

        @Test
        @DisplayName("plan locks sort lexicographically by plan id")
        void planLocks() {
            var first = LockKey.plan(BASE, PROJECT, "alpha");
            var second = LockKey.plan(BASE, PROJECT, "alpha-2");
            var third = LockKey.plan(BASE, PROJECT, "beta");

            assertEquals(List.of(first, second, third), sortedAfterShuffle(List.of(first, second, third)));
        }

        @Test
        @DisplayName("epic locks sort lexicographically by epic id")
        void epicLocks() {
            var first = LockKey.epic(BASE, PROJECT, "alpha");
            var second = LockKey.epic(BASE, PROJECT, "alpha-2");
            var third = LockKey.epic(BASE, PROJECT, "beta");

            assertEquals(List.of(first, second, third), sortedAfterShuffle(List.of(first, second, third)));
        }

        @Test
        @DisplayName("the level decides before the id")
        void levelFirst() {
            var plan = LockKey.plan(BASE, PROJECT, "zzz");
            var epic = LockKey.epic(BASE, PROJECT, "aaa");

            assertTrue(plan.compareTo(epic) < 0);
        }

        @Test
        @DisplayName("the same lock of two projects is ordered, and only an equal key compares as equal")
        void acrossProjects() {
            var first = LockKey.plan(BASE, "project-a", "shared-id");
            var second = LockKey.plan(BASE, "project-b", "shared-id");

            assertAll(
                    () -> assertTrue(first.compareTo(second) < 0),
                    () -> assertTrue(second.compareTo(first) > 0),
                    () -> assertEquals(0, first.compareTo(LockKey.plan(BASE, "project-a", "shared-id"))));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = { "", "ab", "../other", "a/b", "Upper-case", "-leading", "trailing-", "with space" })
        @DisplayName("an identifier outside the grammar never reaches a lock path")
        void invalidIdentifier(String identifier) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.workspace(BASE, identifier)),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.plan(BASE, PROJECT, identifier)),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.epic(BASE, PROJECT, identifier)),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.enrolment(BASE, identifier)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LockKey.mailbox(BASE, PROJECT, identifier)));
        }

        @Test
        @DisplayName("a missing base directory or identifier is refused")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> LockKey.buildSlots(null)),
                    () -> assertThrows(NullPointerException.class, () -> LockKey.workspace(BASE, null)),
                    () -> assertThrows(NullPointerException.class, () -> LockKey.plan(BASE, PROJECT, null)));
        }

        @Test
        @DisplayName("a key is refused for a relative or unnormalized lock file and for a missing component")
        void invalidComponents() {
            var lockFile = MACHINE_LOCKS.resolve("merge.lock");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, Path.of("locks/merge.lock"), "merge")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, BASE.resolve("state/../locks/merge.lock"),
                                    "merge")),
                    () -> assertThrows(NullPointerException.class, () -> new LockKey(null, lockFile, "merge")),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, null, "merge")),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, lockFile, null)));
        }
    }
}
