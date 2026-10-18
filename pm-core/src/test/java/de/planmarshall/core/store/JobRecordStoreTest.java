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

import static de.planmarshall.core.store.JobRecordCodecTest.BUILD_ID;
import static de.planmarshall.core.store.JobRecordCodecTest.PLAN_SCOPE;
import static de.planmarshall.core.store.JobRecordCodecTest.ended;
import static de.planmarshall.core.store.JobRecordCodecTest.postSpawn;
import static de.planmarshall.core.store.JobRecordCodecTest.queued;
import static de.planmarshall.core.store.JobRecordCodecTest.refused;
import static de.planmarshall.core.store.JobRecordCodecTest.workerAhead;
import static de.planmarshall.core.store.JobRecordCodecTest.writeAhead;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The job records of a store (PM-EXEC-4, PM-ARCH-7, PM-IMPL-7): the store paths of a project, the machine scope and
 * an onboarding entry, the lock of the owning scope under which every write point runs, a fresh id drawn again on a
 * collision, the order of the write points, and the listing for the recovery.
 */
@DisplayName("Job record store")
class JobRecordStoreTest {

    private static final String PROJECT = "sample-project";
    private static final String ENTRY = "ent-0011";
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);
    private static final Scope ENTRY_SCOPE = new Scope(Scope.Type.ENTRY, ENTRY);

    @TempDir
    Path base;

    private JobRecordStore store;
    private RecordingLockManager locks;

    /** A source of digits that answers the given values in turn, as a collision of draws needs it. */
    private static final class ScriptedRandom implements RandomGenerator {

        private final Deque<Long> values;

        ScriptedRandom(Long... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public long nextLong() {
            return values.removeFirst();
        }
    }

    @BeforeEach
    void createStore() {
        store = JobRecordStore.ofProject(base, PROJECT);
        locks = new RecordingLockManager();
    }

    /** Runs one write point in a transaction of its own. */
    private void write(BiConsumer<LockTransaction, JobRecord> point, JobRecord job) {
        try (var transaction = locks.openTransaction()) {
            point.accept(transaction, job);
        }
    }

    static Stream<Arguments> ownedScopes() {
        var base = Path.of("/pm-base");
        return Stream.of(
                Arguments.of(PLAN_SCOPE, LockKey.plan(base, PROJECT, "plan-one")),
                Arguments.of(new Scope(Scope.Type.EPIC, "epic-one"), LockKey.epic(base, PROJECT, "epic-one")),
                Arguments.of(new Scope(Scope.Type.WORKSPACE, null), LockKey.workspace(base, PROJECT)),
                Arguments.of(new Scope(Scope.Type.PROJECT, null), LockKey.queue(base, PROJECT)));
    }

    @Nested
    @DisplayName("paths and locks")
    class PathsAndLocks {

        @Test
        @DisplayName("each store lies in the jobs/ of its home, the log beside the record")
        void storePaths() {
            var machine = JobRecordStore.ofMachine(base);
            var entry = JobRecordStore.ofEntry(base, ENTRY);

            assertAll(
                    () -> assertEquals(base.resolve("projects/sample-project/jobs"), store.directoryPath()),
                    () -> assertEquals(base.resolve("machine/jobs"), machine.directoryPath()),
                    () -> assertEquals(base.resolve("onboardings/ent-0011/jobs"), entry.directoryPath()),
                    () -> assertEquals(base.resolve("projects/sample-project/jobs/b-0123456789abcdef.json"),
                            store.recordPath(BUILD_ID)),
                    () -> assertEquals(base.resolve("projects/sample-project/jobs/b-0123456789abcdef.log"),
                            store.logPath(BUILD_ID)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.JobRecordStoreTest#ownedScopes")
        @DisplayName("a job of a project is written under the lock of its scope")
        void projectLocks(Scope scope, LockKey expected) {
            var projectStore = JobRecordStore.ofProject(Path.of("/pm-base"), PROJECT);

            assertEquals(expected, projectStore.lockKey(scope));
        }

        @Test
        @DisplayName("a job of the machine is written under the machine queue lock, one of an entry under its lock")
        void machineAndEntryLocks() {
            assertAll(
                    () -> assertEquals(LockKey.machineQueue(base), JobRecordStore.ofMachine(base).lockKey(MACHINE)),
                    () -> assertEquals(LockKey.onboardingEntry(base, ENTRY),
                            JobRecordStore.ofEntry(base, ENTRY).lockKey(ENTRY_SCOPE)));
        }

        @Test
        @DisplayName("a job of a scope of another store is refused")
        void foreignScopes() {
            var machine = JobRecordStore.ofMachine(base);
            var entry = JobRecordStore.ofEntry(base, ENTRY);
            var otherEntry = new Scope(Scope.Type.ENTRY, "ent-0022");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> store.lockKey(MACHINE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> store.lockKey(ENTRY_SCOPE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> machine.lockKey(PLAN_SCOPE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> entry.lockKey(otherEntry)),
                    () -> assertThrows(IllegalArgumentException.class, () -> JobRecordStore.ofEntry(base, "../x")));
        }
    }

    @Nested
    @DisplayName("write points")
    class WritePoints {

        @Test
        @DisplayName("a job moves through queued, write-ahead, post-spawn and fate, each under the plan lock")
        void fullLife() {
            write(store::writeQueued, queued(BUILD_ID, PLAN_SCOPE));
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));
            var running = store.read(BUILD_ID).orElseThrow();
            write(store::writePostSpawn, postSpawn(BUILD_ID, PLAN_SCOPE));
            var spawned = store.read(BUILD_ID).orElseThrow();
            var settled = ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7);
            write(store::writeFate, settled);

            var planLock = LockKey.plan(base, PROJECT, "plan-one");
            assertAll(
                    () -> assertFalse(running.isSpawnRecorded(), "the write-ahead record does not count as running"),
                    () -> assertTrue(spawned.isSpawnRecorded(), "the post-spawn record counts as running"),
                    () -> assertEquals(Optional.of(settled), store.read(BUILD_ID)),
                    () -> assertEquals(4, locks.transactions()),
                    () -> assertEquals(List.of(planLock, planLock, planLock, planLock), locks.acquisitions()),
                    () -> assertEquals(List.of(), locks.held()));
        }

        @Test
        @DisplayName("each scope's job is written under the lock of that scope")
        void ownerLocks() {
            var machine = JobRecordStore.ofMachine(base);
            var entry = JobRecordStore.ofEntry(base, ENTRY);
            var worker = workerAhead(new JobId("l-0000000000000001"), new Scope(Scope.Type.PROJECT, null), "{}");
            var probe = writeAhead(new JobId("v-0000000000000001"), MACHINE);
            var entryProbe = writeAhead(new JobId("v-0000000000000002"), ENTRY_SCOPE);

            write(store::writeAhead, worker);
            write(machine::writeAhead, probe);
            write(entry::writeAhead, entryProbe);

            assertAll(
                    () -> assertEquals(List.of(LockKey.queue(base, PROJECT), LockKey.machineQueue(base),
                            LockKey.onboardingEntry(base, ENTRY)), locks.acquisitions()),
                    () -> assertTrue(Files.isRegularFile(machine.recordPath(probe.jobId()))),
                    () -> assertTrue(Files.isRegularFile(entry.recordPath(entryProbe.jobId()))));
        }

        @Test
        @DisplayName("a refused job's only record is its fate")
        void refusedOnly() {
            var job = refused(BUILD_ID, PLAN_SCOPE);

            write(store::writeFate, job);

            assertEquals(Optional.of(job), store.read(BUILD_ID));
        }

        @Test
        @DisplayName("a write point that does not follow the record it replaces is refused and writes nothing")
        void outOfOrder() {
            var postSpawn = postSpawn(BUILD_ID, PLAN_SCOPE);

            assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, postSpawn));
            var queuedRecord = queued(BUILD_ID, PLAN_SCOPE);
            write(store::writeQueued, queuedRecord);
            var before = store.read(BUILD_ID);
            var writeAheadRecord = writeAhead(BUILD_ID, PLAN_SCOPE);
            var epicWriteAhead = writeAhead(BUILD_ID, new Scope(Scope.Type.EPIC, "e-1"));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeQueued, queuedRecord)),
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, postSpawn)),
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeAhead, postSpawn)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(store::writeFate, writeAheadRecord)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(store::writeAhead, epicWriteAhead)),
                    () -> assertEquals(before, store.read(BUILD_ID)));
        }

        @Test
        @DisplayName("a settled record is never replaced, and a post-spawn record never twice")
        void settledFinal() {
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));
            write(store::writePostSpawn, postSpawn(BUILD_ID, PLAN_SCOPE));
            var settled = ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7);
            var secondPostSpawn = postSpawn(BUILD_ID, PLAN_SCOPE);
            var failed = ended(BUILD_ID, PLAN_SCOPE, Fate.FAILED, 8);

            assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, secondPostSpawn));
            write(store::writeFate, settled);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeFate, failed)),
                    () -> assertEquals(Optional.of(settled), store.read(BUILD_ID)));
        }

        @Test
        @DisplayName("a write through a transaction that may not take the lock writes nothing")
        void lockRefused() {
            var refusing = new LockTransaction() {

                @Override
                public void acquire(LockKey key) {
                    throw new InternalFaultException(InternalFaultException.Reason.STORE_LOCK_NOT_HELD, "refused",
                            Map.of());
                }

                @Override
                public boolean holds(LockKey key) {
                    return false;
                }

                @Override
                public void close() {
                    // nothing is held
                }
            };

            var job = writeAhead(BUILD_ID, PLAN_SCOPE);

            assertAll(
                    () -> assertThrows(InternalFaultException.class, () -> store.writeAhead(refusing, job)),
                    () -> assertTrue(Files.notExists(store.recordPath(BUILD_ID))));
        }
    }

    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("a drawn id that names a record or a log present is drawn again, under the owning lock")
        void collisionRedraw() throws Exception {
            write(store::writeAhead, writeAhead(new JobId("b-efcdab8967452301"), PLAN_SCOPE));
            Files.writeString(store.logPath(new JobId("b-1032547698badcfe")), "log");
            locks.reset();

            JobId allocated;
            try (var transaction = locks.openTransaction()) {
                allocated = store.allocate(transaction, JobId.Kind.BUILD, PLAN_SCOPE,
                        new ScriptedRandom(0x0123456789abcdefL, 0xfedcba9876543210L, 0x1111111111111111L));
                assertTrue(transaction.holds(LockKey.plan(base, PROJECT, "plan-one")),
                        "the lock stays held for the first write");
            }

            assertAll(
                    () -> assertEquals(new JobId("b-1111111111111111"), allocated),
                    () -> assertEquals(List.of(LockKey.plan(base, PROJECT, "plan-one")), locks.acquisitions()));
        }

        @Test
        @DisplayName("the secure default draws a fresh id of the kind")
        void secureDefault() {
            try (var transaction = locks.openTransaction()) {
                var allocated = store.allocate(transaction, JobId.Kind.CI, PLAN_SCOPE);

                assertEquals(JobId.Kind.CI, allocated.kind());
            }
        }
    }

    @Nested
    @DisplayName("reading and listing")
    class Listing {

        @Test
        @DisplayName("only the files named <job_id>.json are jobs, and the recovery reads them all")
        void listing() throws Exception {
            var first = writeAhead(new JobId("b-0000000000000001"), PLAN_SCOPE);
            var second = queued(new JobId("c-0000000000000002"), new Scope(Scope.Type.WORKSPACE, null));
            write(store::writeAhead, first);
            write(store::writeQueued, second);
            Files.writeString(store.logPath(first.jobId()), "output");
            Files.writeString(store.directoryPath().resolve("b-0000000000000003.json.tmp.x"), "{}");
            Files.writeString(store.directoryPath().resolve("notes.json"), "{}");
            Files.createDirectory(store.directoryPath().resolve("b-0000000000000004.json"));

            assertAll(
                    () -> assertEquals(List.of(first.jobId(), second.jobId()), List.copyOf(store.jobIds())),
                    () -> assertEquals(List.of(first, second), store.records()));
        }

        @Test
        @DisplayName("a store without jobs/ lists nothing and reads nothing")
        void absent() {
            assertAll(
                    () -> assertTrue(store.jobIds().isEmpty()),
                    () -> assertEquals(List.of(), store.records()),
                    () -> assertEquals(Optional.empty(), store.read(BUILD_ID)));
        }

        @Test
        @DisplayName("a record that states another job id than its file name is refused")
        void foreignId() throws Exception {
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));
            var other = new JobId("b-00000000000000ff");
            Files.copy(store.recordPath(BUILD_ID), store.recordPath(other));

            var refusal = assertThrows(StoreRefusal.class, () -> store.read(other));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(store.recordPath(other), refusal.file()));
        }
    }
}
