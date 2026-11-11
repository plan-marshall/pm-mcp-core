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
import java.util.Locale;
import java.util.Optional;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;

/**
 * The second process of the two-process test: it acquires the lock of one plan, or of a leaf key, through a
 * {@link FileLockManager} of its own, runs the one store operation of its mode, says so, and holds the lock until its
 * standard input ends.
 * <p>
 * Its arguments are the base directory of the runtime, the project id, the plan id and optionally a {@link Mode}: none
 * for the plan key, or {@link #LEAF}, {@link #MAILBOX}, {@link #REPAIR}, {@link #READ} and {@link #JOURNAL}. In a leaf
 * mode it runs one store operation after acquiring the key, because a store write or read is what opens and closes a
 * channel on the guarded file; {@link #JOURNAL} takes the queue task id as a fifth argument. The lines it writes to its
 * standard output are the protocol with the process that started it and not log messages, so they go to the file
 * descriptor itself: the {@link #READ} mode first reports how many complete lines it found ({@link #LINES_PREFIX}), and
 * every mode then writes {@link #HELD}. Standard input ends when the starting process closes it, and also when that
 * process dies, so this process does not outlive it. It then releases the lock and ends with exit code 0; every failure
 * ends it with an exception and another exit code.
 * <p>
 * Every datum of this class is a contract: the protocol lines {@link #HELD}, {@link #LEAF}, {@link #MAILBOX},
 * {@link #REPAIR}, {@link #READ} and {@link #JOURNAL} are the protocol with the starting process, and the project, plan
 * and task ids arrive as arguments from the test that drew them.
 */
final class LockHolderProcess {

    /** The line that says the lock is held. */
    static final String HELD = "held";

    /** The argument that selects the audit leaf key and appends one line through its store. */
    static final String LEAF = Mode.LEAF.argument();

    /** The argument that selects a plan's mailbox leaf key and appends one line through its store. */
    static final String MAILBOX = Mode.MAILBOX.argument();

    /** The argument that selects the audit leaf key and repairs a torn tail of its store. */
    static final String REPAIR = Mode.REPAIR.argument();

    /** The argument that selects the audit leaf key and reads the last line of its store. */
    static final String READ = Mode.READ.argument();

    /** The argument that selects a journal-shaped leaf key and appends one line through its store. */
    static final String JOURNAL = Mode.JOURNAL.argument();

    /** The prefix of the line that reports how many complete lines a {@link #READ} found. */
    static final String LINES_PREFIX = "lines:";

    /** The index of the mode among the arguments; the queue task id of {@link #JOURNAL} follows it. */
    private static final int MODE_ARGUMENT = 3;

    /** The line a leaf mode appends: the contract of a JSONL record, not generated data. */
    private static final byte[] APPENDED = "{\"format_version\":1,\"n\":1}\n".getBytes(StandardCharsets.UTF_8);

    private LockHolderProcess() {
        // entry point only
    }

    /**
     * The mode of the child: one definition of the protocol argument, the key it selects and the store operation it
     * runs. Both switches below are exhaustive over it, so a mode added here and handled in one place only does not
     * compile.
     */
    private enum Mode {

        /** The plan's lock, with no store operation. */
        PLAN,

        /** The audit leaf key, with one append. */
        LEAF,

        /** The plan's mailbox leaf key, with one append. */
        MAILBOX,

        /** The audit leaf key, with a torn-tail repair. */
        REPAIR,

        /** The audit leaf key, with a read of the store. */
        READ,

        /** A journal-shaped leaf key, with one append. */
        JOURNAL;

        /** @return the protocol argument of the mode, its name in lower case */
        String argument() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** @return the mode the argument names, or empty for the plan key and an unknown argument */
        static Optional<Mode> of(String argument) {
            for (var mode : values()) {
                if (mode.argument().equals(argument)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * @param args the base directory of the runtime, the project id, the plan id, optionally a mode, and for
     *             {@link #JOURNAL} the queue task id
     * @throws IOException if standard input cannot be read
     */
    @SuppressWarnings("resource") // stdout belongs to the process; closing it would close our own output
    public static void main(String[] args) throws IOException {
        var base = Path.of(args[0]);
        var mode = Mode.of(args.length > MODE_ARGUMENT ? args[MODE_ARGUMENT] : "").orElse(Mode.PLAN);
        var key = key(base, args, mode);
        var standardOutput = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try (var transaction = new FileLockManager(new RecordingAuditSink()).openTransaction()) {
            transaction.acquire(key);
            operate(key, transaction, mode).ifPresent(standardOutput::println);
            standardOutput.println(HELD);
            System.in.transferTo(OutputStream.nullOutputStream());
        }
    }

    /**
     * The key a mode names. The journal-shaped key is derived from the layout of the task's journal, the shape
     * {@code ActivityJournal} builds: a leaf key on {@code tasks/activity/<queue_task_id>.jsonl}.
     */
    private static LockKey key(Path base, String[] args, Mode mode) {
        return switch (mode) {
            case PLAN -> LockKey.plan(base, args[1], args[2]);
            case LEAF, REPAIR, READ -> LockKey.auditLog(base);
            case MAILBOX -> LockKey.mailbox(base, args[1], args[2]);
            case JOURNAL -> {
                var taskId = args[MODE_ARGUMENT + 1];
                var journal = LockKey.projectStore(base, args[1]).resolve("plans").resolve(args[2])
                        .resolve("tasks").resolve("activity").resolve(taskId + ".jsonl");
                yield new LockKey(LockLevel.LEAF, journal, taskId);
            }
        };
    }

    /**
     * Runs the store operation of the mode.
     *
     * @return the line that reports what the operation did, or empty when the mode reports nothing
     */
    private static Optional<String> operate(LockKey key, LockTransaction transaction, Mode mode) {
        var store = new JsonlStoreFile(key.lockFile(), key);
        return switch (mode) {
            case PLAN -> Optional.empty();
            case LEAF, MAILBOX, JOURNAL -> {
                store.append(transaction, APPENDED, new RecordingAuditSink());
                yield Optional.empty();
            }
            case REPAIR -> {
                store.repairTail(transaction, new RecordingAuditSink());
                yield Optional.empty();
            }
            case READ -> Optional.of(LINES_PREFIX + store.read(transaction).lines().size());
        };
    }
}
