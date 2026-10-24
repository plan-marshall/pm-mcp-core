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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntFunction;
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
 * oldest {@code text} entries, never a server entry, under the same lock. A journal of more than the bound in server
 * entries alone keeps them: the bound bounds what is cut, and a server entry is never cut.
 *
 * @since 0.1
 */
public final class ActivityJournal {

    /** The compiled bound of the entries of one journal. */
    public static final int MAX_ENTRIES = 5000;

    /** The directory of the journal of a scope, relative to the home of the scope. */
    public static final String DIRECTORY_NAME = "tasks/activity";

    /** The label of an entry in a refusal. */
    private static final String ENTRY_LABEL = "entry";

    private static final Pattern SCOPE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final byte NEWLINE = '\n';

    private final Path directory;
    private final LockManager lockManager;
    private final int maxEntries;

    /**
     * @param directory   the {@code tasks/activity/} directory of the scope
     * @param lockManager the lock manager of the runtime
     */
    public ActivityJournal(Path directory, LockManager lockManager) {
        this(directory, lockManager, MAX_ENTRIES);
    }

    /**
     * @param directory   the {@code tasks/activity/} directory of the scope
     * @param lockManager the lock manager of the runtime
     * @param maxEntries  the compiled bound of the journal's entries
     */
    ActivityJournal(Path directory, LockManager lockManager, int maxEntries) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (maxEntries < 1) {
            throw new IllegalArgumentException("The bound of a journal counts from 1: " + maxEntries);
        }
        this.maxEntries = maxEntries;
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
            case WORKSPACE -> project(canonical, projectId, "workspace", null, lockManager);
            case MACHINE -> new ActivityJournal(canonical.resolve("machine").resolve(DIRECTORY_NAME), lockManager);
        };
    }

    private static ActivityJournal project(Path base, String projectId, String home, String id,
            LockManager lockManager) {
        if (projectId == null || !SCOPE_ID.matcher(projectId).matches()) {
            throw new IllegalArgumentException("A task of the scope needs a valid project id: " + projectId);
        }
        var root = LockKey.projectStore(base, projectId);
        if (home != null) {
            root = root.resolve(home);
            if (id != null) {
                if (!SCOPE_ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("Not a valid scope id: " + id);
                }
                root = root.resolve(id);
            }
        }
        return new ActivityJournal(root.resolve(DIRECTORY_NAME), lockManager);
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
        Objects.requireNonNull(entry, ENTRY_LABEL);
        appendNext(queueTaskId, expected -> {
            if (entry.seq() != expected) {
                throw new IllegalArgumentException(
                        "The next entry of the journal has the seq %d, not %d".formatted(expected, entry.seq()));
            }
            return entry;
        });
    }

    /**
     * Appends one entry whose sequence the journal assigns under its leaf lock: the entry builder receives the sequence
     * one above the last entry ever written, so two concurrent appends cannot compute the same sequence.
     *
     * @param queueTaskId the task whose journal receives the entry
     * @param entry       builds the entry from its assigned sequence
     * @throws java.io.UncheckedIOException if the journal cannot be read or written
     */
    public void appendNext(String queueTaskId, IntFunction<ActivityEntry> entry) {
        var file = journalPath(queueTaskId);
        Objects.requireNonNull(entry, ENTRY_LABEL);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Activity journal directory cannot be created: " + file.getParent(), e);
        }
        var key = new LockKey(LockLevel.LEAF, lockFile(file), queueTaskId);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(key);
            var current = Files.exists(file) ? read(file) : List.<ActivityEntry>of();
            var expected = current.isEmpty() ? 1 : current.getLast().seq() + 1;
            var built = Objects.requireNonNull(entry.apply(expected), ENTRY_LABEL);
            if (built.seq() != expected) {
                throw new IllegalArgumentException(
                        "The next entry of the journal has the seq %d, not %d".formatted(expected, built.seq()));
            }
            truncateTornTail(file);
            var entries = new ArrayList<>(current);
            entries.add(built);
            if (entries.size() > maxEntries) {
                rewrite(file, compact(entries));
            } else {
                appendLine(file, ActivityEntryCodec.write(built));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Activity journal cannot be written: " + file, e);
        }
    }

    /**
     * The lock of a journal is a sibling file, not the journal itself: a bounded journal is replaced by an atomic
     * rename, which replaces the inode, and a lock on the old inode would exclude nobody
     * (link:../../adr/core-queue/006-the-journal-lock-is-a-sibling-file.adoc[ADR 006]).
     */
    private static Path lockFile(Path journal) {
        return journal.resolveSibling(journal.getFileName() + ".lock");
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

    private List<ActivityEntry> compact(List<ActivityEntry> entries) {
        var excess = entries.size() - maxEntries;
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

    /**
     * Cuts a torn last line, so the next append starts at a line boundary. The reader ignores such a tail already; this
     * is the repair the specification gives the next writer.
     */
    private static void truncateTornTail(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        var bytes = Files.readAllBytes(file);
        for (var index = bytes.length - 1; index >= 0; index--) {
            if (bytes[index] == NEWLINE) {
                if (index + 1 < bytes.length) {
                    truncateTo(file, (long) index + 1);
                }
                return;
            }
        }
        if (bytes.length > 0) {
            truncateTo(file, 0);
        }
    }

    private static void truncateTo(Path file, long size) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }

    private static void appendLine(Path file, byte[] entry) throws IOException {
        Files.write(file, block(entry), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Replaces the journal by an atomic write-and-rename beside it, so a reader sees the old journal or the new one and
     * a stop mid-write loses neither.
     */
    private static void rewrite(Path file, List<ActivityEntry> entries) throws IOException {
        var out = new ByteArrayOutputStream();
        for (var entry : entries) {
            out.writeBytes(block(ActivityEntryCodec.write(entry)));
        }
        var temporary = file.resolveSibling(file.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            Files.write(temporary, out.toByteArray(), StandardOpenOption.CREATE_NEW);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] block(byte[] entry) {
        var block = Arrays.copyOf(entry, entry.length + 1);
        block[entry.length] = NEWLINE;
        return block;
    }
}
