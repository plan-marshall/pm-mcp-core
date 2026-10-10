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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.TransactionJournal.Mode;
import de.planmarshall.core.store.TransactionJournal.Target;

/**
 * One write to the stores of a scope, run through the transaction journal of the scope (PM-IMPL-7). A single-store
 * write is a transaction with one target.
 * <p>
 * The targets are collected first, at most one per store, and written by {@link #commit()} in five steps:
 * <ol>
 * <li><em>Stage</em>: the new content of every replaced store is written to {@code <store>.tmp.<transaction id>}, and
 * the block of every append to {@code <store>.append.<transaction id>}, each forced to disk. A torn tail of a JSONL
 * store is repaired first, so the size before the append is the repaired size.</li>
 * <li><em>Journal</em>: the journal is written by atomic rename and forced to disk with its directory. From here on a
 * stopped write is completed by {@link JournalRecovery}.</li>
 * <li><em>Apply</em>: every replaced store is renamed over by its staged file, every staged block appended, and the
 * directories of the stores are forced to disk.</li>
 * <li><em>Digests</em>: the digest sidecar of the scope is written with the size and SHA-256 the journal records for
 * each target.</li>
 * <li><em>Close</em>: the journal is deleted and its directory forced to disk.</li>
 * </ol>
 * Every step is a check-then-act on files that only the holder of the scope lock writes, so the transaction is opened
 * and committed only by a transaction that holds the key of the scope. Nothing removes a staged file or the journal
 * when a step fails: a write stopped at any point leaves exactly what a killed process leaves, and the recovery of the
 * scope decides what becomes of it.
 * <p>
 * The directory of every store exists before the commit; this type does not create it.
 *
 * @since 0.1
 */
public final class ScopeTransaction {

    /**
     * A step of {@link #commit()}, after which the step hook is called.
     *
     * @since 0.1
     */
    enum Step {
        /** A target was staged; the hook is called once per target. */
        STAGE,
        /** The journal was written. */
        JOURNAL,
        /** A target was applied; the hook is called once per target. */
        APPLY,
        /** The digest sidecar was written. */
        DIGESTS
    }

    /**
     * Called between the steps of {@link #commit()} and between the targets of one step. The tests of crash
     * consistency stop a write there by throwing; production passes {@link #NONE}.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface StepHook {

        /** The hook that does nothing. */
        StepHook NONE = (step, target) -> {
            // production: no boundary is observed
        };

