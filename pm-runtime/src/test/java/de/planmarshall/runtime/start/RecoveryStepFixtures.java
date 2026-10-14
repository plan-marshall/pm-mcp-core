/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.CrossScopeReconciler;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.OrderedWrite;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.core.store.TransactionJournal;
import de.planmarshall.core.store.TransactionJournal.Mode;
import de.planmarshall.core.store.TransactionJournal.Target;

/**
 * The stores of the tests of the implemented recovery steps, shared by the tests in this JVM and by the children of
 * {@link RuntimeChildProcess}: lease claims, the interrupted journal of a scope, and a write across two plan scopes
 * with its reconciler.
 */
final class RecoveryStepFixtures {

    /** The project being recovered. */
    static final String PROJECT_A = "project-a";

    /** Another project of the same machine. */
    static final String PROJECT_B = "project-b";

    /** The store each scope writes, relative to its path base. */
    static final String STORE = "state.json";

    /** The instant of a lease claim. */
    static final Instant ACQUIRED_AT = Instant.parse("2026-10-11T08:00:00Z");

    /** The name of the reconciliation rule of {@link CopyReconciler}. */
    static final String COPY_RULE = "copy-state";

    private RecoveryStepFixtures() {
    }

    /** The audit sink of the tests: it keeps the records it is handed, in their order. */
    static final class RecordingAuditSink implements AuditSink {

        private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void append(AuditEvent event) {
            events.add(event);
        }

        /** @return the names of the records handed over so far, in their order */
        List<String> eventNames() {
            return events.stream().map(AuditEvent::event).toList();
        }

        /** @return the records handed over so far, in their order */
        List<AuditEvent> events() {
            return List.copyOf(events);
        }
    }

    /**
     * Completes the steps of a test to the full list {@link ProjectRecovery} requires: every
     * {@link RecoveryStep.Name} without a given step gets a {@link RecoveryStep#notImplemented(RecoveryStep.Name)} seam,
     * which passes at once.
     *
     * @param given the steps the test is about
     * @return the given steps and a passing seam for each missing name
     */
    static List<RecoveryStep> completeSteps(RecoveryStep... given) {
        var steps = new ArrayList<>(List.of(given));
        var names = steps.stream().map(RecoveryStep::name).collect(Collectors.toCollection(
                () -> EnumSet.noneOf(RecoveryStep.Name.class)));
        for (var name : EnumSet.complementOf(names)) {
            steps.add(RecoveryStep.notImplemented(name));
        }
        return List.copyOf(steps);
    }

    /**
     * @return the holder of a lease served by this process
     */
    static HolderInstance self() {
        var self = ProcessHandle.current();
        return new HolderInstance(self.pid(), self.info().startInstant().orElseThrow());
    }

    /**
     * @return a holder that is not alive: the process id of this process with another start instant, as the holder
     *         of an ended runtime whose process id was reused
     */
    static HolderInstance dead() {
        var self = self();
        return new HolderInstance(self.runtimePid(), self.runtimeStartedAt().minusSeconds(86_400));
    }

    /**
     * The build-slot lease store of a base; its {@code state/} directory is created.
     *
     * @param base        the {@code PM_MCP_BASE}
     * @param lockManager the lock manager of the store
     * @return the lease store
     * @throws IOException if the directory cannot be created
     */
    static LeaseStore buildSlots(Path base, LockManager lockManager) throws IOException {
        var file = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        return new LeaseStore(new AtomicStoreFile(file, LockKey.buildSlots(base)), lockManager);
    }

    /**
     * Claims a lease of a plan of the project.
     *
     * @return the lease claimed
     */
    static LeaseRecord claim(LeaseStore store, String key, String projectId, HolderInstance holder) {
        var lease = new LeaseRecord(key, new LeaseOwner(projectId, ScopeType.PLAN, "a-plan", holder, null), ACQUIRED_AT,
                null);
        assertTrue(store.claim(lease).granted(), key);
        return lease;
    }

