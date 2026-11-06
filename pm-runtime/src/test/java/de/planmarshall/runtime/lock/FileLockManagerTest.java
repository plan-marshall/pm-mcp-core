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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;

/**
 * The lock manager of the runtime (PM-IMPL-7): one in-process lock per key, one lock of the operating system per
 * lock file, and the order assertion on every acquisition.
 * <p>
 * Whether a file is locked is probed from inside this process: a second lock on a file this process has locked
 * fails with {@link OverlappingFileLockException}. Whether another process is excluded is the subject of the
 * two-process test.
 * <p>
 * The project, plan and epic ids, the content and the modification time of an existing lock file are generated. The
 * literals that stay are the contract: the lock file layout below the base ({@code locks/}, {@code projects/},
 * {@code logs/}, {@code merge.lock}), the file modes {@code rw-------} and {@code rwx------}, the audit record's
 * event, category and outcome, the store file name and document of the merge queue, and the entry counts the
 * manager keeps.
 */
@EnableGeneratorController
@EnableTestLogger
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("File lock manager")
class FileLockManagerTest {

    @TempDir
    Path base;

    private RecordingAuditSink auditSink;
    private FileLockManager manager;
    private String projectId;
    private String planId;

    /** The channels the probes opened; closed after the test, when the manager holds no lock any more. */
    private final List<FileChannel> probes = new ArrayList<>();

    @BeforeEach
    void createManager() {
        projectId = "project-" + lowerLetters();
        planId = "plan-" + lowerLetters();
        auditSink = new RecordingAuditSink();
        manager = new FileLockManager(auditSink);
    }

    private static String lowerLetters() {
        return Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }

    @AfterEach
    void closeProbes() throws IOException {
        for (var probe : probes) {
            probe.close();
        }
    }

