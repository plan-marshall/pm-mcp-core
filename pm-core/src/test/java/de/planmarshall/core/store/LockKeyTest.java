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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
@EnableGeneratorController
@DisplayName("Lock key")
class LockKeyTest {

    private static final Path BASE = Path.of("/pm-base");
    private static final Path MACHINE_LOCKS = BASE.resolve("locks");

    private String project;
    private String plan;
    private String epic;
    private String entry;
    private Path projectStore;
    private Path projectLocks;

    @BeforeEach
    void createIdentifiers() {
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
        epic = "epic-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
        entry = "ent-" + "%08x".formatted(Generators.integers(0, Integer.MAX_VALUE - 1).next());
        projectStore = BASE.resolve("projects").resolve(project);
        projectLocks = projectStore.resolve("locks");
    }

    static Stream<Arguments> projectKeys() {
        return Stream.of(
                Arguments.of(LockLevel.WORKSPACE),
                Arguments.of(LockLevel.PLAN),
                Arguments.of(LockLevel.EPIC),
                Arguments.of(LockLevel.EPICS_WORKTREE),
                Arguments.of(LockLevel.LESSON_STORE),
                Arguments.of(LockLevel.LOCAL_CONFIGURATION),
                Arguments.of(LockLevel.DERIVED_FACTS),
                Arguments.of(LockLevel.BUILD_TIMINGS),
                Arguments.of(LockLevel.QUEUE));
    }

