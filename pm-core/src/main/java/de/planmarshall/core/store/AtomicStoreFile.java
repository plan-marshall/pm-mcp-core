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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
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
 *
 * @since 0.1
 */
public final class AtomicStoreFile {

    private static final String OWNER_ONLY = "rw-------";

    private final Path store;
    private final LockKey lockKey;

    /**
     * @param store   the store file
     * @param lockKey the key of the sibling lock file that guards the store
     * @throws IllegalArgumentException if the lock file of the key is the store file
     */
    public AtomicStoreFile(Path store, LockKey lockKey) {
        this.store = Objects.requireNonNull(store, "store").toAbsolutePath().normalize();
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
        if (this.store.equals(lockKey.lockFile())) {
            throw new IllegalArgumentException(
                    "A store replaced by rename is guarded by a sibling lock file, never by itself: " + this.store);
        }
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return store;
    }

    /** @return the key of the sibling lock file that guards the store */
    public LockKey lockKey() {
        return lockKey;
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
     * Replaces the store by the content in one atomic rename.
     *
     * @param transaction a transaction that holds the key of the store
     * @param content     the new content of the store
     * @throws InternalFaultException if the transaction does not hold the key; nothing was written then
     * @throws UncheckedIOException   if the content cannot be written; the store is unchanged then
     */
    public void write(LockTransaction transaction, byte[] content) {
        requireHeld(transaction);
        Objects.requireNonNull(content, "content");
        var temporary = store.resolveSibling(store.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            try {
                writeSynced(temporary, content);
                Files.move(temporary, store, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Store cannot be written: " + store, e);
        }
    }

    private static void writeSynced(Path temporary, byte[] content) throws IOException {
        var ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY));
        try (var channel = FileChannel.open(temporary,
                     Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), ownerOnly)) {
            var buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void requireHeld(LockTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.holds(lockKey)) {
            throw new InternalFaultException(Reason.STORE_LOCK_NOT_HELD,
                    "Store '%s' accessed without its lock '%s'".formatted(store, lockKey.lockFile()),
                    Map.of("store", store.toString(), "lock", lockKey.lockFile().toString()));
        }
    }
}
