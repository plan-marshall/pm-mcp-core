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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
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
 * An operator write owes its audit record (PM-IMPL-7) in the project's intent file
 * {@code <project_id>.json.audit-intent}, the document of the intent files of the JSONL stores, touched only under the
 * project's lock. The order is: owe the record durably ({@link #oweAudit}), mutate, confirm the record
 * ({@link #confirmAudit}), hand it over ({@link #deliverOwedAudit}); the intent is removed only after the sink
 * accepted every record it owes. What is owed before the mutation is the unconfirmed form of the record
 * ({@link AuditEvent#notConfirmed()}): unlike the repair of a JSONL store, which a later repair completes, the
 * mutation of an operator write is not redone by a recovery, so the record may claim the success only once the
 * mutation has completed. A sink that refuses a record, or a stop between the mutation and the hand-over, leaves the
 * confirmed record owed; a mutation that fails, or a stop before the confirmation, leaves the unconfirmed record, so
 * an attempted operator write is audited, as what is known of it, rather than lost. A later hand-over delivers the
 * record with the same transaction id: delivery is at least once. The intent file is not an enrolment, and
 * {@link #auditOwingProjectIds()} lists the projects that still owe a record.
 * <p>
 * The directory {@code enrolments/} exists before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class EnrolmentStore {

    /** The name of the directory of the enrolment files below the base directory. */
    public static final String DIRECTORY_NAME = "enrolments";

    private static final String SUFFIX = ".json";

    /** The parameter name {@link LockKey#identifier(String, String)} reports for an invalid project id. */
    private static final String PROJECT_ID_PARAMETER = "projectId";

    private final Path base;
    private final Path directory;

    /** @param base the base directory of the runtime */
    public EnrolmentStore(Path base) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.directory = this.base.resolve(DIRECTORY_NAME);
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
        return directory.resolve(LockKey.identifier(projectId, PROJECT_ID_PARAMETER) + SUFFIX);
    }

    /**
     * @param projectId the project
     * @return the integrity sidecar of the project's enrolment file
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public Path sidecar(String projectId) {
        return directory.resolve(
                LockKey.identifier(projectId, PROJECT_ID_PARAMETER) + SUFFIX + EnrolmentSidecar.SUFFIX);
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
     * id. A sidecar, an audit intent, a temporary file of a write and any other entry are not an enrolment.
     *
     * @return the ids of the enrolled projects, sorted; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<String> projectIds() {
        return listProjectIds(SUFFIX);
    }

    /**
     * Lists the projects that still owe an audit record, without a lock: every intent file
     * {@code <project_id>.json.audit-intent} whose name carries a valid project id. It is what a recovery iterates to
     * hand the owed records over.
     *
     * @return the ids of the projects whose intent file exists, sorted; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<String> auditOwingProjectIds() {
        return listProjectIds(SUFFIX + AuditIntentFile.SUFFIX);
    }

    private SortedSet<String> listProjectIds(String suffix) {
        var ids = new TreeSet<String>();
        try (var entries = Files.list(directory)) {
            entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length()))
                    .filter(EnrolmentStore::isProjectId)
                    .forEach(ids::add);
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment directory cannot be listed: " + directory, e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * @param projectId the project
     * @return the intent file of the audit records the project's enrolment writes owe,
     *         {@code <project_id>.json.audit-intent}; it exists only while a record is owed and is not an enrolment
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public Path auditIntent(String projectId) {
        return auditIntentFile(projectId).file();
    }

    /**
     * Owes an audit record in the project's intent file, written durably, before the mutation it records. A record
     * whose transaction id is already owed is not owed twice.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @param event       the record to owe; it carries the transaction of the mutation
     * @throws InternalFaultException   if the transaction does not hold the project's lock; nothing was written then
     * @throws IllegalArgumentException if a detail value of the record is not a string, an integer or a list of
     *                                  strings; nothing was written then
     * @throws StoreRefusal             if the intent file is unreadable or of another format version, naming it;
     *                                  nothing was written then
     * @throws UncheckedIOException     if the intent file cannot be read or written
     */
    public void oweAudit(LockTransaction transaction, String projectId, AuditEvent event) {
        OwedAuditEvents.requireWritable(event);
        var intent = heldIntent(transaction, projectId);
        var owed = new ArrayList<>(intent.read());
        var transactionId = event.details().get(AuditEvent.DETAIL_TRANSACTION_ID);
        if (transactionId != null && owed.stream()
                .anyMatch(other -> transactionId.equals(other.details().get(AuditEvent.DETAIL_TRANSACTION_ID)))) {
            return;
        }
        owed.add(event);
        intent.write(transaction, owed);
    }

    /**
     * Confirms the mutation an owed record stands for: replaces, in the project's intent file and written durably, the
     * owed record of the same transaction id by the confirmed record, in its place. The operator writes owe the
     * unconfirmed form of their record ({@link AuditEvent#notConfirmed()}) before the mutation and confirm it with
     * the succeeded record after the mutation, so a write that stops or fails in between hands over the unconfirmed
     * record. If no record of that transaction is owed, the confirmed record is owed afresh.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @param confirmed   the record of the completed mutation; it carries the transaction of the owed record
     * @throws NullPointerException     if the record carries no transaction id
     * @throws InternalFaultException   if the transaction does not hold the project's lock; nothing was written then
     * @throws IllegalArgumentException if a detail value of the record is not a string, an integer or a list of
     *                                  strings; nothing was written then
     * @throws StoreRefusal             if the intent file is unreadable or of another format version, naming it;
     *                                  nothing was written then
     * @throws UncheckedIOException     if the intent file cannot be read or written
     */
    public void confirmAudit(LockTransaction transaction, String projectId, AuditEvent confirmed) {
        OwedAuditEvents.requireWritable(confirmed);
        var transactionId = Objects.requireNonNull(confirmed.details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                "transaction id of the record");
        var intent = heldIntent(transaction, projectId);
        var owed = new ArrayList<AuditEvent>();
        var replaced = false;
        for (var event : intent.read()) {
            if (transactionId.equals(event.details().get(AuditEvent.DETAIL_TRANSACTION_ID))) {
                owed.add(confirmed);
                replaced = true;
            } else {
                owed.add(event);
            }
        }
        if (!replaced) {
            owed.add(confirmed);
        }
        intent.write(transaction, owed);
    }

    /**
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return the records the project's intent file still owes, in their order; empty if it does not exist
     * @throws InternalFaultException if the transaction does not hold the project's lock
     * @throws StoreRefusal           if the intent file is unreadable or of another format version, naming it
     * @throws UncheckedIOException   if the intent file exists and cannot be read
     */
    public List<AuditEvent> owedAudit(LockTransaction transaction, String projectId) {
        return heldIntent(transaction, projectId).read();
    }

    /**
     * Hands the records the project's intent file owes over to the sink, in their order, and stops at the first the
     * sink refuses with an {@link UncheckedIOException}, which is logged as deferred and stays owed; the intent file
     * is removed once every record was handed over. Any other failure of the sink propagates and leaves the intent.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @param sink        the audit sink
     * @return the records that were handed over, in their order; empty if nothing was owed or the sink refused the
     *         first
     * @throws InternalFaultException if the transaction does not hold the project's lock; nothing was handed over then
     * @throws StoreRefusal           if the intent file is unreadable or of another format version, naming it;
     *                                nothing was handed over then
     * @throws UncheckedIOException   if the intent file cannot be read, rewritten or removed
     */
    public List<AuditEvent> deliverOwedAudit(LockTransaction transaction, String projectId, AuditSink sink) {
        Objects.requireNonNull(sink, "sink");
        var intent = heldIntent(transaction, projectId);
        try {
            return intent.handOver(transaction, intent.read(), sink);
        } catch (IOException e) {
            throw new UncheckedIOException("Audit intent cannot be removed: " + intent.file(), e);
        }
    }

    /**
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return whether the project's enrolment file or its sidecar exists
     * @throws InternalFaultException if the transaction does not hold the project's lock
     */
    public boolean exists(LockTransaction transaction, String projectId) {
        var file = file(projectId);
        StoreLockGuard.requireHeld(transaction, file, lockKey(projectId));
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(sidecar(projectId), LinkOption.NOFOLLOW_LINKS);
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

    private AuditIntentFile auditIntentFile(String projectId) {
        return AuditIntentFile.beside(file(projectId), lockKey(projectId));
    }

    private AuditIntentFile heldIntent(LockTransaction transaction, String projectId) {
        var intent = auditIntentFile(projectId);
        StoreLockGuard.requireHeld(transaction, intent.file(), intent.lockKey());
        return intent;
    }

    /**
     * The grammar of a project id has one definition, {@link LockKey#identifier(String, String)}; the listing asks it
     * rather than repeating it, so the names it lists are exactly the ones {@link #file(String)} accepts.
     */
    private static boolean isProjectId(String candidate) {
        try {
            LockKey.identifier(candidate, PROJECT_ID_PARAMETER);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}
