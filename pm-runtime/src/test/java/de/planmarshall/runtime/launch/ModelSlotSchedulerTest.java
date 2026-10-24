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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
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
 */
@DisplayName("Model slots")
class ModelSlotSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final String PLAN = "some-plan";
    private static final String OTHER_PLAN = "other-plan";

    @TempDir
    Path base;

    private Path file;
    private ModelSlotStore store;

    @BeforeEach
    void createStore() throws IOException {
        file = Files.createDirectories(base.resolve("state")).resolve("model-slots.json");
        store = new ModelSlotStore(new AtomicStoreFile(file, LockKey.modelSlots(base)),
                new FileLockManager(_ -> {
                }));
    }

    private ModelSlotScheduler scheduler(int slots) {
        return new ModelSlotScheduler(store, slots, ModelSlotScheduler.Liveness.processes(),
                InstantSource.fixed(NOW));
    }

    private static LeaseOwner owner(String planId) {
        return new LeaseOwner("pm-mcp-core", planId == null ? ScopeType.PROJECT : ScopeType.PLAN, planId,
                new HolderInstance(ProcessHandle.current().pid(), NOW), null);
    }

    private static Request request(String planId, int job, WorkerClass workerClass, Form form) {
        return new Request(owner(planId), "l-%016x".formatted(job), "writer", workerClass, form, planId);
    }

    private static Request writer(String planId, int job) {
        return request(planId, job, WorkerClass.WRITER, Form.FRESH);
    }

    @Nested
    @DisplayName("admission")
    class AdmissionRules {

        @Test
        @DisplayName("grants while the cap has room and queues the worker beyond it, in FIFO order")
        void fifo() {
            var scheduler = scheduler(1);

            var first = assertInstanceOf(Granted.class, scheduler.request(writer(PLAN, 1)));
            var second = assertInstanceOf(Queued.class, scheduler.request(writer(OTHER_PLAN, 2)));
            var third = assertInstanceOf(Queued.class, scheduler.request(writer("third-plan", 3)));

            assertAll(
                    () -> assertEquals("l-0000000000000001", first.lease().jobId()),
                    () -> assertEquals(1, store.snapshot().slots().size()),
                    () -> assertEquals(List.of(second.waiting(), third.waiting()), store.snapshot().waiting()));

            var admitted = scheduler.release(first.lease().jobId());

            assertAll(
                    () -> assertEquals(List.of("l-0000000000000002"), admitted.stream().map(Lease::jobId).toList()),
                    () -> assertEquals(List.of(third.waiting()), store.snapshot().waiting()));
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
                        return scheduler.request(writer("plan-" + job, job + 1));
                    });
                }
                var futures = claims.stream().map(executor::submit).toList();
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                var outcomes = new ArrayList<Admission>();
                for (var future : futures) {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                }

                var granted = outcomes.stream().filter(Granted.class::isInstance).count();
                assertAll(
                        () -> assertEquals(2, granted, "exactly the cap is granted"),
                        () -> assertEquals(2, store.snapshot().slots().size(), "never above the cap"),
                        () -> assertEquals(6, store.snapshot().waiting().size()));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("admits at most one writer worker of a plan, and defers the second")
        void oneWriterPerPlan() {
            var scheduler = scheduler(2);

            var first = assertInstanceOf(Granted.class, scheduler.request(writer(PLAN, 1)));
            var second = assertInstanceOf(Deferred.class, scheduler.request(writer(PLAN, 2)));
            var reader = assertInstanceOf(Granted.class,
                    scheduler.request(request(PLAN, 3, WorkerClass.READ_ONLY, Form.WARM)));

            assertAll(
                    () -> assertEquals(ModelSlotScheduler.WRITER_HOLDS_PLAN, second.reason()),
                    () -> assertEquals(2, store.snapshot().slots().size()),
                    () -> assertEquals(WorkerClass.READ_ONLY, reader.lease().workerClass()),
                    () -> assertEquals(Form.WARM, reader.lease().form()),
                    () -> assertEquals(List.of(), store.snapshot().waiting()),
                    () -> assertEquals(1, store.snapshot().slots().stream()
                            .filter(lease -> lease.workerClass() == WorkerClass.WRITER).count()));
        }

        @Test
        @DisplayName("reaps the slot of a runtime that no longer runs in the admission transaction")
        void staleSlot() {
            var scheduler = scheduler(1);
            var dead = new Lease(new LeaseOwner("pm-mcp-core", ScopeType.PLAN, PLAN,
                            new HolderInstance(Integer.MAX_VALUE - 1, NOW), null), "l-00000000000000aa", "writer",
                    WorkerClass.WRITER, Form.FRESH, PLAN, NOW, null);
            store.update(document -> new ModelSlotStore.Revision<>(
                    new ModelSlotStore.Document(List.of(dead), List.of()), Boolean.TRUE));

            var admitted = assertInstanceOf(Granted.class, scheduler.request(writer(OTHER_PLAN, 1)));

            assertAll(
                    () -> assertEquals("l-0000000000000001", admitted.lease().jobId()),
                    () -> assertEquals(List.of(admitted.lease()), store.snapshot().slots()));
        }
    }

    @Nested
    @DisplayName("a paused plan")
    class Pause {

        @Test
        @DisplayName("at once releases the slots of its workers and admits the workers they made room for")
        void aPlanPausedAtOnceReleasesTheSlotsOfItsWorkers() {
            var scheduler = scheduler(2);
            scheduler.request(writer(PLAN, 1));
            scheduler.request(request(PLAN, 2, WorkerClass.READ_ONLY, Form.WARM));
            var waiting = assertInstanceOf(Queued.class, scheduler.request(writer(OTHER_PLAN, 3)));

            var admitted = scheduler.releasePlan(PLAN);

            assertAll(
                    () -> assertEquals(List.of("l-0000000000000003"), admitted.stream().map(Lease::jobId).toList()),
                    () -> assertEquals(1, store.snapshot().slots().size()),
                    () -> assertEquals(OTHER_PLAN, store.snapshot().slots().getFirst().planId()),
                    () -> assertEquals(List.of(), store.snapshot().waiting()),
                    () -> assertEquals("l-0000000000000003", waiting.waiting().jobId()));
        }

        @Test
        @DisplayName("drops its waiting workers too, so no slot is reserved for a plan that does not run")
        void releasesWaiting() {
            var scheduler = scheduler(1);
            scheduler.request(writer(OTHER_PLAN, 1));
            scheduler.request(request(PLAN, 2, WorkerClass.READ_ONLY, Form.FRESH));

            scheduler.releasePlan(PLAN);

            assertAll(
                    () -> assertEquals(1, store.snapshot().slots().size()),
                    () -> assertEquals(List.of(), store.snapshot().waiting()));
        }
    }

    @Nested
    @DisplayName("the request")
    class TheRequest {

        @Test
        @DisplayName("refuses a writer without a plan and a missing component")
        void refusals() {
            var owner = owner(PLAN);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Request(owner,
                            "l-0000000000000001", "writer", WorkerClass.WRITER, Form.FRESH, null)),
                    () -> assertThrows(NullPointerException.class, () -> new Request(null,
                            "l-0000000000000001", "writer", WorkerClass.READ_ONLY, Form.FRESH, PLAN)),
                    () -> assertThrows(NullPointerException.class, () -> new Request(owner,
                            null, "writer", WorkerClass.READ_ONLY, Form.FRESH, PLAN)));
        }
    }

    @Nested
    @DisplayName("the cap")
    class Cap {
        @Test
        @DisplayName("lies between 1 and 16, as machine-config.json bounds it")
        void bounds() {
            var liveness = ModelSlotScheduler.Liveness.processes();
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ModelSlotScheduler(store, 0, liveness, InstantSource.fixed(NOW))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ModelSlotScheduler(store, 17, liveness, InstantSource.fixed(NOW))),
                    () -> assertEquals(ModelSlotScheduler.DEFAULT_SLOTS, 2),
                    () -> assertEquals(16, new ModelSlotScheduler(store, 16, liveness, InstantSource.fixed(NOW))
                            .slotCount()));
        }
    }
}
