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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * The leases of a machine lease store (PM-IMPL-7): records that are claimed and released in one short transaction
 * on the lock of the store, and that outlive the transaction and the store instance that wrote them.
 */
@EnableGeneratorController
@DisplayName("Lease store")
class LeaseStoreTest {

    private String project;
    private String planId;
    private String otherPlanId;
    private String key;
    private String otherKey;
    private String removedKey;
    private String changedKey;
    private Instant acquiredAt;
    private Instant expiresAt;
    private Instant orphanedAt;
    private HolderInstance firstRuntime;
    private HolderInstance secondRuntime;
    private LeaseOwner firstOwner;
    private LeaseRecord baseLease;

    @TempDir
    Path base;

    private Path store;
    private LockKey storeKey;
    private RecordingLockManager locks;
    private LeaseStore leaseStore;

    private LeaseRecord planLease(String planId, HolderInstance holder) {
        var owner = new LeaseOwner(project, ScopeType.PLAN, planId, holder, null);
        return baseLease.withOwner(owner);
    }

    @BeforeEach
    void createStore() throws IOException {
        baseLease = CoreGenerators.leaseRecords().next();
        project = baseLease.owner().projectId();
        planId = baseLease.owner().scopeId();
        key = baseLease.key();
        acquiredAt = baseLease.acquiredAt();
        expiresAt = baseLease.leaseExpiresAt();
        orphanedAt = acquiredAt.plusSeconds(7_200);
        otherPlanId = "other-" + Generators.longs(1, Long.MAX_VALUE).next();
        otherKey = "other-" + Generators.longs(1, Long.MAX_VALUE).next();
        removedKey = "removed-" + Generators.longs(1, Long.MAX_VALUE).next();
        changedKey = "changed-" + Generators.longs(1, Long.MAX_VALUE).next();
        firstRuntime = baseLease.owner().holderInstance();
        secondRuntime = new HolderInstance(
                Generators.longs(1, Long.MAX_VALUE).next(),
                Instant.ofEpochMilli(Generators.longs(0, 1_700_000_000_000L).next()));
        firstOwner = baseLease.owner();
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
                () -> assertEquals(1, locks.transactions(), "transactions opened"),
                () -> assertEquals(List.of(storeKey), locks.acquisitions(), "keys acquired"),
                () -> assertEquals(List.of(), locks.held(), "keys held afterwards"));
    }

    @Nested
    @DisplayName("claim")
    class Claiming {

        @Test
        @DisplayName("a claim writes the lease in one transaction on the machine store key and holds nothing after")
        void claim() {
            var lease = planLease(planId, firstRuntime);

            var claim = leaseStore.claim(lease);

            assertAll("a claim writes the lease and holds nothing after",
                    () -> assertTrue(claim.granted(), "the claim is granted"),
                    () -> assertEquals(lease, claim.holder(), "the recorded lease is the claimed one"),
                    () -> assertArrayEquals(LeaseCodec.write(List.of(lease)), Files.readAllBytes(store),
                            "the store holds the claimed lease"),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        /**
         * A lease is not a lock of the process that claimed it. A runtime that starts again, here a second store
         * instance with its own lock manager, finds the lease in the file.
         */
        @Test
        @DisplayName("a claimed lease is read back by a second store instance over the same file")
        void survivesRestart() {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);

            var restarted = new LeaseStore(new AtomicStoreFile(store, storeKey), new RecordingLockManager());

            assertEquals(List.of(lease), restarted.snapshot(),
                    "a restarted store reads the claimed lease");
        }

        @Test
        @DisplayName("a second claim of a held key writes nothing and names the holder")
        void contended() throws Exception {
            var held = planLease(planId, firstRuntime);
            leaseStore.claim(held);
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();
            var restarted = new LeaseStore(new AtomicStoreFile(store, storeKey), new RecordingLockManager());

            var claim = restarted.claim(planLease(otherPlanId, secondRuntime));

            assertAll("a contended claim writes nothing and names the holder",
                    () -> assertFalse(claim.granted(), "the contended claim is not granted"),
                    () -> assertEquals(held, claim.holder(), "the claim names the current holder"),
                    () -> assertArrayEquals(written, Files.readAllBytes(store),
                            "the store stays byte-identical"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("a second key is claimed beside the first, in the order of the claims")
        void secondKey() {
            var first = planLease(planId, firstRuntime);
            var second = new LeaseRecord(otherKey, first.owner(), acquiredAt, null);
            leaseStore.claim(first);

            var claim = leaseStore.claim(second);

            assertAll("a second key is claimed beside the first",
                    () -> assertTrue(claim.granted(), "the second claim is granted"),
                    () -> assertEquals(List.of(first, second), leaseStore.snapshot(),
                            "both leases are kept in the claim order"));
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("a release removes the lease in one transaction on the machine store key and holds nothing after")
        void release() {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);
            locks.reset();

            var released = leaseStore.release(key);

            assertAll("a release removes the lease and holds nothing after",
                    () -> assertEquals(Optional.of(lease), released, "the released lease is returned"),
                    () -> assertEquals(List.of(), leaseStore.snapshot(), "the store holds no lease"),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("a released key is claimed again")
        void claimAfterRelease() {
            leaseStore.claim(planLease(planId, firstRuntime));
            leaseStore.release(key);
            var next = planLease(otherPlanId, secondRuntime);

            var claim = leaseStore.claim(next);

            assertAll("a released key is claimed again",
                    () -> assertTrue(claim.granted(), "the new claim is granted"),
                    () -> assertEquals(List.of(next), leaseStore.snapshot(),
                            "the store holds the new claim"));
        }

        @Test
        @DisplayName("a release that names the owner of the lease removes it in one transaction on the store key")
        void releaseByOwner() {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);
            locks.reset();

            var release = leaseStore.release(key, lease.owner());

            assertAll("a release by owner removes the lease in one transaction",
                    () -> assertEquals(new LeaseStore.Release(true, lease), release,
                            "the release reports the removed lease"),
                    () -> assertEquals(List.of(), leaseStore.snapshot(), "the store holds no lease"),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        /**
         * The claimer still knows the owner it wrote. Meanwhile another runtime adopted the lease, so the lease in
         * the store is the claim of the newer holder, and a release by the first one must not take it away.
         */
        @Test
        @DisplayName("a release that names a stale owner leaves the lease of the newer holder and writes nothing")
        void staleOwner() throws Exception {
            var claimed = planLease(planId, firstRuntime);
            leaseStore.claim(claimed);
            var adopted = leaseStore.adopt(key, secondRuntime).orElseThrow();
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();

            var release = leaseStore.release(key, claimed.owner());

            assertAll("a stale owner release leaves the newer holder",
                    () -> assertEquals(new LeaseStore.Release(false, adopted), release,
                            "the release names the adopting holder"),
                    () -> assertArrayEquals(written, Files.readAllBytes(store),
                            "the store stays byte-identical"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        @Test
        @DisplayName("a release that names an owner finds nothing under a key that is not held and writes nothing")
        void releaseByOwnerOfUnknownKey() {
            var owner = planLease(planId, firstRuntime).owner();

            var release = leaseStore.release(key, owner);

            assertAll("a release by owner of an unknown key writes nothing",
                    () -> assertEquals(new LeaseStore.Release(false, null), release,
                            "the release reports no removed lease"),
                    () -> assertFalse(Files.exists(store), "the store file is not created"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        @Test
        @DisplayName("releasing, adopting or orphaning a key that is not held returns nothing and writes nothing")
        void unknownKey() {
            var released = leaseStore.release(key);
            var adopted = leaseStore.adopt(key, secondRuntime);
            var orphaned = leaseStore.orphan(key, acquiredAt);

            assertAll("an unknown key returns nothing and writes nothing",
                    () -> assertEquals(Optional.empty(), released, "the release returns nothing"),
                    () -> assertEquals(Optional.empty(), adopted, "the adoption returns nothing"),
                    () -> assertEquals(Optional.empty(), orphaned, "the orphan returns nothing"),
                    () -> assertFalse(Files.exists(store), "the store file is not created"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }
    }

    @Nested
    @DisplayName("orphan and adopt")
    class OrphanAndAdopt {

        @Test
        @DisplayName("orphaning marks the lease and keeps it, its owner and its holder")
        void orphan() {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);

            var orphaned = leaseStore.orphan(key, orphanedAt).orElseThrow();

            assertAll("orphaning marks the lease and keeps owner and holder",
                    () -> assertEquals(lease.withOwner(lease.owner().orphaned(orphanedAt)), orphaned,
                            "the lease carries the orphaned owner"),
                    () -> assertEquals(orphanedAt, orphaned.owner().orphanedAt(),
                            "the orphaned instant is the one given"),
                    () -> assertEquals(firstRuntime, orphaned.owner().holderInstance(),
                            "the holder instance is kept"),
                    () -> assertEquals(List.of(orphaned), leaseStore.snapshot(),
                            "the store holds the orphaned lease"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        @Test
        @DisplayName("a lease that is orphaned already keeps the instant it was first found orphaned")
        void orphanTwice() throws Exception {
            leaseStore.claim(planLease(planId, firstRuntime));
            leaseStore.orphan(key, orphanedAt);
            var fileBefore = fileIdentity();

            var again = leaseStore.orphan(key, orphanedAt.plusSeconds(3600)).orElseThrow();

            assertAll("a second orphan keeps the first orphaned instant",
                    () -> assertEquals(orphanedAt, again.owner().orphanedAt(),
                            "the first orphaned instant is kept"),
                    () -> assertEquals(orphanedAt, leaseStore.snapshot().getFirst().owner().orphanedAt(),
                            "the store keeps the first orphaned instant"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("adopting names the new runtime as holder, clears orphaned_at and keeps the owning scope")
        void adopt() {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);
            leaseStore.orphan(key, orphanedAt);
            locks.reset();

            var adopted = leaseStore.adopt(key, secondRuntime).orElseThrow();

            assertAll("adopting names the new holder and keeps the owning scope",
                    () -> assertNull(adopted.owner().orphanedAt(), "orphaned_at is cleared"),
                    () -> assertFalse(adopted.owner().isOrphaned(), "the lease is no longer orphaned"),
                    () -> assertEquals(secondRuntime, adopted.owner().holderInstance(),
                            "the adopting runtime is the holder"),
                    () -> assertEquals(lease.withOwner(lease.owner().adoptedBy(secondRuntime)), adopted,
                            "the lease carries the adopted owner"),
                    () -> assertEquals(planId, adopted.owner().scopeId(), "the owning scope is kept"),
                    () -> assertEquals(List.of(adopted), leaseStore.snapshot(),
                            "the store holds the adopted lease"),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("an adopted lease is still held: a claim of its key names the adopting runtime")
        void adoptedLeaseIsHeld() {
            leaseStore.claim(planLease(planId, firstRuntime));
            leaseStore.orphan(key, orphanedAt);
            leaseStore.adopt(key, secondRuntime);

            var claim = leaseStore.claim(planLease(otherPlanId, firstRuntime));

            assertAll("an adopted lease is still held by the adopting runtime",
                    () -> assertFalse(claim.granted(), "the claim is not granted"),
                    () -> assertEquals(secondRuntime, claim.holder().owner().holderInstance(),
                            "the claim names the adopting runtime"));
        }

        @Test
        @DisplayName("an orphaned lease is still held: it is not released by being orphaned")
        void orphanedLeaseIsHeld() {
            leaseStore.claim(planLease(planId, firstRuntime));
            leaseStore.orphan(key, orphanedAt);

            var claim = leaseStore.claim(planLease(otherPlanId, secondRuntime));

            assertAll("an orphaned lease is still held",
                    () -> assertFalse(claim.granted(), "the claim is not granted"),
                    () -> assertTrue(claim.holder().owner().isOrphaned(),
                            "the held lease stays orphaned"));
        }
    }

    @Nested
    @DisplayName("revision of every lease")
    class ReviseAll {

        @Test
        @DisplayName("each lease is replaced or removed in one transaction on the machine store key")
        void revise() {
            var kept = planLease(planId, firstRuntime);
            var removed = new LeaseRecord(removedKey, kept.owner(), acquiredAt, null);
            var changed = new LeaseRecord(changedKey, kept.owner(), acquiredAt, null);
            leaseStore.claim(kept);
            leaseStore.claim(removed);
            leaseStore.claim(changed);
            locks.reset();
            var orphaned = changed.withOwner(changed.owner().orphaned(orphanedAt));

            var before = leaseStore.reviseAll(lease -> {
                if (removedKey.equals(lease.key())) {
                    return Optional.empty();
                }
                if (changedKey.equals(lease.key())) {
                    return Optional.of(orphaned);
                }
                return Optional.of(lease);
            });

            assertAll("each lease is replaced or removed in one transaction",
                    () -> assertEquals(Optional.of(List.of(kept, removed, changed)), before,
                            "the revision sees the leases in order"),
                    () -> assertEquals(List.of(kept, orphaned), leaseStore.snapshot(),
                            "the store holds the revised leases"),
                    LeaseStoreTest.this::assertOneTransactionOnTheStoreKey);
        }

        @Test
        @DisplayName("a revision that changes nothing does not write the store")
        void unchanged() throws Exception {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);
            var fileBefore = fileIdentity();

            var before = leaseStore.reviseAll(Optional::of);

            assertAll("a revision that changes nothing does not write the store",
                    () -> assertEquals(Optional.of(List.of(lease)), before,
                            "the revision sees the lease"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
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
            leaseStore.claim(planLease(planId, firstRuntime));
            leaseStore.release(key);

            var empty = leaseStore.reviseAll(Optional::of);

            assertAll("an absent store is told apart from an empty one",
                    () -> assertEquals(Optional.empty(), absent, "the absent store returns nothing"),
                    () -> assertFalse(absentStoreExists, "the absent store is not created"),
                    () -> assertEquals(List.of(), revised, "the absent store has no lease to revise"),
                    () -> assertEquals(Optional.of(List.of()), empty,
                            "the empty store returns an empty list"));
        }

        @Test
        @DisplayName("a revision that gives two leases one key is refused, and the store stays as it was")
        void duplicateKey() throws Exception {
            var first = planLease(planId, firstRuntime);
            leaseStore.claim(first);
            leaseStore.claim(new LeaseRecord(otherKey, first.owner(), acquiredAt, null));
            var written = Files.readAllBytes(store);

            assertThrows(IllegalArgumentException.class, () -> leaseStore.reviseAll(lease -> Optional.of(first)),
                    "two leases with one key are refused");

            assertAll("a refused revision leaves the store as it was",
                    () -> assertArrayEquals(written, Files.readAllBytes(store),
                            "the store stays byte-identical"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        /**
         * A lease under another key would take the claim away from the key it was given for and write a claim
         * nobody made. One lease is enough to show it: with a single lease no two leases share a key.
         */
        @Test
        @DisplayName("a revision that returns a lease under another key is refused, and the store stays as it was")
        void otherKey() throws Exception {
            var lease = planLease(planId, firstRuntime);
            leaseStore.claim(lease);
            var written = Files.readAllBytes(store);
            var fileBefore = fileIdentity();
            var underOtherKey = new LeaseRecord(otherKey, lease.owner(), acquiredAt, expiresAt);

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> leaseStore.reviseAll(_ -> Optional.of(underOtherKey)),
                    "a lease returned under another key is refused");

            assertAll("a lease under another key leaves the store as it was",
                    () -> assertTrue(refusal.getMessage().contains(key), refusal.getMessage()),
                    () -> assertTrue(refusal.getMessage().contains(otherKey), refusal.getMessage()),
                    () -> assertArrayEquals(written, Files.readAllBytes(store),
                            "the store stays byte-identical"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        @Test
        @DisplayName("the store names its file")
        void storePath() {
            assertEquals(store.toAbsolutePath().normalize(), leaseStore.store(),
                    "the store names its absolute file");
        }
    }

    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        @Test
        @DisplayName("a snapshot takes no lock and opens no transaction")
        void lockFree() {
            leaseStore.claim(planLease(planId, firstRuntime));
            locks.reset();

            var leases = leaseStore.snapshot();

            assertAll("a snapshot takes no lock and opens no transaction",
                    () -> assertEquals(1, leases.size(), "the snapshot holds the one lease"),
                    () -> assertEquals(0, locks.transactions(), "no transaction is opened"),
                    () -> assertEquals(List.of(), locks.acquisitions(), "no key is acquired"));
        }

        @Test
        @DisplayName("a store that does not exist has no leases")
        void absentStore() {
            assertEquals(List.of(), leaseStore.snapshot(),
                    "an absent store has no leases");
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
            var lease = planLease(planId, firstRuntime);

            var refusal = assertThrows(StoreRefusal.class, () -> leaseStore.claim(lease),
                    "a newer format store is refused");

            assertAll("a newer store is refused and left byte-identical",
                    () -> assertEquals(Outcome.FORMAT_NEWER, refusal.outcome(),
                            "the outcome is format newer"),
                    () -> assertEquals(leaseStore.store(), refusal.file(), "the store path is named"),
                    () -> assertEquals(OptionalInt.of(2), refusal.found(), "the found version is reported"),
                    () -> assertEquals(LeaseCodec.FORMAT_VERSION, refusal.current(),
                            "the current version is reported"),
                    () -> assertTrue(refusal.getMessage().contains(leaseStore.store().toString()),
                            refusal.getMessage()),
                    () -> assertArrayEquals(newer, Files.readAllBytes(store),
                            "the store stays byte-identical"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }

        @Test
        @DisplayName("an unreadable store is never taken for an empty one, by a write or by a snapshot")
        void unreadable() throws Exception {
            var torn = "{\"format_version\":1,\"leases\":[".getBytes(StandardCharsets.UTF_8);
            Files.write(store, torn);
            var lease = planLease(planId, firstRuntime);

            assertAll("an unreadable store is never taken for an empty one",
                    () -> assertEquals(Outcome.UNREADABLE,
                            assertThrows(StoreRefusal.class, () -> leaseStore.claim(lease)).outcome(),
                            "a claim of the torn store is unreadable"),
                    () -> assertEquals(Outcome.UNREADABLE,
                            assertThrows(StoreRefusal.class, () -> leaseStore.release(key)).outcome(),
                            "a release of the torn store is unreadable"),
                    () -> assertEquals(leaseStore.store(),
                            assertThrows(StoreRefusal.class, leaseStore::snapshot).file(),
                            "a snapshot names the torn store"),
                    () -> assertArrayEquals(torn, Files.readAllBytes(store),
                            "the torn store stays byte-identical"),
                    () -> assertEquals(List.of(), locks.held(), "the lock is released"));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a store file that is not guarded by a machine store lock is refused")
        void otherLockLevel() {
            var queueFile = new AtomicStoreFile(store, LockKey.queue(base, project));

            assertThrows(IllegalArgumentException.class, () -> new LeaseStore(queueFile, locks),
                    "a store file not guarded by the machine key is refused");
        }

        @Test
        @DisplayName("a missing store file, lock manager, lease, key, holder or instant is refused")
        void missingArguments() {
            var storeFile = new AtomicStoreFile(store, storeKey);

            assertAll("a missing argument is refused before the store is used",
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore(null, locks),
                            "a missing store file is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore(storeFile, null),
                            "a missing lock manager is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.claim(null),
                            "a missing lease is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.release(null),
                            "a missing key is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> leaseStore.release(null, firstOwner),
                            "a missing key before an owner is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.release(key, null),
                            "a missing owner is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(null, secondRuntime),
                            "a missing adopt key is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.adopt(key, null),
                            "a missing adopting holder is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.orphan(key, null),
                            "a missing orphan instant is refused"),
                    () -> assertThrows(NullPointerException.class, () -> leaseStore.reviseAll(null),
                            "a missing revision is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseStore.Claim(true, null),
                            "a claim without a lease is refused"),
                    () -> assertEquals(0, locks.transactions(), "no transaction is opened"));
        }
    }
}
