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

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
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
 * it, and refuses a sidecar that does not record the size and SHA-256 of the file it is written with. A write stages
 * the file and its sidecar completely, synced, as {@code <project_id>.json.tmp.<transaction id>} and
 * {@code <project_id>.json.hmac.tmp.<transaction id>} before the first rename, and then renames the file and the
 * sidecar into place. A write stopped on the way is settled by {@link #recoverPair}, which rolls a completely staged
 * pair forward and discards an incomplete staging; the next write of the project runs it first. A pair that is not
 * settled is still refused by the runtime's integrity check, never trusted. The transaction journal is not used for
 * the pair: it is bound to the scopes of the per-project machine store, whose recovery deletes the staged files of
 * its own directories, and the flat {@code enrolments/} directory is shared by every project.
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

    private static final CuiLogger LOGGER = new CuiLogger(EnrolmentStore.class);

    private static final String SUFFIX = ".json";

    private static final String SIDECAR = "sidecar";

    /** The parameter name {@link LockKey#identifier(String, String)} reports for an invalid project id. */
    private static final String PROJECT_ID_PARAMETER = "projectId";

    /**
     * The steps of a write of the enrolment pair, after each of which a test may stop the write as a killed process
     * would.
     */
    enum PairStep {
        /** The enrolment file was staged as {@code <project_id>.json.tmp.<transaction id>}. */
        FILE_STAGED,
        /** The sidecar was staged as {@code <project_id>.json.hmac.tmp.<transaction id>} and the directory forced. */
        SIDECAR_STAGED,
        /** The staged enrolment file was renamed over the enrolment file. */
        FILE_REPLACED,
        /** The staged sidecar was renamed over the sidecar and the directory forced. */
        SIDECAR_REPLACED
    }

    /**
     * Observes the steps of a write of the enrolment pair. A hook that throws stops the write exactly there and leaves
     * what a killed process leaves: the failing write cleans nothing up.
     */
    @FunctionalInterface
    interface PairHook {

        /** The hook that does nothing. */
        PairHook NONE = step -> {
            // production: no step is observed
        };

        /** @param step the step that just completed */
        void after(PairStep step);
    }

    private final Path base;
    private final Path directory;
    private final PairHook hook;

    /** @param base the base directory of the runtime */
    public EnrolmentStore(Path base) {
        this(base, PairHook.NONE);
    }

    /**
     * @param base the base directory of the runtime
     * @param hook the observer of the steps of a pair write
     */
    EnrolmentStore(Path base, PairHook hook) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.directory = this.base.resolve(DIRECTORY_NAME);
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    /**
     * @return the base directory the store was built with, absolute and normalized; the runtime compares the write
     *         paths of an enrolment with it and takes the key-creation lock from it
     */
    public Path base() {
        return base;
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
     * Answers whether a stopped write of the project's enrolment pair left a staged name of this project,
     * {@code <project_id>.json.tmp.<transaction id>} or {@code <project_id>.json.hmac.tmp.<transaction id>}, with the
     * same name matching as {@link #recoverPair} and {@link #remove}: a staged name of another project whose id merely
     * begins with this one, and the temporary file of a write that is not a transaction, do not count. It exists so
     * that unenrol can decide whether anything is left to cancel when neither the enrolment file nor its sidecar exists
     * ({@link #exists} answers only for those two).
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return whether a staged name of a stopped write of the project exists; {@code false} if the directory does not
     *         exist
     * @throws InternalFaultException if the transaction does not hold the project's lock
     * @throws UncheckedIOException   if the directory exists and cannot be listed
     */
    public boolean hasStagedWrite(LockTransaction transaction, String projectId) {
        StoreLockGuard.requireHeld(transaction, file(projectId), lockKey(projectId));
        try {
            return !stagedTransactions(projectId).isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Staged writes of the enrolment of '%s' cannot be listed".formatted(projectId), e);
        }
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
     * Writes the enrolment as its canonical bytes together with its sidecar, as one pair: a stopped write of the pair
     * left earlier is settled first ({@link #recoverPair}); then the file and the sidecar are staged completely and
     * synced, and only then renamed into place, the file first. A write stopped after the staging is completed by the
     * next {@link #recoverPair}, one stopped before is discarded by it; a pair that is not settled is refused by the
     * runtime's integrity check, never trusted.
     *
     * @param transaction a transaction that holds the project's lock
     * @param enrolment   the enrolment to write
     * @param sidecar     the sidecar of the canonical bytes of the enrolment
     * @throws InternalFaultException   if the transaction does not hold the project's lock; nothing was written then
     * @throws IllegalArgumentException if the sidecar does not record the size and SHA-256 of the canonical bytes;
     *                                  nothing was written then
     * @throws UncheckedIOException     if a file cannot be written; what was staged is left for {@link #recoverPair}
     */
    public void write(LockTransaction transaction, ProjectEnrolment enrolment, EnrolmentSidecar sidecar) {
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(sidecar, SIDECAR);
        var projectId = enrolment.projectId();
        var file = file(projectId);
        StoreLockGuard.requireHeld(transaction, file, lockKey(projectId));
        var content = EnrolmentCodec.write(enrolment);
        if (!sidecar.matches(content)) {
            throw new IllegalArgumentException(
                    "The sidecar does not record the canonical bytes of the enrolment of " + projectId);
        }
        recoverPair(transaction, projectId);
        var transactionId = TransactionId.generate(Clock.systemUTC());
        var sidecarFile = sidecar(projectId);
        var stagedFile = staged(file, transactionId);
        var stagedSidecar = staged(sidecarFile, transactionId);
        try {
            SyncedFiles.writeNew(stagedFile, content);
            hook.after(PairStep.FILE_STAGED);
            SyncedFiles.writeNew(stagedSidecar, sidecar.write());
            SyncedFiles.forceDirectory(directory);
            hook.after(PairStep.SIDECAR_STAGED);
            Files.move(stagedFile, file, StandardCopyOption.ATOMIC_MOVE);
            hook.after(PairStep.FILE_REPLACED);
            Files.move(stagedSidecar, sidecarFile, StandardCopyOption.ATOMIC_MOVE);
            SyncedFiles.forceDirectory(directory);
            hook.after(PairStep.SIDECAR_REPLACED);
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment of '%s' cannot be written".formatted(projectId), e);
        }
    }

    /**
     * Settles the stopped writes of the project's enrolment pair: completes a write that staged the file and its
     * sidecar completely, and discards a staging that is incomplete or does not match. It holds no key and signs
     * nothing: it only completes or discards a pair that a write already produced. The staged files of each stopped
     * write are processed in the ascending order of their transaction id:
     * <ol>
     * <li>The staged file and a staged sidecar that reads strictly and matches it: both are moved into place, the
     * write is {@link PairRecovery.Completed}.</li>
     * <li>The staged file and a staged sidecar that is missing, torn, unreadable or does not match it: no rename
     * happened, both are deleted, the write is {@link PairRecovery.Discarded} and the enrolment is as it was.</li>
     * <li>Only the staged sidecar, because the file was already replaced: it is moved into place if it reads strictly
     * and matches the current enrolment file ({@link PairRecovery.Completed}); otherwise it is deleted
     * ({@link PairRecovery.Discarded}) and the file is left as it is, which the integrity check then refuses.</li>
     * </ol>
     * A completed write is logged as INFO {@code PM_MCP-64}, a discarded one as WARN {@code PM_MCP-166}. Only the
     * staged names of this project are touched: {@code <project_id>.json.tmp.<transaction id>} and
     * {@code <project_id>.json.hmac.tmp.<transaction id>}.
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @return what was done, one entry per stopped write, in the order of the transaction ids; empty if nothing was
     *         staged or the directory does not exist
     * @throws InternalFaultException   if the transaction does not hold the project's lock; nothing was touched then
     * @throws IllegalArgumentException if the project id is not a valid identifier
     * @throws UncheckedIOException     if the directory cannot be listed or a staged file cannot be read, moved or
     *                                  deleted
     */
    public List<PairRecovery> recoverPair(LockTransaction transaction, String projectId) {
        var file = file(projectId);
        StoreLockGuard.requireHeld(transaction, file, lockKey(projectId));
        var settled = new ArrayList<PairRecovery>();
        try {
            for (var transactionId : stagedTransactions(projectId)) {
                settle(projectId, transactionId).ifPresent(settled::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Stopped write of the enrolment of '%s' cannot be settled".formatted(projectId), e);
        }
        return List.copyOf(settled);
    }

    private Optional<PairRecovery> settle(String projectId, TransactionId transactionId) throws IOException {
        var stagedFile = staged(file(projectId), transactionId);
        var stagedSidecar = staged(sidecar(projectId), transactionId);
        var stagedContent = StoreSnapshot.read(stagedFile).content();
        var sidecarBytes = StoreSnapshot.read(stagedSidecar).content().orElse(null);
        if (stagedContent.isPresent()) {
            var reason = mismatch(stagedSidecar, sidecarBytes, stagedContent.get());
            if (reason.isEmpty()) {
                return Optional.of(completed(projectId, transactionId,
                        List.of(moveIntoPlace(stagedFile, file(projectId)),
                                moveIntoPlace(stagedSidecar, sidecar(projectId)))));
            }
            return Optional.of(discarded(projectId, transactionId, List.of(stagedFile, stagedSidecar),
                    reason.get()));
        }
        if (sidecarBytes == null) {
            return Optional.empty();
        }
        var current = StoreSnapshot.read(file(projectId)).content();
        var reason = current.isEmpty() ? Optional.of("the enrolment file is missing")
                : mismatch(stagedSidecar, sidecarBytes, current.get());
        if (reason.isEmpty()) {
            return Optional.of(completed(projectId, transactionId,
                    List.of(moveIntoPlace(stagedSidecar, sidecar(projectId)))));
        }
        return Optional.of(discarded(projectId, transactionId, List.of(stagedSidecar), reason.get()));
    }

    /**
     * @param sidecarBytes the bytes of the staged sidecar; {@code null} if it does not exist
     * @return why the staged sidecar does not stand for the content; empty if it reads strictly and matches it
     */
    private static Optional<String> mismatch(Path stagedSidecar, byte[] sidecarBytes, byte[] content) {
        if (sidecarBytes == null) {
            return Optional.of("the staged sidecar is missing");
        }
        try {
            return EnrolmentSidecar.read(stagedSidecar, sidecarBytes).matches(content) ? Optional.empty()
                    : Optional.of("the staged sidecar does not match the file it stands for");
        } catch (StoreRefusal e) {
            return Optional.of("the staged sidecar is refused: " + e.getMessage());
        }
    }

    private Path moveIntoPlace(Path staged, Path target) throws IOException {
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        SyncedFiles.forceDirectory(directory);
        return target;
    }

    private static PairRecovery completed(String projectId, TransactionId transactionId, List<Path> replaced) {
        LOGGER.info(INFO.ENROLMENT_PAIR_COMPLETED, projectId, transactionId);
        return new PairRecovery.Completed(transactionId, replaced);
    }

    private PairRecovery discarded(String projectId, TransactionId transactionId, List<Path> staged, String reason)
            throws IOException {
        var deleted = deleteAll(staged);
        LOGGER.warn(WARN.ENROLMENT_PAIR_DISCARDED, projectId, reason);
        return new PairRecovery.Discarded(transactionId, deleted, reason);
    }

    private List<Path> deleteAll(List<Path> files) throws IOException {
        var deleted = new ArrayList<Path>();
        for (var staged : files) {
            if (Files.deleteIfExists(staged)) {
                deleted.add(staged);
            }
        }
        SyncedFiles.forceDirectory(directory);
        return deleted;
    }

    /**
     * Lists the transactions that left a staged name of the project: {@code <project_id>.json.tmp.<id>} or
     * {@code <project_id>.json.hmac.tmp.<id>} for a transaction id. A name of another project, whose id merely begins
     * with this one, and the temporary file of a write that is not a transaction are not among them.
     */
    private List<TransactionId> stagedTransactions(String projectId) throws IOException {
        var fileName = file(projectId).getFileName().toString();
        var sidecarName = sidecar(projectId).getFileName().toString();
        var transactions = new TreeMap<String, TransactionId>();
        try (var entries = Files.list(directory)) {
            entries.map(entry -> entry.getFileName().toString())
                    .forEach(name -> TransactionId.ofSuffix(name)
                            .filter(id -> name.equals(id.stagedName(fileName, TransactionJournal.Mode.REPLACE))
                                    || name.equals(id.stagedName(sidecarName, TransactionJournal.Mode.REPLACE)))
                            .ifPresent(id -> transactions.put(id.value(), id)));
        } catch (NoSuchFileException _) {
            return List.of();
        }
        return List.copyOf(transactions.values());
    }

    private static Path staged(Path target, TransactionId transactionId) {
        return target.resolveSibling(
                transactionId.stagedName(target.getFileName().toString(), TransactionJournal.Mode.REPLACE));
    }

    /**
     * Replaces only the sidecar of the project's enrolment file: the re-baseline of a file the operator reviewed. It
     * settles nothing itself: a stopped write of the pair is settled by its caller first, under the same lock, so the
     * sidecar is written for the file that is really in place (the runtime's re-baseline calls {@link #recoverPair}
     * before it reads and signs the file).
     *
     * @param transaction a transaction that holds the project's lock
     * @param projectId   the project
     * @param sidecar     the sidecar of the current bytes of the enrolment file
     * @throws InternalFaultException if the transaction does not hold the project's lock; nothing was written then
     * @throws UncheckedIOException   if the sidecar cannot be written
     */
    public void writeSidecar(LockTransaction transaction, String projectId, EnrolmentSidecar sidecar) {
        Objects.requireNonNull(sidecar, SIDECAR);
        storeFile(sidecar(projectId), projectId).write(transaction, sidecar.write());
    }

    /**
     * Removes the project's enrolment file and its sidecar, and the staged files of every stopped write of the pair
     * first, so a stopped write is never rolled forward later and an unenrolled project never comes back. Its audit
     * intent and everything else stay.
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
            for (var transactionId : stagedTransactions(projectId)) {
                Files.deleteIfExists(staged(file, transactionId));
                Files.deleteIfExists(staged(sidecarFile, transactionId));
            }
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
