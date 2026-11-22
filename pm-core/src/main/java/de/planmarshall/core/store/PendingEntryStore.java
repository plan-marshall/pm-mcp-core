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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;

/**
 * The pending entries {@code pending/<entry_id>/} of a project's workspace scope (PM-CLIENT-1, PM-IMPL-7): one
 * directory per entry a client opens for a scope that does not exist yet, the state store of that entry's bootstrap.
 * <p>
 * The directory holds the record {@code entry.json} (its schema is {@code pending-entry.schema.json}), the source
 * directory {@code sources/} with its manifest {@code sources/sources.json}, the entry's question store
 * {@code questions.jsonl}, its decision store {@code decision-log.jsonl}, and the transcript {@code chat/} (Store
 * Schemas § Pending Entry). The directory, its record and its stores are created in one transaction of the workspace
 * scope when the entry is opened; every bootstrap transition replaces the record.
 * <p>
 * Every write is a target of the caller's {@link ScopeTransaction} of the workspace scope, which holds the workspace
 * lock: a store is created or replaced as a replace target and removed as a delete target, so it is journaled and the
 * digest sidecar of the workspace covers it. Allocating an id is a check-then-act on {@code pending/}: the id is drawn
 * at random and checked against the directories present and against the stores the same transaction already writes,
 * and the entry is created in that transaction under the same lock, so two concurrent entries never share a directory
 * (PM-WATCH-STORE-42). A read takes no lock; a caller that decides on what it read holds the workspace lock.
 * <p>
 * The listing of {@code pending/} names only the directories whose name parses as an entry id: anything else in the
 * directory is never an entry, and no sweep selects it. An entry never expires: nothing removes it by its age.
 * <p>
 * A digest mismatch of an entry's store pauses only that entry ({@link #verify}); the operator's {@code accept-edit} of
 * the store ({@link ScopeIntegrity#rebaseline}) re-baselines it, and the abandoning of the entry deletes its directory.
 * No other entry and no workspace workflow is affected.
 *
 * @since 0.1
 */
public final class PendingEntryStore {

    /** The directory of the pending entries below the path base of the workspace scope. */
    public static final String DIRECTORY = "pending";

    /** The record of an entry, inside the entry's directory. */
    public static final String RECORD = "entry.json";

    /** The source directory of an entry, inside the entry's directory. */
    public static final String SOURCES = "sources";

    /** The manifest of the source directory, relative to the entry's directory. */
    public static final String SOURCE_MANIFEST = SOURCES + "/sources.json";

    /** The question store of an entry, relative to the entry's directory. */
    public static final String QUESTIONS = "questions.jsonl";

    /** The decision store of an entry, relative to the entry's directory. */
    public static final String DECISION_LOG = "decision-log.jsonl";

    /** The transcript directory of an entry, relative to the entry's directory. */
    public static final String CHAT = "chat";

    private static final byte[] EMPTY = new byte[0];

    /** The name of the entry argument, shared by the null checks. */
    private static final String ENTRY_ID_ARGUMENT = "entryId";

    private final JournalScope scope;

    /**
     * @param workspace the workspace scope of the project
     * @throws IllegalArgumentException if the scope is no workspace scope
     */
    public PendingEntryStore(JournalScope workspace) {
        this.scope = Objects.requireNonNull(workspace, "workspace");
        if (workspace.type() != JournalScopeType.WORKSPACE) {
            throw new IllegalArgumentException("Pending entries belong to the workspace scope, not to "
                    + workspace.type().wireName());
        }
    }

    /** @return the directory {@code pending/}, absolute and normalized */
    public Path directoryPath() {
        return scope.resolve(DIRECTORY);
    }

    /**
     * @param entryId an entry
     * @return the entry's directory, absolute and normalized
     */
    public Path entryDirectory(EntryId entryId) {
        return scope.resolve(directoryPath(entryId));
    }

    /**
     * @param entryId an entry
     * @return the directory of the entry, relative to the path base of the workspace
     */
    public static String directoryPath(EntryId entryId) {
        return DIRECTORY + "/" + Objects.requireNonNull(entryId, ENTRY_ID_ARGUMENT).directoryName();
    }

    /**
     * @param entryId an entry
     * @return the record of the entry, absolute and normalized
     */
    public Path store(EntryId entryId) {
        return scope.resolve(storePath(entryId));
    }

    /**
     * @param entryId an entry
     * @return the path of the record relative to the path base of the workspace, as its journal and sidecar name it
     */
    public static String storePath(EntryId entryId) {
        return directoryPath(entryId) + "/" + RECORD;
    }