    /** Each machine store lock with the store it guards, relative to the base directory. */
    static Stream<Arguments> machineStoreKeys() {
        return Stream.of(
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.buildSlots(BASE),
                        "locks/build-slots.lock", "state/build-slots.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.consents(BASE),
                        "locks/consents.lock", "consents.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.credentials(BASE),
                        "locks/credentials.lock", "credentials"),
                Arguments.of((Function<String, LockKey>) enrolment -> LockKey.enrolment(BASE, enrolment),
                        "locks/enrolments/%s.lock", "enrolments/%s.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.harnesses(BASE),
                        "locks/harnesses.lock", "harnesses.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.installRecords(BASE),
                        "locks/install-records.lock", "state/install-records.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.machineConfiguration(BASE),
                        "locks/machine-config.lock", "machine-config.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.mergeQueue(BASE),
                        "locks/merge.lock", "state/merge-queue.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.modelCatalogue(BASE),
                        "locks/model-catalogue.lock", "model-catalogue.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.modelSlots(BASE),
                        "locks/model-slots.lock", "state/model-slots.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.reviewBotWindows(BASE),
                        "locks/review-bot.lock", "state/review-bot-windows.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.roleAssignments(BASE),
                        "locks/role-assignments.lock", "role-assignments.json"),
                Arguments.of((Function<String, LockKey>) ignored -> LockKey.webDevices(BASE),
                        "locks/web-devices.lock", "state/web-devices.json"));
    }

    private static List<LockKey> sortedAfterShuffle(List<LockKey> keys) {
        var shuffled = new ArrayList<>(keys);
        Collections.reverse(shuffled);
        shuffled.sort(Comparator.naturalOrder());
        return shuffled;
    }

    private LockKey projectKey(LockLevel level) {
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
            default -> throw new IllegalArgumentException("not a project lock level: " + level);
        };
    }

    private String projectLockName(LockLevel level) {
        return switch (level) {
            case WORKSPACE -> "workspace.lock";
            case PLAN -> "plan-" + plan + ".lock";
            case EPIC -> "epic-" + epic + ".lock";
            case EPICS_WORKTREE -> "epics-worktree.lock";
            case LESSON_STORE -> "lessons.lock";
            case LOCAL_CONFIGURATION -> "local-config.lock";
            case DERIVED_FACTS -> "derived-facts.lock";
            case BUILD_TIMINGS -> "build-timings.lock";
            case QUEUE -> "queue.lock";
            default -> throw new IllegalArgumentException("not a project lock level: " + level);
        };
    }

    @Nested
    @DisplayName("lock paths")
    class LockPaths {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.LockKeyTest#projectKeys")
        @DisplayName("a lock of a project lies in the project's lock directory, outside every directory it protects")
        void projectLock(LockLevel level) {
            var key = projectKey(level);

            assertAll("a project lock lies in the project's lock directory",
                    () -> assertEquals(level, key.level(), "the key carries the lock's level"),
                    () -> assertEquals(projectLocks.resolve(projectLockName(level)), key.lockFile(),
                            "the lock file is the expected lock path"),
                    () -> assertFalse(key.lockFile().startsWith(projectStore.resolve("plans")),
                            "the lock lies outside the plan directories"),
                    () -> assertFalse(key.lockFile().startsWith(projectStore.resolve("epics")),
                            "the lock lies outside the epic directories"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("de.planmarshall.core.store.LockKeyTest#machineStoreKeys")
        @DisplayName("a machine store lock is a sibling lock file and never the store it guards")
        void machineStoreLock(Function<String, LockKey> keyFactory, String lockPath, String guarded) {
            var key = keyFactory.apply(project);
            var store = BASE.resolve(guarded.formatted(project));

            assertAll("a machine store lock is a sibling lock file",
                    () -> assertEquals(LockLevel.MACHINE_STORE, key.level(),
                            "the key carries the machine store level"),
                    () -> assertEquals(BASE.resolve(lockPath.formatted(project)), key.lockFile(),
                            "the lock file is the expected machine path"),
                    () -> assertTrue(key.lockFile().startsWith(MACHINE_LOCKS),
                            "the lock lies below the locks directory"),
                    () -> assertNotEquals(store, key.lockFile(), "the lock is never the store it guards"),
                    () -> assertFalse(key.lockFile().startsWith(store),
                            "the lock lies outside the guarded store"));
        }

        @Test
        @DisplayName("the leaf keys are taken on the append-only file itself")
        void leafKeys() {
            var audit = LockKey.auditLog(BASE);
            var mailbox = LockKey.mailbox(BASE, project, plan);

            assertAll("the leaf keys name the append-only file itself",
                    () -> assertEquals(LockLevel.LEAF, audit.level(), "the audit key carries the leaf level"),
                    () -> assertEquals(BASE.resolve("logs/audit.jsonl"), audit.lockFile(),
                            "the audit key names its log file"),
                    () -> assertEquals(LockLevel.LEAF, mailbox.level(), "the mailbox key carries the leaf level"),
                    () -> assertEquals(projectStore.resolve("plans/" + plan + "/mcp/mailbox.jsonl"),
                            mailbox.lockFile(), "the mailbox key names the plan's file"));
        }

        @Test
        @DisplayName("a base directory is made absolute and normalized, so one lock has one key")
        void canonicalBase() {
            var direct = LockKey.queue(BASE, project);

            var indirect = LockKey.queue(BASE.resolve("state").resolve(".."), project);

            assertEquals(direct, indirect, "a detour base resolves to the same key");
        }
    }

    @Nested
    @DisplayName("order")
    class Order {

        @Test
        @DisplayName("the thirteen machine store locks sort alphabetically by lock path")
        void machineStoreLocks() {
            var keys = machineStoreKeys()
                    .map(arguments -> ((Function<String, LockKey>) arguments.get()[0]).apply(project)).toList();
            var alphabetical = keys.stream().map(key -> BASE.relativize(key.lockFile()).toString()).sorted().toList();

            var sorted = sortedAfterShuffle(keys);

            assertAll("the thirteen machine store locks sort by path",
                    () -> assertEquals(13, sorted.size(), "the fixture holds all thirteen locks"),
                    () -> assertEquals(alphabetical,
                            sorted.stream().map(key -> BASE.relativize(key.lockFile()).toString()).toList(),
                            "the keys sort alphabetically by lock path"));
        }

        @Test
        @DisplayName("enrolment locks sort by project id, also where the lock paths sort the other way")
        void enrolmentLocks() {
            var shorter = LockKey.enrolment(BASE, "abc");
            var longer = LockKey.enrolment(BASE, "abc-d");
            var last = LockKey.enrolment(BASE, "abd");

            var sorted = sortedAfterShuffle(List.of(shorter, longer, last));

            assertAll("enrolment locks sort by project id",
                    () -> assertEquals(List.of(shorter, longer, last), sorted,
                            "the enrolment keys sort by project id"),
                    () -> assertTrue(shorter.lockFile().toString().compareTo(longer.lockFile().toString()) > 0,
                            "the fixture paths sort against the ids"));
        }

        @Test
        @DisplayName("plan locks sort lexicographically by plan id")
        void planLocks() {
            var first = LockKey.plan(BASE, project, "alpha");
            var second = LockKey.plan(BASE, project, "alpha-2");
            var third = LockKey.plan(BASE, project, "beta");

            assertEquals(List.of(first, second, third), sortedAfterShuffle(List.of(first, second, third)),
                    "plan keys sort lexicographically by id");
        }

        @Test
        @DisplayName("epic locks sort lexicographically by epic id")
        void epicLocks() {
            var first = LockKey.epic(BASE, project, "alpha");
            var second = LockKey.epic(BASE, project, "alpha-2");
            var third = LockKey.epic(BASE, project, "beta");

            assertEquals(List.of(first, second, third), sortedAfterShuffle(List.of(first, second, third)),
                    "epic keys sort lexicographically by id");
        }

        @Test
        @DisplayName("the level decides before the id")
        void levelFirst() {
            var planKey = LockKey.plan(BASE, project, "zzz");
            var epicKey = LockKey.epic(BASE, project, "aaa");

            assertTrue(planKey.compareTo(epicKey) < 0, "the level decides before the id");
        }

        @Test
        @DisplayName("the same lock of two projects is ordered, and only an equal key compares as equal")
        void acrossProjects() {
            var first = LockKey.plan(BASE, "project-a", "shared-id");
            var second = LockKey.plan(BASE, "project-b", "shared-id");

            assertAll("the same lock of two projects is ordered",
                    () -> assertTrue(first.compareTo(second) < 0, "the first project sorts before the second"),
                    () -> assertTrue(second.compareTo(first) > 0, "the second sorts after the first"),
                    () -> assertEquals(0, first.compareTo(LockKey.plan(BASE, "project-a", "shared-id")),
                            "only an equal key compares as equal"));
        }
    }

    @Nested
    @DisplayName("machine scope and onboarding entries")
    class MachineScopeAndEntries {

        @Test
        @DisplayName("the machine queue lock lies in machine/locks/ at the level of the queue locks")
        void machineQueue() {
            var key = LockKey.machineQueue(BASE);

            assertAll("the machine queue lock lies in machine/locks",
                    () -> assertEquals(LockLevel.QUEUE, key.level(), "the machine queue carries the queue level"),
                    () -> assertEquals(BASE.resolve("machine/locks/queue.lock"), key.lockFile(),
                            "the lock lies in machine/locks/"),
                    () -> assertFalse(key.lockFile().startsWith(BASE.resolve("machine/jobs")),
                            "the lock lies outside the jobs it guards"));
        }

        @Test
        @DisplayName("the lock of an entry lies in onboardings/locks/ at the level of the workspace scope")
        void onboardingEntry() {
            var key = LockKey.onboardingEntry(BASE, entry);

            assertAll("the entry lock lies in onboardings/locks",
                    () -> assertEquals(LockLevel.WORKSPACE, key.level(),
                            "the entry lock carries the workspace level"),
                    () -> assertEquals(BASE.resolve("onboardings/locks/" + entry + ".lock"), key.lockFile(),
                            "the entry lock names its entry file"),
                    () -> assertEquals(entry, key.id(), "the key's id is the entry id"),
                    () -> assertFalse(key.lockFile().startsWith(BASE.resolve("onboardings/" + entry)),
                            "the lock lies outside the entry it guards"));
        }

        @Test
        @DisplayName("an entry lock is taken before a plan lock, the machine queue lock after the build timings "
                + "and before every machine store lock")
        void placeInTheOrder() {
            var entryKey = LockKey.onboardingEntry(BASE, entry);
            var planKey = LockKey.plan(BASE, project, plan);
            var buildTimings = LockKey.buildTimings(BASE, project);
            var machineQueue = LockKey.machineQueue(BASE);
            var buildSlots = LockKey.buildSlots(BASE);

            var sorted = sortedAfterShuffle(List.of(buildSlots, machineQueue, planKey, buildTimings, entryKey));

            assertEquals(List.of(entryKey, planKey, buildTimings, machineQueue, buildSlots), sorted,
                    "the keys sort in the total lock order");
        }

        @Test
        @DisplayName("the machine queue lock and a project's queue lock are distinct keys of one level")
        void distinctFromProjectQueue() {
            var machine = LockKey.machineQueue(BASE);
            var queue = LockKey.queue(BASE, project);

            assertAll("the machine and project queue are distinct",
                    () -> assertNotEquals(machine, queue, "the two queue locks are not equal"),
                    () -> assertEquals(machine.level(), queue.level(), "both queue locks share one level"),
                    () -> assertNotEquals(0, machine.compareTo(queue), "the two keys compare as unequal"));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "ab", "../other", "a/b", "Upper-case", "-leading", "trailing-"})
        @DisplayName("an entry id outside the grammar never reaches a lock path")
        void invalidEntryId(String entryId) {
            assertThrows(IllegalArgumentException.class, () -> LockKey.onboardingEntry(BASE, entryId),
                    "an invalid entry id never reaches a path");
        }

        @Test
        @DisplayName("a missing base directory or entry id is refused")
        void missingArguments() {
            assertAll("a missing base or entry id is refused",
                    () -> assertThrows(NullPointerException.class, () -> LockKey.machineQueue(null),
                            "a missing base directory is refused"),
                    () -> assertThrows(NullPointerException.class, () -> LockKey.onboardingEntry(BASE, null),
                            "a missing entry id is refused"));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "ab", "../other", "a/b", "Upper-case", "-leading", "trailing-", "with space"})
        @DisplayName("an identifier outside the grammar never reaches a lock path")
        void invalidIdentifier(String identifier) {
            assertAll("an identifier outside the grammar is refused",
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.workspace(BASE, identifier),
                            "a workspace with an invalid id is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.plan(BASE, project, identifier),
                            "a plan with an invalid id is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.epic(BASE, project, identifier),
                            "an epic with an invalid id is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> LockKey.enrolment(BASE, identifier),
                            "an enrolment with an invalid id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LockKey.mailbox(BASE, project, identifier),
                            "a mailbox with an invalid id is refused"));
        }

        @Test
        @DisplayName("a missing base directory or identifier is refused")
        void missingArguments() {
            assertAll("a missing base or identifier is refused",
                    () -> assertThrows(NullPointerException.class, () -> LockKey.buildSlots(null),
                            "a missing base directory is refused"),
                    () -> assertThrows(NullPointerException.class, () -> LockKey.workspace(BASE, null),
                            "a missing project id is refused"),
                    () -> assertThrows(NullPointerException.class, () -> LockKey.plan(BASE, project, null),
                            "a missing plan id is refused"));
        }

        @Test
        @DisplayName("a key is refused for a relative or unnormalized lock file and for a missing component")
        void invalidComponents() {
            var lockFile = MACHINE_LOCKS.resolve("merge.lock");
            var relative = Path.of("locks/merge.lock");
            var unnormalized = BASE.resolve("state/../locks/merge.lock");

            assertAll("an invalid lock key component is refused",
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, relative, "merge"),
                            "a relative lock file is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, unnormalized, "merge"),
                            "an unnormalized lock file is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new LockKey(null, lockFile, "merge"),
                            "a missing lock level is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, null, "merge"),
                            "a missing lock file is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LockKey(LockLevel.MACHINE_STORE, lockFile, null),
                            "a missing key id is refused"));
        }
    }
}
