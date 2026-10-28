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
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.IntentAcceptance.Accepted;
import de.planmarshall.core.store.IntentAcceptance.Context;
import de.planmarshall.core.store.IntentAcceptance.Outcome;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * The writer and reader of a plan's {@code intent.json} beside {@code request.md} (Filesystem Layout § The Source
 * Directory; Store Schemas § Intent Record): the file the accepting transaction replaces.
 * <p>
 * {@link #accept(IntentSubmission, Revision, Context)} is one transaction on the plan lock of the store: it reads the
 * store, runs the {@link IntentAcceptance} checks and writes the accepted store back, so two submissions never
 * interleave and a refusal writes nothing. The directory exists before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class IntentStoreFile {

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * @param storeFile   the store file with the key of the plan lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not a plan lock
     */
    public IntentStoreFile(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.PLAN) {
            throw new IllegalArgumentException(
                    "An intent store is guarded by a plan lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Accepts a submission in one transaction on the lock of the store.
     *
     * @param submission the entry point the record arrived through
     * @param next       the revision the task submitted
     * @param context    what the checks need beyond the record
     * @return the accepted store, or the refusal with its detail
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store cannot be read or written
     */
    public Outcome accept(IntentSubmission submission, Revision next, Context context) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(context, "context");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElse(null);
            var outcome = IntentAcceptance.accept(before, submission, next, context);
            if (outcome instanceof Accepted(IntentStore store)) {
                storeFile.write(transaction, IntentCodec.write(store));
            }
            return outcome;
        }
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read.
     *
     * @return the store; empty when the plan holds no accepted record yet
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<IntentStore> snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private IntentStore decode(byte[] document) {
        return IntentCodec.read(storeFile.store(), document);
    }
}
