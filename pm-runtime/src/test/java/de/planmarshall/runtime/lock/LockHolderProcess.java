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

import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;

/**
 * The second process of the two-process test: it acquires the lock of one plan, or of the audit leaf key, through a
 * {@link FileLockManager} of its own, says so, and holds the lock until its standard input ends.
 * <p>
 * Its arguments are the base directory of the runtime, the project id, the plan id and, for the leaf mode,
 * {@link #LEAF}. In the leaf mode it appends one line to the audit log after acquiring the key, because a store write
 * is what opens and closes a channel on the guarded file. The one line it writes to its standard output is
 * {@link #HELD}; it is the protocol with the process that started it and not a log message, so it goes to the file
 * descriptor itself. Standard input ends when the starting process closes it, and also when that process dies, so
 * this process does not outlive it. It then releases the lock and ends with exit code 0; every failure ends it with
 * an exception and another exit code.
 * <p>
 * Every datum of this class is a contract: the lines {@link #HELD} and {@link #LEAF} are the protocol with the
 * starting process, and the project and plan ids arrive as arguments from the test that drew them.
 */
final class LockHolderProcess {

    /** The line that says the lock is held. */
    static final String HELD = "held";

    /** The argument that selects the audit leaf key and appends one line through its store. */
    static final String LEAF = "leaf";

    /** The line the leaf mode appends: the contract of a JSONL record, not generated data. */
    private static final byte[] APPENDED = "{\"format_version\":1,\"n\":1}\n".getBytes(StandardCharsets.UTF_8);

    private LockHolderProcess() {
        // entry point only
    }

    /**
     * @param args the base directory of the runtime, the project id, the plan id and optionally {@link #LEAF}
     * @throws IOException if standard input cannot be read
     */
    @SuppressWarnings("resource") // stdout belongs to the process; closing it would close our own output
    public static void main(String[] args) throws IOException {
        var base = Path.of(args[0]);
        var leaf = args.length > 3 && LEAF.equals(args[3]);
        var key = leaf ? LockKey.auditLog(base) : LockKey.plan(base, args[1], args[2]);
        var standardOutput = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try (var transaction = new FileLockManager(new RecordingAuditSink()).openTransaction()) {
            transaction.acquire(key);
            if (leaf) {
                new JsonlStoreFile(key.lockFile(), key).append(transaction, APPENDED, new RecordingAuditSink());
            }
            standardOutput.println(HELD);
            System.in.transferTo(OutputStream.nullOutputStream());
        }
    }
}
