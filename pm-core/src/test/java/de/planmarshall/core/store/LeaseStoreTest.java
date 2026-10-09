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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LeaseCodec.FormatException;
import de.planmarshall.core.store.LeaseCodec.Refusal;

/**
 * The leases of a machine lease store (PM-IMPL-7): records that are claimed and released in one short transaction
 * on the lock of the store, and that outlive the transaction and the store instance that wrote them.
 */
@DisplayName("Lease store")
class LeaseStoreTest {

    private static final String KEY = "plan-marshall/pm-mcp-core";
    private static final Instant ACQUIRED_AT = Instant.parse("2026-10-09T08:16:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-09T09:16:00Z");
    private static final HolderInstance FIRST_RUNTIME = new HolderInstance(4711,
            Instant.parse("2026-10-09T08:15:30.123456Z"));
    private static final HolderInstance SECOND_RUNTIME = new HolderInstance(4712,
            Instant.parse("2026-10-09T10:00:00Z"));

    @TempDir
    Path base;

    private Path store;
    private LockKey storeKey;
    private RecordingLockManager locks;
    private LeaseStore leaseStore;

    /**
     * A lock manager that takes no lock and records what it is asked for: every transaction it opened, every key
     * acquired, and the keys held at the moment, one entry per transaction that holds a key.
     */
    private static final class RecordingLockManager implements LockManager {

        private final List<LockKey> acquisitions = new ArrayList<>();
        private final List<LockKey> held = new ArrayList<>();
        private int transactions;

        @Override
        public LockTransaction openTransaction() {
            transactions++;
            return new LockTransaction() {

                private final Set<LockKey> own = new HashSet<>();

                @Override
                public void acquire(LockKey key) {
                    acquisitions.add(key);
                    if (own.add(key)) {
                        held.add(key);
                    }
                }

                @Override
                public boolean holds(LockKey key) {
                    return own.contains(key);
                }

                @Override
                public void close() {
                    own.forEach(held::remove);
                    own.clear();
                }
            };
        }
    }

