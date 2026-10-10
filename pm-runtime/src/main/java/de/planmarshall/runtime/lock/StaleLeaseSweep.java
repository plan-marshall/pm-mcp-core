/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.store.LeaseCodec;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import lombok.experimental.UtilityClass;

/**
 * The sweep of one lease store at a runtime start (PM-IMPL-7). A lease is owned by its scope and only served by a
 * runtime, so a lease whose runtime is gone is not released: it is marked as orphaned and kept for its scope to
 * adopt. The rules, in this order, for each lease:
 * <ol>
 * <li>The holder is alive: the lease is kept as it is.</li>
 * <li>The holder is not alive and there is evidence that the lease was abandoned: the lease is removed.</li>
 * <li>The holder is not alive and there is no such evidence: the lease is kept. It is marked as orphaned at the
 * instant of the sweep, unless an earlier sweep marked it already; the first mark stays.</li>
 * </ol>
 * <p>
 * A lease is never removed for a lack of evidence. What counts as evidence is the caller's knowledge and is passed
 * in: the owning scope is terminal, archived or no longer resolvable, the lease expired without being adopted, or
 * the job of a slot was recorded as killed. The sweep itself reads the lease store and nothing else.
 * <p>
 * The leases are read, judged and written in one transaction on the lock of the store, so no claim or adoption
 * comes between the judgement of a lease and the write. The evidence is asked for while that lock is held: it reads
 * without a lock and takes none.
 *
 * @since 0.1
 */
@UtilityClass
public class StaleLeaseSweep {

    private static final CuiLogger LOGGER = new CuiLogger(StaleLeaseSweep.class);

    /**
     * What the sweep did to a lease.
     *
     * @since 0.1
     */
    public enum Action {
        /** The lease is in the store as it was before the sweep. */
        KEPT,
        /** The lease is in the store and was marked as orphaned by this sweep. */
        ORPHANED,
        /** The lease was removed from the store. */
        REMOVED
    }

    /**
     * The rule that decided on a lease: for a lease that stays, the first rule that keeps it; for a removed lease,
     * what triggered the removal.
     *
     * @since 0.1
     */
    public enum Rule {
        /** The runtime instance that serves the lease is alive. */
        HOLDER_ALIVE("holder_alive"),
        /** The holder is not alive, and nothing shows that the lease was abandoned. */
        SKIPPED_NO_EVIDENCE("skipped_no_evidence"),
        /** The holder is not alive, and the caller's evidence shows that the lease was abandoned. */
        ABANDONED("abandoned");

        private final String code;

        Rule(String code) {
            this.code = code;
        }

        /** @return the name of the rule in a report */
        public String code() {
            return code;
        }
    }

    /**
     * Why a sweep removed nothing. A sweep that removed nothing says which of these it was and never reports a
     * bare zero.
     *
     * @since 0.1
     */
    public enum EmptyPopulation {
        /** The store does not exist. */
        ROOT_ABSENT("root_absent"),
        /** The store is in another format version or unreadable; nothing was judged and nothing was written. */
        ROOT_UNREADABLE("root_unreadable"),
        /** The store exists and holds no lease. */
        NOTHING_LISTED("nothing_listed"),
        /** The store holds leases, and every one of them was kept. */
        ALL_KEPT("all_kept");

        private final String code;

        EmptyPopulation(String code) {
            this.code = code;
        }

        /** @return the name of the value in a report */
        public String code() {
            return code;
        }
    }

    /**
     * The decision on one lease.
     *
     * @param lease  the lease as the sweep left it; for a removed lease, as it was
     * @param action what the sweep did to the lease
     * @param rule   the rule that decided
     * @since 0.1
     */
    public record Decision(LeaseRecord lease, Action action, Rule rule) {

        /** @throws NullPointerException if a component is {@code null} */
        public Decision {
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(rule, "rule");
        }
    }

