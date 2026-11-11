/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;

/**
 * The second process of the two-process test: it acquires the lock of one plan, or of a leaf key, through a
 * {@link FileLockManager} of its own, says so, and holds the lock until its standard input ends.
 * <p>
 * Its arguments are the base directory of the runtime, the project id, the plan id and optionally a mode: none for the
 * plan key, or one of {@link #LEAF}, {@link #MAILBOX}, {@link #REPAIR}, {@link #READ} and {@link #JOURNAL}. In a leaf
 * mode it runs one store operation after acquiring the key, because a store write or read is what opens and closes a
 * channel on the guarded file; {@link #JOURNAL} takes the queue task id as a fifth argument. The one line it writes to
 * its standard output is {@link #HELD}; it is the protocol with the process that started it and not a log message, so
 * it goes to the file descriptor itself. Standard input ends when the starting process closes it, and also when that
 * process dies, so this process does not outlive it. It then releases the lock and ends with exit code 0; every failure
 * ends it with an exception and another exit code.
 * <p>
 * Every datum of this class is a contract: the lines {@link #HELD}, {@link #LEAF}, {@link #MAILBOX}, {@link #REPAIR},
 * {@link #READ} and {@link #JOURNAL} are the protocol with the starting process, and the project, plan and task ids
 * arrive as arguments from the test that drew them.
 */
final class LockHolderProcess {

    /** The line that says the lock is held. */
    static final String HELD = "held";

    /** The argument that selects the audit leaf key and appends one line through its store. */
    static final String LEAF = "leaf";

    /** The argument that selects a plan's mailbox leaf key and appends one line through its store. */
    static final String MAILBOX = "mailbox";

    /** The argument that selects the audit leaf key and repairs a torn tail of its store. */
    static final String REPAIR = "repair";

    /** The argument that selects the audit leaf key and reads the last line of its store. */
    static final String READ = "read";

    /** The argument that selects a journal-shaped leaf key and appends one line through its store. */
    static final String JOURNAL = "journal";

    /** The index of the mode among the arguments; the queue task id of {@link #JOURNAL} follows it. */
    private static final int MODE_ARGUMENT = 3;

    /** The line a leaf mode appends: the contract of a JSONL record, not generated data. */
    private static final byte[] APPENDED = "{\"format_version\":1,\"n\":1}\n".getBytes(StandardCharsets.UTF_8);

    private LockHolderProcess() {
        // entry point only
    }

    /**
     * @param args the base directory of the runtime, the project id, the plan id, optionally a mode, and for
     *             {@link #JOURNAL} the queue task id
     * @throws IOException if standard input cannot be read
     */
    @SuppressWarnings("resource") // stdout belongs to the process; closing it would close our own output
    public static void main(String[] args) throws IOException {
        var base = Path.of(args[0]);
        var mode = args.length > MODE_ARGUMENT ? args[MODE_ARGUMENT] : "";
        var key = key(base, args, mode);
        var standardOutput = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try (var transaction = new FileLockManager(new RecordingAuditSink()).openTransaction()) {
            transaction.acquire(key);
            operate(key, transaction, mode, args);
            standardOutput.println(HELD);
            System.in.transferTo(OutputStream.nullOutputStream());
        }
    }

    /**
     * The key a mode names. The journal-shaped key is derived from the layout of the task's journal, the shape
     * {@code ActivityJournal} builds: a leaf key on {@code tasks/activity/<queue_task_id>.jsonl}.
     */
    private static LockKey key(Path base, String[] args, String mode) {
        return switch (mode) {
            case LEAF, REPAIR, READ -> LockKey.auditLog(base);
            case MAILBOX -> LockKey.mailbox(base, args[1], args[2]);
            case JOURNAL -> {
                var taskId = args[MODE_ARGUMENT + 1];
                var journal = LockKey.projectStore(base, args[1]).resolve("plans").resolve(args[2])
                        .resolve("tasks").resolve("activity").resolve(taskId + ".jsonl");
                yield new LockKey(LockLevel.LEAF, journal, taskId);
            }
            default -> LockKey.plan(base, args[1], args[2]);
        };
    }

    /** Runs the one store operation of a leaf mode; the plan key runs none. */
    private static void operate(LockKey key, LockTransaction transaction, String mode, String[] args) {
        var store = new JsonlStoreFile(key.lockFile(), key);
        switch (mode) {
            case LEAF, MAILBOX, JOURNAL -> store.append(transaction, APPENDED, new RecordingAuditSink());
            case REPAIR -> store.repairTail(transaction, new RecordingAuditSink());
            case READ -> store.read(transaction);
            default -> {
                // the plan key holds its lock only
            }
        }
    }
}
