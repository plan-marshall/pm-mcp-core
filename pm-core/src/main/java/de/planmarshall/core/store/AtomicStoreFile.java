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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;

/**
 * The one write path of a store that is replaced by atomic rename (PM-IMPL-7): a store file bound to the key of its
 * sibling lock file.
 * <p>
 * A write is a read-modify-write on a file that others read without a lock. Two rules keep it safe. The store is
 * read and written only by a transaction that holds the sibling key, so two writers never interleave. And the new
 * content is written to a temporary file beside the store, synced, and moved over the store in one atomic rename,
 * so a reader sees the old or the new content and nothing between.
 * <p>
 * The store file itself is never locked: the rename replaces the inode, and a lock on the old file would exclude
 * nobody. A key whose lock file is the store is therefore refused.
 * <p>
 * After the rename the directory of the store is forced to disk as well, so that the rename itself, and not only
 * the content it moved into place, survives a crash of the machine. This goes beyond what the specification of the
 * write states (temporary file, sync, atomic rename); it is a recorded decision of the operator.
 *
 * @param store   the store file; kept absolute and normalized
 * @param lockKey the key of the sibling lock file that guards the store
 * @since 0.1
 */
public record AtomicStoreFile(Path store, LockKey lockKey) {

    /**
     * Makes the store path absolute and normalized, so that it is compared with the lock file as the file it names.
     *
     * @throws IllegalArgumentException if the lock file of the key is the store file
     */
    public AtomicStoreFile {
        store = Objects.requireNonNull(store, "store").toAbsolutePath().normalize();
        Objects.requireNonNull(lockKey, "lockKey");
        if (store.equals(lockKey.lockFile())) {
            throw new IllegalArgumentException(
                    "A store replaced by rename is guarded by a sibling lock file, never by itself: " + store);
        }
    }

    /**
     * @param transaction a transaction that holds the key of the store
     * @return the current content of the store; empty if the store does not exist
     * @throws InternalFaultException if the transaction does not hold the key
     * @throws UncheckedIOException   if the store exists and cannot be read
     */
    public Optional<byte[]> read(LockTransaction transaction) {
        requireHeld(transaction);
        return StoreSnapshot.read(store).content();
    }

    /**
     * Replaces the store by the content in one atomic rename, and forces the directory of the store to disk.
     *
     * @param transaction a transaction that holds the key of the store
     * @param content     the new content of the store
     * @throws InternalFaultException if the transaction does not hold the key; nothing was written then
     * @throws UncheckedIOException   if the content cannot be written, and the store is unchanged then; or if the
     *                                store was replaced and its directory cannot be forced to disk, and the store has
     *                                the new content then, which is what the message says
     */
    public void write(LockTransaction transaction, byte[] content) {
        requireHeld(transaction);
        Objects.requireNonNull(content, "content");
        var temporary = store.resolveSibling(store.getFileName() + TransactionId.STAGED_TMP + UUID.randomUUID());
        try {
            try {
                replace(temporary, content);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Store cannot be written: " + store, e);
        }
        forceDirectoryOfStore();
    }

    /**
     * Replaces the store as {@link #write(LockTransaction, byte[])} does, for a journaled transaction: the temporary
     * file is {@code <store>.tmp.<transaction id>}, and it is not removed when the write fails. A write stopped at any
     * point thus leaves what a killed process leaves, and only files that the recovery of the transaction recognises
     * as its own.
     * <p>
     * A temporary file of the same transaction that a stopped write left is deleted before the write: the recovery of
     * the transaction writes the same store again under the same id, and the name belongs to this transaction only,
     * whose scope lock the caller holds.
     *
     * @param transaction   a transaction that holds the key of the store
     * @param content       the new content of the store
     * @param transactionId the transaction the write belongs to
     * @throws InternalFaultException if the transaction does not hold the key; nothing was written then
     * @throws UncheckedIOException   if the content cannot be written or the directory cannot be forced to disk
     */
    void write(LockTransaction transaction, byte[] content, TransactionId transactionId) {
        requireHeld(transaction);
        Objects.requireNonNull(content, "content");
        var temporary = store.resolveSibling(
                transactionId.stagedName(store.getFileName().toString(), TransactionJournal.Mode.REPLACE));
        try {
            Files.deleteIfExists(temporary);
            replace(temporary, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Store cannot be written: " + store, e);
        }
        forceDirectoryOfStore();
    }

    private void replace(Path temporary, byte[] content) throws IOException {
        SyncedFiles.writeNew(temporary, content);
        Files.move(temporary, store, StandardCopyOption.ATOMIC_MOVE);
    }

    private void forceDirectoryOfStore() {
        try {
            SyncedFiles.forceDirectory(store.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Store was replaced, but its directory cannot be forced to disk: " + store, e);
        }
    }

    private void requireHeld(LockTransaction transaction) {
        StoreLockGuard.requireHeld(transaction, store, lockKey);
    }
}