    /**
     * The result of the sweep of one store.
     *
     * @param decisions       the decision on each lease, in the order of the store
     * @param emptyPopulation why nothing was removed; empty if a lease was removed
     * @param refusal         why the store could not be read; empty unless the store was refused
     * @since 0.1
     */
    public record Result(
    List<Decision> decisions,
    Optional<EmptyPopulation> emptyPopulation,
    Optional<LeaseCodec.Refusal> refusal) {

        /** @throws NullPointerException if a component or a decision is {@code null} */
        public Result {
            decisions = List.copyOf(decisions);
            Objects.requireNonNull(emptyPopulation, "emptyPopulation");
            Objects.requireNonNull(refusal, "refusal");
        }

        /**
         * @param action an action
         * @return the number of leases the sweep did that to
         */
        public long count(Action action) {
            return decisions.stream().filter(decision -> decision.action() == action).count();
        }
    }

    /**
     * Sweeps one lease store.
     *
     * @param store     the lease store
     * @param abandoned the caller's evidence: whether a lease whose holder is not alive was abandoned
     * @param now       the instant of the sweep, which a lease that is orphaned by it records
     * @return the decision on each lease, and why nothing was removed if nothing was
     * @throws java.io.UncheckedIOException if the store cannot be read or written
     */
    public static Result sweep(LeaseStore store, Predicate<LeaseRecord> abandoned, Instant now) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(abandoned, "abandoned");
        Objects.requireNonNull(now, "now");
        var decisions = new ArrayList<Decision>();
        Optional<List<LeaseRecord>> swept;
        try {
            swept = store.reviseAll(lease -> {
                var decision = decide(lease, abandoned, now);
                decisions.add(decision);
                return decision.action() == Action.REMOVED ? Optional.empty() : Optional.of(decision.lease());
            });
        } catch (LeaseCodec.FormatException e) {
            LOGGER.debug(e, "Stale lease sweep of '%s' refused the store", store.store());
            return new Result(List.of(), Optional.of(EmptyPopulation.ROOT_UNREADABLE), Optional.of(e.refusal()));
        }
        if (swept.isEmpty()) {
            return new Result(List.of(), Optional.of(EmptyPopulation.ROOT_ABSENT), Optional.empty());
        }
        var result = new Result(decisions, emptyPopulation(decisions), Optional.empty());
        log(store, result);
        return result;
    }

    private static Decision decide(LeaseRecord lease, Predicate<LeaseRecord> abandoned, Instant now) {
        if (HolderLiveness.isAlive(lease.owner().holderInstance())) {
            return new Decision(lease, Action.KEPT, Rule.HOLDER_ALIVE);
        }
        if (abandoned.test(lease)) {
            return new Decision(lease, Action.REMOVED, Rule.ABANDONED);
        }
        if (lease.owner().isOrphaned()) {
            return new Decision(lease, Action.KEPT, Rule.SKIPPED_NO_EVIDENCE);
        }
        return new Decision(lease.withOwner(lease.owner().orphaned(now)), Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE);
    }

    private static Optional<EmptyPopulation> emptyPopulation(List<Decision> decisions) {
        if (decisions.isEmpty()) {
            return Optional.of(EmptyPopulation.NOTHING_LISTED);
        }
        var removed = decisions.stream().anyMatch(decision -> decision.action() == Action.REMOVED);
        return removed ? Optional.empty() : Optional.of(EmptyPopulation.ALL_KEPT);
    }

    private static void log(LeaseStore store, Result result) {
        for (var decision : result.decisions()) {
            if (decision.action() == Action.ORPHANED) {
                LOGGER.warn(WARN.LEASE_ORPHANED, decision.lease().key(), store.store(),
                        decision.lease().owner().holderInstance().runtimePid());
            }
        }
        LOGGER.info(INFO.STALE_LEASE_SWEEP_FINISHED, store.store(), result.count(Action.KEPT),
                result.count(Action.ORPHANED), result.count(Action.REMOVED));
    }
}
