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
 * it reads the document, checks that the caller's revision is the one the document holds, replaces the qualification
 * block, increments the document's {@code revision} and writes the document back, so every other member the operator
 * set survives unchanged and a change of another writer is never overwritten. A write against an older revision is
 * refused with a {@link StoreRefusal} of outcome {@link StoreRefusal.Outcome#CONFLICT conflict}.
 *
 * @since 0.1
 */
public final class QualificationSettingsStore {

    /** The format version of the operator's local configuration document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * What a read of the settings found: the settings and the revision the document carries.
     *
     * @param settings the settings; the compiled defaults where the project set none
     * @param revision the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(QualificationSettings settings, long revision) {

        /** @throws NullPointerException if the settings are {@code null} */
        public Snapshot {
            Objects.requireNonNull(settings, "settings");
        }
    }

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
     * Reads the settings and the document's revision without a lock.
     *
     * @return the settings and the revision; the compiled defaults and {@value #NO_DOCUMENT} if the document does not
     *         exist
     * @throws StoreRefusal         if the document is unreadable
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(document -> QualificationSettingsCodec.read(storeFile.store(), document))
                .map(read -> new Snapshot(read.settings(), read.revision()))
                .orElseGet(() -> new Snapshot(QualificationSettings.defaults(), NO_DOCUMENT));
    }

    /**
     * Replaces the settings in one transaction on the lock of the document and increments its revision.
     *
     * @param settings the settings
     * @param revision the revision the caller read
     * @throws StoreRefusal           as {@link StoreRefusal.Outcome#CONFLICT conflict} if the document holds another
     *                                revision than {@code revision}; if the document is unreadable, of another format
     *                                version, or absent
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void write(QualificationSettings settings, long revision) {
        Objects.requireNonNull(settings, "settings");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var document = storeFile.read(transaction)
                    .orElseThrow(() -> StoreRefusal.absent(storeFile.store(), FORMAT_VERSION));
            var current = QualificationSettingsCodec.read(storeFile.store(), document).revision();
            if (current != revision) {
                throw StoreRefusal.conflict(storeFile.store(), revision, current);
            }
            storeFile.write(transaction, QualificationSettingsCodec.write(storeFile.store(), document, settings));
        }
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
