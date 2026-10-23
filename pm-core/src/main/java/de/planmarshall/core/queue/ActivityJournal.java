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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;

/**
 * The activity journal of one queue task (Store Schemas § Activity Journal):
 * {@code plans/<plan_id>/tasks/activity/<queue_task_id>.jsonl}, and the same below {@code epics/<epic_id>/},
 * {@code workspace/} and the machine scope.
 * <p>
 * The journal is append-only. The runtime appends each entry as its line arrives, under the journal's own leaf lock
 * taken on the file, outside the scope's transaction journal and digest sidecar, like a job log. A torn last line is
 * ignored by a reader and repaired by the next writer. {@code seq} is never reused, so a client's cursor stays valid.
 * A journal holds at most the compiled bound of 5000 entries; an append beyond it rewrites the journal without its
 * oldest {@code text} entries, never a server entry, under the same lock.
 *
 * @since 0.1
 */
public final class ActivityJournal {

    /** The compiled bound of the entries of one journal. */
    public static final int MAX_ENTRIES = 5000;

    /** The directory of the journal of a scope, relative to the home of the scope. */
    public static final String DIRECTORY = "tasks/activity";

    private static final Pattern SCOPE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final byte NEWLINE = '\n';

    private final Path directory;
    private final LockManager lockManager;

    /**
     * @param directory   the {@code tasks/activity/} directory of the scope
     * @param lockManager the lock manager of the runtime
     */
    public ActivityJournal(Path directory, LockManager lockManager) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    /**
     * @param base        the base directory of the runtime
     * @param projectId   the project, or {@code null} for the machine scope
     * @param scope       the scope of the task
     * @param lockManager the lock manager of the runtime
     * @return the activity journal of the scope
     * @throws IllegalArgumentException if the project id or the scope id is not an identifier, or the scope needs a
     *                                  project and none is given
     */
    public static ActivityJournal ofScope(Path base, String projectId, QueueTaskRecord.Scope scope,
            LockManager lockManager) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(scope, "scope");
        var canonical = base.toAbsolutePath().normalize();
        return switch (scope.type()) {
            case PLAN -> project(canonical, projectId, "plans", scope.id(), lockManager);
            case EPIC -> project(canonical, projectId, "epics", scope.id(), lockManager);
            case WORKSPACE -> project(canonical, projectId, null, null, lockManager);
            case MACHINE -> new ActivityJournal(canonical.resolve("machine").resolve(DIRECTORY), lockManager);
        };
    }

    private static ActivityJournal project(Path base, String projectId, String home, String id,
            LockManager lockManager) {
        if (projectId == null || !SCOPE_ID.matcher(projectId).matches()) {
            throw new IllegalArgumentException("A task of the scope needs a valid project id: " + projectId);
        }
        var root = LockKey.projectStore(base, projectId);
        if (home != null) {
            if (id == null || !SCOPE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Not a valid scope id: " + id);
            }
            root = root.resolve(home).resolve(id);
        }
        return new ActivityJournal(root.resolve(DIRECTORY), lockManager);
    }

    /** @return the journal directory, absolute and normalized */
    public Path directory() {
        return directory;
    }

    /**
     * @param queueTaskId the task
     * @return the journal file of the task, absolute and normalized
     */
    public Path journalPath(String queueTaskId) {
        var id = QueueTaskId.parse(queueTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Not a queue task id: " + queueTaskId));
        return directory.resolve(id.value() + ".jsonl");
    }

    /**
     * Appends one entry under the journal's leaf lock. Its {@code seq} must be one above the last entry ever written.
     *
     * @param queueTaskId the task whose journal receives the entry
     * @param entry       the entry
     * @throws IllegalArgumentException if the sequence is not the next one; nothing was written then
     * @throws java.io.UncheckedIOException if the journal cannot be read or written
     */
    public void append(String queueTaskId, ActivityEntry entry) {
        var file = journalPath(queueTaskId);
        Objects.requireNonNull(entry, "entry");
        var key = new LockKey(LockLevel.LEAF, file, queueTaskId);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(key);
            var current = Files.exists(file) ? read(file) : List.<ActivityEntry>of();
            var expected = current.isEmpty() ? 1 : current.getLast().seq() + 1;
            if (entry.seq() != expected) {
                throw new IllegalArgumentException(
                        "The next entry of the journal has the seq %d, not %d".formatted(expected, entry.seq()));
            }
            Files.createDirectories(file.getParent());
            var entries = new ArrayList<>(current);
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES) {
                rewrite(file, compact(entries));
            } else {
                appendLine(file, ActivityEntryCodec.write(entry));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Activity journal cannot be written: " + file, e);
        }
    }

    /**
     * Reads the complete lines of a task's journal without a lock. A torn last line is ignored.
     *
     * @param queueTaskId the task
     * @return the entries, in sequence order
     * @throws java.io.UncheckedIOException if the journal exists and cannot be read
     */
    public List<ActivityEntry> entries(String queueTaskId) {
        var file = journalPath(queueTaskId);
        try {
            return Files.exists(file) ? read(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Activity journal cannot be read: " + file, e);
        }
    }

    private static List<ActivityEntry> read(Path file) throws IOException {
        var bytes = Files.readAllBytes(file);
        var entries = new ArrayList<ActivityEntry>();
        var start = 0;
        for (var index = 0; index < bytes.length; index++) {
            if (bytes[index] == NEWLINE) {
                entries.add(ActivityEntryCodec.read(file, Arrays.copyOfRange(bytes, start, index)));
                start = index + 1;
            }
        }
        return entries;
    }

    private static List<ActivityEntry> compact(List<ActivityEntry> entries) {
        var excess = entries.size() - MAX_ENTRIES;
        var kept = new ArrayList<ActivityEntry>();
        for (var entry : entries) {
            if (excess > 0 && entry.kind() == ActivityEntry.Kind.TEXT) {
                excess--;
            } else {
                kept.add(entry);
            }
        }
        return kept;
    }

    private static void appendLine(Path file, byte[] entry) throws IOException {
        Files.write(file, block(entry), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void rewrite(Path file, List<ActivityEntry> entries) throws IOException {
        var out = new ByteArrayOutputStream();
        for (var entry : entries) {
            out.writeBytes(block(ActivityEntryCodec.write(entry)));
        }
        Files.write(file, out.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static byte[] block(byte[] entry) {
        var block = Arrays.copyOf(entry, entry.length + 1);
        block[entry.length] = NEWLINE;
        return block;
    }
}
