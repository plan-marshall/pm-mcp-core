/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ModelSlotStore;
import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.Lease;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.runtime.launch.ModelSlotScheduler.Admission;
import de.planmarshall.runtime.launch.ModelSlotScheduler.Deferred;
import de.planmarshall.runtime.launch.ModelSlotScheduler.Granted;
import de.planmarshall.runtime.launch.ModelSlotScheduler.Queued;
import de.planmarshall.runtime.launch.ModelSlotScheduler.Request;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The admission of the machine model slots (PM-WF-14, Model Work § Model Slots &amp; One Writer per Plan).
 *
 * <p>
 * The instant, the project and plan ids and the job numbers behind the job ids are generated. The literals that stay
 * are the contract: the job id shape {@code l-} and sixteen hex digits, the role {@code writer}, the store's file name
 * {@code model-slots.json}, the slot counts at their boundaries (the cap 1 to 16 and the default 2), the eight
 * concurrent claimants, and the PID {@code Integer.MAX_VALUE - 1} of a runtime that no longer runs.
 */
@EnableGeneratorController
@DisplayName("Model slots")
class ModelSlotSchedulerTest {

    private static final Instant PROCESS_START =
            ProcessHandle.current().info().startInstant().orElse(Instant.EPOCH);

    @TempDir
    Path base;

    private Path file;
    private ModelSlotStore store;
    private Instant now;
    private String project;
    private String plan;
    private String otherPlan;
    private int jobBase;

    @BeforeEach
    void createStore() throws IOException {
        now = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
        project = "project-" + lowerLetters();
        plan = "plan-" + lowerLetters();
        otherPlan = plan + "-other";
        jobBase = Generators.integers(0, 1_000_000).next();
        file = Files.createDirectories(base.resolve("state")).resolve("model-slots.json");
        store = new ModelSlotStore(new AtomicStoreFile(file, LockKey.modelSlots(base)),
                new FileLockManager(_ -> {
                }));
    }

    private ModelSlotScheduler scheduler(int slots) {
        return new ModelSlotScheduler(store, slots, ModelSlotScheduler.Liveness.processes(),
                InstantSource.fixed(now));
    }

    private LeaseOwner owner(String planId) {
        return new LeaseOwner(project, planId == null ? ScopeType.PROJECT : ScopeType.PLAN, planId,
                new HolderInstance(ProcessHandle.current().pid(), PROCESS_START), null);
    }

    /** @return the job id of the n-th job of a test, in the shape {@code l-} and sixteen hex digits */
    private String jobId(int job) {
        return "l-%016x".formatted(jobBase + job);
    }

    private Request request(String planId, int job, WorkerClass workerClass, Form form) {
        return new Request(owner(planId), jobId(job), "writer", workerClass, form, planId);
    }

    private Request writer(String planId, int job) {
        return request(planId, job, WorkerClass.WRITER, Form.FRESH);
    }

    private static String lowerLetters() {
        return Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }

    @Nested
    @DisplayName("admission")
    class AdmissionRules {

        @Test
        @DisplayName("grants while the cap has room and queues the worker beyond it, in FIFO order")
        void fifo() {
            var scheduler = scheduler(1);

            var first = assertInstanceOf(Granted.class, scheduler.request(writer(plan, 1)),
                    "the first worker is granted a slot");
            var second = assertInstanceOf(Queued.class, scheduler.request(writer(otherPlan, 2)),
                    "the second worker is queued beyond the cap");
            var third = assertInstanceOf(Queued.class, scheduler.request(writer(plan + "-third", 3)),
                    "the third worker is queued beyond the cap");

            assertAll(
                    () -> assertEquals(jobId(1), first.lease().jobId(), "the first worker holds the slot"),
                    () -> assertEquals(1, store.snapshot().slots().size(), "one slot is held under a cap of one"),
                    () -> assertEquals(List.of(second.waiting(), third.waiting()), store.snapshot().waiting(),
                            "the queued workers wait in FIFO order"));

            var admitted = scheduler.release(first.lease().jobId());

            assertAll(
                    () -> assertEquals(List.of(jobId(2)), admitted.stream().map(Lease::jobId).toList(),
                            "the release admits the first waiting worker"),
                    () -> assertEquals(List.of(third.waiting()), store.snapshot().waiting(),
                            "the third worker still waits"));
        }

