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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.TransactionJournal.Mode;
import de.planmarshall.core.store.TransactionJournal.Target;

/**
 * The recovery of a scope that writes through its transaction journal (PM-IMPL-7): it completes a transaction that
 * was stopped after its journal was written, and removes what a transaction left that never journaled.
 * <p>
 * Every decision of the recovery is a check-then-act on the files of the scope: whether a target is applied already
 * and then a rename, whether a staged file has a journal and then a delete. It runs only in a transaction that holds
 * the key of the scope, so no writer of the scope comes between a check and its act. It deletes only files in the
 * directories the scope owns whose name ends in {@code .tmp.<transaction id>} or {@code .append.<transaction id>}:
 * the staged files of another scope's transaction in flight never lie there.
 * <p>
 * A journal is rolled forward or, where that is impossible, left alone:
 * <ul>
 * <li>A target is <em>applied</em> when its store has the size and SHA-256 the journal records, and
 * <em>appliable</em> when its staged file is present. An appliable replace is renamed over its store; an appliable
 * append cuts its store back to the size before the append and appends the staged block once.</li>
 * <li>A target that is neither has lost what the transaction wrote. Then nothing is applied, the journal and every
 * store stay in place, and the stores are reported for the caller to raise the integrity breach.</li>
 * <li>A journal that is refused (unreadable or of another format version) is never deleted; the refusal
 * propagates.</li>
 * </ul>
 *
 * @since 0.1
 */
@UtilityClass
public class JournalRecovery {

    private static final String STAGED_TMP = ".tmp.";
    private static final String STAGED_APPEND = ".append.";

    /**
     * What a recovery of a scope found and did.
     *
     * @since 0.1
     */
    public sealed interface Outcome permits NothingToRecover, RolledForward, InvariantViolation {
    }

    /**
     * The scope has no journal. The staged files that a transaction left without journaling them were deleted.
     *
     * @param deletedStagedFiles the staged files deleted; empty if there were none
     * @since 0.1
     */
    public record NothingToRecover(List<Path> deletedStagedFiles) implements Outcome {

        /** @throws NullPointerException if the list is {@code null} */
        public NothingToRecover {
            deletedStagedFiles = List.copyOf(deletedStagedFiles);
        }
    }

    /**
     * The journal was rolled forward: every target is applied, the sidecar holds the digests of the journal, the
     * journal and the staged files are deleted, and one {@code transaction_rolled_forward} record was handed to the
     * audit sink.
     *
     * @param journal the journal that was rolled forward
     * @param applied the targets the recovery applied, relative to the path base; the others were applied already
     * @since 0.1
     */
    public record RolledForward(TransactionJournal journal, List<String> applied) implements Outcome {

        /** @throws NullPointerException if a component is {@code null} */
        public RolledForward {
            Objects.requireNonNull(journal, "journal");
            applied = List.copyOf(applied);
        }
    }

    /**
     * The journal cannot be rolled forward: a target has neither the digest the journal records nor its staged file,
     * or an append target's store is shorter than it was before the append. Nothing was applied, and the journal and
     * every store are as they were. It is also the answer of a {@link ScopeIntegrity} verification that ran the
     * recovery first.
     *
     * @param journal the journal that was left in place
     * @param stores  the violating stores, relative to the path base, in the order of the journal
     * @since 0.1
     */
    public record InvariantViolation(TransactionJournal journal, List<String> stores)
            implements Outcome, ScopeIntegrity.Verification {

        /** @throws NullPointerException if a component is {@code null} */
        public InvariantViolation {
            Objects.requireNonNull(journal, "journal");
            stores = List.copyOf(stores);
        }
    }