    /**
     * @param entryId an entry
     * @param member  a member of the entry's directory, relative to it
     * @return the path of the member relative to the path base of the workspace
     */
    public static String entryPath(EntryId entryId, String member) {
        return directoryPath(entryId) + "/" + Objects.requireNonNull(member, "member");
    }

    /**
     * @param entryId an entry
     * @return the source directory of the entry, absolute and normalized
     */
    public Path sourcesDirectory(EntryId entryId) {
        return scope.resolve(directoryPath(entryId) + "/" + SOURCES);
    }

    /**
     * @param entryId an entry
     * @return the manifest of the entry's source directory, absolute and normalized
     */
    public Path sourcesStore(EntryId entryId) {
        return scope.resolve(directoryPath(entryId) + "/" + SOURCE_MANIFEST);
    }

    /**
     * @param entryId an entry
     * @return the transcript directory of the entry, absolute and normalized
     */
    public Path chatDirectory(EntryId entryId) {
        return scope.resolve(directoryPath(entryId) + "/" + CHAT);
    }

    /**
     * Draws a fresh entry id: one that names no entry present and no entry the transaction already writes. The draw is
     * repeated on a collision. The id is free for the transaction that creates its directory under the same lock.
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
    EntryId allocate(ScopeTransaction transaction, @Nullable RandomGenerator random) {
        requireOwnScope(transaction);
        EntryId candidate;
        do {
            candidate = random == null ? EntryId.random() : EntryId.random(random);
        } while (exists(candidate) || transaction.isTarget(store(candidate)));
        return candidate;
    }

    /**
     * Adds a new entry as replace targets of a transaction of the workspace: its record, an empty manifest, its empty
     * question and decision stores. All of them are written when the caller commits; the directories of the entry, its
     * source directory and its transcript exist before the commit, as the directory of every store does.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entry       the new record
     * @throws IllegalArgumentException if the transaction belongs to another scope, an entry of the id exists already,
     *                                  or one of its stores is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void create(ScopeTransaction transaction, PendingEntryRecord entry) {
        create(transaction, entry, SourceManifest.empty());
    }

    /**
     * Adds a new entry with its manifest as replace targets of a transaction of the workspace.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entry       the new record
     * @param sources     the manifest of its source directory
     * @see #create(ScopeTransaction, PendingEntryRecord)
     */
    public void create(ScopeTransaction transaction, PendingEntryRecord entry, SourceManifest sources) {
        requireOwnScope(transaction);
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(sources, SOURCES);
        if (exists(entry.entryId())) {
            throw new IllegalArgumentException("The pending entry %s exists already".formatted(entry.entryId()));
        }
        prepareDirectories(entry.entryId());
        transaction.create(storePath(entry.entryId()), PendingEntryCodec.write(entry));
        transaction.create(directoryPath(entry.entryId()) + "/" + SOURCE_MANIFEST, SourceManifestCodec.write(sources));
        transaction.create(directoryPath(entry.entryId()) + "/" + QUESTIONS, EMPTY);
        transaction.create(directoryPath(entry.entryId()) + "/" + DECISION_LOG, EMPTY);
    }

