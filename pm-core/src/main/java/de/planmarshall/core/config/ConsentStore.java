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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The reader and writer of {@code <PM_MCP_BASE>/consents.json}, the only place a remembered consent lives (PM-SEC-8,
 * Store Schemas § Consent Before Acting). A project's enrolment holds none.
 * <p>
 * A write is one short transaction on {@code locks/consents.lock}: it reads the document again under the lock, checks
 * that the caller's revision is the one the document holds, replaces the entry of the (kind, project) it changes,
 * increments the document's {@code revision} and writes the document back. A change against an older revision is
 * refused with a {@link SettingsConflict} carrying the revision and the entries the document holds, so nothing is
 * merged and nothing is overwritten unseen.
 * <p>
 * The store decides nothing about the question: {@link ConsentGate} decides whether an action runs, and
 * {@link #grant(Consent, long)} records the consent it hands over. The entries of a project go with its machine store
 * when the cleanup deletes it ({@link #removeProject(String, long)}).
 *
 * @since 0.1
 */
public final class ConsentStore {

    /** The parameter name {@link #belongsTo(String)} and its callers report for a missing project id. */
    private static final String PROJECT_ID = "projectId";

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    /** The name of the store's lock below {@code locks/}. */
    private static final String LOCK = "consents";

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;
    private final AuditSink auditSink;

    /**
     * What a read of the store found.
     *
     * @param consents the entries, in the order of the document
     * @param revision the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(List<Consent> consents, long revision) {

        /** Keeps the entries unmodifiable. */
        public Snapshot {
            consents = List.copyOf(consents);
        }

        /**
         * Whether the kind is remembered for the scope of the request: a machine kind is remembered by any entry of
         * its kind, a project kind by an entry of its kind and project, and {@code run_checkout_scripts} only by an
         * entry that lists every requested script with the same digest.
         *
         * @param kind      the kind the action needs
         * @param projectId the project the action runs for; ignored for a kind remembered for the machine
         * @param scripts   the scripts the action would run, for {@code run_checkout_scripts}
         * @return whether the action may run without a question
         */
        public boolean isRemembered(ConsentKind kind, Optional<String> projectId, List<ConsentScript> scripts) {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(projectId, PROJECT_ID);
            Objects.requireNonNull(scripts, "scripts");
            return consents.stream()
                    .filter(consent -> consent.kind() == kind)
                    .filter(consent -> belongsTo(consent, projectId))
                    .anyMatch(consent -> covers(consent, scripts));
        }

        /**
         * @param kind      the kind
         * @param projectId the project, for a kind remembered for a project
         * @return whether the kind is remembered for the scope
         */
        public boolean isRemembered(ConsentKind kind, Optional<String> projectId) {
            return isRemembered(kind, projectId, List.of());
        }

        /**
         * @param projectId a project
         * @return the entries recorded for the project
         */
        public List<Consent> ofProject(String projectId) {
            return consents.stream().filter(consent -> consent.belongsTo(projectId)).toList();
        }

        private static boolean belongsTo(Consent consent, Optional<String> projectId) {
            return consent.kind().projectScoped()
                    ? projectId.filter(consent::belongsTo).isPresent()
                    : consent.projectId().isEmpty();
        }

        private static boolean covers(Consent consent, List<ConsentScript> requested) {
            if (!consent.kind().scriptScoped()) {
                return true;
            }
            for (var script : requested) {
                if (consent.scripts().stream().noneMatch(covered -> covered.equals(script))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * @param storeFile   the {@code consents.json} file with the key of the consents lock
     * @param lockManager the lock manager of the runtime
     * @param auditSink   where a remembered and a withdrawn consent are recorded
     * @throws IllegalArgumentException if the key of the store file is not the consents lock
     */
    public ConsentStore(AtomicStoreFile storeFile, LockManager lockManager, AuditSink auditSink) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        if (storeFile.lockKey().level() != LockLevel.MACHINE_STORE
                || !LOCK.equals(storeFile.lockKey().id())) {
            throw new IllegalArgumentException("The consent store is guarded by the machine's consents lock: "
                    + storeFile.lockKey().lockFile());
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the store file with the key of the consents lock
     */
    public static AtomicStoreFile file(Path base) {
        return new AtomicStoreFile(Objects.requireNonNull(base, "base").toAbsolutePath().normalize()
                .resolve("consents.json"), LockKey.consents(base));
    }

    /**
     * Reads the entries and the document's revision without a lock.
     *
     * @return the entries and the revision; an empty list and {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(this::snapshot)
                .orElseGet(() -> new Snapshot(List.of(), NO_DOCUMENT));
    }

    /**
     * Records a consent, replacing the entry the same (kind, project) already has, and increments the revision.
     *
     * @param consent  the consent the operator granted
     * @param revision the revision the caller read
     * @throws IllegalArgumentException if the kind is never remembered, so no {@code yes_remember} link exists for it
     * @throws SettingsConflict        if the document holds another revision than {@code revision}
     * @throws StoreRefusal            if the document is unreadable or in another format version
     * @throws UncheckedIOException    if the document cannot be read or written
     * @throws InternalFaultException  if the lock of the document must not be acquired by the transaction
     */
    public void grant(Consent consent, long revision) {
        Objects.requireNonNull(consent, "consent");
        if (!consent.kind().rememberable()) {
            throw new IllegalArgumentException(
                    "The consent kind '%s' is never remembered".formatted(consent.kind().wireName()));
        }
        mutate(revision, entries -> {
            entries.removeIf(entry -> sameScope(entry, consent));
            entries.add(consent);
            return 1;
        }, Optional.of(AuditEvent.consentRemembered(consent.kind().wireName(),
                consent.projectId().orElse(null))));
    }

    /**
     * Withdraws the entry of a (kind, project): its state asks again the next time its action runs, and nothing recorded
     * with the consent changes.
     *
     * @param kind      the kind to withdraw
     * @param projectId the project, for a kind remembered for a project
     * @param revision  the revision the caller read
     * @return whether an entry was withdrawn; {@code false} leaves the document as it is
     * @throws SettingsConflict     if the document holds another revision than {@code revision}
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document cannot be read or written
     */
    public boolean withdraw(ConsentKind kind, Optional<String> projectId, long revision) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(projectId, PROJECT_ID);
        return mutate(revision, entries -> entries.removeIf(entry -> entry.kind() == kind
                        && sameProject(entry, projectId)) ? 1 : 0,
                Optional.of(AuditEvent.consentWithdrawn(kind.wireName(), projectId.orElse(null)))) > 0;
    }

    /**
     * Drops every entry of a project, as the cleanup does with the entries of a project whose machine store it deletes.
     *
     * @param projectId the project
     * @param revision  the revision the caller read
     * @return the number of entries dropped; {@code 0} leaves the document as it is
     * @throws SettingsConflict     if the document holds another revision than {@code revision}
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document cannot be read or written
     */
    public int removeProject(String projectId, long revision) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        return mutate(revision, entries -> {
            var before = entries.size();
            entries.removeIf(entry -> entry.belongsTo(projectId));
            return before - entries.size();
        }, Optional.empty());
    }

    /**
     * Reads the document again under the lock, checks the caller's revision, lets the change revise the entries, hands
     * the record the write owes to the audit sink and writes the document when the change changed anything. The record
     * is handed over before the write: a sink that refuses it aborts the change, so a committed consent always has its
     * record; a failure of the write after the hand-over leaves a record for a change that was not stored, which the
     * operator's next attempt repeats.
     *
     * @return the number of entries the change reports as changed
     */
    private int mutate(long revision, Change change, Optional<AuditEvent> event) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var current = readLocked(transaction);
            if (current.revision() != revision) {
                throw new SettingsConflict(storeFile.store(), revision, current.revision(), current);
            }
            var entries = new ArrayList<>(current.consents());
            var changed = change.apply(entries);
            if (changed > 0) {
                event.ifPresent(auditSink::append);
                storeFile.write(transaction, ConsentCodec.write(entries, revision + 1));
            }
            return changed;
        }
    }

    /** One change of the entries; answers how many entries it changed. */
    @FunctionalInterface
    private interface Change {

        /**
         * @param entries the entries of the document, which the change revises in place
         * @return the number of entries the change changed; {@code 0} writes nothing
         */
        int apply(List<Consent> entries);
    }

    private Snapshot readLocked(LockTransaction transaction) {
        return storeFile.read(transaction).map(this::snapshot).orElseGet(() -> new Snapshot(List.of(), NO_DOCUMENT));
    }

    private Snapshot snapshot(byte[] document) {
        var read = ConsentCodec.read(storeFile.store(), document);
        return new Snapshot(read.consents(), read.revision());
    }

    private static boolean sameScope(Consent left, Consent right) {
        return left.kind() == right.kind() && sameProject(left, right.projectId());
    }

    private static boolean sameProject(Consent entry, Optional<String> projectId) {
        return entry.kind().projectScoped() ? projectId.filter(entry::belongsTo).isPresent()
                : entry.projectId().isEmpty() && projectId.isEmpty();
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }
}
