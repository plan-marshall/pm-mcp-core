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
import java.util.Optional;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.StoreRefusal;

/**
 * One step of the settlement of a job at its end (PM-EXEC-4, PM-ARCH-7), in the fixed write order of
 * {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc}, Outcome Derivation, Write order at job end.
 * {@link JobSettlement} runs the steps in the order of {@link Name}.
 * <p>
 * Two steps are implemented: the fate of the job record ({@link #fate}) and the release of the build or model slot
 * through the {@link LeaseStore} of its machine lease store ({@link #slotRelease()}). The steps whose stores do not
 * exist in this runtime yet are named seams ({@link #notImplemented}) that do nothing and report
 * {@value #NOT_IMPLEMENTED}, like the seams of the per-project recovery: the worker queue task record, the metrics row,
 * the change-ledger row, the timing observation and the removal of a worker's job-private directory. The fate step
 * reports the revocation of a worker's token and the fence of its generation as such a seam as well.
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
        /** The fate of the job record; for a worker it revokes the job token and fences the generation. */
        FATE,
        /** For a worker, the queue task record: the attempt end and the task lease release. */
        WORKER_QUEUE_TASK,
        /** The metrics row of the attempt that ended with the job, with its usage parsed. */
        METRICS_ROW,
        /** The change-ledger row of a job that stamps one. */
        CHANGE_LEDGER_ROW,
        /** The timing observation of the job's budget key. */
        TIMING_OBSERVATION,
        /** The release of the build slot or the model slot the job held. */
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
     * @param slot the slot the job held; empty for a job that needs none or was never admitted
     * @since 0.1
     */
    record Settled(JobRecord job, Optional<Slot> slot) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the record is not settled
         */
        public Settled {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(slot, "slot");
            if (!job.status().isSettled()) {
                throw new IllegalArgumentException("A settled job has a fate: " + job.jobId());
            }
        }
    }

    /** @return the name of the step, which sets its place in the order */
    Name name();

    /**
     * Runs the step for one settled job.
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
     * that owns the job. For a worker, the revocation of its job token and the fence of its generation are a seam
     * reported with the step.
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
                    store.writeFate(transaction, job);
                } catch (UncheckedIOException | StoreRefusal e) {
                    return new Outcome.Failed("the fate of %s was not recorded: %s".formatted(job.jobId(),
                            e.getMessage()));
                }
                var report = "fate %s recorded".formatted(job.fate().wireName());
                return new Outcome.Done(job.jobId().kind() == JobId.Kind.LLM
                        ? report + "; worker token and fence " + NOT_IMPLEMENTED : report);
            }
        };
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
                if (settled.slot().isEmpty()) {
                    return new Outcome.Done("no slot held");
                }
                var slot = settled.slot().get();
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