        @Test
        @DisplayName("never exceeds the count under concurrent claims")
        void concurrentClaimsNeverExceedTheirCount() throws Exception {
            var scheduler = scheduler(2);
            var threads = 8;
            var ready = new CountDownLatch(threads);
            var start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(threads);
            try {
                List<Callable<Admission>> claims = new ArrayList<>();
                for (var i = 0; i < threads; i++) {
                    var job = i;
                    claims.add(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return scheduler.request(writer(plan + "-" + job, job + 1));
                    });
                }
                var futures = claims.stream().map(executor::submit).toList();
                assertTrue(ready.await(10, TimeUnit.SECONDS), "every claimant is ready in time");
                start.countDown();
                var outcomes = new ArrayList<Admission>();
                for (var future : futures) {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                }

                var granted = outcomes.stream().filter(Granted.class::isInstance).count();
                assertAll(
                        () -> assertEquals(2, granted, "exactly the cap is granted"),
                        () -> assertEquals(2, store.snapshot().slots().size(), "never above the cap"),
                        () -> assertEquals(6, store.snapshot().waiting().size(),
                                "every other claimant waits for a slot"));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("admits at most one writer worker of a plan, and defers the second")
        void oneWriterPerPlan() {
            var scheduler = scheduler(2);

            assertInstanceOf(Granted.class, scheduler.request(writer(plan, 1)), "the plan's first writer is granted");
            var second = assertInstanceOf(Deferred.class, scheduler.request(writer(plan, 2)),
                    "the plan's second writer is deferred");
            var reader = assertInstanceOf(Granted.class,
                    scheduler.request(request(plan, 3, WorkerClass.READ_ONLY, Form.WARM)),
                    "a reader of the plan is granted beside it");

            assertAll(
                    () -> assertEquals(ModelSlotScheduler.WRITER_HOLDS_PLAN, second.reason(),
                            "the deferral names the plan's writer"),
                    () -> assertEquals(2, store.snapshot().slots().size(), "the writer and the reader hold slots"),
                    () -> assertEquals(WorkerClass.READ_ONLY, reader.lease().workerClass(),
                            "the reader's lease is of the read-only class"),
                    () -> assertEquals(Form.WARM, reader.lease().form(), "the reader's lease keeps its warm form"),
                    () -> assertEquals(List.of(), store.snapshot().waiting(), "a deferred writer does not wait"),
                    () -> assertEquals(1, store.snapshot().slots().stream()
                            .filter(lease -> lease.workerClass() == WorkerClass.WRITER).count(),
                            "only one writer of the plan holds a slot"));
        }

        @Test
        @DisplayName("admits the writers of two projects that name the same plan id")
        void writersOfTwoProjects() {
            var scheduler = scheduler(2);
            var holder = new HolderInstance(ProcessHandle.current().pid(), PROCESS_START);
            var first = new Request(new LeaseOwner(project, ScopeType.PLAN, plan, holder, null),
                    jobId(1), "writer", WorkerClass.WRITER, Form.FRESH, plan);
            var second = new Request(new LeaseOwner(project + "-other", ScopeType.PLAN, plan, holder, null),
                    jobId(2), "writer", WorkerClass.WRITER, Form.FRESH, plan);

            assertInstanceOf(Granted.class, scheduler.request(first), "the first project's writer is granted");
            assertInstanceOf(Granted.class, scheduler.request(second), "the second project's writer is granted");
        }

        @Test
        @DisplayName("reaps the slot of a runtime that no longer runs in the admission transaction")
        void staleSlot() {
            var scheduler = scheduler(1);
            var dead = new Lease(new LeaseOwner(project, ScopeType.PLAN, plan,
                            new HolderInstance(Integer.MAX_VALUE - 1, now), null), jobId(170), "writer",
                    WorkerClass.WRITER, Form.FRESH, plan, now, null);
            store.update(document -> new ModelSlotStore.Revision<>(
                    new ModelSlotStore.Document(List.of(dead), List.of()), Boolean.TRUE));

            var admitted = assertInstanceOf(Granted.class, scheduler.request(writer(otherPlan, 1)),
                    "the stale slot is reaped and granted");

            assertAll(
                    () -> assertEquals(jobId(1), admitted.lease().jobId(), "the new worker holds the reaped slot"),
                    () -> assertEquals(List.of(admitted.lease()), store.snapshot().slots(),
                            "the dead runtime's lease is gone"));
        }
    }

