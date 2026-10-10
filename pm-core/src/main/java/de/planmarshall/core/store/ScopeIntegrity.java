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

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JournalRecovery.InvariantViolation;

/**
 * The digest verification of the stores of a scope (PM-IMPL-7): whether a store still has the size and SHA-256 its
 * digest sidecar records, decided under the lock of the scope, reported unverified without it, and re-recorded when
 * the operator accepts an edit.
 * <p>
 * A reader without the lock may see a transaction in flight: a store already replaced and its sidecar entry not yet
 * written, or a journal present. Deciding on that view would be a check-then-act on files a writer is changing. So
 * only {@link #verify} and {@link #verifyAll} decide: they require the key of the scope, which every writer of the
 * scope holds, and run the recovery of the scope first, so the stores and the sidecar they compare are those of the
 * last complete transaction. {@link #snapshot} takes no lock and never decides: it reports a store whose content
 * differs, and every store while a journal exists, as {@link StoreIntegrity#DIGEST_UNVERIFIED}.
 * <p>
 * A digest is recorded only through the transaction journal of the scope, so {@link #rebaseline} re-records one by a
 * {@link ScopeTransaction} that replaces the store by its own current bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class ScopeIntegrity {

    /**
     * The answer of a locked verification of one store.
     *
     * @since 0.1
     */
    public sealed interface Verification permits Verified, Mismatch, Untracked, InvariantViolation {
    }

    /**
     * The store has the size and SHA-256 its sidecar entry records.
     *
     * @param store  the store, relative to the path base of the scope
     * @param digest the recorded digest, which the store has
     * @since 0.1
     */
    public record Verified(String store, StoreDigest digest) implements Verification {

        /** @throws NullPointerException if a component is {@code null} */
        public Verified {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(digest, "digest");
        }
    }

    /**
     * The store differs from its sidecar entry, after the recovery of the scope: it was changed or deleted outside a
     * transaction. This is the evidence the caller raises an integrity breach of the kind {@code store_tampered} on.
     *
     * @param store    the store, relative to the path base of the scope
     * @param recorded the digest the sidecar records
     * @param current  the digest of the store as it is; empty if the store does not exist
     * @since 0.1
     */
    public record Mismatch(String store, StoreDigest recorded, Optional<StoreDigest> current) implements Verification {

        /** @throws NullPointerException if a component is {@code null} */
        public Mismatch {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(recorded, "recorded");
            Objects.requireNonNull(current, "current");
        }
    }

    /**
     * The sidecar has no entry for the store: no transaction of the scope wrote it, so there is nothing to compare.
     *
     * @param store the store, relative to the path base of the scope
     * @since 0.1
     */
    public record Untracked(String store) implements Verification {

        /** @throws NullPointerException if the store is {@code null} */
        public Untracked {
            Objects.requireNonNull(store, "store");
        }
    }

    /**
     * Recovers the scope and verifies one store against its sidecar entry.
     *
     * @param transaction  a transaction that holds the key of the scope
     * @param scope        the scope
     * @param relativePath the store, relative to the path base of the scope
     * @param auditSink    where the record of a roll-forward of the recovery is handed over
     * @return the answer for the store; the {@link InvariantViolation} of the recovery if it could not roll the
     *         journal forward, and then nothing was compared
     * @throws InternalFaultException   if the transaction does not hold the key of the scope; nothing was touched then
     * @throws IllegalArgumentException if the path does not lie below the path base of the scope
     * @throws StoreRefusal             if the journal or the sidecar is unreadable or of another format version
     * @throws UncheckedIOException     if a file of the scope cannot be read or written
     */
    public static Verification verify(LockTransaction transaction, JournalScope scope, String relativePath,
            AuditSink auditSink) {
        Objects.requireNonNull(scope, "scope");
        scope.resolve(relativePath);
        if (JournalRecovery.recover(transaction, scope, auditSink) instanceof InvariantViolation violation) {
            return violation;
        }
        return compare(scope, DigestSidecar.load(scope.sidecarFile()), relativePath);
    }

    /**
     * Recovers the scope and verifies every store its sidecar has an entry for.
     *
     * @param transaction a transaction that holds the key of the scope
     * @param scope       the scope
     * @param auditSink   where the record of a roll-forward of the recovery is handed over
     * @return one answer per sidecar entry, in the sorted order of the store paths; or the {@link InvariantViolation}
     *         of the recovery alone if it could not roll the journal forward, and then nothing was compared
     * @throws InternalFaultException if the transaction does not hold the key of the scope; nothing was touched then
     * @throws StoreRefusal           if the journal or the sidecar is unreadable or of another format version
     * @throws UncheckedIOException   if a file of the scope cannot be read or written
     */
    public static List<Verification> verifyAll(LockTransaction transaction, JournalScope scope,
            AuditSink auditSink) {
        Objects.requireNonNull(scope, "scope");
        if (JournalRecovery.recover(transaction, scope, auditSink) instanceof InvariantViolation violation) {
            return List.of(violation);
        }
        var sidecar = DigestSidecar.load(scope.sidecarFile());
        var answers = new ArrayList<Verification>();
        for (var store : sidecar.stores().keySet()) {
            answers.add(compare(scope, sidecar, store));
        }
        return List.copyOf(answers);
    }

    /**
     * Reads a store without a lock and compares it with its sidecar entry. The result never decides that the store was
     * edited.
     *
     * @param scope        the scope
     * @param relativePath the store, relative to the path base of the scope
     * @return the snapshot of the store: {@link StoreIntegrity#DIGEST_UNVERIFIED} while the journal of the scope
     *         exists or if the content differs from the sidecar entry, {@link StoreIntegrity#VERIFIED} if it matches,
     *         and {@link StoreIntegrity#UNCHECKED} if the sidecar has no entry for the store
     * @throws IllegalArgumentException if the path does not lie below the path base of the scope
     * @throws StoreRefusal             if the sidecar is unreadable or of another format version
     * @throws UncheckedIOException     if the store or the sidecar exists and cannot be read
     */
    public static StoreSnapshot snapshot(JournalScope scope, String relativePath) {
        Objects.requireNonNull(scope, "scope");
        var store = scope.resolve(relativePath);
        if (Files.exists(scope.journalFile(), LinkOption.NOFOLLOW_LINKS)) {
            return StoreSnapshot.unverified(store);
        }
        return DigestSidecar.load(scope.sidecarFile()).digest(relativePath)
                .map(recorded -> StoreSnapshot.read(store, recorded.size(), recorded.sha256()))
                .orElseGet(() -> StoreSnapshot.read(store));
    }

    /**
     * Re-records the digest of one store as it is now, through the journal of the scope, and verifies the scope
     * again. This is how an edit of a store outside a transaction is accepted.
     *
     * @param transaction  a transaction that holds the key of the scope
     * @param scope        the scope
     * @param relativePath the store, relative to the path base of the scope
     * @param auditSink    where the records of the recovery and the transaction are handed over
     * @return {@link #verifyAll} of the scope after the re-baseline; the {@link InvariantViolation} of the recovery
     *         alone if it could not roll a journal forward, and then nothing was re-recorded
     * @throws InternalFaultException   if the transaction does not hold the key of the scope; nothing was touched then
     * @throws IllegalArgumentException if the store does not exist, or is no store a transaction of the scope writes;
     *                                  nothing was journaled then
     * @throws StoreRefusal             if the journal or the sidecar is unreadable or of another format version
     * @throws UncheckedIOException     if a file of the scope cannot be read or written
     */
    public static List<Verification> rebaseline(LockTransaction transaction, JournalScope scope, String relativePath,
            AuditSink auditSink) {
        Objects.requireNonNull(scope, "scope");
        var store = scope.resolve(relativePath);
        if (JournalRecovery.recover(transaction, scope, auditSink) instanceof InvariantViolation violation) {
            return List.of(violation);
        }
        var content = StoreSnapshot.read(store).content().orElseThrow(() -> new IllegalArgumentException(
                "Store '%s' does not exist; there is no digest to re-record".formatted(relativePath)));
        var write = ScopeTransaction.open(transaction, scope, auditSink, Clock.systemUTC());
        write.replace(relativePath, content);
        write.commit();
        return verifyAll(transaction, scope, auditSink);
    }

    private static Verification compare(JournalScope scope, DigestSidecar sidecar, String relativePath) {
        var recorded = sidecar.digest(relativePath);
        if (recorded.isEmpty()) {
            return new Untracked(relativePath);
        }
        var current = StoreSnapshot.read(scope.resolve(relativePath)).content().map(StoreDigest::of);
        if (current.filter(recorded.get()::equals).isPresent()) {
            return new Verified(relativePath, recorded.get());
        }
        return new Mismatch(relativePath, recorded.get(), current);
    }
}
