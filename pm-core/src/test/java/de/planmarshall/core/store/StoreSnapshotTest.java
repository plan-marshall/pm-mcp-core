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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;

/**
 * The lock-free read of a store (PM-IMPL-7): it takes no lock, never sees a part of a write, and reports a digest
 * mismatch as unverified instead of deciding that the store was edited.
 */
@DisplayName("Store snapshot")
class StoreSnapshotTest {

    private static final byte[] CONTENT = "{\"format_version\":1,\"leases\":[]}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private Path store;

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] filled(int length, char character) {
        var bytes = new byte[length];
        Arrays.fill(bytes, (byte) character);
        return bytes;
    }

    /** A transaction on one in-process lock, which is all a writer needs to exclude other writers in this test. */
    private static final class OneLock implements LockTransaction {

        private final ReentrantLock lock;
        private LockKey held;

        OneLock(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void acquire(LockKey key) {
            lock.lock();
            held = key;
        }

        @Override
        public boolean holds(LockKey key) {
            return key.equals(held);
        }

        @Override
        public void close() {
            if (held != null) {
                held = null;
                lock.unlock();
            }
        }
    }

    @BeforeEach
    void createStore() throws IOException {
        store = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        Files.write(store, CONTENT);
    }

    @Nested
    @DisplayName("without a lock")
    class WithoutLock {

        @Test
        @DisplayName("a read returns the content of the store and compares it with nothing")
        void unchecked() {
            var snapshot = StoreSnapshot.read(store);

            assertAll(
                    () -> assertArrayEquals(CONTENT, snapshot.content().orElseThrow()),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity()),
                    () -> assertEquals(store, snapshot.store()));
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("a read succeeds while another thread holds the sibling lock key of the store")
        void readsBesideHeldLock() throws Exception {
            var siblingLock = new ReentrantLock();
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var holder = executor.submit(() -> {
                    try (var transaction = new OneLock(siblingLock)) {
                        transaction.acquire(LockKey.buildSlots(base));
                        held.countDown();
                        release.await();
                    }
                    return null;
                });
                held.await();

                var snapshot = StoreSnapshot.read(store);

                var lockedDuringRead = siblingLock.isLocked();
                release.countDown();
                holder.get();
                assertAll(
                        () -> assertTrue(lockedDuringRead, "the sibling key was held while the snapshot was read"),
                        () -> assertArrayEquals(CONTENT, snapshot.content().orElseThrow()));
            }
        }

        /**
         * A reader that could observe a store between the truncation and the end of a write would serve a
         * half-written store. Every write replaces the file by one rename, so a reader beside a writer sees one of
         * the complete contents and nothing else.
         */
        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("a reader beside a writer only ever sees one of the complete written contents")
        void neverHalfWritten() throws Exception {
            var first = filled(256 * 1024, 'a');
            var second = filled(64 * 1024, 'b');
            var siblingKey = LockKey.buildSlots(base);
            var storeFile = new AtomicStoreFile(store, siblingKey);
            var siblingLock = new ReentrantLock();
            try (var transaction = new OneLock(siblingLock)) {
                transaction.acquire(siblingKey);
                storeFile.write(transaction, first);
            }
            var writing = new AtomicBoolean(true);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var writer = executor.submit(() -> {
                    try {
                        for (int round = 0; round < 200; round++) {
                            try (var transaction = new OneLock(siblingLock)) {
                                transaction.acquire(siblingKey);
                                storeFile.write(transaction, round % 2 == 0 ? second : first);
                            }
                        }
                    } finally {
                        writing.set(false);
                    }
                    return null;
                });

                int reads = 0;
                int incomplete = 0;
                while (writing.get()) {
                    var content = StoreSnapshot.read(store).content().orElseThrow();
                    reads++;
                    if (!Arrays.equals(first, content) && !Arrays.equals(second, content)) {
                        incomplete++;
                    }
                }
                writer.get();

                int observedReads = reads;
                int observedIncomplete = incomplete;
                assertAll(
                        () -> assertTrue(observedReads > 0, "the reader ran beside the writer"),
                        () -> assertEquals(0, observedIncomplete, "reads of a content no write produced"));
            }
        }

        @Test
        @DisplayName("a store that does not exist is a snapshot without content, not a failure")
        void absentStore() throws IOException {
            Files.delete(store);

            var snapshot = StoreSnapshot.read(store);

            assertAll(
                    () -> assertTrue(snapshot.content().isEmpty()),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity()));
        }

        @Test
        @DisplayName("the content of a snapshot cannot be changed through what it returned")
        void contentIsCopied() {
            var snapshot = StoreSnapshot.read(store);

            snapshot.content().orElseThrow()[0] = 'X';

            assertArrayEquals(CONTENT, snapshot.content().orElseThrow());
        }

        @Test
        @DisplayName("a store that exists and cannot be read is a failure of the read")
        void unreadableStore() {
            var directory = store.getParent();

            assertAll(
                    () -> assertThrows(UncheckedIOException.class, () -> StoreSnapshot.read(directory)),
                    () -> assertThrows(NullPointerException.class, () -> StoreSnapshot.read(null)),
                    () -> assertThrows(NullPointerException.class, () -> StoreSnapshot.read(store, 1, null)));
        }
    }

    @Nested
    @DisplayName("with a recorded digest")
    class WithRecordedDigest {

        @Test
        @DisplayName("a content with the recorded size and SHA-256 is verified, whatever the case of the digits")
        void verified() throws Exception {
            var digest = sha256(CONTENT);

            var lowerCase = StoreSnapshot.read(store, CONTENT.length, digest);
            var upperCase = StoreSnapshot.read(store, CONTENT.length, digest.toUpperCase(Locale.ROOT));

            assertAll(
                    () -> assertEquals(StoreIntegrity.VERIFIED, lowerCase.integrity()),
                    () -> assertEquals(StoreIntegrity.VERIFIED, upperCase.integrity()),
                    () -> assertArrayEquals(CONTENT, lowerCase.content().orElseThrow()));
        }

        @Test
        @DisplayName("another SHA-256 is reported as digest_unverified for the store, with its content, and nothing is thrown")
        void digestMismatch() throws Exception {
            var recorded = sha256("another content".getBytes(StandardCharsets.UTF_8));

            var snapshot = assertDoesNotThrow(() -> StoreSnapshot.read(store, CONTENT.length, recorded));

            assertAll(
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity()),
                    () -> assertEquals(store, snapshot.store()),
                    () -> assertArrayEquals(CONTENT, snapshot.content().orElseThrow()));
        }

        @Test
        @DisplayName("another size is reported as digest_unverified although the SHA-256 is the recorded one")
        void sizeMismatch() throws Exception {
            var snapshot = StoreSnapshot.read(store, CONTENT.length + 1L, sha256(CONTENT));

            assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity());
        }

        @Test
        @DisplayName("a store that does not exist although a digest is recorded is digest_unverified without content")
        void absentStore() throws Exception {
            Files.delete(store);

            var snapshot = StoreSnapshot.read(store, CONTENT.length, sha256(CONTENT));

            assertAll(
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity()),
                    () -> assertTrue(snapshot.content().isEmpty()));
        }

        @Test
        @DisplayName("the results of a snapshot read are unchecked, verified and digest_unverified")
        void integrityValues() {
            assertEquals(List.of("unchecked", "verified", "digest_unverified"),
                    Arrays.stream(StoreIntegrity.values()).map(StoreIntegrity::code).toList());
        }
    }
}
