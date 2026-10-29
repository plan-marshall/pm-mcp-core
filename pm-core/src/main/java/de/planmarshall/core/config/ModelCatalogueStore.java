/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The reader and writer of a model catalogue, {@code model-catalogue.json} of the machine or of one project (Store
 * Schemas § Model Catalogue, PM-WORK-1). The machine's file is written by the catalogue step of the machine's onboarding
 * under {@code locks/model-catalogue.lock}; a project's file, of the same schema, under the project's
 * {@code locks/local-config.lock}.
 * <p>
 * A write is one short transaction on the document's lock: it reads the document again, checks the caller's revision,
 * drops the entries of every harness that is no longer enrolled, writes the allowed models it is given and increments
 * the revision. A change against an older revision is refused with a {@link SettingsConflict} carrying the revision and
 * the catalogue the document holds, so nothing is merged and nothing is overwritten unseen.
 *
 * @since 0.1
 */
public final class ModelCatalogueStore {

    /** The name of the machine's file and of a project's file below its machine store. */
    public static final String FILE_NAME = "model-catalogue.json";

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * What a read of a catalogue found.
     *
     * @param harnesses the allowed models per client id of an enrolled harness
     * @param revision  the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(Map<String, List<CatalogueModel>> harnesses, long revision) {

        /** Keeps the map and its lists unmodifiable. */
        public Snapshot {
            var copy = new LinkedHashMap<String, List<CatalogueModel>>();
            harnesses.forEach((harness, models) -> copy.put(harness, List.copyOf(models)));
            harnesses = Collections.unmodifiableMap(copy);
        }

        /**
         * @return the read projection the role package validates a model against
         */
        public ModelCatalogue catalogue() {
            var allowed = new LinkedHashMap<String, Set<String>>();
            harnesses.forEach((harness, models) -> allowed.put(harness,
                    models.stream().map(CatalogueModel::model).collect(Collectors.toSet())));
            return new ModelCatalogue(allowed);
        }

        /**
         * @param harness the client id of a harness
         * @param model   a model identifier
         * @return whether the catalogue allows the model on the harness
         */
        public boolean allows(String harness, String model) {
            return harnesses.getOrDefault(harness, List.of()).stream()
                    .anyMatch(entry -> entry.model().equals(model));
        }
    }

    /**
     * @param storeFile   the {@code model-catalogue.json} file with the key of its lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key is neither the machine's model catalogue lock nor a project's
     *                                  local-configuration lock
     */
    public ModelCatalogueStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        var key = storeFile.lockKey();
        var machine = key.level() == LockLevel.MACHINE_STORE && "model-catalogue".equals(key.id());
        var project = key.level() == LockLevel.LOCAL_CONFIGURATION;
        if (!machine && !project) {
            throw new IllegalArgumentException("The model catalogue is guarded by the catalogue lock of the machine or "
                    + "the local-configuration lock of a project: " + key.lockFile());
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the machine's catalogue with the key of the machine's catalogue lock
     */
    public static AtomicStoreFile machine(Path base) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        return new AtomicStoreFile(root.resolve(FILE_NAME), LockKey.modelCatalogue(root));
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the project's catalogue with the key of the project's local-configuration lock
     */
    public static AtomicStoreFile project(Path base, String projectId) {
        return new AtomicStoreFile(LockKey.projectStore(base, projectId).resolve(FILE_NAME),
                LockKey.localConfiguration(base, projectId));
    }

    /**
     * Reads the catalogue and the document's revision without a lock.
     *
     * @return the allowed models and the revision; none and {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(this::snapshot)
                .orElseGet(() -> new Snapshot(Map.of(), NO_DOCUMENT));
    }

    /**
     * Replaces the allowed models and increments the revision. An entry of a harness that is no longer enrolled is
     * dropped.
     *
     * @param harnesses          the allowed models per client id of an enrolled harness
     * @param enrolledHarnesses  the client ids of the harnesses enrolled now
     * @param revision           the revision the caller read
     * @throws IllegalArgumentException if a harness key is not a client id
     * @throws SettingsConflict         if the document holds another revision than {@code revision}
     * @throws StoreRefusal             if the document is unreadable or in another format version
     * @throws UncheckedIOException     if the document cannot be read or written
     * @throws InternalFaultException   if the lock of the document must not be acquired by the transaction
     */
    public void write(Map<String, List<CatalogueModel>> harnesses, Set<String> enrolledHarnesses, long revision) {
        Objects.requireNonNull(harnesses, "harnesses");
        Objects.requireNonNull(enrolledHarnesses, "enrolledHarnesses");
        var kept = new LinkedHashMap<String, List<CatalogueModel>>();
        harnesses.forEach((harness, models) -> {
            ModelCatalogueCodec.requireClientId(harness);
            if (enrolledHarnesses.contains(harness)) {
                kept.put(harness, List.copyOf(models));
            }
        });
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var current = readLocked(transaction);
            if (current.revision() != revision) {
                throw new SettingsConflict(storeFile.store(), revision, current.revision(), current);
            }
            storeFile.write(transaction, ModelCatalogueCodec.write(kept, revision + 1));
        }
    }

    private Snapshot readLocked(LockTransaction transaction) {
        return storeFile.read(transaction).map(this::snapshot).orElseGet(() -> new Snapshot(Map.of(), NO_DOCUMENT));
    }

    private Snapshot snapshot(byte[] document) {
        var read = ModelCatalogueCodec.read(storeFile.store(), document);
        return new Snapshot(read.harnesses(), read.revision());
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
