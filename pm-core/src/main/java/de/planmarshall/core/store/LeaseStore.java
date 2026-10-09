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

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;

/**
 * The leases of one machine lease store (PM-IMPL-7). A long-lived claim is a record in the store and never a lock of
 * the operating system: it survives the transaction that wrote it and the runtime that wrote it, and it is found
 * again by any instance that reads the store.
 * <p>
 * Every writing method is one short transaction: it takes the lock of the store, reads the store, decides, writes,
 * and releases the lock, so the decision and the write see the same leases and no second writer comes between them.
 * The lock is held for that transaction only. A caller may therefore hold a lease while it takes any other lock;
 * the lock order is not touched by it.
 * <p>
 * The store keeps the lease record all lease stores have in common. What a single store adds to it (a waiting list,
 * a slot limit) belongs to the type of that store.
 * <p>
 * The directory of the store file exists before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class LeaseStore {

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * The outcome of a claim.
     *
     * @param granted whether the lease was written for the claimer
     * @param holder  the lease that holds the key now: the claimed one if granted, else the one found
     * @since 0.1
     */
    public record Claim(boolean granted, LeaseRecord holder) {

        /** @throws NullPointerException if the holder is {@code null} */
        public Claim {
            Objects.requireNonNull(holder, "holder");
        }
    }

    /**
     * @param storeFile   the lease store file with the key of its machine store lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not a machine store lock
     */
    public LeaseStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.MACHINE_STORE) {
            throw new IllegalArgumentException(
                    "A lease store is guarded by a machine store lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Claims the key of the lease. A key that is held is not claimed again, whoever holds it: nothing is written,
     * and the outcome names the lease that holds it.
     *
     * @param lease the lease to write
     * @return whether the lease was written, and the lease that holds the key now
     * @throws LeaseCodec.FormatException if the store is in another format version or unreadable
     * @throws UncheckedIOException       if the store cannot be read or written
     * @throws InternalFaultException     if the lock of the store must not be acquired by the transaction
     */
    public Claim claim(LeaseRecord lease) {
        Objects.requireNonNull(lease, "lease");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var leases = load(transaction);
            var held = leases.get(lease.key());
            if (held != null) {
                return new Claim(false, held);
            }
            leases.put(lease.key(), lease);
            save(transaction, leases);
            return new Claim(true, lease);
        }
    }

    /**
     * Releases the lease of the key.
     *
     * @param key the key of a lease
     * @return the released lease; empty if the key was not held, and nothing was written then
     * @throws LeaseCodec.FormatException if the store is in another format version or unreadable
     * @throws UncheckedIOException       if the store cannot be read or written
     * @throws InternalFaultException     if the lock of the store must not be acquired by the transaction
     */
    public Optional<LeaseRecord> release(String key) {
        Objects.requireNonNull(key, "key");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var leases = load(transaction);
            var released = leases.remove(key);
            if (released != null) {
                save(transaction, leases);
            }
            return Optional.ofNullable(released);
        }
    }

    /**
     * Adopts the lease of the key for a runtime: the runtime becomes its holder, and the lease is no longer
     * orphaned. The owning scope is unchanged.
     *
     * @param key       the key of a lease
     * @param newHolder the runtime instance that serves the lease from now on
     * @return the adopted lease; empty if the key is not held
     * @throws LeaseCodec.FormatException if the store is in another format version or unreadable
     * @throws UncheckedIOException       if the store cannot be read or written
     * @throws InternalFaultException     if the lock of the store must not be acquired by the transaction
     */
    public Optional<LeaseRecord> adopt(String key, HolderInstance newHolder) {
        Objects.requireNonNull(newHolder, "newHolder");
        return change(key, lease -> lease.withOwner(lease.owner().adoptedBy(newHolder)));
    }

    /**
     * Marks the lease of the key as orphaned: its holder is no longer alive. The lease is kept, for its owning
     * scope to adopt. A lease that is orphaned already keeps the instant it was first found orphaned.
     *
     * @param key        the key of a lease
     * @param orphanedAt the instant the holder was found dead
     * @return the orphaned lease; empty if the key is not held
     * @throws LeaseCodec.FormatException if the store is in another format version or unreadable
     * @throws UncheckedIOException       if the store cannot be read or written
     * @throws InternalFaultException     if the lock of the store must not be acquired by the transaction
     */
    public Optional<LeaseRecord> orphan(String key, Instant orphanedAt) {
        Objects.requireNonNull(orphanedAt, "orphanedAt");
        return change(key,
                lease -> lease.owner().isOrphaned() ? lease : lease.withOwner(lease.owner().orphaned(orphanedAt)));
    }

    /**
     * Reads the leases without a lock, as they are in the store at the moment of the read. A write replaces the
     * store in one step, so the result is the leases before or after a write and never a part of one; it may be
     * out of date as soon as it is returned, and nothing is decided on it that a write depends on.
     *
     * @return the leases of the store, in its order; empty if the store does not exist
     * @throws LeaseCodec.FormatException if the store is in another format version or unreadable
     * @throws UncheckedIOException       if the store exists and cannot be read
     */
    public List<LeaseRecord> snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode).orElseGet(List::of);
    }

    private Optional<LeaseRecord> change(String key, UnaryOperator<LeaseRecord> change) {
        Objects.requireNonNull(key, "key");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var leases = load(transaction);
            var current = leases.get(key);
            if (current == null) {
                return Optional.empty();
            }
            var changed = change.apply(current);
            if (!changed.equals(current)) {
                leases.put(key, changed);
                save(transaction, leases);
            }
            return Optional.of(changed);
        }
    }

    private Map<String, LeaseRecord> load(LockTransaction transaction) {
        var leases = new LinkedHashMap<String, LeaseRecord>();
        storeFile.read(transaction).map(this::decode).orElseGet(List::of)
                .forEach(lease -> leases.put(lease.key(), lease));
        return leases;
    }

    private void save(LockTransaction transaction, Map<String, LeaseRecord> leases) {
        storeFile.write(transaction, LeaseCodec.write(leases.values()));
    }

    private List<LeaseRecord> decode(byte[] document) {
        try {
            return LeaseCodec.read(document);
        } catch (LeaseCodec.FormatException e) {
            throw e.at(storeFile.store());
        }
    }
}
