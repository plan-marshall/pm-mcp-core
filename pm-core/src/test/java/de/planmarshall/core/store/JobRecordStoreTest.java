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
import static de.planmarshall.core.store.JobRecordCodecTest.CHILD_WALL_MS;
import static de.planmarshall.core.store.JobRecordCodecTest.ENDED_AT;
import static de.planmarshall.core.store.JobRecordCodecTest.PLAN_SCOPE;
import static de.planmarshall.core.store.JobRecordCodecTest.PROCESS_STARTED_AT;
import static de.planmarshall.core.store.JobRecordCodecTest.SPAWNED_AT;
import static de.planmarshall.core.store.JobRecordCodecTest.ended;
import static de.planmarshall.core.store.JobRecordCodecTest.postSpawn;
import static de.planmarshall.core.store.JobRecordCodecTest.queued;
import static de.planmarshall.core.store.JobRecordCodecTest.refused;
import static de.planmarshall.core.store.JobRecordCodecTest.workerAhead;
import static de.planmarshall.core.store.JobRecordCodecTest.writeAhead;
import static de.planmarshall.core.store.WorkerStateCodecTest.full;
import static de.planmarshall.core.store.WorkerStateCodecTest.waiting;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The job records of a store (PM-EXEC-4, PM-ARCH-7, PM-IMPL-7): the store paths of a project, the machine scope and
 * an onboarding entry, the lock of the owning scope under which every write point runs, a fresh id drawn again on a
 * collision, the order of the write points, and the listing for the recovery.
 */
@EnableGeneratorController
@DisplayName("Job record store")
class JobRecordStoreTest {