    /**
     * Recovers the scope.
     *
     * @param transaction a transaction that holds the key of the scope
     * @param scope       the scope
     * @param auditSink   where the record of a roll-forward is handed over
     * @return what the recovery found and did
     * @throws InternalFaultException if the transaction does not hold the key of the scope; nothing was touched then
     * @throws StoreRefusal           if the journal or the sidecar is unreadable or of another format version; the
     *                                journal is left in place then
     * @throws UncheckedIOException   if a file of the scope cannot be read or written
     */
    public static Outcome recover(LockTransaction transaction, JournalScope scope, AuditSink auditSink) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(auditSink, "auditSink");
        StoreLockGuard.requireHeld(transaction, scope.journalFile(), scope.lockKey());
        try {
            var document = StoreSnapshot.read(scope.journalFile()).content();
            if (document.isEmpty()) {
                return new NothingToRecover(deleteStagedFiles(scope));
            }
            var journal = TransactionJournal.read(scope.journalFile(), document.get());
            var violating = violatingStores(scope, journal);
            if (!violating.isEmpty()) {
                return new InvariantViolation(journal, violating);
            }
            var applied = applyPending(scope, journal);
            ScopeTransaction.writeDigests(scope, transaction, journal);
            ScopeTransaction.close(scope);
            deleteStagedFiles(scope);
            auditSink.append(AuditEvent.transactionRolledForward(scope.type().wireName(), scope.scopeId(),
                    journal.transactionId().value(), journal.targets().stream().map(Target::path).toList()));
            return new RolledForward(journal, applied);
        } catch (IOException e) {
            throw new UncheckedIOException("Scope cannot be recovered: " + scope.pathBase(), e);
        }
    }

    private static List<String> violatingStores(JournalScope scope, TransactionJournal journal) throws IOException {
        var violating = new ArrayList<String>();
        for (var target : journal.targets()) {
            var store = scope.resolve(target.path());
            if (isApplied(store, target)) {
                continue;
            }
            var stagedMissing = !exists(scope.resolve(target.stagedPath()));
            var cutShort = target.mode() == Mode.APPEND && size(store) < target.sizeBefore().orElseThrow();
            if (stagedMissing || cutShort) {
                violating.add(target.path());
            }
        }
        return violating;
    }

    private static List<String> applyPending(JournalScope scope, TransactionJournal journal) throws IOException {
        var applied = new ArrayList<String>();
        var directories = new LinkedHashSet<Path>();
        for (var target : journal.targets()) {
            var store = scope.resolve(target.path());
            if (isApplied(store, target)) {
                continue;
            }
            var staged = scope.resolve(target.stagedPath());
            if (target.mode() == Mode.REPLACE) {
                Files.move(staged, store, StandardCopyOption.ATOMIC_MOVE);
            } else {
                var sizeBefore = target.sizeBefore().orElseThrow();
                if (exists(store)) {
                    SyncedFiles.truncate(store, sizeBefore);
                }
                SyncedFiles.append(store, Files.readAllBytes(staged));
            }
            directories.add(store.getParent());
            applied.add(target.path());
        }
        for (var directory : directories) {
            SyncedFiles.forceDirectory(directory);
        }
        return applied;
    }

    private static boolean isApplied(Path store, Target target) throws IOException {
        return exists(store) && target.newDigest().matches(Files.readAllBytes(store));
    }

    /** Deletes the staged files of the scope's owned directories; no journal may name them when this runs. */
    private static List<Path> deleteStagedFiles(JournalScope scope) throws IOException {
        var deleted = new ArrayList<Path>();
        for (var owned : scope.ownedDirectories()) {
            if (!exists(owned)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(owned)) {
                var staged = files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                        .filter(JournalRecovery::isStagedFile).toList();
                for (var file : staged) {
                    Files.delete(file);
                    deleted.add(file);
                }
            }
        }
        if (!deleted.isEmpty()) {
            for (var directory : deleted.stream().map(Path::getParent).distinct().toList()) {
                SyncedFiles.forceDirectory(directory);
            }
        }
        return deleted;
    }

    private static boolean isStagedFile(Path file) {
        var name = file.getFileName().toString();
        return TransactionId.ofSuffix(name).filter(id -> {
            var prefix = name.substring(0, name.length() - id.value().length());
            return prefix.endsWith(STAGED_TMP) || prefix.endsWith(STAGED_APPEND);
        }).isPresent();
    }

    private static boolean exists(Path file) {
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS);
    }

    private static long size(Path file) throws IOException {
        return exists(file) ? Files.size(file) : 0;
    }
}
