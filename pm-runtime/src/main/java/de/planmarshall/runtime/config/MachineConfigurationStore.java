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
import java.util.function.Function;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The writer and reader of the machine's settings of {@code machine-config.json} that this package owns (Store Schemas §
 * Machine Configuration Store): the provider defaults ({@code providers}), how a start takes its documents
 * ({@code sources.documents_mode}), the durations of the one cleanup ({@code cleanup}) and the machine's default of each
 * plan behaviour axis ({@code plan_defaults}). The provider step of the machine's onboarding and its later runs write the
 * defaults, the machine's settings write the other three, all under {@code locks/machine-config.lock}.
 * <p>
 * A write is one short transaction on the lock: it reads the document again, checks the caller's revision, replaces that
 * one member — every other member of the operator's document survives, among them {@code credentials.store}, which only
 * the credential-store aspect and the store move write — writes the document's next {@code revision} and writes it back. A
 * change against an older revision is refused with a {@link SettingsConflict} carrying the revision and the current value
 * of that member, so nothing is merged and nothing is overwritten unseen. A document that does not exist yet is created
 * with its required members at their schema defaults.
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
     * @param providers     the machine's provider defaults; none where the document records none
     * @param documentsMode the machine's {@code sources.documents_mode}; {@link DocumentsMode#DEFAULT} where the
     *                      document records none, which is the value the schema applies
     * @param cleanup       the machine's {@code cleanup} durations; {@link CleanupDurations#defaults()} where the
     *                      document records none, which is the value the schema applies
     * @param planDefaults  the machine's {@code plan_defaults}; {@link PlanDefaults#shipped()} where the document records
     *                      none, which is the value the schema applies
     * @param revision      the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    @Builder
    public record Snapshot(ProviderDefaults providers, DocumentsMode documentsMode, CleanupDurations cleanup,
    PlanDefaults planDefaults, long revision) {
    }

    /**
     * @param storeFile   the {@code machine-config.json} file with the key of the machine configuration lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not the machine configuration lock, its lock file
     *                                  lies too near the root to have a base above its lock directory, or the store
     *                                  file is not {@value #FILE_NAME} below the base of that lock
     */
    public MachineConfigurationStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        var key = storeFile.lockKey();
        if (key.level() != LockLevel.MACHINE_STORE || !"machine-config".equals(key.id())) {
            throw new IllegalArgumentException("The machine configuration is guarded by its own lock: "
                    + key.lockFile());
        }
        var locks = key.lockFile().getParent();
        var base = locks == null ? null : locks.getParent();
        if (base == null) {
            throw new IllegalArgumentException("The lock of the machine configuration has no base above its lock "
                    + "directory: " + key.lockFile());
        }
        if (!storeFile.store().equals(base.resolve(FILE_NAME))) {
            throw new IllegalArgumentException("The machine configuration is " + FILE_NAME + " below "
                    + base + ": " + storeFile.store());
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
     * Reads the members this store owns and the document's revision without a lock.
     *
     * @return the defaults and settings at their recorded value, or at the schema default where the document records
     *         none; the revision is {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(document -> MachineConfigurationCodec.read(storeFile.store(), document))
                .map(read -> new Snapshot(read.providers(),
                        read.documentsMode() == null ? DocumentsMode.DEFAULT : read.documentsMode(),
                        read.cleanup() == null ? CleanupDurations.defaults() : read.cleanup(),
                        read.planDefaults() == null ? PlanDefaults.shipped() : read.planDefaults(), read.revision()))
                .orElseGet(() -> new Snapshot(ProviderDefaults.none(), DocumentsMode.DEFAULT,
                        CleanupDurations.defaults(), PlanDefaults.shipped(), NO_DOCUMENT));
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
        transact(revision, current -> current == null
                ? ProviderDefaults.none()
                : current.providers(), bytes -> bytes == null
                ? MachineConfigurationCodec.create(providers)
                : MachineConfigurationCodec.write(storeFile.store(), bytes, providers, revision + 1));
    }

    /**
     * Records how a start takes its documents, the machine's {@code sources.documents_mode}, and increments the
     * revision.
     *
     * @param mode     the mode the machine's settings recorded
     * @param revision the revision the caller read
     * @throws SettingsConflict       if the document holds another revision than {@code revision}
     * @throws StoreRefusal           if the document is unreadable or in another format version
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void writeDocumentsMode(DocumentsMode mode, long revision) {
        Objects.requireNonNull(mode, "mode");
        transact(revision,
                current -> current == null || current.documentsMode() == null
                        ? DocumentsMode.DEFAULT
                        : current.documentsMode(),
                bytes -> bytes == null
                        ? MachineConfigurationCodec.createDocumentsMode(mode)
                        : MachineConfigurationCodec.writeDocumentsMode(storeFile.store(), bytes, mode,
                        revision + 1));
    }

    /**
     * Records the durations of the one cleanup, the machine's {@code cleanup} member, and increments the revision.
     *
     * @param durations the durations the machine's settings recorded
     * @param revision  the revision the caller read
     * @throws SettingsConflict       if the document holds another revision than {@code revision}
     * @throws StoreRefusal           if the document is unreadable or in another format version
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void writeCleanupDurations(CleanupDurations durations, long revision) {
        Objects.requireNonNull(durations, "durations");
        transact(revision,
                current -> current == null || current.cleanup() == null
                        ? CleanupDurations.defaults()
                        : current.cleanup(),
                bytes -> bytes == null
                        ? MachineConfigurationCodec.createCleanup(durations)
                        : MachineConfigurationCodec.writeCleanup(storeFile.store(), bytes, durations,
                        revision + 1));
    }

    /**
     * Records the machine's default of each plan behaviour axis, {@code plan_defaults}, and increments the revision.
     *
     * @param defaults the plan defaults the machine's settings recorded
     * @param revision the revision the caller read
     * @throws SettingsConflict       if the document holds another revision than {@code revision}
     * @throws StoreRefusal           if the document is unreadable or in another format version
     * @throws UncheckedIOException   if the document cannot be read or written
     * @throws InternalFaultException if the lock of the document must not be acquired by the transaction
     */
    public void writePlanDefaults(PlanDefaults defaults, long revision) {
        Objects.requireNonNull(defaults, "defaults");
        transact(revision,
                current -> current == null || current.planDefaults() == null
                        ? PlanDefaults.shipped()
                        : current.planDefaults(),
                bytes -> bytes == null
                        ? MachineConfigurationCodec.createPlanDefaults(defaults)
                        : MachineConfigurationCodec.writePlanDefaults(storeFile.store(), bytes, defaults,
                        revision + 1));
    }

    /**
     * Writes one member in the document's transaction: it reads the document again, refuses a revision the document
     * moved past, and writes what {@code composer} returns.
     *
     * @param <T>          the type of the member written
     * @param revision     the revision the caller read
     * @param currentValue the current value of the member, for the refusal
     * @param composer     the new document, from the document as read ({@code null} when it does not exist)
     */
    private <T> void transact(long revision, Function<MachineConfigurationCodec.@Nullable Document, T> currentValue,
            Composer composer) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var document = storeFile.read(transaction);
            var current = document.map(bytes -> MachineConfigurationCodec.read(storeFile.store(), bytes))
                    .orElse(null);
            var currentRevision = current == null ? NO_DOCUMENT : current.revision();
            if (currentRevision != revision) {
                throw new SettingsConflict(storeFile.store(), revision, currentRevision, currentValue.apply(current));
            }
            storeFile.write(transaction, composer.compose(document.orElse(null)));
        }
    }

    /** Composes the new document from the document as read, or from none. */
    @FunctionalInterface
    private interface Composer {

        /**
         * @param document the document as read; {@code null} when it does not exist
         * @return the document to write
         */
        byte[] compose(byte @Nullable [] document);
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
