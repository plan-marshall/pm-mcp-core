/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;


import de.planmarshall.api.MachinePaths;

/**
 * The child of the killed-runtime tests ({@link KilledRuntimeFixture}): it runs one named lifecycle step against the
 * {@code PM_MCP_BASE} it is given, writes a marker line before the step and one inside it, and then blocks so that
 * it can be killed in the middle of the step.
 * <p>
 * Its arguments are the mode, which names the step, and the base directory. The marker lines are the protocol with
 * the test that started it and not log messages, so they go to the file descriptor itself. A blocked child waits for
 * the end of its standard input, which comes when the test JVM dies; it then halts at once, so it never finishes a
 * step on its own and never outlives the test.
 * <p>
 * Modes:
 * <ul>
 * <li>{@value #STARTUP}: steps 1 to 5 of the {@link StartupSequence}, blocked inside step 4 while the lock is held
 * and before the token is written.</li>
 * <li>{@value #STARTUP_READY}: steps 1 to 5 run to {@link StartupSequence.Outcome.Ready}; blocked after them with the
 * lock held and the token written.</li>
 * </ul>
 */
final class RuntimeChildProcess {

    /** Mode: blocked inside step 4 of the startup sequence. */
    static final String STARTUP = "startup";

    /** Mode: blocked after the startup sequence reached ready. */
    static final String STARTUP_READY = "startup-ready";

    /** Exit code of a child whose standard input ended while it was blocked. */
    static final int EXIT_ORPHANED = 3;

    private static final String BEFORE = "before";
    private static final String INSIDE = "inside";
    private static final String READY = "ready";

    private RuntimeChildProcess() {
        // entry point only
    }

    /**
     * @param mode the mode
     * @return the line written before the step of the mode
     */
    static String before(String mode) {
        return BEFORE + " " + mode;
    }

    /**
     * @param mode the mode
     * @return the line written inside the step of the mode
     */
    static String inside(String mode) {
        return INSIDE + " " + mode;
    }

    /**
     * @param mode the mode
     * @return the line written when the step of the mode has reached its end state
     */
    static String ready(String mode) {
        return READY + " " + mode;
    }

    /**
     * @param args the mode and the base directory of the runtime
     * @throws IOException if the step fails
     */
    public static void main(String[] args) throws IOException {
        var mode = args[0];
        var paths = new MachinePaths(Path.of(args[1]), MachinePaths.Os.current());
        var markers = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        switch (mode) {
            case STARTUP -> startup(paths, markers, () -> {
                markers.println(inside(STARTUP));
                return awaitKill();
            }, STARTUP);
            case STARTUP_READY -> startup(paths, markers, AuditChainVerification.NONE, STARTUP_READY);
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    private static void startup(MachinePaths paths, PrintStream markers, AuditChainVerification auditChain,
            String mode) throws IOException {
        markers.println(before(mode));
        var outcome = new StartupSequence(paths, System.getProperty("user.name"), auditChain, _ -> {
        }).run(new SecureRandom());
        if (outcome instanceof StartupSequence.Outcome.Ready) {
            markers.println(ready(mode));
            awaitKill();
        }
        throw new IllegalStateException("startup did not reach ready: " + outcome);
    }

    /** Blocks until the child is killed; halts at once when standard input ends first. */
    private static <T> T awaitKill() {
        try {
            System.in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().halt(EXIT_ORPHANED);
        throw new IllegalStateException("halt returned");
    }
}
