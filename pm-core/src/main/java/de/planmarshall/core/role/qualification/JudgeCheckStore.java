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

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The project set's judge check record (Store Schemas § Judge Check Record; Filesystem Layout § Role Sets):
 * {@code qualification/judge-check.json}, written by atomic write-and-rename under a transaction of the store's lock. A
 * new check replaces the record.
 *
 * @since 0.1
 */
public final class JudgeCheckStore implements JudgeChecks {

    /** The file name of the record below the qualification directory. */
    public static final String FILE_NAME = "judge-check.json";

    private final Path file;
    private final LockKey lockKey;
    private final LockManager lockManager;

    /**
     * @param qualificationDirectory the {@code qualification/} directory of the project role set
     * @param lockKey                the key of the lock a write runs under
     * @param lockManager            the lock manager of the runtime
     */
    public JudgeCheckStore(Path qualificationDirectory, LockKey lockKey, LockManager lockManager) {
        this.file = Objects.requireNonNull(qualificationDirectory, "qualificationDirectory")
                .toAbsolutePath().normalize().resolve(FILE_NAME);
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    /**
     * @return the record; empty if the project set holds none
     * @throws StoreRefusal         if the record exists and is unreadable
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    @Override
    public Optional<JudgeCheck.Record> lookup() {
        return StoreSnapshot.read(file).content().map(document -> JudgeCheckCodec.read(file, document));
    }

    /**
     * @param record the check record of the run
     * @throws UncheckedIOException   if the record cannot be written
     * @throws InternalFaultException if the lock must not be acquired by the transaction
     */
    @Override
    public void write(JudgeCheck.Record entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("The qualification directory cannot be created: " + file.getParent(), e);
        }
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            new AtomicStoreFile(file, lockKey).write(transaction, JudgeCheckCodec.write(entry));
        }
    }

    /** @return the record file, absolute and normalized */
    public Path file() {
        return file;
    }
}