    private static LeaseRecord planLease(String planId, HolderInstance holder) {
        var owner = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, planId, holder, null);
        return new LeaseRecord(KEY, owner, ACQUIRED_AT, EXPIRES_AT);
    }

    @BeforeEach
    void createStore() throws IOException {
        store = Files.createDirectories(base.resolve("state")).resolve("merge-queue.json");
        storeKey = LockKey.mergeQueue(base);
        locks = new RecordingLockManager();
        leaseStore = new LeaseStore(new AtomicStoreFile(store, storeKey), locks);
    }

    /** A write replaces the store by a rename, so the file of an unwritten store is the same file as before. */
    private Object fileIdentity() throws IOException {
        return Files.readAttributes(store, BasicFileAttributes.class).fileKey();
    }

    private void assertOneTransactionOnTheStoreKey() {
        assertAll(
                () -> assertEquals(1, locks.transactions, "transactions opened"),
                () -> assertEquals(List.of(storeKey), locks.acquisitions, "keys acquired"),
                () -> assertEquals(List.of(), locks.held, "keys held afterwards"));
    }

    @Nested
    @DisplayName("claim")
    class Claiming {

        @Test
        @DisplayName("a claim writes the lease in one transaction on the machine store key and holds nothing after")
        void claim() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);

            var claim = leaseStore.claim(lease);

            assertAll(
                    () -> assertTrue(claim.granted()),
                    () -> assertEquals(lease, claim.holder()),
                    () -> assertArrayEquals(LeaseCodec.write(List.of(lease)), Files.readAllBytes(store)),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        /**
         * A lease is not a lock of the process that claimed it. A runtime that starts again, here a second store
         * instance with its own lock manager, finds the lease in the file.
         */
        @Test
        @DisplayName("a claimed lease is read back by a second store instance over the same file")
        void survivesRestart() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);

            var restarted = new LeaseStore(new AtomicStoreFile(store, storeKey), new RecordingLockManager());

            assertEquals(List.of(lease), restarted.snapshot());
        }

        @Test
        @DisplayName("a second claim of a held key writes nothing and names the holder")
        void contended() throws IOException {
            var held = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(held);
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();
            var restarted = new LeaseStore(new AtomicStoreFile(store, storeKey), new RecordingLockManager());

            var claim = restarted.claim(planLease("another-plan", SECOND_RUNTIME));

            assertAll(
                    () -> assertFalse(claim.granted()),
                    () -> assertEquals(held, claim.holder()),
                    () -> assertArrayEquals(written, Files.readAllBytes(store)),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("a second key is claimed beside the first, in the order of the claims")
        void secondKey() {
            var first = planLease("lock-manager", FIRST_RUNTIME);
            var second = new LeaseRecord("another/repository", first.owner(), ACQUIRED_AT, null);
            leaseStore.claim(first);

            var claim = leaseStore.claim(second);

            assertAll(
                    () -> assertTrue(claim.granted()),
                    () -> assertEquals(List.of(first, second), leaseStore.snapshot()));
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("a release removes the lease in one transaction on the machine store key and holds nothing after")
        void release() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);
            locks.acquisitions.clear();
            locks.transactions = 0;

            var released = leaseStore.release(KEY);

            assertAll(
                    () -> assertEquals(Optional.of(lease), released),
                    () -> assertEquals(List.of(), leaseStore.snapshot()),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("a released key is claimed again")
        void claimAfterRelease() {
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            leaseStore.release(KEY);
            var next = planLease("another-plan", SECOND_RUNTIME);

            var claim = leaseStore.claim(next);

            assertAll(
                    () -> assertTrue(claim.granted()),
                    () -> assertEquals(List.of(next), leaseStore.snapshot()));
        }

        @Test
        @DisplayName("releasing, adopting or orphaning a key that is not held returns nothing and writes nothing")
        void unknownKey() {
            var released = leaseStore.release(KEY);
            var adopted = leaseStore.adopt(KEY, SECOND_RUNTIME);
            var orphaned = leaseStore.orphan(KEY, ACQUIRED_AT);

            assertAll(
                    () -> assertEquals(Optional.empty(), released),
                    () -> assertEquals(Optional.empty(), adopted),
                    () -> assertEquals(Optional.empty(), orphaned),
                    () -> assertFalse(Files.exists(store)),
                    () -> assertEquals(List.of(), locks.held));
        }
    }

    @Nested
    @DisplayName("orphan and adopt")
    class OrphanAndAdopt {

        private final Instant orphanedAt = Instant.parse("2026-10-09T10:00:01Z");

        @Test
        @DisplayName("orphaning marks the lease and keeps it, its owner and its holder")
        void orphan() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);

            var orphaned = leaseStore.orphan(KEY, orphanedAt).orElseThrow();

            assertAll(
                    () -> assertEquals(lease.withOwner(lease.owner().orphaned(orphanedAt)), orphaned),
                    () -> assertEquals(orphanedAt, orphaned.owner().orphanedAt()),
                    () -> assertEquals(FIRST_RUNTIME, orphaned.owner().holderInstance()),
                    () -> assertEquals(List.of(orphaned), leaseStore.snapshot()),
                    () -> assertEquals(List.of(), locks.held));
        }

        @Test
        @DisplayName("a lease that is orphaned already keeps the instant it was first found orphaned")
        void orphanTwice() throws IOException {
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            leaseStore.orphan(KEY, orphanedAt);
            var fileBefore = fileIdentity();

            var again = leaseStore.orphan(KEY, orphanedAt.plusSeconds(3600)).orElseThrow();

            assertAll(
                    () -> assertEquals(orphanedAt, again.owner().orphanedAt()),
                    () -> assertEquals(orphanedAt, leaseStore.snapshot().getFirst().owner().orphanedAt()),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("adopting names the new runtime as holder, clears orphaned_at and keeps the owning scope")
        void adopt() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);
            leaseStore.orphan(KEY, orphanedAt);
            locks.acquisitions.clear();
            locks.transactions = 0;

            var adopted = leaseStore.adopt(KEY, SECOND_RUNTIME).orElseThrow();

            assertAll(
                    () -> assertNull(adopted.owner().orphanedAt()),
                    () -> assertFalse(adopted.owner().isOrphaned()),
                    () -> assertEquals(SECOND_RUNTIME, adopted.owner().holderInstance()),
                    () -> assertEquals(lease.withOwner(lease.owner().adoptedBy(SECOND_RUNTIME)), adopted),
                    () -> assertEquals("lock-manager", adopted.owner().scopeId()),
                    () -> assertEquals(List.of(adopted), leaseStore.snapshot()),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("an adopted lease is still held: a claim of its key names the adopting runtime")
        void adoptedLeaseIsHeld() {
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            leaseStore.orphan(KEY, orphanedAt);
            leaseStore.adopt(KEY, SECOND_RUNTIME);

            var claim = leaseStore.claim(planLease("another-plan", FIRST_RUNTIME));

            assertAll(
                    () -> assertFalse(claim.granted()),
                    () -> assertEquals(SECOND_RUNTIME, claim.holder().owner().holderInstance()));
        }

        @Test
        @DisplayName("an orphaned lease is still held: it is not released by being orphaned")
        void orphanedLeaseIsHeld() {
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            leaseStore.orphan(KEY, orphanedAt);

            var claim = leaseStore.claim(planLease("another-plan", SECOND_RUNTIME));

            assertAll(
                    () -> assertFalse(claim.granted()),
                    () -> assertTrue(claim.holder().owner().isOrphaned()));
        }
    }

    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        @Test
        @DisplayName("a snapshot takes no lock and opens no transaction")
        void lockFree() {
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            locks.acquisitions.clear();
            locks.transactions = 0;

            var leases = leaseStore.snapshot();

            assertAll(
                    () -> assertEquals(1, leases.size()),
                    () -> assertEquals(0, locks.transactions),
                    () -> assertEquals(List.of(), locks.acquisitions));
        }

        @Test
        @DisplayName("a store that does not exist has no leases")
        void absentStore() {
            assertEquals(List.of(), leaseStore.snapshot());
        }
    }

    @Nested
    @DisplayName("a store that is refused")
    class RefusedStore {

        @Test
        @DisplayName("a store in a newer format is refused with its path, left byte-identical, and the lock released")
        void newerFormat() throws IOException {
            var newer = "{\"format_version\":2,\"leases\":[]}".getBytes(StandardCharsets.UTF_8);
            Files.write(store, newer);
            var lease = planLease("lock-manager", FIRST_RUNTIME);

            var refusal = assertThrows(FormatException.class, () -> leaseStore.claim(lease));

            assertAll(
                    () -> assertEquals(Refusal.FORMAT_NEWER, refusal.refusal()),
                    () -> assertTrue(refusal.getMessage().contains(store.toString()), refusal.getMessage()),
                    () -> assertArrayEquals(newer, Files.readAllBytes(store)),
                    () -> assertEquals(List.of(), locks.held));
        }

        @Test
        @DisplayName("an unreadable store is never taken for an empty one, by a write or by a snapshot")
        void unreadable() throws IOException {
            var torn = "{\"format_version\":1,\"leases\":[".getBytes(StandardCharsets.UTF_8);
            Files.write(store, torn);
            var lease = planLease("lock-manager", FIRST_RUNTIME);

            assertAll(
                    () -> assertEquals(Refusal.UNREADABLE,
                            assertThrows(FormatException.class, () -> leaseStore.claim(lease)).refusal()),
                    () -> assertEquals(Refusal.UNREADABLE,
                            assertThrows(FormatException.class, () -> leaseStore.release(KEY)).refusal()),
                    () -> assertEquals(Refusal.UNREADABLE,
                            assertThrows(FormatException.class, leaseStore::snapshot).refusal()),
                    () -> assertArrayEquals(torn, Files.readAllBytes(store)),
                    () -> assertEquals(List.of(), locks.held));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a store file that is not guarded by a machine store lock is refused")
        void otherLockLevel() {
            var queueFile = new AtomicStoreFile(store, LockKey.queue(base, "pm-mcp-core"));

            assertThrows(IllegalArgumentException.class, () -> new LeaseStore(queueFile, locks));
        }

        @Test
        @DisplayName("a missing store file, lock manager, lease, key, holder or instant is refused")
        void missingArguments() {
            var storeFile = new AtomicStoreFile(store, storeKey);

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore(null, locks)),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore(storeFile, null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.claim(null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.release(null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(null, SECOND_RUNTIME)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(KEY, null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.orphan(KEY, null)),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore.Claim(true, null)),
                    () -> assertEquals(0, locks.transactions));
        }
    }
}
