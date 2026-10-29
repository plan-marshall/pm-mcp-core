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
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.role.RoleAssignment;
import de.planmarshall.core.role.RoleAssignments;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The writer and reader of a role assignment store, {@code role-assignments.json} of the machine or of one project
 * (Store Schemas § Role Assignments, Model Work § Role Assignment). The machine's file is written by
 * {@code roles.write} of the machine's onboarding under {@code locks/role-assignments.lock}; a project's file, of the
 * same schema, under the project's {@code locks/local-config.lock}.
 * <p>
 * A write is one short transaction on the document's lock: it reads the document again, checks the caller's revision,
 * replaces the assignments and increments the revision. A change against an older revision is refused with a
 * {@link SettingsConflict} carrying the revision and the assignments the document holds, so nothing is merged and
 * nothing is overwritten unseen.
 *
 * @since 0.1
 */
public final class RoleAssignmentsStore {

    /** The name of the machine's file and of a project's file below its machine store. */
    public static final String FILE_NAME = "role-assignments.json";

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * What a read of a store found.
     *
     * @param roles    the assignments by role name
     * @param revision the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(Map<String, RoleAssignmentValue> roles, long revision) {

        /** Keeps the map unmodifiable. */
        public Snapshot {
            roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        }

        /**
         * @return the assignments as the role package reads them. The store's {@link EffortSetting#DEFAULT} (the level
         *         names {@code null} and clears the effort) and {@link EffortSetting#INHERIT} (the level names none)
         *         both read as an empty effort here, because {@code RoleAssignment} carries no third state; a caller
         *         that must tell them apart reads {@link #roles()}
         */
        public RoleAssignments assignments() {
            var read = new LinkedHashMap<String, RoleAssignment>();
            roles.forEach((role, value) -> read.put(role, value.assignment()));
            return new RoleAssignments(read);
        }
    }

    /**
     * @param storeFile   the {@code role-assignments.json} file with the key of its lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key is neither the machine's role assignment lock nor a project's
     *                                  local-configuration lock
     */
    public RoleAssignmentsStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        var key = storeFile.lockKey();
        var machine = key.level() == LockLevel.MACHINE_STORE && "role-assignments".equals(key.id());
        var project = key.level() == LockLevel.LOCAL_CONFIGURATION;
        if (!machine && !project) {
            throw new IllegalArgumentException("The role assignments are guarded by the assignment lock of the machine "
                    + "or the local-configuration lock of a project: " + key.lockFile());
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the machine's assignment store with the key of the machine's assignment lock
     */
    public static AtomicStoreFile machine(Path base) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        return new AtomicStoreFile(root.resolve(FILE_NAME), LockKey.roleAssignments(root));
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the project's assignment store with the key of the project's local-configuration lock
     */
    public static AtomicStoreFile project(Path base, String projectId) {
        return new AtomicStoreFile(LockKey.projectStore(base, projectId).resolve(FILE_NAME),
                LockKey.localConfiguration(base, projectId));
    }

    /**
     * Reads the assignments and the document's revision without a lock.
     *
     * @return the assignments and the revision; none and {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(this::snapshot)
                .orElseGet(() -> new Snapshot(Map.of(), NO_DOCUMENT));
    }

    /**
     * Replaces the assignments and increments the revision. The caller validates every value as the role's effective
     * configuration before it writes ({@link RoleAssignmentValidation}).
     *
     * @param roles    the assignments by role name
     * @param revision the revision the caller read
     * @throws IllegalArgumentException if a role name is not one
     * @throws SettingsConflict        if the document holds another revision than {@code revision}
     * @throws StoreRefusal            if the document is unreadable or in another format version
     * @throws UncheckedIOException    if the document cannot be read or written
     * @throws InternalFaultException  if the lock of the document must not be acquired by the transaction
     */
    public void write(Map<String, RoleAssignmentValue> roles, long revision) {
        Objects.requireNonNull(roles, "roles");
        roles.keySet().forEach(RoleAssignmentsCodec::requireRoleName);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var current = readLocked(transaction);
            if (current.revision() != revision) {
                throw new SettingsConflict(storeFile.store(), revision, current.revision(), current);
            }
            storeFile.write(transaction, RoleAssignmentsCodec.write(roles, revision + 1));
        }
    }

    private Snapshot readLocked(LockTransaction transaction) {
        return storeFile.read(transaction).map(this::snapshot).orElseGet(() -> new Snapshot(Map.of(), NO_DOCUMENT));
    }

    private Snapshot snapshot(byte[] document) {
        var read = RoleAssignmentsCodec.read(storeFile.store(), document);
        return new Snapshot(read.roles(), read.revision());
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
