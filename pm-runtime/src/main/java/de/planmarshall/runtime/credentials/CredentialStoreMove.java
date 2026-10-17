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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

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
import de.planmarshall.runtime.enrolment.EnrolmentSidecars;
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
 * credentials lock), and mark the record {@code switched} in the same step;</li>
 * <li>delete every entry and the key from the old store;</li>
 * <li>delete the record, and append the {@code operator} audit record {@value #EVENT} with the old and the new store
 * and the number of entries.</li>
 * </ol>
 * A failed copy, read-back or sidecar verification before the switch deletes the copies from the target, deletes the
 * record and fails with {@code credential_store_failed}; the old store stays recorded. Until the switch the recorded
 * store is the old one, so the active store of {@link CredentialStoreChoice} reads no other store while the move runs.
 * The store is switched before the record is marked {@code switched}: a stop between the two leaves a record in phase
 * {@code copying}, from which {@link #resume()} copies again from the old store, which still holds every entry, so a
 * stop never deletes the only copy of an entry.
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

    private final Path base;
    private final Path stateDirectory;
    private final CredentialStoreChoice choice;
    private final LockManager locks;
    private final AuditSink auditSink;
    private final String user;
    private final Clock clock;
    private final StepHook hook;
    private final EnrolmentSidecars sidecars;
    private final AtomicStoreFile recordFile;
    private final SecureRandom random = new SecureRandom();

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
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.stateDirectory = this.base.resolve("state");
        this.choice = Objects.requireNonNull(choice, "choice");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.user = Objects.requireNonNull(user, "user");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.sidecars = new EnrolmentSidecars(this.base, user);
        this.recordFile = new AtomicStoreFile(CredentialStoreMoveRecord.file(this.base), LockKey.credentials(this.base));
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
     * @throws SecretStoreException          with {@code credential_store_failed} if a copy, a read-back or the
     *                                       verification of a sidecar failed before the switch: the copies were deleted
     *                                       from the target and the old store stays recorded; or with the reason of
     *                                       the store if an entry cannot be deleted from the old store after the
     *                                       switch, which the next start completes
     * @throws StoreRefusal                  if the machine configuration or the record cannot be read as its format
     * @throws UncheckedIOException          if the record cannot be read or written, or the audit sink refuses the
     *                                       record of the completed move, which stands then
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
            var record = new CredentialStoreMoveRecord(from, to, clock.instant(), Phase.COPYING);
            recordFile.write(transaction, record.write());
            LOGGER.info(INFO.CREDENTIAL_STORE_MOVE_STARTED, from.value(), to.value());
            hook.after(Step.RECORDED);
            copyAndSwitch(transaction, record);
            finish(transaction, record.switched());
        }
    }

    /**
     * Resumes a move a stop cut, from its record: in phase {@code copying} it deletes the copies from the target and
     * starts again from the first entry; in phase {@code switched} it finishes the deletion from the old store. Without
     * a record it does nothing.
     *
     * @return the record of the move that was resumed, as it was found; empty if no move was open
     * @throws SecretStoreException with {@code credential_store_failed} if the move fails again before the switch, or
     *                              with the reason of the store if an entry cannot be deleted from the old store
     * @throws StoreRefusal         if the record or the machine configuration cannot be read as its format
     * @throws UncheckedIOException if the record cannot be read or written, or the audit sink refuses the record of
     *                              the completed move, which stands then
     */
    public Optional<CredentialStoreMoveRecord> resume() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(recordFile.lockKey());
            var found = readRecord(transaction);
            if (found.isEmpty()) {
                return found;
            }
            var record = found.get();
            if (record.phase() == Phase.COPYING) {
                deleteCopies(record);
                copyAndSwitch(transaction, record);
                finish(transaction, record.switched());
            } else {
                finish(transaction, record);
            }
            return found;
        }
    }

    /** Copies the entries and the key, verifies the sidecars, and switches the store; fails cleanly before the switch. */
    private void copyAndSwitch(LockTransaction transaction, CredentialStoreMoveRecord record) {
        requirePhase(transaction, Phase.COPYING);
        try {
            copy(record);
        } catch (SecretStoreException | UncheckedIOException | StoreRefusal | InvalidCredentialNameException e) {
            throw failed(transaction, record, e);
        }
        requirePhase(transaction, Phase.COPYING);
        choice.record(transaction, record.to(), CredentialStoreChoice.Writer.STORE_MOVE);
        recordFile.write(transaction, record.switched().write());
        hook.after(Step.SWITCHED);
    }

    private void copy(CredentialStoreMoveRecord record) {
        var from = choice.store(record.from());
        var target = choice.store(record.to());
        for (var account : tokenLaneEntries(from)) {
            copyEntry(from, target, account);
        }
        hook.after(Step.ENTRIES_COPIED);
        var key = copyKey(record);
        hook.after(Step.KEY_COPIED);
        if (key.isPresent()) {
            Optional<EnrolmentSidecars.Failure> failure;
            try (var verification = locks.openTransaction()) {
                failure = sidecars.verify(verification, key.get());
            }
            if (failure.isPresent()) {
                throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                        "%s does not verify under the moved integrity key: %s".formatted(failure.get().file(),
                                failure.get().reason()));
            }
        }
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
     * Copies the integrity key into the target and reads it back.
     *
     * @return the key read back from the target; empty if the old store holds no key, so there is nothing signed
     */
    private Optional<SecretKey> copyKey(CredentialStoreMoveRecord record) {
        var key = keyIn(record.from()).read();
        if (key.isEmpty()) {
            return key;
        }
        var target = keyIn(record.to());
        target.write(key.get());
        var back = target.read();
        if (back.isEmpty() || !sameKey(key.get(), back.get())) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "the integrity key does not read back from '%s'".formatted(record.to().value()));
        }
        return back;
    }

    private static boolean sameKey(SecretKey expected, SecretKey actual) {
        var expectedBytes = expected.getEncoded();
        var actualBytes = actual.getEncoded();
        try {
            return MessageDigest.isEqual(expectedBytes, actualBytes);
        } finally {
            Arrays.fill(expectedBytes, (byte) 0);
            Arrays.fill(actualBytes, (byte) 0);
        }
    }

    /**
     * Ends a move that failed before the switch: logs it, deletes the copies from the target and the record. A
     * failure of the clean-up is added to the cause.
     *
     * @return the failure of the move, for the caller to throw
     */
    private SecretStoreException failed(LockTransaction transaction, CredentialStoreMoveRecord record,
            RuntimeException cause) {
        LOGGER.warn(cause, WARN.CREDENTIAL_STORE_MOVE_FAILED, record.from().value(), record.to().value(),
                record.from().value(), cause.getMessage());
        try {
            deleteCopies(record);
        } catch (SecretStoreException | UncheckedIOException | StoreRefusal | InvalidCredentialNameException e) {
            cause.addSuppressed(e);
        }
        try {
            deleteRecord(transaction);
        } catch (UncheckedIOException e) {
            cause.addSuppressed(e);
        }
        return new SecretStoreException(SecretStoreException.Reason.FAILED,
                "the move from '%s' to '%s' failed, nothing was switched and '%s' stays recorded: %s".formatted(
                        record.from().value(), record.to().value(), record.from().value(), cause.getMessage()),
                cause);
    }

    /**
     * Deletes from the target what a move copied into it: the entries the old store holds, and the key where the old
     * store holds one. An entry the target held before is never touched, since a secret never exists in two stores.
     */
    private void deleteCopies(CredentialStoreMoveRecord record) {
        var from = choice.store(record.from());
        var target = choice.store(record.to());
        for (var account : tokenLaneEntries(from)) {
            target.delete(account);
        }
        if (keyIn(record.from()).read().isPresent()) {
            keyIn(record.to()).delete();
        }
    }

    /** Deletes every entry and the key from the old store, ends the record, and logs and audits the move. */
    private void finish(LockTransaction transaction, CredentialStoreMoveRecord record) {
        requirePhase(transaction, Phase.SWITCHED);
        var old = choice.store(record.from());
        for (var account : tokenLaneEntries(old)) {
            old.delete(account);
        }
        keyIn(record.from()).delete();
        hook.after(Step.OLD_DELETED);
        var entries = tokenLaneEntries(choice.store(record.to())).size();
        deleteRecord(transaction);
        LOGGER.info(INFO.CREDENTIAL_STORE_MOVED, record.from().value(), record.to().value(), entries);
        auditSink.append(new AuditEvent(EVENT, AuditEvent.CATEGORY_OPERATOR, AuditEvent.OUTCOME_SUCCEEDED,
                Map.of(DETAIL_FROM, record.from().value(), DETAIL_TO, record.to().value(), DETAIL_ENTRIES,
                        (long) entries)));
    }

    /** @return the Token Lane entries of the store: every entry but the integrity key, which moves last */
    private static List<CredentialAccount> tokenLaneEntries(SecretStore store) {
        return store.entries().stream().filter(account -> !KEY_ACCOUNT.equals(account)).toList();
    }

    /** @return the integrity key as it lives in the store: its keyring entry, or the key file below {@code state/} */
    private EnrolmentKey keyIn(Store store) {
        return new EnrolmentKey(choice.store(store), stateDirectory, user, random);
    }

    private Optional<CredentialStoreMoveRecord> readRecord(LockTransaction transaction) {
        return recordFile.read(transaction)
                .map(document -> CredentialStoreMoveRecord.read(recordFile.store(), document));
    }

    /** Re-reads the record inside the lock before a phase. */
    private void requirePhase(LockTransaction transaction, Phase phase) {
        var record = readRecord(transaction);
        if (record.isEmpty() || record.get().phase() != phase) {
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
