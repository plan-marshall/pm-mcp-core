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

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LeaseCodec.FormatException;
import de.planmarshall.core.store.LeaseCodec.Refusal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    private static final LeaseOwner FIRST_OWNER = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "lock-manager",
            FIRST_RUNTIME, null);

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
        void contended() throws Exception {
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
        @DisplayName("a release that names the owner of the lease removes it in one transaction on the store key")
        void releaseByOwner() {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);
            locks.acquisitions.clear();
            locks.transactions = 0;

            var release = leaseStore.release(KEY, lease.owner());

            assertAll(
                    () -> assertEquals(new LeaseStore.Release(true, lease), release),
                    () -> assertEquals(List.of(), leaseStore.snapshot()),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        /**
         * The claimer still knows the owner it wrote. Meanwhile another runtime adopted the lease, so the lease in
         * the store is the claim of the newer holder, and a release by the first one must not take it away.
         */
        @Test
        @DisplayName("a release that names a stale owner leaves the lease of the newer holder and writes nothing")
        void staleOwner() throws Exception {
            var claimed = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(claimed);
            var adopted = leaseStore.adopt(KEY, SECOND_RUNTIME).orElseThrow();
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();

            var release = leaseStore.release(KEY, claimed.owner());

            assertAll(
                    () -> assertEquals(new LeaseStore.Release(false, adopted), release),
                    () -> assertArrayEquals(written, Files.readAllBytes(store)),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held));
        }

        @Test
        @DisplayName("a release that names an owner finds nothing under a key that is not held and writes nothing")
        void releaseByOwnerOfUnknownKey() {
            var owner = planLease("lock-manager", FIRST_RUNTIME).owner();

            var release = leaseStore.release(KEY, owner);

            assertAll(
                    () -> assertEquals(new LeaseStore.Release(false, null), release),
                    () -> assertFalse(Files.exists(store)),
                    () -> assertEquals(List.of(), locks.held));
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
        void orphanTwice() throws Exception {
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
    @DisplayName("revision of every lease")
    class ReviseAll {

        private final Instant orphanedAt = Instant.parse("2026-10-09T10:00:01Z");

        @Test
        @DisplayName("each lease is replaced or removed in one transaction on the machine store key")
        void revise() {
            var kept = planLease("lock-manager", FIRST_RUNTIME);
            var removed = new LeaseRecord("removed/repository", kept.owner(), ACQUIRED_AT, null);
            var changed = new LeaseRecord("changed/repository", kept.owner(), ACQUIRED_AT, null);
            leaseStore.claim(kept);
            leaseStore.claim(removed);
            leaseStore.claim(changed);
            locks.acquisitions.clear();
            locks.transactions = 0;
            var orphaned = changed.withOwner(changed.owner().orphaned(orphanedAt));

            var before = leaseStore.reviseAll(lease -> switch (lease.key()) {
                case "removed/repository" -> Optional.empty();
                case "changed/repository" -> Optional.of(orphaned);
                default -> Optional.of(lease);
            });

            assertAll(
                    () -> assertEquals(Optional.of(List.of(kept, removed, changed)), before),
                    () -> assertEquals(List.of(kept, orphaned), leaseStore.snapshot()),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("a revision that changes nothing does not write the store")
        void unchanged() throws Exception {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);
            var fileBefore = fileIdentity();

            var before = leaseStore.reviseAll(Optional::of);

            assertAll(
                    () -> assertEquals(Optional.of(List.of(lease)), before),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held));
        }

        @Test
        @DisplayName("a store that does not exist is told apart from an empty one, and is not created")
        void absentAndEmpty() {
            var revised = new ArrayList<LeaseRecord>();
            var absent = leaseStore.reviseAll(lease -> {
                revised.add(lease);
                return Optional.of(lease);
            });
            var absentStoreExists = Files.exists(store);
            leaseStore.claim(planLease("lock-manager", FIRST_RUNTIME));
            leaseStore.release(KEY);

            var empty = leaseStore.reviseAll(Optional::of);

            assertAll(
                    () -> assertEquals(Optional.empty(), absent),
                    () -> assertFalse(absentStoreExists),
                    () -> assertEquals(List.of(), revised),
                    () -> assertEquals(Optional.of(List.of()), empty));
        }

        @Test
        @DisplayName("a revision that gives two leases one key is refused, and the store stays as it was")
        void duplicateKey() throws Exception {
            var first = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(first);
            leaseStore.claim(new LeaseRecord("another/repository", first.owner(), ACQUIRED_AT, null));
            var written = Files.readAllBytes(store);

            assertThrows(IllegalArgumentException.class, () -> leaseStore.reviseAll(lease -> Optional.of(first)));

            assertAll(
                    () -> assertArrayEquals(written, Files.readAllBytes(store)),
                    () -> assertEquals(List.of(), locks.held));
        }

        /**
         * A lease under another key would take the claim away from the key it was given for and write a claim
         * nobody made. One lease is enough to show it: with a single lease no two leases share a key.
         */
        @Test
        @DisplayName("a revision that returns a lease under another key is refused, and the store stays as it was")
        void otherKey() throws Exception {
            var lease = planLease("lock-manager", FIRST_RUNTIME);
            leaseStore.claim(lease);
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();
            var underOtherKey = new LeaseRecord("another/repository", lease.owner(), ACQUIRED_AT, EXPIRES_AT);

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> leaseStore.reviseAll(_ -> Optional.of(underOtherKey)));

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(KEY), refusal.getMessage()),
                    () -> assertTrue(refusal.getMessage().contains("another/repository"), refusal.getMessage()),
                    () -> assertArrayEquals(written, Files.readAllBytes(store)),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held));
        }

        @Test
        @DisplayName("the store names its file")
        void storePath() {
            assertEquals(store.toAbsolutePath().normalize(), leaseStore.store());
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
        void newerFormat() throws Exception {
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
        void unreadable() throws Exception {
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
                    () -> assertThrows(NullPointerException.class,
                            () -> leaseStore.release(null, FIRST_OWNER)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.release(KEY, null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(null, SECOND_RUNTIME)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(KEY, null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.orphan(KEY, null)),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.reviseAll(null)),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore.Claim(true, null)),
                    () -> assertEquals(0, locks.transactions));
        }
    }
}
