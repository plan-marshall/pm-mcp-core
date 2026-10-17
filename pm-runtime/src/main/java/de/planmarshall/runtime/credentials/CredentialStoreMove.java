/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The move of every entry between the two credential stores (PM-CRED-2, PM-CRED-3, PM-SEC-5), in either direction: from
 * the OS store to the file store or back. It runs under {@code locks/credentials.lock}, the lock every credential write
 * takes, for its whole duration, so no credential is written while it runs; it re-reads its record inside the lock
 * before every phase. The steps are:
 * <ol>
 * <li>write the record {@code state/credential-store-move.json} ({@link CredentialStoreMoveRecord}, phase
 * {@code copying});</li>
 * <li>copy every Token Lane entry the old store enumerates into the target store and read each back;</li>
 * <li>copy the enrolment integrity key last, into the keyring entry of the target or the key file below
 * {@code state/}, read it back from the target, and verify every HMAC sidecar under the key read back
 * ({@link EnrolmentSidecars}: the enrolment files, {@code harnesses.json}, {@code logs/audit.head});</li>
 * <li>switch {@code credentials.store} in one write through {@link CredentialStoreChoice} (writer
 * {@link CredentialStoreChoice.Writer#STORE_MOVE}, under {@code locks/machine-config.lock}, which sorts after the
 * credentials lock);</li>
 * <li>rewrite the record in phase {@code switched};</li>
 * <li>delete every entry and the key from the old store;</li>
 * <li>append the {@code operator} audit record {@value #EVENT} with the old and the new store and the number of
 * entries, and only then delete the record, so a stop or a failing audit sink leaves the record and the next start
 * finishes the move again and appends the audit record then: at least once.</li>
 * </ol>
 * A move into a target that holds a Token Lane entry already is refused before the record is written: a secret never
 * exists in two stores, so such a target is in an inconsistent state, and the move neither overwrites nor deletes
 * what it holds. The integrity key is no Token Lane entry; where a failed deletion after a switch left it in both
 * stores, the copy of the key handles it ({@link IntegrityKeyCarry}).
 * <p>
 * Until the switch {@code credentials.store} names the old store, so the active store of {@link CredentialStoreChoice}
 * reads no other store while the move runs. Between the switch and the rewrite of the record the record still says
 * {@code copying} while {@code credentials.store} names the target already. While the record exists no other writer
 * records a store ({@link CredentialStoreChoice#record}), so that state can only be this move's own switch.
 * <p>
 * A failed copy, read-back or sidecar verification before the switch fails with {@code credential_store_failed}; the
 * old store stays recorded and untouched. The record stays exactly while the next start has a clean-up left that it
 * may do: where the old store is recorded and every copy was removed from the target, the record is deleted; where a
 * copy could not be removed, the record is kept, and the next start removes the copies and moves again from the first
 * entry. The clean-up never deletes from the recorded store: where {@code credentials.store} cannot be read, the
 * copies stay and the record is kept, so the next start resumes once it reads again; where it names the target,
 * which the lock discipline excludes, the copies stay, since the target is the recorded store, and the record is
 * deleted, since a resume would read that state as the switch of the move and delete the entries from the old store.
 * The failure says what became of the copies and of the record.
 * <p>
 * {@link #resume()} branches on the record and the recorded store: with the record in phase {@code copying} and the
 * target recorded, a stop fell between the switch and the rewrite of the record, so the switch has happened; it
 * rewrites the record to {@code switched} and finishes the deletion from the old store, and never deletes from or
 * copies into the recorded target. A stop before the switch leaves the old store recorded and holding every entry, so
 * the resume removes the copies from the target and copies again from the first entry, and a stop never deletes the
 * only copy of an entry. A record in phase {@code switched} is finished only while {@code credentials.store} names its
 * target; otherwise the resume is refused and deletes nothing.
 * <p>
 * The sidecars are verified in a transaction of their own while the move's transaction holds the credentials lock:
 * the verification takes the audit leaf lock last, after which a transaction may acquire nothing, and the move takes
 * {@code locks/machine-config.lock} after it for the switch.
 * <p>
 * No secret reaches a log record, an exception message or the audit record.
 *
 * @since 0.1
 */
public final class CredentialStoreMove {

    /** The name of the audit event of a completed move. */
    public static final String EVENT = "credential_store_moved";

    /** The detail of the audit record that names the old store. */
    public static final String DETAIL_FROM = "from";

    /** The detail of the audit record that names the new store. */
    public static final String DETAIL_TO = "to";

    /** The detail of the audit record that counts the Token Lane entries in the new store. */
    public static final String DETAIL_ENTRIES = "entries";

    private static final CuiLogger LOGGER = new CuiLogger(CredentialStoreMove.class);

    private static final CredentialAccount KEY_ACCOUNT = CredentialAccount.global(EnrolmentKey.ACCOUNT);

    /** The steps of a move, after each of which a test may stop the move as a killed process would. */
    enum Step {
        /** The record was written in phase {@code copying}. */
        RECORDED,
        /** Every Token Lane entry was copied into the target and read back. */
        ENTRIES_COPIED,
        /** The integrity key was copied into the target and read back. */
        KEY_COPIED,
        /** {@code credentials.store} names the target; the record is still in phase {@code copying}. */
        STORE_RECORDED,
        /** {@code credentials.store} names the target and the record is in phase {@code switched}. */
        SWITCHED,
        /** Every entry and the key were deleted from the old store; the record still exists. */
        OLD_DELETED
    }

    /**
     * Observes the steps of a move. A hook that throws stops the move exactly there and leaves what a killed process
     * leaves: nothing is cleaned up.
     */
    @FunctionalInterface
    interface StepHook {

        /** The hook that does nothing. */
        StepHook NONE = step -> {
            // production: no step is observed
        };

        /** @param step the step that just completed */
        void after(Step step);
    }

    private final CredentialStoreChoice choice;
    private final LockManager locks;
    private final AuditSink auditSink;
    private final Clock clock;
    private final StepHook hook;
    private final IntegrityKeyCarry keyCarry;
    private final AtomicStoreFile recordFile;

    /**
     * @param base      the base directory {@code <PM_MCP_BASE>} of the runtime
     * @param choice    the recorded store, which the move switches
     * @param locks     the lock manager
     * @param auditSink where the completed move is recorded
     * @param user      the name of the running user, the owner of the key file and of every file the sidecar
     *                  verification reads
     * @param clock     the clock of the start of a move
     */
    public CredentialStoreMove(Path base, CredentialStoreChoice choice, LockManager locks, AuditSink auditSink,
            String user, Clock clock) {
        this(base, choice, locks, auditSink, user, clock, StepHook.NONE);
    }

    /**
     * @param hook the observer of the steps of a move
     */
    CredentialStoreMove(Path base, CredentialStoreChoice choice, LockManager locks, AuditSink auditSink, String user,
            Clock clock, StepHook hook) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.choice = Objects.requireNonNull(choice, "choice");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.keyCarry = new IntegrityKeyCarry(root, locks, Objects.requireNonNull(user, "user"));
        this.recordFile = new AtomicStoreFile(CredentialStoreMoveRecord.file(root), LockKey.credentials(root));
    }

    /**
     * Moves every entry from the recorded store into the other one and records that one.
     *
     * @param to the store to move to
     * @throws CredentialStoreChoice.Refusal with {@code onboarding_incomplete} if no store is recorded yet; nothing was
     *                                       written then
     * @throws IllegalArgumentException      if the store is the recorded one; nothing was written then
     * @throws IllegalStateException         if a move is open already, which the next start resumes; nothing was
     *                                       written then
     * @throws SecretStoreException          with {@code credential_store_failed} if the target holds a Token Lane
     *                                       entry already, naming the store and the number of entries: nothing was
     *                                       written or deleted then; with {@code credential_store_failed} if a copy, a
     *                                       read-back or the verification of a sidecar failed before the switch: the
     *                                       old store stays recorded and untouched, and the message says what became
     *                                       of the copies and of the record (the record is kept where a copy cannot be
     *                                       removed or the recorded store cannot be read, and the next start resumes
     *                                       the move); or with the reason of the store if the target cannot be listed
     *                                       before the move, or an entry cannot be deleted from the old store after
     *                                       the switch, which the next start completes
     * @throws StoreRefusal                  if the machine configuration or the record cannot be read as its format
     * @throws UncheckedIOException          if the record cannot be read or written, or the audit sink refuses the
     *                                       record of the completed move: the switch stands then and the record is
     *                                       kept, so the next start finishes the move again and appends the audit
     *                                       record
     */
    public void move(Store to) {
        Objects.requireNonNull(to, "to");
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(recordFile.lockKey());
            if (readRecord(transaction).isPresent()) {
                throw new IllegalStateException("A move between the credential stores is open in "
                        + recordFile.store() + "; the next start of the runtime resumes it");
            }
            var from = choice.recorded().orElseThrow(() -> new CredentialStoreChoice.Refusal(
                    CredentialStoreChoice.Refusal.Reason.ONBOARDING_INCOMPLETE,
                    "no credential store is recorded yet, so there is nothing to move"));
            if (from == to) {
                throw new IllegalArgumentException("The credential store '%s' is recorded already".formatted(
                        to.value()));
            }
            requireEmptyTarget(to);
            var moveRecord = new CredentialStoreMoveRecord(from, to, clock.instant(), Phase.COPYING);
            recordFile.write(transaction, moveRecord.write());
            LOGGER.info(INFO.CREDENTIAL_STORE_MOVE_STARTED, from.value(), to.value());
            hook.after(Step.RECORDED);
            copyAndSwitch(transaction, moveRecord);
            finish(transaction, moveRecord.switched());
        }
    }

    /**
     * Resumes a move a stop cut, from its record and the recorded store: in phase {@code switched} while
     * {@code credentials.store} names the target, or in phase {@code copying} while {@code credentials.store} names the
     * target already (a stop between the switch and the rewrite of the record), it marks the record {@code switched}
     * and finishes the deletion from the old store; in phase {@code copying} with the old store recorded it deletes
     * the copies from the target and starts again from the first entry. Without a record it does nothing.
     *
     * @return the record of the move that was resumed, as it was found; empty if no move was open
     * @throws IllegalStateException if the record is in phase {@code switched} while {@code credentials.store} does not
     *                               name its target; nothing was deleted from either store and the record is kept
     * @throws SecretStoreException  with {@code credential_store_failed} if the move fails again before the switch, or
     *                               with the reason of the store if a copy cannot be removed from the target before
     *                               the move starts again, or an entry cannot be deleted from the old store; the
     *                               record is kept then
     * @throws StoreRefusal          if the record or the machine configuration cannot be read as its format
     * @throws UncheckedIOException  if the record cannot be read or written, or the audit sink refuses the record of
     *                               the completed move: the switch stands then and the record is kept, so the next
     *                               start finishes the move again and appends the audit record
     */
    public Optional<CredentialStoreMoveRecord> resume() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(recordFile.lockKey());
            var found = readRecord(transaction);
            if (found.isEmpty()) {
                return found;
            }
            var moveRecord = found.get();
            if (moveRecord.phase() == Phase.SWITCHED) {
                requireTargetRecorded(moveRecord);
                finish(transaction, moveRecord);
            } else if (choice.recorded().equals(Optional.of(moveRecord.to()))) {
                var switched = moveRecord.switched();
                recordFile.write(transaction, switched.write());
                finish(transaction, switched);
            } else {
                deleteCopies(moveRecord);
                copyAndSwitch(transaction, moveRecord);
                finish(transaction, moveRecord.switched());
            }
            return found;
        }
    }

    /**
     * Refuses a move into a target that holds a Token Lane entry already; nothing is written or deleted then.
     *
     * @throws SecretStoreException with {@code credential_store_failed} naming the store and the number of entries, or
     *                              with the reason of the store if it cannot be listed
     */
    private void requireEmptyTarget(Store to) {
        var target = choice.backendOf(to);
        var held = tokenLaneEntries(target).size();
        if (held > 0) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    ("the move into '%s' is refused: '%s' holds Token Lane entries already (%d), and a secret never "
                            + "exists in two stores; nothing was written or deleted")
                            .formatted(to.value(), target.name(), held));
        }
    }

    /**
     * Refuses to finish a record in phase {@code switched} while {@code credentials.store} does not name its target.
     *
     * @throws IllegalStateException naming the record and the recorded store; nothing was deleted then
     */
    private void requireTargetRecorded(CredentialStoreMoveRecord switched) {
        var recorded = choice.recorded();
        if (!recorded.equals(Optional.of(switched.to()))) {
            throw new IllegalStateException(("The record %s is in phase '%s' but credentials.store names %s, not '%s'; "
                    + "nothing was deleted from '%s' or '%s' and the record is kept").formatted(recordFile.store(),
                    Phase.SWITCHED.value(), recorded.map(store -> "'" + store.value() + "'").orElse("no store"),
                    switched.to().value(), switched.from().value(), switched.to().value()));
        }
    }

    /** Copies the entries and the key, verifies the sidecars, and switches the store; fails cleanly before the switch. */
    private void copyAndSwitch(LockTransaction transaction, CredentialStoreMoveRecord moveRecord) {
        requirePhase(transaction, Phase.COPYING);
        try {
            copy(moveRecord);
        } catch (SecretStoreException | UncheckedIOException | StoreRefusal | InvalidCredentialNameException e) {
            throw failed(transaction, moveRecord, e);
        }
        requirePhase(transaction, Phase.COPYING);
        choice.record(transaction, moveRecord.to(), CredentialStoreChoice.Writer.STORE_MOVE);
        hook.after(Step.STORE_RECORDED);
        recordFile.write(transaction, moveRecord.switched().write());
        hook.after(Step.SWITCHED);
    }

    private void copy(CredentialStoreMoveRecord moveRecord) {
        var from = choice.backendOf(moveRecord.from());
        var target = choice.backendOf(moveRecord.to());
        for (var account : tokenLaneEntries(from)) {
            copyEntry(from, target, account);
        }
        hook.after(Step.ENTRIES_COPIED);
        var key = keyCarry.copy(from, target);
        hook.after(Step.KEY_COPIED);
        key.ifPresent(keyCarry::verifySidecars);
    }

    private static void copyEntry(SecretStore from, SecretStore target, CredentialAccount account) {
        var secret = from.get(account).orElseThrow(() -> new SecretStoreException(SecretStoreException.Reason.FAILED,
                "entry '%s' vanished from '%s' during the move".formatted(account.account(), from.name())));
        target.put(account, secret);
        if (!target.get(account).map(secret::equals).orElse(false)) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "entry '%s' does not read back from '%s'".formatted(account.account(), target.name()));
        }
    }

    /**
     * Ends a move that failed before the switch: deletes the copies from the target, deletes or keeps the record, and
     * logs what became of both; the old store is never touched. The copies are deleted only while
     * {@code credentials.store} names the old store, so the clean-up never deletes from the recorded store. The record
     * stays exactly while the next start has a clean-up left that it may do:
     * <ul>
     * <li>the old store recorded and every copy removed: the record is deleted;</li>
     * <li>the old store recorded and a copy that cannot be removed: the record is kept, and the next start removes the
     * copies and moves again from the first entry;</li>
     * <li>the machine configuration unreadable: the copies stay and the record is kept, and the next start resumes
     * once the configuration reads again;</li>
     * <li>the target recorded, which the lock discipline excludes: the copies stay, since the target is the recorded
     * store, and the record is deleted, since a resume would read that state as the switch of this move and delete
     * the entries from the old store.</li>
     * </ul>
     * A failure of the clean-up is added to the cause. The move holds the credentials lock, under which alone a store
     * is recorded while its record exists, so the recorded store cannot change between its read here and the clean-up.
     *
     * @return the failure of the move, for the caller to throw
     */
    private SecretStoreException failed(LockTransaction transaction, CredentialStoreMoveRecord moveRecord,
            RuntimeException cause) {
        var recorded = recordedNow(cause);
        var from = moveRecord.from().value();
        var to = moveRecord.to().value();
        String outcome;
        boolean keepRecord;
        if (recorded.equals(Optional.of(moveRecord.from()))) {
            var removed = deleteCopiesAfterFailure(moveRecord, cause);
            keepRecord = !removed;
            outcome = removed
                    ? "the copies were removed from '%s' and '%s' stays recorded".formatted(to, from)
                    : ("the copies could not all be removed from '%s' and '%s' stays recorded; the record is kept, so "
                    + "the next start removes them and moves again").formatted(to, from);
        } else if (recorded.isPresent()) {
            keepRecord = false;
            outcome = "'%s' is recorded, so the copies stay in '%s', '%s' keeps every entry and the record is deleted"
                    .formatted(recorded.get().value(), to, from);
        } else {
            keepRecord = true;
            outcome = ("the recorded store cannot be read, so the copies stay in '%s'; the record is kept, so the next "
                    + "start resumes the move once it reads again").formatted(to);
        }
        if (!keepRecord) {
            try {
                deleteRecord(transaction);
            } catch (UncheckedIOException e) {
                cause.addSuppressed(e);
            }
        }
        LOGGER.warn(cause, WARN.CREDENTIAL_STORE_MOVE_FAILED, from, to, outcome, cause.getMessage());
        return new SecretStoreException(SecretStoreException.Reason.FAILED,
                "the move from '%s' to '%s' failed before the switch, %s: %s".formatted(from, to, outcome,
                        cause.getMessage()),
                cause);
    }

    /** @return whether every copy was removed from the target; a failure of the removal is added to the cause */
    private boolean deleteCopiesAfterFailure(CredentialStoreMoveRecord moveRecord, RuntimeException cause) {
        try {
            deleteCopies(moveRecord);
            return true;
        } catch (SecretStoreException | UncheckedIOException | StoreRefusal | InvalidCredentialNameException e) {
            cause.addSuppressed(e);
            return false;
        }
    }

    /**
     * @return the store {@code credentials.store} names now; empty, with the failure added to the cause, if the
     *         machine configuration cannot be read
     */
    private Optional<Store> recordedNow(RuntimeException cause) {
        try {
            return choice.recorded();
        } catch (StoreRefusal | UncheckedIOException e) {
            cause.addSuppressed(e);
            return Optional.empty();
        }
    }

    /**
     * Deletes from the target what a move copied into it: the entries the old store holds, and the key where the old
     * store holds one. An entry the target held before is never touched: the move refuses a target that holds a Token
     * Lane entry before it starts, so every entry deleted here is a copy of this move.
     */
    private void deleteCopies(CredentialStoreMoveRecord moveRecord) {
        var from = choice.backendOf(moveRecord.from());
        var target = choice.backendOf(moveRecord.to());
        for (var account : tokenLaneEntries(from)) {
            target.delete(account);
        }
        if (keyIn(moveRecord.from()).read().isPresent()) {
            keyIn(moveRecord.to()).delete();
        }
    }

    /**
     * Deletes every entry and the key from the old store, audits the move, ends the record, and logs the move. The
     * audit record is appended before the record is deleted, so a stop or a failing sink leaves the record, and the
     * next start finishes the move again and appends the audit record then: at least once.
     */
    private void finish(LockTransaction transaction, CredentialStoreMoveRecord moveRecord) {
        requirePhase(transaction, Phase.SWITCHED);
        var old = choice.backendOf(moveRecord.from());
        for (var account : tokenLaneEntries(old)) {
            old.delete(account);
        }
        keyIn(moveRecord.from()).delete();
        hook.after(Step.OLD_DELETED);
        var entries = tokenLaneEntries(choice.backendOf(moveRecord.to())).size();
        auditSink.append(new AuditEvent(EVENT, AuditEvent.CATEGORY_OPERATOR, AuditEvent.OUTCOME_SUCCEEDED,
                Map.of(DETAIL_FROM, moveRecord.from().value(), DETAIL_TO, moveRecord.to().value(), DETAIL_ENTRIES,
                        (long) entries)));
        deleteRecord(transaction);
        LOGGER.info(INFO.CREDENTIAL_STORE_MOVED, moveRecord.from().value(), moveRecord.to().value(), entries);
    }

    /** @return the Token Lane entries of the store: every entry but the integrity key, which moves last */
    private static List<CredentialAccount> tokenLaneEntries(SecretStore store) {
        return store.entries().stream().filter(account -> !KEY_ACCOUNT.equals(account)).toList();
    }

    /** @return the integrity key as it lives in the store: its keyring entry, or the key file below {@code state/} */
    private EnrolmentKey keyIn(Store store) {
        return keyCarry.keyIn(choice.backendOf(store));
    }

    private Optional<CredentialStoreMoveRecord> readRecord(LockTransaction transaction) {
        return recordFile.read(transaction)
                .map(document -> CredentialStoreMoveRecord.read(recordFile.store(), document));
    }

    /** Re-reads the record inside the lock before a phase. */
    private void requirePhase(LockTransaction transaction, Phase phase) {
        var current = readRecord(transaction);
        if (current.isEmpty() || current.get().phase() != phase) {
            throw new IllegalStateException("The record %s is not in phase '%s' although the move holds its lock"
                    .formatted(recordFile.store(), phase.value()));
        }
    }

    private void deleteRecord(LockTransaction transaction) {
        if (!transaction.holds(recordFile.lockKey())) {
            throw new IllegalStateException("The record is deleted only under " + recordFile.lockKey().lockFile());
        }
        var file = recordFile.store();
        try {
            if (Files.deleteIfExists(file)) {
                try (var directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                    directory.force(true);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Credential store move record cannot be deleted: " + file, e);
        }
    }
}