    private static final String PLAN = PLAN_SCOPE.id();
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);

    @TempDir
    Path base;

    private String project;
    private String entry;
    private String epic;
    private Scope entryScope;
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
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        entry = "ent-" + "%08x".formatted(Generators.integers(0, Integer.MAX_VALUE - 1).next());
        epic = "epic-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        entryScope = new Scope(Scope.Type.ENTRY, entry);
        store = JobRecordStore.ofProject(base, project);
        locks = new RecordingLockManager();
    }

    /** Runs one write point in a transaction of its own. */
    private void write(BiConsumer<LockTransaction, JobRecord> point, JobRecord job) {
        try (var transaction = locks.openTransaction()) {
            point.accept(transaction, job);
        }
    }

    static Stream<Scope.Type> ownedScopes() {
        return Stream.of(Scope.Type.PLAN, Scope.Type.EPIC, Scope.Type.WORKSPACE, Scope.Type.PROJECT);
    }

    @Nested
    @DisplayName("paths and locks")
    class PathsAndLocks {

        @Test
        @DisplayName("each store lies in the jobs/ of its home, the log beside the record")
        void storePaths() {
            var machine = JobRecordStore.ofMachine(base);
            var entryStore = JobRecordStore.ofEntry(base, entry);

            assertAll("each store and record lies in its jobs directory",
                    () -> assertEquals(base.resolve("projects/" + project + "/jobs"), store.directoryPath(),
                            "the project job directory"),
                    () -> assertEquals(base.resolve("machine/jobs"), machine.directoryPath(),
                            "the machine store lies in machine/jobs"),
                    () -> assertEquals(base.resolve("onboardings/" + entry + "/jobs"), entryStore.directoryPath(),
                            "the entry job directory"),
                    () -> assertEquals(base.resolve("projects/" + project + "/jobs/b-0123456789abcdef.json"),
                            store.recordPath(BUILD_ID), "the project record path"),
                    () -> assertEquals(base.resolve("projects/" + project + "/jobs/b-0123456789abcdef.log"),
                            store.logPath(BUILD_ID), "the project log path"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.JobRecordStoreTest#ownedScopes")
        @DisplayName("a job of a project is written under the lock of its scope")
        void projectLocks(Scope.Type type) {
            var root = Path.of("/pm-base");
            var projectStore = JobRecordStore.ofProject(root, project);
            var scope = switch (type) {
                case PLAN -> PLAN_SCOPE;
                case EPIC -> new Scope(Scope.Type.EPIC, epic);
                case WORKSPACE -> new Scope(Scope.Type.WORKSPACE, null);
                case PROJECT -> new Scope(Scope.Type.PROJECT, null);
                default -> throw new IllegalArgumentException("no owned scope of type " + type);
            };
            var expected = switch (type) {
                case PLAN -> LockKey.plan(root, project, PLAN);
                case EPIC -> LockKey.epic(root, project, epic);
                case WORKSPACE -> LockKey.workspace(root, project);
                case PROJECT -> LockKey.queue(root, project);
                default -> throw new IllegalArgumentException("no owned lock of type " + type);
            };

            assertEquals(expected, projectStore.lockKey(scope), "the project store locks the scope as expected");
        }

        @Test
        @DisplayName("a job of the machine is written under the machine queue lock, one of an entry under its lock")
        void machineAndEntryLocks() {
            assertAll("the machine and entry jobs take their locks",
                    () -> assertEquals(LockKey.machineQueue(base), JobRecordStore.ofMachine(base).lockKey(MACHINE),
                            "a machine job takes the machine queue lock"),
                    () -> assertEquals(LockKey.onboardingEntry(base, entry),
                            JobRecordStore.ofEntry(base, entry).lockKey(entryScope),
                            "an entry job takes its onboarding entry lock"));
        }

        @Test
        @DisplayName("a job of a scope of another store is refused")
        void foreignScopes() {
            var machine = JobRecordStore.ofMachine(base);
            var entryStore = JobRecordStore.ofEntry(base, entry);
            var otherEntry = new Scope(Scope.Type.ENTRY,
                    "ent-" + "%08x".formatted(Generators.integers(0, Integer.MAX_VALUE - 1).next()));

            assertAll("a scope of another store is refused",
                    () -> assertThrows(IllegalArgumentException.class, () -> store.lockKey(MACHINE),
                            "a project store refuses the machine scope"),
                    () -> assertThrows(IllegalArgumentException.class, () -> store.lockKey(entryScope),
                            "a project store refuses an entry scope"),
                    () -> assertThrows(IllegalArgumentException.class, () -> machine.lockKey(PLAN_SCOPE),
                            "the machine store refuses a plan scope"),
                    () -> assertThrows(IllegalArgumentException.class, () -> entryStore.lockKey(otherEntry),
                            "an entry store refuses another entry"),
                    () -> assertThrows(IllegalArgumentException.class, () -> JobRecordStore.ofEntry(base, "../x"),
                            "an entry path that is no id is refused"));
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

            var planLock = LockKey.plan(base, project, PLAN);
            assertAll("the life moves through the write points in order",
                    () -> assertFalse(running.isSpawnRecorded(), "the write-ahead record does not count as running"),
                    () -> assertTrue(spawned.isSpawnRecorded(), "the post-spawn record counts as running"),
                    () -> assertEquals(Optional.of(settled), store.read(BUILD_ID),
                            "the store reads back the settled record"),
                    () -> assertEquals(4, locks.transactions(),
                            "each of the four write points is a transaction"),
                    () -> assertEquals(List.of(planLock, planLock, planLock, planLock), locks.acquisitions(),
                            "every write point takes the plan lock"),
                    () -> assertEquals(List.of(), locks.held(), "no lock stays held after the writes"));
        }

        @Test
        @DisplayName("each scope's job is written under the lock of that scope")
        void ownerLocks() {
            var machine = JobRecordStore.ofMachine(base);
            var entryStore = JobRecordStore.ofEntry(base, entry);
            var worker = workerAhead(new JobId("l-0000000000000001"), new Scope(Scope.Type.PROJECT, null), "{}");
            var probe = writeAhead(new JobId("v-0000000000000001"), MACHINE);
            var entryProbe = writeAhead(new JobId("v-0000000000000002"), entryScope);

            write(store::writeAhead, worker);
            write(machine::writeAhead, probe);
            write(entryStore::writeAhead, entryProbe);

            assertAll("each scope's job is written under its lock",
                    () -> assertEquals(List.of(LockKey.queue(base, project), LockKey.machineQueue(base),
                                    LockKey.onboardingEntry(base, entry)), locks.acquisitions(),
                            "each job takes the lock of its own scope"),
                    () -> assertTrue(Files.isRegularFile(machine.recordPath(probe.jobId())),
                            "the machine job has its record file"),
                    () -> assertTrue(Files.isRegularFile(entryStore.recordPath(entryProbe.jobId())),
                            "the entry job has its record file"));
        }

        @Test
        @DisplayName("a refused job's only record is its fate")
        void refusedOnly() {
            var job = refused(BUILD_ID, PLAN_SCOPE);

            write(store::writeFate, job);

            assertEquals(Optional.of(job), store.read(BUILD_ID), "the refused job's fate record is stored");
        }

        @Test
        @DisplayName("a write point that does not follow the record it replaces is refused and writes nothing")
        void outOfOrder() {
            var postSpawn = postSpawn(BUILD_ID, PLAN_SCOPE);

            assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, postSpawn),
                    "a post-spawn record before a queued one is refused");
            var queuedRecord = queued(BUILD_ID, PLAN_SCOPE);
            write(store::writeQueued, queuedRecord);
            var before = store.read(BUILD_ID);
            var writeAheadRecord = writeAhead(BUILD_ID, PLAN_SCOPE);
            var epicWriteAhead = writeAhead(BUILD_ID, new Scope(Scope.Type.EPIC, epic));

            assertAll("every out-of-order write point is refused",
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeQueued, queuedRecord),
                            "a queued record is never written twice"),
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, postSpawn),
                            "a post-spawn after queued alone is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeAhead, postSpawn),
                            "a post-spawn record is no write-ahead record"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(store::writeFate, writeAheadRecord),
                            "a fate on a write-ahead record is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(store::writeAhead, epicWriteAhead),
                            "a write-ahead of another scope is refused"),
                    () -> assertEquals(before, store.read(BUILD_ID),
                            "no refused write changes the stored record"));
        }

        @Test
        @DisplayName("a settled record is never replaced, and a post-spawn record never twice")
        void settledFinal() {
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));
            write(store::writePostSpawn, postSpawn(BUILD_ID, PLAN_SCOPE));
            var settled = ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7);
            var secondPostSpawn = postSpawn(BUILD_ID, PLAN_SCOPE);
            var failed = ended(BUILD_ID, PLAN_SCOPE, Fate.FAILED, 8);

            assertThrows(IllegalArgumentException.class, () -> write(store::writePostSpawn, secondPostSpawn),
                    "a post-spawn record is never written twice");
            write(store::writeFate, settled);

            assertAll("a settled record stays final",
                    () -> assertThrows(IllegalArgumentException.class, () -> write(store::writeFate, failed),
                            "a settled record is never replaced"),
                    () -> assertEquals(Optional.of(settled), store.read(BUILD_ID),
                            "the first settlement stays the stored one"));
        }

        @Test
        @DisplayName("a write through a transaction that may not take the lock writes nothing")
        void lockRefused() {
            var refusing = new LockTransaction() {

                @Override
                public void acquire(LockKey key) {
                    throw new InternalFaultException(InternalFaultException.Reason.STORE_LOCK_NOT_HELD,
                            Generators.letterStrings(4, 12).next(), Map.of());
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

            assertAll("a write without the lock is refused",
                    () -> assertThrows(InternalFaultException.class, () -> store.writeAhead(refusing, job),
                            "a transaction without the lock writes nothing"),
                    () -> assertTrue(Files.notExists(store.recordPath(BUILD_ID)),
                            "no record file is created without the lock"));
        }
    }

    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("a drawn id that names a record or a log present is drawn again, under the owning lock")
        void collisionRedraw() throws Exception {
            write(store::writeAhead, writeAhead(new JobId("b-efcdab8967452301"), PLAN_SCOPE));
            Files.writeString(store.logPath(new JobId("b-1032547698badcfe")),
                    Generators.letterStrings(1, 8).next());
            locks.reset();

            JobId allocated;
            try (var transaction = locks.openTransaction()) {
                allocated = store.allocate(transaction, JobId.Kind.BUILD, PLAN_SCOPE,
                        new ScriptedRandom(0x0123456789abcdefL, 0xfedcba9876543210L, 0x1111111111111111L));
                assertTrue(transaction.holds(LockKey.plan(base, project, PLAN)),
                        "the lock stays held for the first write");
            }

            assertAll("a colliding draw is redrawn under the lock",
                    () -> assertEquals(new JobId("b-1111111111111111"), allocated,
                            "the third draw is the fresh id"),
                    () -> assertEquals(List.of(LockKey.plan(base, project, PLAN)), locks.acquisitions(),
                            "the drawn id is taken under the plan lock"));
        }

        @Test
        @DisplayName("the secure default draws a fresh id of the kind")
        void secureDefault() {
            try (var transaction = locks.openTransaction()) {
                var allocated = store.allocate(transaction, JobId.Kind.CI, PLAN_SCOPE);

                assertEquals(JobId.Kind.CI, allocated.kind(), "the secure default draws a CI id");
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
            Files.writeString(store.logPath(first.jobId()), Generators.letterStrings(1, 8).next());
            Files.writeString(store.directoryPath().resolve("b-0000000000000003.json.tmp.x"), "{}");
            Files.writeString(store.directoryPath().resolve("notes.json"), "{}");
            Files.createDirectory(store.directoryPath().resolve("b-0000000000000004.json"));

            assertAll("the listing holds exactly the job records",
                    () -> assertEquals(List.of(first.jobId(), second.jobId()), List.copyOf(store.jobIds()),
                            "only the job files are listed"),
                    () -> assertEquals(List.of(first, second), store.records(),
                            "the recovery reads both job records"));
        }

        @Test
        @DisplayName("a store without jobs/ lists nothing and reads nothing")
        void absent() {
            assertAll("an absent store lists and reads nothing",
                    () -> assertTrue(store.jobIds().isEmpty(), "an absent store lists no job id"),
                    () -> assertEquals(List.of(), store.records(), "an absent store reads no record"),
                    () -> assertEquals(Optional.empty(), store.read(BUILD_ID), "an absent store reads no job"));
        }

        @Test
        @DisplayName("a record that states another job id than its file name is refused")
        void foreignId() throws Exception {
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));
            var other = new JobId("b-00000000000000ff");
            Files.copy(store.recordPath(BUILD_ID), store.recordPath(other));

            var refusal = assertThrows(StoreRefusal.class, () -> store.read(other),
                    "a record of another job id is refused");

            assertAll("a record of another id is refused",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a foreign job id makes the record unreadable"),
                    () -> assertEquals(store.recordPath(other), refusal.file(),
                            "the refusal names the file of the foreign id"));
        }
    }

    @Nested
    @DisplayName("the worker state of a record")
    class WorkerStatePoint {

        private static final JobId WORKER = new JobId("l-00000000000000ff");
        private static final Scope PROJECT_SCOPE = new Scope(Scope.Type.PROJECT, null);

        /** The settled record of a worker, written as its only record. */
        private static JobRecord settledWorker(JobId jobId) {
            return workerAhead(jobId, PROJECT_SCOPE, "{}").toBuilder().status(Status.ENDED).fate(Fate.SUCCEEDED)
                    .outcomeRow(7).pid(4242L).processStartedAt(PROCESS_STARTED_AT).spawnedAt(SPAWNED_AT)
                    .confinement(Confinement.CONFINED).landlockAbi(5).endedAt(ENDED_AT).childWallMs(CHILD_WALL_MS)
                    .exitCode(0L).build();
        }

        private void writeWorker(JobRecordStore target, JobId jobId, WorkerState state) {
            try (var transaction = locks.openTransaction()) {
                target.writeWorker(transaction, jobId, state);
            }
        }

        @Test
        @DisplayName("it replaces only the worker block of a running worker, under the queue lock")
        void replacesTheWorkerBlock() {
            var worker = workerAhead(WORKER, PROJECT_SCOPE, "{}");
            write(store::writeAhead, worker);
            var state = full();

            writeWorker(store, WORKER, state);

            var expected = worker.withWorker(WorkerStateCodec.write(state));
            assertAll("only the worker block of a running job changes",
                    () -> assertEquals(Optional.of(expected), store.read(WORKER),
                            "the worker block is replaced in place"),
                    () -> assertEquals(Status.RUNNING, store.read(WORKER).orElseThrow().status(),
                            "the worker record stays running"),
                    () -> assertEquals(List.of(LockKey.queue(base, project), LockKey.queue(base, project)),
                            locks.acquisitions(), "both writes take the queue lock"));
        }

        @Test
        @DisplayName("a settled worker record is refused and stays as it was")
        void settledRefused() {
            var settled = settledWorker(WORKER);
            var state = waiting();
            write(store::writeFate, settled);

            assertAll("a settled worker refuses a new state",
                    () -> assertThrows(IllegalArgumentException.class, () -> writeWorker(store, WORKER, state),
                            "a settled worker record takes no state"),
                    () -> assertEquals(Optional.of(settled), store.read(WORKER),
                            "the settled worker record stays as it was"));
        }

        @Test
        @DisplayName("a record of another kind is refused")
        void nonWorkerRefused() {
            write(store::writeAhead, writeAhead(BUILD_ID, PLAN_SCOPE));

            var state = waiting();

            assertThrows(IllegalArgumentException.class, () -> writeWorker(store, BUILD_ID, state),
                    "a build record accepts no worker state");
        }

        @Test
        @DisplayName("a worker without a record and a state of another generation are refused")
        void unknownJobAndGeneration() {
            var worker = new JobId("l-0000000000000001");
            var unknown = new JobId("l-00000000000000aa");
            var state = waiting();
            var otherGeneration = state.withGenerationId(new JobId("l-00000000000000ee"));
            write(store::writeAhead, workerAhead(worker, PROJECT_SCOPE, "{}"));

            assertAll("an unknown job and a stale generation are refused",
                    () -> assertThrows(IllegalArgumentException.class, () -> writeWorker(store, unknown, state),
                            "a worker without a record is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> writeWorker(store, worker, otherGeneration),
                            "a state of another generation is refused"));
        }

        @Test
        @DisplayName("a worker of the machine is written under the machine queue lock")
        void machineWorker() {
            var machine = JobRecordStore.ofMachine(base);
            var worker = new JobId("l-0000000000000002");
            try (var transaction = locks.openTransaction()) {
                machine.writeAhead(transaction, workerAhead(worker, MACHINE, "{}"));
            }
            var state = waiting().withGenerationId(worker);

            writeWorker(machine, worker, state);

            assertAll("a machine worker is written under the machine lock",
                    () -> assertEquals(LockKey.machineQueue(base), locks.acquisitions().get(1),
                            "the machine worker write takes the machine lock"),
                    () -> assertEquals(WorkerStateCodec.write(state),
                            machine.read(worker).orElseThrow().worker(),
                            "the machine worker carries the new state"));
        }
    }
}