    /**
     * Writes what a {@link ScopeTransaction} of one replace leaves when it is stopped after its journal was written
     * and before the rename: the staged file and the journal, the store not yet replaced. The step hook that stops a
     * real commit there is internal to {@code pm-core}, so the files are written through the journal's own API.
     *
     * @param scope   the scope
     * @param content the content the transaction replaces {@link #STORE} with
     * @return the journal written
     * @throws IOException if a file cannot be written
     */
    static TransactionJournal interruptedReplace(JournalScope scope, byte[] content) throws IOException {
        Files.createDirectories(scope.journalFile().getParent());
        var transactionId = TransactionId.generate(Clock.systemUTC());
        // The staged file of a replace is <store>.tmp.<transaction id>, the only name the journal reader accepts
        var staged = STORE + ".tmp." + transactionId.value();
        Files.write(scope.resolve(staged), content);
        var journal = new TransactionJournal(transactionId, scope.type(), scope.scopeId(), Instant.now(),
                List.of(new Target(STORE, Mode.REPLACE, staged, OptionalLong.empty(), StoreDigest.of(content))));
        Files.write(scope.journalFile(), journal.write());
        return journal;
    }

    /**
     * Replaces {@link #STORE} of the scope in a scope transaction, owing the reconciliation record when one is given.
     *
     * @param transaction    a transaction that holds the key of the scope
     * @param reconciliation the record to owe, or {@code null}
     */
    static void replace(LockTransaction transaction, JournalScope scope, byte[] content, AuditSink auditSink,
            CrossScopeReconciler.Reconciliation reconciliation) {
        try {
            Files.createDirectories(scope.journalFile().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var write = ScopeTransaction.open(transaction, scope, auditSink, Clock.systemUTC());
        write.replace(STORE, content);
        if (reconciliation != null) {
            OrderedWrite.oweReconciled(write, reconciliation);
        }
        write.commit();
    }

    /**
     * The write across scopes of the tests: the authoritative plan scope first, then the derived plan scope with the
     * same content.
     *
     * @param beforeDerived runs inside the transaction of the derived step, before it writes
     * @return the ordered write
     */
    static OrderedWrite orderedWrite(JournalScope authoritative, JournalScope derived, byte[] content,
            AuditSink auditSink, Runnable beforeDerived) {
        return new OrderedWrite(List.of(
                new OrderedWrite.WriteStep(authoritative.lockKey(),
                        transaction -> replace(transaction, authoritative, content, auditSink, null)),
                new OrderedWrite.WriteStep(derived.lockKey(), transaction -> {
                    beforeDerived.run();
                    replace(transaction, derived, content, auditSink, null);
                })));
    }

    /**
     * The reconciler of {@link #orderedWrite}: it reads {@link #STORE} of the authoritative scope under its lock and
     * copies it to the derived scope, under the lock of that scope, where the derived store differs.
     *
     * @param authoritative the scope written first
     * @param derived       the scope derived from it
     * @param calls         where each call is recorded
     */
    record CopyReconciler(JournalScope authoritative, JournalScope derived, List<String> calls)
            implements CrossScopeReconciler {

        @Override
        public List<Reconciliation> reconcile(LockManager lockManager, AuditSink auditSink) {
            calls.add("reconcile");
            byte[] source;
            try (var transaction = lockManager.openTransaction()) {
                transaction.acquire(authoritative.lockKey());
                source = read(authoritative.resolve(STORE));
            }
            if (source == null) {
                return List.of();
            }
            try (var transaction = lockManager.openTransaction()) {
                transaction.acquire(derived.lockKey());
                if (Arrays.equals(source, read(derived.resolve(STORE)))) {
                    return List.of();
                }
                var reconciliation = new Reconciliation(COPY_RULE, derived.resolve(STORE).toString());
                replace(transaction, derived, source, auditSink, reconciliation);
                return List.of(reconciliation);
            }
        }

        private static byte[] read(Path file) {
            try {
                return Files.exists(file) ? Files.readAllBytes(file) : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
