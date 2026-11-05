/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;

/**
 * The write path of a store replaced by atomic rename (PM-IMPL-7): written only under its sibling lock key, and the
 * store file itself never locked.
 */
@EnableGeneratorController
@DisplayName("Atomic store file")
class AtomicStoreFileTest {

    private static final byte[] OLD = "{\"format_version\":1,\"leases\":[]}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW = "{\"format_version\":1,\"leases\":[1]}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private Path store;
    private LockKey siblingKey;
    private AtomicStoreFile storeFile;

    /** A transaction that holds the keys it was asked for and takes no lock; the manager is not under test here. */
    private static final class HeldKeys implements LockTransaction {

        private final Set<LockKey> held = new HashSet<>();

        @Override
        public void acquire(LockKey key) {
            held.add(key);
        }

        @Override
        public boolean holds(LockKey key) {
            return held.contains(key);
        }

        @Override
        public void close() {
            held.clear();
        }
    }

    @BeforeEach
    void createStore() throws IOException {
        store = Files.createDirectories(base.resolve("state")).resolve("merge-queue.json");
        Files.write(store, OLD);
        siblingKey = LockKey.mergeQueue(base);
        storeFile = new AtomicStoreFile(store, siblingKey);
    }

    private List<String> filesBesideStore() throws IOException {
        try (var files = Files.list(store.getParent())) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Nested
    @DisplayName("under the sibling key")
    class UnderSiblingKey {

        @Test
        @DisplayName("a write replaces the store, with mode 0600, and leaves no temporary file behind")
        void write() {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                storeFile.write(transaction, NEW);
            }

            assertAll("the write replaced the store under its lock",
                    () -> assertArrayEquals(NEW, Files.readAllBytes(store), "the new content replaced the old one"),
                    () -> assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store)),
                            "the store keeps the mode 0600"),
                    () -> assertEquals(List.of("merge-queue.json"), filesBesideStore(),
                            "no temporary file is left beside it"));
        }

        @Test
        @DisplayName("a write creates a store that does not exist yet")
        void writeNewStore() throws Exception {
            Files.delete(store);
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                storeFile.write(transaction, NEW);
            }

            assertArrayEquals(NEW, Files.readAllBytes(store), "the store that was missing is created");
        }

        @Test
        @DisplayName("a read returns the current content, and nothing for a store that does not exist")
        void read() throws Exception {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                var present = storeFile.read(transaction);
                Files.delete(store);
                var absent = storeFile.read(transaction);

                assertAll("the read returns the store and its absence",
                        () -> assertArrayEquals(OLD, present.orElseThrow(), "the present store keeps its content"),
                        () -> assertTrue(absent.isEmpty(), "an absent store reads as empty"));
            }
        }

        /**
         * A rename replaces the inode, so a lock on the store file would exclude nobody; the store is guarded by
         * its sibling lock file alone. Would the write lock the store, it could not run while this JVM holds a lock
         * on it.
         */
        @Test
        @DisplayName("the store file is never locked: a write runs while another channel holds a lock on the store")
        void storeFileIsNotLocked() throws Exception {
            try (var channel = FileChannel.open(store, StandardOpenOption.WRITE);
                 var lockOnStore = channel.tryLock();
                 var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                storeFile.write(transaction, NEW);

                assertAll("the store is never locked by the write",
                        () -> assertNotNull(lockOnStore, "the test holds a lock on the store"),
                        () -> assertTrue(lockOnStore.isValid(), "the lock of the test is still valid"),
                        () -> assertArrayEquals(NEW, Files.readAllBytes(store),
                                "the write ran under the sibling key only"));
            }
        }

        /**
         * After the rename the write opens the directory of the store to force it to disk. Whether the directory
         * reached the disk is not visible to a test. That the write opens it after the rename is: a directory that
         * may be written but not read takes the temporary file and the rename, and refuses to be opened.
         */
        @Test
        @DisplayName("a store whose directory cannot be opened is replaced, and the write reports the directory")
        void directoryNotForced() throws Exception {
            var directory = store.getParent();
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("-wx------"));
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);
                assumeFalse(Files.isReadable(directory), "this user reads a directory without the read permission");

                var failure = assertThrows(UncheckedIOException.class, () -> storeFile.write(transaction, NEW),
                        "the write reports the directory it cannot open");

                assertAll("the store was replaced and the directory named",
                        () -> assertTrue(failure.getMessage().contains("directory cannot be forced to disk"),
                                "the failure names the directory: " + failure.getMessage()),
                        () -> assertArrayEquals(NEW, Files.readAllBytes(store),
                                "the store was replaced despite the directory"));
            } finally {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            }

            assertEquals(List.of("merge-queue.json"), filesBesideStore(),
                    "no temporary file survived the failed force");
        }

        @Test
        @DisplayName("a write that fails leaves the store as it was and no temporary file")
        void failedWrite() throws Exception {
            var directory = Files.createDirectory(base.resolve("state").resolve("install-records.json"));
            Files.writeString(directory.resolve("entry"), Generators.letterStrings(10, 30).next());
            var blocked = new AtomicStoreFile(directory, LockKey.installRecords(base));
            try (var transaction = new HeldKeys()) {
                transaction.acquire(LockKey.installRecords(base));

                assertThrows(UncheckedIOException.class, () -> blocked.write(transaction, NEW),
                        "a directory in place of the store refuses the write");
            }

            assertAll("the failed write left store and directory intact",
                    () -> assertTrue(Files.isDirectory(directory), "the blocking directory is still there"),
                    () -> assertEquals(List.of("install-records.json", "merge-queue.json"), filesBesideStore(),
                            "no temporary file is left behind"));
        }
    }

    @Nested
    @DisplayName("without the sibling key")
    class WithoutSiblingKey {

        @Test
        @DisplayName("a write is refused as an internal fault and leaves the store byte-identical")
        void writeRefused() {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(LockKey.buildSlots(base));

                var fault = assertThrows(InternalFaultException.class, () -> storeFile.write(transaction, NEW),
                        "the write without the sibling key is refused");

                assertAll("the write without the sibling key is refused",
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                                "the refusal names the missing store lock"),
                        () -> assertEquals(Map.of("store", store.toString(), "lock",
                                siblingKey.lockFile().toString()), fault.details(),
                                "the details name the store and its lock"),
                        () -> assertArrayEquals(OLD, Files.readAllBytes(store),
                                "the store stays byte-identical"),
                        () -> assertEquals(List.of("merge-queue.json"), filesBesideStore(),
                                "no temporary file was written"));
            }
        }

        @Test
        @DisplayName("a read is refused as an internal fault")
        void readRefused() {
            try (var transaction = new HeldKeys()) {
                var fault = assertThrows(InternalFaultException.class, () -> storeFile.read(transaction),
                        "the read without the sibling key is refused");

                assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                        "the refusal names the missing store lock");
            }
        }

        @Test
        @DisplayName("a key that was released with its transaction no longer permits a write")
        void releasedKey() {
            var transaction = new HeldKeys();
            transaction.acquire(siblingKey);
            transaction.close();

            assertThrows(InternalFaultException.class, () -> storeFile.write(transaction, NEW),
                    "a released key no longer permits a write");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a key whose lock file is the store file itself is refused")
        void lockOnStoreRefused() {
            var audit = LockKey.auditLog(base);
            var lockFile = audit.lockFile();

            assertThrows(IllegalArgumentException.class, () -> new AtomicStoreFile(lockFile, audit),
                    "a key on the store file itself is refused");
        }

        @Test
        @DisplayName("the store path is made absolute and normalized, so the refusal cannot be bypassed")
        void normalizedStore() {
            var audit = LockKey.auditLog(base);
            var detour = base.resolve("logs").resolve("..").resolve("logs").resolve("audit.jsonl");

            assertAll("the store path is absolute and normalized",
                    () -> assertThrows(IllegalArgumentException.class, () -> new AtomicStoreFile(detour, audit),
                            "a detour path to the lock file is refused"),
                    () -> assertEquals(store.toAbsolutePath().normalize(), storeFile.store(),
                            "the store is absolute and normalized"),
                    () -> assertEquals(siblingKey, storeFile.lockKey(), "the store keeps its sibling lock key"),
                    () -> assertNotEquals(siblingKey.lockFile(), storeFile.store(),
                            "the store is never its own lock file"));
        }

        @Test
        @DisplayName("a missing store, key, transaction or content is refused")
        void missingArguments() {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                assertAll("a missing argument is refused",
                        () -> assertThrows(NullPointerException.class, () -> new AtomicStoreFile(null, siblingKey),
                                "a missing store path is refused"),
                        () -> assertThrows(NullPointerException.class, () -> new AtomicStoreFile(store, null),
                                "a missing lock key is refused"),
                        () -> assertThrows(NullPointerException.class, () -> storeFile.write(null, NEW),
                                "a missing transaction is refused"),
                        () -> assertThrows(NullPointerException.class, () -> storeFile.write(transaction, null),
                                "missing write content is refused"));
            }
        }
    }
}
