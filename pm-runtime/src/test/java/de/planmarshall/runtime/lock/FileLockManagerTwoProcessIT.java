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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


import de.planmarshall.core.store.LockKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The lock of the operating system that the lock manager takes on a lock file, seen from a second process
 * (PM-IMPL-7). {@link FileLockManagerTest} can only probe a lock from inside the process that holds it; here
 * {@link LockHolderProcess} holds the key in a JVM of its own, started with the java and the class path of this one.
 * <p>
 * The other process is ended after every test, also after a failed one, and never waited for without a bound.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("File lock manager across two processes")
class FileLockManagerTwoProcessIT {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";

    /** Covers the start of a JVM on a loaded build machine. */
    private static final Duration PROCESS_BOUND = Duration.ofSeconds(30);

    /** How long an acquisition is watched that must not come back while the other process holds the key. */
    private static final Duration HELD_OBSERVATION = Duration.ofMillis(500);

    @TempDir
    Path base;

    private Process holder;

    @AfterEach
    void endHolder() throws IOException, InterruptedException {
        if (holder == null) {
            return;
        }
        holder.getOutputStream().close();
        if (!holder.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS)) {
            holder.destroyForcibly();
            holder.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Starts the other process and returns once it has said that it holds the lock of the plan. */
    private void startHolder() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                LockHolderProcess.class.getName(), base.toString(), PROJECT, PLAN)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        var reader = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        // Reading the line cannot be interrupted, so it is waited for from outside; ending the process ends the read
        var line = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).get(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(LockHolderProcess.HELD, line, "the other process did not report that it holds the lock");
    }

    /** Lets the other process release the lock and end, and returns its exit code. */
    private int releaseHolder() throws IOException, InterruptedException {
        holder.getOutputStream().close();
        assertTrue(holder.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS),
                "the other process did not end after its standard input was closed");
        return holder.exitValue();
    }

    /** @return whether a lock of the operating system on the file is refused to this process */
    private static boolean lockedByAnotherProcess(Path file) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            var lock = channel.tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
            return false;
        }
    }

    @Test
    @DisplayName("a key held in another process locks its lock file for this process until that process releases it")
    void lockFileIsLockedForThisProcess() throws Exception {
        var key = LockKey.plan(base, PROJECT, PLAN);
        startHolder();

        var lockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var lockedAfterRelease = lockedByAnotherProcess(key.lockFile());

        assertAll(
                () -> assertTrue(lockedWhileHeld, "locked while the other process holds the key"),
                () -> assertEquals(0, exitCode, "exit code of the other process"),
                () -> assertFalse(lockedAfterRelease, "locked after the other process released the key"));
    }

    @Test
    @DisplayName("a transaction of this process waits for a key held in another process and gets it on its release")
    void transactionWaitsForTheOtherProcess() throws Exception {
        var key = LockKey.plan(base, PROJECT, PLAN);
        var manager = new FileLockManager(new RecordingAuditSink());
        var acquired = new AtomicBoolean();
        startHolder();
        var waiting = Thread.ofPlatform().daemon().start(() -> {
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);
                acquired.set(true);
            }
        });

        var endedWhileHeld = waiting.join(HELD_OBSERVATION);
        var exitCode = releaseHolder();
        var endedAfterRelease = waiting.join(PROCESS_BOUND);

        assertAll(
                () -> assertFalse(endedWhileHeld, "acquisition came back while the other process holds the key"),
                () -> assertEquals(0, exitCode, "exit code of the other process"),
                () -> assertTrue(endedAfterRelease, "acquisition came back after the other process released the key"),
                () -> assertTrue(acquired.get(), "key acquired"),
                () -> assertFalse(lockedByAnotherProcess(key.lockFile()), "locked after both released the key"));
    }
}
