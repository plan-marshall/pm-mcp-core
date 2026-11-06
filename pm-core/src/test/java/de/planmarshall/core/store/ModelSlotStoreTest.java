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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
 * refused before they are written. The JSON store documents and the store file name are wire contracts.
 */
@EnableGeneratorController
@DisplayName("Model-slot store")
class ModelSlotStoreTest {

    @TempDir
    Path base;

    private Instant now;
    private int pid;
    private String project;
    private String plan;
    private String otherPlan;
    private String role;
    private List<String> jobIds;
    private Path file;
    private ModelSlotStore store;

    private static String name(String prefix) {
        return prefix + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private LeaseOwner owner(String planId) {
        var scope = planId == null ? ScopeType.WORKSPACE : ScopeType.PLAN;
        return new LeaseOwner(project, scope, planId, new HolderInstance(pid, now), null);
    }

    private Lease lease(String jobId, String planId, WorkerClass workerClass) {
        return new Lease(owner(planId), jobId, role, workerClass, Form.WARM, planId, now, null);
    }

    private Waiting waiting(String jobId, String planId, WorkerClass workerClass) {
        return new Waiting(owner(planId), jobId, role, workerClass, Form.FRESH, planId, now);
    }

    @BeforeEach
    void createStore() throws IOException {
        now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        pid = Generators.integers(1, 99_999).next();
        project = name("project-");
        plan = name("plan-");
        otherPlan = name("other-");
        role = Generators.letterStrings(2, 20).next().toLowerCase(Locale.ROOT);
        var first = Generators.longs(0, Long.MAX_VALUE - 4).next();
        jobIds = List.of(jobId(first), jobId(first + 1), jobId(first + 2), jobId(first + 3));
        // The store file name is the layout contract of the machine lease store.
        file = Files.createDirectories(base.resolve("state")).resolve("model-slots.json");
        store = new ModelSlotStore(new AtomicStoreFile(file, LockKey.modelSlots(base)), new RecordingLockManager());
    }

    private static String jobId(long value) {
        return "l-%016x".formatted(value);
    }

    @Nested
    @DisplayName("the document")
    class DocumentRoundTrip {

        @Test
        @DisplayName("is written and read back with its slots and waiting workers")
        void roundTrip() {
            var held = lease(jobIds.get(0), plan, WorkerClass.WRITER);
            var idle = lease(jobIds.get(1), null, WorkerClass.READ_ONLY);
            var queue = List.of(waiting(jobIds.get(2), otherPlan, WorkerClass.WRITER),
                    waiting(jobIds.get(3), null, WorkerClass.ZERO_TOOL));

            store.update(_ -> new Revision<>(new Document(List.of(held, idle), queue), Boolean.TRUE));

            var document = store.snapshot();
            assertAll(
                    () -> assertEquals(List.of(held, idle), document.slots(), "the slots should round-trip"),
                    () -> assertEquals(queue, document.waiting(), "the waiting workers should round-trip"),
                    () -> assertEquals(Form.FRESH, document.waiting().getFirst().form(),
                            "the waiting form should round-trip"),
                    () -> assertNull(document.waiting().getLast().planId(),
                            "a workspace waiter should have no plan"),
                    () -> assertEquals(WorkerClass.ZERO_TOOL, document.waiting().getLast().workerClass(),
                            "the worker class should round-trip"));
        }

        @Test
        @DisplayName("is not written when the change returns it unchanged")
        void unchanged() {
            var outcome = store.update(document -> new Revision<>(document, Boolean.TRUE));

            assertAll(
                    () -> assertTrue(outcome, "the change's outcome should be returned"),
                    () -> assertFalse(Files.exists(file), "an unchanged document should not be written"));
        }

        @Test
        @DisplayName("refuses two slots for one worker")
        void duplicateWorker() {
            var first = lease(jobIds.get(0), plan, WorkerClass.WRITER);
            var second = lease(jobIds.get(0), otherPlan, WorkerClass.WRITER);

            assertThrows(IllegalArgumentException.class,
                    () -> store.update(_ -> new Revision<>(new Document(List.of(first, second), List.of()), 1)),
                    "two slots of one worker should be refused");
            assertFalse(Files.exists(file), "a refused document should not be written");
        }

        @Test
        @DisplayName("accepts one writer of the same plan id in two different projects")
        void writersOfDifferentProjects() {
            var holder = new HolderInstance(pid, now);
            var first = new Lease(new LeaseOwner(name("project-a-"), ScopeType.PLAN, plan, holder, null),
                    jobIds.get(0), role, WorkerClass.WRITER, Form.FRESH, plan, now, null);
            var second = new Lease(new LeaseOwner(name("project-b-"), ScopeType.PLAN, plan, holder, null),
                    jobIds.get(1), role, WorkerClass.WRITER, Form.FRESH, plan, now, null);

            store.update(_ -> new Revision<>(new Document(List.of(first, second), List.of()), Boolean.TRUE));

            assertEquals(List.of(first, second), store.snapshot().slots(),
                    "both projects' writers should be held");
        }

        @Test
        @DisplayName("refuses a plan that holds and awaits two writers")
        void twoWritersOfOnePlan() {
            var held = lease(jobIds.get(0), plan, WorkerClass.WRITER);
            var queued = waiting(jobIds.get(1), plan, WorkerClass.WRITER);

            assertThrows(IllegalArgumentException.class,
                    () -> store.update(_ -> new Revision<>(new Document(List.of(held), List.of(queued)), 1)),
                    "a second writer of one plan should fail");
        }
    }

    @Nested
    @DisplayName("a refused store; the stored documents are wire documents")
    class RefusedStore {

        @Test
        @DisplayName("in another format version is refused and left byte-identical")
        void newerFormat() throws Exception {
            var newer = "{\"format_version\":2,\"slots\":[],\"waiting\":[]}".getBytes(UTF_8);
            Files.write(file, newer);

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a newer format version should fail");
            assertEquals(new String(newer, UTF_8), Files.readString(file),
                    "the refused store should stay unchanged");
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

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a worker held twice should be refused");
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

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a writer without a plan should fail");
        }

        @Test
        @DisplayName("that is unreadable is never an empty store")
        void unreadable() throws Exception {
            var torn = "{\"format_version\":1,\"slots\":[".getBytes(UTF_8);
            Files.write(file, torn);

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a torn store should fail on read");
            assertThrows(StoreRefusal.class,
                    () -> store.update(document -> new Revision<>(document, Boolean.TRUE)),
                    "a torn store should fail on update");
            assertEquals(new String(torn, UTF_8), Files.readString(file), "the torn store should stay unchanged");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a lock that is not a machine store lock is refused")
        void otherLockLevel() {
            var storeFile = new AtomicStoreFile(file, LockKey.plan(base, project, plan));
            var locks = new RecordingLockManager();

            assertThrows(IllegalArgumentException.class, () -> new ModelSlotStore(storeFile, locks),
                    "a plan lock should be refused for it");
        }

        @Test
        @DisplayName("names its file")
        void storePath() {
            assertEquals(file.toAbsolutePath().normalize(), store.store(), "the store should name its own file");
        }
    }
}
