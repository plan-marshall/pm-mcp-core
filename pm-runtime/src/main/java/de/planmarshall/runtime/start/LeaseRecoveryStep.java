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
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.runtime.lock.StaleLeaseSweep;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Action;

/**
 * Step 3 of the per-project recovery ({@link RecoveryStep.Name#LEASES}): the leases of the project whose holder is
 * dead are marked orphaned, in every machine lease store the caller passes (the merge queue, the review-bot windows,
 * the build slots and the model slots), by {@link StaleLeaseSweep#sweep(LeaseStore, String, java.util.function.Predicate,
 * java.time.Instant)}. A lease is released only on positive evidence of abandonment, which is the caller's knowledge
 * ({@link AbandonmentEvidence}); the default {@link AbandonmentEvidence#NONE} finds none, so the step never releases.
 * Leases of other projects are not touched.
 * <p>
 * The releases of slots and task leases at once, for jobs and workers that ended with the previous runtime, belong to
 * {@link RecoveryStep.Name#JOBS_AND_WORKERS} and are not done here.
 *
 * @since 0.1
 */
public final class LeaseRecoveryStep implements RecoveryStep {

    private final List<LeaseStore> stores;
    private final AbandonmentEvidence evidence;
    private final Clock clock;

    /**
     * The caller's evidence that a lease whose holder is dead was abandoned: the owning scope is terminal, archived
     * or no longer resolvable, the lease expired without being adopted, or the job of a slot was recorded as killed.
     */
    @FunctionalInterface
    public interface AbandonmentEvidence {

        /** The seam while no store knows of an abandonment: nothing is abandoned, so nothing is released. */
        AbandonmentEvidence NONE = (_, _) -> false;

        /**
         * @param projectId the id of the project being recovered
         * @param lease     a lease of the project whose holder is not alive
         * @return whether there is positive evidence that the lease was abandoned
         */
        boolean abandoned(String projectId, LeaseRecord lease);
    }

    /**
     * @param stores   the machine lease stores
     * @param evidence the caller's evidence of abandonment
     * @param clock    the clock whose instant an orphaned lease records
     */
    public LeaseRecoveryStep(List<LeaseStore> stores, AbandonmentEvidence evidence, Clock clock) {
        this.stores = List.copyOf(stores);
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Name name() {
        return Name.LEASES;
    }

    /**
     * Sweeps the leases of the project in every store.
     *
     * @param projectId the id of the enrolled project
     * @return the counts per store; a failure when a store cannot be read or written, after which the remaining
     *         stores are not swept
     */
    @Override
    public Outcome run(String projectId) {
        var now = clock.instant();
        var reports = new ArrayList<String>();
        for (var store : stores) {
            StaleLeaseSweep.Result result;
            try {
                result = StaleLeaseSweep.sweep(store, projectId, lease -> evidence.abandoned(projectId, lease), now);
            } catch (UncheckedIOException e) {
                return new Outcome.Failed("lease store " + store.store() + " not swept: " + e.getMessage());
            }
            reports.add(report(store, result));
        }
        return new Outcome.Done(reports.isEmpty() ? "no lease store" : String.join("; ", reports));
    }

    private static String report(LeaseStore store, StaleLeaseSweep.Result result) {
        var counts = "%s kept, %s orphaned, %s removed".formatted(result.count(Action.KEPT),
                result.count(Action.ORPHANED), result.count(Action.REMOVED));
        var empty = result.emptyPopulation().map(population -> " (" + population.code() + ")").orElse("");
        var refusal = result.refusal().map(outcome -> ", refused: " + outcome).orElse("");
        return store.store() + ": " + counts + empty + refusal;
    }
}
