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


import de.planmarshall.core.store.LockKey;

/**
 * The second process of the two-process test: it acquires the lock of one plan through a {@link FileLockManager} of
 * its own, says so, and holds the lock until its standard input ends.
 * <p>
 * Its arguments are the base directory of the runtime, the project id and the plan id. The one line it writes to its
 * standard output is {@link #HELD}; it is the protocol with the process that started it and not a log message, so
 * it goes to the file descriptor itself. Standard input ends when the starting process closes it, and also when that
 * process dies, so this process does not outlive it. It then releases the lock and ends with exit code 0; every
 * failure ends it with an exception and another exit code.
 */
final class LockHolderProcess {

    /** The line that says the lock is held. */
    static final String HELD = "held";

    private LockHolderProcess() {
        // entry point only
    }

    /**
     * @param args the base directory of the runtime, the project id, the plan id
     * @throws IOException if standard input cannot be read
     */
    public static void main(String[] args) throws IOException {
        var key = LockKey.plan(Path.of(args[0]), args[1], args[2]);
        var standardOutput = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try (var transaction = new FileLockManager(new RecordingAuditSink()).openTransaction()) {
            transaction.acquire(key);
            standardOutput.println(HELD);
            System.in.transferTo(OutputStream.nullOutputStream());
        }
    }
}
