/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LiveWork;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.SecretStoreException;

/**
 * The operator writes of the enrolment store (PM-EXEC-2, PM-MIG-1, PM-IMPL-7): {@code enrol}, {@code unenrol} and the
 * re-baseline of {@code accept-edit enrolment}, which the runtime executes on an operator verb only.
 * <p>
 * Each write runs in one transaction that holds the project's enrolment lock, so two writes of one project never
 * interleave the file and its sidecar, and every write leaves the file and a sidecar record over its exact bytes
 * (size, SHA-256 and HMAC under the integrity key), or neither. {@code unenrol} asks for the project's live work while
 * it holds that lock, so the answer and the removal belong to one transaction; a future scope-creation path must close
 * the remaining window by reading the enrolment under the same lock. Every write is logged after its transaction.
 * <p>
 * Every write owes its audit record (PM-IMPL-7) and runs in the order owe, mutate, confirm, hand over, all four in the
 * transaction that holds the project's lock: the record, carrying the transaction id of the write, is written durably
 * to the project's intent file ({@link EnrolmentStore#oweAudit}) before the mutation, and handed over to the audit sink
 * after it ({@link EnrolmentStore#deliverOwedAudit}). What is owed before the mutation is the unconfirmed form of the
 * record ({@link AuditEvent#notConfirmed()}, outcome {@code failed}, reason {@code not_confirmed}); the mutation of an
 * operator write is not redone by a recovery, so the record claims the success, outcome {@code succeeded}, only once
 * the mutation completed, when {@link EnrolmentStore#confirmAudit} replaces the unconfirmed record by it. A sink that
 * refuses the record does not fail the write: the substrate logs {@code PM_MCP-160} and the record stays owed. A
 * mutation that fails, or a stop before the confirmation, keeps the unconfirmed record owed ({@code PM_MCP-164} when
 * it is handed over), so an attempted operator write is audited as what is known of it, never as a success that did
 * not happen. The next write of the project and the recovery hand an owed record over: the project's recovery calls
 * {@link #replayOwedAudit(String)} as step {@code OWED_ENROLMENT_AUDIT} ({@code OwedEnrolmentAuditStep}), and its
 * background pass calls {@link #replayOwedAuditOfUnenrolledProjects()} for the projects no longer enrolled;
 * {@link #replayOwedAudit()} sweeps every project. Delivery is at least once, and a record handed over again after a
 * stop between the append and the removal of the intent carries the same transaction id, by which the audit store
 * drops the duplicate. A refusal ({@code enrolment_tampered}, {@code enrolment_insecure}, {@code enrolment_invalid},
 * {@code unenrol_refused}) changed nothing and recurs with the next attempt; it is appended directly and not owed, and
 * a sink that fails does not lift it: the failure is added to the refusal as a suppressed exception.
 * <p>
 * {@code enrol} and the re-baseline check the enrolment against the enrolment policy ({@link EnrolmentPolicy}) before
 * they sign it, and refuse a violation as {@code enrolment_invalid} before the key is resolved or anything is owed.
 * {@code enrol} stages the file and its sidecar completely before the first rename ({@link EnrolmentStore#write}); a
 * stopped write is settled by the project's recovery or the next write of the project, and the check refuses a
 * mismatched pair. The store removal of {@code unenrol} also drops the staged leftovers of a stopped write, and
 * {@code unenrol} removes, and owes its record for, a stopped write that left only staged files
 * ({@link EnrolmentStore#hasStagedWrite}), so a cancelled first enrolment cannot be rolled forward later.
 * <p>
 * The re-baseline first settles a stopped write of the pair ({@link EnrolmentStore#recoverPair}) under both locks, so
 * a staged pair can never replace the re-baselined pair later; it then reads, checks and signs one no-follow snapshot
 * of the settled file ({@code EnrolmentFileCheck.snapshot}), so the per-use checks, the strict read and the new sidecar
 * record cover the same bytes; its {@code enrolment_insecure} refusal is logged and audited like the refusal of the
 * enrolment check.
 * <p>
 * The integrity key is created by the first write that needs it and never replaced, under the credentials lock
 * ({@link EnrolmentKey#creationLock}), which {@code enrol} and the re-baseline acquire before the project's lock, so two
 * runtime instances never create two keys. {@code enrol} creates it only
 * while no enrolment file exists; when the key is absent from the active key source although enrolment files exist,
 * {@code enrol} refuses as {@code enrolment_tampered} rather than strand them under a new key. The re-baseline is the
 * one write that creates the key over existing enrolments, because it is the operator's explicit act.
 *
 * @since 0.1
 */
