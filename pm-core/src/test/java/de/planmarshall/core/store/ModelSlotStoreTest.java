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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.ModelSlotStore.Document;
import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.Lease;
import de.planmarshall.core.store.ModelSlotStore.Revision;
import de.planmarshall.core.store.ModelSlotStore.Waiting;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;

/**
 * The model-slot store (Store Schemas § Machine Lease Stores): its document round-trips byte-for-byte through the
 * codec, one transaction writes it, and the rules of the document — one slot per worker, one writer per plan — are
 * refused before they are written.
 */
@DisplayName("Model-slot store")
class ModelSlotStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final String PLAN = "some-plan";
    private static final String OTHER_PLAN = "other-plan";

    @TempDir
    Path base;

    private Path file;
    private ModelSlotStore store;

    private static LeaseOwner owner(String planId) {
        var scope = planId == null ? ScopeType.WORKSPACE : ScopeType.PLAN;
        return new LeaseOwner("pm-mcp-core", scope, planId, new HolderInstance(4711, NOW), null);
    }

    private static Lease lease(String jobId, String planId, WorkerClass workerClass) {
        return new Lease(owner(planId), jobId, "writer", workerClass, Form.WARM, planId, NOW, null);
    }

    private static Waiting waiting(String jobId, String planId, WorkerClass workerClass) {
        return new Waiting(owner(planId), jobId, "writer", workerClass, Form.FRESH, planId, NOW);
    }

    @BeforeEach
    void createStore() throws IOException {
        file = Files.createDirectories(base.resolve("state")).resolve("model-slots.json");
        store = new ModelSlotStore(new AtomicStoreFile(file, LockKey.modelSlots(base)), new RecordingLockManager());
    }

    @Nested
    @DisplayName("the document")
    class DocumentRoundTrip {

        @Test
        @DisplayName("is written and read back with its slots and waiting workers")
        void roundTrip() {
            var held = lease("l-0000000000000001", PLAN, WorkerClass.WRITER);
            var idle = lease("l-0000000000000002", null, WorkerClass.READ_ONLY);
            var queue = List.of(waiting("l-0000000000000003", OTHER_PLAN, WorkerClass.WRITER),
                    waiting("l-0000000000000004", null, WorkerClass.ZERO_TOOL));

            store.update(_ -> new Revision<>(new Document(List.of(held, idle), queue), Boolean.TRUE));

            var document = store.snapshot();
            assertAll(
                    () -> assertEquals(List.of(held, idle), document.slots()),
                    () -> assertEquals(queue, document.waiting()),
                    () -> assertEquals(Form.FRESH, document.waiting().getFirst().form()),
                    () -> assertNull(document.waiting().getLast().planId()),
                    () -> assertEquals(WorkerClass.ZERO_TOOL, document.waiting().getLast().workerClass()));
        }

        @Test
        @DisplayName("is not written when the change returns it unchanged")
        void unchanged() {
            var outcome = store.update(document -> new Revision<>(document, Boolean.TRUE));

            assertAll(
                    () -> assertTrue(outcome),
                    () -> assertFalse(Files.exists(file)));
        }

        @Test
        @DisplayName("refuses two slots for one worker")
        void duplicateWorker() {
            var first = lease("l-0000000000000001", PLAN, WorkerClass.WRITER);
            var second = lease("l-0000000000000001", OTHER_PLAN, WorkerClass.WRITER);

            assertThrows(IllegalArgumentException.class,
                    () -> store.update(_ -> new Revision<>(new Document(List.of(first, second), List.of()), 1)));
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("accepts one writer of the same plan id in two different projects")
        void writersOfDifferentProjects() {
            var holder = new HolderInstance(4711, NOW);
            var first = new Lease(new LeaseOwner("project-a", ScopeType.PLAN, PLAN, holder, null),
                    "l-0000000000000001", "writer", WorkerClass.WRITER, Form.FRESH, PLAN, NOW, null);
            var second = new Lease(new LeaseOwner("project-b", ScopeType.PLAN, PLAN, holder, null),
                    "l-0000000000000002", "writer", WorkerClass.WRITER, Form.FRESH, PLAN, NOW, null);

            store.update(_ -> new Revision<>(new Document(List.of(first, second), List.of()), Boolean.TRUE));

            assertEquals(List.of(first, second), store.snapshot().slots());
        }

        @Test
        @DisplayName("refuses a plan that holds and awaits two writers")
        void twoWritersOfOnePlan() {
            var held = lease("l-0000000000000001", PLAN, WorkerClass.WRITER);
            var queued = waiting("l-0000000000000002", PLAN, WorkerClass.WRITER);

            assertThrows(IllegalArgumentException.class,
                    () -> store.update(_ -> new Revision<>(new Document(List.of(held), List.of(queued)), 1)));
        }
    }

    @Nested
    @DisplayName("a refused store")
    class RefusedStore {

        @Test
        @DisplayName("in another format version is refused and left byte-identical")
        void newerFormat() throws Exception {
            var newer = "{\"format_version\":2,\"slots\":[],\"waiting\":[]}".getBytes(UTF_8);
            Files.write(file, newer);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
            assertEquals(new String(newer, UTF_8), Files.readString(file));
        }

        @Test
        @DisplayName("that holds one worker twice is refused on read")
        void duplicateWorkerInTheFile() throws Exception {
            var owner = "{\"project_id\":\"pm-mcp-core\",\"scope_type\":\"plan\",\"scope_id\":\"some-plan\","
                    + "\"holder_instance\":{\"runtime_pid\":4711,"
                    + "\"runtime_started_at\":\"2026-10-22T10:00:00Z\"},\"orphaned_at\":null}";
            var first = "{\"owner\":" + owner + ",\"job_id\":\"l-0000000000000001\",\"role\":\"writer\","
                    + "\"class\":\"writer\",\"form\":\"warm\",\"plan_id\":\"some-plan\","
                    + "\"acquired_at\":\"2026-10-22T10:00:00Z\",\"lease_expires_at\":null}";
            var second = first.replace("some-plan", "other-plan");
            Files.writeString(file, "{\"format_version\":1,\"slots\":[" + first + "," + second
                    + "],\"waiting\":[]}", UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }

        @Test
        @DisplayName("that holds a writer without a plan is refused on read")
        void writerWithoutPlanInTheFile() throws Exception {
            var owner = "{\"project_id\":\"pm-mcp-core\",\"scope_type\":\"plan\",\"scope_id\":\"some-plan\","
                    + "\"holder_instance\":{\"runtime_pid\":4711,"
                    + "\"runtime_started_at\":\"2026-10-22T10:00:00Z\"},\"orphaned_at\":null}";
            var slot = "{\"owner\":" + owner + ",\"job_id\":\"l-0000000000000001\",\"role\":\"writer\","
                    + "\"class\":\"writer\",\"form\":\"warm\",\"plan_id\":null,"
                    + "\"acquired_at\":\"2026-10-22T10:00:00Z\",\"lease_expires_at\":null}";
            Files.writeString(file, "{\"format_version\":1,\"slots\":[" + slot + "],\"waiting\":[]}", UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }

        @Test
        @DisplayName("that is unreadable is never an empty store")
        void unreadable() throws Exception {
            var torn = "{\"format_version\":1,\"slots\":[".getBytes(UTF_8);
            Files.write(file, torn);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
            assertThrows(StoreRefusal.class,
                    () -> store.update(document -> new Revision<>(document, Boolean.TRUE)));
            assertEquals(new String(torn, UTF_8), Files.readString(file));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a lock that is not a machine store lock is refused")
        void otherLockLevel() {
            var storeFile = new AtomicStoreFile(file, LockKey.plan(base, "pm-mcp-core", PLAN));
            var locks = new RecordingLockManager();

            assertThrows(IllegalArgumentException.class, () -> new ModelSlotStore(storeFile, locks));
        }

        @Test
        @DisplayName("names its file")
        void storePath() {
            assertEquals(file.toAbsolutePath().normalize(), store.store());
        }
    }
}
