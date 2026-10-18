/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.runtime.job.JobSettlement.Report;
import de.planmarshall.runtime.job.SettlementStep.Name;
import de.planmarshall.runtime.job.SettlementStep.Outcome;
import de.planmarshall.runtime.job.SettlementStep.Settled;
import de.planmarshall.runtime.job.SettlementStep.Slot;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The settlement of a job at its end (PM-EXEC-4, PM-ARCH-7): the steps run in the fixed write order whatever order
 * they are given in, a failure injected at any step stops the later ones and leaves the stores consistent, so a released
 * slot never precedes the recorded fate, and the steps whose stores do not exist report themselves as not implemented.
 * The settlement runs over a real job record store and a real build-slot lease store on the lock manager of the
 * runtime.
 */
@DisplayName("Job settlement")
class JobSettlementTest {

    private static final String PROJECT = "project-a";
    private static final Scope PLAN = new Scope(Scope.Type.PLAN, "plan-one");
    private static final JobId JOB = new JobId("b-0123456789abcdef");
    private static final Instant CREATED_AT = Instant.parse("2026-10-17T10:00:00Z");
    private static final String SLOT_KEY = "build-slot-1";

    @TempDir
    Path base;

    private FileLockManager lockManager;
    private JobRecordStore records;
    private LeaseStore slots;
    private LeaseOwner owner;

    @BeforeEach
    void createStores() throws IOException {
        lockManager = new FileLockManager(_ -> {
        });
        records = JobRecordStore.ofProject(base, PROJECT);
        var slotFile = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        slots = new LeaseStore(new AtomicStoreFile(slotFile, LockKey.buildSlots(base)), lockManager);
        var self = ProcessHandle.current();
        owner = new LeaseOwner(PROJECT, ScopeType.PLAN, PLAN.id(),
                new HolderInstance(self.pid(), self.info().startInstant().orElseThrow()), null);
    }

    private static JobRecord postSpawn() {
        return JobRecord.builder().jobId(JOB).execution(Execution.PROCESS).scope(PLAN).budgetKey("maven:verify")
                .budgetSeconds(600).budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .argvDigest("ab".repeat(32)).status(Status.RUNNING).confinement(Confinement.UNAVAILABLE)
                .createdAt(CREATED_AT).clampedValues(List.of()).pid(4242L).processStartedAt(CREATED_AT.plusMillis(20))
                .spawnedAt(CREATED_AT).build();
    }

    private static JobRecord ended() {
        return postSpawn().toBuilder().status(Status.ENDED).fate(Fate.SUCCEEDED).outcomeRow(7)
                .endedAt(CREATED_AT.plusSeconds(60)).childWallMs(59_000L).exitCode(0L).build();
    }

    /** Runs the job to its post-spawn record and claims its build slot. */
    private Settled runningJobWithSlot() {
        try (var transaction = lockManager.openTransaction()) {
            records.writeAhead(transaction, postSpawn().toBuilder().pid(null).processStartedAt(null).spawnedAt(null)
                    .build());
        }
        try (var transaction = lockManager.openTransaction()) {
            records.writePostSpawn(transaction, postSpawn());
        }
        assertTrue(slots.claim(new LeaseRecord(SLOT_KEY, owner, CREATED_AT, null)).granted());
        return new Settled(ended(), Optional.of(new Slot(slots, SLOT_KEY, owner)));
    }

    private boolean fateRecorded() {
        return records.read(JOB).orElseThrow().status() == Status.ENDED;
    }

    private boolean slotHeld() {
        return slots.snapshot().stream().anyMatch(lease -> SLOT_KEY.equals(lease.key()));
    }

    /** A step that records its name when it runs and is done. */
    private static SettlementStep recording(Name name, List<Name> ran) {
        return new SettlementStep() {

            @Override
            public Name name() {
                return name;
            }

            @Override
            public Outcome run(Settled settled) {
                ran.add(name);
                return new Outcome.Done("ran");
            }
        };
    }