    /**
     * Adds the new content of an existing entry's record as a replace target to a transaction of the workspace.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entry       the record as it is to be written
     * @throws IllegalArgumentException if the transaction belongs to another scope, no entry of the id exists, or the
     *                                  record is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void replace(ScopeTransaction transaction, PendingEntryRecord entry) {
        requireOwnScope(transaction);
        Objects.requireNonNull(entry, "entry");
        if (!exists(entry.entryId())) {
            throw new IllegalArgumentException("The pending entry %s does not exist".formatted(entry.entryId()));
        }
        transaction.replace(storePath(entry.entryId()), PendingEntryCodec.write(entry));
    }

    /**
     * Adds the new content of an existing entry's manifest as a replace target to a transaction of the workspace.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entryId     the entry
     * @param sources     the manifest as it is to be written
     * @throws IllegalArgumentException if the transaction belongs to another scope, no entry of the id exists, or the
     *                                  manifest is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void replaceSources(ScopeTransaction transaction, EntryId entryId, SourceManifest sources) {
        requireOwnScope(transaction);
        Objects.requireNonNull(entryId, ENTRY_ID_ARGUMENT);
        Objects.requireNonNull(sources, SOURCES);
        if (!exists(entryId)) {
            throw new IllegalArgumentException("The pending entry %s does not exist".formatted(entryId));
        }
        transaction.replace(directoryPath(entryId) + "/" + SOURCE_MANIFEST, SourceManifestCodec.write(sources));
    }

    /**
     * Adds the removal of an entry's stores as delete targets to a transaction of the workspace: its record, its
     * manifest, its question and its decision store. The transcript and the files of the source directory are not
     * stores of the manifest and go with the directory, which {@link #discard(EntryId)} removes last. Removing an
     * entry that does not exist is no error.
     *
     * @param transaction an open transaction of the workspace scope
     * @param entryId     the entry whose stores are removed
     * @return whether the entry existed when the target was added
     * @throws IllegalArgumentException if the transaction belongs to another scope, or a store is a target of the
     *                                  transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public boolean remove(ScopeTransaction transaction, EntryId entryId) {
        requireOwnScope(transaction);
        var existed = exists(entryId);
        transaction.delete(storePath(entryId));
        transaction.delete(directoryPath(entryId) + "/" + SOURCE_MANIFEST);
        transaction.delete(directoryPath(entryId) + "/" + QUESTIONS);
        transaction.delete(directoryPath(entryId) + "/" + DECISION_LOG);
        return existed;
    }

    /**
     * Deletes an entry's directory with everything in it, the last act of its end: the abandoning of the entry, the
     * completion of a creation, or the sweep of an entry whose removal a stop cut off. It takes no lock and is no
     * transaction target: it is called after the transaction that deleted the entry's stores, so the record is gone and
     * the workspace sidecar no longer covers them; a directory whose record still exists is refused, because deleting
     * it would leave the four stores in the sidecar without their files. A symbolic link below the entry's directory is
     * removed as a link and never followed; a link that IS the entry directory is removed as a link too.
     *
     * @param entryId the entry
     * @return whether the directory existed
     * @throws IllegalStateException if the entry's record still exists in the directory
     * @throws UncheckedIOException  if the directory or a file in it cannot be removed
     */
    public boolean discard(EntryId entryId) {
        var directory = entryDirectory(entryId);
        if (Files.isSymbolicLink(directory)) {
            try {
                Files.delete(directory);
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException("The pending entry link cannot be removed: " + directory, e);
            }
        }
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (exists(entryId)) {
            throw new IllegalStateException("The pending entry %s still has its record; its stores are not deleted"
                    .formatted(entryId));
        }
        deleteRecursively(directory);
        return true;
    }

    /**
     * Reads a record without a lock. A record is readable only if the {@code entry_id} it states is the id its
     * directory carries: a record that states another id is refused as unreadable, so no caller judges or removes it
     * under an identity it does not state.
     *
     * @param entryId the entry
     * @return the record; empty if it does not exist
     * @throws StoreRefusal         if the record is in another format version, unreadable, or states another
     *                              {@code entry_id} than the one of its directory
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public Optional<PendingEntryRecord> read(EntryId entryId) {
        var file = store(entryId);
        return StoreSnapshot.read(file).content().map(document -> {
            var entry = PendingEntryCodec.read(file, document);
            if (!entry.entryId().equals(entryId)) {
                throw StoreRefusal.unreadable(file, PendingEntryRecord.FORMAT_VERSION,
                        "'entry_id' is '%s', not the '%s' of the directory".formatted(entry.entryId(), entryId),
                        null);
            }
            return entry;
        });
    }

    /**
     * Reads the manifest of an entry's source directory without a lock.
     *
     * @param entryId the entry
     * @return the manifest; empty if the entry or its manifest does not exist
     * @throws StoreRefusal         if the manifest is in another format version or unreadable
     * @throws UncheckedIOException if the manifest exists and cannot be read
     */
    public Optional<SourceManifest> readSources(EntryId entryId) {
        var file = sourcesStore(entryId);
        return StoreSnapshot.read(file).content().map(document -> SourceManifestCodec.read(file, document));
    }

