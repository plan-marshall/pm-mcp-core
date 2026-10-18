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
import java.nio.file.Path;
import java.util.Objects;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;

/**
 * The per-project machine store {@code <PM_MCP_BASE>/projects/<project_id>/} (PM-ARCH-1, PM-IMPL-2): the one place
 * every server-owned state of a project lies, the same for its main checkout and all its worktrees, and its identity
 * record {@code store.json} ({@link ProjectStoreIdentity}).
 * <p>
 * The store resolves its directories ({@link Directory}) and its identity record below the base directory of the
 * runtime, and names no path below the repository of the project: the repository's {@code .marshall/local/} holds no
 * state. It is created with its identity under the project's workspace lock ({@link #create(LockTransaction,
 * ProjectStoreIdentity)}); the identity is written once and never replaced by the creation. Its read takes no lock
 * ({@link #readIdentity()}) and tells a store that does not exist from an identity record that cannot be read: neither
 * is ever an empty record.
 *
 * @since 0.1
 */
public final class ProjectStore {

    /** The name of the identity record in the store directory. */
    public static final String IDENTITY_FILE = "store.json";

    private final String projectId;
    private final Path root;
    private final AtomicStoreFile identityFile;

    /**
     * A directory of the store that its creation creates.
     *
     * @since 0.1
     */
    public enum Directory {
        /** Every live plan directory. */
        PLANS("plans"),
        /** The ended plans. */
        ARCHIVE("archive"),
        /** The home of the workspace scope. */
        WORKSPACE("workspace"),
        /** The non-tracked state of the epics. */
        EPICS("epics"),
        /** The entries of plans and epics not yet created. */
        PENDING("pending"),
        /** The transaction locks of the project. */
        LOCKS("locks"),
        /** The task queue of the project. */
        QUEUE("queue"),
        /** The job journal and the job logs. */
        JOBS("jobs");

        private final String directoryName;

        Directory(String directoryName) {
            this.directoryName = directoryName;
        }

        /** @return the name of the directory in the store */
        public String directoryName() {
            return directoryName;
        }
    }

    /**
     * What the creation of a store did.
     *
     * @since 0.1
     */
    public enum Creation {
        /** The directories were created and the identity record was written. */
        CREATED,
        /** An identity record existed already; it was left as it is. */
        EXISTED
    }

    /**
     * What a read of the identity record found.
     *
     * @since 0.1
     */
    public sealed interface IdentityRead permits Present, Absent, Unreadable {
    }

    /**
     * The identity record was read.
     *
     * @param identity the identity of the store
     * @since 0.1
     */
    public record Present(ProjectStoreIdentity identity) implements IdentityRead {

        /** @throws NullPointerException if the identity is {@code null} */
        public Present {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * The identity record does not exist: the store was not created, or it was deleted.
     *
     * @param file the identity record that does not exist
     * @since 0.1
     */
    public record Absent(Path file) implements IdentityRead {

        /** @throws NullPointerException if the file is {@code null} */
        public Absent {
            Objects.requireNonNull(file, "file");
        }
    }

    /**
     * The identity record exists and was refused: it does not parse as the one shape of a store identity, it is of
     * another format version, or it names another project than the one of the store directory.
     *
     * @param refusal the refusal of the record, naming the file and why
     * @since 0.1
     */
    public record Unreadable(StoreRefusal refusal) implements IdentityRead {

        /** @throws NullPointerException if the refusal is {@code null} */
        public Unreadable {
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public ProjectStore(Path base, String projectId) {
        this.root = LockKey.projectStore(base, projectId);
        this.projectId = projectId;
        this.identityFile = new AtomicStoreFile(root.resolve(IDENTITY_FILE), LockKey.workspace(base, projectId));
    }

    /** @return the project of the store */
    public String projectId() {
        return projectId;
    }

    /** @return the store directory {@code projects/<project_id>/}, absolute and normalized */
    public Path root() {
        return root;
    }

    /**
     * @param directory a directory of the store
     * @return the directory, absolute and normalized
     */
    public Path directory(Directory directory) {
        return root.resolve(Objects.requireNonNull(directory, "directory").directoryName());
    }

    /** @return the identity record {@code store.json}, absolute and normalized */
    public Path identityFile() {
        return identityFile.store();
    }

    /** @return the key of the project's workspace lock, under which the store is created */
    public LockKey lockKey() {
        return identityFile.lockKey();
    }

    /**
     * Creates the store: its directories and its identity record, in the caller's transaction, which takes the
     * project's workspace lock. An identity record that exists already is left as it is, whatever it holds; the
     * creation writes it once.
     *
     * @param transaction the caller's transaction; the workspace lock is acquired in it
     * @param identity    the identity of the store
     * @return whether the identity record was written or existed already
     * @throws IllegalArgumentException if the identity is of another project
     * @throws InternalFaultException   if the workspace lock must not be acquired after the locks the transaction
     *                                  holds; nothing was written then
     * @throws UncheckedIOException     if a directory or the identity record cannot be created
     */
    public Creation create(LockTransaction transaction, ProjectStoreIdentity identity) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(identity, "identity");
        if (!identity.projectId().equals(projectId)) {
            throw new IllegalArgumentException("The identity of the project %s is not the identity of the store of %s"
                    .formatted(identity.projectId(), projectId));
        }
        transaction.acquire(lockKey());
        for (var directory : Directory.values()) {
            createDirectory(directory(directory));
        }
        if (identityFile.read(transaction).isPresent()) {
            return Creation.EXISTED;
        }
        identityFile.write(transaction, ProjectStoreIdentityCodec.write(identity));
        return Creation.CREATED;
    }

    /**
     * Reads the identity record without a lock, as it is at the moment of the read. A record whose
     * {@code project_id} is not the project of this store, as a {@code store.json} restored into the directory of
     * another project, is refused: it is no identity of this store.
     *
     * @return the identity; {@link Absent} if the record does not exist, {@link Unreadable} if it is refused or names
     *         another project
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public IdentityRead readIdentity() {
        var file = identityFile();
        var content = StoreSnapshot.read(file).content();
        if (content.isEmpty()) {
            return new Absent(file);
        }
        ProjectStoreIdentity identity;
        try {
            identity = ProjectStoreIdentityCodec.read(file, content.get());
        } catch (StoreRefusal refusal) {
            return new Unreadable(refusal);
        }
        if (!identity.projectId().equals(projectId)) {
            return new Unreadable(StoreRefusal.unreadable(file, ProjectStoreIdentity.FORMAT_VERSION,
                    "project_id %s is not the project %s of the store directory".formatted(identity.projectId(),
                            projectId),
                    null));
        }
        return new Present(identity);
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Store directory cannot be created: " + directory, e);
        }
    }
}
