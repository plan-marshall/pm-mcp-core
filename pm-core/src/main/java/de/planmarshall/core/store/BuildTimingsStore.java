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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.BuildTimings.Baseline;
import de.planmarshall.core.store.BuildTimings.KeyTimings;
import de.planmarshall.core.store.BuildTimings.Residue;

/**
 * The writer and reader of a project's {@code build-timings.json} (Store Schemas § Build Timings Store): the learned
 * budgets that the allocation of a job's budget reads.
 * <p>
 * Every writing method is one short transaction on the build-timings lock of the project: it reads the store, changes
 * the entry of one key and writes the store back, so two observations never interleave. The store lives in the
 * per-project machine store; the directory exists before the first write and this type does not create it.
 * <p>
 * A finished run feeds the baseline of its kind with the max-biased blend of the specification, and clears the
 * one-shot escalation. A timed-out run never reaches {@link #observe(String, Duration, Residue, Instant)}: it sets the
 * escalation through {@link #escalate(String, long, long)} instead.
 *
 * @since 0.1
 */
public final class BuildTimingsStore {

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * @param storeFile   the store file with the key of the build-timings lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not a build-timings lock
     */
    public BuildTimingsStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.BUILD_TIMINGS) {
            throw new IllegalArgumentException(
                    "A build-timings store is guarded by a build-timings lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Feeds a finished run into the baseline of its kind and clears the one-shot escalation, in one transaction on the
     * lock of the store.
     *
     * @param key        the budget key, {@code llm:<role>} for a worker
     * @param duration   the duration of the finished run
     * @param residue    the kind of the run
     * @param observedAt the instant the run finished
     * @return the timings of the key after the observation
     * @throws StoreRefusal           if the store is in another format version or unreadable
     * @throws UncheckedIOException   if the store cannot be read or written
     * @throws InternalFaultException if the lock of the store must not be acquired by the transaction
     */
    public KeyTimings observe(String key, Duration duration, Residue residue, Instant observedAt) {
        requireKey(key);
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(observedAt, "observedAt");
        var seconds = Math.max(1, duration.toSeconds());
        return update(key, timings -> new KeyTimings(observed(timings.clean(), residue, Residue.CLEAN, seconds),
                observed(timings.poisoned(), residue, Residue.POISONED, seconds),
                observed(timings.cold(), residue, Residue.COLD, seconds), null, observedAt));
    }

    /**
     * Sets the one-shot escalation of a timed-out run, in one transaction on the lock of the store:
     * {@code E = max(t, min(2 * t, C))}. A timed-out run never feeds a baseline.
     *
     * @param key           the budget key
     * @param budgetSeconds the allocated budget {@code t} of the timed-out run
     * @param capSeconds    the escalation cap {@code C}
     * @return the timings of the key after the escalation
     * @throws StoreRefusal           if the store is in another format version or unreadable
     * @throws UncheckedIOException   if the store cannot be read or written
     * @throws InternalFaultException if the lock of the store must not be acquired by the transaction
     */
    public KeyTimings escalate(String key, long budgetSeconds, long capSeconds) {
        requireKey(key);
        if (budgetSeconds < 1 || capSeconds < 1) {
            throw new IllegalArgumentException("The budget and its cap are at least one second");
        }
        var escalation = Math.clamp(2 * budgetSeconds, budgetSeconds, capSeconds);
        return update(key, timings -> new KeyTimings(timings.clean(), timings.poisoned(), timings.cold(), escalation,
                timings.lastObservedAt()));
    }

    /**
     * @param key the budget key
     * @return the timings of the key; empty when no run of the key has finished and no escalation is owed
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<KeyTimings> timings(String key) {
        requireKey(key);
        return snapshot().timings(key);
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read.
     *
     * @return the store; the empty one if the store does not exist
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public BuildTimings snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode).orElseGet(BuildTimings::empty);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private KeyTimings update(String key, UnaryOperator<KeyTimings> change) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElseGet(BuildTimings::empty);
            var timings = change.apply(before.timings(key).orElseGet(KeyTimings::empty));
            var keys = new LinkedHashMap<>(before.keys());
            keys.put(key, timings);
            storeFile.write(transaction, BuildTimingsCodec.write(new BuildTimings(keys)));
            return timings;
        }
    }

    private static Baseline observed(Baseline baseline, Residue residue, Residue wanted, long seconds) {
        return residue == wanted ? Baseline.observed(baseline, seconds) : baseline;
    }

    private BuildTimings decode(byte[] document) {
        return BuildTimingsCodec.read(storeFile.store(), document);
    }

    private static void requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!BuildTimings.BUDGET_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a budget key: " + key);
        }
    }
}