    /**
     * Lists the entries without a lock: every directory of {@code pending/} whose name parses as an entry id. Every
     * other name, a staged file of a transaction included, is not an entry.
     *
     * @return the entries, sorted; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<EntryId> entryIds() {
        var ids = new TreeSet<EntryId>(Comparator.comparing(EntryId::value));
        try (var entries = Files.list(directoryPath())) {
            entries.filter(entry -> Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                    .map(entry -> EntryId.ofDirectoryName(entry.getFileName().toString()))
                    .flatMap(Optional::stream).forEach(ids::add);
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Pending entries cannot be listed: " + directoryPath(), e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * Verifies the stores of one entry against the digest sidecar of the workspace, under the workspace lock, so that a
     * mismatch pauses only that entry: its record, its manifest, its question store and its decision store. A store
     * that was edited outside a transaction is named with its recorded and current digest — a store that exists
     * although no transaction wrote it is such an edit too, never verified.
     *
     * @param transaction a transaction that holds the key of the workspace scope
     * @param entryId     the entry
     * @param auditSink   where the record of a roll-forward of the recovery is handed over
     * @return whether the entry is verified, absent, or tampered
     * @throws IllegalArgumentException if the transaction does not hold the key of the scope, or a store path passes
     *                                  through a symbolic link below the path base
     * @throws StoreRefusal             if the journal or the sidecar is unreadable or of another format version
     * @throws UncheckedIOException     if a file of the entry cannot be read
     */
    public Integrity verify(LockTransaction transaction, EntryId entryId, AuditSink auditSink) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(entryId, ENTRY_ID_ARGUMENT);
        Objects.requireNonNull(auditSink, "auditSink");
        var recorded = ScopeIntegrity.verify(transaction, scope, storePath(entryId), auditSink);
        if (edited(recorded)) {
            return new Integrity.Tampered(entryId, recorded);
        }
        var absent = recorded instanceof ScopeIntegrity.Absent;
        for (var path : List.of(directoryPath(entryId) + "/" + SOURCE_MANIFEST,
                directoryPath(entryId) + "/" + QUESTIONS, directoryPath(entryId) + "/" + DECISION_LOG)) {
            var verification = ScopeIntegrity.verify(transaction, scope, path, auditSink);
            if (edited(verification)) {
                return new Integrity.Tampered(entryId, verification);
            }
            absent &= verification instanceof ScopeIntegrity.Absent;
        }
        if (absent) {
            return new Integrity.Absent(entryId);
        }
        return new Integrity.Verified(entryId);
    }

    /** A store differs from its sidecar, or exists although no transaction wrote it, or the recovery broke. */
    private static boolean edited(ScopeIntegrity.Verification verification) {
        return verification instanceof ScopeIntegrity.Mismatch
                || verification instanceof ScopeIntegrity.Untracked
                || verification instanceof JournalRecovery.InvariantViolation;
    }

    private void prepareDirectories(EntryId entryId) {
        try {
            Files.createDirectories(sourcesDirectory(entryId));
            Files.createDirectories(chatDirectory(entryId));
        } catch (IOException e) {
            throw new UncheckedIOException("The pending entry %s cannot be created".formatted(entryId), e);
        }
    }

    private boolean exists(EntryId entryId) {
        return Files.exists(store(entryId), LinkOption.NOFOLLOW_LINKS);
    }

    private static void deleteRecursively(Path directory) {
        try (var entries = Files.list(directory)) {
            for (var entry : entries.toList()) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    deleteRecursively(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
            }
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("The pending entry directory cannot be removed: " + directory, e);
        }
    }

    private void requireOwnScope(ScopeTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("A transaction of %s does not write the pending entries of %s"
                    .formatted(transaction.scope().pathBase(), scope.pathBase()));
        }
    }

    /**
     * The answer of {@link #verify} for one entry: verified, absent, or tampered with the store that differs.
     *
     * @since 0.1
     */
    public sealed interface Integrity permits Integrity.Verified, Integrity.Absent, Integrity.Tampered {

        /** @return the entry the answer is about */
        EntryId entryId();

        /**
         * Every store of the entry has the digest the sidecar records.
         *
         * @param entryId the entry
         * @since 0.1
         */
        record Verified(EntryId entryId) implements Integrity {
        }

        /**
         * The entry does not exist.
         *
         * @param entryId the entry
         * @since 0.1
         */
        record Absent(EntryId entryId) implements Integrity {
        }

        /**
         * A store of the entry was edited outside a transaction; the entry alone is held.
         *
         * @param entryId the entry
         * @param cause   the store and its digests, from {@link ScopeIntegrity#verify}
         * @since 0.1
         */
        record Tampered(EntryId entryId, ScopeIntegrity.Verification cause) implements Integrity {

            /**
             * @throws NullPointerException if a component is {@code null}
             */
            public Tampered {
                Objects.requireNonNull(entryId, ENTRY_ID_ARGUMENT);
                Objects.requireNonNull(cause, "cause");
            }
        }
    }
}