    @Nested
    @DisplayName("a paused plan")
    class Pause {

        @Test
        @DisplayName("at once releases the slots of its workers and admits the workers they made room for")
        void aPlanPausedAtOnceReleasesTheSlotsOfItsWorkers() {
            var scheduler = scheduler(2);
            scheduler.request(writer(plan, 1));
            scheduler.request(request(plan, 2, WorkerClass.READ_ONLY, Form.WARM));
            var waiting = assertInstanceOf(Queued.class, scheduler.request(writer(otherPlan, 3)),
                    "the other plan's writer waits");

            var admitted = scheduler.releasePlan(project, plan);

            assertAll(
                    () -> assertEquals(List.of(jobId(3)), admitted.stream().map(Lease::jobId).toList(),
                            "the pause admits the waiting worker"),
                    () -> assertEquals(1, store.snapshot().slots().size(), "only the admitted worker holds a slot"),
                    () -> assertEquals(otherPlan, store.snapshot().slots().getFirst().planId(),
                            "the slot belongs to the other plan"),
                    () -> assertEquals(List.of(), store.snapshot().waiting(), "no worker waits after the pause"),
                    () -> assertEquals(jobId(3), waiting.waiting().jobId(), "the queued worker was the admitted"));
        }

        @Test
        @DisplayName("releases only the plan of the named project, never another project's plan of the same id")
        void releasesOneProjectOnly() {
            var scheduler = scheduler(2);
            var holder = new HolderInstance(ProcessHandle.current().pid(), PROCESS_START);
            var other = project + "-other";
            scheduler.request(new Request(new LeaseOwner(project, ScopeType.PLAN, plan, holder, null),
                    jobId(1), "writer", WorkerClass.WRITER, Form.FRESH, plan));
            scheduler.request(new Request(new LeaseOwner(other, ScopeType.PLAN, plan, holder, null),
                    jobId(2), "writer", WorkerClass.WRITER, Form.FRESH, plan));

            scheduler.releasePlan(project, plan);

            var remaining = store.snapshot().slots();
            assertAll(
                    () -> assertEquals(1, remaining.size(), "one slot remains after the release"),
                    () -> assertEquals(other, remaining.getFirst().owner().projectId(),
                            "the other project's slot remains"),
                    () -> assertEquals(jobId(2), remaining.getFirst().jobId(), "the other project's job remains"));
        }

        @Test
        @DisplayName("drops its waiting workers too, so no slot is reserved for a plan that does not run")
        void releasesWaiting() {
            var scheduler = scheduler(1);
            scheduler.request(writer(otherPlan, 1));
            scheduler.request(request(plan, 2, WorkerClass.READ_ONLY, Form.FRESH));

            scheduler.releasePlan(project, plan);

            assertAll(
                    () -> assertEquals(1, store.snapshot().slots().size(), "the other plan keeps its slot"),
                    () -> assertEquals(List.of(), store.snapshot().waiting(), "the paused plan's waiters are gone"));
        }
    }

    @Nested
    @DisplayName("the request")
    class TheRequest {

        @Test
        @DisplayName("refuses a writer without a plan and a missing component")
        void refusals() {
            var owner = owner(plan);
            var job = jobId(1);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Request(owner,
                            job, "writer", WorkerClass.WRITER, Form.FRESH, null), "a writer without a plan is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Request(null,
                            job, "writer", WorkerClass.READ_ONLY, Form.FRESH, plan), "a request without owner is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Request(owner,
                            null, "writer", WorkerClass.READ_ONLY, Form.FRESH, plan),
                            "a request without a job id is refused"));
        }
    }

    @Nested
    @DisplayName("the cap")
    class Cap {
        @Test
        @DisplayName("lies between 1 and 16, as machine-config.json bounds it")
        void bounds() {
            var liveness = ModelSlotScheduler.Liveness.processes();
            var clock = InstantSource.fixed(now);
            var capped = new ModelSlotScheduler(store, 16, liveness, clock);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ModelSlotScheduler(store, 0, liveness, clock), "a cap of zero slots is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ModelSlotScheduler(store, 17, liveness, clock),
                            "a cap above sixteen slots is refused"),
                    () -> assertEquals(2, ModelSlotScheduler.DEFAULT_SLOTS, "the default cap is two slots"),
                    () -> assertEquals(16, capped.slotCount(), "the upper bound sixteen is accepted"));
        }
    }
}
