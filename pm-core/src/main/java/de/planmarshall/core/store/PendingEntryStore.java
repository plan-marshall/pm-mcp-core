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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

/**
 * The pending entry records {@code pending/<entry_id>.json} of a project's workspace scope (PM-CLIENT-1,
 * PM-IMPL-7): one record per entry a client opens for a scope that does not exist yet, the state store of that entry's
 * bootstrap.
 * <p>
 * Every write is a target of the caller's {@link ScopeTransaction} of the workspace scope, which holds the workspace
 * lock: a record is created or replaced as a replace target and removed as a delete target, so it is journaled and the
 * digest sidecar of the workspace covers it. Allocating an id is a check-then-act on {@code pending/}: the id is drawn
 * at random and checked against the records present and against the records the same transaction already writes,
 * and the record is created in that transaction under the same lock, so two concurrent entries never share a record
 * (PM-WATCH-STORE-42). A read takes no lock; a caller that decides on what it read holds the workspace lock.
 * <p>
 * The listing of {@code pending/} names only the files whose name parses as an entry id: anything else in the
 * directory is never an entry, and no sweep selects it.
 *
 * @since 0.1
 */
public final class PendingEntryStore {

    /** The directory of the pending entry records below the path base of the workspace scope. */
    public static final String DIRECTORY = "pending";

    private final JournalScope scope;

    /**
     * @param workspace the workspace scope of the project
     * @throws IllegalArgumentException if the scope is no workspace scope
     */
    public PendingEntryStore(JournalScope workspace) {
        this.scope = Objects.requireNonNull(workspace, "workspace");
        if (workspace.type() != JournalScopeType.WORKSPACE) {
            throw new IllegalArgumentException("Pending entry records belong to the workspace scope, not to "
                    + workspace.type().wireName());
        }
    }

    /** @return the directory {@code pending/}, absolute and normalized */
    public Path directoryPath() {
        return scope.resolve(DIRECTORY);
    }

    /**
     * @param entryId an entry
     * @return the record file of the entry, absolute and normalized
     */
    public Path store(EntryId entryId) {
        return scope.resolve(storePath(entryId));
    }

    /**
     * @param entryId an entry
     * @return the path of the record relative to the path base of the workspace, as its journal and sidecar name it
     */
    public static String storePath(EntryId entryId) {
        return DIRECTORY + "/" + Objects.requireNonNull(entryId, "entryId").fileName();
    }

    /**
     * Draws a fresh entry id: one that names no record present and no record the transaction already writes. The draw
     * is repeated on a collision. The id is free for the transaction that creates its record under the same lock.
     *
     * @param transaction an open transaction of the workspace scope, which holds the workspace lock
     * @return a free entry id
     * @throws IllegalArgumentException if the transaction belongs to another scope
     */
    public EntryId allocate(ScopeTransaction transaction) {
        return allocate(transaction, null);
    }

    /**
     * @param random the source of the drawn digits; {@code null} for the secure default
     * @see #allocate(ScopeTransaction)
     */
    EntryId allocate(ScopeTransaction transaction, RandomGenerator random) {
        requireOwnScope(transaction);
        EntryId candidate;
        do {
            candidate = random == null ? EntryId.random() : EntryId.random(random);
        } while (exists(candidate) || transaction.isTarget(store(candidate)));
        return candidate;
    }

    /**
     * Adds a new record as a replace target to a transaction of the workspace; it is written when the caller commits.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entry       the new record
     * @throws IllegalArgumentException if the transaction belongs to another scope, a record of the id exists
     *                                  already, or the record is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void create(ScopeTransaction transaction, PendingEntryRecord entry) {
        requireOwnScope(transaction);
        Objects.requireNonNull(entry, "entry");
        if (exists(entry.entryId())) {
            throw new IllegalArgumentException("The pending entry record %s exists already".formatted(
                    entry.entryId()));
        }
        transaction.replace(storePath(entry.entryId()), PendingEntryCodec.write(entry));
    }

    /**
     * Adds the new content of an existing record as a replace target to a transaction of the workspace.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entry       the record as it is to be written
     * @throws IllegalArgumentException if the transaction belongs to another scope, no record of the id exists, or
     *                                  the record is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void replace(ScopeTransaction transaction, PendingEntryRecord entry) {
        requireOwnScope(transaction);
        Objects.requireNonNull(entry, "entry");
        if (!exists(entry.entryId())) {
            throw new IllegalArgumentException("The pending entry record %s does not exist".formatted(
                    entry.entryId()));
        }
        transaction.replace(storePath(entry.entryId()), PendingEntryCodec.write(entry));
    }

    /**
     * Adds the removal of a record as a delete target to a transaction of the workspace; the record and its sidecar
     * entry are gone once the caller commits. Removing a record that does not exist is no error.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entryId     the entry whose record is removed
     * @return whether the record existed when the target was added
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the record is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public boolean remove(ScopeTransaction transaction, EntryId entryId) {
        requireOwnScope(transaction);
        var existed = exists(entryId);
        transaction.delete(storePath(entryId));
        return existed;
    }

    /**
     * Reads a record without a lock. A record is readable only if the {@code entry_id} it states is the id its file
     * name carries: a record that states another id is refused as unreadable, so no caller judges or removes it under
     * an identity it does not state.
     *
     * @param entryId the entry
     * @return the record; empty if it does not exist
     * @throws StoreRefusal         if the record is in another format version, unreadable, or states another
     *                              {@code entry_id} than the one of its file name
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public Optional<PendingEntryRecord> read(EntryId entryId) {
        var file = store(entryId);
        return StoreSnapshot.read(file).content().map(document -> {
            var entry = PendingEntryCodec.read(file, document);
            if (!entry.entryId().equals(entryId)) {
                throw StoreRefusal.unreadable(file, PendingEntryRecord.FORMAT_VERSION,
                        "'entry_id' is '%s', not the '%s' of the file name".formatted(entry.entryId(), entryId),
                        null);
            }
            return entry;
        });
    }

    /**
     * Lists the entries without a lock: every file {@code <entry_id>.json} of {@code pending/} whose name parses as an
     * entry id. Every other name, a staged file of a transaction included, is not an entry.
     *
     * @return the entries, sorted; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<EntryId> entryIds() {
        var ids = new TreeSet<EntryId>((left, right) -> left.value().compareTo(right.value()));
        try (var entries = Files.list(directoryPath())) {
            entries.filter(entry -> Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    .map(entry -> EntryId.ofFileName(entry.getFileName().toString()))
                    .flatMap(Optional::stream).forEach(ids::add);
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Pending entry records cannot be listed: " + directoryPath(), e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    private boolean exists(EntryId entryId) {
        return Files.exists(store(entryId), LinkOption.NOFOLLOW_LINKS);
    }

    private void requireOwnScope(ScopeTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("A transaction of %s does not write the pending entry records of %s"
                    .formatted(transaction.scope().pathBase(), scope.pathBase()));
        }
    }
}
