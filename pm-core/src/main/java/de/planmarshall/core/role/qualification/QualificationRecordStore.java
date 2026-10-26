/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The qualification records of one role set (Store Schemas § Role Qualification Record; Filesystem Layout § Role Sets):
 * one file per role, {@code qualification/<role>.json}, written by atomic write-and-rename under a transaction of the
 * store's lock. A new grading replaces the role's record.
 * <p>
 * The store is the project set's writer and reader. The shipped set's records lie on the classpath and are read through
 * another {@link QualificationRecords} implementation; a lookup that must see both reads the project set first.
 *
 * @since 0.1
 */
public final class QualificationRecordStore implements QualificationRecords {

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    private final Path directory;
    private final LockKey lockKey;
    private final LockManager lockManager;

    /**
     * @param directory   the qualification directory of the role set, {@code <set>/qualification/}
     * @param lockKey     the key of the lock a write runs under
     * @param lockManager the lock manager of the runtime
     */
    public QualificationRecordStore(Path directory, LockKey lockKey, LockManager lockManager) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    /**
     * @param role a role name
     * @return the record of the role; empty if the set holds none
     * @throws StoreRefusal           if the record exists and is unreadable
     * @throws UncheckedIOException   if the record exists and cannot be read
     * @throws IllegalArgumentException if the role name is not a role's grammar
     */
    @Override
    public Optional<QualificationRecord> lookup(String role) {
        var file = file(role);
        return StoreSnapshot.read(file).content()
                .map(document -> QualificationRecordCodec.read(file, document));
    }

    /**
     * Writes the role's record, replacing the one the set held, in one transaction of the store's lock.
     *
     * @param record the record
     * @throws StoreRefusal           never for a conflict: a record is replaced, not versioned
     * @throws UncheckedIOException   if the record cannot be written
     * @throws InternalFaultException if the lock must not be acquired by the transaction
     */
    public void write(QualificationRecord record) {
        Objects.requireNonNull(record, "record");
        var file = file(record.role());
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("The qualification directory cannot be created: " + directory, e);
        }
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            new AtomicStoreFile(file, lockKey).write(transaction, QualificationRecordCodec.write(record));
        }
    }

    /**
     * @param role a role name
     * @return the file of the role's record, absolute and normalized
     */
    public Path file(String role) {
        Objects.requireNonNull(role, "role");
        if (!ROLE.matcher(role).matches()) {
            throw new IllegalArgumentException("Not a role name: " + role);
        }
        return directory.resolve(role + ".json");
    }

    /** @return the qualification directory, absolute and normalized */
    public Path directory() {
        return directory;
    }
}