    /** A step that fails: the crash injected at its place. */
    private static SettlementStep failing(Name name) {
        return new SettlementStep() {

            @Override
            public Name name() {
                return name;
            }

            @Override
            public Outcome run(Settled settled) {
                return new Outcome.Failed("crash injected at " + name);
            }
        };
    }

    @Nested
    @DisplayName("the order")
    class Order {

        static Stream<Arguments> givenOrders() {
            var natural = List.of(Name.values());
            var reversed = new ArrayList<>(natural);
            Collections.reverse(reversed);
            var slotFirst = new ArrayList<>(natural);
            slotFirst.remove(Name.SLOT_RELEASE);
            slotFirst.addFirst(Name.SLOT_RELEASE);
            return Stream.of(Arguments.of("natural", natural), Arguments.of("reversed", reversed),
                    Arguments.of("slot release first", slotFirst));
        }

        @ParameterizedTest(name = "given {0}")
        @MethodSource("givenOrders")
        @DisplayName("is the fixed write order whatever order the steps are given in")
        void fixedOrder(String given, List<Name> names) {
            var ran = new ArrayList<Name>();
            var settlement = new JobSettlement(names.stream().map(name -> recording(name, ran)).toList());

            var report = settlement.settle(new Settled(ended(), Optional.empty()));

            var order = List.of(Name.values());
            assertAll(
                    () -> assertEquals(order, ran),
                    () -> assertEquals(order, settlement.order()),
                    () -> assertEquals(order, report.steps().stream().map(JobSettlement.StepReport::name).toList()),
                    () -> assertTrue(report.complete()),
                    () -> assertEquals(Name.FATE, order.getFirst()),
                    () -> assertTrue(order.indexOf(Name.FATE) < order.indexOf(Name.SLOT_RELEASE)));
        }

