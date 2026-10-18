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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.job.SettlementStep.Name;
import de.planmarshall.runtime.job.SettlementStep.Outcome;
import de.planmarshall.runtime.job.SettlementStep.Settled;

/**
 * The settlement of a job at its end (PM-EXEC-4, PM-ARCH-7): the {@link SettlementStep steps} run in the fixed write
 * order of {@link Name}, whatever order they are given in, the fate of the job record first and the release of its
 * slot after it, so a released slot never precedes the recorded fate.
 * <p>
 * The first step that fails stops the settlement, and no later step runs. A crash or failure between two steps
 * therefore loses at most what the specification allows: a task's metrics row, a freshness mark or a timing sample.
 * A slot or a task lease left on a job whose fate is already recorded is not released by the recovery, whose jobs
 * part skips every settled job: the stale lease sweep of the recovery marks the lease orphaned because its holder no
 * longer lives, and the lease is left to its own expiry.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Outcome Derivation, Write
 * order at job end); {@code doc/specification/store-schemas/10-job-and-lesson-records.adoc} (Job Record).
 *
 * @since 0.1
 */
public final class JobSettlement {

    private final List<SettlementStep> steps;

    /**
     * @param steps one step for every {@link Name}, in any order
     * @throws NullPointerException     if the steps or a step is {@code null}
     * @throws IllegalArgumentException if a name has no step or more than one
     */
    public JobSettlement(Collection<SettlementStep> steps) {
        var byName = new EnumMap<Name, SettlementStep>(Name.class);
        for (var step : Objects.requireNonNull(steps, "steps")) {
            var name = Objects.requireNonNull(step, "step").name();
            if (byName.put(name, step) != null) {
                throw new IllegalArgumentException("More than one settlement step is named " + name);
            }
        }
        var missing = EnumSet.allOf(Name.class);
        missing.removeAll(byName.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Every name of the settlement needs a step, missing: " + missing);
        }
        this.steps = List.copyOf(byName.values());
    }

    /**
     * The settlement of this runtime: the fate in the job's record store, the slot release through the lease store
     * the slot came from, and the seams of every step whose store does not exist yet.
     *
     * @param store       the job records of the job's store
     * @param lockManager the lock manager of the runtime
     * @return the settlement
     */
    public static JobSettlement of(JobRecordStore store, LockManager lockManager) {
        var steps = new ArrayList<SettlementStep>();
        for (var name : Name.values()) {
            steps.add(switch (name) {
                case FATE -> SettlementStep.fate(store, lockManager);
                case SLOT_RELEASE -> SettlementStep.slotRelease();
                case WORKER_QUEUE_TASK, METRICS_ROW, CHANGE_LEDGER_ROW, TIMING_OBSERVATION, JOB_PRIVATE_DIRECTORY ->
                    SettlementStep.notImplemented(name);
            });
        }
        return new JobSettlement(steps);
    }

    /**
     * What one step did.
     *
     * @param name    the step
     * @param outcome what it did
     * @since 0.1
     */
    public record StepReport(Name name, Outcome outcome) {

        /** @throws NullPointerException if a component is {@code null} */
        public StepReport {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * The report of a settlement: the steps that ran, in their order.
     *
     * @param steps the reports of the steps that ran; the last one is the failed step when the settlement stopped
     * @since 0.1
     */
    public record Report(List<StepReport> steps) {

        /** @throws NullPointerException if the steps are {@code null} */
        public Report {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        }

        /** @return the step that failed and stopped the settlement; empty when every step ran */
        public Optional<Name> stoppedAt() {
            return steps.isEmpty() || !(steps.getLast().outcome() instanceof Outcome.Failed) ? Optional.empty()
                    : Optional.of(steps.getLast().name());
        }

        /** @return whether every step ran and none failed */
        public boolean complete() {
            return steps.size() == Name.values().length && stoppedAt().isEmpty();
        }
    }

    /**
     * Settles a job: runs the steps in their fixed order until one fails.
     *
     * @param settled the settled record of the job and the slot it held
     * @return what each step that ran did
     */
    public Report settle(Settled settled) {
        Objects.requireNonNull(settled, "settled");
        var reports = new ArrayList<StepReport>();
        for (var step : steps) {
            var outcome = Objects.requireNonNull(step.run(settled), "outcome");
            reports.add(new StepReport(step.name(), outcome));
            if (outcome instanceof Outcome.Failed) {
                break;
            }
        }
        return new Report(reports);
    }

    /** @return the names of the steps in the order they run */
    List<Name> order() {
        return steps.stream().map(SettlementStep::name).toList();
    }
}
