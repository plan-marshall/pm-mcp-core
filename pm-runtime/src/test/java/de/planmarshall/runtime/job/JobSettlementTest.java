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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArgument;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import de.planmarshall.core.testing.generators.CoreGenerators;
import de.planmarshall.runtime.job.JobSettlement.Report;
import de.planmarshall.runtime.job.SettlementStep.Name;
import de.planmarshall.runtime.job.SettlementStep.Outcome;
import de.planmarshall.runtime.job.SettlementStep.Settled;
import de.planmarshall.runtime.job.SettlementStep.Slot;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The settlement of a job at its end (PM-EXEC-4, PM-ARCH-7): the steps run in the fixed write order whatever order
 * they are given in, a failure injected at any step stops the later ones and leaves the stores consistent, so a released
 * slot never precedes the recorded fate, and the steps whose stores do not exist report themselves as not implemented.
 * The settlement runs over a real job record store and a real build-slot lease store on the lock manager of the
 * runtime; a replacement or failing step is an EasyMock double.
 * <p>
 * Contract literals that stay: the order of the step names, the outcome texts of the fate and slot steps, the key of
 * the build slot, the malformed store content {@code {} and the not-implemented marker. The project, the plans, the
 * job id, the instants, the PID, the budget, the digest and the details of the doubled steps are generated.
 */
@EnableGeneratorController
@DisplayName("Job settlement")
class JobSettlementTest {

    /** The key of the build slot: the fixture's one slot. */
    private static final String SLOT_KEY = "build-slot-1";

    @TempDir
    Path base;

    private FileLockManager lockManager;
    private JobRecordStore records;
    private LeaseStore slots;
    private LeaseOwner owner;
    private String project;
    private Scope plan;
    private JobId job;
    private Instant createdAt;
    private String budgetKey;
    private int budgetSeconds;
    private String argvDigest;
    private long pid;
    private String doneDetail;

    @BeforeEach
    void createStores() throws IOException {
        project = CoreGenerators.projectEnrolments().next().projectId();
        plan = new Scope(Scope.Type.PLAN, "plan-" + words().next());
        job = new JobId("b-" + RuntimeGenerators.sha256Digests().next().substring(0, 16));
        createdAt = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
        budgetKey = "maven:" + words().next();
        budgetSeconds = Generators.integers(10, 3600).next();
        argvDigest = RuntimeGenerators.sha256Digests().next();
        pid = Generators.longs(2, 4_000_000).next();
        doneDetail = Generators.letterStrings(1, 32).next();
        lockManager = new FileLockManager(_ -> {
        });
        records = JobRecordStore.ofProject(base, project);
        var slotFile = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        slots = new LeaseStore(new AtomicStoreFile(slotFile, LockKey.buildSlots(base)), lockManager);
        var self = ProcessHandle.current();
        owner = new LeaseOwner(project, ScopeType.PLAN, plan.id(),
                new HolderInstance(self.pid(), self.info().startInstant().orElseThrow()), null);
    }

    private JobRecord postSpawn() {
        return JobRecord.builder().jobId(job).execution(Execution.PROCESS).scope(plan).budgetKey(budgetKey)
                .budgetSeconds(budgetSeconds)
                .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .argvDigest(argvDigest).status(Status.RUNNING).confinement(Confinement.UNAVAILABLE)
                .createdAt(createdAt).clampedValues(List.of()).pid(pid).processStartedAt(createdAt.plusMillis(20))
                .spawnedAt(createdAt).build();
    }

    private JobRecord ended() {
        return postSpawn().toBuilder().status(Status.ENDED).fate(Fate.SUCCEEDED).outcomeRow(7)
                .endedAt(createdAt.plusSeconds(60)).childWallMs(59_000L).exitCode(0L).build();
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
        assertTrue(slots.claim(new LeaseRecord(SLOT_KEY, owner, createdAt, null)).granted(),
                "the job's build slot is granted");
        return new Settled(ended(), Optional.of(new Slot(slots, SLOT_KEY, owner)));
    }

    private boolean fateRecorded() {
        return records.read(job).orElseThrow().status() == Status.ENDED;
    }

    private boolean slotHeld() {
        return slots.snapshot().stream().anyMatch(lease -> SLOT_KEY.equals(lease.key()));
    }

    /** @return an EasyMock double of a step of that name whose run does what the behaviour does */
    private static SettlementStep step(Name name, Function<Settled, Outcome> behaviour) {
        SettlementStep step = createMock(SettlementStep.class);
        expect(step.name()).andStubReturn(name);
        expect(step.run(anyObject(Settled.class))).andStubAnswer(() -> behaviour.apply(getCurrentArgument(0)));
        replay(step);
        return step;
    }

    /** A step that records its name when it runs and is done. */
    private SettlementStep recording(Name name, List<Name> ran) {
        return step(name, settled -> {
            ran.add(name);
            return new Outcome.Done(doneDetail);
        });
    }

    /** A step that fails: the crash injected at its place. */
    private static SettlementStep failing(Name name) {
        return step(name, settled -> new Outcome.Failed("crash injected at " + name));
    }

    @Nested
    @DisplayName("the order")
    class Order {

        /** The orders the steps are given in, each a deliberate permutation of the fixed order. */
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
                    () -> assertEquals(order, ran, "the steps ran in the fixed order"),
                    () -> assertEquals(order, settlement.order(), "the settlement holds the fixed order"),
                    () -> assertEquals(order, report.steps().stream().map(JobSettlement.StepReport::name).toList(),
                            "the report lists the fixed order"),
                    () -> assertTrue(report.complete(), "the settlement completed"),
                    () -> assertEquals(Name.FATE, order.getFirst(), "the fate is written first"),
                    () -> assertTrue(order.indexOf(Name.FATE) < order.indexOf(Name.SLOT_RELEASE),
                            "the fate precedes the slot release"));
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
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobSettlement(missing),
                            "a missing step is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new JobSettlement(duplicate),
                            "a duplicate step is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Settled(running, noSlot),
                            "a running job is not settled"));
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
                    () -> assertTrue(report.complete(), "the full settlement completed"),
                    () -> assertTrue(report.stoppedAt().isEmpty(), "the full settlement did not stop"),
                    () -> assertEquals(ended(), records.read(job).orElseThrow(), "the ended record was written"),
                    () -> assertFalse(slotHeld(), "the slot was released"),
                    () -> assertEquals(1, releases.size(), "the slot was released once"),
                    () -> assertEquals(new Outcome.Done("slot " + SLOT_KEY + " released"),
                            releases.getFirst().outcome(), "the release reports the slot"),
                    () -> assertEquals(new Outcome.Done("fate succeeded recorded"),
                            report.steps().getFirst().outcome(), "the fate step reports the fate"));
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
                            seams.stream().map(JobSettlement.StepReport::name).toList(),
                            "the seams are the steps without a store"),
                    () -> assertTrue(seams.stream().allMatch(step -> step.outcome()
                            .equals(new Outcome.Done(SettlementStep.NOT_IMPLEMENTED))),
                            "every seam reports not implemented"));
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
                    () -> assertEquals(Optional.of(crash), report.stoppedAt(), "the settlement stopped at the crash"),
                    () -> assertFalse(report.complete(), "the crashed settlement did not complete"),
                    () -> assertEquals(crash.ordinal() + 1, report.steps().size(),
                            "no step after the crash ran"),
                    () -> assertEquals(crash.compareTo(Name.FATE) > 0, fateRecorded,
                            "the fate is recorded only before the crash"),
                    () -> assertEquals(crash.compareTo(Name.SLOT_RELEASE) > 0, slotReleased,
                            "the slot is released only before the crash"),
                    () -> assertTrue(fateRecorded || !slotReleased, "a released slot precedes the recorded fate"));
        }

        @Test
        @DisplayName("an unreadable job record fails the fate step, and the slot stays held")
        void fateStoreFailure() throws Exception {
            var settled = runningJobWithSlot();
            Files.writeString(records.recordPath(job), "{");

            var report = JobSettlement.of(records, lockManager).settle(settled);

            assertAll(
                    () -> assertEquals(Optional.of(Name.FATE), report.stoppedAt(), "the settlement stopped at fate"),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getFirst().outcome(),
                            "the fate step failed"),
                    () -> assertTrue(slotHeld(), "the slot stays held"));
        }

        @Test
        @DisplayName("an unreadable lease store fails the slot release after the fate was recorded")
        void slotStoreFailure() throws Exception {
            var settled = runningJobWithSlot();
            Files.writeString(slots.store(), "{");

            var report = JobSettlement.of(records, lockManager).settle(settled);

            assertAll(
                    () -> assertEquals(Optional.of(Name.SLOT_RELEASE), report.stoppedAt(),
                            "the settlement stopped at the release"),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getLast().outcome(),
                            "the release step failed"),
                    () -> assertTrue(fateRecorded(), "the fate was recorded before"));
        }
    }

    @Nested
    @DisplayName("the slot release")
    class SlotRelease {

        @Test
        @DisplayName("releases nothing for a job without a slot")
        void noSlot() {
            var outcome = SettlementStep.slotRelease().run(new Settled(ended(), Optional.empty()));

            assertEquals(new Outcome.Done("no slot held"), outcome, "a job without a slot releases nothing");
        }

        @Test
        @DisplayName("leaves a lease in place that another owner holds by now")
        void foreignOwner() {
            var other = new LeaseOwner(project, ScopeType.PLAN, "plan-other-" + words().next(), owner.holderInstance(),
                    null);
            assertTrue(slots.claim(new LeaseRecord(SLOT_KEY, other, createdAt, null)).granted(),
                    "the other owner's slot is granted");

            var outcome = SettlementStep.slotRelease().run(new Settled(ended(),
                    Optional.of(new Slot(slots, SLOT_KEY, owner))));

            assertAll(
                    () -> assertEquals(new Outcome.Done("slot " + SLOT_KEY + " not held by the job"), outcome,
                            "the release reports the foreign holder"),
                    () -> assertTrue(slotHeld(), "the foreign lease stays in place"));
        }
    }

    @Nested
    @DisplayName("the replacement steps")
    class ReplacementSteps {

        @Test
        @DisplayName("a replaced step runs in the order of its name")
        void replacedStepRunsInItsPlace() {
            var ran = new ArrayList<Name>();

            var report = JobSettlement.of(records, lockManager, Map.of(Name.CHANGE_LEDGER_ROW,
                    recording(Name.CHANGE_LEDGER_ROW, ran))).settle(runningJobWithSlot());

            assertAll(
                    () -> assertEquals(List.of(Name.values()), report.steps().stream()
                            .map(JobSettlement.StepReport::name).toList(), "the fixed order is unchanged"),
                    () -> assertEquals(List.of(Name.CHANGE_LEDGER_ROW), ran, "the replacement ran once"),
                    () -> assertFalse(slotHeld(), "the slot was released after the settlement"),
                    () -> assertEquals(new Outcome.Done(doneDetail), report.steps().stream()
                            .filter(step -> step.name() == Name.CHANGE_LEDGER_ROW).findFirst().orElseThrow()
                            .outcome(), "the replacement reports what it did"),
                    () -> assertEquals(new Outcome.Done(SettlementStep.NOT_IMPLEMENTED), report.steps().stream()
                            .filter(step -> step.name() == Name.TIMING_OBSERVATION).findFirst().orElseThrow()
                            .outcome(), "a name without a replacement stays a seam"));
        }

        @Test
        @DisplayName("a failed replacement stops the settlement at its place")
        void failedReplacementStopsTheSettlement() {
            var report = JobSettlement.of(records, lockManager, Map.of(Name.TIMING_OBSERVATION,
                    failing(Name.TIMING_OBSERVATION))).settle(runningJobWithSlot());

            var fateRecorded = fateRecorded();
            var slotHeld = slotHeld();
            assertAll(
                    () -> assertEquals(Optional.of(Name.TIMING_OBSERVATION), report.stoppedAt(),
                            "the settlement stopped at the replaced step"),
                    () -> assertFalse(report.complete(), "the settlement did not complete"),
                    () -> assertFalse(report.steps().stream().anyMatch(step -> step.name() == Name.SLOT_RELEASE),
                            "no step after the failure ran"),
                    () -> assertTrue(fateRecorded, "the fate was recorded before the failure"),
                    () -> assertTrue(slotHeld, "the slot was not released after the failure"));
        }

        @Test
        @DisplayName("a replacement step that throws a store exception stops the settlement")
        void throwingReplacementStopsTheSettlement() {
            var message = Generators.letterStrings(1, 32).next();
            var throwing = step(Name.TIMING_OBSERVATION, settled -> {
                throw new UncheckedIOException(new IOException(message));
            });

            var report = JobSettlement.of(records, lockManager, Map.of(Name.TIMING_OBSERVATION, throwing))
                    .settle(runningJobWithSlot());

            assertAll(
                    () -> assertEquals(Optional.of(Name.TIMING_OBSERVATION), report.stoppedAt(),
                            "the store failure stopped the settlement"),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getLast().outcome(),
                            "the store failure is a failed step"));
        }

        @Test
        @DisplayName("a replacement step that throws a runtime fault is a failed step")
        void runtimeFaultReplacementStopsTheSettlement() {
            var message = Generators.letterStrings(1, 32).next();
            var throwing = step(Name.TIMING_OBSERVATION, settled -> {
                throw new IllegalStateException(message);
            });

            var report = JobSettlement.of(records, lockManager, Map.of(Name.TIMING_OBSERVATION, throwing))
                    .settle(runningJobWithSlot());

            assertAll(
                    () -> assertEquals(Optional.of(Name.TIMING_OBSERVATION), report.stoppedAt(),
                            "the runtime fault stopped the settlement"),
                    () -> assertInstanceOf(Outcome.Failed.class, report.steps().getLast().outcome(),
                            "the runtime fault is a failed step"));
        }

        @Test
        @DisplayName("refuses a replacement filed under another name")
        void refusesAMisnamedReplacement() {
            var mismatched = Map.of(Name.METRICS_ROW, recording(Name.TIMING_OBSERVATION, new ArrayList<>()));

            assertThrows(IllegalArgumentException.class,
                    () -> JobSettlement.of(records, lockManager, mismatched),
                    "a replacement under another name is refused");
        }

        @Test
        @DisplayName("refuses to replace an implemented step")
        void refusesAnImplementedStep() {
            var fate = Map.of(Name.FATE, recording(Name.FATE, new ArrayList<>()));
            var slotRelease = Map.of(Name.SLOT_RELEASE, recording(Name.SLOT_RELEASE, new ArrayList<>()));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> JobSettlement.of(records, lockManager, fate),
                            "the fate step cannot be replaced"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> JobSettlement.of(records, lockManager, slotRelease),
                            "the slot release cannot be replaced"));
        }
    }

    @Test
    @DisplayName("a report without steps has not stopped and is not complete")
    void emptyReport() {
        var report = new Report(List.of());

        assertAll(
                () -> assertTrue(report.stoppedAt().isEmpty(), "an empty report has not stopped"),
                () -> assertFalse(report.complete(), "an empty report is not complete"));
    }
}
