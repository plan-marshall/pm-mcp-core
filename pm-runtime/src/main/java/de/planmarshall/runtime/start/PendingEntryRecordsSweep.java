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
import java.util.Optional;


import de.cuioss.tools.logging.CuiLogger;
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
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The step {@link SweepStep.Name#PENDING_ENTRY_RECORDS} of the periodic sweep: every pending entry record
 * {@code pending/<entry_id>.json} of the project's workspace scope created before the instant the step is given is
 * removed (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Periodic Sweep).
 * <p>
 * The step follows the sweep rule. Its only evidence is the age: the creation instant a record states, read under the
 * workspace lock. A file of {@code pending/} whose name does not parse as an entry id is never an entry and is never
 * selected ({@link PendingEntryStore#entryIds()}), and a record that cannot be read states no age and is kept.
 * <p>
 * All the old records of a run are removed in one transaction of the workspace scope, each as a delete target, and
 * the transaction owes one {@code pending_entry_removed} record per removed record, carrying its entry id, its store
 * path, its creation instant and a {@code transaction_id} of its own ({@link ScopeTransaction#recordId()}), never the
 * id of the transaction: two records removed in one run carry two ids, and the id is the key by which the reader of
 * the audit log drops a record handed over again. A stopped removal is therefore completed by the recovery of the
 * workspace, which hands the owed records over again under the same ids. The step reports how many records it
 * removed; a record it could not read is reported as a failure of the step, after the readable old records were
 * removed.
 *
 * @since 0.1
 */
public final class PendingEntryRecordsSweep implements SweepStep {

    private static final CuiLogger LOGGER = new CuiLogger(PendingEntryRecordsSweep.class);

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
     * Removes every pending entry record of the project created before the instant.
     *
     * @param projectId the id of the enrolled project, whose recovery has finished
     * @param olderThan the instant before which a record is old enough to be removed
     * @return how many records were removed; a failure if a record could not be read or the removal could not be
     *         written
     */
    @Override
    public RecoveryStep.Outcome run(String projectId, Instant olderThan) {
        Objects.requireNonNull(olderThan, "olderThan");
        var workspace = JournalScope.workspace(base, projectId);
        var store = new PendingEntryStore(workspace);
        try {
            var candidates = store.entryIds();
            if (candidates.isEmpty()) {
                return new RecoveryStep.Outcome.Done("no pending entry record");
            }
            var unreadable = new ArrayList<String>();
            var removed = remove(workspace, store, List.copyOf(candidates), olderThan, unreadable);
            if (removed > 0) {
                LOGGER.info(INFO.PENDING_ENTRY_RECORDS_SWEPT, removed, projectId);
            }
            var report = "removed " + removed + " pending entry records";
            if (!unreadable.isEmpty()) {
                return new RecoveryStep.Outcome.Failed(report + "; kept unreadable records " + unreadable);
            }
            return new RecoveryStep.Outcome.Done(report);
        } catch (UncheckedIOException | IllegalStateException | InternalFaultException e) {
            return new RecoveryStep.Outcome.Failed("pending entry records not swept: " + e.getMessage());
        }
    }

    /**
     * Removes the old records in one workspace transaction; the decision on the age is taken under the workspace
     * lock, so a record created or replaced meanwhile is judged as it is now.
     *
     * @return the number of removed records
     */
    private int remove(JournalScope workspace, PendingEntryStore store, List<EntryId> candidates, Instant olderThan,
            List<String> unreadable) {
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            var transaction = ScopeTransaction.open(lock, workspace, auditSink, clock);
            var removed = 0;
            for (var entryId : candidates) {
                var entry = readCandidate(store, entryId, unreadable);
                if (entry.isPresent() && entry.get().createdBefore(olderThan)) {
                    store.remove(transaction, entryId);
                    transaction.owe(AuditEvent.pendingEntryRemoved(entryId.value(),
                            PendingEntryStore.storePath(entryId), entry.get().createdAt().toString(),
                            transaction.recordId().value()));
                    removed++;
                }
            }
            if (removed > 0) {
                transaction.commit();
            }
            return removed;
        }
    }

    /**
     * Reads one candidate. A record that is refused, or that cannot be read at all, for example because it was removed
     * or made unreadable after the listing, states no age: it is kept and named in the unreadable list, and the sweep
     * goes on with the next candidate.
     *
     * @return the record; empty if it does not exist or could not be read
     */
    private static Optional<PendingEntryRecord> readCandidate(PendingEntryStore store, EntryId entryId,
            List<String> unreadable) {
        try {
            return store.read(entryId);
        } catch (StoreRefusal e) {
            unreadable.add(entryId.value() + " (" + e.outcome().code() + ")");
        } catch (UncheckedIOException e) {
            unreadable.add(entryId.value() + " (io: " + e.getMessage() + ")");
        }
        return Optional.empty();
    }
}
