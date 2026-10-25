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

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;

/**
 * The writer and reader of a project's qualification settings, the {@code settings.qualification} member of its
 * {@code local-config.json} (Store Schemas § Operator Local Configuration).
 * <p>
 * The store never creates the operator's file: the project's onboarding writes it, and this store changes the one
 * member that bounds role qualification. A write is one short transaction on the project's local-configuration lock:
 * it reads the document, replaces the qualification block, increments the document's {@code revision} and writes the
 * document back, so every other member the operator set survives unchanged.
 *
 * @since 0.1
 */
public final class QualificationSettingsStore {

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * @param storeFile   the local-configuration file with the key of the local-configuration lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not the local-configuration lock of a project
     */
    public QualificationSettingsStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.LOCAL_CONFIGURATION) {
            throw new IllegalArgumentException(
                    "The qualification settings are guarded by the local-configuration lock: "
                            + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Reads the settings without a lock.
     *
     * @return the settings; the compiled defaults where the project set none
     * @throws StoreRefusal         if the document is unreadable
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public QualificationSettings read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(document -> QualificationSettingsCodec.read(storeFile.store(), document))
                .orElseGet(QualificationSettings::defaults);
    }

    /**
     * Replaces the settings in one transaction on the lock of the document and increments its revision.
     *
     * @param settings the settings
     * @throws StoreRefusal           if the document is unreadable, of another format version, or absent
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void write(QualificationSettings settings) {
        Objects.requireNonNull(settings, "settings");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var document = storeFile.read(transaction)
                    .orElseThrow(() -> StoreRefusal.absent(storeFile.store(), 1));
            storeFile.write(transaction, QualificationSettingsCodec.write(storeFile.store(), document, settings));
        }
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
