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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The step {@link SweepStep.Name#PENDING_ENTRY_RECORDS} of the periodic sweep: the directory
 * {@code pending/<entry_id>/} of a pending entry whose removal a stop cut off is removed
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Periodic Sweep).
 * <p>
 * The step follows the sweep rule: its evidence is the end of the entry, never the entry's age. An entry whose record
 * {@code entry.json} exists is open and is never removed, however old it is. The creation of a scope and the
 * abandoning of an entry remove the entry's stores in one transaction of the workspace and delete the directory last;
 * a stop between the two leaves the directory behind, and the record is gone then. Such a directory is the residue the
 * step removes. A file of {@code pending/} whose name does not parse as an entry id is never an entry and is never
 * selected ({@link PendingEntryStore#entryIds()}), and an entry whose record cannot be read is kept and reported, so a
 * tampered entry is held for its operator instead of being swept.
 * <p>
 * The residue of a run is removed in one transaction of the workspace scope: the entry's stores again as delete
 * targets (they are absent), and one {@code pending_entry_removed} record per entry, carrying its entry id, its
 * directory and the instant of the run, under a {@code transaction_id} of its own ({@link ScopeTransaction#recordId()}),
 * never the id of the transaction. The directories themselves are deleted after that transaction, each as the last act,
 * so a stop before it leaves the residue for the next run. The step reports how many directories it removed; an entry
 * it could not read is reported as a failure of the step, after the readable residue was removed.
 *
 * @since 0.1
 */
public final class PendingEntryRecordsSweep implements SweepStep {

    private final Path base;
    private final LockManager lockManager;
    private final AuditSink auditSink;
    private final Clock clock;

    /**
     * @param base        the base directory of the runtime
     * @param lockManager the lock manager the workspace transaction is opened on
     * @param auditSink   where the owed records of a removal are handed over
     * @param clock       the clock of the transaction
     */
    public PendingEntryRecordsSweep(Path base, LockManager lockManager, AuditSink auditSink, Clock clock) {
        this.base = Objects.requireNonNull(base, "base");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Name name() {
        return Name.PENDING_ENTRY_RECORDS;
    }

    /**
     * Removes the directories of the project's pending entries whose removal a stop cut off.
     *
     * @param projectId the id of the enrolled project, whose recovery has finished
     * @param at        the instant of the sweep's run, which the removal records carry
     * @return how many directories were removed; a failure if an entry could not be read or the removal could not be
     *         written
     */
    @Override
    public RecoveryStep.Outcome run(String projectId, Instant at) {
        Objects.requireNonNull(at, "at");
        var workspace = JournalScope.workspace(base, projectId);
        var store = new PendingEntryStore(workspace);
        try {
            var candidates = new ArrayList<EntryId>();
            var unreadable = new ArrayList<String>();
            for (var entryId : store.entryIds()) {
                if (isResidue(store, entryId, unreadable)) {
                    candidates.add(entryId);
                }
            }
            var removed = candidates.isEmpty() ? 0 : remove(workspace, store, candidates, at);
            var report = "removed " + removed + " pending entry directories";
            if (!unreadable.isEmpty()) {
                return new RecoveryStep.Outcome.Failed(report + "; kept unreadable entries " + unreadable);
            }
            return new RecoveryStep.Outcome.Done(report);
        } catch (UncheckedIOException | IllegalStateException | InternalFaultException e) {
            return new RecoveryStep.Outcome.Failed("pending entry directories not swept: " + e.getMessage());
        }
    }

    /**
     * Removes the residue in one workspace transaction and deletes the directories after it; the decision is taken
     * under the workspace lock, so an entry opened meanwhile is judged as it is now.
     *
     * @return the number of removed directories
     */
    private int remove(JournalScope workspace, PendingEntryStore store, List<EntryId> candidates, Instant at) {
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            var transaction = ScopeTransaction.open(lock, workspace, auditSink, clock);
            var removed = new ArrayList<EntryId>();
            for (var entryId : candidates) {
                store.remove(transaction, entryId);
                transaction.owe(AuditEvent.pendingEntryRemoved(entryId.value(),
                        PendingEntryStore.directoryPath(entryId), at, transaction.recordId().value()));
                removed.add(entryId);
            }
            if (removed.isEmpty()) {
                return 0;
            }
            transaction.commit();
            removed.forEach(store::discard);
            return removed.size();
        }
    }

    /**
     * Decides whether one entry directory is residue. A record that exists means the entry is open: it is kept. A
     * record that is refused, or that cannot be read at all, is kept and named in the unreadable list, and the sweep
     * goes on with the next entry. An absent record is the evidence of a removal a stop cut off.
     *
     * @return whether the directory is residue
     */
    private static boolean isResidue(PendingEntryStore store, EntryId entryId, List<String> unreadable) {
        try {
            return store.read(entryId).isEmpty();
        } catch (StoreRefusal e) {
            unreadable.add(entryId.value() + " (" + e.outcome().code() + ")");
        } catch (UncheckedIOException e) {
            unreadable.add(entryId.value() + " (io: " + e.getMessage() + ")");
        }
        return false;
    }
}
