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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;

/**
 * The project enrolment store (PM-EXEC-2, PM-IMPL-7): one file per enrolled project,
 * {@code <base>/enrolments/<project_id>.json}, beside its integrity sidecar {@code <project_id>.json.hmac}.
 * <p>
 * The set of enrolled projects is the listing of the directory. The operator-owned {@code enrolments/} is separate
 * from the server-owned {@code projects/}, so removing an enrolment never touches the project's store. Every access
 * that a decision or a write depends on runs in a transaction that holds the project's own lock
 * {@link LockKey#enrolment(Path, String)}; the file and its sidecar are each replaced by an atomic rename under that
 * one lock, so two writes of one project never interleave the file and its sidecar.
 * <p>
 * The store holds no key and computes no HMAC: it reads and writes the bytes and the sidecar record the runtime gives
 * it, and refuses a sidecar that does not record the size and SHA-256 of the file it is written with. A write replaces
 * the file before the sidecar, so a write stopped between the two leaves a pair that the runtime's integrity check
 * refuses, never one it trusts.
 * <p>
 * The directory {@code enrolments/} exists before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class EnrolmentStore {

    /** The name of the directory of the enrolment files below the base directory. */
    public static final String DIRECTORY = "enrolments";

    private static final String SUFFIX = ".json";
    private static final Pattern FILE_NAME = Pattern.compile("([a-z0-9][a-z0-9-]{1,62}[a-z0-9])\\.json");

    private final Path base;
    private final Path directory;

    /** @param base the base directory of the runtime */
    public EnrolmentStore(Path base) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.directory = this.base.resolve(DIRECTORY);
    }

    /** @return the directory of the enrolment files, absolute and normalized */
    public Path directory() {
        return directory;
    }

    /**
     * @param projectId the project
     * @return the enrolment file of the project
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public Path file(String projectId) {
        return directory.resolve(LockKey.identifier(projectId, "projectId") + SUFFIX);
    }

    /**
     * @param projectId the project
     * @return the integrity sidecar of the project's enrolment file
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public Path sidecar(String projectId) {
        return directory.resolve(LockKey.identifier(projectId, "projectId") + SUFFIX + EnrolmentSidecar.SUFFIX);
    }

    /**
     * @param projectId the project
     * @return the key of the lock that guards the project's enrolment file and its sidecar
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public LockKey lockKey(String projectId) {
        return LockKey.enrolment(base, projectId);
    }

    /**
     * Lists the enrolled projects without a lock: every file {@code <project_id>.json} whose name is a valid project
     * id. A sidecar, a temporary file of a write and any other entry are not an enrolment.
     *
     * @return the ids of the enrolled projects, sorted; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<String> projectIds() {
        var ids = new TreeSet<String>();
        try (var entries = Files.list(directory)) {
            entries.map(entry -> FILE_NAME.matcher(entry.getFileName().toString()))
                    .filter(Matcher::matches)
                    .forEach(match -> ids.add(match.group(1)));
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment directory cannot be listed: " + directory, e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return the exact bytes of the enrolment file; empty if it does not exist
     * @throws InternalFaultException if the transaction does not hold the project's lock
     * @throws UncheckedIOException   if the file exists and cannot be read
     */
    public Optional<byte[]> readFile(LockTransaction transaction, String projectId) {
        return storeFile(file(projectId), projectId).read(transaction);
    }

    /**
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return the exact bytes of the sidecar; empty if it does not exist
     * @throws InternalFaultException if the transaction does not hold the project's lock
     * @throws UncheckedIOException   if the sidecar exists and cannot be read
     */
    public Optional<byte[]> readSidecar(LockTransaction transaction, String projectId) {
        return storeFile(sidecar(projectId), projectId).read(transaction);
    }

    /**
     * Decodes the bytes of an enrolment file read for a project.
     *
     * @param projectId the project the file was read for
     * @param content   the bytes of its enrolment file
     * @return the enrolment
     * @throws StoreRefusal if the content is in another format version, unreadable, or names another project than the
     *                      one its file is named for
     */
    public ProjectEnrolment decode(String projectId, byte[] content) {
        var file = file(projectId);
        var enrolment = EnrolmentCodec.read(file, content);
        if (!enrolment.projectId().equals(projectId)) {
            throw StoreRefusal.unreadable(file, ProjectEnrolment.FORMAT_VERSION,
                    "'project_id' is '%s', not the name of its file".formatted(enrolment.projectId()), null);
        }
        return enrolment;
    }

    /**
     * Decodes the bytes of a sidecar read for a project.
     *
     * @param projectId the project the sidecar was read for
     * @param content   the bytes of its sidecar
     * @return the sidecar record
     * @throws StoreRefusal if the content is in another format version or unreadable
     */
    public EnrolmentSidecar decodeSidecar(String projectId, byte[] content) {
        return EnrolmentSidecar.read(sidecar(projectId), content);
    }

    /**
     * Writes the enrolment as its canonical bytes and then its sidecar, each by an atomic rename.
     *
     * @param transaction a transaction that holds the project's lock
     * @param enrolment   the enrolment to write
     * @param sidecar     the sidecar of the canonical bytes of the enrolment
     * @throws InternalFaultException   if the transaction does not hold the project's lock; nothing was written then
     * @throws IllegalArgumentException if the sidecar does not record the size and SHA-256 of the canonical bytes;
     *                                  nothing was written then
     * @throws UncheckedIOException     if a file cannot be written
     */
    public void write(LockTransaction transaction, ProjectEnrolment enrolment, EnrolmentSidecar sidecar) {
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(sidecar, "sidecar");
        var projectId = enrolment.projectId();
        var file = storeFile(file(projectId), projectId);
        var sidecarFile = storeFile(sidecar(projectId), projectId);
        StoreLockGuard.requireHeld(transaction, file.store(), file.lockKey());
        var content = EnrolmentCodec.write(enrolment);
        if (!sidecar.matches(content)) {
            throw new IllegalArgumentException(
                    "The sidecar does not record the canonical bytes of the enrolment of " + projectId);
        }
        file.write(transaction, content);
        sidecarFile.write(transaction, sidecar.write());
    }

    /**
     * Replaces only the sidecar of the project's enrolment file: the re-baseline of a file the operator reviewed.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @param sidecar     the sidecar of the current bytes of the enrolment file
     * @throws InternalFaultException if the transaction does not hold the project's lock; nothing was written then
     * @throws UncheckedIOException   if the sidecar cannot be written
     */
    public void writeSidecar(LockTransaction transaction, String projectId, EnrolmentSidecar sidecar) {
        Objects.requireNonNull(sidecar, "sidecar");
        storeFile(sidecar(projectId), projectId).write(transaction, sidecar.write());
    }

    /**
     * Removes the project's enrolment file and its sidecar, and nothing else.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return whether the enrolment file or its sidecar existed
     * @throws InternalFaultException if the transaction does not hold the project's lock; nothing was removed then
     * @throws UncheckedIOException   if a file cannot be removed
     */
    public boolean remove(LockTransaction transaction, String projectId) {
        var file = file(projectId);
        var sidecarFile = sidecar(projectId);
        StoreLockGuard.requireHeld(transaction, file, lockKey(projectId));
        try {
            var removed = Files.deleteIfExists(file);
            removed |= Files.deleteIfExists(sidecarFile);
            SyncedFiles.forceDirectory(directory);
            return removed;
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment of '%s' cannot be removed".formatted(projectId), e);
        }
    }

    private AtomicStoreFile storeFile(Path store, String projectId) {
        return new AtomicStoreFile(store, lockKey(projectId));
    }
}
