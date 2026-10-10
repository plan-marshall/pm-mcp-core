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

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The write path of a store replaced by atomic rename (PM-IMPL-7): written only under its sibling lock key, and the
 * store file itself never locked.
 */
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
        void write() throws Exception {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                storeFile.write(transaction, NEW);
            }

            assertAll(
                    () -> assertArrayEquals(NEW, Files.readAllBytes(store)),
                    () -> assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store))),
                    () -> assertEquals(List.of("merge-queue.json"), filesBesideStore()));
        }

        @Test
        @DisplayName("a write creates a store that does not exist yet")
        void writeNewStore() throws Exception {
            Files.delete(store);
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                storeFile.write(transaction, NEW);
            }

            assertArrayEquals(NEW, Files.readAllBytes(store));
        }

        @Test
        @DisplayName("a read returns the current content, and nothing for a store that does not exist")
        void read() throws Exception {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                var present = storeFile.read(transaction);
                Files.delete(store);
                var absent = storeFile.read(transaction);

                assertAll(
                        () -> assertArrayEquals(OLD, present.orElseThrow()),
                        () -> assertTrue(absent.isEmpty()));
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

                assertAll(
                        () -> assertNotNull(lockOnStore),
                        () -> assertTrue(lockOnStore.isValid()),
                        () -> assertArrayEquals(NEW, Files.readAllBytes(store)));
            }
        }

        @Test
        @DisplayName("a write that fails leaves the store as it was and no temporary file")
        void failedWrite() throws Exception {
            var directory = Files.createDirectory(base.resolve("state").resolve("install-records.json"));
            Files.writeString(directory.resolve("entry"), "keeps the directory non-empty");
            var blocked = new AtomicStoreFile(directory, LockKey.installRecords(base));
            try (var transaction = new HeldKeys()) {
                transaction.acquire(LockKey.installRecords(base));

                assertThrows(UncheckedIOException.class, () -> blocked.write(transaction, NEW));
            }

            assertAll(
                    () -> assertTrue(Files.isDirectory(directory)),
                    () -> assertEquals(List.of("install-records.json", "merge-queue.json"), filesBesideStore()));
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

                var fault = assertThrows(InternalFaultException.class, () -> storeFile.write(transaction, NEW));

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                        () -> assertEquals(Map.of("store", store.toString(), "lock",
                                siblingKey.lockFile().toString()), fault.details()),
                        () -> assertArrayEquals(OLD, Files.readAllBytes(store)),
                        () -> assertEquals(List.of("merge-queue.json"), filesBesideStore()));
            }
        }

        @Test
        @DisplayName("a read is refused as an internal fault")
        void readRefused() {
            try (var transaction = new HeldKeys()) {
                var fault = assertThrows(InternalFaultException.class, () -> storeFile.read(transaction));

                assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason());
            }
        }

        @Test
        @DisplayName("a key that was released with its transaction no longer permits a write")
        void releasedKey() {
            var transaction = new HeldKeys();
            transaction.acquire(siblingKey);
            transaction.close();

            assertThrows(InternalFaultException.class, () -> storeFile.write(transaction, NEW));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a key whose lock file is the store file itself is refused")
        void lockOnStoreRefused() {
            var audit = LockKey.auditLog(base);

            assertThrows(IllegalArgumentException.class, () -> new AtomicStoreFile(audit.lockFile(), audit));
        }

        @Test
        @DisplayName("the store path is made absolute and normalized, so the refusal cannot be bypassed")
        void normalizedStore() {
            var audit = LockKey.auditLog(base);
            var detour = base.resolve("logs").resolve("..").resolve("logs").resolve("audit.jsonl");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new AtomicStoreFile(detour, audit)),
                    () -> assertEquals(store.toAbsolutePath().normalize(), storeFile.store()),
                    () -> assertEquals(siblingKey, storeFile.lockKey()),
                    () -> assertNotEquals(siblingKey.lockFile(), storeFile.store()));
        }

        @Test
        @DisplayName("a missing store, key, transaction or content is refused")
        void missingArguments() {
            try (var transaction = new HeldKeys()) {
                transaction.acquire(siblingKey);

                assertAll(
                        () -> assertThrows(NullPointerException.class, () -> new AtomicStoreFile(null, siblingKey)),
                        () -> assertThrows(NullPointerException.class, () -> new AtomicStoreFile(store, null)),
                        () -> assertThrows(NullPointerException.class, () -> storeFile.write(null, NEW)),
                        () -> assertThrows(NullPointerException.class, () -> storeFile.write(transaction, null)));
            }
        }
    }
}
