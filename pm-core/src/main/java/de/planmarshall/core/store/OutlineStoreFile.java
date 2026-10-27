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
import de.planmarshall.core.store.OutlineStore.Revision;

/**
 * The writer and reader of a plan's {@code outline.json} (Store Schemas § Outline Record): the file the accepting
 * transaction replaces, and the record {@link OutlineRenderer} renders {@code outline.md} from.
 * <p>
 * {@link #accept(Revision)} is one transaction on the plan lock of the store: it reads the store, accepts the next
 * revision and writes the store back, so two submissions never interleave. The directory exists before the first
 * write; this type does not create it.
 *
 * @since 0.1
 */
public final class OutlineStoreFile {

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * @param storeFile   the store file with the key of the plan lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not a plan lock
     */
    public OutlineStoreFile(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.PLAN) {
            throw new IllegalArgumentException(
                    "An outline store is guarded by a plan lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Accepts the next revision in one transaction on the lock of the store.
     *
     * @param next the revision the task submitted
     * @return the store after the acceptance
     * @throws IllegalArgumentException if the revision does not follow the current one
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws UncheckedIOException     if the store cannot be read or written
     */
    public OutlineStore accept(Revision next) {
        Objects.requireNonNull(next, "next");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElse(null);
            var after = before == null ? OutlineStore.first(next) : before.accept(next);
            storeFile.write(transaction, OutlineCodec.write(after));
            return after;
        }
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read.
     *
     * @return the store; empty when the plan holds no accepted outline yet
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<OutlineStore> snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private OutlineStore decode(byte[] document) {
        return OutlineCodec.read(storeFile.store(), document);
    }
}