        /**
         * @param step   the step that just completed
         * @param target the index of the target the step completed for; 0 for a step without targets
         */
        void after(Step step, int target);
    }

    private final LockTransaction transaction;
    private final JournalScope scope;
    private final AuditSink auditSink;
    private final Clock clock;
    private final StepHook hook;
    private final Map<String, PendingTarget> targets = new LinkedHashMap<>();
    private boolean committed;

    private record PendingTarget(String path, Mode mode, byte[] content) {
    }

    private ScopeTransaction(LockTransaction transaction, JournalScope scope, AuditSink auditSink, Clock clock,
            StepHook hook) {
        this.transaction = transaction;
        this.scope = scope;
        this.auditSink = auditSink;
        this.clock = clock;
        this.hook = hook;
    }

    /**
     * @param transaction a transaction that holds the key of the scope
     * @param scope       the scope whose stores are written
     * @param auditSink   where the record of a JSONL tail repair is handed over
     * @param clock       the clock of the transaction id and the journal
     * @return an open transaction without targets
     * @throws InternalFaultException if the transaction does not hold the key of the scope
     */
    public static ScopeTransaction open(LockTransaction transaction, JournalScope scope, AuditSink auditSink,
            Clock clock) {
        return open(transaction, scope, auditSink, clock, StepHook.NONE);
    }

    /**
     * @param hook called between the steps of the commit and between the targets of one step
     * @see #open(LockTransaction, JournalScope, AuditSink, Clock)
     */
    static ScopeTransaction open(LockTransaction transaction, JournalScope scope, AuditSink auditSink, Clock clock,
            StepHook hook) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(auditSink, "auditSink");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(hook, "hook");
        StoreLockGuard.requireHeld(transaction, scope.journalFile(), scope.lockKey());
        return new ScopeTransaction(transaction, scope, auditSink, clock, hook);
    }

    /**
     * Replaces a store of the scope by new content.
     *
     * @param relativePath the store, relative to the path base of the scope
     * @param content      the new content of the store
     * @throws IllegalArgumentException if the store does not lie in a directory the scope owns, is the journal or the
     *                                  sidecar of the scope, or is a target of this transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public void replace(String relativePath, byte[] content) {
        add(relativePath, Mode.REPLACE, Objects.requireNonNull(content, "content").clone());
    }

    /**
     * Appends a block of records to a JSONL store of the scope.
     *
     * @param relativePath the store, relative to the path base of the scope
     * @param records      the records, UTF-8 encoded, each ending with a newline
     * @throws IllegalArgumentException if the block is empty or does not end with a newline, or for a store as
     *                                  {@link #replace(String, byte[])} refuses it
     * @throws IllegalStateException    if the transaction was committed
     */
    public void append(String relativePath, byte[] records) {
        JsonlStoreFile.requireBlock(scope.pathBase().resolve(relativePath), records);
        add(relativePath, Mode.APPEND, records.clone());
    }

    /**
     * Writes every target in the five steps of the journal. A commit is tried once: a transaction whose commit
     * failed leaves its journal and its staged files to the recovery of the scope, and is not committed again.
     *
     * @throws InternalFaultException if the transaction no longer holds the key of the scope; nothing was written then
     * @throws IllegalStateException  if the transaction has no target or was committed
     * @throws UncheckedIOException   if a file cannot be written; what was written stays for the recovery
     */
    public void commit() {
        requireOpen();
        if (targets.isEmpty()) {
            throw new IllegalStateException("A transaction of the scope " + scope.pathBase() + " has no target");
        }
        StoreLockGuard.requireHeld(transaction, scope.journalFile(), scope.lockKey());
        committed = true;
        var transactionId = TransactionId.generate(clock);
        try {
            var journal = stage(transactionId);
            new AtomicStoreFile(scope.journalFile(), scope.lockKey()).write(transaction, journal.write(),
                    transactionId);
            hook.after(Step.JOURNAL, 0);
            apply(journal);
            writeDigests(scope, transaction, journal);
            hook.after(Step.DIGESTS, 0);
            close(scope);
        } catch (IOException e) {
            throw new UncheckedIOException("Transaction %s of the scope %s cannot be written"
                    .formatted(transactionId, scope.pathBase()), e);
        }
    }

    private void add(String relativePath, Mode mode, byte[] content) {
        requireOpen();
        var store = scope.resolve(relativePath);
        if (store.equals(scope.journalFile()) || store.equals(scope.sidecarFile())) {
            throw new IllegalArgumentException("The journal and the sidecar are not stores of a transaction: "
                    + relativePath);
        }
        if (scope.ownedDirectories().stream().noneMatch(store::startsWith)) {
            throw new IllegalArgumentException("Store '%s' does not lie in a directory the scope owns: %s"
                    .formatted(relativePath, scope.ownedDirectories()));
        }
        if (targets.putIfAbsent(relativePath, new PendingTarget(relativePath, mode, content)) != null) {
            throw new IllegalArgumentException("Store '%s' is a target of the transaction already"
                    .formatted(relativePath));
        }
    }

    private TransactionJournal stage(TransactionId transactionId) throws IOException {
        var staged = new ArrayList<Target>();
        var index = 0;
        for (var target : targets.values()) {
            staged.add(stageTarget(target, transactionId));
            hook.after(Step.STAGE, index++);
        }
        return new TransactionJournal(transactionId, scope.type(), scope.scopeId(), clock.instant(), staged);
    }

    private Target stageTarget(PendingTarget target, TransactionId transactionId) throws IOException {
        var store = scope.resolve(target.path());
        if (target.mode() == Mode.REPLACE) {
            var stagedPath = target.path() + ".tmp." + transactionId.value();
            SyncedFiles.writeNew(scope.resolve(stagedPath), target.content());
            return new Target(target.path(), Mode.REPLACE, stagedPath, OptionalLong.empty(),
                    StoreDigest.of(target.content()));
        }
        new JsonlStoreFile(store, scope.lockKey()).repairTail(transaction, auditSink);
        var before = readIfPresent(store);
        var stagedPath = target.path() + ".append." + transactionId.value();
        SyncedFiles.writeNew(scope.resolve(stagedPath), target.content());
        var after = new byte[before.length + target.content().length];
        System.arraycopy(before, 0, after, 0, before.length);
        System.arraycopy(target.content(), 0, after, before.length, target.content().length);
        return new Target(target.path(), Mode.APPEND, stagedPath, OptionalLong.of(before.length),
                StoreDigest.of(after));
    }

    private void apply(TransactionJournal journal) throws IOException {
        var directories = new LinkedHashSet<Path>();
        var index = 0;
        for (var target : journal.targets()) {
            var store = scope.resolve(target.path());
            var staged = scope.resolve(target.stagedPath());
            if (target.mode() == Mode.REPLACE) {
                Files.move(staged, store, StandardCopyOption.ATOMIC_MOVE);
            } else {
                SyncedFiles.append(store, Files.readAllBytes(staged));
                Files.delete(staged);
            }
            directories.add(store.getParent());
            hook.after(Step.APPLY, index++);
        }
        for (var directory : directories) {
            SyncedFiles.forceDirectory(directory);
        }
    }

    /**
     * Writes the sidecar of the scope with the digests the journal records for its targets; the digests of the
     * other stores stay as they are.
     */
    static void writeDigests(JournalScope scope, LockTransaction transaction, TransactionJournal journal) {
        var sidecar = DigestSidecar.load(scope.sidecarFile());
        for (var target : journal.targets()) {
            sidecar = sidecar.with(target.path(), target.newDigest());
        }
        new AtomicStoreFile(scope.sidecarFile(), scope.lockKey()).write(transaction, sidecar.write(),
                journal.transactionId());
    }

    /** Deletes the journal and forces its directory to disk; the transaction is complete then. */
    static void close(JournalScope scope) throws IOException {
        Files.delete(scope.journalFile());
        SyncedFiles.forceDirectory(scope.journalFile().getParent());
    }

    private static byte[] readIfPresent(Path store) throws IOException {
        try {
            return Files.readAllBytes(store);
        } catch (NoSuchFileException _) {
            return new byte[0];
        }
    }

    private void requireOpen() {
        if (committed) {
            throw new IllegalStateException("The transaction of the scope %s was committed"
                    .formatted(scope.pathBase()));
        }
    }
}
