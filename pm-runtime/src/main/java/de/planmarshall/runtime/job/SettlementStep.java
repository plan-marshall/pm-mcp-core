/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.io.UncheckedIOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.WorkerStateCodec;

/**
 * One step of the settlement of a job at its end (PM-EXEC-4, PM-ARCH-7), in the fixed write order of
 * {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc}, Outcome Derivation, Write order at job end.
 * {@link JobSettlement} runs the steps in the order of {@link Name}.
 * <p>
 * Two steps are implemented: the fate of the job record ({@link #fate}) and the release of the build slot through the
 * {@link LeaseStore} of its machine lease store ({@link #slotRelease()}). A worker's model slot is no lease-store slot
 * and is released through {@code ModelSlotScheduler.release}. The steps whose stores do not
 * exist in this runtime yet are named seams ({@link #notImplemented}) that do nothing and report
 * {@value #NOT_IMPLEMENTED}, like the seams of the per-project recovery: the worker queue task record, the metrics row,
 * the change-ledger row, the timing observation and the removal of a worker's job-private directory. The fence of a
 * worker's generation is no step of the settlement: the supervisor's end path performs it after the settlement, once it
 * reads the settled record ({@code WorkerEnding}). The runner revokes the worker's token once the settlement has
 * recorded the fate, and the end path revokes it once more.
 * <p>
 * A step reports what it did as an {@link Outcome}; a failure of its store it can name is a {@link Outcome.Failed},
 * and the settlement stops there.
 *
 * @since 0.1
 */
public interface SettlementStep {

    /** The report of a step whose store is not implemented in this runtime yet. */
    String NOT_IMPLEMENTED = "not implemented in this runtime";

    /**
     * The steps of the settlement, in the fixed write order of the specification.
     *
     * @since 0.1
     */
    enum Name {
        /**
         * The fate of the job record; for a worker with its terminal worker block. The generation's fence follows in
         * the supervisor's end path, not in this step; the runner revokes the job token once the settlement has
         * recorded the fate.
         */
        FATE,
        /** For a worker, the queue task record: the attempt end and the task lease release. */
        WORKER_QUEUE_TASK,
        /** The metrics row of the attempt that ended with the job, with its usage parsed. */
        METRICS_ROW,
        /** The change-ledger row of a job that stamps one. */
        CHANGE_LEDGER_ROW,
        /** The timing observation of the job's budget key. */
        TIMING_OBSERVATION,
        /** The release of the build slot the job held. */
        SLOT_RELEASE,
        /** The removal of a worker's job-private directory, last. */
        JOB_PRIVATE_DIRECTORY
    }

    /**
     * What a step did.
     *
     * @since 0.1
     */
    sealed interface Outcome {

        /**
         * The step is done.
         *
         * @param report a short report of what the step did
         * @since 0.1
         */
        record Done(String report) implements Outcome {

            /** @throws NullPointerException if the report is {@code null} */
            public Done {
                Objects.requireNonNull(report, "report");
            }
        }

        /**
         * The step failed; the settlement stops, and no later step runs.
         *
         * @param reason why the step failed
         * @since 0.1
         */
        record Failed(String reason) implements Outcome {

