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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The task queue of one scope (Store Schemas § Task Queue): one queue task record {@code
 * queue/<queue_task_id>.json} per task, in the project's queue {@code projects/<project_id>/queue/} or in the
 * machine's own queue {@code machine/queue/}.
 * <p>
 * Every write is one short transaction on the queue lock ({@code locks/queue.lock} for a project, {@code
 * machine/locks/queue.lock} for the machine), as an atomic write-and-rename of the one record a transition changes. A
 * decision that reads several records and writes some of them runs through {@link #updateAll(UnaryOperator)} in one
 * transaction, so no second writer comes between the read and the write.
 * <p>
 * A read takes no lock. A store that does not exist is empty, and a file whose name is not a queue task id is no
 * record. The directory is created by the first write.
 *
 * @since 0.1
 */
public final class QueueStore {

    /** The name of the queue directory below its store home. */
    public static final String DIRECTORY_NAME = "queue";

    private final Path directory;
    private final LockKey lockKey;
    private final LockManager lockManager;

    /**
     * @param directory   the queue directory
     * @param lockKey     the key of the queue lock that guards it
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the lock is not a queue lock
     */
    public QueueStore(Path directory, LockKey lockKey, LockManager lockManager) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (lockKey.level() != LockLevel.QUEUE) {
            throw new IllegalArgumentException("A queue store is guarded by a queue lock: " + lockKey.lockFile());
        }
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param locks     the lock manager of the runtime
     * @return the task queue of the project, {@code projects/<project_id>/queue/}
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public static QueueStore ofProject(Path base, String projectId, LockManager locks) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(locks, "locks");
        return new QueueStore(LockKey.projectStore(base, projectId).resolve(DIRECTORY_NAME),
                LockKey.queue(base, projectId), locks);
    }

    /**
     * @param base  the base directory of the runtime
     * @param locks the lock manager of the runtime
     * @return the task queue of the machine scope, {@code machine/queue/}
     */
    public static QueueStore ofMachine(Path base, LockManager locks) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(locks, "locks");
        var canonical = base.toAbsolutePath().normalize();
        return new QueueStore(canonical.resolve("machine").resolve(DIRECTORY_NAME), LockKey.machineQueue(base),
                locks);
    }

    /** @return the queue directory, absolute and normalized */
    public Path directory() {
        return directory;
    }

    /** @return the key of the queue lock */
    public LockKey lockKey() {
        return lockKey;
    }

    /**
     * @param queueTaskId a task
     * @return the record file of the task, absolute and normalized
     */
    public Path recordPath(String queueTaskId) {
        var id = QueueTaskId.parse(queueTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Not a queue task id: " + queueTaskId));
        return directory.resolve(id.recordFileName());
    }

    /**
     * Writes the first record of a task under the queue lock. A record of the task must not exist.
     *
     * @param task the record
     * @throws IllegalArgumentException if the task already exists
     * @throws StoreRefusal             if the record that exists is unreadable
     * @throws UncheckedIOException     if the record cannot be written
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public void create(QueueTaskRecord task) {
        Objects.requireNonNull(task, "task");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            if (read(task.queueTaskId()).isPresent()) {
                throw new IllegalArgumentException("The queue task %s exists already".formatted(task.queueTaskId()));
            }
            write(transaction, task);
        }
    }

    /**
     * Revises every record of the store in one transaction, under the queue lock: the revision receives the records
     * as they are and returns the records to keep, in the same order and with the same ids. The store is written only
     * for a record that changed.
     *
     * @param revision what becomes of the records
     * @return the records after the revision, in id order
     * @throws StoreRefusal         if a record is in another format version or unreadable
     * @throws UncheckedIOException if the queue or a record cannot be read or written
     * @throws InternalFaultException if the lock must not be acquired after the locks the transaction holds
     */
    public List<QueueTaskRecord> updateAll(Function<List<QueueTaskRecord>, List<QueueTaskRecord>> revision) {
        Objects.requireNonNull(revision, "revision");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            var before = readAll();
            var after = new ArrayList<>(Objects.requireNonNull(revision.apply(List.copyOf(before)), "revision"));
            if (after.size() != before.size()) {
                throw new IllegalArgumentException(
                        "A revision keeps every record of the queue: %d before, %d after"
                                .formatted(before.size(), after.size()));
            }
            for (var index = 0; index < after.size(); index++) {
                if (!after.get(index).queueTaskId().equals(before.get(index).queueTaskId())) {
                    throw new IllegalArgumentException(
                            "A revision keeps the id of every record: '%s' became '%s'"
                                    .formatted(before.get(index).queueTaskId(), after.get(index).queueTaskId()));
                }
            }
            after.forEach(QueueTaskRecord::validate);
            for (var index = 0; index < after.size(); index++) {
                if (!after.get(index).equals(before.get(index))) {
                    write(transaction, after.get(index));
                }
            }
            return List.copyOf(after);
        }
    }

    /**
     * Reads a record without a lock. A record is readable only if the {@code queue_task_id} it states is the id its
     * file name carries.
     *
     * @param queueTaskId the task
     * @return the record; empty if it does not exist
     * @throws StoreRefusal         if the record is in another format version, unreadable, or states another id than
     *                              the one of its file name
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public Optional<QueueTaskRecord> read(String queueTaskId) {
        var file = recordPath(queueTaskId);
        return StoreSnapshot.read(file).content().map(document -> {
            var task = QueueTaskRecordCodec.read(file, document);
            if (!task.queueTaskId().equals(queueTaskId)) {
                throw StoreRefusal.unreadable(file, QueueTaskRecord.FORMAT_VERSION,
                        "'queue_task_id' is '%s', not the '%s' of the file name"
                                .formatted(task.queueTaskId(), queueTaskId),
                        null);
            }
            return task;
        });
    }

    /**
     * Lists the tasks without a lock: every file {@code <queue_task_id>.json} of the queue whose name parses as an id.
     *
     * @return the tasks, sorted by id; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<QueueTaskId> queueTaskIds() {
        var ids = new TreeSet<>(Comparator.comparing(QueueTaskId::value));
        try (var entries = Files.list(directory)) {
            entries.filter(entry -> Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    .map(entry -> QueueTaskId.ofRecordFileName(entry.getFileName().toString()))
                    .flatMap(Optional::stream).forEach(ids::add);
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Queue task records cannot be listed: " + directory, e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * Reads every task without a lock, in id order.
     *
     * @return the records; empty if the queue does not exist
     * @throws StoreRefusal         if a record is in another format version or unreadable
     * @throws UncheckedIOException if the directory or a record cannot be read
     */
    public List<QueueTaskRecord> records() {
        return queueTaskIds().stream().map(id -> read(id.value())).flatMap(Optional::stream).toList();
    }

    private List<QueueTaskRecord> readAll() {
        return queueTaskIds().stream().map(id -> read(id.value())).flatMap(Optional::stream).toList();
    }

    private void write(LockTransaction transaction, QueueTaskRecord task) {
        task.validate();
        createDirectory();
        new AtomicStoreFile(recordPath(task.queueTaskId()), lockKey).write(transaction,
                QueueTaskRecordCodec.write(task));
    }

    private void createDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Queue directory cannot be created: " + directory, e);
        }
    }
}
