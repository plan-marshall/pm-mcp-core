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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;

/**
 * The lock of the operating system that the lock manager takes on a lock file, seen from a second process
 * (PM-IMPL-7). {@link FileLockManagerTest} can only probe a lock from inside the process that holds it; here
 * {@link LockHolderProcess} holds the key in a JVM of its own, started with the java and the class path of this one.
 * <p>
 * The other process is ended after every test, also after a failed one, and never waited for without a bound.
 * <p>
 * The project and plan ids are generated and passed to the other process. The literals that stay are the contract:
 * the child-process protocol (its arguments, the {@link LockHolderProcess#HELD} line and the
 * {@link LockHolderProcess#LEAF} mode), its exit code 0 and the process and observation bounds.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("File lock manager across two processes")
class FileLockManagerTwoProcessIT {

    private String projectId;
    private String planId;

    /** Covers the start of a JVM on a loaded build machine. */
    private static final Duration PROCESS_BOUND = Duration.ofSeconds(30);

    /** How long an acquisition is watched that must not come back while the other process holds the key. */
    private static final Duration HELD_OBSERVATION = Duration.ofMillis(500);

    /** A complete line and a torn tail of a JSONL store: the contract of a store append, not generated data. */
    private static final byte[] COMPLETE_LINE = "{\"format_version\":1,\"n\":1}\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TORN_LINE = "{\"format_version\":1,\"n\":".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private Process holder;

    /** The protocol lines the other process wrote before it said it holds the key. */
    private final List<String> protocolLines = new ArrayList<>();

    @BeforeEach
    void ids() {
        projectId = "project-" + Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
        planId = "plan-" + Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }

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

    /**
     * Starts the other process and returns once it has said that it holds its key. The protocol lines it wrote before
     * {@link LockHolderProcess#HELD} are kept in {@link #protocolLines}.
     */
    private void startHolder(String... extra) throws IOException, InterruptedException, ExecutionException,
            TimeoutException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ArrayList<>(List.of(java, "-cp", System.getProperty("java.class.path"),
                LockHolderProcess.class.getName(), base.toString(), projectId, planId));
        command.addAll(List.of(extra));
        holder = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        var reader = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        // Reading the lines cannot be interrupted, so it is waited for from outside; ending the process ends the read
        var lines = CompletableFuture.supplyAsync(() -> {
            try {
                var collected = new ArrayList<String>();
                String line;
                while ((line = reader.readLine()) != null && !LockHolderProcess.HELD.equals(line)) {
                    collected.add(line);
                }
                return collected;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).get(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        protocolLines.clear();
        protocolLines.addAll(lines);
    }

    /** Lets the other process release the lock and end, and returns its exit code. */
    private int releaseHolder() throws IOException, InterruptedException {
        holder.getOutputStream().close();
        assertTrue(holder.waitFor(PROCESS_BOUND.toMillis(), TimeUnit.MILLISECONDS),
                "the other process ends once its input is closed");
        return holder.exitValue();
    }

    /**
     * The sidecar a leaf key's OS lock is taken on (ADR 068), derived from the layout alone: the guarded file's name
     * with {@code .lock} appended, beside it.
     */
    private static Path sidecar(LockKey key) {
        return key.lockFile().resolveSibling(key.lockFile().getFileName() + ".lock");
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

    /**
     * The journal of a task, derived from the layout alone: {@code plans/<plan_id>/tasks/activity/<task>.jsonl} below
     * the project store.
     */
    private Path journalPath(String taskId) {
        return LockKey.projectStore(base, projectId).resolve("plans").resolve(planId)
                .resolve("tasks").resolve("activity").resolve(taskId + ".jsonl");
    }

    /** @return the two blocks as one byte array */
    private static byte[] concatenated(byte[] first, byte[] second) {
        var joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    @Test
    @DisplayName("a key held in another process locks its lock file for this process until that process releases it")
    void lockFileIsLockedForThisProcess() throws Exception {
        var key = LockKey.plan(base, projectId, planId);
        startHolder();

        var lockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var lockedAfterRelease = lockedByAnotherProcess(key.lockFile());

        assertAll(
                () -> assertTrue(lockedWhileHeld, "locked while the other process holds the key"),
                () -> assertEquals(0, exitCode, "exit code of the other process"),
                () -> assertFalse(lockedAfterRelease, "locked after the other process released the key"));
    }

    /**
     * The defect PLAN-V2-95 fixes: on POSIX a close of any channel on a file drops every OS lock the process holds on
     * it, and a JSONL append closes the store's channel. If the leaf key's OS lock were taken on the store, the other
     * process's append would release it. Seen from this process, the guarded store stays unlocked and the sidecar
     * carries the lock (ADR 068).
     * <p>
     * Enabled on Linux and macOS only: the behaviour it shows, a close dropping the process's OS locks, is POSIX.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("an append on a leaf key in another process keeps that key's OS lock against this process")
    void leafAppendKeepsTheOsLock() throws Exception {
        Files.createDirectories(base.resolve("logs"));
        var key = LockKey.auditLog(base);
        startHolder(LockHolderProcess.LEAF);

        var sidecarLockedWhileHeld = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));
        var storeLockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var sidecarLockedAfterRelease = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));

        assertAll(
                () -> assertTrue(sidecarLockedWhileHeld,
                        "the sidecar is locked while the other process holds the key"),
                () -> assertFalse(storeLockedWhileHeld, "the guarded store itself is not locked"),
                () -> assertEquals(0, exitCode, "exit code of the other process"),
                () -> assertFalse(sidecarLockedAfterRelease,
                        "the sidecar is free after the other process released the key"),
                () -> assertEquals(COMPLETE_LINE.length, Files.size(key.lockFile()),
                        "the child appended one line to the audit log"));
    }

    /**
     * The mailbox is the second production leaf key ({@code LockKey.mailbox}); its append opens and closes a channel on
     * the mailbox, so a lock taken on the mailbox itself would be dropped by that very append.
     * <p>
     * Enabled on Linux and macOS only: the POSIX rule that closing any channel on a file releases the process's OS
     * locks is what the test shows.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("an append on the mailbox leaf key in another process keeps its OS lock against this process")
    void leafMailboxAppendKeepsTheOsLock() throws Exception {
        var key = LockKey.mailbox(base, projectId, planId);
        Files.createDirectories(key.lockFile().getParent());
        startHolder(LockHolderProcess.MAILBOX);

        var sidecarLockedWhileHeld = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));
        var storeLockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var sidecarLockedAfterRelease = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));

        assertAll(
                () -> assertTrue(sidecarLockedWhileHeld,
                        "the mailbox sidecar is locked while the child holds the key"),
                () -> assertFalse(storeLockedWhileHeld, "the mailbox itself is not locked by the OS"),
                () -> assertEquals(0, exitCode, "the child ends with exit code 0"),
                () -> assertFalse(sidecarLockedAfterRelease,
                        "the mailbox sidecar is free after the child released it"),
                () -> assertEquals(COMPLETE_LINE.length, Files.size(key.lockFile()),
                        "the child appended one line to the mailbox"));
    }

    /**
     * A tail repair opens and closes a channel on the guarded store, so the OS lock has to survive it as well.
     * <p>
     * Enabled on Linux and macOS only: the POSIX rule that closing any channel on a file releases the process's OS
     * locks is what the test shows.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a torn-tail repair on a leaf key in another process keeps its OS lock against this process")
    void leafTailRepairKeepsTheOsLock() throws Exception {
        Files.createDirectories(base.resolve("logs"));
        var key = LockKey.auditLog(base);
        Files.write(key.lockFile(), concatenated(COMPLETE_LINE, TORN_LINE));
        startHolder(LockHolderProcess.REPAIR);

        var sidecarLockedWhileHeld = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));
        var storeLockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var sidecarLockedAfterRelease = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));

        assertAll(
                () -> assertTrue(sidecarLockedWhileHeld,
                        "the audit sidecar is locked while the child holds the key"),
                () -> assertFalse(storeLockedWhileHeld, "the audit log itself is not locked by the OS"),
                () -> assertEquals(0, exitCode, "the child ends with exit code 0"),
                () -> assertFalse(sidecarLockedAfterRelease,
                        "the audit sidecar is free after the child released it"),
                () -> assertEquals(COMPLETE_LINE.length, Files.size(key.lockFile()),
                        "the child cut the torn tail from the audit log"));
    }

    /**
     * The last-line read of an append-only store opens and closes a channel on it too, so the OS lock must survive the
     * read (ADR 068).
     * <p>
     * Enabled on Linux and macOS only: the POSIX rule that closing any channel on a file releases the process's OS
     * locks is what the test shows.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a last-line read on a leaf key in another process keeps its OS lock against this process")
    void leafLastLineReadKeepsTheOsLock() throws Exception {
        Files.createDirectories(base.resolve("logs"));
        var key = LockKey.auditLog(base);
        Files.write(key.lockFile(), COMPLETE_LINE);
        startHolder(LockHolderProcess.READ);

        var sidecarLockedWhileHeld = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));
        var storeLockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var sidecarLockedAfterRelease = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));

        assertAll(
                () -> assertTrue(sidecarLockedWhileHeld,
                        "the audit sidecar is locked while the child holds the key"),
                () -> assertFalse(storeLockedWhileHeld, "the audit log itself is not locked by the OS"),
                () -> assertEquals(0, exitCode, "the child ends with exit code 0"),
                () -> assertFalse(sidecarLockedAfterRelease,
                        "the audit sidecar is free after the child released it"),
                () -> assertEquals(List.of(LockHolderProcess.LINES_PREFIX + 1), protocolLines,
                        "the child read the one complete line of the log"));
    }

    /**
     * A task's activity journal is the third production leaf key, built by {@code ActivityJournal} as
     * {@code new LockKey(LockLevel.LEAF, <task>.jsonl, id)}; its store write must not release the OS lock either.
     * <p>
     * Enabled on Linux and macOS only: the POSIX rule that closing any channel on a file releases the process's OS
     * locks is what the test shows.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("an append on a journal-shaped leaf key in another process keeps its OS lock against this process")
    void leafJournalAppendKeepsTheOsLock() throws Exception {
        var taskId = "qt-" + "%016x".formatted(Generators.longs().next());
        var journal = journalPath(taskId);
        Files.createDirectories(journal.getParent());
        var key = new LockKey(LockLevel.LEAF, journal, taskId);
        startHolder(LockHolderProcess.JOURNAL, taskId);

        var sidecarLockedWhileHeld = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));
        var storeLockedWhileHeld = lockedByAnotherProcess(key.lockFile());
        var exitCode = releaseHolder();
        var sidecarLockedAfterRelease = Files.exists(sidecar(key)) && lockedByAnotherProcess(sidecar(key));

        assertAll(
                () -> assertTrue(sidecarLockedWhileHeld,
                        "the journal sidecar is locked while the child holds the key"),
                () -> assertFalse(storeLockedWhileHeld, "the journal itself is not locked by the OS"),
                () -> assertEquals(0, exitCode, "the child ends with exit code 0"),
                () -> assertFalse(sidecarLockedAfterRelease,
                        "the journal sidecar is free after the child released it"),
                () -> assertEquals(COMPLETE_LINE.length, Files.size(key.lockFile()),
                        "the child appended one line to the journal"));
    }

    @Test
    @DisplayName("a transaction of this process waits for a key held in another process and gets it on its release")
    void transactionWaitsForTheOtherProcess() throws Exception {
        var key = LockKey.plan(base, projectId, planId);
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
                () -> assertTrue(endedAfterRelease, "acquisition came back after the other's release"),
                () -> assertTrue(acquired.get(), "the key is acquired by this process"),
                () -> assertFalse(lockedByAnotherProcess(key.lockFile()), "locked after both released the key"));
    }
}