            /** @throws NullPointerException if the reason is {@code null} */
            public Failed {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * The slot a job held: the key of its lease in a machine lease store and the owner the lease was claimed with.
     *
     * @param store         the machine lease store of the slot, build slots today; a model slot is released through
     *                      {@code ModelSlotScheduler.release}, not through a lease store
     * @param key           the key of the lease
     * @param expectedOwner the owner the lease was claimed with
     * @since 0.1
     */
    record Slot(LeaseStore store, String key, LeaseOwner expectedOwner) {

        /** @throws NullPointerException if a component is {@code null} */
        public Slot {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(expectedOwner, "expectedOwner");
        }
    }

    /**
     * What is settled: the settled record of the job and the slot it held.
     *
     * @param job  the record of the job, {@code refused} or {@code ended}, with its fate
     * @param slot the slot the job held; {@code null} for a job that needs none or was never admitted
     * @since 0.1
     */
    record Settled(JobRecord job, @Nullable Slot slot) {

        /**
         * @throws NullPointerException     if the job is {@code null}
         * @throws IllegalArgumentException if the record is not settled
         */
        public Settled {
            Objects.requireNonNull(job, "job");
            if (!job.status().isSettled()) {
                throw new IllegalArgumentException("A settled job has a fate: " + job.jobId());
            }
        }
    }

    /** @return the name of the step, which sets its place in the order */
    Name name();

    /**
     * Runs the step for one settled job.
     * <p>
     * A step after the fate never throws. A failure of its store that it can name is an {@link Outcome.Failed}; the
     * settlement turns a runtime fault it could not report as a value into a {@code Failed} as well, so a later step
     * never hides a durable end from the settlement wait. The fate step is the exception: a fault it does not report as
     * a {@code Failed} propagates so the runner can retry the record write; a store fault it names is a {@code Failed}
     * step and the record stays for the recovery.
     *
     * @param settled the job and its slot
     * @return what the step did
     */
    Outcome run(Settled settled);

    /**
     * A named seam whose store comes with a later component: it does nothing and reports {@value #NOT_IMPLEMENTED}.
     *
     * @param name the name of the step
     * @return the no-op step
     */
    static SettlementStep notImplemented(Name name) {
        Objects.requireNonNull(name, "name");
        return new SettlementStep() {

            @Override
            public Name name() {
                return name;
            }

            @Override
            public Outcome run(Settled settled) {
                return new Outcome.Done(NOT_IMPLEMENTED);
            }
        };
    }

    /**
     * The first step: the settled record replaces the job's record, in one transaction under the lock of the scope
     * that owns the job. For a worker, the terminal worker block is written with the fate in that same transaction, so
     * a settled record carries its terminal state and no later write has to change it ({@link JobRecordStore} refuses
     * a worker write into a settled record, and that refusal stays). The fence of the generation is performed by
     * the supervisor's end path after the settlement, and the job token is revoked by the runner once the settlement
     * has recorded the fate, not by this step.
     *
     * @param store       the job records of the job's store
     * @param lockManager the lock manager of the runtime
     * @return the fate step
     */
    static SettlementStep fate(JobRecordStore store, LockManager lockManager) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(lockManager, "lockManager");
        return new SettlementStep() {

            @Override
            public Name name() {
                return Name.FATE;
            }

            @Override
            public Outcome run(Settled settled) {
                var job = settled.job();
                try (var transaction = lockManager.openTransaction()) {
                    store.writeFate(transaction, withTerminalWorker(store, job, transaction));
                } catch (UncheckedIOException | StoreRefusal e) {
                    return new Outcome.Failed("the fate of %s was not recorded: %s".formatted(job.jobId(),
                            e.getMessage()));
                }
                var report = "fate %s recorded".formatted(job.fate().wireName());
                return new Outcome.Done(job.jobId().kind() == JobId.Kind.LLM ? report + "; worker block terminal"
                        : report);
            }
        };
    }

    /**
     * Replaces the worker block of a settled record with its terminal state; a job that is no worker keeps its record
     * as it is, and so does a record whose block is terminal already or cannot be read — the fate is what must be
     * durable, and a block the codec refuses is left for the reader that reports it.
     * <p>
     * The block is the one the record carries under the transaction's lock, not the one the runner read when it decided
     * the fate: a worker state the transport wrote after that read ({@code writeWorker}) is newer than the runner's
     * snapshot, and the fate write replaces the whole record. Deriving the terminal state from the stored block keeps
     * that newer state — the connection instant among it — from being overwritten by the older text.
     *
     * @param store       the job records of the job's store
     * @param job         the settled record
     * @param transaction the transaction that holds the lock of the record's scope
     * @return the record the fate write stores
     */
    private static JobRecord withTerminalWorker(JobRecordStore store, JobRecord job, LockTransaction transaction) {
        if (job.kind() != JobId.Kind.LLM) {
            return job;
        }
        transaction.acquire(store.lockKey(job.scope()));
        var block = store.read(job.jobId()).map(JobRecord::worker).orElse(job.worker());
        if (block == null) {
            return job;
        }
        // The stored block is what the fate write persists, whatever it says: converting a readable nonterminal block
        // and keeping every other one as it is — also one the codec refuses — never restores the runner's older text.
        try {
            var current = WorkerStateCodec.read(store.recordPath(job.jobId()), block);
            return current.state().isTerminal() ? job.withWorker(block)
                    : job.withWorker(WorkerStateCodec.write(WorkerTermination.terminal(current, job)));
        } catch (StoreRefusal _) {
            return job.withWorker(block);
        }
    }

    /**
     * The release of the build slot the job held, through {@link LeaseStore#release(String, LeaseOwner)}: the lease is
     * removed only while it still has the owner the job claimed it with. A lease that has another owner by now was
     * adopted or claimed anew and is left in place; a job without a slot releases nothing. A worker's model slot is no
     * {@link LeaseStore} lease: it is released through {@code ModelSlotScheduler.release}.
     *
     * @return the slot release step
     */
    static SettlementStep slotRelease() {
        return new SettlementStep() {

            @Override
            public Name name() {
                return Name.SLOT_RELEASE;
            }

            @Override
            public Outcome run(Settled settled) {
                if (settled.slot() == null) {
                    return new Outcome.Done("no slot held");
                }
                var slot = settled.slot();
                LeaseStore.Release release;
                try {
                    release = slot.store().release(slot.key(), slot.expectedOwner());
                } catch (UncheckedIOException | StoreRefusal e) {
                    return new Outcome.Failed("the slot %s was not released: %s".formatted(slot.key(),
                            e.getMessage()));
                }
                return new Outcome.Done(release.released() ? "slot %s released".formatted(slot.key())
                        : "slot %s not held by the job".formatted(slot.key()));
            }
        };
    }
}