public final class Enrolments {

    private static final CuiLogger LOGGER = new CuiLogger(Enrolments.class);

    private final EnrolmentStore store;
    private final EnrolmentKey key;
    private final EnrolmentFileCheck fileCheck;
    private final LockManager lockManager;
    private final LiveWork liveWork;
    private final AuditSink auditSink;

    /**
     * @param store       the enrolment store
     * @param key         the enrolment integrity key
     * @param fileCheck   the per-use checks of the file a re-baseline reads
     * @param lockManager the lock manager of the runtime
     * @param liveWork    the live work of the projects, which refuses an {@code unenrol}
     * @param auditSink   where every write and refusal is recorded
     */
    public Enrolments(EnrolmentStore store, EnrolmentKey key, EnrolmentFileCheck fileCheck, LockManager lockManager,
            LiveWork liveWork, AuditSink auditSink) {
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
        this.fileCheck = Objects.requireNonNull(fileCheck, "fileCheck");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.liveWork = Objects.requireNonNull(liveWork, "liveWork");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    /**
     * Writes the project's enrolment file and its sidecar record. The written enrolment is a pilot exactly if
     * {@code pilot} is set, and is not released: enrolling a released project again clears its release.
     * <p>
     * The write creates the integrity key only if the key does not exist and no enrolment file exists. A key that is
     * absent from the active key source while enrolment files exist (typically after the key source was switched
     * between the keyring and the key file) is not replaced by a new one; the write is refused. A sidecar without any
     * enrolment file strands nothing and does not count.
     * <p>
     * The enrolment is checked against the enrolment policy first ({@link EnrolmentPolicy}), before the key is
     * resolved, a record is owed or anything is written. The credentials lock ({@link EnrolmentKey#creationLock}) is
     * acquired before the project's lock, so two runtime instances never create two keys. The file and its sidecar are
     * staged completely before the first rename; a stopped write is settled by the project's recovery
     * ({@code EnrolmentIntegrityStep}) or by the next write of the project, and the check refuses a mismatched pair.
     *
     * @param enrolment the enrolment the operator confirmed
     * @param pilot     whether the project is enrolled as a pilot of the lifecycle gate ({@code --pilot})
     * @return the enrolment as written
     * @throws StoreRefusal         as {@code enrolment_invalid} if the enrolment violates the enrolment policy, logged
     *                              ({@code PM_MCP-165}) and audited, no key created; as {@code enrolment_tampered} if
     *                              the integrity key is absent while enrolment files exist, naming the first enrolled
     *                              project and its {@code accept-edit} command; nothing was written then
     * @throws SecretStoreException if the integrity key cannot be read or created; nothing was written then
     * @throws UncheckedIOException if a file or the intent file cannot be written or the enrolment directory cannot be
     *                              listed; a file that cannot be written leaves the {@code enrolment_written} record
     *                              owed unconfirmed. A refused audit append is not one of them: the record then stays
     *                              owed and is handed over later
     */
    public ProjectEnrolment enrol(ProjectEnrolment enrolment, boolean pilot) {
        Objects.requireNonNull(enrolment, "enrolment");
        var written = asEnrolled(enrolment, pilot);
        var projectId = written.projectId();
        requireCompliant(written);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(EnrolmentKey.creationLock(store.base()));
            transaction.acquire(store.lockKey(projectId));
            var sidecar = EnrolmentHmac.sidecar(enrolmentKey(), EnrolmentCodec.write(written));
            var event = AuditEvent.enrolmentWritten(projectId, newTransactionId());
            writeOwing(transaction, projectId, event, () -> store.write(transaction, written, sidecar));
        }
        LOGGER.info(INFO.ENROLMENT_WRITTEN, projectId, pilot);
        return written;
    }

