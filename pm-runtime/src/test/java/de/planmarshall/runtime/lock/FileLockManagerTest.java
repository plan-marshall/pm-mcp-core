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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.shortWords;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.AuditAnchor;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.TransactionId;
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

    /**
     * The instant and the clock of the audit records the leaf-lock tests write. Both are a literal because they are
     * the contract: the audit records need one fixed instant and clock for the chain, and a generated instant would
     * make that chain differ on every run (R20, the contract exception to generated test data).
     */
    private static final Instant AT = Instant.parse("2026-11-08T09:30:15.250Z");
    private static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);

    /** A complete line and a torn tail of a JSONL store: the contract of an append, not generated data. */
    private static final byte[] APPENDED = "{\"format_version\":1,\"n\":1}\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TORN = "{\"format_version\":1,\"n\":".getBytes(StandardCharsets.UTF_8);

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
        projectId = "project-" + shortWords().next();
        planId = "plan-" + shortWords().next();
        auditSink = new RecordingAuditSink();
        manager = new FileLockManager(auditSink);
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
     * <p>
     * The answer comes from the JVM's lock table, which keeps a channel's registration after another channel on the
     * file has closed: this probe therefore cannot show that an OS lock survived a store write. That proof is
     * {@code FileLockManagerTwoProcessIT}, which reads the lock from a second process.
     *
     * @return whether this process holds a lock of the operating system on the file, as the JVM's lock table says
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

    /**
     * The sidecar a leaf key's OS lock is taken on, derived from the layout alone: the guarded file's name with
     * {@code .lock} appended, beside it. A test derives the expected path independently of the class under test.
     */
    private static Path osLockFile(LockKey key) {
        return key.lockFile().resolveSibling(key.lockFile().getFileName() + ".lock");
    }

    /** @return whether this process holds a lock of the operating system on the file; a missing file is unlocked */
    private boolean isLockedIfPresent(Path file) throws IOException {
        return Files.exists(file) && isLocked(file);
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
            var epic = LockKey.epic(base, projectId, "epic-" + shortWords().next());
            var mailbox = LockKey.mailbox(base, projectId, planId);
            Files.createDirectories(mailbox.lockFile().getParent());
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(epic);

                transaction.acquire(mailbox);

                assertAll(
                        () -> assertTrue(transaction.holds(epic), "the epic lock is held"),
                        () -> assertTrue(transaction.holds(mailbox), "the mailbox leaf lock is held"),
                        () -> assertTrue(isLocked(osLockFile(mailbox)), "the mailbox sidecar is locked"),
                        () -> assertFalse(Files.exists(mailbox.lockFile()), "the mailbox itself is not created"),
                        () -> assertEquals(List.of(), auditSink.events(), "the ordered pair is not audited"),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, FileLockManager.class));
            }
        }
    }

    @Nested
    @DisplayName("lock files")
    class LockFiles {

        /**
         * Enabled on Linux and macOS only: it reads POSIX file modes, which the other operating systems do not have.
         */
        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
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
         * lock manager created would stand in the way of the plan that is published into that place. The OS lock is
         * taken on the sidecar beside the guarded file (ADR 068), so the acquisition creates the sidecar and not the
         * store.
         * <p>
         * Enabled on Linux and macOS only: it reads the sidecar's mode through POSIX file permissions.
         */
        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a leaf lock creates its sidecar with mode 0600 but never its directory nor the store")
        void leafLock() throws Exception {
            var audit = LockKey.auditLog(base);
            var sidecar = osLockFile(audit);
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
                        () -> assertTrue(missingDirectory.getMessage().contains(sidecar.toString()),
                                "the failure names the sidecar lock file"),
                        () -> assertFalse(heldAfterFailure, "a failed leaf acquisition holds nothing"),
                        () -> assertNull(failure.get(), "the failed acquisition left the key free"),
                        () -> assertEquals("rw-------", mode(sidecar), "the sidecar lock file has mode 0600"),
                        () -> assertEquals(0, Files.size(sidecar), "the sidecar lock file is created empty"),
                        () -> assertFalse(Files.exists(audit.lockFile()),
                                "the guarded store is not created by the acquisition"));
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

    /**
     * A leaf key names an append-only store, and a store write opens and closes channels on that file. On POSIX a
     * close on a file releases every OS lock the process holds on it, so the leaf key's OS lock is taken on a sidecar
     * that no store channel opens (ADR 068).
     * <p>
     * A probe inside this process cannot see a lost OS lock: the JVM's own lock table keeps the first channel's lock
     * registered after another channel on the file closes, so {@code isLocked} and a second manager both answer from
     * that table. The OS-level proof is {@code FileLockManagerTwoProcessIT}, which watches the sidecar from a second
     * process with one test per leaf mode: the audit append, the tail repair, the last-line read, the mailbox append
     * and a journal-shaped key. These tests pin the sidecar's layout, the second manager's refusal, the release, and
     * the append and repair path.
     */
    @Nested
    @DisplayName("the OS lock of a leaf key")
    class LeafLockSurvival {

        @Test
        @DisplayName("an append through a JSONL store keeps the OS lock until the transaction ends")
        void appendKeepsTheLock() throws Exception {
            Files.createDirectories(base.resolve("logs"));
            var key = LockKey.auditLog(base);
            var store = new JsonlStoreFile(key.lockFile(), key);
            var sidecar = osLockFile(key);

            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);

                store.append(transaction, APPENDED, auditSink);

                assertAll("an append under a leaf key",
                        () -> assertTrue(isLockedIfPresent(sidecar), "the OS lock survives the append"),
                        () -> assertTrue(transaction.holds(key), "the transaction still holds the key"),
                        () -> assertArrayEquals(APPENDED, Files.readAllBytes(key.lockFile()),
                                "the block is appended to the guarded store"));
            }

            assertAll("after the transaction closed",
                    () -> assertFalse(isLockedIfPresent(sidecar), "the OS lock is released with the transaction"),
                    () -> assertTrue(Files.exists(sidecar), "the sidecar lock file stays in place"));
        }

        @Test
        @DisplayName("a second lock manager in a second thread cannot take the leaf key after an append")
        void secondManagerCannotTakeTheKey() throws Exception {
            Files.createDirectories(base.resolve("logs"));
            var key = LockKey.auditLog(base);
            var store = new JsonlStoreFile(key.lockFile(), key);
            var secondManager = new FileLockManager(new RecordingAuditSink());
            var secondResult = new AtomicReference<RuntimeException>();

            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);
                store.append(transaction, APPENDED, auditSink);
                var lockedAfterAppend = isLockedIfPresent(osLockFile(key));

                var secondThread = Thread.ofPlatform().start(() -> {
                    try (var other = secondManager.openTransaction()) {
                        other.acquire(key);
                    } catch (IllegalStateException | UncheckedIOException e) {
                        secondResult.set(e);
                    }
                });
                secondThread.join(Duration.ofSeconds(10));

                assertAll("a second lock manager on the same base",
                        () -> assertFalse(secondThread.isAlive(), "the second manager's attempt ends in the bound"),
                        () -> assertTrue(lockedAfterAppend, "the OS lock survives the append"),
                        () -> assertInstanceOf(IllegalStateException.class, secondResult.get(),
                                "the second manager cannot take the key"),
                        () -> assertTrue(transaction.holds(key), "the first transaction still holds the key"));
            }
        }

        @Test
        @DisplayName("the OS lock is released exactly once, by the last holder's close")
        void releasedOnceByTheLastHolder() throws Exception {
            Files.createDirectories(base.resolve("logs"));
            var key = LockKey.auditLog(base);
            var sidecar = osLockFile(key);
            var first = manager.openTransaction();
            var second = manager.openTransaction();
            first.acquire(key);
            second.acquire(key);

            first.close();
            var lockedAfterFirstClose = isLockedIfPresent(sidecar);
            second.close();

            assertAll("the OS lock of a leaf key across two holders",
                    () -> assertTrue(lockedAfterFirstClose, "the lock is held by the second transaction"),
                    () -> assertFalse(isLockedIfPresent(sidecar), "the last close releases the OS lock"),
                    () -> assertFalse(second.holds(key), "the closed transaction holds nothing"),
                    () -> assertTrue(Files.exists(sidecar), "the sidecar lock file stays in place"));

            try (var again = manager.openTransaction()) {
                again.acquire(key);
                assertTrue(again.holds(key), "the released key is acquired again");
            }
        }

        @Test
        @DisplayName("a torn-tail repair and the audit anchor calls run in the one transaction holding the key")
        void repairAndAnchorUnderTheLock() throws Exception {
            Files.createDirectories(base.resolve("logs"));
            var key = LockKey.auditLog(base);
            AuditAnchor anchor = createMock(AuditAnchor.class);
            var principal = Generators.letterStrings(3, 20).next();
            var log = new AuditLog(base, anchor, CLOCK, principal);
            Capture<LockTransaction> seen = newCapture(CaptureType.LAST);
            anchor.beforeAppend(capture(seen), anyObject(AuditLog.class));
            expectLastCall();
            anchor.afterAppend(capture(seen), anyObject(AuditLog.class), anyObject(AuditRecord.class), anyString());
            expectLastCall();
            expect(anchor.emptyLogPredecessor()).andReturn(Optional.empty()).anyTimes();
            replay(anchor);
            Files.write(log.logFile(), TORN);

            try (var transaction = manager.openTransaction()) {
                transaction.acquire(key);

                log.append(transaction, template(principal));

                assertAll("a repairing append under the leaf key",
                        () -> assertTrue(isLockedIfPresent(osLockFile(key)),
                                "the OS lock survives the repair and the writes"),
                        () -> assertDoesNotThrow(() -> verify(anchor), "both anchor calls ran"),
                        () -> assertSame(transaction, seen.getValue(),
                                "the anchor ran in the transaction holding the key"),
                        () -> assertEquals(2, log.read(transaction).lines().size(),
                                "the repair and the append are both in the log"));
            }
        }

        private AuditRecord template(String principal) {
            return AuditRecord.builder()
                    .eventId(TransactionId.generate(CLOCK).value())
                    .prevHash(AuditCodec.GENESIS_HASH)
                    .timestamp(AT)
                    .category(Category.OPERATOR)
                    .event("leaf_lock_test")
                    .scopeType(Scope.MACHINE)
                    .actor(principal)
                    .outcome(Outcome.SUCCEEDED)
                    .details(Map.of("os_principal", principal))
                    .build();
        }
    }

    @Test
    @DisplayName("a lock manager needs an audit sink")
    void auditSinkRequired() {
        assertThrows(NullPointerException.class, () -> new FileLockManager(null),
                "a lock manager without audit sink is refused");
    }
}