    /**
     * The channel of a probe stays open until the test has ended: closing any channel on a file releases every lock
     * the process holds on it, so a probe that closed its channel would drop the lock of the manager it has just
     * seen. No test fails without this, because the loss is visible from another process only.
     *
     * @return whether this process holds a lock of the operating system on the file
     */
    private boolean isLocked(Path file) throws IOException {
        var channel = FileChannel.open(file, StandardOpenOption.WRITE);
        probes.add(channel);
        try {
            var lock = channel.tryLock();
            if (lock != null) {
                lock.release();
            }
            return false;
        } catch (OverlappingFileLockException _) {
            return true;
        }
    }

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    /** Returns when the thread waits for a lock, or has ended without ever waiting for one. */
    private static void awaitWaitingOrEnded(Thread thread) {
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TERMINATED) {
            Thread.onSpinWait();
        }
    }

    /** Waits for the latch; a thread that is interrupted meanwhile goes on with its interrupt flag set. */
    private static void awaitUninterrupted(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("one key")
    class OneKey {

        @Test
        @DisplayName("a second thread waits for a held key and gets it when the holder closes")
        void threadsExcludeEachOther() throws Exception {
            var key = LockKey.mergeQueue(base);
            var acquiredBySecond = new AtomicBoolean();
            var first = manager.openTransaction();
            first.acquire(key);
            var second = Thread.ofPlatform().start(() -> {
                try (var transaction = manager.openTransaction()) {
                    transaction.acquire(key);
                    acquiredBySecond.set(true);
                }
            });

            awaitWaitingOrEnded(second);
            var acquiredWhileHeld = acquiredBySecond.get();
            first.close();
            second.join();

            assertAll(
                    () -> assertFalse(acquiredWhileHeld, "acquired while the first transaction held the key"),
                    () -> assertTrue(acquiredBySecond.get(), "acquired after the first transaction closed"),
                    () -> assertFalse(isLocked(key.lockFile()), "the lock file is free after both closed"));
        }

        @Test
        @DisplayName("a thread that holds one key does not make another thread wait for another key")
        void otherKeyIsFree() throws Exception {
            var acquiredBySecond = new AtomicBoolean();
            try (var first = manager.openTransaction()) {
                first.acquire(LockKey.mergeQueue(base));
                var second = Thread.ofPlatform().start(() -> {
                    try (var transaction = manager.openTransaction()) {
                        transaction.acquire(LockKey.buildSlots(base));
                        acquiredBySecond.set(true);
                    }
                });

                second.join();

                assertTrue(acquiredBySecond.get(), "another key is acquired without waiting");
            }
        }

        @Test
        @DisplayName("re-entry of a held key succeeds, and one close releases it")
        void reentry() throws Exception {
            var key = LockKey.mergeQueue(base);
            var transaction = manager.openTransaction();
            transaction.acquire(key);

            transaction.acquire(key);
            var heldAfterReentry = transaction.holds(key);
            var lockedAfterReentry = isLocked(key.lockFile());
            transaction.close();

            assertAll(
                    () -> assertTrue(heldAfterReentry, "the key is held after the re-entry"),
                    () -> assertTrue(lockedAfterReentry, "the lock file is locked after the re-entry"),
                    () -> assertFalse(transaction.holds(key), "one close releases the re-entered key"),
                    () -> assertFalse(isLocked(key.lockFile()), "one close unlocks the lock file"));
        }

        /**
         * A second channel that locked the file from this process would fail with
         * {@link OverlappingFileLockException}, and closing the channel of the first transaction would release the
         * lock under the second one. Both transactions therefore share the one channel, and it stays open until
         * the last of them closes.
         */
        @Test
        @DisplayName("two transactions on one lock file share one channel, and the lock is held until the last closes")
        void sharedChannel() throws Exception {
            var key = LockKey.mergeQueue(base);
            var first = manager.openTransaction();
            var second = manager.openTransaction();
            first.acquire(key);

            assertDoesNotThrow(() -> second.acquire(key), "a second transaction shares the channel");
            first.close();
            var lockedAfterFirstClose = isLocked(key.lockFile());
            var heldBySecond = second.holds(key);
            second.close();

            assertAll(
                    () -> assertTrue(lockedAfterFirstClose, "locked while the second transaction holds the key"),
                    () -> assertTrue(heldBySecond, "the second transaction still holds the key"),
                    () -> assertFalse(isLocked(key.lockFile()), "unlocked after the last transaction closed"));
        }

        @Test
        @DisplayName("a closed transaction acquires nothing, and closing it again does nothing")
        void closedTransaction() {
            var key = LockKey.mergeQueue(base);
            var transaction = manager.openTransaction();
            transaction.acquire(key);
            transaction.close();

            transaction.close();

            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> transaction.acquire(key),
                            "a closed transaction acquires nothing"),
                    () -> assertFalse(transaction.holds(key), "a closed transaction holds no key"),
                    () -> assertFalse(isLocked(key.lockFile()), "the lock file is free after the close"));
        }
    }

    @Nested
    @DisplayName("lock order")
    class Order {

        @Test
        @DisplayName("keys in the lock order are all held, and close releases all of them")
        void inOrder() throws Exception {
            var workspace = LockKey.workspace(base, projectId);
            var plan = LockKey.plan(base, projectId, planId);
            var merge = LockKey.mergeQueue(base);
            var transaction = manager.openTransaction();

            transaction.acquire(workspace);
            transaction.acquire(plan);
            transaction.acquire(merge);
            var allHeld = transaction.holds(workspace) && transaction.holds(plan) && transaction.holds(merge);
            var allLocked = isLocked(workspace.lockFile()) && isLocked(plan.lockFile()) && isLocked(merge.lockFile());
            transaction.close();

            assertAll(
                    () -> assertTrue(allHeld, "every key in the lock order is held"),
                    () -> assertTrue(allLocked, "every lock file in the order is locked"),
                    () -> assertFalse(isLocked(workspace.lockFile()), "the workspace lock is released on close"),
                    () -> assertFalse(isLocked(plan.lockFile()), "the plan lock is released on close"),
                    () -> assertFalse(isLocked(merge.lockFile()), "the merge lock is released on close"),
                    () -> assertEquals(List.of(), auditSink.events(), "an ordered acquisition is not audited"));
        }

        /**
         * A key out of the lock order is refused before anything is touched: the call fails as an internal fault,
         * one record of the refusal reaches the audit log, and the refused key leaves no trace on disk.
         */
        @Test
        @DisplayName("an out-of-order acquisition is an internal fault, audited and logged once, and acquires nothing")
        void outOfOrder() {
            var merge = LockKey.mergeQueue(base);
            var plan = LockKey.plan(base, projectId, planId);
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(merge);

                var fault = assertThrows(InternalFaultException.class, () -> transaction.acquire(plan),
                        "an out-of-order acquisition is a fault");

                var expectedDetails = Map.of(
                        AuditEvent.DETAIL_HELD, List.of(merge.lockFile().toString()),
                        AuditEvent.DETAIL_REQUESTED, plan.lockFile().toString());
                assertAll(
                        () -> assertEquals(Reason.LOCK_ORDER_VIOLATION, fault.reason(),
                                "the fault names the lock order violation"),
                        () -> assertEquals(expectedDetails, fault.details(),
                                "the fault names the held and requested files"),
                        () -> assertEquals(List.of(new AuditEvent("lock_order_violation", "store", "refused",
                                expectedDetails)), auditSink.events(), "one audit record names the refusal"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                                ERROR.LOCK_ORDER_VIOLATION.resolveIdentifierString()),
                        () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                                plan.lockFile().toString()),
                        () -> assertFalse(Files.exists(plan.lockFile()), "lock file of the refused key"),
                        () -> assertFalse(Files.exists(base.resolve("projects")), "directory of the refused key"),
                        () -> assertFalse(transaction.holds(plan), "the refused key is not held"),
                        () -> assertTrue(transaction.holds(merge), "the earlier key stays held"),
                        () -> assertTrue(isLocked(merge.lockFile()), "the earlier key's lock file stays locked"));
            }
        }

        @Test
        @DisplayName("a refused key is not held in the process: another thread acquires it at once")
        void refusedKeyStaysFree() throws Exception {
            var plan = LockKey.plan(base, projectId, planId);
            var acquiredByOther = new AtomicBoolean();
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(LockKey.mergeQueue(base));
                assertThrows(InternalFaultException.class, () -> transaction.acquire(plan),
                        "the out-of-order key is refused");

                var other = Thread.ofPlatform().start(() -> {
                    try (var otherTransaction = manager.openTransaction()) {
                        otherTransaction.acquire(plan);
                        acquiredByOther.set(true);
                    }
                });
                other.join();

                assertTrue(acquiredByOther.get(), "another thread acquires the refused key");
            }
        }

        @Test
        @DisplayName("nothing is acquired after a leaf lock")
        void nothingAfterLeaf() throws Exception {
            Files.createDirectories(base.resolve("logs"));
            var audit = LockKey.auditLog(base);
            var merge = LockKey.mergeQueue(base);
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(audit);

                assertThrows(InternalFaultException.class, () -> transaction.acquire(merge),
                        "nothing is acquired after a leaf lock");

                assertAll(
                        () -> assertEquals(1, auditSink.events().size(), "the refusal after a leaf is audited"),
                        () -> assertFalse(Files.exists(merge.lockFile()), "the refused key leaves no lock file"));
            }
        }

        /**
         * An epic transaction sends to the mailbox of a plan under its epic lock: the epic lock, then the mailbox
         * leaf lock. That order is the lock order, so it is not refused and nothing reaches the audit log.
         */
        @Test
        @DisplayName("the epic lock and then the mailbox leaf lock of a plan is accepted without an audit record")
        void epicThenMailbox() throws Exception {
            var epic = LockKey.epic(base, projectId, "epic-" + lowerLetters());
            var mailbox = LockKey.mailbox(base, projectId, planId);
            Files.createDirectories(mailbox.lockFile().getParent());
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(epic);

                transaction.acquire(mailbox);

                assertAll(
                        () -> assertTrue(transaction.holds(epic), "the epic lock is held"),
                        () -> assertTrue(transaction.holds(mailbox), "the mailbox leaf lock is held"),
                        () -> assertTrue(isLocked(mailbox.lockFile()), "the mailbox lock file is locked"),
                        () -> assertEquals(List.of(), auditSink.events(), "the ordered pair is not audited"),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, FileLockManager.class));
            }
        }
    }

    @Nested
    @DisplayName("lock files")
    class LockFiles {

        @Test
        @DisplayName("a lock file is created with mode 0600 and its directories with mode 0700")
        void modes() {
            var machine = LockKey.enrolment(base, projectId);
            var project = LockKey.queue(base, projectId);
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(project);
                transaction.acquire(machine);
            }

            assertAll(
                    () -> assertEquals("rw-------", mode(machine.lockFile()), "the machine lock file has mode 0600"),
                    () -> assertEquals("rwx------", mode(base.resolve("locks")), "the locks directory has mode 0700"),
                    () -> assertEquals("rwx------", mode(base.resolve("locks").resolve("enrolments")),
                            "the enrolments directory has mode 0700"),
                    () -> assertEquals("rw-------", mode(project.lockFile()), "the project lock file has mode 0600"),
                    () -> assertEquals("rwx------", mode(base.resolve("projects")),
                            "the projects directory has mode 0700"),
                    () -> assertEquals("rwx------", mode(base.resolve("projects").resolve(projectId)),
                            "the project's directory has mode 0700"),
                    () -> assertEquals("rwx------", mode(base.resolve("projects").resolve(projectId).resolve("locks")),
                            "the project's locks directory has mode 0700"));
        }

        @Test
        @DisplayName("acquiring an existing lock file changes neither its content nor its modification time")
        void existingLockFileUntouched() throws Exception {
            var key = LockKey.mergeQueue(base);
            var content = Generators.nonEmptyStrings().next().getBytes(StandardCharsets.UTF_8);
            var modified = FileTime.from(Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next()));
            Files.createDirectories(key.lockFile().getParent());
            Files.write(key.lockFile(), content);
            Files.setLastModifiedTime(key.lockFile(), modified);

            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);
            }

            assertAll(
                    () -> assertArrayEquals(content, Files.readAllBytes(key.lockFile()),
                            "the lock file's content is kept"),
                    () -> assertEquals(modified, Files.getLastModifiedTime(key.lockFile()),
                            "the lock file's modification time is kept"));
        }

        /**
         * A leaf key names an append-only file of a store. Its directory is the store's: a plan directory that the
         * lock manager created would stand in the way of the plan that is published into that place.
         */
        @Test
        @DisplayName("a leaf lock creates its file with mode 0600 but never its directory")
        void leafLock() throws Exception {
            var audit = LockKey.auditLog(base);
            var failure = new AtomicReference<RuntimeException>();
            try (var transaction = manager.openTransaction()) {
                var missingDirectory = assertThrows(UncheckedIOException.class, () -> transaction.acquire(audit),
                        "a leaf lock never creates its directory");
                var heldAfterFailure = transaction.holds(audit);
                Files.createDirectories(base.resolve("logs"));
                var other = Thread.ofPlatform().start(() -> {
                    try (var otherTransaction = manager.openTransaction()) {
                        otherTransaction.acquire(audit);
                    } catch (UncheckedIOException | IllegalStateException e) {
                        failure.set(e);
                    }
                });
                other.join();

                assertAll(
                        () -> assertTrue(missingDirectory.getMessage().contains(audit.lockFile().toString()),
                                "the failure names the leaf lock file"),
                        () -> assertFalse(heldAfterFailure, "a failed leaf acquisition holds nothing"),
                        () -> assertNull(failure.get(), "the failed acquisition left the key free"),
                        () -> assertEquals("rw-------", mode(audit.lockFile()), "the leaf lock file has mode 0600"),
                        () -> assertEquals(0, Files.size(audit.lockFile()), "the leaf lock file is created empty"));
            }
        }

        /**
         * The store is replaced by a rename, which replaces the inode, so a lock on the store file would exclude
         * nobody. The write runs under the sibling lock file below {@code locks/}, and the store file stays
         * unlocked.
         */
        @Test
        @DisplayName("a store written under its sibling key is not locked itself, the lock file below locks is")
        void storeFileUnlocked() throws Exception {
            var key = LockKey.mergeQueue(base);
            var store = Files.createDirectories(base.resolve("state")).resolve("merge-queue.json");
            var storeFile = new AtomicStoreFile(store, key);
            var content = "{\"format_version\":1,\"leases\":[]}".getBytes(StandardCharsets.UTF_8);
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);

                storeFile.write(transaction, content);

                assertAll(
                        () -> assertArrayEquals(content, storeFile.read(transaction).orElseThrow(),
                                "the store reads back what was written"),
                        () -> assertFalse(isLocked(store), "the store file itself is not locked"),
                        () -> assertTrue(isLocked(key.lockFile()), "the sibling lock file is locked"),
                        () -> assertEquals(base.resolve("locks").resolve("merge.lock"), key.lockFile(),
                                "the sibling lock file lies below locks"));
            }
        }

        @Test
        @DisplayName("a transaction that does not hold the sibling key cannot write the store")
        void storeNeedsSiblingKey() throws Exception {
            var store = Files.createDirectories(base.resolve("state")).resolve("merge-queue.json");
            var storeFile = new AtomicStoreFile(store, LockKey.mergeQueue(base));
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(LockKey.buildSlots(base));

                var fault = assertThrows(InternalFaultException.class,
                        () -> storeFile.write(transaction, new byte[0]), "a write without the sibling key fails");

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                                "the fault names the missing store lock"),
                        () -> assertFalse(Files.exists(store), "the store is not written without its key"));
            }
        }

        /**
         * A lock on a lock file that this process took past the manager makes the manager's own lock fail. The
         * failure names the cause, closes the channel it opened and leaves the key free.
         */
        @Test
        @DisplayName("a lock taken past the manager is reported, and the key is free again once it is gone")
        void lockPastTheManager() throws Exception {
            var key = LockKey.mergeQueue(base);
            Files.createDirectories(key.lockFile().getParent());
            Files.createFile(key.lockFile());
            try (var transaction = manager.openTransaction()) {
                try (var foreign = FileChannel.open(key.lockFile(), StandardOpenOption.WRITE);
                     var foreignLock = foreign.lock()) {
                    var refused = assertThrows(IllegalStateException.class, () -> transaction.acquire(key),
                            "a lock past the manager refuses the key");

                    assertAll(
                            () -> assertTrue(foreignLock.isValid(), "the foreign lock stays valid"),
                            () -> assertTrue(refused.getMessage().contains(key.lockFile().toString()),
                                    "the refusal names the lock file"),
                            () -> assertFalse(transaction.holds(key), "the refused key is not held"));
                }

                transaction.acquire(key);

                assertTrue(transaction.holds(key), "the key is acquired once the foreign lock left");
            }
        }
    }

    /**
     * The manager keeps the in-process lock of a key and the channel of a lock file only while a thread uses them.
     * The lock of a key must outlive every thread that holds or waits for it: a waiting thread that was handed a
     * lock which is no longer the lock of the key would hold the key together with the next thread that asks for it.
     */
    @Nested
    @DisplayName("entries per key")
    class Entries {

        @Test
        @DisplayName("held keys have an entry each, and none is left after the transaction closed")
        void noEntryAfterLastRelease() {
            var transaction = manager.openTransaction();
            transaction.acquire(LockKey.workspace(base, projectId));
            transaction.acquire(LockKey.plan(base, projectId, planId));
            transaction.acquire(LockKey.mergeQueue(base));
            var threadLocksWhileHeld = manager.threadLockCount();
            var osLocksWhileHeld = manager.osLockCount();

            transaction.close();

            assertAll(
                    () -> assertEquals(3, threadLocksWhileHeld, "each held key has a thread lock entry"),
                    () -> assertEquals(3, osLocksWhileHeld, "each held key has an OS lock entry"),
                    () -> assertEquals(0, manager.threadLockCount(), "no thread lock entry is left after close"),
                    () -> assertEquals(0, manager.osLockCount(), "no OS lock entry is left after close"));
        }

        @Test
        @DisplayName("a key held by two transactions keeps its entry and its lock until the last of them closes")
        void entryKeptWhileHeld() throws Exception {
            var key = LockKey.mergeQueue(base);
            var first = manager.openTransaction();
            var second = manager.openTransaction();
            first.acquire(key);
            second.acquire(key);

            first.close();
            var threadLocksAfterFirstClose = manager.threadLockCount();
            var osLocksAfterFirstClose = manager.osLockCount();
            var lockedAfterFirstClose = isLocked(key.lockFile());
            second.close();

            assertAll(
                    () -> assertEquals(1, threadLocksAfterFirstClose, "the shared key keeps its thread lock entry"),
                    () -> assertEquals(1, osLocksAfterFirstClose, "the shared key keeps its OS lock entry"),
                    () -> assertTrue(lockedAfterFirstClose, "the shared key's lock file stays locked"),
                    () -> assertEquals(0, manager.threadLockCount(), "no thread lock entry after the last close"),
                    () -> assertEquals(0, manager.osLockCount(), "no OS lock entry after the last close"));
        }

        @Test
        @DisplayName("an acquisition that fails leaves no entry")
        void noEntryAfterFailedAcquisition() {
            var audit = LockKey.auditLog(base);
            try (var transaction = manager.openTransaction()) {
                assertThrows(UncheckedIOException.class, () -> transaction.acquire(audit),
                        "the leaf acquisition without directory fails");

                assertAll(
                        () -> assertEquals(0, manager.threadLockCount(), "a failed acquisition leaves no thread lock"),
                        () -> assertEquals(0, manager.osLockCount(), "a failed acquisition leaves no OS lock"));
            }
        }

        /**
         * The first holder gives the key back while the second thread waits for it. The second thread then holds
         * the key, and a third thread that asks for it has to wait: the lock the second thread was waiting for is
         * still the lock of the key.
         */
        @Test
        @DisplayName("a key given back while another thread waits for it still excludes a third thread")
        void releasedWhileAwaited() throws Exception {
            var key = LockKey.mergeQueue(base);
            var heldBySecond = new CountDownLatch(1);
            var secondMayClose = new CountDownLatch(1);
            var acquiredByThird = new AtomicBoolean();
            var first = manager.openTransaction();
            first.acquire(key);
            var second = Thread.ofPlatform().start(() -> {
                try (var transaction = manager.openTransaction()) {
                    transaction.acquire(key);
                    heldBySecond.countDown();
                    awaitUninterrupted(secondMayClose);
                }
            });
            awaitWaitingOrEnded(second);

            first.close();
            var secondHolds = heldBySecond.await(10, TimeUnit.SECONDS);
            var third = Thread.ofPlatform().start(() -> {
                try (var transaction = manager.openTransaction()) {
                    transaction.acquire(key);
                    acquiredByThird.set(true);
                }
            });
            awaitWaitingOrEnded(third);
            var acquiredWhileSecondHeld = acquiredByThird.get();
            var threadLocksWhileAwaited = manager.threadLockCount();
            secondMayClose.countDown();
            second.join();
            third.join();

            assertAll(
                    () -> assertTrue(secondHolds, "the waiting thread got the key"),
                    () -> assertFalse(acquiredWhileSecondHeld, "acquired while the second thread held the key"),
                    () -> assertEquals(1, threadLocksWhileAwaited, "the awaited key keeps one thread lock entry"),
                    () -> assertTrue(acquiredByThird.get(), "acquired after the second thread closed"),
                    () -> assertEquals(0, manager.threadLockCount(), "no thread lock entry is left at the end"),
                    () -> assertEquals(0, manager.osLockCount(), "no OS lock entry is left at the end"),
                    () -> assertFalse(isLocked(key.lockFile()), "the lock file is free at the end"));
        }

        @Test
        @DisplayName("a key whose entry was dropped is acquired again, locked and released")
        void reacquired() throws Exception {
            var key = LockKey.mergeQueue(base);
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);
            }
            var threadLocksAfterFirstUse = manager.threadLockCount();
            var osLocksAfterFirstUse = manager.osLockCount();

            var again = manager.openTransaction();
            again.acquire(key);
            var heldAgain = again.holds(key);
            var lockedAgain = isLocked(key.lockFile());
            again.close();

            assertAll(
                    () -> assertEquals(0, threadLocksAfterFirstUse, "the first use leaves no thread lock entry"),
                    () -> assertEquals(0, osLocksAfterFirstUse, "the first use leaves no OS lock entry"),
                    () -> assertTrue(heldAgain, "the dropped key is held again"),
                    () -> assertTrue(lockedAgain, "the dropped key's lock file is locked again"),
                    () -> assertFalse(isLocked(key.lockFile()), "the lock file is released again"),
                    () -> assertEquals(0, manager.threadLockCount(), "no thread lock entry after the reuse"),
                    () -> assertEquals(0, manager.osLockCount(), "no OS lock entry after the reuse"));
        }
    }

    @Test
    @DisplayName("a lock manager needs an audit sink")
    void auditSinkRequired() {
        assertThrows(NullPointerException.class, () -> new FileLockManager(null),
                "a lock manager without audit sink is refused");
    }
}