    /**
     * The key of an enrolment write: the existing key, or a new one only if no enrolment file exists. A key that is
     * absent while enrolment files exist was lost or lives in the other key source, and creating a new one would
     * strand every existing enrolment; the write is refused instead, naming the first enrolled project.
     */
    private SecretKey enrolmentKey() {
        var existing = key.read();
        if (existing.isPresent()) {
            return existing.get();
        }
        var enrolled = store.projectIds();
        if (!enrolled.isEmpty()) {
            var first = enrolled.first();
            throw EnrolmentCheck.tampered(auditSink, store.file(first), first, EnrolmentCheck.KEY_ABSENT);
        }
        return key.readOrCreate();
    }

    /**
     * Removes the project's enrolment file and its sidecar, and the staged files of a stopped write of the pair, so it
     * is never rolled forward later; the project's store below {@code projects/<project_id>/} stays as it is. A first
     * enrolment that was staged and stopped, with neither a file nor a sidecar in place, is cancelled and audited the
     * same way.
     *
     * @param projectId the project
     * @return whether the enrolment file, its sidecar or the staged files of a stopped write existed; {@code false}
     *         only when nothing of the enrolment existed, and then no record is written
     * @throws ScopeActiveException if the project has live work, naming it; nothing was removed then. An audit sink
     *                              that fails to record the refusal does not replace it: its failure is a suppressed
     *                              exception of the refusal
     * @throws UncheckedIOException if a file cannot be removed or the intent file cannot be written; a file that cannot
     *                              be removed leaves the {@code enrolment_removed} record owed unconfirmed. A refused
     *                              audit append is not one of them: the record then stays owed and is handed over
     *                              later
     */
    public boolean unenrol(String projectId) {
        boolean removed;
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            var items = List.copyOf(liveWork.of(projectId));
            if (!items.isEmpty()) {
                var refusal = new ScopeActiveException(projectId, items);
                LOGGER.warn(WARN.UNENROL_REFUSED, projectId, refusal.namedItems());
                throw EnrolmentCheck.recorded(auditSink, refusal, AuditEvent.unenrolRefused(projectId));
            }
            removed = removeOwingAudit(transaction, projectId);
        }
        if (removed) {
            LOGGER.info(INFO.ENROLMENT_REMOVED, projectId);
        }
        return removed;
    }

    /**
     * Removes the project's enrolment, if anything of it exists (the file, the sidecar or a staged name of a stopped
     * write), after owing its record; in every case hands over
     * what the project owes, so a record an earlier write still owes is flushed.
     */
    private boolean removeOwingAudit(LockTransaction transaction, String projectId) {
        var present = store.exists(transaction, projectId) || store.hasStagedWrite(transaction, projectId);
        if (present) {
            var event = AuditEvent.enrolmentRemoved(projectId, newTransactionId());
            writeOwing(transaction, projectId, event, () -> store.remove(transaction, projectId));
        } else {
            handOverOwed(transaction, projectId, null);
        }
        return present;
    }

    /**
     * Re-baselines the project's enrolment file the operator reviewed ({@code accept-edit enrolment}): rewrites its
     * sidecar record over the current bytes of the file. A stopped write of the pair is settled first, under both
     * locks ({@link EnrolmentStore#recoverPair}: a completely staged pair is rolled forward, an incomplete one
     * discarded), so the file it signs is the settled one and the values it reports describe it. The file must pass
     * the per-use checks and read strictly as
     * an enrolment of the project; nothing else of it is changed. The checks, the strict read and the signature cover
     * one no-follow snapshot of the file; a file that fails a check is refused, logged ({@code PM_MCP-162}) and
     * audited ({@code enrolment_insecure}) without owing or writing anything. The decoded enrolment is checked against
     * the enrolment policy before the key is read, so a re-baseline never signs a file that violates it. The
     * credentials lock is acquired before the project's lock, as in {@link #enrol(ProjectEnrolment, boolean)}.
     * <p>
     * The re-baseline is the operator's explicit, named act, and the one write that creates the integrity key although
     * enrolment files exist: it is the way out of the refusal of {@link #enrol(ProjectEnrolment, boolean)} after a
     * switch of the key source. The other enrolments written under the old key then fail their own check with an HMAC
     * mismatch naming the {@code accept-edit} command of their project, and are re-baselined one by one.
     *
     * @param projectId the project
     * @return the recorded and the current size and SHA-256 of the file, for the operator's display
     * @throws StoreRefusal         as {@code absent} if the file does not exist, as {@code enrolment_insecure} if it
     *                              fails a per-use check (a symbolic link is refused, never followed) or changed
     *                              while it was read, as {@code unreadable}, {@code format_outdated} or
     *                              {@code format_newer} if it does not read strictly, as {@code enrolment_invalid} if
     *                              it violates the enrolment policy (logged {@code PM_MCP-165} and audited, before
     *                              the key is read or anything is owed); nothing was written then
     * @throws SecretStoreException if the integrity key cannot be read or created; nothing was written then
     * @throws UncheckedIOException if a staged file of a stopped write cannot be listed, read, moved or deleted
     *                              (nothing is signed then), if a file cannot be read or written or the intent file
     *                              cannot be written; a sidecar that cannot be written leaves the
     *                              {@code enrolment_rebaselined} record owed unconfirmed. A refused audit append is
     *                              not one of them: the record then stays owed and is handed over later
     */
    public Rebaseline rebaseline(String projectId) {
        var file = store.file(projectId);
        Optional<StoreDigest> recorded;
        StoreDigest current;
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(EnrolmentKey.creationLock(store.base()));
            transaction.acquire(store.lockKey(projectId));
            store.recoverPair(transaction, projectId);
            var snapshot = fileCheck.snapshot(file);
            var violation = snapshot.violation();
            if (violation.isPresent()) {
                throw EnrolmentCheck.insecure(auditSink, file, projectId, violation.get());
            }
            var content = snapshot.content()
                    .orElseThrow(() -> StoreRefusal.absent(file, ProjectEnrolment.FORMAT_VERSION));
            requireCompliant(store.decode(projectId, content));
            recorded = store.readSidecar(transaction, projectId).flatMap(bytes -> recordedDigest(projectId, bytes));
            var sidecar = EnrolmentHmac.sidecar(key.readOrCreate(), content);
            var event = AuditEvent.enrolmentRebaselined(projectId, newTransactionId());
            writeOwing(transaction, projectId, event, () -> store.writeSidecar(transaction, projectId, sidecar));
            current = sidecar.digest();
        }
        LOGGER.info(INFO.ENROLMENT_REBASELINED, projectId);
        return new Rebaseline(file, recorded, current);
    }

    /**
     * Hands over the audit records an earlier operator write of the project still owes, in one transaction that holds
     * the project's enrolment lock: step {@code OWED_ENROLMENT_AUDIT} of the project's recovery
     * ({@code OwedEnrolmentAuditStep}) calls it. It reads no enrolment and creates no key.
     *
     * @param projectId the project
     * @return the number of records handed over; a record the sink refuses again stays owed and is not counted
     * @throws StoreRefusal         if the project's intent file is unreadable or of another format version; it is
     *                              kept
     * @throws UncheckedIOException if the intent file cannot be read, rewritten or removed; it is kept
     */
    public int replayOwedAudit(String projectId) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            return handOverOwed(transaction, projectId, null);
        }
    }

    /**
     * Hands over the audit records that projects no longer enrolled still owe, as {@link #replayOwedAudit()} does,
     * skipping every project whose enrolment file or sidecar exists: the recovery of an enrolled project hands its own
     * records over. The background pass of the recovery calls it ({@code OwedEnrolmentAuditStep.unenrolledPass()}).
     *
     * @return the number of records handed over; a record the sink refuses again stays owed and is not counted
     * @throws UncheckedIOException as {@link #replayOwedAudit()}
     * @throws StoreRefusal         as {@link #replayOwedAudit()}
     */
    public int replayOwedAuditOfUnenrolledProjects() {
        return replay(true);
    }

    /**
     * Hands over the audit records an earlier operator write still owes, project by project in the order of their
     * ids, each in its own transaction that holds the project's enrolment lock: the recovery of the owed enrolment
     * records of every project. It reads no enrolment and creates no key. The recovery of a project calls
     * {@link #replayOwedAudit(String)} for the project and {@link #replayOwedAuditOfUnenrolledProjects()} in its
     * background pass; the operator verbs, which would call this sweep, have no caller yet.
     * <p>
     * One project's bad intent file does not stop the replay for the projects after it, whether it is refused
     * ({@link StoreRefusal}: unreadable, another format version) or fails on I/O ({@link UncheckedIOException}: it
     * cannot be read, rewritten or removed): the file is kept, the replay goes on, and after the last project the first
     * failure in the order of the project ids is thrown as its own type, with every later failure of either type added
     * to it as a suppressed exception. The records of the other projects are handed over by then; the count of a
     * replay that threw is not returned, the handed-over records are logged ({@code PM_MCP-63}). Any other failure,
     * such as a stop, ends the replay at once.
     *
     * @return the number of records handed over; a record the sink refuses again stays owed and is not counted
     * @throws UncheckedIOException if the enrolment directory cannot be listed, or the first intent file that cannot be
     *                              read, rewritten or removed precedes every refused one; every such file is kept
     * @throws StoreRefusal         if the intent file of a project is unreadable or of another format version and
     *                              precedes every intent file that fails on I/O, naming the first such file in the
     *                              order of the project ids; every such file is kept
     */
    public int replayOwedAudit() {
        return replay(false);
    }

    /**
     * The sweep of the owed records: the projects in the order of their ids, each in its own transaction; a failing
     * project does not stop the sweep, the first failure is thrown at the end with the later ones suppressed.
     */
    private int replay(boolean unenrolledOnly) {
        var handedOver = 0;
        RuntimeException firstFailure = null;
        for (var projectId : store.auditOwingProjectIds()) {
            try (var transaction = lockManager.openTransaction()) {
                transaction.acquire(store.lockKey(projectId));
                if (!unenrolledOnly || !store.exists(transaction, projectId)) {
                    handedOver += handOverOwed(transaction, projectId, null);
                }
            } catch (StoreRefusal | UncheckedIOException failure) {
                firstFailure = withLater(firstFailure, failure);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return handedOver;
    }

    /**
     * Refuses an enrolment that violates the enrolment policy, before anything is signed, owed or written and before
     * the key is resolved: logged ({@code PM_MCP-165}) and audited as {@code enrolment_invalid}.
     */
    private void requireCompliant(ProjectEnrolment enrolment) {
        var violations = EnrolmentPolicy.violations(enrolment, store.base());
        if (!violations.isEmpty()) {
            var projectId = enrolment.projectId();
            throw EnrolmentCheck.invalid(auditSink, store.file(projectId), projectId, String.join("; ", violations));
        }
    }

    /**
     * Remembers the first failure of a replay; a later one is added to it as a suppressed exception.
     */
    private static RuntimeException withLater(RuntimeException first, RuntimeException failure) {
        if (first == null) {
            return failure;
        }
        first.addSuppressed(failure);
        return first;
    }

    /**
     * Runs a mutation that owes its audit record, in the order owe, mutate, confirm, hand over, all in the
     * transaction that holds the project's lock. What is owed before the mutation is the unconfirmed form of the
     * record; the succeeded record replaces it only after the mutation completed. A mutation that fails therefore
     * keeps the unconfirmed record owed, and the failure propagates.
     */
    private void writeOwing(LockTransaction transaction, String projectId, AuditEvent succeeded, Runnable mutation) {
        store.oweAudit(transaction, projectId, succeeded.notConfirmed());
        mutation.run();
        store.confirmAudit(transaction, projectId, succeeded);
        handOverOwed(transaction, projectId, (String) succeeded.details().get(AuditEvent.DETAIL_TRANSACTION_ID));
    }

    /**
     * Hands over what the project owes and answers how many records were handed over. A refusing sink fails nothing:
     * the substrate logs it and the records stay owed. Any other failure of the sink, such as a stop, propagates and
     * leaves the intent in place. Every record handed over whose transaction is not the current write's (the current
     * id is {@code null} for the recovery) is logged as replayed.
     */
    private int handOverOwed(LockTransaction transaction, String projectId, String currentId) {
        var handedOver = store.deliverOwedAudit(transaction, projectId, auditSink);
        for (var event : handedOver) {
            var transactionId = event.details().get(AuditEvent.DETAIL_TRANSACTION_ID);
            if (!Objects.equals(transactionId, currentId)) {
                LOGGER.info(INFO.ENROLMENT_AUDIT_REPLAYED, event.event(), transactionId, projectId);
            }
            if (AuditEvent.OUTCOME_FAILED.equals(event.outcome())) {
                LOGGER.warn(WARN.ENROLMENT_WRITE_NOT_CONFIRMED, event.event(), transactionId, projectId);
            }
        }
        return handedOver.size();
    }

    private static String newTransactionId() {
        return TransactionId.generate(Clock.systemUTC()).value();
    }

    private Optional<StoreDigest> recordedDigest(String projectId, byte[] sidecar) {
        try {
            return Optional.of(store.decodeSidecar(projectId, sidecar).digest());
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
    }

    private static ProjectEnrolment asEnrolled(ProjectEnrolment enrolment, boolean pilot) {
        return new ProjectEnrolment(enrolment.projectId(), enrolment.projectRoot(), enrolment.enrolledAt(), pilot,
                enrolment.containerEngine(), null, enrolment.wrappers(), enrolment.hostTools(), enrolment.build(),
                enrolment.providers(), enrolment.projectScripts(), enrolment.languageServers(),
                enrolment.commandBudgets(), enrolment.writePaths(), enrolment.buildEnvironment());
    }

    /**
     * The outcome of a re-baseline: what the operator reviewed against what was recorded.
     *
     * @param file     the re-baselined enrolment file
     * @param recorded the size and SHA-256 the old sidecar recorded; empty if there was no readable sidecar
     * @param current  the size and SHA-256 of the file, as the new sidecar records them
     * @since 0.1
     */
    public record Rebaseline(Path file, Optional<StoreDigest> recorded, StoreDigest current) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Rebaseline {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(recorded, "recorded");
            Objects.requireNonNull(current, "current");
        }
    }

    /**
     * An {@code unenrol} refused with {@code scope_active} because the project has live work; it names every item.
     *
     * @since 0.1
     */
    public static final class ScopeActiveException extends RuntimeException {

        /** The outcome code of the refusal. */
        public static final String CODE = "scope_active";

        @Serial
        private static final long serialVersionUID = 1L;

        /** The project whose {@code unenrol} was refused. */
        private final String projectId;

        /** The live work; a live work item is not serializable, so it is not kept across serialization. */
        private final transient List<LiveWork.Item> items;

        /**
         * @param projectId the project whose {@code unenrol} was refused
         * @param items     the live work of the project, not empty
         */
        ScopeActiveException(String projectId, List<LiveWork.Item> items) {
            super("unenrol of '%s' refused with %s, live work: %s".formatted(projectId, CODE, names(items)));
            this.projectId = projectId;
            this.items = List.copyOf(items);
        }

        /** @return the project whose {@code unenrol} was refused */
        public String projectId() {
            return projectId;
        }

        /** @return the live work of the project; empty only after the refusal was deserialized */
        public List<LiveWork.Item> items() {
            return items == null ? List.of() : items;
        }

        String namedItems() {
            return names(items());
        }

        private static String names(List<LiveWork.Item> items) {
            return items.stream().map(LiveWork.Item::toString).collect(Collectors.joining(", "));
        }
    }
}
