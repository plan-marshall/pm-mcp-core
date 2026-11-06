/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The queue store: a record is written and read under the queue lock, the machine scope has its own queue, and a file
 * that is not a queue task record is no task.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Queue store")
class QueueStoreTest {

    private static String taskId() {
        return "qt-" + "%016x".formatted(Generators.longs().next());
    }

    @Nested
    @DisplayName("paths")
    class Paths {

        @Test
        @DisplayName("the project queue is projects/<id>/queue/ under the project queue lock")
        void project(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());

            assertAll(
                    () -> assertEquals(base.resolve("projects/project-a/queue").toAbsolutePath().normalize(),
                            store.directory()),
                    () -> assertEquals(LockLevel.QUEUE, store.lockKey().level()),
                    () -> assertEquals(base.resolve("projects/project-a/locks/queue.lock").toAbsolutePath()
                            .normalize(), store.lockKey().lockFile()));
        }

        @Test
        @DisplayName("the machine queue is machine/queue/ under machine/locks/queue.lock")
        void machine(@TempDir Path base) {
            var store = QueueStore.ofMachine(base, new TestLocks());

            assertAll(
                    () -> assertEquals(base.resolve("machine/queue").toAbsolutePath().normalize(), store.directory()),
                    () -> assertEquals(base.resolve("machine/locks/queue.lock").toAbsolutePath().normalize(),
                            store.lockKey().lockFile()));
        }
    }

    @Nested
    @DisplayName("records")
    class Records {

        @Test
        @DisplayName("a record is created and read back")
        void createAndRead(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var task = QueueFixtures.queued(taskId()).build();

            store.create(task);

            assertAll(
                    () -> assertTrue(Files.exists(store.recordPath(task.queueTaskId()))),
                    () -> assertEquals(task, store.read(task.queueTaskId()).orElseThrow()),
                    () -> assertEquals(List.of(task), store.records()));
        }

        @Test
        @DisplayName("a record that exists is not created twice")
        void duplicate(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var task = QueueFixtures.queued(taskId()).build();
            store.create(task);

            assertThrows(IllegalArgumentException.class, () -> store.create(task));
        }

        @Test
        @DisplayName("a record states the id of its file name")
        void idMismatch(@TempDir Path base) throws Exception {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var id = taskId();
            var otherId = taskId();
            var task = QueueFixtures.queued(id).build();
            Files.createDirectories(store.directory());
            Files.write(store.directory().resolve(otherId + ".json"),
                    QueueTaskRecordCodec.write(task));

            assertThrows(StoreRefusal.class, () -> store.read(otherId));
        }

        @Test
        @DisplayName("the listing names only queue task records")
        void listing(@TempDir Path base) throws Exception {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var id = taskId();
            store.create(QueueFixtures.queued(id).build());
            Files.writeString(store.directory().resolve("fences.json"), "{}");
            Files.writeString(store.directory().resolve("notes.txt"), "not a task");

            assertEquals(List.of(new QueueTaskId(id)),
                    List.copyOf(store.queueTaskIds()));
        }

        @Test
        @DisplayName("updateAll revises every record in one transaction and keeps the ids")
        void updateAll(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var first = QueueFixtures.queued(taskId()).build();
            var second = QueueFixtures.queued(taskId()).build();
            store.create(first);
            store.create(second);

            var after = store.updateAll(records -> records.stream()
                    .map(task -> task.withRole("reviewer"))
                    .toList());

            assertAll(
                    () -> assertEquals(List.of("reviewer", "reviewer"),
                            after.stream().map(QueueTaskRecord::role).toList()),
                    () -> assertEquals("reviewer", store.read(first.queueTaskId()).orElseThrow().role()));
        }

        @Test
        @DisplayName("updateAll refuses an invalid revision before it writes anything")
        void updateAllRefusesInvalidRecord(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var first = QueueFixtures.queued(taskId()).build();
            var second = QueueFixtures.queued(taskId()).build();
            store.create(first);
            store.create(second);

            assertThrows(IllegalArgumentException.class, () -> store.updateAll(records -> List.of(
                    records.getFirst().withStatus(QueueTaskRecord.Status.CLOSED),
                    records.getLast().withRole("reviewer"))));
            assertAll(
                    () -> assertEquals(first, store.read(first.queueTaskId()).orElseThrow()),
                    () -> assertEquals(second, store.read(second.queueTaskId()).orElseThrow()));
        }

        @Test
        @DisplayName("updateAll refuses a revision that drops or renames a record")
        void updateAllRefusesAnotherSet(@TempDir Path base) {
            var store = QueueStore.ofProject(base, "project-a", new TestLocks());
            var task = QueueFixtures.queued(taskId()).build();
            store.create(task);
            var renamed = taskId();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> store.updateAll(records -> List.of())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> store.updateAll(records -> List.of(task.withQueueTaskId(
                                    renamed)))));
        }
    }
}
