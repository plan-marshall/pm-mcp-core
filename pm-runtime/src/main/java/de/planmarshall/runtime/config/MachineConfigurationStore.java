/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The writer and reader of the machine's provider defaults, {@code providers} of {@code machine-config.json} (Store
 * Schemas § Machine Configuration Store, § Machine Provider Defaults). The provider step of the machine's onboarding and
 * its later runs write them here, under {@code locks/machine-config.lock}.
 * <p>
 * A write is one short transaction on the lock: it reads the document again, checks the caller's revision, replaces
 * {@code providers} alone — every other member of the operator's document survives, among them
 * {@code credentials.store}, which only the credential-store aspect and the store move write — writes the document's
 * next {@code revision} and writes it back. A change against an older revision is refused with a
 * {@link SettingsConflict} carrying the revision and the defaults the document holds, so nothing is merged and nothing is
 * overwritten unseen. A document that does not exist yet is created with its required members at their schema defaults.
 *
 * @since 0.1
 */
public final class MachineConfigurationStore {

    /** The name of the machine configuration file below the base. */
    public static final String FILE_NAME = "machine-config.json";

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * What a read of the document found.
     *
     * @param providers the machine's provider defaults; none where the document records none
     * @param revision  the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(ProviderDefaults providers, long revision) {
    }

    /**
     * @param storeFile   the {@code machine-config.json} file with the key of the machine configuration lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not the machine configuration lock
     */
    public MachineConfigurationStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        var key = storeFile.lockKey();
        if (key.level() != LockLevel.MACHINE_STORE || !"machine-config".equals(key.id())) {
            throw new IllegalArgumentException("The machine configuration is guarded by its own lock: "
                    + key.lockFile());
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the store file with the key of the machine configuration lock
     */
    public static AtomicStoreFile file(Path base) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        return new AtomicStoreFile(root.resolve(FILE_NAME), LockKey.machineConfiguration(root));
    }

    /**
     * Reads the provider defaults and the document's revision without a lock.
     *
     * @return the defaults and the revision; none and {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(document -> MachineConfigurationCodec.read(storeFile.store(), document))
                .map(read -> new Snapshot(read.providers(), read.revision()))
                .orElseGet(() -> new Snapshot(ProviderDefaults.none(), NO_DOCUMENT));
    }

    /**
     * Replaces the machine's provider defaults and increments the revision.
     *
     * @param providers the defaults the provider step recorded
     * @param revision  the revision the caller read
     * @throws SettingsConflict       if the document holds another revision than {@code revision}
     * @throws StoreRefusal           if the document is unreadable or in another format version
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void writeProviders(ProviderDefaults providers, long revision) {
        Objects.requireNonNull(providers, "providers");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var document = storeFile.read(transaction);
            var current = document.map(bytes -> MachineConfigurationCodec.read(storeFile.store(), bytes))
                    .orElse(null);
            var currentRevision = current == null ? NO_DOCUMENT : current.revision();
            if (currentRevision != revision) {
                var value = current == null ? (Object) ProviderDefaults.none() : current.providers();
                throw new SettingsConflict(storeFile.store(), revision, currentRevision, value);
            }
            storeFile.write(transaction, document
                    .map(bytes -> MachineConfigurationCodec.write(storeFile.store(), bytes, providers, revision + 1))
                    .orElseGet(() -> MachineConfigurationCodec.create(providers)));
        }
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