        @Test
        @DisplayName("needs exactly one step per name")
        void oneStepPerName() {
            var ran = new ArrayList<Name>();
            var missing = Arrays.stream(Name.values()).filter(name -> name != Name.METRICS_ROW)
                    .map(name -> recording(name, ran)).toList();
            var duplicate = new ArrayList<>(Arrays.stream(Name.values()).map(name -> recording(name, ran)).toList());
            duplicate.add(recording(Name.FATE, ran));
            var running = postSpawn();
            Optional<Slot> noSlot = Optional.empty();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobSettlement(missing)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobSettlement(duplicate)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Settled(running, noSlot)));
        }
    }

    @Nested
    @DisplayName("over the real stores")
    class RealStores {

        @Test
        @DisplayName("matched control: a full settlement records the fate and releases the slot exactly once")
        void fullSettlement() {
            var settled = runningJobWithSlot();

            var report = JobSettlement.of(records, lockManager).settle(settled);

            var releases = report.steps().stream().filter(step -> step.name() == Name.SLOT_RELEASE).toList();
            assertAll(
                    () -> assertTrue(report.complete()),
                    () -> assertTrue(report.stoppedAt().isEmpty()),
                    () -> assertEquals(ended(), records.read(JOB).orElseThrow()),
                    () -> assertFalse(slotHeld()),
                    () -> assertEquals(1, releases.size()),
                    () -> assertEquals(new Outcome.Done("slot build-slot-1 released"), releases.getFirst().outcome()),
                    () -> assertEquals(new Outcome.Done("fate succeeded recorded"),
                            report.steps().getFirst().outcome()));
        }

        @Test
        @DisplayName("the steps without a store are seams that report they are not implemented")
        void seams() {
            var report = JobSettlement.of(records, lockManager).settle(runningJobWithSlot());

            var seams = report.steps().stream().filter(step -> step.name() != Name.FATE
                    && step.name() != Name.SLOT_RELEASE).toList();
            assertAll(
                    () -> assertEquals(List.of(Name.WORKER_QUEUE_TASK, Name.METRICS_ROW, Name.CHANGE_LEDGER_ROW,
                                    Name.TIMING_OBSERVATION, Name.JOB_PRIVATE_DIRECTORY),
                            seams.stream().map(JobSettlement.StepReport::name).toList()),
                    () -> assertTrue(seams.stream().allMatch(step -> step.outcome()
                            .equals(new Outcome.Done(SettlementStep.NOT_IMPLEMENTED)))));
        }

        @ParameterizedTest(name = "crash at {0}")
        @EnumSource(Name.class)
        @DisplayName("a crash at any step stops the later ones; a released slot never precedes the recorded fate")
        void crashInjection(Name crash) {
            var settled = runningJobWithSlot();
            var steps = new ArrayList<SettlementStep>();
            for (var name : Name.values()) {
                steps.add(name == crash ? failing(name) : switch (name) {
                    case FATE -> SettlementStep.fate(records, lockManager);
                    case SLOT_RELEASE -> SettlementStep.slotRelease();
                    default -> SettlementStep.notImplemented(name);
                });
            }

            var report = new JobSettlement(steps).settle(settled);

            var fateRecorded = fateRecorded();
            var slotReleased = !slotHeld();
            assertAll(
                    () -> assertEquals(Optional.of(crash), report.stoppedAt()),
                    () -> assertFalse(report.complete()),
                    () -> assertEquals(crash.ordinal() + 1, report.steps().size()),
                    () -> assertEquals(crash.compareTo(Name.FATE) > 0, fateRecorded),
                    () -> assertEquals(crash.compareTo(Name.SLOT_RELEASE) > 0, slotReleased),
                    () -> assertTrue(fateRecorded || !slotReleased, "a released slot precedes the recorded fate"));
        }

        @Test
        @DisplayName("an unreadable job record fails the fate step, and the slot stays held")
        void fateStoreFailure() throws Exception {
            var settled = runningJobWithSlot();
            Files.writeString(records.recordPath(JOB), "{");

            var report = JobSettlement.of(records, lockManager).settle(settled);

            assertAll(
                    () -> assertEquals(Optional.of(Name.FATE), report.stoppedAt()),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getFirst().outcome()),
                    () -> assertTrue(slotHeld()));
        }

        @Test
        @DisplayName("an unreadable lease store fails the slot release after the fate was recorded")
        void slotStoreFailure() throws Exception {
            var settled = runningJobWithSlot();
            Files.writeString(slots.store(), "{");

            var report = JobSettlement.of(records, lockManager).settle(settled);

            assertAll(
                    () -> assertEquals(Optional.of(Name.SLOT_RELEASE), report.stoppedAt()),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getLast().outcome()),
                    () -> assertTrue(fateRecorded()));
        }
    }

    @Nested
    @DisplayName("the slot release")
    class SlotRelease {

        @Test
        @DisplayName("releases nothing for a job without a slot")
        void noSlot() {
            var outcome = SettlementStep.slotRelease().run(new Settled(ended(), Optional.empty()));

            assertEquals(new Outcome.Done("no slot held"), outcome);
        }

        @Test
        @DisplayName("leaves a lease in place that another owner holds by now")
        void foreignOwner() {
            var other = new LeaseOwner(PROJECT, ScopeType.PLAN, "plan-two", owner.holderInstance(), null);
            assertTrue(slots.claim(new LeaseRecord(SLOT_KEY, other, CREATED_AT, null)).granted());

            var outcome = SettlementStep.slotRelease().run(new Settled(ended(),
                    Optional.of(new Slot(slots, SLOT_KEY, owner))));

            assertAll(
                    () -> assertEquals(new Outcome.Done("slot build-slot-1 not held by the job"), outcome),
                    () -> assertTrue(slotHeld()));
        }
    }

    @Test
    @DisplayName("a report without steps has not stopped and is not complete")
    void emptyReport() {
        var report = new Report(List.of());

        assertAll(
                () -> assertTrue(report.stoppedAt().isEmpty()),
                () -> assertFalse(report.complete()));
    }
}
