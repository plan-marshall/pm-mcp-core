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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
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
 * step removes. The evidence is decided by {@link PendingEntryStore#verify} under the workspace lock: an entry is
 * residue when its four stores are absent from the workspace's digest sidecar and from the filesystem
 * ({@link PendingEntryStore.Integrity.Absent}), so a removal a stop cut off and a crash before the creating
 * transaction committed both leave exactly that state. A file of {@code pending/} whose name does not parse as an entry
 * id is never an entry and is never selected ({@link PendingEntryStore#entryIds()}), and an entry whose record cannot be
 * read is kept and reported, so a tampered entry is held for its operator instead of being swept. A store path that
 * passes through a symbolic link is held and reported the same way, so one such entry does not stop the step for the
 * project.
 * <p>
 * The residue of a run is removed in one transaction of the workspace scope: the entry's stores again as delete
 * targets (they are absent), and one {@code pending_entry_removed} record per entry, carrying its entry id, its
 * directory and the instant of the run, under a {@code transaction_id} of its own ({@link ScopeTransaction#recordId()}),
 * never the id of the transaction. Every candidate is re-judged under the workspace lock before it is removed, so an
 * entry opened between the listing and the lock is kept. The directories themselves are deleted after that
 * transaction, each as the last act, so a stop before it leaves the residue for the next run. A run whose removal
 * completes reports how many directories it left gone, names every one of them, and names every entry it kept or could
 * not remove — the held and tampered ones and a directory whose discard was refused — in one report: an entry whose
 * record cannot be read, whose store path is refused, or whose directory the discard could not remove is named there,
 * and the step's outcome is a failure then, after the readable residue was removed. A fault of the removal itself is
 * reported with what the run had staged for removal, what it had removed and every entry it kept as they stood, so
 * that fault drops none of them; whether the fault left the removals committed is decided by the workspace's journal,
 * not by the fault: a fault before the journal is written leaves every store as it was, and the next run re-judges
 * every entry; a fault after it leaves the deletions applied or owed to the recovery of the workspace, and the next
 * run then re-judges only the directories whose discard did not run (ADR runtime-start/016).
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
     * @return the directories that are gone, named one by one; a failure naming every entry kept or failed, and the
     *         unreadable entries the listing collected when the removal itself faults
     */
    @Override
    public RecoveryStep.Outcome run(String projectId, Instant at) {
        Objects.requireNonNull(at, "at");
        var workspace = JournalScope.workspace(base, projectId);
        var store = new PendingEntryStore(workspace);
        var unreadable = new ArrayList<String>();
        Removal removal;
        try {
            var candidates = new ArrayList<EntryId>();
            for (var entryId : store.entryIds()) {
                if (isResidue(store, entryId, unreadable)) {
                    candidates.add(entryId);
                }
            }
            removal = candidates.isEmpty() ? new Removal(List.of(), List.of(), null)
                    : remove(workspace, store, candidates, at);
        } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException | InternalFaultException
                | StoreRefusal e) {
            var failure = new StringBuilder("pending entry directories not swept: ").append(e.getMessage());
            if (!unreadable.isEmpty()) {
                failure.append("; kept unreadable entries ").append(unreadable);
            }
            return new RecoveryStep.Outcome.Failed(failure.toString());
        }
        var report = report(removal, unreadable);
        if (removal.fault() != null || !unreadable.isEmpty() || !removal.kept().isEmpty()) {
            return new RecoveryStep.Outcome.Failed(report);
        }
        return new RecoveryStep.Outcome.Done(report);
    }

    /**
     * Removes the residue in one workspace transaction and deletes the directories after it. Every candidate is
     * re-judged under the workspace lock before it is removed, so an entry opened between the listing and the lock is
     * kept: its record is applied by the creator's transaction before the sweep takes the lock.
     * <p>
     * A fault of the removal is no exception of this method but part of its answer: what the run had staged, removed
     * and kept travels into the report as it stood when the fault struck. Whether the fault left the staged removals
     * committed is decided by the workspace's journal, which the answer carries as the fault's own witness, so the
     * report claims no more than the journal holds.
     *
     * @return what the directories are gone, what the run kept, and the fault that stopped it
     */
    private Removal remove(JournalScope workspace, PendingEntryStore store, List<EntryId> candidates, Instant at) {
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            var transaction = ScopeTransaction.open(lock, workspace, auditSink, clock);
            var kept = new ArrayList<String>();
            var staged = new ArrayList<EntryId>();
            var removed = new ArrayList<EntryId>();
            try {
                for (var entryId : candidates) {
                    var integrity = verifyUnderLock(store, lock, entryId, kept);
                    integrity.ifPresent(verdict -> stageIfResidue(transaction, store, entryId, verdict, at, staged,
                            kept));
                }
                if (staged.isEmpty()) {
                    return new Removal(removed, kept, null);
                }
                transaction.commit();
                for (var entryId : staged) {
                    discard(store, entryId, removed, kept);
                }
                return new Removal(removed, kept, null);
            } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException | InternalFaultException
                    | StoreRefusal e) {
                // A journal that exists here is this transaction's own: every verification before the commit recovers
                // the scope's journal away, and a scope that holds one refuses a new transaction before it stages anything
                var journaled = !staged.isEmpty()
                        && Files.exists(workspace.journalFile(), LinkOption.NOFOLLOW_LINKS);
                return new Removal(removed, kept, new Fault(e.getMessage(), List.copyOf(staged), journaled));
            }
        }
    }

    /**
     * Deletes one directory whose stores the transaction removed. A refusal is named as the entry that was kept and
     * leaves the other staged directories to be deleted.
     */
    private static void discard(PendingEntryStore store, EntryId entryId, List<EntryId> removed, List<String> kept) {
        try {
            store.discard(entryId);
            removed.add(entryId);
        } catch (IllegalStateException e) {
            kept.add(entryId.value() + " (record again: " + e.getMessage() + ")");
        } catch (UncheckedIOException e) {
            kept.add(entryId.value() + " (" + e.getMessage() + ")");
        }
    }

    /**
     * The one report of a run, complete or stopped by a fault: the directories it removed, and — when a fault struck —
     * the entries whose removal it had staged with the journal's witness of whether they are committed, then every
     * entry it kept and every unreadable one the listing collected.
     */
    private static String report(Removal removal, List<String> unreadable) {
        var report = new StringBuilder();
        if (removal.fault() == null) {
            report.append("removed ").append(removal.removed().size()).append(" pending entry directories");
            if (!removal.removed().isEmpty()) {
                report.append(": ").append(names(removal.removed()));
            }
        } else {
            report.append("pending entry directories not swept: ").append(removal.fault().message());
            if (!removal.fault().staged().isEmpty()) {
                report.append(removal.fault().journaled()
                        ? "; the removals of " + names(removal.fault().staged())
                        + " are completed by the recovery of the workspace"
                        : "; the removals of " + names(removal.fault().staged()) + " were staged and not committed");
            }
            if (!removal.removed().isEmpty()) {
                report.append("; removed ").append(names(removal.removed()));
            }
        }
        if (!unreadable.isEmpty()) {
            report.append("; kept unreadable entries ").append(unreadable);
        }
        if (!removal.kept().isEmpty()) {
            report.append("; kept or failed entries: ").append(removal.kept());
        }
        return report.toString();
    }

    private static List<String> names(List<EntryId> entryIds) {
        return entryIds.stream().map(EntryId::value).toList();
    }

    /**
     * Removes one candidate whose stores are absent, keeps it as tampered when a store differs, and keeps it silently
     * when it is verified: an entry opened between the lock-free listing and the lock is intact, which is no failure.
     */
    private void stageIfResidue(ScopeTransaction transaction, PendingEntryStore store, EntryId entryId,
            PendingEntryStore.Integrity integrity, Instant at, List<EntryId> staged, List<String> kept) {
        if (integrity instanceof PendingEntryStore.Integrity.Absent) {
            store.remove(transaction, entryId);
            transaction.owe(AuditEvent.pendingEntryRemoved(entryId.value(),
                    PendingEntryStore.directoryPath(entryId), at, transaction.recordId().value()));
            staged.add(entryId);
        } else if (integrity instanceof PendingEntryStore.Integrity.Tampered) {
            kept.add(entryId.value() + " (tampered)");
        }
    }

    /**
     * Verifies one candidate under the workspace lock. A store path that passes through a symbolic link below the path
     * base is refused by the verification with a {@link JournalScope.SymbolicLinkRefusal}; that entry alone is held and
     * reported, so a link one entry's directory carries does not stop the step for the project. Any other refusal of
     * the verification is a fault of the recovery or the sidecar code, not a link: it is not read as a held entry and
     * leaves the step as the failure it is.
     *
     * @return the verdict; empty when the verification refused the entry's path, which is reported in {@code kept}
     */
    private Optional<PendingEntryStore.Integrity> verifyUnderLock(PendingEntryStore store, LockTransaction lock,
            EntryId entryId, List<String> kept) {
        try {
            return Optional.of(store.verify(lock, entryId, auditSink));
        } catch (IllegalArgumentException e) {
            holdOrRethrow(e, entryId, kept);
            return Optional.empty();
        }
    }

    /**
     * Holds an entry for the symbolic-link refusal and rethrows every other refusal, which is a fault of the recovery
     * or the sidecar code and not a held entry.
     *
     * @param refusal the refusal of the verification
     * @param entryId the entry the verification was about
     * @param kept    where a held entry is reported
     * @throws IllegalArgumentException if the refusal is no symbolic-link refusal
     */
    static void holdOrRethrow(IllegalArgumentException refusal, EntryId entryId, List<String> kept) {
        if (!(refusal instanceof JournalScope.SymbolicLinkRefusal)) {
            throw refusal;
        }
        kept.add(entryId.value() + " (held: " + refusal.getMessage() + ")");
    }

    /**
     * What one run removed and what it kept.
     *
     * @param removed the directories that are gone: a refused discard is not among them
     * @param kept    the entries that were kept or whose directory the discard refused, each with its reason
     * @param fault   the fault that stopped the removal; {@code null} when it completed
     */
    private record Removal(List<EntryId> removed, List<String> kept, @Nullable Fault fault) {
    }

    /**
     * The fault of a removal, with the entries its transaction had staged.
     *
     * @param message   the fault's message
     * @param staged    the entries whose removal the transaction had staged
     * @param journaled whether the workspace's journal survived the fault, so the recovery completes the staged removals
     */
    private record Fault(String message, List<EntryId> staged, boolean journaled) {
    }

    /**
     * Re-judges one candidate without the lock: an entry whose record exists is open; a record that cannot be read
     * is held and named in the unreadable list, and the sweep goes on with the next entry. The answer is a candidate
     * only: the entry is decided again under the workspace lock before it is removed.
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
